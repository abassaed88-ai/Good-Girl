package com.goodgirl.accounting.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goodgirl.accounting.data.local.AccountingDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Ensures backup credentials are cleared even when failure happens before crypto starts. */
@RunWith(AndroidJUnit4::class)
class BackupPasswordCleanupIntegrationTest {

    @Test
    fun exportClearsPasswordWhenDatabaseSnapshotCannotBeRead() = runBlocking {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(
            context,
            AccountingDatabase::class.java
        ).allowMainThreadQueries().build()
        val manager = AccountingBackupManager(context, database)
        val password = "Export-Backup-Password".toCharArray()

        database.close()
        val error = runCatching {
            manager.exportBackup(Uri.parse("content://invalid.backup.provider/export"), password)
        }.exceptionOrNull()

        assertNotNull("Export should fail after the database is closed", error)
        assertArrayEquals(CharArray(password.size) { '\u0000' }, password)
    }

    @Test
    fun invalidBackupDoesNotReplaceExistingDatabaseRecords() = runBlocking {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(
            context,
            AccountingDatabase::class.java
        ).allowMainThreadQueries().build()
        val backupFile = File.createTempFile("good-girl-invalid-", ".json", context.cacheDir)
        try {
            ProductRepository(database).addProduct(
                sku = "BACKUP-SAFE-001",
                name = "بيانات يجب الحفاظ عليها",
                unit = "قطعة",
                purchaseCost = "2",
                salePrice = "4",
                openingStock = "3",
                note = "اختبار حماية الاستعادة"
            )
            backupFile.writeText("""{"format":"not-a-good-girl-backup"}""")
            val manager = AccountingBackupManager(context, database)

            val error = runCatching {
                manager.restoreBackup(Uri.fromFile(backupFile), charArrayOf())
            }.exceptionOrNull()

            assertNotNull("An unrelated JSON file must be rejected", error)
            assertEquals("بيانات يجب الحفاظ عليها", database.productDao().getAll().single().name)
        } finally {
            backupFile.delete()
            database.close()
        }
    }

    @Test
    fun restoreClearsPasswordWhenBackupFileCannotBeOpened() = runBlocking {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(
            context,
            AccountingDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val manager = AccountingBackupManager(context, database)
            val password = "Restore-Backup-Password".toCharArray()

            val error = runCatching {
                manager.restoreBackup(
                    Uri.parse("content://missing.backup.provider/no-such-file"),
                    password
                )
            }.exceptionOrNull()

            assertNotNull("Restore should fail when the content provider is missing", error)
            assertArrayEquals(CharArray(password.size) { '\u0000' }, password)
        } finally {
            database.close()
        }
    }
}
