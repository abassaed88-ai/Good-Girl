package com.goodgirl.accounting.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goodgirl.accounting.data.local.AccountingDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

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
