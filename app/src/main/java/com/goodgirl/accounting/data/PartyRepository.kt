package com.goodgirl.accounting.data

import androidx.room.withTransaction
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.LedgerEntryEntity
import com.goodgirl.accounting.data.local.PartyEntity
import com.goodgirl.accounting.domain.PartyInputRules
import com.goodgirl.accounting.domain.ProductInputRules
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.util.UUID

class PartyRepository(private val database: AccountingDatabase) {
    fun observeParties(kind: String): Flow<List<PartyEntity>> {
        val cleanKind = PartyInputRules.normalizeKind(kind)
        return database.partyDao().observeByKind(cleanKind)
    }

    fun observeParty(id: String): Flow<PartyEntity?> =
        database.partyDao().observeById(id)

    suspend fun saveParty(
        id: String?,
        kind: String,
        name: String,
        phone: String,
        address: String,
        note: String,
        openingBalance: String = "0",
        currencyCode: String = "SYP",
        openingExchangeRate: String = "1"
    ) {
        val cleanKind = PartyInputRules.normalizeKind(kind)
        val cleanName = PartyInputRules.normalizeName(name)
        val cleanOpeningBalance = ProductInputRules.normalizeNonNegativeDecimal(
            openingBalance, "الرصيد الافتتاحي"
        )
        val cleanCurrency = PartyInputRules.normalizeOpeningCurrency(currencyCode)
        val cleanRate = ProductInputRules.normalizeNonNegativeDecimal(
            openingExchangeRate, "سعر صرف الرصيد الافتتاحي"
        )
        require(cleanRate.toBigDecimal() > BigDecimal.ZERO) {
            "سعر الصرف يجب أن يكون أكبر من صفر"
        }
        if (cleanCurrency == "SYP") {
            require(cleanRate.toBigDecimal().compareTo(BigDecimal.ONE) == 0) {
                "سعر صرف الليرة السورية يجب أن يكون 1"
            }
        }

        val now = System.currentTimeMillis()
        database.withTransaction {
            if (id == null) {
                val partyId = UUID.randomUUID().toString()
                database.partyDao().insert(
                    PartyEntity(
                        id = partyId,
                        name = cleanName,
                        kind = cleanKind,
                        phone = phone.trim(),
                        address = address.trim(),
                        openingBalance = cleanOpeningBalance,
                        currencyCode = cleanCurrency,
                        openingExchangeRate = cleanRate,
                        note = note.trim(),
                        createdAt = now
                    )
                )

                if (cleanOpeningBalance.toBigDecimal() > BigDecimal.ZERO) {
                    database.ledgerDao().insert(
                        LedgerEntryEntity(
                            id = UUID.randomUUID().toString(),
                            partyId = partyId,
                            entryType = if (cleanKind == "CUSTOMER") "RECEIVABLE" else "PAYABLE",
                            amount = cleanOpeningBalance,
                            currencyCode = cleanCurrency,
                            exchangeRateSnapshot = cleanRate,
                            sourceType = if (cleanKind == "CUSTOMER") "CUSTOMER_OPENING_BALANCE" else "SUPPLIER_OPENING_BALANCE",
                            sourceId = partyId,
                            occurredAt = now,
                            note = "رصيد افتتاحي عند إنشاء الحساب"
                        )
                    )
                }
            } else {
                val current = database.partyDao().findById(id)
                    ?: throw IllegalArgumentException("جهة التعامل غير موجودة")
                require(current.kind == cleanKind) { "نوع جهة التعامل غير مطابق" }
                database.partyDao().update(
                    current.copy(
                        name = cleanName,
                        phone = phone.trim(),
                        address = address.trim(),
                        note = note.trim()
                    )
                )
            }
        }
    }
}
