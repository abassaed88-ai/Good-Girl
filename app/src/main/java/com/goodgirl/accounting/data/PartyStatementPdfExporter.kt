package com.goodgirl.accounting.data

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.goodgirl.accounting.data.local.LedgerEntryEntity
import com.goodgirl.accounting.data.local.PartyEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PartyStatementPdfExporter {
    suspend fun write(context: Context, destination: Uri, party: PartyEntity, entries: List<LedgerEntryEntity>) =
        withContext(Dispatchers.IO) {
            val pageWidth = 595
            val pageHeight = 842
            val margin = 30
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.BLACK
                textSize = 12f
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            }
            val content = buildContent(party, entries, System.currentTimeMillis())
            val layout = StaticLayout.Builder.obtain(content, 0, content.length, paint, pageWidth - margin * 2)
                .setAlignment(Layout.Alignment.ALIGN_OPPOSITE)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .setLineSpacing(2f, 1f)
                .setIncludePad(true)
                .build()
            val lineHeight = if (layout.lineCount > 0) (layout.getLineBottom(0) - layout.getLineTop(0)).coerceAtLeast(1) else 18
            val availableHeight = pageHeight - margin * 2 - 28
            val contentHeight = (availableHeight / lineHeight).coerceAtLeast(1) * lineHeight
            val pages = ceil(layout.height.toDouble() / contentHeight).toInt().coerceAtLeast(1)
            val document = PdfDocument()
            try {
                for (index in 0 until pages) {
                    val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                    val page = document.startPage(info)
                    page.canvas.drawColor(android.graphics.Color.WHITE)
                    page.canvas.save()
                    page.canvas.translate(margin.toFloat(), (margin - index * contentHeight).toFloat())
                    layout.draw(page.canvas)
                    page.canvas.restore()
                    val footer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF64748B.toInt()
                        textSize = 10f
                        textAlign = Paint.Align.CENTER
                    }
                    page.canvas.drawText("Good Girl  •  ${index + 1} / $pages", pageWidth / 2f, (pageHeight - 14).toFloat(), footer)
                    document.finishPage(page)
                }
                val output = context.contentResolver.openOutputStream(destination, "wt")
                    ?: throw IllegalStateException("تعذر فتح ملف PDF للكتابة")
                output.use { document.writeTo(it); it.flush() }
            } finally {
                document.close()
            }
        }

    internal fun buildContent(party: PartyEntity, entries: List<LedgerEntryEntity>, generatedAt: Long): String {
        val date = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(generatedAt))
        val relevant = if (party.kind == "CUSTOMER") setOf("RECEIVABLE", "RECEIVABLE_PAYMENT")
            else setOf("PAYABLE", "PAYABLE_PAYMENT")
        val balances = mutableMapOf<String, java.math.BigDecimal>()
        val rows = entries.filter { it.entryType in relevant }.sortedBy { it.occurredAt }.map { entry ->
            val increase = if (party.kind == "CUSTOMER") entry.entryType == "RECEIVABLE" else entry.entryType == "PAYABLE"
            val previous = balances[entry.currencyCode] ?: java.math.BigDecimal.ZERO
            val next = if (increase) previous.add(entry.amount.toBigDecimal()) else previous.subtract(entry.amount.toBigDecimal())
            balances[entry.currencyCode] = next
            Triple(entry, next, increase)
        }.reversed()
        return buildString {
            append("Good Girl\nكشف حساب ").append(if (party.kind == "CUSTOMER") "عميل" else "مورد").append("\n")
            append("الاسم: ").append(party.name).append("\n")
            if (party.phone.isNotBlank()) append("الهاتف: ").append(party.phone).append("\n")
            if (party.address.isNotBlank()) append("العنوان: ").append(party.address).append("\n")
            append("تاريخ إنشاء الكشف: ").append(date).append("\n")
            append("----------------------------------------\nالأرصدة الحالية حسب العملة\n")
            if (balances.isEmpty()) append("لا يوجد رصيد مسجل\n")
            balances.forEach { (currency, amount) -> append(amount.stripTrailingZeros().toPlainString()).append(" ").append(currency).append("\n") }
            append("----------------------------------------\nالحركات المالية (الأحدث أولاً)\n")
            if (rows.isEmpty()) append("لا توجد حركات مالية لهذا الحساب.\n")
            rows.forEach { (entry, balance, increase) ->
                append(SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(entry.occurredAt))).append("\n")
                append(when (entry.entryType) {
                    "RECEIVABLE" -> "فاتورة بيع / زيادة مستحق"
                    "RECEIVABLE_PAYMENT" -> "تحصيل / تخفيض المستحق"
                    "PAYABLE" -> "فاتورة شراء / زيادة مستحق"
                    "PAYABLE_PAYMENT" -> "دفعة مورد / تخفيض المستحق"
                    else -> entry.entryType
                }).append("\n")
                append(if (increase) "+" else "-").append(entry.amount).append(" ").append(entry.currencyCode).append("\n")
                append("الرصيد بعد الحركة: ").append(balance.stripTrailingZeros().toPlainString()).append(" ").append(entry.currencyCode).append("\n")
                if (entry.note.isNotBlank()) append("ملاحظة: ").append(entry.note).append("\n")
                append("----------------------------------------\n")
            }
            append("ملاحظة: تُعرض كل عملة بشكل مستقل دون تحويل أو جمع تلقائي.")
        }
    }
}
