package com.goodgirl.accounting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.goodgirl.accounting.data.JournalRepository
import com.goodgirl.accounting.data.local.AccountEntity
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.JournalEntryEntity
import com.goodgirl.accounting.data.local.JournalLineEntity
import com.goodgirl.accounting.domain.JournalLineDraft
import com.goodgirl.accounting.domain.AccountLedgerCalculator
import com.goodgirl.accounting.domain.AccountInputRules
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.launch

private val AccountingAccent get() = BrandBlue
private val AccountingSurface get() = CardSurface
private val AccountingText get() = Ink
private val AccountingMuted get() = MutedText
private val AccountingGood get() = SoftGreen
private val AccountingBad get() = SoftRed

@Composable
fun GeneralAccountingHubScreen(
    onBack: () -> Unit,
    onOpenChart: () -> Unit,
    onOpenJournal: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("العودة إلى لوحة التحكم") }
        Text("المحاسبة العامة", style = MaterialTheme.typography.headlineSmall, color = AccountingText, fontWeight = FontWeight.Bold)
        Text(
            "إدارة دليل الحسابات وقيود اليومية من مصدر محاسبي واحد.",
            color = AccountingMuted,
            fontSize = 13.sp
        )
        AccountingNavigationCard(
            title = "دليل الحسابات",
            description = "عرض الحسابات الافتراضية وإضافة حسابات المتجر وتصنيفها.",
            onClick = onOpenChart
        )
        AccountingNavigationCard(
            title = "دفتر اليومية العام",
            description = "مراجعة القيود المرحلة وإدخال قيود يدوية متوازنة.",
            onClick = onOpenJournal
        )
        Text(
            "تُحفظ القيود المرحلة ولا تُعدّل مباشرة. تصحيح القيد المحاسبي يجب أن يتم بقيد عكسي مستقل.",
            color = AccountingMuted,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun AccountingNavigationCard(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AccountingSurface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, color = AccountingAccent, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(description, color = AccountingMuted, fontSize = 13.sp)
        }
    }
}

@Composable
fun ChartOfAccountsScreen(
    onBack: () -> Unit,
    onOpenLedger: (String) -> Unit
) {
    val context = LocalContext.current
    val database = remember(context) { AccountingDatabase.getInstance(context) }
    val repository = remember(database) { JournalRepository(database) }
    val accounts by database.accountDao().observeAll().collectAsState(initial = emptyList())
    val entries by database.journalEntryDao().observeAll().collectAsState(initial = emptyList())
    val lines by database.journalLineDao().observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }
    var screenMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(repository) {
        runCatching { repository.ensureSystemAccounts() }
            .onFailure { screenMessage = it.message ?: "تعذر تجهيز الحسابات الافتراضية" }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TextButton(onClick = onBack) { Text("العودة إلى المحاسبة العامة") }
        Text("دليل الحسابات", style = MaterialTheme.typography.headlineSmall, color = AccountingText, fontWeight = FontWeight.Bold)
        Text("عدد الحسابات: " + accounts.size, color = AccountingMuted, fontSize = 13.sp)
        Button(onClick = { showAddDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text("إضافة حساب جديد")
        }
        screenMessage?.let { Text(it, color = AccountingBad, fontSize = 12.sp) }

        if (accounts.isEmpty()) {
            Text("لا توجد حسابات مسجلة حتى الآن.", color = AccountingMuted)
        } else {
            val entriesById = entries.associateBy { it.id }
            accounts.forEach { account ->
                val accountLines = lines.filter { it.accountId == account.id }
                val currencyBalances = accountLines
                    .mapNotNull { line -> entriesById[line.journalEntryId]?.currencyCode?.let { it to line } }
                    .groupBy({ it.first }, { it.second })
                    .mapValues { (_, currencyLines) ->
                        currencyLines.fold(BigDecimal.ZERO) { total, line ->
                            total.add(line.debit.toBigDecimal()).subtract(line.credit.toBigDecimal())
                        }
                    }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onOpenLedger(account.id) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = AccountingSurface)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(account.code + " — " + account.name, color = AccountingText, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(
                                when {
                                    !account.isActive -> "موقوف"
                                    account.isSystem -> "أساسي"
                                    else -> "مخصص"
                                },
                                color = if (account.isActive) AccountingAccent else AccountingBad,
                                fontSize = 11.sp
                            )
                        }
                        Text(accountTypeArabic(account.accountType) + " · " + if (account.normalSide == "DEBIT") "طبيعته مدينة" else "طبيعته دائنة", color = AccountingMuted, fontSize = 12.sp)
                        if (currencyBalances.isEmpty()) {
                            Text("لا توجد حركات مسجلة", color = AccountingMuted, fontSize = 12.sp)
                        } else {
                            currencyBalances.toSortedMap().forEach { (currency, balance) ->
                                val normalBalance = if (account.normalSide == "DEBIT") balance else balance.negate()
                                val balanceDirection = when {
                                    normalBalance.signum() == 0 -> "متوازن"
                                    normalBalance.signum() > 0 && account.normalSide == "DEBIT" -> "مدين"
                                    normalBalance.signum() > 0 -> "دائن"
                                    account.normalSide == "DEBIT" -> "دائن"
                                    else -> "مدين"
                                }
                                Text(
                                    "الرصيد: " + normalBalance.abs().stripTrailingZeros().toPlainString() + " " + currency + " (" + balanceDirection + ")",
                                    color = AccountingMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        if (!account.isSystem) {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        runCatching {
                                            database.accountDao().update(account.copy(isActive = !account.isActive))
                                        }.onSuccess {
                                            screenMessage = if (account.isActive) "تم إيقاف الحساب عن القيود الجديدة." else "تم تفعيل الحساب."
                                        }.onFailure {
                                            screenMessage = it.message ?: "تعذر تحديث حالة الحساب"
                                        }
                                    }
                                }
                            ) {
                                Text(if (account.isActive) "إيقاف الحساب" else "تفعيل الحساب", color = if (account.isActive) AccountingBad else AccountingGood)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddLedgerAccountDialog(
            onDismiss = { showAddDialog = false },
            onSave = { code, name, type ->
                scope.launch {
                    runCatching {
                        val normalized = AccountInputRules.normalize(code, name, type)
                        require(database.accountDao().findByCode(normalized.code) == null) { "رمز الحساب مستخدم مسبقاً" }
                        database.accountDao().insert(
                            AccountEntity(
                                id = UUID.randomUUID().toString(),
                                code = normalized.code,
                                name = normalized.name,
                                accountType = normalized.accountType,
                                normalSide = normalized.normalSide,
                                isSystem = false,
                                isActive = true,
                                createdAt = System.currentTimeMillis()
                            )
                        )
                    }.onSuccess {
                        showAddDialog = false
                        screenMessage = "تمت إضافة الحساب بنجاح."
                    }.onFailure {
                        screenMessage = it.message ?: "تعذرت إضافة الحساب"
                    }
                }
            }
        )
    }
}

@Composable
private fun AddLedgerAccountDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("ASSET") }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDiscardConfirmation by remember { mutableStateOf(false) }
    val hasUnsavedDraft = hasUnsavedLedgerAccountDraft(code, name, type)
    val types = listOf("ASSET", "LIABILITY", "EQUITY", "REVENUE", "EXPENSE")
    fun requestDismiss() {
        if (hasUnsavedDraft) showDiscardConfirmation = true else onDismiss()
    }

    Dialog(onDismissRequest = { requestDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).heightIn(max = 620.dp),
            shape = RoundedCornerShape(18.dp),
            color = AccountingSurface
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("إضافة حساب إلى الدليل", color = AccountingText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                OutlinedTextField(value = code, onValueChange = { code = it; error = null }, label = { Text("رمز الحساب") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = name, onValueChange = { name = it; error = null }, label = { Text("اسم الحساب") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Box {
                    TextButton(onClick = { typeMenuExpanded = true }) {
                        Text("نوع الحساب: " + accountTypeArabic(type), color = AccountingAccent)
                    }
                    DropdownMenu(expanded = typeMenuExpanded, onDismissRequest = { typeMenuExpanded = false }) {
                        types.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(accountTypeArabic(item)) },
                                onClick = { type = item; typeMenuExpanded = false }
                            )
                        }
                    }
                }
                Text("الطبيعة المحاسبية: " + if (type == "ASSET" || type == "EXPENSE") "مدينة" else "دائنة", color = AccountingMuted, fontSize = 12.sp)
                error?.let { Text(it, color = AccountingBad, fontSize = 12.sp) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { requestDismiss() }, modifier = Modifier.weight(1f)) { Text("إلغاء") }
                    Button(onClick = {
                        if (code.isBlank() || name.isBlank()) error = "أدخل رمز الحساب واسمه."
                        else onSave(code, name, type)
                    }, modifier = Modifier.weight(1f)) { Text("حفظ الحساب") }
                }
            }
        }
    }
    if (showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmation = false },
            title = { Text("بيانات حساب غير محفوظة") },
            text = { Text("هل تريد إغلاق النافذة والتخلص من رمز الحساب أو اسمه؟") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirmation = false
                        onDismiss()
                    }
                ) { Text("إغلاق دون حفظ") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirmation = false }) {
                    Text("متابعة الإدخال")
                }
            }
        )
    }
}

@Composable
fun AccountLedgerScreen(
    accountId: String,
    onBack: () -> Unit,
    onOpenEntry: (String) -> Unit
) {
    val context = LocalContext.current
    val database = remember(context) { AccountingDatabase.getInstance(context) }
    val accounts by database.accountDao().observeAll().collectAsState(initial = emptyList())
    val entries by database.journalEntryDao().observeAll().collectAsState(initial = emptyList())
    val lines by database.journalLineDao().observeAll().collectAsState(initial = emptyList())
    val account = accounts.firstOrNull { it.id == accountId }
    val statementResult = remember(account, entries, lines) {
        account?.let {
            runCatching { AccountLedgerCalculator.calculate(it, entries, lines) }
        }
    }
    val statement = statementResult?.getOrNull()
    val error = statementResult?.exceptionOrNull()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TextButton(onClick = onBack) { Text("العودة إلى دليل الحسابات") }
        if (account == null) {
            Text("الحساب غير موجود.", color = AccountingBad)
        } else {
            Text("كشف حساب", style = MaterialTheme.typography.headlineSmall, color = AccountingText, fontWeight = FontWeight.Bold)
            Text(account.code + " — " + account.name, color = AccountingAccent, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(
                accountTypeArabic(account.accountType) + " · " +
                    if (account.normalSide == "DEBIT") "طبيعته مدينة" else "طبيعته دائنة",
                color = AccountingMuted,
                fontSize = 12.sp
            )

            if (error != null) {
                Text("تعذر حساب كشف الحساب: " + (error.message ?: "بيانات غير سليمة"), color = AccountingBad)
            } else if (statement == null || statement.movements.isEmpty()) {
                Text("لا توجد حركات محاسبية على هذا الحساب حتى الآن.", color = AccountingMuted)
            } else {
                statement.currencySummaries.forEach { summary ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = AccountingSurface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("ملخص العملة: " + summary.currencyCode, color = AccountingAccent, fontWeight = FontWeight.Bold)
                            Text("إجمالي المدين: " + summary.totalDebit.stripTrailingZeros().toPlainString() + " " + summary.currencyCode, color = AccountingMuted, fontSize = 12.sp)
                            Text("إجمالي الدائن: " + summary.totalCredit.stripTrailingZeros().toPlainString() + " " + summary.currencyCode, color = AccountingMuted, fontSize = 12.sp)
                            val closingSideLabel = if (summary.closingSide == "DEBIT") "مدين" else "دائن"
                            Text(
                                "الرصيد الختامي: " + summary.closingBalance.stripTrailingZeros().toPlainString() +
                                    " " + summary.currencyCode + " (" + closingSideLabel + ")",
                                color = AccountingText,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Text("حركات الحساب", style = MaterialTheme.typography.titleLarge, color = AccountingText, fontWeight = FontWeight.Bold)
                statement.movements.forEach { movement ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onOpenEntry(movement.journalEntryId) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = AccountingSurface)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(movement.journalNumber, color = AccountingAccent, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text(movement.currencyCode, color = AccountingMuted, fontSize = 11.sp)
                            }
                            Text(movement.description, color = AccountingText, fontWeight = FontWeight.SemiBold)
                            Text(
                                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(movement.occurredAt)),
                                color = AccountingMuted,
                                fontSize = 11.sp
                            )
                            Text("مدين: " + movement.debit.stripTrailingZeros().toPlainString() + " " + movement.currencyCode, color = AccountingMuted, fontSize = 12.sp)
                            Text("دائن: " + movement.credit.stripTrailingZeros().toPlainString() + " " + movement.currencyCode, color = AccountingMuted, fontSize = 12.sp)
                            if (movement.memo.isNotBlank()) Text(movement.memo, color = AccountingMuted, fontSize = 11.sp)
                            val runningSideLabel = if (movement.balanceSide == "DEBIT") "مدين" else "دائن"
                            Text(
                                "الرصيد الجاري: " + movement.runningBalance.abs().stripTrailingZeros().toPlainString() +
                                    " " + movement.currencyCode + " (" + runningSideLabel + ")",
                                color = AccountingText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text("فتح القيد", color = AccountingAccent, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun JournalBookScreen(
    onBack: () -> Unit,
    onOpenEntry: (String) -> Unit
) {
    val context = LocalContext.current
    val database = remember(context) { AccountingDatabase.getInstance(context) }
    val repository = remember(database) { JournalRepository(database) }
    val accounts by database.accountDao().observeAll().collectAsState(initial = emptyList())
    val entries by database.journalEntryDao().observeAll().collectAsState(initial = emptyList())
    val lines by database.journalLineDao().observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showManualEntry by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(repository) {
        runCatching { repository.ensureSystemAccounts() }
            .onFailure { message = it.message ?: "تعذر تجهيز الحسابات" }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TextButton(onClick = onBack) { Text("العودة إلى المحاسبة العامة") }
        Text("دفتر اليومية العام", style = MaterialTheme.typography.headlineSmall, color = AccountingText, fontWeight = FontWeight.Bold)
        Text("عدد القيود المرحلة: " + entries.size, color = AccountingMuted, fontSize = 13.sp)
        Button(onClick = { showManualEntry = true }, modifier = Modifier.fillMaxWidth()) {
            Text("إدخال قيد يدوي")
        }
        message?.let { Text(it, color = AccountingBad, fontSize = 12.sp) }
        if (entries.isEmpty()) {
            Text("لا توجد قيود يومية حتى الآن.", color = AccountingMuted)
        } else {
            val accountsById = accounts.associateBy { it.id }
            val linesByEntry = lines.groupBy { it.journalEntryId }
            entries.forEach { entry ->
                val entryLines = linesByEntry[entry.id].orEmpty()
                val debitTotal = entryLines.fold(BigDecimal.ZERO) { total, line -> total.add(line.debit.toBigDecimal()) }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onOpenEntry(entry.id) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = AccountingSurface)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(entry.journalNumber, color = AccountingAccent, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(entry.currencyCode, color = AccountingMuted, fontSize = 12.sp)
                        }
                        Text(entry.description, color = AccountingText, fontWeight = FontWeight.SemiBold)
                        Text(
                            "التاريخ: " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(entry.occurredAt)),
                            color = AccountingMuted,
                            fontSize = 11.sp
                        )
                        Text("البيان المصدر: " + journalSourceArabic(entry.sourceType), color = AccountingMuted, fontSize = 11.sp)
                        Text("إجمالي القيد: " + debitTotal.stripTrailingZeros().toPlainString() + " " + entry.currencyCode, color = AccountingText, fontSize = 13.sp)
                        Text("عدد الأسطر: " + entryLines.size, color = AccountingMuted, fontSize = 11.sp)
                    }
                }
            }
        }
        if (accounts.isEmpty()) {
            Text("يتم تجهيز دليل الحسابات الافتراضي...", color = AccountingMuted, fontSize = 12.sp)
        }
    }

    if (showManualEntry) {
        ManualJournalEntryDialog(
            accounts = accounts.filter { it.isActive },
            onDismiss = { showManualEntry = false },
            onSave = { number, description, currency, rate, drafts ->
                scope.launch {
                    runCatching {
                        repository.postJournalEntry(
                            journalNumber = number,
                            sourceType = "MANUAL_JOURNAL",
                            sourceId = "MANUAL-" + number.trim(),
                            currencyCode = currency,
                            exchangeRateSnapshot = rate,
                            occurredAt = System.currentTimeMillis(),
                            description = description,
                            lines = drafts
                        )
                    }.onSuccess {
                        showManualEntry = false
                        message = "تم ترحيل القيد اليدوي بنجاح."
                    }.onFailure {
                        message = it.message ?: "تعذر ترحيل القيد اليدوي"
                    }
                }
            }
        )
    }
}

internal data class ManualJournalLineInput(
    val id: String = UUID.randomUUID().toString(),
    val accountId: String = "",
    val debit: String = "",
    val credit: String = "",
    val memo: String = ""
)

@Composable
private fun ManualJournalEntryDialog(
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, List<JournalLineDraft>) -> Unit
) {
    val initialJournalNumber = remember {
        "MAN-" + System.currentTimeMillis().toString().takeLast(8)
    }
    var journalNumber by remember { mutableStateOf(initialJournalNumber) }
    var description by remember { mutableStateOf("") }
    val context = LocalContext.current
    var currency by remember { mutableStateOf("SYP") }
    val defaultExchangeRate = remember(context) {
        context.getSharedPreferences("good_girl_settings", android.content.Context.MODE_PRIVATE)
            .getString("usd_syp_rate", "1") ?: "1"
    }
    var exchangeRate by remember { mutableStateOf(defaultExchangeRate) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDiscardConfirmation by remember { mutableStateOf(false) }
    val lines = remember { mutableStateListOf(ManualJournalLineInput(), ManualJournalLineInput()) }
    val hasUnsavedDraft = hasUnsavedManualJournalDraft(
        journalNumberChanged = journalNumber != initialJournalNumber,
        description = description,
        currencyCode = currency,
        exchangeRate = exchangeRate,
        defaultExchangeRate = defaultExchangeRate,
        lineCount = lines.size,
        hasLineContent = lines.any { line ->
            line.accountId.isNotBlank() || line.debit.isNotBlank() ||
                line.credit.isNotBlank() || line.memo.isNotBlank()
        }
    )
    fun requestDismiss() {
        if (hasUnsavedDraft) showDiscardConfirmation = true else onDismiss()
    }

    Dialog(onDismissRequest = { requestDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.96f).heightIn(max = 760.dp),
            shape = RoundedCornerShape(18.dp),
            color = AccountingSurface
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text("قيد يومية يدوي", color = AccountingText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                OutlinedTextField(value = journalNumber, onValueChange = { journalNumber = it; error = null }, label = { Text("رقم القيد") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it; error = null }, label = { Text("شرح القيد") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { currency = "SYP"; exchangeRate = "1" }, modifier = Modifier.weight(1f)) {
                        Text(if (currency == "SYP") "✓ ليرة سورية" else "ليرة سورية")
                    }
                    TextButton(onClick = { currency = "USD" }, modifier = Modifier.weight(1f)) {
                        Text(if (currency == "USD") "✓ دولار أمريكي" else "دولار أمريكي")
                    }
                }
                if (currency == "USD") {
                    OutlinedTextField(
                        value = exchangeRate,
                        onValueChange = { exchangeRate = it; error = null },
                        label = { Text("سعر صرف الدولار وقت القيد") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text("أسطر القيد", color = AccountingAccent, fontWeight = FontWeight.Bold)
                lines.forEachIndexed { index, line ->
                    key(line.id) {
                        ManualJournalLineEditor(
                            index = index,
                            line = line,
                            accounts = accounts,
                            onChange = { updated -> lines[index] = updated; error = null },
                            onRemove = { if (lines.size > 2) lines.removeAt(index) }
                        )
                    }
                }
                TextButton(onClick = { lines.add(ManualJournalLineInput()) }) {
                    Text("إضافة سطر")
                }
                error?.let { Text(it, color = AccountingBad, fontSize = 12.sp) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { requestDismiss() }, modifier = Modifier.weight(1f)) { Text("إلغاء") }
                    Button(
                        onClick = {
                            val drafts = lines.map { line ->
                                JournalLineDraft(
                                    accountId = line.accountId,
                                    debit = line.debit.ifBlank { "0" },
                                    credit = line.credit.ifBlank { "0" },
                                    memo = line.memo
                                )
                            }
                            if (journalNumber.isBlank() || description.isBlank()) {
                                error = "رقم القيد وشرحه مطلوبان."
                            } else if (drafts.any { it.accountId.isBlank() }) {
                                error = "اختر حساباً لكل سطر."
                            } else {
                                onSave(journalNumber.trim(), description.trim(), currency, exchangeRate, drafts)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("ترحيل القيد") }
                }
            }
        }
    }
    if (showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmation = false },
            title = { Text("قيد يومية غير محفوظ") },
            text = { Text("هل تريد إغلاق القيد والتخلص من البيانات المدخلة في أسطر اليومية؟") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirmation = false
                        onDismiss()
                    }
                ) { Text("إغلاق دون حفظ") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirmation = false }) {
                    Text("متابعة الإدخال")
                }
            }
        )
    }
}

@Composable
internal fun ManualJournalLineEditor(
    index: Int,
    line: ManualJournalLineInput,
    accounts: List<AccountEntity>,
    onChange: (ManualJournalLineInput) -> Unit,
    onRemove: () -> Unit
) {
    var menuExpanded by remember(line.id) { mutableStateOf(false) }
    val selectedAccount = accounts.firstOrNull { it.id == line.accountId }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = LightGray)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("السطر " + (index + 1), color = AccountingText, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (index >= 2) TextButton(onClick = onRemove) { Text("حذف السطر", color = AccountingBad) }
            }
            Box {
                TextButton(onClick = { menuExpanded = true }) {
                    Text(
                        selectedAccount?.let { it.code + " — " + it.name } ?: "اختيار الحساب",
                        color = if (selectedAccount == null) AccountingAccent else AccountingText
                    )
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    accounts.forEach { account ->
                        DropdownMenuItem(
                            text = { Text(account.code + " — " + account.name) },
                            onClick = {
                                onChange(line.copy(accountId = account.id))
                                menuExpanded = false
                            }
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = line.debit,
                    onValueChange = { updated ->
                        onChange(line.copy(debit = updated, credit = if (updated.isNotBlank()) "" else line.credit))
                    },
                    label = { Text("مدين") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("manual-journal-debit-${line.id}")
                )
                OutlinedTextField(
                    value = line.credit,
                    onValueChange = { updated ->
                        onChange(line.copy(credit = updated, debit = if (updated.isNotBlank()) "" else line.debit))
                    },
                    label = { Text("دائن") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("manual-journal-credit-${line.id}")
                )
            }
            OutlinedTextField(
                value = line.memo,
                onValueChange = { updated ->
                    onChange(line.copy(memo = updated))
                },
                label = { Text("بيان السطر (اختياري)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("manual-journal-memo-${line.id}")
            )
        }
    }
}

@Composable
fun JournalEntryDetailScreen(
    entryId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val database = remember(context) { AccountingDatabase.getInstance(context) }
    val entries by database.journalEntryDao().observeAll().collectAsState(initial = emptyList())
    val accounts by database.accountDao().observeAll().collectAsState(initial = emptyList())
    val lines by database.journalLineDao().observeForEntry(entryId).collectAsState(initial = emptyList())
    val repository = remember(database) { JournalRepository(database) }
    val scope = rememberCoroutineScope()
    val entry = entries.firstOrNull { it.id == entryId }
    val existingReversal = entries.firstOrNull { it.sourceType == "REVERSAL" && it.sourceId == entryId }
    val accountsById = accounts.associateBy { it.id }
    var showReversalDialog by remember { mutableStateOf(false) }
    var reversalReason by remember { mutableStateOf("") }
    var reversalMessage by remember { mutableStateOf<String?>(null) }
    var isReversing by remember { mutableStateOf(false) }
    var showDiscardReversalConfirmation by remember { mutableStateOf(false) }
    fun requestCloseReversalDialog() {
        if (!isReversing) {
            if (hasUnsavedReversalDraft(reversalReason)) {
                showDiscardReversalConfirmation = true
            } else {
                showReversalDialog = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TextButton(onClick = onBack) { Text("العودة إلى دفتر اليومية") }
        if (entry == null) {
            Text("القيد غير موجود.", color = AccountingBad)
        } else {
            Text("تفاصيل القيد", style = MaterialTheme.typography.headlineSmall, color = AccountingText, fontWeight = FontWeight.Bold)
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AccountingSurface)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(entry.journalNumber, color = AccountingAccent, fontWeight = FontWeight.Bold)
                    Text(entry.description, color = AccountingText)
                    Text("العملة: " + entry.currencyCode, color = AccountingMuted, fontSize = 12.sp)
                    Text("سعر الصرف المحفوظ: " + entry.exchangeRateSnapshot, color = AccountingMuted, fontSize = 12.sp)
                    Text("التاريخ: " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(entry.occurredAt)), color = AccountingMuted, fontSize = 12.sp)
                    Text("نوع المصدر: " + journalSourceArabic(entry.sourceType), color = AccountingMuted, fontSize = 12.sp)
                }
            }
            if (entry.sourceType == "MANUAL_JOURNAL" && existingReversal == null) {
                Button(
                    onClick = { showReversalDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("إنشاء قيد عكسي")
                }
            }
            if (existingReversal != null) {
                Text(
                    "تم إنشاء القيد العكسي لهذا القيد: " + existingReversal.journalNumber,
                    color = AccountingGood,
                    fontSize = 12.sp
                )
            }
            reversalMessage?.let { Text(it, color = if (it.startsWith("تم")) AccountingGood else AccountingBad, fontSize = 12.sp) }
            if (entry.sourceType == "REVERSAL") {
                Text("هذا قيد عكسي مستقل؛ لا يمكن عكسه مرة أخرى.", color = AccountingMuted, fontSize = 12.sp)
            }
            val debitTotal = lines.fold(BigDecimal.ZERO) { total, line -> total.add(line.debit.toBigDecimal()) }
            val creditTotal = lines.fold(BigDecimal.ZERO) { total, line -> total.add(line.credit.toBigDecimal()) }
            lines.forEach { line ->
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AccountingSurface)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val account = accountsById[line.accountId]
                        Text(
                            (account?.code ?: "—") + " — " + (account?.name ?: "حساب غير موجود"),
                            color = AccountingText,
                            fontWeight = FontWeight.Bold
                        )
                        Text("مدين: " + line.debit + " " + entry.currencyCode, color = AccountingMuted, fontSize = 12.sp)
                        Text("دائن: " + line.credit + " " + entry.currencyCode, color = AccountingMuted, fontSize = 12.sp)
                        if (line.memo.isNotBlank()) Text(line.memo, color = AccountingMuted, fontSize = 12.sp)
                    }
                }
            }
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AccountingSurface)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("إجمالي المدين: " + debitTotal.stripTrailingZeros().toPlainString() + " " + entry.currencyCode, color = AccountingText, fontWeight = FontWeight.Bold)
                    Text("إجمالي الدائن: " + creditTotal.stripTrailingZeros().toPlainString() + " " + entry.currencyCode, color = AccountingText, fontWeight = FontWeight.Bold)
                    Text(if (debitTotal.compareTo(creditTotal) == 0) "القيد متوازن" else "تحذير: القيد غير متوازن", color = if (debitTotal.compareTo(creditTotal) == 0) AccountingGood else AccountingBad, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showReversalDialog) {
        Dialog(
            onDismissRequest = { requestCloseReversalDialog() },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.96f).heightIn(max = 420.dp),
                shape = RoundedCornerShape(18.dp),
                color = AccountingSurface
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("إنشاء قيد عكسي", color = AccountingText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "سيُنشأ قيد جديد يعكس المدين والدائن دون تعديل القيد اليدوي الأصلي. لا يتاح هذا الإجراء حالياً لفواتير البيع والشراء أو حركات المخزون والتحصيل.",
                        color = AccountingMuted,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = reversalReason,
                        onValueChange = { reversalReason = it },
                        label = { Text("سبب عكس القيد") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(
                            onClick = { requestCloseReversalDialog() },
                            enabled = !isReversing,
                            modifier = Modifier.weight(1f)
                        ) { Text("إلغاء") }
                        Button(
                            onClick = {
                                val currentEntry = entry
                                if (currentEntry != null) {
                                    scope.launch {
                                        isReversing = true
                                        try {
                                            val reversalId = repository.reverseJournalEntry(
                                                originalEntryId = currentEntry.id,
                                                reversalJournalNumber = "REV-" + System.currentTimeMillis().toString(),
                                                occurredAt = System.currentTimeMillis(),
                                                reason = reversalReason
                                            )
                                            val reversal = database.journalEntryDao().findById(reversalId)
                                            reversalMessage = "تم إنشاء القيد العكسي: " + (reversal?.journalNumber ?: reversalId)
                                            reversalReason = ""
                                            showReversalDialog = false
                                        } catch (error: Exception) {
                                            reversalMessage = error.message ?: "تعذر إنشاء القيد العكسي"
                                        } finally {
                                            isReversing = false
                                        }
                                    }
                                }
                            },
                            enabled = reversalReason.isNotBlank() && !isReversing,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isReversing) "جارٍ الإنشاء..." else "تأكيد العكس")
                        }
                    }
                }
            }
        }
    }
    if (showDiscardReversalConfirmation) {
        AlertDialog(
            onDismissRequest = { showDiscardReversalConfirmation = false },
            title = { Text("سبب غير محفوظ") },
            text = { Text("كتبت سبباً لعكس القيد. هل تريد إغلاق النافذة والتخلص من السبب المدخل؟") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardReversalConfirmation = false
                        reversalReason = ""
                        showReversalDialog = false
                    }
                ) { Text("إغلاق دون حفظ") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardReversalConfirmation = false }) {
                    Text("متابعة الإدخال")
                }
            }
        )
    }
}

private fun accountTypeArabic(type: String): String = when (type) {
    "ASSET" -> "أصول"
    "LIABILITY" -> "التزامات"
    "EQUITY" -> "حقوق ملكية"
    "REVENUE" -> "إيرادات"
    "EXPENSE" -> "مصروفات"
    else -> "غير محدد"
}

private fun journalSourceArabic(source: String): String = when (source) {
    "SALE_INVOICE" -> "فاتورة بيع"
    "PURCHASE_INVOICE" -> "فاتورة شراء"
    "CUSTOMER_RECEIPT" -> "قبض من عميل"
    "SUPPLIER_PAYMENT" -> "دفعة لمورد"
    "CUSTOMER_OPENING_BALANCE" -> "رصيد عميل افتتاحي"
    "SUPPLIER_OPENING_BALANCE" -> "رصيد مورد افتتاحي"
    "PRODUCT_OPENING_STOCK" -> "مخزون افتتاحي"
    "MANUAL_STOCK_GAIN" -> "زيادة مخزون يدوية"
    "MANUAL_STOCK_LOSS" -> "نقص مخزون يدوي"
    "STOCKTAKE_GAIN" -> "زيادة ناتجة عن الجرد"
    "STOCKTAKE_LOSS" -> "نقص ناتج عن الجرد"
    "MANUAL_CASH_INCOME" -> "إيراد نقدي يدوي"
    "MANUAL_CASH_EXPENSE" -> "مصروف نقدي يدوي"
    "MANUAL_JOURNAL" -> "قيد يومية يدوي"
    "REVERSAL" -> "قيد عكسي"
    else -> source
}
