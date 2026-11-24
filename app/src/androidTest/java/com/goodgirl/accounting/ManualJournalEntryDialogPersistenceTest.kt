package com.goodgirl.accounting

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goodgirl.accounting.data.JournalRepository
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.domain.JournalLineDraft
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManualJournalEntryDialogPersistenceTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun arabicMemosEnteredInManualJournalDialogSurviveSaveAndDatabaseReopen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "journal-dialog-reopen-" + java.util.UUID.randomUUID() + ".db"
        val database = Room.databaseBuilder(context, AccountingDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()
        var reopenedDatabase: AccountingDatabase? = null

        try {
            val repository = JournalRepository(database)
            runBlocking { repository.ensureSystemAccounts() }
            val accounts = runBlocking { database.accountDao().getAll() }.filter { it.isActive }
            val saveRequest = AtomicReference<PendingJournalSave?>(null)

            composeRule.setContent {
                MaterialTheme {
                    ManualJournalEntryDialog(
                        accounts = accounts,
                        onDismiss = {},
                        onSave = { number, description, currency, rate, lines ->
                            saveRequest.set(
                                PendingJournalSave(number, description, currency, rate, lines)
                            )
                        }
                    )
                }
            }

            composeRule.onNodeWithTag("manual-journal-description")
                .performScrollTo()
                .performTextInput("قيد اختبار من الواجهة")
            composeRule.onNodeWithText("✓ ليرة سورية").performClick()

            selectAccountRow(index = 0, accountCode = "1000")
            selectAccountRow(index = 1, accountCode = "4000")

            composeRule.onNodeWithTag("manual-journal-debit-0")
                .performScrollTo()
                .performTextInput("25")
            composeRule.onNodeWithTag("manual-journal-credit-1")
                .performScrollTo()
                .performTextInput("25")

            enterArabicText("manual-journal-memo-0", "عطر فاخر ورد")
            enterArabicText("manual-journal-memo-1", "ملاحظة عربية")

            composeRule.onNodeWithTag("manual-journal-submit")
                .performScrollTo()
                .performClick()
            composeRule.waitForIdle()

            val pending = saveRequest.get()
            assertNotNull("The dialog should pass its draft to the save callback", pending)
            val request = pending!!
            assertEquals("قيد اختبار من الواجهة", request.description)
            assertEquals("SYP", request.currencyCode)
            assertEquals("1", request.exchangeRate)
            assertEquals("عطر فاخر ورد", request.lines[0].memo)
            assertEquals("ملاحظة عربية", request.lines[1].memo)

            val entryId = runBlocking {
                repository.postJournalEntry(
                    journalNumber = request.number,
                    sourceType = "MANUAL_JOURNAL",
                    sourceId = "MANUAL-" + request.number,
                    currencyCode = request.currencyCode,
                    exchangeRateSnapshot = request.exchangeRate,
                    occurredAt = System.currentTimeMillis(),
                    description = request.description,
                    lines = request.lines
                )
            }

            database.close()
            reopenedDatabase = Room.databaseBuilder(
                context,
                AccountingDatabase::class.java,
                databaseName
            ).allowMainThreadQueries().build()

            val restoredEntry = runBlocking {
                reopenedDatabase!!.journalEntryDao().findById(entryId)
            }
            assertNotNull("The posted journal should still exist after reopening", restoredEntry)
            assertEquals("قيد اختبار من الواجهة", restoredEntry!!.description)

            val restoredLines = runBlocking {
                JournalRepository(reopenedDatabase!!).getLines(entryId)
            }
            assertEquals(2, restoredLines.size)
            assertEquals("عطر فاخر ورد", restoredLines[0].memo)
            assertEquals("ملاحظة عربية", restoredLines[1].memo)
        } finally {
            database.close()
            reopenedDatabase?.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun selectAccountRow(index: Int, accountCode: String) {
        composeRule.onNodeWithTag("manual-journal-account-$index")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("manual-journal-account-option-$accountCode")
            .performClick()
    }

    private fun enterArabicText(tag: String, expected: String) {
        val field = composeRule.onNodeWithTag(tag)
        field.performScrollTo()
        var entered = ""
        expected.forEach { character ->
            entered += character
            field.performTextInput(character.toString())
            assertEditableText(tag, entered)
        }
    }

    private fun assertEditableText(tag: String, expected: String) {
        composeRule.waitForIdle()
        val actual = composeRule.onNodeWithTag(tag)
            .fetchSemanticsNode()
            .config[SemanticsProperties.EditableText]
            .text
        assertEquals("Arabic text should render immediately in $tag", expected, actual)
    }

    private data class PendingJournalSave(
        val number: String,
        val description: String,
        val currencyCode: String,
        val exchangeRate: String,
        val lines: List<JournalLineDraft>
    )
}
