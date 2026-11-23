package com.goodgirl.accounting

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goodgirl.accounting.data.AccountingBackupManager
import com.goodgirl.accounting.data.EncryptedBackupCodec
import com.goodgirl.accounting.data.local.AccountingDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private val BackupWarningTextColor = Color(0xFFFFC078)
private val BackupSuccessTextColor = Color(0xFF78C9A0)
private val BackupErrorTextColor = Color(0xFFE58A9B)

@Composable
internal fun EncryptedBackupManagementScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val database = remember(context) { AccountingDatabase.getInstance(context) }
    val manager = remember(context, database) { AccountingBackupManager(context, database) }
    val scope = rememberCoroutineScope()

    var isBusy by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    var exportPassword by remember { mutableStateOf("") }
    var confirmExportPassword by remember { mutableStateOf("") }
    var restorePassword by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    val exportReady = exportPassword.length >= EncryptedBackupCodec.MIN_PASSWORD_LENGTH &&
        exportPassword == confirmExportPassword

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri == null) {
            // Do not keep a password in Compose state when the user cancels the picker.
            exportPassword = ""
            confirmExportPassword = ""
        } else {
            scope.launch {
                isBusy = true
                message = null
                try {
                    val result = manager.exportBackup(uri, exportPassword.toCharArray())
                    message = "تم إنشاء نسخة احتياطية مشفّرة بنجاح. عدد السجلات: " +
                        result.recordCount
                } catch (error: Exception) {
                    message = error.message ?: "تعذر إنشاء النسخة الاحتياطية"
                } finally {
                    isBusy = false
                    exportPassword = ""
                    confirmExportPassword = ""
                }
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            // Clear typed credentials on cancellation as well as after an attempt.
            restorePassword = ""
            confirmRestore = false
        } else {
            scope.launch {
                isBusy = true
                message = null
                try {
                    val result = manager.restoreBackup(uri, restorePassword.toCharArray())
                    message = if (result.wasLegacyUnencrypted) {
                        "تمت استعادة النسخة القديمة. تنبيه: الملف القديم غير مشفّر؛ أنشئ نسخة جديدة مشفّرة."
                    } else {
                        "تمت استعادة النسخة المشفّرة بنجاح. عدد السجلات المستعادة: " +
                            result.recordCount
                    }
                    confirmRestore = false
                } catch (error: Exception) {
                    message = error.message ?: "تعذرت استعادة النسخة الاحتياطية"
                } finally {
                    isBusy = false
                    restorePassword = ""
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TextButton(onClick = onBack, enabled = !isBusy) {
            Text("العودة إلى لوحة التحكم")
        }
        Text(
            "النسخ الاحتياطي والاستعادة",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("إنشاء نسخة احتياطية مشفّرة", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    "تُشفّر النسخة قبل حفظها في ملف GGBackup. اختر كلمة مرور لا تقل عن 8 أحرف، واحفظها بمكان آمن منفصل عن الملف. لا يمكن استعادة النسخة المشفّرة من دونها.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = exportPassword,
                    onValueChange = { exportPassword = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("كلمة مرور النسخة") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !isBusy
                )
                OutlinedTextField(
                    value = confirmExportPassword,
                    onValueChange = { confirmExportPassword = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("تأكيد كلمة المرور") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !isBusy
                )
                Button(
                    onClick = {
                        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                        exportLauncher.launch("GoodGirl_Backup_" + stamp + ".ggbackup")
                    },
                    enabled = !isBusy && exportReady,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("تشفير النسخة وحفظها")
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("استعادة نسخة احتياطية", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    "الاستعادة تستبدل البيانات الحالية بالكامل ببيانات الملف المختار، ولا يمكن التراجع عنها من داخل التطبيق.",
                    color = BackupWarningTextColor,
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = restorePassword,
                    onValueChange = { restorePassword = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("كلمة مرور النسخة المشفّرة") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !isBusy
                )
                Text(
                    "اترك كلمة المرور فارغة فقط عند استيراد ملف JSON قديم غير مشفّر.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                Checkbox(
                    checked = confirmRestore,
                    onCheckedChange = { confirmRestore = it },
                    enabled = !isBusy
                )
                Text("أفهم أن الاستعادة ستحل محل جميع البيانات الحالية.")
                Button(
                    onClick = {
                        restoreLauncher.launch(
                            arrayOf(
                                "application/octet-stream",
                                "application/json",
                                "text/json",
                                "*/*"
                            )
                        )
                    },
                    enabled = !isBusy && confirmRestore,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("اختيار ملف واستعادة البيانات")
                }
            }
        }

        Text(
            "ملفات JSON القديمة ما زال يمكن استيرادها، لكنها غير مشفّرة. بعد استعادتها أنشئ نسخة جديدة مشفّرة.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
        if (isBusy) {
            Text("جارٍ تنفيذ العملية...", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
        message?.let {
            Text(
                it,
                color = if (it.startsWith("تم")) BackupSuccessTextColor else BackupErrorTextColor,
                fontSize = 13.sp
            )
        }
    }
}
