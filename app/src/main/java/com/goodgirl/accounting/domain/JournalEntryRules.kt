package com.goodgirl.accounting.domain

import java.math.BigDecimal

data class JournalLineDraft(
    val accountId: String,
    val debit: String = "0",
    val credit: String = "0",
    val memo: String = ""
)

data class NormalizedJournalLine(
    val accountId: String,
    val debit: String,
    val credit: String,
    val memo: String
)

data class NormalizedJournalDocument(
    val currencyCode: String,
    val exchangeRateSnapshot: String,
    val lines: List<NormalizedJournalLine>
)

/**
 * Validates a journal document using exact decimal arithmetic.
 * Each journal is single-currency; cross-currency entries require a
 * separately designed and explicitly valued exchange journal.
 */
object JournalEntryRules {
    fun normalize(
        currencyCode: String,
        exchangeRateSnapshot: String,
        lines: List<JournalLineDraft>
    ): NormalizedJournalDocument {
        val currency = currencyCode.trim().uppercase()
        require(currency == "SYP" || currency == "USD") { "عملة القيد غير مدعومة" }

        val rate = decimal(exchangeRateSnapshot, "سعر الصرف")
        require(rate.compareTo(BigDecimal.ZERO) > 0) { "سعر الصرف يجب أن يكون أكبر من صفر" }

        require(lines.size >= 2) { "يجب أن يحتوي القيد على سطرين على الأقل" }
        val normalized = lines.map { line ->
            val accountId = line.accountId.trim()
            require(accountId.isNotEmpty()) { "الحساب مطلوب لكل سطر" }
            val debit = decimal(line.debit, "المدين")
            val credit = decimal(line.credit, "الدائن")
            require(debit.signum() >= 0 && credit.signum() >= 0) {
                "لا يجوز تسجيل مبلغ مدين أو دائن سالب"
            }
            require((debit.signum() > 0) xor (credit.signum() > 0)) {
                "يجب أن يحتوي كل سطر على مبلغ مدين أو دائن فقط"
            }
            NormalizedJournalLine(
                accountId = accountId,
                debit = debit.stripTrailingZeros().toPlainString(),
                credit = credit.stripTrailingZeros().toPlainString(),
                memo = line.memo.trim()
            )
        }

        val totalDebit = normalized.fold(BigDecimal.ZERO) { total, line ->
            total.add(line.debit.toBigDecimal())
        }
        val totalCredit = normalized.fold(BigDecimal.ZERO) { total, line ->
            total.add(line.credit.toBigDecimal())
        }
        require(totalDebit.compareTo(totalCredit) == 0) {
            "القيد غير متوازن: مجموع المدين لا يساوي مجموع الدائن"
        }

        return NormalizedJournalDocument(
            currencyCode = currency,
            exchangeRateSnapshot = rate.stripTrailingZeros().toPlainString(),
            lines = normalized
        )
    }

    private fun decimal(value: String, label: String): BigDecimal {
        val clean = value.trim()
        require(clean.isNotEmpty()) { "$label مطلوب" }
        return try {
            BigDecimal(clean)
        } catch (error: NumberFormatException) {
            throw IllegalArgumentException("$label غير صالح", error)
        }
    }
}
