package com.goodgirl.accounting.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.AccountEntity
import com.goodgirl.accounting.domain.JournalLineDraft
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
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
            assertEquals("SYS-4100", accounts.getValue("4100").id)
            assertEquals("REVENUE", accounts.getValue("4100").accountType)
            assertEquals("SYS-7100", accounts.getValue("7100").id)
            assertEquals("EXPENSE", accounts.getValue("7100").accountType)

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
            assertEquals(1, repository.observeEntries().first().size)
        } finally {
            database.close()
        }
    }

    @Test
    fun systemAccountProvisioningNeverReplacesAnExistingCustomRevaluationCode() = runBlocking {
        val database = newDatabase()
        try {
            database.accountDao().insert(
                AccountEntity(
                    id = "CUSTOM-4100",
                    code = "4100",
                    name = "حساب مخصص قديم",
                    accountType = "ASSET",
                    normalSide = "DEBIT",
                    isSystem = false,
                    isActive = true,
                    createdAt = 1L
                )
            )
            JournalRepository(database).ensureSystemAccounts()

            val existing = database.accountDao().findByCode("4100")
            assertEquals("CUSTOM-4100", existing!!.id)
            assertEquals("حساب مخصص قديم", existing.name)
            assertEquals("SYS-7100", database.accountDao().findByCode("7100")!!.id)
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
            assertTrue(repository.observeEntries().first().isEmpty())
        } finally {
            database.close()
        }
    }


    @Test
    fun manualJournalLineArabicMemoSurvivesDatabaseReopen() = runBlocking {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "journal-memo-reopen-" + java.util.UUID.randomUUID() + ".db"
        var database: AccountingDatabase? = null
        var reopenedDatabase: AccountingDatabase? = null

        try {
            database = Room.databaseBuilder(context, AccountingDatabase::class.java, databaseName)
                .allowMainThreadQueries()
                .build()
            val repository = JournalRepository(database)
            repository.ensureSystemAccounts()
            val accounts = database.accountDao().getAll().associateBy { it.code }

            val entryId = repository.postJournalEntry(
                journalNumber = "JE-MEMO-REOPEN",
                sourceType = "MANUAL_JOURNAL",
                sourceId = "manual-memo-reopen",
                currencyCode = "SYP",
                exchangeRateSnapshot = "1",
                occurredAt = 3_000L,
                description = "اختبار حفظ بيان عربي",
                lines = listOf(
                    JournalLineDraft(
                        accountId = accounts.getValue("1000").id,
                        debit = "25",
                        memo = "عطر فاخر ورد"
                    ),
                    JournalLineDraft(
                        accountId = accounts.getValue("4000").id,
                        credit = "25",
                        memo = "ملاحظة عربية"
                    )
                )
            )

            database.close()
            database = null

            reopenedDatabase = Room.databaseBuilder(
                context,
                AccountingDatabase::class.java,
                databaseName
            ).allowMainThreadQueries().build()

            val restoredEntry = reopenedDatabase.journalEntryDao().findById(entryId)
            assertTrue("The posted journal should exist after reopening the database", restoredEntry != null)
            assertEquals("اختبار حفظ بيان عربي", restoredEntry!!.description)

            val restoredLines = JournalRepository(reopenedDatabase).getLines(entryId)
            assertEquals(2, restoredLines.size)
            assertEquals("عطر فاخر ورد", restoredLines[0].memo)
            assertEquals("ملاحظة عربية", restoredLines[1].memo)
        } finally {
            database?.close()
            reopenedDatabase?.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun newDatabase(): AccountingDatabase {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        return Room.inMemoryDatabaseBuilder(context, AccountingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }
}
