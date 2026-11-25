package com.goodgirl.accounting.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.ExchangeRateHistoryEntity
import com.goodgirl.accounting.domain.JournalLineDraft
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PeriodClosingRepositoryIntegrationTest {
    @Test
    fun postsBalancedCloseAndBlocksNewTransactionsInsideClosedDateRange() = runBlocking {
        val database = newDatabase()
        try {
            val journals = JournalRepository(database)
            journals.ensureSystemAccounts()
            val accounts = database.accountDao().getAll().associateBy { it.code }
            journals.postJournalEntry(
                "CLOSE-TEST-SALE", "TEST_SALE", "sale-1", "SYP", "1", 1_000,
                "مبيعات اختبارية",
                listOf(
                    JournalLineDraft(accounts.getValue("1000").id, debit = "100"),
                    JournalLineDraft(accounts.getValue("4000").id, credit = "100")
                )
            )
            journals.postJournalEntry(
                "CLOSE-TEST-EXP", "TEST_EXPENSE", "expense-1", "SYP", "1", 1_500,
                "مصروف اختباري",
                listOf(
                    JournalLineDraft(accounts.getValue("7000").id, debit = "25"),
                    JournalLineDraft(accounts.getValue("1000").id, credit = "25")
                )
            )

            val closingId = PeriodClosingRepository(database).closePeriod(
                1_000, 2_000, "SYP", accounts.getValue("3100").id, "CLOSE-TEST-001"
            )
            val closing = database.journalEntryDao().findById(closingId)!!
            assertEquals("PERIOD_CLOSE", closing.sourceType)
            val closingLines = database.journalLineDao().getForEntry(closingId)
            assertEquals(3, closingLines.size)
            assertTrue(closingLines.any { it.accountId == accounts.getValue("3100").id })
            assertTrue(closingLines.none { it.accountId == accounts.getValue("3000").id })
            assertEquals(
                0,
                closingLines.fold(java.math.BigDecimal.ZERO) { total, line -> total.add(line.debit.toBigDecimal()) }
                    .compareTo(closingLines.fold(java.math.BigDecimal.ZERO) { total, line -> total.add(line.credit.toBigDecimal()) })
            )

            val closeAgain = assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    PeriodClosingRepository(database).closePeriod(
                        1_000, 2_000, "SYP", accounts.getValue("3000").id, "CLOSE-TEST-002"
                    )
                }
            }
            assertTrue(closeAgain.message!!.contains("تتداخل"))

            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    journals.postJournalEntry(
                        "CLOSE-TEST-BLOCKED", "TEST_SALE", "sale-blocked", "SYP", "1", 1_700,
                        "حركة بتاريخ مغلق",
                        listOf(
                            JournalLineDraft(accounts.getValue("1000").id, debit = "5"),
                            JournalLineDraft(accounts.getValue("4000").id, credit = "5")
                        )
                    )
                }
            }

            journals.postJournalEntry(
                "CLOSE-TEST-AFTER", "TEST_SALE", "sale-after", "SYP", "1", 3_000,
                "حركة بعد الإغلاق",
                listOf(
                    JournalLineDraft(accounts.getValue("1000").id, debit = "5"),
                    JournalLineDraft(accounts.getValue("4000").id, credit = "5")
                )
            )
            assertEquals(4, database.journalEntryDao().getAll().size)
        } finally {
            database.close()
        }
    }

    @Test
    fun capturesTheLatestExchangeRateEffectiveOnTheDollarClosingDate() = runBlocking {
        val database = newDatabase()
        try {
            val journals = JournalRepository(database)
            journals.ensureSystemAccounts()
            val accounts = database.accountDao().getAll().associateBy { it.code }
            journals.postJournalEntry(
                "CLOSE-USD-SALE", "TEST_SALE", "usd-sale-1", "USD", "14900", 1_200,
                "مبيعات بالدولار",
                listOf(
                    JournalLineDraft(accounts.getValue("1000").id, debit = "100"),
                    JournalLineDraft(accounts.getValue("4000").id, credit = "100")
                )
            )
            database.exchangeRateHistoryDao().insert(
                ExchangeRateHistoryEntity(
                    id = "usd-rate-before-close",
                    currencyPair = "USD/SYP",
                    rate = "14000",
                    effectiveAt = 1_000,
                    recordedAt = 1_000,
                    source = "TEST"
                )
            )
            database.exchangeRateHistoryDao().insert(
                ExchangeRateHistoryEntity(
                    id = "usd-rate-after-close",
                    currencyPair = "USD/SYP",
                    rate = "16000",
                    effectiveAt = 2_001,
                    recordedAt = 2_001,
                    source = "TEST"
                )
            )

            val closingId = PeriodClosingRepository(database).closePeriod(
                1_000, 2_000, "USD", accounts.getValue("3100").id, "CLOSE-USD-001"
            )
            assertEquals("14000", database.journalEntryDao().findById(closingId)!!.exchangeRateSnapshot)
        } finally {
            database.close()
        }
    }

    private fun newDatabase(): AccountingDatabase =
        Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AccountingDatabase::class.java
        ).allowMainThreadQueries().build()
}