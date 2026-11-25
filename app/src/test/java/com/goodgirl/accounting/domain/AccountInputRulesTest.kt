package com.goodgirl.accounting.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AccountInputRulesTest {
    @Test
    fun normalizesAccountNameAndCalculatesNaturalBalanceSide() {
        val asset = AccountInputRules.normalize(" 1101 ", " صندوق فرعي ", "asset")
        assertEquals("1101", asset.code)
        assertEquals("صندوق فرعي", asset.name)
        assertEquals("ASSET", asset.accountType)
        assertEquals("DEBIT", asset.normalSide)

        val liability = AccountInputRules.normalize("2101", "موردون فرعيون", "LIABILITY")
        assertEquals("CREDIT", liability.normalSide)

        val expense = AccountInputRules.normalize("7101", "مصروف نقل", "EXPENSE")
        assertEquals("DEBIT", expense.normalSide)

        val revenue = AccountInputRules.normalize("6101", "إيراد خدمة", "REVENUE")
        assertEquals("CREDIT", revenue.normalSide)
    }

    @Test
    fun rejectsArabicDigitsAndInvalidAccountCodes() {
        listOf("١١٠١", "11", "110A", "1101.1", "").forEach { code ->
            assertThrows(IllegalArgumentException::class.java) {
                AccountInputRules.normalize(code, "حساب اختبار", "ASSET")
            }
        }
    }

    @Test
    fun rejectsEmptyLongAndUnsupportedAccountTypes() {
        assertThrows(IllegalArgumentException::class.java) {
            AccountInputRules.normalize("1101", " ", "ASSET")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AccountInputRules.normalize("1101", "ا".repeat(81), "ASSET")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AccountInputRules.normalize("1101", "حساب اختبار", "BANK")
        }
    }
}
