package com.goodgirl.accounting.data

import androidx.room.withTransaction
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.DailySalesDraftEntity
import com.goodgirl.accounting.data.local.DailySalesDraftLineEntity
import com.goodgirl.accounting.data.local.InvoiceEntity
import com.goodgirl.accounting.data.local.InvoiceLineEntity
import java.math.BigDecimal
import java.util.UUID

data class DailySalesDraftSnapshot(
    val header: DailySalesDraftEntity?,
    val lines: List<DailySalesDraftLineEntity>
)

/**
 * Persists the daily-sales editor separately from posted accounting documents.
 * Each row has a deterministic invoice number derived from its immutable ID.
 * This lets a retry recover if the app stops after posting but before removing
 * that row from the draft.
 */
class DailySalesDraftRepository(private val database: AccountingDatabase) {
    private val dao get() = database.dailySalesDraftDao()
    private val invoices = InvoiceRepository(database)

    suspend fun loadSnapshot(): DailySalesDraftSnapshot {
        val header = dao.getActiveDraft()
        val lines = if (header == null) emptyList() else dao.getLines(header.id)
        return DailySalesDraftSnapshot(header, lines)
    }

    suspend fun saveSnapshot(
        header: DailySalesDraftEntity,
        lines: List<DailySalesDraftLineEntity>
    ) {
        require(header.id == ACTIVE_DRAFT_ID) { "معرّف مسودة المبيعات غير صالح" }
        require(lines.size <= MAX_PENDING_LINES) { "تجاوزت المسودة الحد الأقصى لعدد السطور" }
        require(lines.map { it.id }.distinct().size == lines.size) { "يوجد سطر مكرر في المسودة" }
        val orderedLines = lines.mapIndexed { index, line ->
            require(line.draftId == ACTIVE_DRAFT_ID) { "السطر لا يتبع مسودة المبيعات الحالية" }
            line.copy(lineOrder = index)
        }
        database.withTransaction {
            dao.saveDraft(header)
            dao.deleteLines(ACTIVE_DRAFT_ID)
            if (orderedLines.isNotEmpty()) dao.saveLines(orderedLines)
        }
    }

    suspend fun removePendingLine(lineId: String) {
        database.withTransaction {
            dao.deleteLine(lineId)
            val remaining = dao.getLines(ACTIVE_DRAFT_ID)
            if (remaining.isNotEmpty()) {
                dao.deleteLines(ACTIVE_DRAFT_ID)
                dao.saveLines(remaining.mapIndexed { index, line -> line.copy(lineOrder = index) })
            }
        }
    }

    suspend fun clearPendingLines() {
        database.withTransaction { dao.deleteLines(ACTIVE_DRAFT_ID) }
    }

    suspend fun clearDraft() {
        database.withTransaction {
            dao.deleteLines(ACTIVE_DRAFT_ID)
            dao.deleteDraft(ACTIVE_DRAFT_ID)
        }
    }

    suspend fun postPendingLine(line: DailySalesDraftLineEntity): PostedInvoiceResult {
        require(line.draftId == ACTIVE_DRAFT_ID) { "السطر لا يتبع مسودة المبيعات الحالية" }
        val invoiceNumber = invoiceNumberFor(line.id)
        val existing = database.invoiceDao().findByInvoiceNumber(invoiceNumber)
        val result = if (existing != null) {
            existing.toPostedResultAfterVerifying(line)
        } else {
            validateCurrentReferences(line)
            try {
                invoices.postInvoice(
                    invoiceNumber = invoiceNumber,
                    kind = "SALE",
                    partyId = line.customerId,
                    currencyCode = line.currencyCode,
                    exchangeRateSnapshot = line.exchangeRate,
                    lines = listOf(
                        InvoiceLineDraft(
                            productId = line.productId,
                            quantity = line.quantity,
                            unitPrice = line.unitPrice,
                            discount = line.discount,
                            unitName = line.unitName,
                            saleUnitCostSnapshot = line.purchaseCostSnapshot
                        )
                    ),
                    paidAmount = line.paidAmount,
                    issuedAt = line.issuedAt,
                    note = line.note
                )
            } catch (error: Exception) {
                // A competing or retried operation may have committed this
                // deterministic invoice while this caller was waiting.
                val committed = database.invoiceDao().findByInvoiceNumber(invoiceNumber)
                    ?: throw error
                committed.toPostedResultAfterVerifying(line)
            }
        }

        // If the process stops before this delete, a retry recognizes the
        // already-posted invoice and removes the draft row without reposting.
        database.withTransaction {
            dao.deleteLine(line.id)
            val remainingLines = dao.getLines(ACTIVE_DRAFT_ID)
            if (remainingLines.isNotEmpty()) {
                dao.deleteLines(ACTIVE_DRAFT_ID)
                dao.saveLines(remainingLines.mapIndexed { index, row -> row.copy(lineOrder = index) })
            }
        }
        return result
    }

    private suspend fun validateCurrentReferences(line: DailySalesDraftLineEntity) {
        val product = database.productDao().findById(line.productId)
            ?: throw IllegalArgumentException("المادة المحفوظة غير موجودة؛ بقي السطر في المسودة")
        require(product.isActive) { "المادة مؤرشفة؛ أعد اختيار مادة متاحة قبل الترحيل" }
        require(product.name == line.productNameSnapshot) {
            "اسم المادة تغيّر منذ إضافة السطر؛ راجع السطر وأعد اختياره قبل الترحيل"
        }
        require(product.priceCurrencyCode == line.currencyCode) {
            "عملة المادة تغيّرت منذ إضافة السطر؛ راجع السطر قبل الترحيل"
        }
        val unit = database.productUnitDao().findByName(product.id, line.unitName)
            ?: throw IllegalArgumentException("وحدة القياس المحفوظة لم تعد مرتبطة بالمادة")
        require(sameDecimal(unit.baseFactor, line.unitFactor)) {
            "عامل وحدة القياس تغيّر منذ إضافة السطر؛ راجع السطر قبل الترحيل"
        }
    }

    private suspend fun InvoiceEntity.toPostedResultAfterVerifying(
        draftLine: DailySalesDraftLineEntity
    ): PostedInvoiceResult {
        require(status == "POSTED" && kind == "SALE") {
            "يوجد رقم فاتورة مطابق لكنه ليس عملية بيع مرحلة؛ بقي السطر محفوظاً للمراجعة"
        }
        require(invoiceNumber == invoiceNumberFor(draftLine.id)) {
            "رقم الفاتورة لا يطابق معرّف سطر المسودة"
        }
        require(partyId == draftLine.customerId && currencyCode == draftLine.currencyCode) {
            "بيانات الفاتورة الموجودة لا تطابق المسودة؛ لم يتم تكرار الترحيل"
        }
        require(sameDecimal(exchangeRateSnapshot, draftLine.exchangeRate)) {
            "سعر صرف الفاتورة الموجودة لا يطابق المسودة"
        }
        require(issuedAt == draftLine.issuedAt && note == draftLine.note) {
            "تاريخ أو ملاحظات الفاتورة الموجودة لا تطابق المسودة"
        }
        val savedLines = database.invoiceDao().getLines(id)
        require(savedLines.size == 1) { "الفاتورة الموجودة لا تحتوي سطر المسودة المتوقع" }
        val savedLine = savedLines.single()
        val expectedTotal = BigDecimal(draftLine.quantity)
            .multiply(BigDecimal(draftLine.unitPrice))
            .subtract(BigDecimal(draftLine.discount))
        val expectedBaseQuantity = BigDecimal(draftLine.quantity)
            .multiply(BigDecimal(draftLine.unitFactor))
        require(savedLine.matchesDraftLine(draftLine, expectedTotal, expectedBaseQuantity)) {
            "تفاصيل الفاتورة الموجودة لا تطابق السطر المحفوظ؛ بقيت المسودة دون حذف"
        }
        require(sameDecimal(paid, draftLine.paidAmount) && sameDecimal(total, expectedTotal.toPlainString())) {
            "إجمالي الفاتورة أو المقبوض لا يطابق المسودة"
        }
        return PostedInvoiceResult(
            invoiceId = id,
            invoiceNumber = invoiceNumber,
            total = total,
            paid = paid,
            remaining = remaining
        )
    }

    private fun InvoiceLineEntity.matchesDraftLine(
        draftLine: DailySalesDraftLineEntity,
        expectedTotal: BigDecimal,
        expectedBaseQuantity: BigDecimal
    ): Boolean =
        productId == draftLine.productId &&
            productNameSnapshot == draftLine.productNameSnapshot &&
            unitSnapshot == draftLine.unitName &&
            sameDecimal(quantity, draftLine.quantity) &&
            sameDecimal(unitFactorSnapshot, draftLine.unitFactor) &&
            sameDecimal(baseQuantity, expectedBaseQuantity.toPlainString()) &&
            sameDecimal(unitPriceSnapshot, draftLine.unitPrice) &&
            (draftLine.purchaseCostSnapshot == null || sameDecimal(unitCostSnapshot, draftLine.purchaseCostSnapshot)) &&
            sameDecimal(discount, draftLine.discount) &&
            sameDecimal(lineTotal, expectedTotal.toPlainString())

    private fun sameDecimal(left: String, right: String): Boolean =
        runCatching { BigDecimal(left).compareTo(BigDecimal(right)) == 0 }.getOrDefault(false)

    companion object {
        const val ACTIVE_DRAFT_ID = "active"
        const val MAX_PENDING_LINES = 100

        fun invoiceNumberFor(lineId: String): String {
            require(lineId.isNotBlank() && lineId.length <= 36) { "معرّف سطر المسودة غير صالح" }
            return "DS-$lineId"
        }

        fun newLineId(): String = UUID.randomUUID().toString()
    }
}
