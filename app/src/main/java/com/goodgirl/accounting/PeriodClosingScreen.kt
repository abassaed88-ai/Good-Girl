package com.goodgirl.accounting

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.goodgirl.accounting.data.ActivePeriodClosure
import com.goodgirl.accounting.data.JournalRepository
import com.goodgirl.accounting.data.PeriodClosingRepository
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.domain.PeriodCloseDraft
import com.goodgirl.accounting.domain.PeriodClosingRules
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun PeriodClosingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val database = remember(context) { AccountingDatabase.getInstance(context) }
    val journalRepository = remember(database) { JournalRepository(database) }
    val closingRepository = remember(database) { PeriodClosingRepository(database) }
    val accounts by database.accountDao().observeAll().collectAsState(initial = emptyList())
    val journalEntries by database.journalEntryDao().observeAll().collectAsState(initial = emptyList())
    val journalLines by database.journalLineDao().observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val calendarNow = remember { Calendar.getInstance() }
    val initialStart = remember {
        (calendarNow.clone() as Calendar).apply {
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val initialEnd = remember {
        (calendarNow.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }
    var startAt by remember { mutableStateOf(initialStart) }
    var endAt by remember { mutableStateOf(initialEnd) }
    var currency by remember { mutableStateOf("SYP") }
    var equityAccountId by remember { mutableStateOf("SYS-3100") }
    var expandedAccounts by remember { mutableStateOf(false) }
    var showConfirmation by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<PeriodCloseDraft?>(null) }
    var previewError by remember { mutableStateOf<String?>(null) }
    var reopeningClosure by remember { mutableStateOf<ActivePeriodClosure?>(null) }
    var reopenReason by remember { mutableStateOf("") }
    var reopening by remember { mutableStateOf(false) }

    LaunchedEffect(journalRepository) {
        runCatching { journalRepository.ensureSystemAccounts() }
            .onFailure {
                message = it.message ?: "تعذر تجهيز دليل الحسابات"
                messageIsError = true
            }
    }

    val equityAccounts = accounts.filter { it.accountType == "EQUITY" && it.isActive }
    val selectedEquity = equityAccounts.firstOrNull { it.id == equityAccountId }
    val activeClosures = remember(journalEntries) {
        PeriodClosingRepository.activeClosures(journalEntries)
    }

    fun showDatePicker(initial: Long, onPicked: (Long) -> Unit, endOfDay: Boolean) {
        val selected = Calendar.getInstance().apply { timeInMillis = initial }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val chosen = Calendar.getInstance().apply {
                    set(year, month, day, if (endOfDay) 23 else 0, if (endOfDay) 59 else 0, if (endOfDay) 59 else 0)
                    set(Calendar.MILLISECOND, if (endOfDay) 999 else 0)
                }
                onPicked(chosen.timeInMillis)
            },
            selected.get(Calendar.YEAR),
            selected.get(Calendar.MONTH),
            selected.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }

    LaunchedEffect(startAt, endAt, currency, selectedEquity?.id, accounts, journalEntries, journalLines) {
        preview = null
        previewError = null
        val targetAccountId = selectedEquity?.id ?: return@LaunchedEffect
        if (startAt > endAt) {
            previewError = "تاريخ بداية الفترة يجب أن يسبق تاريخ نهايتها."
            return@LaunchedEffect
        }
        runCatching {
            PeriodClosingRules.build(
                periodStartInclusive = startAt,
                periodEndInclusive = endAt,
                currencyCode = currency,
                accounts = accounts,
                entries = journalEntries,
                lines = journalLines,
                retainedEarningsAccountId = targetAccountId
            )
        }.onSuccess { preview = it }
            .onFailure { previewError = it.message ?: "تعذر حساب نتيجة الفترة" }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("العودة إلى المحاسبة العامة") }
        Text("إغلاق الفترة المحاسبية", style = MaterialTheme.typography.headlineSmall)
        Text(
            "ينشئ التطبيق قيد إقفال دائم في دفتر اليومية، ويمنع تسجيل قيود لاحقة بتاريخ يقع داخل الفترة المغلقة. اختر نطاقاً مستقلاً لكل عملة.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("العملة", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { currency = "SYP" }, enabled = currency != "SYP") { Text("الليرة السورية") }
                    OutlinedButton(onClick = { currency = "USD" }, enabled = currency != "USD") { Text("الدولار") }
                }
                Text("بداية الفترة")
                OutlinedButton(
                    onClick = { showDatePicker(startAt, { startAt = it }, false) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(dateFormat.format(Date(startAt))) }
                Text("نهاية الفترة")
                OutlinedButton(
                    onClick = { showDatePicker(endAt, { endAt = it }, true) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(dateFormat.format(Date(endAt))) }
                Text("حساب ترحيل الربح أو الخسارة")
                OutlinedButton(
                    onClick = { expandedAccounts = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = equityAccounts.isNotEmpty()
                ) { Text(selectedEquity?.let { it.code + " — " + it.name } ?: "لا يوجد حساب حقوق ملكية") }
                DropdownMenu(expanded = expandedAccounts, onDismissRequest = { expandedAccounts = false }) {
                    equityAccounts.forEach { account ->
                        DropdownMenuItem(
                            text = { Text(account.code + " — " + account.name) },
                            onClick = {
                                equityAccountId = account.id
                                expandedAccounts = false
                            }
                        )
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("معاينة نتيجة الفترة", style = MaterialTheme.typography.titleMedium)
                when {
                    preview != null -> {
                        val result = preview!!
                        val amount = result.netIncome.abs().stripTrailingZeros().toPlainString() + " " + currency
                        Text(
                            when {
                                result.netIncome.signum() > 0 -> "صافي الربح: $amount"
                                result.netIncome.signum() < 0 -> "صافي الخسارة: $amount"
                                else -> "نتيجة الفترة: صفر"
                            },
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text("عدد أسطر قيد الإغلاق: " + result.lines.size)
                    }
                    previewError != null -> Text(previewError!!, color = MaterialTheme.colorScheme.error)
                    else -> Text("جارٍ حساب نتيجة الفترة...")
                }
                Text("تُعاد المحاسبة والتحققات من قاعدة البيانات لحظة تأكيد الإغلاق.")
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("قبل تنفيذ الإغلاق", style = MaterialTheme.typography.titleMedium)
                Text("• لا يمكن تكرار إقفال فترة نشطة أو إقفال فترة متداخلة معها.")
                Text("• لن تُقبل قيود جديدة بتاريخ داخل الفترة بعد إغلاقها.")
                Text("• تبقى حركات الفترات التالية متاحة بصورة طبيعية.")
                Text("• يمكن إعادة فتح الفترة لاحقاً بقيد عكسي مستقل مع تسجيل سبب الإعادة، دون حذف قيد الإقفال الأصلي.")
                Text("• احتفظ بنسخة احتياطية مشفرة قبل الإغلاق أو إعادة الفتح.")
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("الفترات المغلقة النشطة", style = MaterialTheme.typography.titleMedium)
                if (activeClosures.isEmpty()) {
                    Text("لا توجد فترات مغلقة حالياً.")
                } else {
                    activeClosures.forEach { closure ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    (if (closure.currencyCode == "SYP") "الليرة السورية" else "الدولار الأمريكي") +
                                        " — " + dateFormat.format(Date(closure.periodStartInclusive)) +
                                        " إلى " + dateFormat.format(Date(closure.periodEndInclusive)),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "قيد الإقفال: " + closure.closingEntry.journalNumber,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                enabled = !reopening && !saving,
                                onClick = {
                                    reopeningClosure = closure
                                    reopenReason = ""
                                    message = null
                                }
                            ) { Text("إعادة فتح") }
                        }
                    }
                }
            }
        }

        message?.let {
            Text(it, color = if (messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }

        Button(
            onClick = { showConfirmation = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = !saving && preview != null && selectedEquity != null && startAt <= endAt
        ) { Text(if (saving) "جارٍ الإغلاق..." else "مراجعة وتنفيذ الإغلاق") }
    }

    if (showConfirmation) {
        AlertDialog(
            onDismissRequest = { showConfirmation = false },
            title = { Text("تأكيد إغلاق الفترة") },
            text = {
                Text(
                    "سيتم ترحيل نتيجة الفترة من " + dateFormat.format(Date(startAt)) +
                        " إلى " + dateFormat.format(Date(endAt)) + " بعملة " + currency +
                        ". لا يمكن تكرار الإغلاق أو إضافة قيود لاحقة داخل هذا النطاق."
                )
            },
            confirmButton = {
                Button(
                    enabled = !saving,
                    onClick = {
                        showConfirmation = false
                        saving = true
                        message = null
                        scope.launch {
                            runCatching {
                                closingRepository.closePeriod(
                                    periodStartInclusive = startAt,
                                    periodEndInclusive = endAt,
                                    currencyCode = currency,
                                    retainedEarningsAccountId = selectedEquity!!.id,
                                    journalNumber = "CLOSE-$currency-$startAt-${UUID.randomUUID().toString().take(8)}"
                                )
                            }.onSuccess {
                                message = "تم إغلاق الفترة وترحيل قيد الإغلاق إلى دفتر اليومية بنجاح."
                                messageIsError = false
                            }.onFailure {
                                message = it.message ?: "تعذر إغلاق الفترة"
                                messageIsError = true
                            }
                            saving = false
                        }
                    }
                ) { Text("تأكيد الإغلاق") }
            },
            dismissButton = { TextButton(onClick = { showConfirmation = false }) { Text("إلغاء") } }
        )
    }


    if (reopeningClosure != null) {
        val target = reopeningClosure!!
        AlertDialog(
            onDismissRequest = { if (!reopening) reopeningClosure = null },
            title = { Text("تأكيد إعادة فتح الفترة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "سيُنشأ قيد عكسي مستقل لقيد الإقفال " + target.closingEntry.journalNumber +
                            " عن الفترة من " + dateFormat.format(Date(target.periodStartInclusive)) +
                            " إلى " + dateFormat.format(Date(target.periodEndInclusive)) +
                            ". لن يُحذف القيد الأصلي."
                    )
                    OutlinedTextField(
                        value = reopenReason,
                        onValueChange = { reopenReason = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("سبب إعادة الفتح") },
                        minLines = 2,
                        enabled = !reopening
                    )
                    Text(
                        "سيُسجّل القيد العكسي بتاريخ يلي نهاية الفترة المغلقة.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !reopening && reopenReason.isNotBlank(),
                    onClick = {
                        val cleanReason = reopenReason.trim()
                        reopening = true
                        message = null
                        scope.launch {
                            runCatching {
                                require(target.periodEndInclusive < Long.MAX_VALUE) {
                                    "لا يمكن إعادة فتح فترة يتجاوز تاريخ نهايتها الحد المدعوم"
                                }
                                val reversalAt = maxOf(
                                    System.currentTimeMillis(),
                                    target.periodEndInclusive + 1L
                                )
                                closingRepository.reopenPeriod(
                                    closingEntryId = target.closingEntry.id,
                                    reversalJournalNumber = "REOPEN-${target.closingEntry.id}",
                                    occurredAt = reversalAt,
                                    reason = cleanReason
                                )
                            }.onSuccess {
                                message = "تمت إعادة فتح الفترة بقيد عكسي موثق، وبقي قيد الإقفال الأصلي محفوظاً."
                                messageIsError = false
                                reopeningClosure = null
                                reopenReason = ""
                            }.onFailure {
                                message = it.message ?: "تعذرت إعادة فتح الفترة"
                                messageIsError = true
                            }
                            reopening = false
                        }
                    }
                ) { Text(if (reopening) "جارٍ تسجيل القيد العكسي..." else "تأكيد إعادة الفتح") }
            },
            dismissButton = {
                TextButton(
                    enabled = !reopening,
                    onClick = { reopeningClosure = null }
                ) { Text("إلغاء") }
            }
        )
    }
}
