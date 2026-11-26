package com.goodgirl.accounting.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.ExchangeRateHistoryEntity
import com.goodgirl.accounting.domain.GeneralLedgerFinancialStatementsCalculator
import com.goodgirl.accounting.domain.JournalLineDraft
import java.math.BigDecimal
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

    @Test
    fun reopensPeriodWithExactReversalAllowsBackdatedPostingAndCanCloseAgain() = runBlocking {
        val database = newDatabase()
        try {
            val journals = JournalRepository(database)
            journals.ensureSystemAccounts()
            val accounts = database.accountDao().getAll().associateBy { it.code }
            journals.postJournalEntry(
                "REOPEN-TEST-SALE", "TEST_SALE", "reopen-sale-1", "SYP", "1", 1_000,
                "مبيعات الفترة الأصلية",
                listOf(
                    JournalLineDraft(accounts.getValue("1000").id, debit = "100"),
                    JournalLineDraft(accounts.getValue("4000").id, credit = "100")
                )
            )
            journals.postJournalEntry(
                "REOPEN-TEST-EXP", "TEST_EXPENSE", "reopen-expense-1", "SYP", "1", 1_500,
                "مصروف الفترة الأصلي",
                listOf(
                    JournalLineDraft(accounts.getValue("7000").id, debit = "25"),
                    JournalLineDraft(accounts.getValue("1000").id, credit = "25")
                )
            )

            val closingRepository = PeriodClosingRepository(database)
            val closeId = closingRepository.closePeriod(
                1_000, 2_000, "SYP", accounts.getValue("3100").id, "CLOSE-REOPEN-001"
            )
            val originalClose = database.journalEntryDao().findById(closeId)!!
            val originalCloseLines = database.journalLineDao().getForEntry(closeId)
            assertTrue(PeriodClosingRepository.isClosedDate(database.journalEntryDao().getAll(), "SYP", 1_700))

            val reversalId = closingRepository.reopenPeriod(
                closingEntryId = closeId,
                reversalJournalNumber = "REOPEN-CLOSE-001",
                occurredAt = 3_000,
                reason = "تصحيح خطأ في قيد الإقفال"
            )
            val reversal = database.journalEntryDao().findById(reversalId)!!
            val reversalLines = database.journalLineDao().getForEntry(reversalId)
            assertEquals("PERIOD_CLOSE_REVERSAL", reversal.sourceType)
            assertEquals(closeId, reversal.sourceId)
            assertEquals("عكس قيد إقفال الفترة " + originalClose.journalNumber + " — تصحيح خطأ في قيد الإقفال", reversal.description)
            assertEquals(originalClose, database.journalEntryDao().findById(closeId))
            assertEquals(originalCloseLines, database.journalLineDao().getForEntry(closeId))
            assertEquals(originalCloseLines.size, reversalLines.size)
            originalCloseLines.forEachIndexed { index, original ->
                val reversed = reversalLines[index]
                assertEquals(original.accountId, reversed.accountId)
                assertEquals(0, BigDecimal(original.debit).compareTo(BigDecimal(reversed.credit)))
                assertEquals(0, BigDecimal(original.credit).compareTo(BigDecimal(reversed.debit)))
            }

            assertTrue(!PeriodClosingRepository.isClosedDate(database.journalEntryDao().getAll(), "SYP", 1_700))
            assertTrue(PeriodClosingRepository.activeClosures(database.journalEntryDao().getAll()).isEmpty())
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    closingRepository.reopenPeriod(
                        closeId, "REOPEN-CLOSE-DUPLICATE", 3_001, "محاولة إعادة فتح مكررة"
                    )
                }
            }

            journals.postJournalEntry(
                "REOPEN-TEST-NEW-SALE", "TEST_SALE", "reopen-sale-2", "SYP", "1", 1_700,
                "مبيعات أضيفت بعد إعادة الفتح",
                listOf(
                    JournalLineDraft(accounts.getValue("1000").id, debit = "15"),
                    JournalLineDraft(accounts.getValue("4000").id, credit = "15")
                )
            )
            val reopenedStatements = GeneralLedgerFinancialStatementsCalculator.calculate(
                periodStartInclusive = 1_000,
                periodEndInclusive = 2_000,
                asOfInclusive = 3_000,
                accounts = database.accountDao().getAll(),
                entries = database.journalEntryDao().getAll(),
                lines = database.journalLineDao().getAll()
            )
            assertEquals(BigDecimal("90"), reopenedStatements.incomeStatements.single().netIncome)
            assertEquals(BigDecimal("90"), reopenedStatements.balanceSheets.single().retainedEarnings)

            val secondCloseId = closingRepository.closePeriod(
                1_000, 2_000, "SYP", accounts.getValue("3100").id, "CLOSE-REOPEN-002"
            )
            assertTrue(secondCloseId != closeId)
            val entriesAfterSecondClose = database.journalEntryDao().getAll()
            assertTrue(PeriodClosingRepository.isClosedDate(entriesAfterSecondClose, "SYP", 1_700))
            val activeClosures = PeriodClosingRepository.activeClosures(entriesAfterSecondClose)
            assertEquals(1, activeClosures.size)
            assertEquals(secondCloseId, activeClosures.single().closingEntry.id)

            val closedAgainStatements = GeneralLedgerFinancialStatementsCalculator.calculate(
                periodStartInclusive = 1_000,
                periodEndInclusive = 2_000,
                asOfInclusive = 3_000,
                accounts = database.accountDao().getAll(),
                entries = entriesAfterSecondClose,
                lines = database.journalLineDao().getAll()
            )
            assertEquals(BigDecimal("90"), closedAgainStatements.incomeStatements.single().netIncome)
            assertEquals(BigDecimal("90"), closedAgainStatements.balanceSheets.single().retainedEarnings)
            assertEquals(6, entriesAfterSecondClose.size)
        } finally {
            database.close()
        }
    }


    @Test
    fun reopeningEarlierPeriodKeepsLaterCloseActiveAndReclosePreservesCumulativeEarnings() = runBlocking {
        val database = newDatabase()
        try {
            val journals = JournalRepository(database)
            journals.ensureSystemAccounts()
            val accounts = database.accountDao().getAll().associateBy { it.code }
            val closingRepository = PeriodClosingRepository(database)

            suspend fun postSale(number: String, sourceId: String, occurredAt: Long, amount: String) {
                journals.postJournalEntry(
                    number, "TEST_SALE", sourceId, "SYP", "1", occurredAt,
                    "مبيعات اختبار تعدد الفترات",
                    listOf(
                        JournalLineDraft(accounts.getValue("1000").id, debit = amount),
                        JournalLineDraft(accounts.getValue("4000").id, credit = amount)
                    )
                )
            }
            suspend fun postExpense(number: String, sourceId: String, occurredAt: Long, amount: String) {
                journals.postJournalEntry(
                    number, "TEST_EXPENSE", sourceId, "SYP", "1", occurredAt,
                    "مصروف اختبار تعدد الفترات",
                    listOf(
                        JournalLineDraft(accounts.getValue("7000").id, debit = amount),
                        JournalLineDraft(accounts.getValue("1000").id, credit = amount)
                    )
                )
            }

            // First accounting period: profit 100 - 25 = 75.
            postSale("MULTI-P1-SALE", "multi-p1-sale", 1_100, "100")
            postExpense("MULTI-P1-EXP", "multi-p1-exp", 1_500, "25")
            val firstCloseId = closingRepository.closePeriod(
                1_000, 2_000, "SYP", accounts.getValue("3100").id, "MULTI-P1-CLOSE-1"
            )

            // Second period remains independently closed: profit 200 - 50 = 150.
            postSale("MULTI-P2-SALE", "multi-p2-sale", 3_100, "200")
            postExpense("MULTI-P2-EXP", "multi-p2-exp", 3_500, "50")
            val secondCloseId = closingRepository.closePeriod(
                3_000, 4_000, "SYP", accounts.getValue("3100").id, "MULTI-P2-CLOSE-1"
            )

            val beforeReopen = GeneralLedgerFinancialStatementsCalculator.calculate(
                periodStartInclusive = 1_000,
                periodEndInclusive = 4_000,
                asOfInclusive = 4_500,
                accounts = database.accountDao().getAll(),
                entries = database.journalEntryDao().getAll(),
                lines = database.journalLineDao().getAll()
            )
            assertEquals(BigDecimal("225"), beforeReopen.incomeStatements.single().netIncome)
            assertEquals(BigDecimal("225"), beforeReopen.balanceSheets.single().retainedEarnings)
            assertEquals(
                0,
                beforeReopen.balanceSheets.single().totalAssets.compareTo(
                    beforeReopen.balanceSheets.single().totalLiabilitiesAndEquity
                )
            )

            // Reopen only the first period. The reversal is dated after the second close,
            // so it does not create a posting inside the still-closed second period.
            closingRepository.reopenPeriod(
                closingEntryId = firstCloseId,
                reversalJournalNumber = "MULTI-P1-REOPEN",
                occurredAt = 5_000,
                reason = "تصحيح الفترة الأولى مع بقاء الثانية مغلقة"
            )
            val afterReopenEntries = database.journalEntryDao().getAll()
            assertTrue(!PeriodClosingRepository.isClosedDate(afterReopenEntries, "SYP", 1_700))
            assertTrue(PeriodClosingRepository.isClosedDate(afterReopenEntries, "SYP", 3_700))
            val activeAfterReopen = PeriodClosingRepository.activeClosures(afterReopenEntries)
            assertEquals(1, activeAfterReopen.size)
            assertEquals(secondCloseId, activeAfterReopen.single().closingEntry.id)

            // A backdated correction is now permitted in period one, but period two stays guarded.
            postSale("MULTI-P1-CORRECTION", "multi-p1-correction", 1_750, "30")
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    postSale("MULTI-P2-BLOCKED", "multi-p2-blocked", 3_750, "10")
                }
            }

            val reopenedStatements = GeneralLedgerFinancialStatementsCalculator.calculate(
                periodStartInclusive = 1_000,
                periodEndInclusive = 4_000,
                asOfInclusive = 5_000,
                accounts = database.accountDao().getAll(),
                entries = database.journalEntryDao().getAll(),
                lines = database.journalLineDao().getAll()
            )
            assertEquals(BigDecimal("255"), reopenedStatements.incomeStatements.single().netIncome)
            assertEquals(BigDecimal("255"), reopenedStatements.balanceSheets.single().retainedEarnings)
            assertEquals(
                0,
                reopenedStatements.balanceSheets.single().totalAssets.compareTo(
                    reopenedStatements.balanceSheets.single().totalLiabilitiesAndEquity
                )
            )

            // Reclose period one while period two remains closed and unchanged.
            val recloseId = closingRepository.closePeriod(
                1_000, 2_000, "SYP", accounts.getValue("3100").id, "MULTI-P1-CLOSE-2"
            )
            assertTrue(recloseId != firstCloseId)
            val entriesAfterReclose = database.journalEntryDao().getAll()
            val activeAfterReclose = PeriodClosingRepository.activeClosures(entriesAfterReclose)
            assertEquals(2, activeAfterReclose.size)
            assertTrue(PeriodClosingRepository.isClosedDate(entriesAfterReclose, "SYP", 1_700))
            assertTrue(PeriodClosingRepository.isClosedDate(entriesAfterReclose, "SYP", 3_700))

            val afterReclose = GeneralLedgerFinancialStatementsCalculator.calculate(
                periodStartInclusive = 1_000,
                periodEndInclusive = 4_000,
                asOfInclusive = 5_000,
                accounts = database.accountDao().getAll(),
                entries = entriesAfterReclose,
                lines = database.journalLineDao().getAll()
            )
            assertEquals(BigDecimal("255"), afterReclose.incomeStatements.single().netIncome)
            assertEquals(BigDecimal("255"), afterReclose.balanceSheets.single().retainedEarnings)
            assertEquals(
                0,
                afterReclose.balanceSheets.single().totalAssets.compareTo(
                    afterReclose.balanceSheets.single().totalLiabilitiesAndEquity
                )
            )
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