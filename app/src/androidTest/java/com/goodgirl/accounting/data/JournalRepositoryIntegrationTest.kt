package com.goodgirl.accounting.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.domain.JournalLineDraft
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JournalRepositoryIntegrationTest {
    @Test
    fun postsBalancedJournalAndPreventsDuplicateSourcePosting() = runBlocking {
        val database = newDatabase()
        try {
            val repository = JournalRepository(database)
            repository.ensureSystemAccounts()
            val accounts = database.accountDao().getAll().associateBy { it.code }

            val entryId = repository.postJournalEntry(
                journalNumber = "JE-TEST-001",
                sourceType = "TEST_SALE",
                sourceId = "sale-001",
                currencyCode = "SYP",
                exchangeRateSnapshot = "1",
                occurredAt = 1_000L,
                description = "قيد اختبار متوازن",
                lines = listOf(
                    JournalLineDraft(accounts.getValue("1000").id, debit = "60"),
                    JournalLineDraft(accounts.getValue("1100").id, debit = "40"),
                    JournalLineDraft(accounts.getValue("4000").id, credit = "100")
                )
            )

            val entry = database.journalEntryDao().findById(entryId)
            assertTrue(entry != null)
            assertEquals("SYP", entry!!.currencyCode)
            val lines = repository.getLines(entryId)
            assertEquals(3, lines.size)
            assertEquals("100", lines.fold(java.math.BigDecimal.ZERO) { total, line ->
                total.add(line.debit.toBigDecimal())
            }.stripTrailingZeros().toPlainString())
            assertEquals("100", lines.fold(java.math.BigDecimal.ZERO) { total, line ->
                total.add(line.credit.toBigDecimal())
            }.stripTrailingZeros().toPlainString())

            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    repository.postJournalEntry(
                        journalNumber = "JE-TEST-002",
                        sourceType = "TEST_SALE",
                        sourceId = "sale-001",
                        currencyCode = "SYP",
                        exchangeRateSnapshot = "1",
                        occurredAt = 2_000L,
                        description = "محاولة ترحيل مكرر",
                        lines = listOf(
                            JournalLineDraft(accounts.getValue("1000").id, debit = "1"),
                            JournalLineDraft(accounts.getValue("4000").id, credit = "1")
                        )
                    )
                }
            }
            assertEquals(1, database.journalEntryDao().observeAll().let { flow ->
                kotlinx.coroutines.flow.first(flow).size
            })
        } finally {
            database.close()
        }
    }

    @Test
    fun rejectsUnbalancedJournalWithoutPersistingAnyPartOfIt() = runBlocking {
        val database = newDatabase()
        try {
            val repository = JournalRepository(database)
            repository.ensureSystemAccounts()
            val accounts = database.accountDao().getAll().associateBy { it.code }

            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    repository.postJournalEntry(
                        journalNumber = "JE-TEST-BAD",
                        sourceType = "TEST",
                        sourceId = "unbalanced",
                        currencyCode = "USD",
                        exchangeRateSnapshot = "15000",
                        occurredAt = 1_000L,
                        description = "قيد غير متوازن",
                        lines = listOf(
                            JournalLineDraft(accounts.getValue("1000").id, debit = "10"),
                            JournalLineDraft(accounts.getValue("4000").id, credit = "9")
                        )
                    )
                }
            }
            assertTrue(kotlinx.coroutines.flow.first(repository.observeEntries()).isEmpty())
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
