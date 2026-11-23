package com.goodgirl.accounting.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.domain.JournalLineDraft
import java.math.BigDecimal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JournalReversalIntegrationTest {
    @Test
    fun reversalPostsExactOppositeLinesAndPreservesOriginalEntry() = runBlocking {
        val database = newDatabase()
        try {
            val repository = JournalRepository(database)
            repository.ensureSystemAccounts()
            val accounts = database.accountDao().getAll().associateBy { it.code }

            val originalId = repository.postJournalEntry(
                journalNumber = "JE-ORIGINAL-001",
                sourceType = "TEST_MANUAL",
                sourceId = "manual-001",
                currencyCode = "SYP",
                exchangeRateSnapshot = "1",
                occurredAt = 1_000L,
                description = "قيد أصلي للاختبار",
                lines = listOf(
                    JournalLineDraft(accounts.getValue("1000").id, debit = "60", memo = "نقدية"),
                    JournalLineDraft(accounts.getValue("1100").id, debit = "40", memo = "ذمم"),
                    JournalLineDraft(accounts.getValue("4000").id, credit = "100", memo = "إيراد")
                )
            )
            val originalBefore = database.journalEntryDao().findById(originalId)!!
            val originalLinesBefore = repository.getLines(originalId)

            val reversalId = repository.reverseJournalEntry(
                originalEntryId = originalId,
                reversalJournalNumber = "JE-REVERSAL-001",
                occurredAt = 2_000L,
                reason = "تصحيح عملية الاختبار"
            )

            val originalAfter = database.journalEntryDao().findById(originalId)!!
            val originalLinesAfter = repository.getLines(originalId)
            val reversal = database.journalEntryDao().findById(reversalId)!!
            val reversalLines = repository.getLines(reversalId)

            assertEquals(originalBefore, originalAfter)
            assertEquals(originalLinesBefore, originalLinesAfter)
            assertEquals("REVERSAL", reversal.sourceType)
            assertEquals(originalId, reversal.sourceId)
            assertEquals("SYP", reversal.currencyCode)
            assertEquals("1", reversal.exchangeRateSnapshot)
            assertEquals(3, reversalLines.size)

            val originalByAccount = originalLinesBefore.associateBy { it.accountId }
            reversalLines.forEach { reversed ->
                val source = originalByAccount.getValue(reversed.accountId)
                assertEquals(0, BigDecimal(source.debit).compareTo(BigDecimal(reversed.credit)))
                assertEquals(0, BigDecimal(source.credit).compareTo(BigDecimal(reversed.debit)))
            }
            val totalDebit = reversalLines.fold(BigDecimal.ZERO) { sum, line ->
                sum.add(BigDecimal(line.debit))
            }
            val totalCredit = reversalLines.fold(BigDecimal.ZERO) { sum, line ->
                sum.add(BigDecimal(line.credit))
            }
            assertEquals(0, totalDebit.compareTo(BigDecimal("100")))
            assertEquals(0, totalCredit.compareTo(BigDecimal("100")))
        } finally {
            database.close()
        }
    }

    @Test
    fun preventsDuplicateReversalAndReversalOfAReversal() = runBlocking {
        val database = newDatabase()
        try {
            val repository = JournalRepository(database)
            repository.ensureSystemAccounts()
            val accounts = database.accountDao().getAll().associateBy { it.code }
            val originalId = repository.postJournalEntry(
                journalNumber = "JE-ORIGINAL-002",
                sourceType = "TEST_MANUAL",
                sourceId = "manual-002",
                currencyCode = "USD",
                exchangeRateSnapshot = "15000",
                occurredAt = 1_000L,
                description = "قيد أصلي ثانٍ",
                lines = listOf(
                    JournalLineDraft(accounts.getValue("1000").id, debit = "12"),
                    JournalLineDraft(accounts.getValue("4000").id, credit = "12")
                )
            )

            val reversalId = repository.reverseJournalEntry(
                originalEntryId = originalId,
                reversalJournalNumber = "JE-REVERSAL-002",
                occurredAt = 2_000L,
                reason = "اختبار منع التكرار"
            )

            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    repository.reverseJournalEntry(
                        originalEntryId = originalId,
                        reversalJournalNumber = "JE-REVERSAL-003",
                        occurredAt = 3_000L,
                        reason = "محاولة عكس مكرر"
                    )
                }
            }
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    repository.reverseJournalEntry(
                        originalEntryId = reversalId,
                        reversalJournalNumber = "JE-REVERSAL-004",
                        occurredAt = 3_000L,
                        reason = "محاولة عكس القيد العكسي"
                    )
                }
            }
            assertEquals(2, repository.observeEntries().first().size)
            assertTrue(database.journalEntryDao().findBySource("REVERSAL", originalId) != null)
        } finally {
            database.close()
        }
    }

    @Test
    fun rejectsReversalBeforeOriginalDateAndBlankReason() = runBlocking {
        val database = newDatabase()
        try {
            val repository = JournalRepository(database)
            repository.ensureSystemAccounts()
            val accounts = database.accountDao().getAll().associateBy { it.code }
            val originalId = repository.postJournalEntry(
                journalNumber = "JE-ORIGINAL-003",
                sourceType = "TEST_MANUAL",
                sourceId = "manual-003",
                currencyCode = "SYP",
                exchangeRateSnapshot = "1",
                occurredAt = 5_000L,
                description = "قيد أصلي ثالث",
                lines = listOf(
                    JournalLineDraft(accounts.getValue("1000").id, debit = "5"),
                    JournalLineDraft(accounts.getValue("4000").id, credit = "5")
                )
            )

            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    repository.reverseJournalEntry(
                        originalEntryId = originalId,
                        reversalJournalNumber = "JE-REVERSAL-005",
                        occurredAt = 4_999L,
                        reason = "تاريخ غير صالح"
                    )
                }
            }
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    repository.reverseJournalEntry(
                        originalEntryId = originalId,
                        reversalJournalNumber = "JE-REVERSAL-006",
                        occurredAt = 6_000L,
                        reason = "   "
                    )
                }
            }
            assertEquals(1, repository.observeEntries().first().size)
        } finally {
            database.close()
        }
    }

    private fun newDatabase(): AccountingDatabase {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        return Room.inMemoryDatabaseBuilder(context, AccountingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }
}
