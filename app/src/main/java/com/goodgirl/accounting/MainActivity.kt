package com.goodgirl.accounting

import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goodgirl.accounting.data.AccountingBackupManager
import com.goodgirl.accounting.data.CashTransactionRepository
import com.goodgirl.accounting.data.CategoryRepository
import com.goodgirl.accounting.data.InvoiceLineDraft
import com.goodgirl.accounting.data.InvoicePdfExporter
import com.goodgirl.accounting.data.InvoiceRepository
import com.goodgirl.accounting.data.FinancialReportPdfExporter
import com.goodgirl.accounting.data.PartyRepository
import com.goodgirl.accounting.data.PartyPaymentRepository
import com.goodgirl.accounting.data.ProductRepository
import com.goodgirl.accounting.data.ProductUnitRepository
import com.goodgirl.accounting.data.StockRepository
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.CategoryEntity
import com.goodgirl.accounting.data.local.PartyEntity
import com.goodgirl.accounting.data.local.ProductEntity
import com.goodgirl.accounting.domain.CashTransactionRules
import com.goodgirl.accounting.domain.FinancialReportCalculator
import com.goodgirl.accounting.domain.InvoicePostingRules
import com.goodgirl.accounting.domain.InvoiceDiscountAllocator
import com.goodgirl.accounting.domain.PartyBalanceCalculator
import com.goodgirl.accounting.domain.ProductInputRules
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import java.util.UUID

private val BrandBlue = Color(0xFF2878C8)
private val LightGray = Color(0xFFE9EEF4)
private val SoftGreen = Color(0xFFA9DDBD)
private val SoftRed = Color(0xFFE9A5AE)
private val SoftPurple = Color(0xFFB9A7DF)
private val Ink = Color(0xFF263445)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(40, 120, 200)
        window.navigationBarColor = android.graphics.Color.rgb(255, 255, 255)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                GoodGirlApp()
            }
        }
    }
}

@Composable
private fun GoodGirlApp() {
    var activePage by remember { mutableStateOf("dashboard") }
    var statementPartyId by remember { mutableStateOf<String?>(null) }
    var statementReturnPage by remember { mutableStateOf("parties") }
    var unitsProductId by remember { mutableStateOf<String?>(null) }
    var historyProductId by remember { mutableStateOf<String?>(null) }
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = BrandBlue,
            secondary = SoftPurple,
            background = LightGray,
            surface = Color.White,
            onPrimary = Color.White,
            onSurface = Ink
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = LightGray) {
            when (activePage) {
                "cash" -> CashManagementScreen(onBack = { activePage = "dashboard" })
                "reports" -> FinancialReportsScreen(onBack = { activePage = "dashboard" })
                "backup" -> BackupManagementScreen(onBack = { activePage = "dashboard" })
                "settings" -> SettingsScreen(onBack = { activePage = "dashboard" })
                "sales" -> InvoiceManagementScreen(kind = "SALE", onBack = { activePage = "dashboard" })
                "daily_sales" -> DailySalesScreen(
                    onBack = { activePage = "dashboard" },
                    onOpenFullInvoice = { activePage = "sales" }
                )
                "purchases" -> InvoiceManagementScreen(kind = "PURCHASE", onBack = { activePage = "dashboard" })
                "products" -> ProductManagementScreen(
                    onBack = { activePage = "dashboard" },
                    onManageUnits = { productId ->
                        unitsProductId = productId
                        activePage = "product_units"
                    },
                    onManageCategories = { activePage = "categories" },
                    onViewHistory = { productId ->
                        historyProductId = productId
                        activePage = "stock_history"
                    }
                )
                "categories" -> ProductCategoriesScreen(onBack = { activePage = "products" })
                "product_units" -> ProductUnitsScreen(
                    productId = unitsProductId.orEmpty(),
                    onBack = { activePage = "products" }
                )
                "stock_history" -> StockHistoryScreen(
                    productId = historyProductId.orEmpty(),
                    onBack = { activePage = "products" }
                )
                "accounts" -> AccountsAndDebtsScreen(
                    onBack = { activePage = "dashboard" },
                    onManageParties = { activePage = "parties" },
                    onOpenStatement = { partyId ->
                        statementPartyId = partyId
                        statementReturnPage = "accounts"
                        activePage = "statement"
                    }
                )
                "parties" -> PartyManagementScreen(
                    onBack = { activePage = "dashboard" },
                    onOpenStatement = { partyId ->
                        statementPartyId = partyId
                        statementReturnPage = "parties"
                        activePage = "statement"
                    }
                )
                "statement" -> PartyStatementScreen(
                    partyId = statementPartyId.orEmpty(),
                    onBack = { activePage = statementReturnPage }
                )
                else -> DashboardScreen(
                    onOpenDailySales = { activePage = "daily_sales" },
                    onOpenSales = { activePage = "sales" },
                    onOpenPurchases = { activePage = "purchases" },
                    onOpenProducts = { activePage = "products" },
                    onOpenParties = { activePage = "parties" },
                    onOpenAccounts = { activePage = "accounts" },
                    onOpenCash = { activePage = "cash" },
                    onOpenReports = { activePage = "reports" },
                    onOpenBackup = { activePage = "backup" },
                    onOpenSettings = { activePage = "settings" }
                )
            }
        }
    }
}

@Composable
private fun DashboardScreen(
    onOpenDailySales: () -> Unit,
    onOpenSales: () -> Unit,
    onOpenPurchases: () -> Unit,
    onOpenProducts: () -> Unit,
    onOpenParties: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenCash: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val database = remember(context) { AccountingDatabase.getInstance(context) }
    val invoices by database.invoiceDao().observeAll().collectAsState(initial = emptyList())
    val ledgerEntries by database.ledgerDao().observeAll().collectAsState(initial = emptyList())
    val customers by database.partyDao().observeByKind("CUSTOMER").collectAsState(initial = emptyList())
    val suppliers by database.partyDao().observeByKind("SUPPLIER").collectAsState(initial = emptyList())

    val todayStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val tomorrowStart = remember(todayStart) {
        Calendar.getInstance().apply {
            timeInMillis = todayStart
            add(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis
    }
    val todaySales = remember(invoices, todayStart, tomorrowStart) {
        invoices.filter {
            it.status == "POSTED" && it.kind == "SALE" && it.issuedAt >= todayStart && it.issuedAt < tomorrowStart
        }.groupBy { it.currencyCode }.mapValues { (_, rows) ->
            rows.fold(BigDecimal.ZERO) { total, invoice -> total.add(invoice.total.toBigDecimal()) }
        }
    }
    val todayReceipts = remember(ledgerEntries, todayStart, tomorrowStart) {
        ledgerEntries.filter {
            it.entryType == "CASH_IN" && it.occurredAt >= todayStart && it.occurredAt < tomorrowStart
        }.groupBy { it.currencyCode }.mapValues { (_, rows) ->
            rows.fold(BigDecimal.ZERO) { total, entry -> total.add(entry.amount.toBigDecimal()) }
        }
    }
    val customerBalances = remember(customers, ledgerEntries) {
        val totals = mutableMapOf<String, BigDecimal>()
        customers.forEach { party ->
            PartyBalanceCalculator.calculate("CUSTOMER", ledgerEntries.filter { it.partyId == party.id })
                .forEach { (currency, amount) ->
                    totals[currency] = (totals[currency] ?: BigDecimal.ZERO).add(amount)
                }
        }
        totals
    }
    val supplierBalances = remember(suppliers, ledgerEntries) {
        val totals = mutableMapOf<String, BigDecimal>()
        suppliers.forEach { party ->
            PartyBalanceCalculator.calculate("SUPPLIER", ledgerEntries.filter { it.partyId == party.id })
                .forEach { (currency, amount) ->
                    totals[currency] = (totals[currency] ?: BigDecimal.ZERO).add(amount)
                }
        }
        totals
    }
    val postedInvoiceCount = remember(invoices) { invoices.count { it.status == "POSTED" } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BrandBlue, RoundedCornerShape(22.dp))
                .padding(20.dp)
        ) {
            Text("Good Girl", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text("نظام إدارة ومحاسبة متكامل", color = Color.White, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                "نسخة تطويرية — تُعرض الأرصدة الفعلية بعد ربط كل الأقسام",
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 12.sp
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("لوحة التحكم", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
            TextButton(onClick = onOpenSettings) {
                Text("⚙️ الإعدادات", color = BrandBlue, fontWeight = FontWeight.Bold)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SummaryCard("مبيعات اليوم", formatCurrencyTotals(todaySales), "حسب العملة", BrandBlue, Modifier.weight(1f))
            SummaryCard("مقبوضات اليوم", formatCurrencyTotals(todayReceipts), "حركات قبض الصندوق", SoftGreen, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SummaryCard("أرصدة العملاء", formatCurrencyTotals(customerBalances), "مستحق لنا", SoftRed, Modifier.weight(1f))
            SummaryCard("أرصدة الموردين", formatCurrencyTotals(supplierBalances), "مستحق علينا", SoftPurple, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SummaryCard("الفواتير المرحلة", postedInvoiceCount.toString(), "فاتورة", BrandBlue, Modifier.weight(1f))
        }

        Text("الأقسام الرئيسية", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
        SectionRow(number = 0, title = "المبيعات اليومية", icon = "🧾", onClick = onOpenDailySales)
        val sections = listOf(
            "المبيعات والفواتير",
            "المشتريات",
            "المنتجات والمخزون",
            "العملاء والموردون",
            "الحسابات والديون",
            "الصندوق والمصروفات",
            "التقارير والأرباح",
            "النسخ الاحتياطي والاستعادة"
        )
        sections.forEachIndexed { index, title ->
            SectionRow(
                number = index + 1,
                title = title,
                onClick = when (index) {
                    0 -> onOpenSales
                    1 -> onOpenPurchases
                    2 -> onOpenProducts
                    3 -> onOpenParties
                    4 -> onOpenAccounts
                    5 -> onOpenCash
                    6 -> onOpenReports
                    7 -> onOpenBackup
                    else -> null
                }
            )
        }

        Text(
            "الأقسام الأخرى ستُفعّل تباعاً بعد ربطها بدورة البيانات والمحاسبة.",
            color = Color(0xFF64748B),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )
    }
}



@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("good_girl_settings", android.content.Context.MODE_PRIVATE)
    }
    var exchangeRate by remember {
        mutableStateOf(preferences.getString("usd_syp_rate", "1") ?: "1")
    }
    var lastUpdated by remember {
        mutableStateOf(preferences.getLong("usd_syp_rate_updated_at", 0L))
    }
    var rateHistory by remember {
        mutableStateOf(preferences.getStringSet("usd_syp_rate_history", emptySet())?.toList().orEmpty().sortedDescending())
    }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TextButton(onClick = onBack) { Text("العودة إلى لوحة التحكم") }
        Text(
            "الإعدادات",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Ink
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("العملات وأسعار الصرف", color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    "حدد قيمة الدولار الواحد بالليرة السورية. يُستخدم السعر المحفوظ كقيمة افتراضية للفواتير الجديدة بالدولار، ويمكن تعديل السعر داخل الفاتورة عند الحاجة.",
                    color = Color(0xFF64748B),
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = exchangeRate,
                    onValueChange = {
                        exchangeRate = it
                        message = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("سعر 1 دولار مقابل الليرة السورية") },
                    singleLine = true
                )
                if (lastUpdated > 0L) {
                    Text(
                        "آخر تعديل: " + SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(lastUpdated)),
                        color = Color(0xFF64748B),
                        fontSize = 12.sp
                    )
                } else {
                    Text("لم يتم حفظ سعر صرف بعد.", color = Color(0xFF64748B), fontSize = 12.sp)
                }
                Button(
                    onClick = {
                        val normalized = runCatching {
                            BigDecimal(exchangeRate.trim().replace(',', '.'))
                        }.getOrNull()
                        if (normalized == null || normalized <= BigDecimal.ZERO) {
                            message = "أدخل سعر صرف صحيحاً أكبر من صفر."
                        } else {
                            val cleanRate = normalized.stripTrailingZeros().toPlainString()
                            val savedAt = System.currentTimeMillis()
                            val historyEntry = savedAt.toString() + "|" + cleanRate
                            val updatedHistory = (rateHistory + historyEntry).distinct().take(30)
                            preferences.edit()
                                .putString("usd_syp_rate", cleanRate)
                                .putLong("usd_syp_rate_updated_at", savedAt)
                                .putStringSet("usd_syp_rate_history", updatedHistory.toSet())
                                .apply()
                            exchangeRate = cleanRate
                            lastUpdated = savedAt
                            rateHistory = updatedHistory.sortedDescending()
                            message = "تم حفظ سعر الصرف الجديد. سيُستخدم للفواتير الجديدة فقط."
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("حفظ سعر الصرف")
                }
                message?.let {
                    Text(
                        it,
                        color = if (it.startsWith("تم")) Color(0xFF287A4B) else Color(0xFFB42332),
                        fontSize = 13.sp
                    )
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("سجل تعديلات سعر الصرف", color = Ink, fontWeight = FontWeight.Bold)
                if (rateHistory.isEmpty()) {
                    Text("لا يوجد سجل تعديلات حتى الآن.", color = Color(0xFF64748B), fontSize = 12.sp)
                } else {
                    rateHistory.take(10).forEach { item ->
                        val parts = item.split("|", limit = 2)
                        val timestamp = parts.firstOrNull()?.toLongOrNull() ?: 0L
                        val rate = parts.getOrNull(1).orEmpty()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(rate + " ل.س / USD", color = BrandBlue, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (timestamp > 0L) SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(timestamp)) else "",
                                color = Color(0xFF64748B),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("ملاحظات", color = Ink, fontWeight = FontWeight.Bold)
                Text("• سعر الصرف محفوظ محلياً على هذا الجهاز.", color = Color(0xFF64748B), fontSize = 12.sp)
                Text("• الفواتير السابقة تحتفظ بسعر الصرف المسجل وقت إنشائها.", color = Color(0xFF64748B), fontSize = 12.sp)
                Text("• العملة السورية تعتمد سعر صرف 1 تلقائياً.", color = Color(0xFF64748B), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun BackupManagementScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val database = remember(context) { AccountingDatabase.getInstance(context) }
    val manager = remember(context, database) { AccountingBackupManager(context, database) }
    val scope = rememberCoroutineScope()
    var isBusy by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isBusy = true
                message = null
                try {
                    val result = manager.exportBackup(uri)
                    message = "تم إنشاء النسخة الاحتياطية بنجاح. عدد السجلات: " + result.recordCount
                } catch (error: Exception) {
                    message = error.message ?: "تعذر إنشاء النسخة الاحتياطية"
                } finally {
                    isBusy = false
                }
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isBusy = true
                message = null
                try {
                    val result = manager.restoreBackup(uri)
                    message = "تمت استعادة النسخة بنجاح. عدد السجلات المستعادة: " + result.recordCount
                    confirmRestore = false
                } catch (error: Exception) {
                    message = error.message ?: "تعذرت استعادة النسخة الاحتياطية"
                } finally {
                    isBusy = false
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TextButton(onClick = onBack) { Text("العودة إلى لوحة التحكم") }
        Text("النسخ الاحتياطي والاستعادة", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Ink)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("إنشاء نسخة احتياطية", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    "يحفظ المنتجات والمخزون والوحدات والعملاء والموردين والفواتير وكشوف الحساب وحركات الصندوق في ملف واحد.",
                    color = Color(0xFF64748B),
                    fontSize = 13.sp
                )
                Button(
                    onClick = {
                        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                        exportLauncher.launch("GoodGirl_Backup_" + stamp + ".json")
                    },
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("إنشاء وحفظ نسخة احتياطية")
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("استعادة نسخة احتياطية", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    "الاستعادة تستبدل البيانات الحالية بالكامل ببيانات الملف المختار، ولا يمكن التراجع عنها من داخل التطبيق.",
                    color = Color(0xFFB42332),
                    fontSize = 13.sp
                )
                androidx.compose.material3.Checkbox(
                    checked = confirmRestore,
                    onCheckedChange = { confirmRestore = it },
                    enabled = !isBusy
                )
                Text("أفهم أن الاستعادة ستحل محل جميع البيانات الحالية.")
                Button(
                    onClick = {
                        restoreLauncher.launch(arrayOf("application/json", "text/json", "application/octet-stream", "*/*"))
                    },
                    enabled = !isBusy && confirmRestore,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("اختيار ملف واستعادة البيانات")
                }
            }
        }
        Text(
            "احتفظ بملف النسخة في مكان آمن. قد يحتوي على بيانات العملاء والموردين والفواتير.",
            color = Color(0xFF64748B),
            fontSize = 12.sp
        )
        if (isBusy) {
            Text("جارٍ تنفيذ العملية...", color = BrandBlue, fontWeight = FontWeight.SemiBold)
        }
        message?.let {
            Text(it, color = if (it.startsWith("تم")) Color(0xFF287A4B) else Color(0xFFB42332), fontSize = 13.sp)
        }
    }
}

@Composable
private fun InvoiceManagementScreen(kind: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val database = remember(context) { AccountingDatabase.getInstance(context) }
    val repository = remember(database) { InvoiceRepository(database) }
    val products by database.productDao().observeActiveProducts().collectAsState(initial = emptyList())
    val partyKind = if (kind == "SALE") "CUSTOMER" else "SUPPLIER"
    val parties by database.partyDao().observeByKind(partyKind).collectAsState(initial = emptyList())
    val invoices by repository.observeInvoices().collectAsState(initial = emptyList())
    val invoiceLines by database.invoiceDao().observeAllLines().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var pendingPdfInvoiceId by remember { mutableStateOf<String?>(null) }
    var isExportingPdf by remember { mutableStateOf(false) }
    var pdfMessage by remember { mutableStateOf<String?>(null) }
    val pdfExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val targetInvoice = invoices.firstOrNull { it.id == pendingPdfInvoiceId }
        if (uri != null && targetInvoice != null) {
            val targetLines = invoiceLines.filter { it.invoiceId == targetInvoice.id }
            val targetPartyName = parties.firstOrNull { it.id == targetInvoice.partyId }?.name
            scope.launch {
                isExportingPdf = true
                pdfMessage = null
                try {
                    InvoicePdfExporter.write(
                        context = context,
                        destination = uri,
                        invoice = targetInvoice,
                        lines = targetLines,
                        partyName = targetPartyName
                    )
                    pdfMessage = "تم حفظ ملف PDF للفاتورة " + targetInvoice.invoiceNumber
                } catch (error: Exception) {
                    pdfMessage = error.message ?: "تعذر إنشاء ملف PDF"
                } finally {
                    isExportingPdf = false
                    pendingPdfInvoiceId = null
                }
            }
        } else {
            pendingPdfInvoiceId = null
        }
    }
    val draftLines = remember(kind) { mutableStateListOf<InvoiceLineDraft>() }

    var selectedProductId by remember { mutableStateOf<String?>(null) }
    var selectedUnitName by remember { mutableStateOf<String?>(null) }
    var unitMenuExpanded by remember { mutableStateOf(false) }
    var productMenuExpanded by remember { mutableStateOf(false) }
    var selectedPartyId by remember { mutableStateOf<String?>(null) }
    var partyMenuExpanded by remember { mutableStateOf(false) }
    var quantity by remember { mutableStateOf("1") }
    var unitPrice by remember { mutableStateOf("0") }
    var lineDiscount by remember { mutableStateOf("0") }
    var invoiceDiscount by remember { mutableStateOf("0") }
    var paidAmount by remember { mutableStateOf("0") }
    var currencyCode by remember { mutableStateOf("SYP") }
    var exchangeRate by remember {
        mutableStateOf(
            context.getSharedPreferences("good_girl_settings", android.content.Context.MODE_PRIVATE)
                .getString("usd_syp_rate", "1") ?: "1"
        )
    }
    var note by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var paymentInvoiceId by remember { mutableStateOf<String?>(null) }
    var paymentAmount by remember { mutableStateOf("") }
    var paymentNote by remember { mutableStateOf("") }
    var isSavingPayment by remember { mutableStateOf(false) }
    var paymentMessage by remember { mutableStateOf<String?>(null) }

    val productUnits by database.productUnitDao()
        .observeForProduct(selectedProductId ?: "")
        .collectAsState(initial = emptyList())
    val selectedProduct = products.firstOrNull { it.id == selectedProductId }
    val selectedUnit = productUnits.firstOrNull { it.unitName == selectedUnitName }
        ?: productUnits.firstOrNull { it.isBaseUnit }
    val selectedParty = parties.firstOrNull { it.id == selectedPartyId }
    val screenTitle = if (kind == "SALE") "فاتورة بيع جديدة" else "فاتورة شراء جديدة"
    val unitPriceLabel = if (kind == "SALE") "سعر البيع للوحدة" else "كلفة الشراء للوحدة"

    val subtotal = draftLines.fold(BigDecimal.ZERO) { total, line ->
        total.add(line.quantity.toBigDecimal().multiply(line.unitPrice.toBigDecimal()))
    }
    val lineDiscountTotal = draftLines.fold(BigDecimal.ZERO) { total, line ->
        total.add(line.discount.toBigDecimal())
    }
    val enteredInvoiceDiscount = runCatching { BigDecimal(invoiceDiscount.trim()) }.getOrDefault(BigDecimal.ZERO)
    val calculatedTotal = subtotal.subtract(lineDiscountTotal).subtract(enteredInvoiceDiscount).max(BigDecimal.ZERO)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("العودة إلى لوحة التحكم") }
        Text(screenTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Ink)
        Text(
            if (kind == "SALE") "اختيار المواد والكميات وتسجيل المقبوض والمتبقي" else "تسجيل المواد الواردة وتحديث كلفة المخزون",
            color = Color(0xFF64748B),
            fontSize = 13.sp
        )

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("بيانات الفاتورة", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink)
                Box {
                    Button(onClick = { partyMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedParty?.name ?: if (kind == "SALE") "اختيار العميل (اختياري للنقدي)" else "اختيار المورد (اختياري للنقدي)")
                    }
                    DropdownMenu(expanded = partyMenuExpanded, onDismissRequest = { partyMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("بدون حساب — عملية نقدية") },
                            onClick = { selectedPartyId = null; partyMenuExpanded = false }
                        )
                        parties.forEach { party ->
                            DropdownMenuItem(
                                text = { Text(party.name) },
                                onClick = { selectedPartyId = party.id; partyMenuExpanded = false }
                            )
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        enabled = draftLines.isEmpty(),
                        onClick = { currencyCode = "SYP"; exchangeRate = "1"; selectedProductId = null; selectedUnitName = null }
                    ) { Text(if (currencyCode == "SYP") "✓ الليرة السورية" else "الليرة السورية") }
                    TextButton(
                        enabled = draftLines.isEmpty(),
                        onClick = { currencyCode = "USD"; exchangeRate = context.getSharedPreferences("good_girl_settings", android.content.Context.MODE_PRIVATE).getString("usd_syp_rate", "1") ?: "1"; selectedProductId = null; selectedUnitName = null }
                    ) { Text(if (currencyCode == "USD") "✓ الدولار الأمريكي" else "الدولار الأمريكي") }
                }
                if (draftLines.isNotEmpty()) {
                    Text("لا يمكن تبديل عملة الفاتورة بعد إضافة المواد. احذف المواد أولاً إذا أردت تغيير العملة.", color = Color(0xFF64748B), fontSize = 12.sp)
                }
                if (currencyCode == "USD") {
                    OutlinedTextField(
                        exchangeRate,
                        { exchangeRate = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("سعر صرف الدولار مقابل الليرة") },
                        singleLine = true
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("إضافة مادة إلى الفاتورة", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink)
                Box {
                    Button(
                        onClick = { productMenuExpanded = true },
                        enabled = products.any { it.priceCurrencyCode == currencyCode },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(selectedProduct?.name ?: "اختيار المادة (" + currencyCode + ")")
                    }
                    DropdownMenu(expanded = productMenuExpanded, onDismissRequest = { productMenuExpanded = false }) {
                        products.filter { it.priceCurrencyCode == currencyCode }.forEach { product ->
                            DropdownMenuItem(
                                text = { Text(product.name + " — المتوفر: " + product.stockQuantity + " " + product.baseUnit) },
                                onClick = {
                                    selectedProductId = product.id
                                    selectedUnitName = product.baseUnit
                                    unitPrice = if (kind == "SALE") product.salePrice else product.purchaseCost
                                    productMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                if (products.none { it.priceCurrencyCode == currencyCode }) {
                    Text("لا توجد مواد معرفة بهذه العملة. أضف مادة أو عدّل عملتها من شاشة المنتجات.", color = Color(0xFF64748B), fontSize = 12.sp)
                }
                if (selectedProduct != null) {
                    Box {
                        Button(onClick = { unitMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("وحدة القياس: " + (selectedUnit?.unitName ?: selectedProduct.baseUnit))
                        }
                        DropdownMenu(expanded = unitMenuExpanded, onDismissRequest = { unitMenuExpanded = false }) {
                            productUnits.forEach { unit ->
                                DropdownMenuItem(
                                    text = {
                                        Text("1 " + unit.unitName + " = " + unit.baseFactor + " " + selectedProduct.baseUnit)
                                    },
                                    onClick = {
                                        selectedUnitName = unit.unitName
                                        val basePrice = (if (kind == "SALE") selectedProduct.salePrice else selectedProduct.purchaseCost).toBigDecimal()
                                        unitPrice = basePrice.multiply(unit.baseFactor.toBigDecimal()).stripTrailingZeros().toPlainString()
                                        unitMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(quantity, { quantity = it }, modifier = Modifier.fillMaxWidth(), label = { Text("الكمية") }, singleLine = true)
                OutlinedTextField(unitPrice, { unitPrice = it }, modifier = Modifier.fillMaxWidth(), label = { Text(unitPriceLabel) }, singleLine = true)
                OutlinedTextField(lineDiscount, { lineDiscount = it }, modifier = Modifier.fillMaxWidth(), label = { Text("خصم المادة") }, singleLine = true)
                Button(
                    onClick = {
                        try {
                            val product = selectedProduct ?: throw IllegalArgumentException("اختر مادة أولاً")
                            val cleanQuantity = InvoicePostingRules.normalizePositiveQuantity(quantity)
                            val cleanPrice = InvoicePostingRules.normalizeNonNegativeAmount(unitPrice, unitPriceLabel)
                            val cleanDiscount = InvoicePostingRules.normalizeNonNegativeAmount(lineDiscount, "خصم المادة")
                            InvoicePostingRules.calculateLineTotal(cleanQuantity, cleanPrice, cleanDiscount)
                            draftLines.add(
                                InvoiceLineDraft(
                                    productId = product.id,
                                    quantity = cleanQuantity,
                                    unitPrice = cleanPrice,
                                    discount = cleanDiscount,
                                    unitName = selectedUnit?.unitName ?: product.baseUnit
                                )
                            )
                            quantity = "1"
                            lineDiscount = "0"
                            message = null
                        } catch (error: Exception) {
                            message = error.message ?: "تعذر إضافة المادة"
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("إضافة إلى الفاتورة") }
            }
        }

        if (draftLines.isNotEmpty()) {
            Text("مواد الفاتورة (" + draftLines.size + ")", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
            draftLines.forEachIndexed { index, line ->
                val productName = products.firstOrNull { it.id == line.productId }?.name ?: "مادة"
                val net = InvoicePostingRules.calculateLineTotal(line.quantity, line.unitPrice, line.discount)
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(productName, color = Ink, fontWeight = FontWeight.Bold)
                            Text(line.quantity + " " + (line.unitName ?: "") + " × " + line.unitPrice + " = " + net + " " + currencyCode, color = Color(0xFF64748B), fontSize = 12.sp)
                        }
                        TextButton(onClick = { draftLines.removeAt(index) }) { Text("حذف") }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("ملخص الفاتورة", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink)
                Text("المجموع قبل الخصومات: " + subtotal.stripTrailingZeros().toPlainString() + " " + currencyCode, color = Ink)
                Text("خصومات المواد: " + lineDiscountTotal.stripTrailingZeros().toPlainString(), color = Color(0xFF64748B), fontSize = 13.sp)
                OutlinedTextField(invoiceDiscount, { invoiceDiscount = it }, modifier = Modifier.fillMaxWidth(), label = { Text("خصم إضافي على الفاتورة") }, singleLine = true)
                Text("الإجمالي التقريبي: " + calculatedTotal.stripTrailingZeros().toPlainString() + " " + currencyCode, color = BrandBlue, fontWeight = FontWeight.Bold)
                OutlinedTextField(paidAmount, { paidAmount = it }, modifier = Modifier.fillMaxWidth(), label = { Text("المبلغ المقبوض / المدفوع") }, singleLine = true)
                OutlinedTextField(note, { note = it }, modifier = Modifier.fillMaxWidth(), label = { Text("ملاحظات الفاتورة") }, minLines = 2)
                Button(
                    onClick = {
                        scope.launch {
                            isSaving = true
                            message = null
                            try {
                                val number = (if (kind == "SALE") "S-" else "P-") + System.currentTimeMillis().toString()
                                val result = repository.postInvoice(
                                    invoiceNumber = number,
                                    kind = kind,
                                    partyId = selectedPartyId,
                                    currencyCode = currencyCode,
                                    exchangeRateSnapshot = if (currencyCode == "SYP") "1" else exchangeRate,
                                    lines = draftLines.toList(),
                                    invoiceDiscount = invoiceDiscount,
                                    paidAmount = paidAmount,
                                    note = note
                                )
                                draftLines.clear()
                                invoiceDiscount = "0"
                                paidAmount = "0"
                                note = ""
                                selectedProductId = null
                                selectedUnitName = null
                                selectedPartyId = null
                                message = "تم ترحيل الفاتورة " + result.invoiceNumber + " — المتبقي: " + result.remaining + " " + currencyCode
                            } catch (error: Exception) {
                                message = error.message ?: "تعذر ترحيل الفاتورة"
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    enabled = !isSaving && draftLines.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isSaving) "جارٍ ترحيل الفاتورة..." else "حفظ وترحيل الفاتورة")
                }
                message?.let {
                    Text(it, color = if (it.startsWith("تم")) Color(0xFF287A4B) else Color(0xFFB42332), fontSize = 13.sp)
                }
            }
        }

        val recentInvoices = invoices.filter { it.kind == kind }.take(10)
        Text("آخر الفواتير (" + recentInvoices.size + ")", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
        recentInvoices.forEach { invoice ->
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(invoice.invoiceNumber, color = Ink, fontWeight = FontWeight.Bold)
                    Text("الإجمالي: " + invoice.total + " " + invoice.currencyCode, color = BrandBlue, fontSize = 13.sp)
                    Text("المدفوع: " + invoice.paid + " — المتبقي: " + invoice.remaining + " " + invoice.currencyCode, color = Color(0xFF64748B), fontSize = 12.sp)
                    TextButton(
                        enabled = !isExportingPdf,
                        onClick = {
                            pendingPdfInvoiceId = invoice.id
                            val safeNumber = invoice.invoiceNumber.replace(Regex("[^A-Za-z0-9_-]"), "_")
                            pdfExportLauncher.launch("GoodGirl_" + safeNumber + ".pdf")
                        }
                    ) { Text("تصدير PDF") }
                    if (invoice.remaining.toBigDecimalOrNull()?.compareTo(BigDecimal.ZERO) == 1) {
                        TextButton(onClick = {
                            paymentInvoiceId = invoice.id
                            paymentAmount = invoice.remaining
                            paymentNote = ""
                            paymentMessage = null
                        }) { Text(if (kind == "SALE") "تسجيل قبض" else "تسجيل دفع") }
                    }
                }
            }
        }

        val paymentInvoice = recentInvoices.firstOrNull { it.id == paymentInvoiceId }
        if (paymentInvoice != null) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(
                        (if (kind == "SALE") "قبض دفعة من " else "دفع دفعة إلى ") + paymentInvoice.invoiceNumber,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Ink
                    )
                    Text("المتبقي الحالي: " + paymentInvoice.remaining + " " + paymentInvoice.currencyCode, color = BrandBlue)
                    OutlinedTextField(paymentAmount, { paymentAmount = it }, modifier = Modifier.fillMaxWidth(), label = { Text("قيمة الدفعة") }, singleLine = true)
                    OutlinedTextField(paymentNote, { paymentNote = it }, modifier = Modifier.fillMaxWidth(), label = { Text("ملاحظة الدفعة") }, minLines = 2)
                    Button(
                        onClick = {
                            scope.launch {
                                isSavingPayment = true
                                paymentMessage = null
                                try {
                                    val result = repository.recordPayment(
                                        invoiceId = paymentInvoice.id,
                                        amount = paymentAmount,
                                        note = paymentNote
                                    )
                                    paymentMessage = "تم تسجيل الدفعة. المدفوع: " + result.paid + " — المتبقي: " + result.remaining + " " + result.currencyCode
                                    paymentInvoiceId = null
                                    paymentAmount = ""
                                    paymentNote = ""
                                } catch (error: Exception) {
                                    paymentMessage = error.message ?: "تعذر تسجيل الدفعة"
                                } finally {
                                    isSavingPayment = false
                                }
                            }
                        },
                        enabled = !isSavingPayment,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isSavingPayment) "جارٍ تسجيل الدفعة..." else "تأكيد الدفعة")
                    }
                    TextButton(onClick = { paymentInvoiceId = null }) { Text("إلغاء") }
                }
            }
        }
        pdfMessage?.let {
            Text(it, color = if (it.startsWith("تم")) Color(0xFF287A4B) else Color(0xFFB42332), fontSize = 13.sp)
        }
        paymentMessage?.let {
            Text(it, color = if (it.startsWith("تم")) Color(0xFF287A4B) else Color(0xFFB42332), fontSize = 13.sp)
        }
    }
}


@Composable
private fun FinancialReportsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val database = remember(context) { AccountingDatabase.getInstance(context) }
    val invoices by database.invoiceDao().observeAll().collectAsState(initial = emptyList())
    val invoiceLines by database.invoiceDao().observeAllLines().collectAsState(initial = emptyList())
    val ledgerEntries by database.ledgerDao().observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var selectedPeriod by remember { mutableStateOf("TODAY") }
    var isExportingReport by remember { mutableStateOf(false) }
    var reportExportMessage by remember { mutableStateOf<String?>(null) }
    val now = System.currentTimeMillis()
    val fromTime = remember(selectedPeriod) {
        when (selectedPeriod) {
            "TODAY" -> java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            "MONTH" -> java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.DAY_OF_MONTH, 1)
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            else -> Long.MIN_VALUE
        }
    }
    val report = remember(selectedPeriod, invoices, invoiceLines, ledgerEntries) {
        FinancialReportCalculator.calculate(
            fromInclusive = fromTime,
            toInclusive = now,
            invoices = invoices,
            lines = invoiceLines,
            ledgerEntries = ledgerEntries
        )
    }

    val reportPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isExportingReport = true
                reportExportMessage = null
                try {
                    val label = when (selectedPeriod) {
                        "TODAY" -> "اليوم"
                        "MONTH" -> "هذا الشهر"
                        else -> "كل المدة"
                    }
                    FinancialReportPdfExporter.write(
                        context = context,
                        destination = uri,
                        report = report,
                        periodLabel = label
                    )
                    reportExportMessage = "تم حفظ تقرير PDF بنجاح."
                } catch (error: Exception) {
                    reportExportMessage = error.message ?: "تعذر إنشاء تقرير PDF"
                } finally {
                    isExportingReport = false
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("العودة إلى لوحة التحكم") }
        Text("التقارير والأرباح", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Ink)
        Button(
            onClick = {
                val periodSuffix = when (selectedPeriod) {
                    "TODAY" -> "Today"
                    "MONTH" -> "Month"
                    else -> "All"
                }
                val dateSuffix = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                reportPdfLauncher.launch("GoodGirl_Report_" + periodSuffix + "_" + dateSuffix + ".pdf")
            },
            enabled = !isExportingReport,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isExportingReport) "جارٍ تجهيز التقرير..." else "تصدير التقرير إلى PDF")
        }
        reportExportMessage?.let {
            Text(it, color = if (it.startsWith("تم")) Color(0xFF287A4B) else Color(0xFFB42332), fontSize = 13.sp)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = { selectedPeriod = "TODAY" }, enabled = selectedPeriod != "TODAY") { Text("اليوم") }
            TextButton(onClick = { selectedPeriod = "MONTH" }, enabled = selectedPeriod != "MONTH") { Text("هذا الشهر") }
            TextButton(onClick = { selectedPeriod = "ALL" }, enabled = selectedPeriod != "ALL") { Text("كل المدة") }
        }
        Text(
            when (selectedPeriod) {
                "TODAY" -> "نتائج اليوم حتى الآن"
                "MONTH" -> "نتائج الشهر الحالي حتى الآن"
                else -> "إجمالي النتائج منذ بدء التسجيل"
            },
            color = Color(0xFF64748B),
            fontSize = 13.sp
        )

        if (report.currencySummaries.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Text("لا توجد بيانات مالية ضمن الفترة المحددة.", modifier = Modifier.padding(18.dp), color = Color(0xFF64748B))
            }
        } else {
            report.currencySummaries.forEach { summary ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("العملة: " + summary.currencyCode, color = BrandBlue, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        ReportValueRow("مبيعات فواتير البيع", summary.sales, summary.currencyCode)
                        ReportValueRow("مشتريات فواتير الشراء", summary.purchases, summary.currencyCode)
                        ReportValueRow("تكلفة البضاعة المباعة", summary.costOfGoodsSold, summary.currencyCode)
                        ReportValueRow("مجمل الربح", summary.grossProfit, summary.currencyCode, strong = true)
                        ReportValueRow("إيرادات أخرى", summary.otherIncome, summary.currencyCode)
                        ReportValueRow("المصروفات", summary.expenses, summary.currencyCode)
                        androidx.compose.material3.Divider(color = LightGray)
                        ReportValueRow("صافي الربح", summary.netProfit, summary.currencyCode, strong = true)
                    }
                }
            }
        }

        Text("تحليل المبيعات حسب المادة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
        if (report.products.isEmpty()) {
            Text("لا توجد مبيعات مواد ضمن الفترة المحددة.", color = Color(0xFF64748B), fontSize = 13.sp)
        } else {
            report.products.take(20).forEach { product ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(product.productName, color = Ink, fontWeight = FontWeight.Bold)
                        Text("الكمية بوحدة الأساس: " + product.quantity.stripTrailingZeros().toPlainString(), color = Color(0xFF64748B), fontSize = 12.sp)
                        Text("صافي المبيعات: " + product.revenue.stripTrailingZeros().toPlainString() + " " + product.currencyCode, color = BrandBlue, fontSize = 12.sp)
                        Text("تكلفة البضاعة: " + product.cost.stripTrailingZeros().toPlainString() + " " + product.currencyCode, color = Color(0xFF64748B), fontSize = 12.sp)
                        Text(
                            "ربح المادة: " + product.profit.stripTrailingZeros().toPlainString() + " " + product.currencyCode,
                            color = if (product.profit >= BigDecimal.ZERO) Color(0xFF287A4B) else Color(0xFFB42332),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        Text(
            "ملاحظة: الأرباح تحسب من فواتير البيع وتكلفة المواد المحفوظة وقت البيع، وتُعرض كل عملة مستقلة دون تحويل تلقائي.",
            color = Color(0xFF64748B),
            fontSize = 12.sp
        )
    }
}

@Composable
private fun ReportValueRow(label: String, value: BigDecimal, currencyCode: String, strong: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = if (strong) Ink else Color(0xFF64748B), fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
        Text(
            value.stripTrailingZeros().toPlainString() + " " + currencyCode,
            color = if (strong) BrandBlue else Ink,
            fontWeight = if (strong) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun CashManagementScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val database = remember(context) { AccountingDatabase.getInstance(context) }
    val repository = remember(database) { CashTransactionRepository(database) }
    val entries by repository.observeEntries().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var selectedKind by remember { mutableStateOf("INCOME") }
    var amount by remember { mutableStateOf("") }
    var currencyCode by remember { mutableStateOf("SYP") }
    var exchangeRate by remember {
        mutableStateOf(context.getSharedPreferences("good_girl_settings", android.content.Context.MODE_PRIVATE).getString("usd_syp_rate", "1") ?: "1")
    }
    var description by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val cashMovements = remember(entries) {
        entries.filter { it.entryType == "CASH_IN" || it.entryType == "CASH_OUT" }
            .sortedByDescending { it.occurredAt }
    }
    val totals = remember(cashMovements) {
        val grouped = mutableMapOf<String, Pair<BigDecimal, BigDecimal>>()
        cashMovements.forEach { entry ->
            val previous = grouped[entry.currencyCode] ?: (BigDecimal.ZERO to BigDecimal.ZERO)
            val value = entry.amount.toBigDecimal()
            grouped[entry.currencyCode] = if (entry.entryType == "CASH_IN") {
                (previous.first.add(value) to previous.second)
            } else {
                (previous.first to previous.second.add(value))
            }
        }
        grouped
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("العودة إلى لوحة التحكم") }
        Text("الصندوق والمصروفات", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Ink)
        Text("تسجيل الإيرادات والمصروفات ومتابعة حركات القبض والصرف المسجلة", color = Color(0xFF64748B), fontSize = 13.sp)

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("حركة مالية جديدة", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { selectedKind = "INCOME"; message = null }, modifier = Modifier.weight(1f)) {
                        Text(if (selectedKind == "INCOME") "✓ إيراد" else "إيراد")
                    }
                    Button(onClick = { selectedKind = "EXPENSE"; message = null }, modifier = Modifier.weight(1f)) {
                        Text(if (selectedKind == "EXPENSE") "✓ مصروف" else "مصروف")
                    }
                }
                OutlinedTextField(amount, { amount = it }, modifier = Modifier.fillMaxWidth(), label = { Text("المبلغ") }, singleLine = true)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { currencyCode = "SYP"; exchangeRate = "1" }) {
                        Text(if (currencyCode == "SYP") "✓ ليرة سورية" else "ليرة سورية")
                    }
                    TextButton(onClick = { currencyCode = "USD"; exchangeRate = context.getSharedPreferences("good_girl_settings", android.content.Context.MODE_PRIVATE).getString("usd_syp_rate", "1") ?: "1" }) {
                        Text(if (currencyCode == "USD") "✓ دولار أمريكي" else "دولار أمريكي")
                    }
                }
                if (currencyCode == "USD") {
                    OutlinedTextField(exchangeRate, { exchangeRate = it }, modifier = Modifier.fillMaxWidth(), label = { Text("سعر صرف الدولار مقابل الليرة") }, singleLine = true)
                }
                OutlinedTextField(description, { description = it }, modifier = Modifier.fillMaxWidth(), label = { Text("البيان أو سبب الحركة") }, minLines = 2)
                Button(
                    onClick = {
                        scope.launch {
                            isSaving = true
                            message = null
                            try {
                                repository.recordTransaction(
                                    kind = selectedKind,
                                    amount = amount,
                                    currencyCode = currencyCode,
                                    exchangeRate = if (currencyCode == "SYP") "1" else exchangeRate,
                                    description = description
                                )
                                amount = ""
                                description = ""
                                message = if (selectedKind == "INCOME") "تم تسجيل الإيراد وإضافته إلى الصندوق" else "تم تسجيل المصروف وتخفيض الصندوق"
                            } catch (error: Exception) {
                                message = error.message ?: "تعذر حفظ الحركة"
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isSaving) "جارٍ الحفظ..." else if (selectedKind == "INCOME") "تسجيل الإيراد" else "تسجيل المصروف")
                }
                message?.let {
                    Text(it, color = if (it.startsWith("تم")) Color(0xFF287A4B) else Color(0xFFB42332), fontSize = 13.sp)
                }
            }
        }

        Text("ملخص الصندوق حسب العملة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
        if (totals.isEmpty()) {
            Text("لا توجد حركات نقدية مسجلة بعد.", color = Color(0xFF64748B), fontSize = 13.sp)
        } else {
            totals.forEach { (currency, pair) ->
                val net = pair.first.subtract(pair.second)
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(currency, color = BrandBlue, fontWeight = FontWeight.Bold)
                        Text("إجمالي المقبوض: " + pair.first.stripTrailingZeros().toPlainString(), color = Color(0xFF287A4B), fontSize = 13.sp)
                        Text("إجمالي المصروف: " + pair.second.stripTrailingZeros().toPlainString(), color = Color(0xFFB42332), fontSize = 13.sp)
                        Text("صافي الحركات: " + net.stripTrailingZeros().toPlainString(), color = Ink, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Text("آخر حركات الصندوق", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
        if (cashMovements.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Text("لا توجد حركات بعد.", modifier = Modifier.padding(18.dp), color = Color(0xFF64748B))
            }
        } else {
            cashMovements.take(30).forEach { entry ->
                val isIn = entry.entryType == "CASH_IN"
                val date = remember(entry.occurredAt) {
                    SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(entry.occurredAt))
                }
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (isIn) "قبض" else "صرف", color = if (isIn) Color(0xFF287A4B) else Color(0xFFB42332), fontWeight = FontWeight.Bold)
                        Text(entry.amount + " " + entry.currencyCode, color = Ink, fontWeight = FontWeight.SemiBold)
                        Text(date, color = Color(0xFF64748B), fontSize = 11.sp)
                        if (entry.note.isNotBlank()) Text(entry.note, color = Color(0xFF64748B), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountsAndDebtsScreen(
    onBack: () -> Unit,
    onManageParties: () -> Unit,
    onOpenStatement: (String) -> Unit
) {
    val context = LocalContext.current
    val database = remember(context) { AccountingDatabase.getInstance(context) }
    val customers by database.partyDao().observeByKind("CUSTOMER").collectAsState(initial = emptyList())
    val suppliers by database.partyDao().observeByKind("SUPPLIER").collectAsState(initial = emptyList())
    val ledgerEntries by database.ledgerDao().observeAll().collectAsState(initial = emptyList())

    val customerRows = remember(customers, ledgerEntries) {
        customers.map { party ->
            val balances = PartyBalanceCalculator.calculate(
                party.kind,
                ledgerEntries.filter { it.partyId == party.id }
            ).filterValues { it.compareTo(BigDecimal.ZERO) != 0 }
            party to balances
        }.filter { it.second.isNotEmpty() }
    }
    val supplierRows = remember(suppliers, ledgerEntries) {
        suppliers.map { party ->
            val balances = PartyBalanceCalculator.calculate(
                party.kind,
                ledgerEntries.filter { it.partyId == party.id }
            ).filterValues { it.compareTo(BigDecimal.ZERO) != 0 }
            party to balances
        }.filter { it.second.isNotEmpty() }
    }
    val customerTotals = remember(customerRows) {
        val totals = mutableMapOf<String, BigDecimal>()
        customerRows.forEach { (_, balances) ->
            balances.forEach { (currency, amount) ->
                totals[currency] = (totals[currency] ?: BigDecimal.ZERO).add(amount)
            }
        }
        totals
    }
    val supplierTotals = remember(supplierRows) {
        val totals = mutableMapOf<String, BigDecimal>()
        supplierRows.forEach { (_, balances) ->
            balances.forEach { (currency, amount) ->
                totals[currency] = (totals[currency] ?: BigDecimal.ZERO).add(amount)
            }
        }
        totals
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("العودة إلى لوحة التحكم") }
        Text("الحسابات والديون", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Ink)
        Text(
            "متابعة المبالغ المستحقة لنا من العملاء والمستحقة علينا للموردين، مع إبقاء كل عملة مستقلة.",
            color = Color(0xFF64748B),
            fontSize = 13.sp
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ديون العملاء", color = Color(0xFF64748B), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(formatCurrencyTotals(customerTotals), color = BrandBlue, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(customerRows.size.toString() + " حساب عليه رصيد", color = Color(0xFF64748B), fontSize = 11.sp)
                }
            }
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ديون الموردين", color = Color(0xFF64748B), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(formatCurrencyTotals(supplierTotals), color = Color(0xFFB42332), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(supplierRows.size.toString() + " حساب له رصيد", color = Color(0xFF64748B), fontSize = 11.sp)
                }
            }
        }

        Text("المبالغ المستحقة لنا", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
        if (customerRows.isEmpty()) {
            Text("لا توجد أرصدة مستحقة على العملاء حالياً.", color = Color(0xFF64748B), fontSize = 13.sp)
        } else {
            customerRows.forEach { (party, balances) ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenStatement(party.id) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(party.name, color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        balances.forEach { (currency, amount) ->
                            Text(
                                amount.stripTrailingZeros().toPlainString() + " " + currency,
                                color = BrandBlue,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                        Text("فتح كشف الحساب", color = BrandBlue, fontSize = 12.sp)
                    }
                }
            }
        }

        Text("المبالغ المستحقة للموردين", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
        if (supplierRows.isEmpty()) {
            Text("لا توجد أرصدة مستحقة للموردين حالياً.", color = Color(0xFF64748B), fontSize = 13.sp)
        } else {
            supplierRows.forEach { (party, balances) ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenStatement(party.id) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(party.name, color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        balances.forEach { (currency, amount) ->
                            Text(
                                amount.stripTrailingZeros().toPlainString() + " " + currency,
                                color = Color(0xFFB42332),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                        Text("فتح كشف الحساب", color = BrandBlue, fontSize = 12.sp)
                    }
                }
            }
        }

        Button(onClick = onManageParties, modifier = Modifier.fillMaxWidth()) {
            Text("إدارة العملاء والموردين")
        }
    }
}

@Composable
private fun PartyStatementScreen(partyId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val database = remember(context) { AccountingDatabase.getInstance(context) }
    val repository = remember(database) { PartyRepository(database) }
    val party by repository.observeParty(partyId).collectAsState(initial = null)
    val entries by database.ledgerDao().observeForParty(partyId).collectAsState(initial = emptyList())
    val paymentRepository = remember(database) { PartyPaymentRepository(database) }
    val scope = rememberCoroutineScope()
    var showPaymentForm by remember { mutableStateOf(false) }
    var paymentAmount by remember { mutableStateOf("") }
    var paymentCurrency by remember { mutableStateOf("SYP") }
    var paymentRate by remember {
        mutableStateOf(context.getSharedPreferences("good_girl_settings", android.content.Context.MODE_PRIVATE).getString("usd_syp_rate", "1") ?: "1")
    }
    var paymentNote by remember { mutableStateOf("") }
    var paymentMessage by remember { mutableStateOf("") }
    var isSavingPayment by remember { mutableStateOf(false) }

    val statementEntries = remember(entries, party?.kind) {
        val relevantTypes = if (party?.kind == "CUSTOMER") {
            setOf("RECEIVABLE", "RECEIVABLE_PAYMENT")
        } else {
            setOf("PAYABLE", "PAYABLE_PAYMENT")
        }
        entries.filter { it.entryType in relevantTypes }.sortedBy { it.occurredAt }
    }
    val runningRows = remember(statementEntries, party?.kind) {
        val balances = mutableMapOf<String, BigDecimal>()
        statementEntries.map { entry ->
            val previous = balances[entry.currencyCode] ?: BigDecimal.ZERO
            val amount = entry.amount.toBigDecimal()
            val increases = if (party?.kind == "CUSTOMER") {
                entry.entryType == "RECEIVABLE"
            } else {
                entry.entryType == "PAYABLE"
            }
            val updated = if (increases) previous.add(amount) else previous.subtract(amount)
            balances[entry.currencyCode] = updated
            Triple(entry, updated, increases)
        }.reversed()
    }

    var isExportingStatement by remember { mutableStateOf(false) }
    var statementPdfMessage by remember { mutableStateOf<String?>(null) }
    val statementPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val currentParty = party
        if (uri != null && currentParty != null) {
            scope.launch {
                isExportingStatement = true
                statementPdfMessage = null
                try {
                    PartyStatementPdfExporter.write(context, uri, currentParty, statementEntries)
                    statementPdfMessage = "تم حفظ كشف الحساب بصيغة PDF."
                } catch (error: Exception) {
                    statementPdfMessage = error.message ?: "تعذر إنشاء ملف PDF"
                } finally {
                    isExportingStatement = false
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("العودة إلى الحسابات") }
        Text("كشف الحساب", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Ink)
        if (party == null) {
            Text("الحساب غير موجود", color = Color(0xFFB42332))
        } else {
            Text(party?.name.orEmpty(), color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            val balances = PartyBalanceCalculator.calculate(party?.kind.orEmpty(), entries)
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (party?.kind == "CUSTOMER") "المبالغ المستحقة لنا" else "المبالغ المستحقة علينا", color = Color(0xFF64748B), fontSize = 13.sp)
                    if (balances.isEmpty()) {
                        Text("لا يوجد رصيد مستحق", color = Color(0xFF287A4B), fontWeight = FontWeight.Bold)
                    } else {
                        balances.forEach { (currency, amount) ->
                            Text(amount.stripTrailingZeros().toPlainString() + " " + currency, color = BrandBlue, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    }
                }
            }
            Button(
                onClick = {
                    val safeName = party?.name.orEmpty().replace(Regex("[^A-Za-z0-9_-]"), "_").take(30).ifBlank { "Account" }
                    val dateSuffix = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                    statementPdfLauncher.launch("GoodGirl_Statement_" + safeName + "_" + dateSuffix + ".pdf")
                },
                enabled = !isExportingStatement,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isExportingStatement) "جارٍ تجهيز كشف الحساب..." else "تصدير كشف الحساب PDF")
            }
            if (statementPdfMessage != null) {
                Text(statementPdfMessage.orEmpty(), color = if (statementPdfMessage!!.startsWith("تم")) Color(0xFF287A4B) else Color(0xFFB42332))
            }
            Button(onClick = {
                showPaymentForm = !showPaymentForm
                paymentMessage = ""
                if (party?.currencyCode == "SYP") {
                    paymentCurrency = "SYP"
                    paymentRate = "1"
                } else if (party?.currencyCode == "USD") {
                    paymentCurrency = "USD"
                    paymentRate = context.getSharedPreferences("good_girl_settings", android.content.Context.MODE_PRIVATE).getString("usd_syp_rate", "1") ?: "1"
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text(if (party?.kind == "CUSTOMER") "تسجيل تحصيل مستقل" else "تسجيل دفعة مستقلة")
            }
            if (showPaymentForm) {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("تسجيل حركة مالية للحساب", color = Ink, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            paymentAmount,
                            { paymentAmount = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("المبلغ") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            paymentCurrency,
                            {
                                paymentCurrency = it.uppercase()
                                paymentRate = when (paymentCurrency) {
                                    "SYP" -> "1"
                                    "USD" -> context.getSharedPreferences("good_girl_settings", android.content.Context.MODE_PRIVATE)
                                        .getString("usd_syp_rate", "1") ?: "1"
                                    else -> paymentRate
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("العملة (SYP أو USD)") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            paymentRate,
                            { paymentRate = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("سعر الصرف وقت الحركة") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            paymentNote,
                            { paymentNote = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("البيان / الملاحظة") },
                            minLines = 2
                        )
                        if (paymentMessage.isNotBlank()) {
                            Text(paymentMessage, color = if (paymentMessage.startsWith("تم")) Color(0xFF287A4B) else Color(0xFFB42332))
                        }
                        Button(
                            onClick = {
                                val currentParty = party ?: return@Button
                                scope.launch {
                                    isSavingPayment = true
                                    paymentMessage = ""
                                    try {
                                        paymentRepository.recordPayment(
                                            partyId = currentParty.id,
                                            amount = paymentAmount,
                                            currencyCode = paymentCurrency,
                                            exchangeRate = paymentRate,
                                            note = paymentNote
                                        )
                                        paymentMessage = "تم تسجيل الحركة وتحديث الصندوق وكشف الحساب"
                                        paymentAmount = ""
                                        paymentNote = ""
                                    } catch (error: Exception) {
                                        paymentMessage = error.message ?: "تعذر تسجيل الحركة"
                                    } finally {
                                        isSavingPayment = false
                                    }
                                }
                            },
                            enabled = !isSavingPayment,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isSavingPayment) "جارٍ الحفظ..." else "تأكيد الحركة")
                        }
                        TextButton(onClick = { showPaymentForm = false }) { Text("إغلاق") }
                    }
                }
            }
            Text("الحركات المالية", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
            if (runningRows.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Text("لا توجد حركات مالية لهذا الحساب بعد.", modifier = Modifier.padding(18.dp), color = Color(0xFF64748B))
                }
            } else {
                runningRows.forEach { (entry, balanceAfter, increases) ->
                    val dateText = remember(entry.occurredAt) {
                        SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(entry.occurredAt))
                    }
                    val title = when (entry.entryType) {
                        "RECEIVABLE" -> "فاتورة بيع / زيادة مستحق"
                        "RECEIVABLE_PAYMENT" -> "تحصيل / تخفيض المستحق"
                        "PAYABLE" -> "فاتورة شراء / زيادة مستحق"
                        "PAYABLE_PAYMENT" -> "دفعة مورد / تخفيض المستحق"
                        else -> entry.entryType
                    }
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(title, color = Ink, fontWeight = FontWeight.Bold)
                            Text(dateText, color = Color(0xFF64748B), fontSize = 11.sp)
                            Text(
                                (if (increases) "+" else "-") + entry.amount + " " + entry.currencyCode,
                                color = if (increases) Color(0xFFB42332) else Color(0xFF287A4B),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text("الرصيد بعد الحركة: " + balanceAfter.stripTrailingZeros().toPlainString() + " " + entry.currencyCode, color = BrandBlue, fontSize = 12.sp)
                            if (entry.note.isNotBlank()) Text(entry.note, color = Color(0xFF64748B), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PartyManagementScreen(onBack: () -> Unit, onOpenStatement: (String) -> Unit) {
    val context = LocalContext.current
    val database = remember(context) { AccountingDatabase.getInstance(context) }
    val repository = remember(database) { PartyRepository(database) }
    var selectedKind by remember { mutableStateOf("CUSTOMER") }
    val parties by repository.observeParties(selectedKind).collectAsState(initial = emptyList())
    val ledgerEntries by database.ledgerDao().observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var editingId by remember { mutableStateOf<String?>(null) }
    var openingBalance by remember { mutableStateOf("0") }
    var openingCurrency by remember { mutableStateOf("SYP") }
    var openingExchangeRate by remember { mutableStateOf("1") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    fun clearForm() {
        editingId = null
        openingBalance = "0"
        openingCurrency = "SYP"
        openingExchangeRate = "1"
        name = ""
        phone = ""
        address = ""
        note = ""
    }

    fun loadParty(party: PartyEntity) {
        editingId = party.id
        openingBalance = party.openingBalance
        openingCurrency = party.currencyCode
        openingExchangeRate = party.openingExchangeRate
        name = party.name
        phone = party.phone
        address = party.address
        note = party.note
        message = null
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("العودة إلى لوحة التحكم") }
        Text("العملاء والموردون", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Ink)
        Text("إدارة بيانات جهات التعامل وحفظها محلياً على الهاتف", color = Color(0xFF64748B), fontSize = 13.sp)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { selectedKind = "CUSTOMER"; clearForm(); message = null },
                modifier = Modifier.weight(1f)
            ) { Text("العملاء") }
            Button(
                onClick = { selectedKind = "SUPPLIER"; clearForm(); message = null },
                modifier = Modifier.weight(1f)
            ) { Text("الموردون") }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(
                    if (editingId == null) {
                        if (selectedKind == "CUSTOMER") "إضافة عميل" else "إضافة مورد"
                    } else {
                        if (selectedKind == "CUSTOMER") "تعديل بيانات العميل" else "تعديل بيانات المورد"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Ink
                )
                OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("الاسم *") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, modifier = Modifier.fillMaxWidth(), label = { Text("رقم الهاتف") }, singleLine = true)
                OutlinedTextField(address, { address = it }, modifier = Modifier.fillMaxWidth(), label = { Text("العنوان") }, singleLine = true)
                if (editingId == null) {
                    OutlinedTextField(openingBalance, { openingBalance = it }, modifier = Modifier.fillMaxWidth(), label = { Text("الرصيد الافتتاحي (اختياري)") }, singleLine = true)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { openingCurrency = "SYP"; openingExchangeRate = "1" }) {
                            Text(if (openingCurrency == "SYP") "✓ ليرة سورية" else "ليرة سورية")
                        }
                        TextButton(onClick = { openingCurrency = "USD"; openingExchangeRate = "1" }) {
                            Text(if (openingCurrency == "USD") "✓ دولار أمريكي" else "دولار أمريكي")
                        }
                    }
                    if (openingCurrency == "USD") {
                        OutlinedTextField(openingExchangeRate, { openingExchangeRate = it }, modifier = Modifier.fillMaxWidth(), label = { Text("سعر صرف الدولار عند تسجيل الرصيد") }, singleLine = true)
                    }
                } else {
                    Text(
                        "الرصيد الافتتاحي محفوظ بسجله ولا يتغير من شاشة بيانات الحساب.",
                        color = Color(0xFF64748B),
                        fontSize = 12.sp
                    )
                }
                OutlinedTextField(note, { note = it }, modifier = Modifier.fillMaxWidth(), label = { Text("ملاحظات") }, minLines = 2)
                Button(
                    onClick = {
                        scope.launch {
                            isSaving = true
                            message = null
                            try {
                                val wasEditing = editingId != null
                                repository.saveParty(
                                    id = editingId,
                                    kind = selectedKind,
                                    name = name,
                                    phone = phone,
                                    address = address,
                                    note = note,
                                    openingBalance = openingBalance,
                                    currencyCode = openingCurrency,
                                    openingExchangeRate = openingExchangeRate
                                )
                                clearForm()
                                message = if (wasEditing) "تم تحديث البيانات" else "تم حفظ البيانات"
                            } catch (error: Exception) {
                                message = error.message ?: "تعذر حفظ البيانات"
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isSaving) "جارٍ الحفظ..." else if (editingId == null) "حفظ" else "حفظ التعديلات")
                }
                if (editingId != null) {
                    TextButton(onClick = { clearForm(); message = null }) { Text("إلغاء التعديل") }
                }
                message?.let {
                    Text(it, color = if (it.startsWith("تم")) Color(0xFF287A4B) else Color(0xFFB42332), fontSize = 13.sp)
                }
            }
        }

        Text(
            (if (selectedKind == "CUSTOMER") "قائمة العملاء (" else "قائمة الموردين (") + parties.size + ")",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Ink
        )
        if (parties.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Text("لا توجد سجلات بعد. أضف أول جهة تعامل من النموذج أعلاه.", modifier = Modifier.padding(18.dp), color = Color(0xFF64748B))
            }
        } else {
            parties.forEach { party ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(party.name, color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            TextButton(onClick = { loadParty(party) }) { Text("تعديل") }
                        }
                        if (party.phone.isNotBlank()) Text("الهاتف: " + party.phone, color = Color(0xFF64748B), fontSize = 12.sp)
                        if (party.address.isNotBlank()) Text("العنوان: " + party.address, color = Color(0xFF64748B), fontSize = 12.sp)
                        val balances = PartyBalanceCalculator.calculate(party.kind, ledgerEntries.filter { it.partyId == party.id })
                        balances.forEach { (currency, balance) ->
                            val label = if (party.kind == "CUSTOMER") "مستحق لنا" else "مستحق علينا"
                            Text(label + ": " + balance.stripTrailingZeros().toPlainString() + " " + currency, color = BrandBlue, fontSize = 12.sp)
                        }
                        TextButton(onClick = { onOpenStatement(party.id) }) { Text("كشف الحساب") }
                        if (party.note.isNotBlank()) Text("ملاحظة: " + party.note, color = Color(0xFF64748B), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun StockHistoryScreen(productId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val database = remember(context) { AccountingDatabase.getInstance(context) }
    val product by database.productDao().observeProduct(productId).collectAsState(initial = null)
    val movements by database.stockMovementDao().observeForProduct(productId).collectAsState(initial = emptyList())

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("العودة إلى المنتجات") }
        Text("سجل حركة المخزون", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Ink)
        if (product == null) {
            Text("المنتج غير موجود", color = Color(0xFFB42332))
        } else {
            Text(product?.name.orEmpty(), color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("الرصيد الحالي", color = Color(0xFF64748B), fontSize = 12.sp)
                    Text(
                        product?.stockQuantity + " " + product?.baseUnit,
                        color = BrandBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                }
            }
            Text("عدد الحركات: ${movements.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
            if (movements.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Text("لا توجد حركات مسجلة لهذا المنتج بعد.", modifier = Modifier.padding(18.dp), color = Color(0xFF64748B))
                }
            } else {
                movements.forEach { movement ->
                    val isIncrease = movement.quantityDelta.toBigDecimal() > BigDecimal.ZERO
                    val quantity = movement.quantityDelta.toBigDecimal().abs().stripTrailingZeros().toPlainString()
                    val date = remember(movement.occurredAt) {
                        SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(movement.occurredAt))
                    }
                    val sourceLabel = when (movement.sourceType) {
                        "OPENING_BALANCE" -> "رصيد افتتاحي"
                        "MANUAL_ADJUSTMENT" -> "تسوية مخزون يدوية"
                        "SALE_INVOICE" -> "فاتورة بيع"
                        "PURCHASE_INVOICE" -> "فاتورة شراء"
                        else -> "حركة مخزون"
                    }
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(sourceLabel, color = Ink, fontWeight = FontWeight.Bold)
                                Text(
                                    (if (isIncrease) "+" else "-") + quantity + " " + (product?.baseUnit ?: ""),
                                    color = if (isIncrease) Color(0xFF287A4B) else Color(0xFFB42332),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(date, color = Color(0xFF64748B), fontSize = 11.sp)
                            Text(
                                "كلفة وحدة الأساس: " + movement.unitCostSnapshot + " " + movement.currencyCode,
                                color = Color(0xFF64748B),
                                fontSize = 12.sp
                            )
                            if (movement.note.isNotBlank()) {
                                Text(movement.note, color = Color(0xFF64748B), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductCategoriesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val database = remember(context) { AccountingDatabase.getInstance(context) }
    val repository = remember(database) { CategoryRepository(database) }
    val categories by repository.observeCategories().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var editingId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    fun clearForm() {
        editingId = null
        name = ""
        note = ""
    }

    fun loadCategory(category: CategoryEntity) {
        editingId = category.id
        name = category.name
        note = category.note
        message = null
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("العودة إلى المنتجات") }
        Text("تصنيفات المنتجات", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Ink)
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(if (editingId == null) "إضافة تصنيف" else "تعديل التصنيف", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink)
                OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("اسم التصنيف *") }, singleLine = true)
                OutlinedTextField(note, { note = it }, modifier = Modifier.fillMaxWidth(), label = { Text("ملاحظات") }, minLines = 2)
                Button(
                    onClick = {
                        scope.launch {
                            isSaving = true
                            message = null
                            try {
                                repository.saveCategory(editingId, name, note)
                                val wasEditing = editingId != null
                                clearForm()
                                message = if (wasEditing) "تم تحديث التصنيف" else "تمت إضافة التصنيف"
                            } catch (error: Exception) {
                                message = error.message ?: "تعذر حفظ التصنيف"
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isSaving) "جارٍ الحفظ..." else if (editingId == null) "إضافة التصنيف" else "حفظ التعديلات")
                }
                if (editingId != null) {
                    TextButton(onClick = { clearForm(); message = null }) { Text("إلغاء التعديل") }
                }
                message?.let {
                    Text(it, color = if (it.startsWith("تم")) Color(0xFF287A4B) else Color(0xFFB42332), fontSize = 13.sp)
                }
            }
        }
        Text("التصنيفات المسجلة (${categories.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
        if (categories.isEmpty()) {
            Text("لم تتم إضافة تصنيفات بعد. يمكنك ترك المنتجات دون تصنيف.", color = Color(0xFF64748B), fontSize = 13.sp)
        } else {
            categories.forEach { category ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(category.name, color = Ink, fontWeight = FontWeight.Bold)
                            if (category.note.isNotBlank()) Text(category.note, color = Color(0xFF64748B), fontSize = 12.sp)
                        }
                        TextButton(onClick = { loadCategory(category) }) { Text("تعديل") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductUnitsScreen(productId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val database = remember(context) { AccountingDatabase.getInstance(context) }
    val repository = remember(database) { ProductUnitRepository(database) }
    val product by database.productDao().observeProduct(productId).collectAsState(initial = null)
    val units by repository.observeUnits(productId).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var unitName by remember { mutableStateOf("") }
    var factor by remember { mutableStateOf("1") }
    var message by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("العودة إلى المنتجات") }
        Text("وحدات قياس المنتج", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Ink)
        if (product == null) {
            Text("المنتج غير موجود", color = Color(0xFFB42332))
        } else {
            Text(product?.name.orEmpty(), color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                "وحدة الأساس: " + product?.baseUnit + " — معامل التحويل يحدد عدد وحدات الأساس في الوحدة الإضافية.",
                color = Color(0xFF64748B),
                fontSize = 13.sp
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("إضافة وحدة جديدة", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink)
                    OutlinedTextField(unitName, { unitName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("اسم الوحدة الإضافية") }, singleLine = true)
                    OutlinedTextField(factor, { factor = it }, modifier = Modifier.fillMaxWidth(), label = { Text("كم وحدة أساس تحتوي؟") }, singleLine = true)
                    Text(
                        "مثال: وحدة الأساس قطعة، والكرتونة تحتوي 12 قطعة؛ اكتب 12.",
                        color = Color(0xFF64748B),
                        fontSize = 12.sp
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                isSaving = true
                                message = null
                                try {
                                    repository.addAlternateUnit(productId, unitName, factor)
                                    unitName = ""
                                    factor = "1"
                                    message = "تمت إضافة وحدة القياس"
                                } catch (error: Exception) {
                                    message = error.message ?: "تعذر إضافة الوحدة"
                                } finally {
                                    isSaving = false
                                }
                            }
                        },
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (isSaving) "جارٍ الحفظ..." else "إضافة الوحدة") }
                    message?.let {
                        Text(it, color = if (it.startsWith("تم")) Color(0xFF287A4B) else Color(0xFFB42332), fontSize = 13.sp)
                    }
                }
            }
            Text("الوحدات المتاحة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
            units.forEach { unit ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(unit.unitName, color = Ink, fontWeight = FontWeight.Bold)
                        Text(
                            if (unit.isBaseUnit) "وحدة الأساس" else "1 " + unit.unitName + " = " + unit.baseFactor + " " + product?.baseUnit,
                            color = BrandBlue,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductManagementScreen(
    onBack: () -> Unit,
    onManageUnits: (String) -> Unit,
    onManageCategories: () -> Unit,
    onViewHistory: (String) -> Unit
) {
    val context = LocalContext.current
    val database = remember(context) { AccountingDatabase.getInstance(context) }
    val repository = remember(database) { ProductRepository(database) }
    val stockRepository = remember(database) { StockRepository(database) }
    val products by repository.observeProducts().collectAsState(initial = emptyList())
    val categories by database.categoryDao().observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var sku by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("قطعة") }
    var purchaseCost by remember { mutableStateOf("0") }
    var salePrice by remember { mutableStateOf("0") }
    var priceCurrencyCode by remember { mutableStateOf("SYP") }
    var openingStock by remember { mutableStateOf("0") }
    var lowStockThreshold by remember { mutableStateOf("0") }
    var searchQuery by remember { mutableStateOf("") }
    var showLowStockOnly by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var adjustingProductId by remember { mutableStateOf<String?>(null) }
    var adjustmentDelta by remember { mutableStateOf("") }
    var adjustmentCost by remember { mutableStateOf("0") }
    var adjustmentNote by remember { mutableStateOf("") }
    var stockMessage by remember { mutableStateOf<String?>(null) }
    var isSavingStock by remember { mutableStateOf(false) }

    fun resetForm() {
        editingId = null
        selectedCategoryId = null
        name = ""
        sku = ""
        unit = "قطعة"
        purchaseCost = "0"
        salePrice = "0"
        priceCurrencyCode = "SYP"
        openingStock = "0"
        lowStockThreshold = "0"
        note = ""
    }

    fun startEditing(product: ProductEntity) {
        adjustingProductId = null
        stockMessage = null
        editingId = product.id
        selectedCategoryId = product.categoryId
        name = product.name
        sku = product.sku
        unit = product.baseUnit
        purchaseCost = product.purchaseCost
        salePrice = product.salePrice
        priceCurrencyCode = product.priceCurrencyCode
        lowStockThreshold = product.lowStockThreshold
        note = product.note
        message = null
    }

    fun startStockAdjustment(product: ProductEntity) {
        editingId = null
        adjustingProductId = product.id
        adjustmentDelta = ""
        adjustmentCost = product.purchaseCost
        adjustmentNote = ""
        stockMessage = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("العودة إلى لوحة التحكم") }
        Text("المنتجات والمخزون", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Ink)
        Text("إضافة المواد وحفظ أرصدتها على الهاتف دون الحاجة إلى الإنترنت", color = Color(0xFF64748B), fontSize = 13.sp)
        TextButton(onClick = onManageCategories) { Text("إدارة تصنيفات المنتجات") }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text(if (editingId == null) "إضافة منتج جديد" else "تعديل بيانات المنتج", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink)
                OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("اسم المنتج *") }, singleLine = true)
                OutlinedTextField(sku, { sku = it }, modifier = Modifier.fillMaxWidth(), label = { Text("رمز المنتج (اختياري)") }, singleLine = true)
                Box {
                    Button(onClick = { categoryMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(categories.firstOrNull { it.id == selectedCategoryId }?.name ?: "بدون تصنيف")
                    }
                    DropdownMenu(expanded = categoryMenuExpanded, onDismissRequest = { categoryMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("بدون تصنيف") },
                            onClick = { selectedCategoryId = null; categoryMenuExpanded = false }
                        )
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = { selectedCategoryId = category.id; categoryMenuExpanded = false }
                            )
                        }
                    }
                }
                OutlinedTextField(unit, { unit = it }, modifier = Modifier.fillMaxWidth(), label = { Text("وحدة القياس الأساسية") }, singleLine = true)
                OutlinedTextField(purchaseCost, { purchaseCost = it }, modifier = Modifier.fillMaxWidth(), label = { Text("كلفة الوحدة") }, singleLine = true)
                OutlinedTextField(salePrice, { salePrice = it }, modifier = Modifier.fillMaxWidth(), label = { Text("سعر البيع") }, singleLine = true)
                Text("عملة الكلفة وسعر البيع", color = Color(0xFF64748B), fontSize = 12.sp)
                val editingProduct = products.firstOrNull { it.id == editingId }
                val canChangeCurrency = editingProduct == null ||
                    (editingProduct.stockQuantity.toBigDecimalOrNull()?.compareTo(BigDecimal.ZERO) == 0)
                if (canChangeCurrency) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { priceCurrencyCode = "SYP" }) {
                            Text(if (priceCurrencyCode == "SYP") "✓ ليرة سورية" else "ليرة سورية")
                        }
                        TextButton(onClick = { priceCurrencyCode = "USD" }) {
                            Text(if (priceCurrencyCode == "USD") "✓ دولار أمريكي" else "دولار أمريكي")
                        }
                    }
                } else {
                    Text("عملة المنتج ثابتة ما دام له رصيد مخزني: " + priceCurrencyCode, color = Color(0xFF64748B), fontSize = 12.sp)
                }
                if (editingId == null) {
                    OutlinedTextField(openingStock, { openingStock = it }, modifier = Modifier.fillMaxWidth(), label = { Text("الرصيد الافتتاحي") }, singleLine = true)
                }
                OutlinedTextField(
                    lowStockThreshold,
                    { lowStockThreshold = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("حد تنبيه انخفاض المخزون (0 لتعطيل التنبيه)") },
                    singleLine = true
                )
                if (editingId != null) {
                    val currentStock = products.firstOrNull { it.id == editingId }?.stockQuantity ?: "—"
                    Text("الرصيد الحالي: $currentStock — يُعدّل عبر حركات المخزون وليس من بطاقة المنتج.", color = Color(0xFF64748B), fontSize = 12.sp)
                }
                OutlinedTextField(note, { note = it }, modifier = Modifier.fillMaxWidth(), label = { Text("ملاحظات") }, minLines = 2)

                Button(
                    onClick = {
                        scope.launch {
                            isSaving = true
                            message = null
                            try {
                                val wasEditing = editingId != null
                                if (wasEditing) {
                                    val id = editingId ?: return@launch
                                    val updated = repository.updateProduct(
                                        id = id,
                                        sku = sku,
                                        name = name,
                                        unit = unit,
                                        purchaseCost = purchaseCost,
                                        salePrice = salePrice,
                                        note = note,
                                        priceCurrencyCode = priceCurrencyCode,
                                        categoryId = selectedCategoryId,
                                        lowStockThreshold = lowStockThreshold
                                    )
                                    check(updated) { "تعذر العثور على المنتج لتحديثه" }
                                } else {
                                    repository.addProduct(
                                        sku = sku,
                                        name = name,
                                        unit = unit,
                                        purchaseCost = purchaseCost,
                                        salePrice = salePrice,
                                        openingStock = openingStock,
                                        note = note,
                                        priceCurrencyCode = priceCurrencyCode,
                                        categoryId = selectedCategoryId,
                                        lowStockThreshold = lowStockThreshold
                                    )
                                }
                                resetForm()
                                message = if (wasEditing) "تم تحديث بيانات المنتج بنجاح، دون تغيير حركاته السابقة" else "تم حفظ المنتج ورصيده الافتتاحي بنجاح"
                            } catch (error: Exception) {
                                message = error.message ?: "تعذر حفظ المنتج"
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isSaving) "جارٍ الحفظ..." else if (editingId != null) "حفظ التعديلات" else "حفظ المنتج")
                }
                message?.let {
                    Text(
                        it,
                        color = if (it.startsWith("تم")) Color(0xFF287A4B) else Color(0xFFB42332),
                        fontSize = 13.sp
                    )
                }
                if (editingId != null) {
                    TextButton(onClick = { resetForm(); message = null }) { Text("إلغاء التعديل") }
                }
            }
        }


        val adjustingProduct = products.firstOrNull { it.id == adjustingProductId }
        if (adjustingProduct != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Text("حركة مخزون: " + adjustingProduct.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink)
                    Text(
                        "الرصيد الحالي: " + adjustingProduct.stockQuantity + " " + adjustingProduct.baseUnit,
                        color = BrandBlue,
                        fontSize = 13.sp
                    )
                    Text("اكتب كمية موجبة للإضافة أو سالبة للسحب، مثل 5 أو -2.", color = Color(0xFF64748B), fontSize = 12.sp)
                    OutlinedTextField(
                        adjustmentDelta,
                        { adjustmentDelta = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("كمية الحركة (+ إضافة / - سحب)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        adjustmentCost,
                        { adjustmentCost = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("كلفة الوحدة لهذه الحركة") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        adjustmentNote,
                        { adjustmentNote = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("سبب الحركة أو ملاحظات") },
                        minLines = 2
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                isSavingStock = true
                                stockMessage = null
                                try {
                                    val newBalance = stockRepository.adjustStock(
                                        productId = adjustingProduct.id,
                                        quantityDelta = adjustmentDelta,
                                        unitCost = adjustmentCost,
                                        note = adjustmentNote
                                    )
                                    stockMessage = "تم تحديث الرصيد إلى " + newBalance
                                    adjustingProductId = null
                                } catch (error: Exception) {
                                    stockMessage = error.message ?: "تعذر تسجيل حركة المخزون"
                                } finally {
                                    isSavingStock = false
                                }
                            }
                        },
                        enabled = !isSavingStock,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isSavingStock) "جارٍ حفظ الحركة..." else "حفظ حركة المخزون")
                    }
                    TextButton(onClick = { adjustingProductId = null; stockMessage = null }) {
                        Text("إلغاء")
                    }
                }
            }
        }
        stockMessage?.let {
            Text(
                it,
                color = if (it.startsWith("تم")) Color(0xFF287A4B) else Color(0xFFB42332),
                fontSize = 13.sp
            )
        }

        val lowStockProducts = products.filter {
            ProductInputRules.isLowStock(it.stockQuantity, it.lowStockThreshold)
        }
        Text("قائمة المنتجات (${products.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
        if (lowStockProducts.isNotEmpty()) {
            Text(
                "مواد منخفضة المخزون: " + lowStockProducts.size,
                color = Color(0xFFB42332),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
        OutlinedTextField(
            searchQuery,
            { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("بحث باسم المنتج أو الرمز أو التصنيف") },
            singleLine = true
        )
        TextButton(onClick = { showLowStockOnly = !showLowStockOnly }) {
            Text(if (showLowStockOnly) "عرض جميع المنتجات" else "عرض المواد منخفضة المخزون فقط")
        }
        val normalizedSearch = searchQuery.trim().lowercase()
        val visibleProducts = products.filter { product ->
            val categoryName = categories.firstOrNull { it.id == product.categoryId }?.name.orEmpty()
            val matchesSearch = normalizedSearch.isBlank() ||
                product.name.lowercase().contains(normalizedSearch) ||
                product.sku.lowercase().contains(normalizedSearch) ||
                categoryName.lowercase().contains(normalizedSearch)
            val matchesStock = !showLowStockOnly ||
                ProductInputRules.isLowStock(product.stockQuantity, product.lowStockThreshold)
            matchesSearch && matchesStock
        }
        if (products.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Text("لا توجد منتجات مسجلة بعد. أضف أول منتج من النموذج أعلاه.", modifier = Modifier.padding(18.dp), color = Color(0xFF64748B))
            }
        } else if (visibleProducts.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Text("لا توجد منتجات مطابقة لمعايير البحث أو التصفية.", modifier = Modifier.padding(18.dp), color = Color(0xFF64748B))
            }
        } else {
            visibleProducts.forEach { product ->
                ProductListItem(
                    product,
                    categoryName = categories.firstOrNull { it.id == product.categoryId }?.name,
                    isLowStock = ProductInputRules.isLowStock(product.stockQuantity, product.lowStockThreshold),
                    onEdit = { startEditing(product) },
                    onStockAdjustment = { startStockAdjustment(product) },
                    onManageUnits = { onManageUnits(product.id) },
                    onViewHistory = { onViewHistory(product.id) }
                )
            }
        }
    }
}

@Composable
private fun ProductListItem(
    product: ProductEntity,
    categoryName: String?,
    isLowStock: Boolean,
    onEdit: () -> Unit,
    onStockAdjustment: () -> Unit,
    onManageUnits: () -> Unit,
    onViewHistory: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(product.name, color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(onClick = onEdit) { Text("تعديل") }
                    TextButton(onClick = onStockAdjustment) { Text("حركة مخزون") }
                    TextButton(onClick = onManageUnits) { Text("الوحدات") }
                    TextButton(onClick = onViewHistory) { Text("السجل") }
                }
            }
            if (product.sku.isNotBlank()) {
                Text("الرمز: ${product.sku}", color = Color(0xFF64748B), fontSize = 12.sp)
            }
            if (!categoryName.isNullOrBlank()) {
                Text("التصنيف: " + categoryName, color = Color(0xFF64748B), fontSize = 12.sp)
            }
            if (isLowStock) {
                Text("تنبيه: المخزون منخفض", color = Color(0xFFB42332), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("الرصيد: ${product.stockQuantity} ${product.baseUnit}", color = BrandBlue, fontSize = 13.sp)
                Text("سعر البيع: ${product.salePrice} ${product.priceCurrencyCode}", color = Color(0xFF287A4B), fontSize = 13.sp)
            }
            Text("الكلفة: ${product.purchaseCost} ${product.priceCurrencyCode}", color = Color(0xFF64748B), fontSize = 12.sp)
        }
    }
}


private data class DailySaleRow(
    val invoiceNumber: String,
    val issuedAt: Long,
    val productName: String,
    val quantity: String,
    val unitName: String,
    val unitPrice: String,
    val currencyCode: String,
    val netSales: BigDecimal,
    val profit: BigDecimal,
    val note: String
)

@Composable
private fun DailySalesScreen(onBack: () -> Unit, onOpenFullInvoice: () -> Unit) {
    val context = LocalContext.current
    val database = remember(context) { AccountingDatabase.getInstance(context) }
    val repository = remember(database) { InvoiceRepository(database) }
    val invoices by database.invoiceDao().observeAll().collectAsState(initial = emptyList())
    val invoiceLines by database.invoiceDao().observeAllLines().collectAsState(initial = emptyList())
    val products by database.productDao().observeActiveProducts().collectAsState(initial = emptyList())
    val customers by database.partyDao().observeByKind("CUSTOMER").collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var selectedDay by remember {
        mutableStateOf(
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        )
    }
    var selectedProductId by remember { mutableStateOf<String?>(null) }
    var selectedUnitName by remember { mutableStateOf<String?>(null) }
    var selectedPartyId by remember { mutableStateOf<String?>(null) }
    var productMenuExpanded by remember { mutableStateOf(false) }
    var unitMenuExpanded by remember { mutableStateOf(false) }
    var partyMenuExpanded by remember { mutableStateOf(false) }
    var quantity by remember { mutableStateOf("1") }
    var salePrice by remember { mutableStateOf("0") }
    var lineDiscount by remember { mutableStateOf("0") }
    var exchangeRate by remember {
        mutableStateOf(context.getSharedPreferences("good_girl_settings", android.content.Context.MODE_PRIVATE).getString("usd_syp_rate", "1") ?: "1")
    }
    var paidAmount by remember { mutableStateOf("") }
    var paidAmountTouched by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val productUnits by database.productUnitDao()
        .observeForProduct(selectedProductId ?: "")
        .collectAsState(initial = emptyList())
    val selectedProduct = products.firstOrNull { it.id == selectedProductId }
    val selectedUnit = productUnits.firstOrNull { it.unitName == selectedUnitName }
        ?: productUnits.firstOrNull { it.isBaseUnit }

    val enteredQuantity = runCatching { BigDecimal(quantity.trim()) }.getOrDefault(BigDecimal.ZERO)
    val enteredPrice = runCatching { BigDecimal(salePrice.trim()) }.getOrDefault(BigDecimal.ZERO)
    val enteredDiscount = runCatching { BigDecimal(lineDiscount.trim()) }.getOrDefault(BigDecimal.ZERO)
    val previewGross = enteredQuantity.multiply(enteredPrice)
    val previewSales = previewGross.subtract(enteredDiscount).max(BigDecimal.ZERO)
    val unitFactor = runCatching { BigDecimal(selectedUnit?.baseFactor ?: "1") }.getOrDefault(BigDecimal.ONE)
    val previewCost = enteredQuantity.multiply(unitFactor)
        .multiply(runCatching { BigDecimal(selectedProduct?.purchaseCost ?: "0") }.getOrDefault(BigDecimal.ZERO))
    val previewProfit = previewSales.subtract(previewCost)
    val effectivePaidAmount = if (paidAmountTouched) paidAmount else previewSales.stripTrailingZeros().toPlainString()

    val dayBounds = remember(selectedDay) {
        val start = Calendar.getInstance().apply { timeInMillis = selectedDay }
        val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
        start.timeInMillis to end.timeInMillis
    }
    val dayLabel = remember(selectedDay) {
        SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(selectedDay))
    }
    val rows = remember(invoices, invoiceLines, dayBounds) {
        invoices.asSequence()
            .filter {
                it.kind == "SALE" && it.status == "POSTED" &&
                    it.issuedAt >= dayBounds.first && it.issuedAt < dayBounds.second
            }
            .flatMap { invoice ->
                val lines = invoiceLines.filter { it.invoiceId == invoice.id }
                val lineNetAmounts = lines.map { it.lineTotal.toBigDecimal() }
                val lineDiscountTotal = lines.fold(BigDecimal.ZERO) { total, line ->
                    total.add(line.discount.toBigDecimal())
                }
                val extraInvoiceDiscount = invoice.discount.toBigDecimal()
                    .subtract(lineDiscountTotal).max(BigDecimal.ZERO)
                val allocatedDiscounts = InvoiceDiscountAllocator.allocate(
                    lineNetAmounts,
                    extraInvoiceDiscount
                )
                lines.mapIndexed { index, line ->
                    val netSales = lineNetAmounts[index].subtract(allocatedDiscounts[index])
                    val cost = line.unitCostSnapshot.toBigDecimal()
                        .multiply(line.baseQuantity.toBigDecimal())
                    DailySaleRow(
                        invoiceNumber = invoice.invoiceNumber,
                        issuedAt = invoice.issuedAt,
                        productName = line.productNameSnapshot,
                        quantity = line.quantity,
                        unitName = line.unitSnapshot,
                        unitPrice = line.unitPriceSnapshot,
                        currencyCode = invoice.currencyCode,
                        netSales = netSales,
                        profit = netSales.subtract(cost),
                        note = invoice.note
                    )
                }
            }
            .sortedByDescending { it.issuedAt }
            .toList()
    }
    val salesTotals = remember(rows) {
        rows.groupBy { it.currencyCode }.mapValues { (_, items) ->
            items.fold(BigDecimal.ZERO) { total, row -> total.add(row.netSales) }
        }
    }
    val profitTotals = remember(rows) {
        rows.groupBy { it.currencyCode }.mapValues { (_, items) ->
            items.fold(BigDecimal.ZERO) { total, row -> total.add(row.profit) }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("العودة إلى لوحة التحكم") }
        Text(
            "المبيعات اليومية",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Ink
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = {
                selectedDay = Calendar.getInstance().apply {
                    timeInMillis = selectedDay
                    add(Calendar.DAY_OF_MONTH, -1)
                }.timeInMillis
            }) { Text("اليوم السابق") }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(dayLabel, color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                TextButton(onClick = {
                    selectedDay = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                }) { Text("العودة إلى اليوم") }
            }
            TextButton(onClick = {
                selectedDay = Calendar.getInstance().apply {
                    timeInMillis = selectedDay
                    add(Calendar.DAY_OF_MONTH, 1)
                }.timeInMillis
            }) { Text("اليوم التالي") }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text("إضافة مبيعة", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Box {
                    Button(
                        onClick = { productMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(selectedProduct?.name ?: "اختيار المادة")
                    }
                    DropdownMenu(
                        expanded = productMenuExpanded,
                        onDismissRequest = { productMenuExpanded = false }
                    ) {
                        products.forEach { product ->
                            DropdownMenuItem(
                                text = { Text(product.name + " — المتوفر: " + product.stockQuantity + " " + product.baseUnit) },
                                onClick = {
                                    selectedProductId = product.id
                                    selectedUnitName = product.baseUnit
                                    salePrice = product.salePrice
                                    lineDiscount = "0"
                                    exchangeRate = if (product.priceCurrencyCode == "SYP") "1" else (context.getSharedPreferences("good_girl_settings", android.content.Context.MODE_PRIVATE).getString("usd_syp_rate", "1") ?: "1")
                                    paidAmountTouched = false
                                    paidAmount = ""
                                    productMenuExpanded = false
                                    message = null
                                }
                            )
                        }
                    }
                }

                if (selectedProduct != null) {
                    if (productUnits.isNotEmpty()) {
                        Box {
                            Button(
                                onClick = { unitMenuExpanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("وحدة القياس: " + (selectedUnit?.unitName ?: selectedProduct.baseUnit))
                            }
                            DropdownMenu(
                                expanded = unitMenuExpanded,
                                onDismissRequest = { unitMenuExpanded = false }
                            ) {
                                productUnits.forEach { unit ->
                                    DropdownMenuItem(
                                        text = {
                                            Text("1 " + unit.unitName + " = " + unit.baseFactor + " " + selectedProduct.baseUnit)
                                        },
                                        onClick = {
                                            selectedUnitName = unit.unitName
                                            val factor = BigDecimal(unit.baseFactor)
                                            salePrice = BigDecimal(selectedProduct.salePrice)
                                                .multiply(factor)
                                                .stripTrailingZeros()
                                                .toPlainString()
                                            unitMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        "عملة المادة: " + selectedProduct.priceCurrencyCode,
                        color = Color(0xFF64748B),
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        quantity,
                        { quantity = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("الكمية المباعة") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        salePrice,
                        { salePrice = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("سعر المبيع للوحدة") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        lineDiscount,
                        { lineDiscount = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("خصم المبيعة (اختياري)") },
                        singleLine = true
                    )
                    Text(
                        "قيمة المبيع بعد الخصم: " + previewSales.stripTrailingZeros().toPlainString() +
                            " " + selectedProduct.priceCurrencyCode,
                        color = BrandBlue,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "الربح التقديري: " + previewProfit.stripTrailingZeros().toPlainString() +
                            " " + selectedProduct.priceCurrencyCode,
                        color = if (previewProfit >= BigDecimal.ZERO) Color(0xFF287A4B) else Color(0xFFB42332),
                        fontWeight = FontWeight.Bold
                    )
                    if (selectedProduct.priceCurrencyCode == "USD") {
                        OutlinedTextField(
                            exchangeRate,
                            { exchangeRate = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("سعر صرف الدولار مقابل الليرة") },
                            singleLine = true
                        )
                    }
                    Box {
                        Button(
                            onClick = { partyMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                customers.firstOrNull { it.id == selectedPartyId }?.name
                                    ?: "اختيار عميل (اختياري للبيع النقدي)"
                            )
                        }
                        DropdownMenu(
                            expanded = partyMenuExpanded,
                            onDismissRequest = { partyMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("بيع نقدي دون حساب عميل") },
                                onClick = {
                                    selectedPartyId = null
                                    partyMenuExpanded = false
                                }
                            )
                            customers.forEach { customer ->
                                DropdownMenuItem(
                                    text = { Text(customer.name) },
                                    onClick = {
                                        selectedPartyId = customer.id
                                        partyMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        effectivePaidAmount,
                        {
                            paidAmount = it
                            paidAmountTouched = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("المبلغ المقبوض") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        note,
                        { note = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("ملاحظات المبيعة") },
                        minLines = 2
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                isSaving = true
                                message = null
                                try {
                                    val product = selectedProduct
                                        ?: throw IllegalArgumentException("اختر المادة أولاً")
                                    val cleanQuantity = InvoicePostingRules.normalizePositiveQuantity(quantity)
                                    val cleanPrice = InvoicePostingRules.normalizeNonNegativeAmount(salePrice, "سعر المبيع")
                                    val cleanDiscount = InvoicePostingRules.normalizeNonNegativeAmount(lineDiscount, "خصم المبيعة")
                                    val cleanPaid = InvoicePostingRules.normalizeNonNegativeAmount(
                                        effectivePaidAmount,
                                        "المبلغ المقبوض"
                                    )
                                    InvoicePostingRules.calculateLineTotal(cleanQuantity, cleanPrice, cleanDiscount)
                                    val issuedAt = System.currentTimeMillis()
                                    val invoiceNumber = "DS-" + issuedAt + "-" + UUID.randomUUID().toString().take(8)
                                    val result = repository.postInvoice(
                                        invoiceNumber = invoiceNumber,
                                        kind = "SALE",
                                        partyId = selectedPartyId,
                                        currencyCode = product.priceCurrencyCode,
                                        exchangeRateSnapshot = if (product.priceCurrencyCode == "SYP") "1" else exchangeRate,
                                        lines = listOf(
                                            InvoiceLineDraft(
                                                productId = product.id,
                                                quantity = cleanQuantity,
                                                unitPrice = cleanPrice,
                                                discount = cleanDiscount,
                                                unitName = selectedUnit?.unitName ?: product.baseUnit
                                            )
                                        ),
                                        paidAmount = cleanPaid,
                                        issuedAt = issuedAt,
                                        note = note.trim()
                                    )
                                    selectedDay = Calendar.getInstance().apply {
                                        set(Calendar.HOUR_OF_DAY, 0)
                                        set(Calendar.MINUTE, 0)
                                        set(Calendar.SECOND, 0)
                                        set(Calendar.MILLISECOND, 0)
                                    }.timeInMillis
                                    selectedProductId = null
                                    selectedUnitName = null
                                    selectedPartyId = null
                                    quantity = "1"
                                    salePrice = "0"
                                    lineDiscount = "0"
                                    exchangeRate = context.getSharedPreferences("good_girl_settings", android.content.Context.MODE_PRIVATE).getString("usd_syp_rate", "1") ?: "1"
                                    paidAmount = ""
                                    paidAmountTouched = false
                                    note = ""
                                    message = "تم تسجيل المبيعة " + result.invoiceNumber +
                                        " — الإجمالي: " + result.total + " " + product.priceCurrencyCode
                                } catch (error: Exception) {
                                    message = error.message ?: "تعذر تسجيل المبيعة"
                                } finally {
                                    isSaving = false
                                }
                            }
                        },
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isSaving) "جارٍ تسجيل المبيعة..." else "حفظ المبيعة وتحديث المخزون")
                    }
                } else {
                    Text(
                        "اختر مادة لعرض سعرها وكلفتها والربح التقديري قبل الحفظ.",
                        color = Color(0xFF64748B),
                        fontSize = 12.sp
                    )
                }
                message?.let {
                    Text(
                        it,
                        color = if (it.startsWith("تم")) Color(0xFF287A4B) else Color(0xFFB42332),
                        fontSize = 13.sp
                    )
                }
            }
        }

        TextButton(onClick = onOpenFullInvoice) {
            Text("فتح فاتورة بيع متعددة المواد")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SummaryCard(
                "إجمالي المبيعات",
                formatCurrencyTotals(salesTotals),
                "لليوم المحدد",
                BrandBlue,
                Modifier.weight(1f)
            )
            SummaryCard(
                "إجمالي الأرباح",
                formatCurrencyTotals(profitTotals),
                "بعد خصومات الفواتير",
                SoftGreen,
                Modifier.weight(1f)
            )
        }
        Text(
            "تفاصيل المواد المباعة (" + rows.size + ")",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Ink
        )
        if (rows.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Text(
                    "لا توجد مبيعات مرحلة في هذا اليوم.",
                    modifier = Modifier.padding(18.dp),
                    color = Color(0xFF64748B)
                )
            }
        } else {
            rows.forEach { row ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(row.productName, color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            "الكمية: " + row.quantity + " " + row.unitName +
                                "  |  سعر المبيع: " + row.unitPrice + " " + row.currencyCode,
                            color = Color(0xFF64748B),
                            fontSize = 12.sp
                        )
                        Text(
                            "قيمة المبيع: " + row.netSales.stripTrailingZeros().toPlainString() +
                                " " + row.currencyCode,
                            color = Ink,
                            fontSize = 13.sp
                        )
                        Text(
                            "الربح: " + row.profit.stripTrailingZeros().toPlainString() +
                                " " + row.currencyCode,
                            color = if (row.profit >= BigDecimal.ZERO) Color(0xFF287A4B) else Color(0xFFB42332),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            "رقم الفاتورة: " + row.invoiceNumber,
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        )
                        Text(
                            "الملاحظات: " + row.note.ifBlank { "لا توجد ملاحظات" },
                            color = Color(0xFF64748B),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
        Text(
            "كل مبيعة تُحفظ كفاتورة بيع عادية من سطر واحد؛ لذلك تُحدّث المخزون وكشف الحساب والصندوق والتقارير من مصدر واحد دون تكرار.",
            color = Color(0xFF64748B),
            fontSize = 12.sp
        )
    }
}


private fun formatCurrencyTotals(values: Map<String, BigDecimal>): String {
    if (values.isEmpty()) return "0"
    return values.toSortedMap().entries.joinToString("\n") { (currency, amount) ->
        amount.stripTrailingZeros().toPlainString() + " " + currency
    }
}

@Composable
private fun SummaryCard(
    title: String,
    value: String,
    unit: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(title, color = Color(0xFF64748B), fontSize = 12.sp)
            Text(value, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 3)
            Text(unit, color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SectionRow(number: Int, title: String, icon: String? = null, onClick: (() -> Unit)? = null) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (icon != null) {
                Text(icon, fontSize = 22.sp)
            } else {
                Text(number.toString(), color = BrandBlue, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Text(title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (onClick != null) {
                Spacer(Modifier.weight(1f))
                Text("فتح", color = BrandBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
