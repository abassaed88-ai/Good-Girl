package com.goodgirl.accounting

import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Slider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goodgirl.accounting.data.AccountingBackupManager
import com.goodgirl.accounting.data.ExchangeRateHistoryRepository
import com.goodgirl.accounting.data.ExchangeRateCorrectionPreview
import com.goodgirl.accounting.data.previewExchangeRateCorrection
import com.goodgirl.accounting.data.applyExchangeRateCorrection
import com.goodgirl.accounting.data.archiveExchangeRate
import com.goodgirl.accounting.data.CashTransactionRepository
import com.goodgirl.accounting.data.CategoryRepository
import com.goodgirl.accounting.data.InvoiceLineDraft
import com.goodgirl.accounting.data.InvoicePdfExporter
import com.goodgirl.accounting.data.InvoiceRepository
import com.goodgirl.accounting.data.FinancialReportPdfExporter
import com.goodgirl.accounting.data.GeneralLedgerStatementsPdfExporter
import com.goodgirl.accounting.data.PartyRepository
import com.goodgirl.accounting.data.PartyPaymentRepository
import com.goodgirl.accounting.data.PartyStatementPdfExporter
import com.goodgirl.accounting.data.ProductRepository
import com.goodgirl.accounting.data.ProductUnitRepository
import com.goodgirl.accounting.data.StockRepository
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.CategoryEntity
import com.goodgirl.accounting.data.local.PartyEntity
import com.goodgirl.accounting.data.local.ProductEntity
import com.goodgirl.accounting.domain.CashTransactionRules
import com.goodgirl.accounting.domain.FinancialReportCalculator
import com.goodgirl.accounting.domain.TrialBalanceCalculator
import com.goodgirl.accounting.domain.CashFlowStatementCalculator
import com.goodgirl.accounting.domain.GeneralLedgerFinancialStatementsCalculator
import com.goodgirl.accounting.domain.InvoicePostingRules
import com.goodgirl.accounting.domain.InvoiceDiscountAllocator
import com.goodgirl.accounting.domain.PartyBalanceCalculator
import com.goodgirl.accounting.domain.ProductInputRules
import com.goodgirl.accounting.domain.ProductSearchRules
import com.goodgirl.accounting.domain.StockInputRules
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import java.util.UUID

private data class AppPalette(
    val brandBlue: Color,
    val background: Color,
    val cardSurface: Color,
    val softGreen: Color,
    val softRed: Color,
    val softPurple: Color,
    val ink: Color,
    val mutedText: Color,
    val surfaceVariant: Color
)

private val LightPalette = AppPalette(
    brandBlue = Color(0xFF18AFC3),
    background = Color(0xFFEAF6FA),
    cardSurface = Color(0xFFFFFFFF),
    softGreen = Color(0xFF34865A),
    softRed = Color(0xFFC34D5A),
    softPurple = Color(0xFF438DA8),
    ink = Color(0xFF203744),
    mutedText = Color(0xFF607985),
    surfaceVariant = Color(0xFFDCEAF0)
)

private val DarkPalette = AppPalette(
    brandBlue = Color(0xFF36BCE0),
    background = Color(0xFF071F36),
    cardSurface = Color(0xFF0E3554),
    softGreen = Color(0xFF83D3A8),
    softRed = Color(0xFFFF929A),
    softPurple = Color(0xFF72CBE4),
    ink = Color(0xFFEAF7FC),
    mutedText = Color(0xFFB2D6E3),
    surfaceVariant = Color(0xFF164B6C)
)


private val LightMintPalette = AppPalette(
    brandBlue = Color(0xFF398F82),
    background = Color(0xFFEAF5F1),
    cardSurface = Color(0xFFFFFFFF),
    softGreen = Color(0xFF34865A),
    softRed = Color(0xFFC34D5A),
    softPurple = Color(0xFF398F82),
    ink = Color(0xFF203A37),
    mutedText = Color(0xFF607A75),
    surfaceVariant = Color(0xFFDCEDE7)
)

private val DarkMintPalette = AppPalette(
    brandBlue = Color(0xFF85D4C1),
    background = Color(0xFF101F1C),
    cardSurface = Color(0xFF1D302B),
    softGreen = Color(0xFF83D3A8),
    softRed = Color(0xFFFF929A),
    softPurple = Color(0xFF9BDACA),
    ink = Color(0xFFE7F3EF),
    mutedText = Color(0xFFA9C2B9),
    surfaceVariant = Color(0xFF2C443E)
)

private val LightRosePalette = AppPalette(
    brandBlue = Color(0xFFB87596),
    background = Color(0xFFF7EEF3),
    cardSurface = Color(0xFFFFFFFF),
    softGreen = Color(0xFF34865A),
    softRed = Color(0xFFC34D5A),
    softPurple = Color(0xFFB87596),
    ink = Color(0xFF43313C),
    mutedText = Color(0xFF7A6672),
    surfaceVariant = Color(0xFFEEDFE7)
)

private val DarkRosePalette = AppPalette(
    brandBlue = Color(0xFFD9A7BE),
    background = Color(0xFF201821),
    cardSurface = Color(0xFF302431),
    softGreen = Color(0xFF8ACDAA),
    softRed = Color(0xFFFFA0AC),
    softPurple = Color(0xFFE0B4C9),
    ink = Color(0xFFF4EAF0),
    mutedText = Color(0xFFC0AAB7),
    surfaceVariant = Color(0xFF443543)
)

private fun appPaletteFor(isDark: Boolean, paletteId: String): AppPalette =
    if (isDark) DarkPalette else LightPalette


private val homeShortcutOptions = listOf(
    "daily_sales" to "المبيعات اليومية",
    "price_settings" to "تحديد سعر البيع",
    "sales" to "المبيعات والفواتير",
    "purchases" to "المشتريات",
    "products" to "المنتجات والمخزون",
    "parties" to "العملاء والموردون",
    "accounts" to "الحسابات والديون",
    "cash" to "الصندوق والمصروفات",
    "reports" to "التقارير والأرباح",
    "backup" to "النسخ الاحتياطي والاستعادة",
    "general_accounting" to "المحاسبة العامة"
)
private val defaultHomeShortcutIds = homeShortcutOptions.map { it.first }.toSet()

private var activePalette by mutableStateOf(LightPalette)
internal val BrandBlue get() = activePalette.brandBlue
internal val LightGray get() = activePalette.background
internal val CardSurface get() = activePalette.cardSurface
internal val SoftGreen get() = activePalette.softGreen
internal val SoftRed get() = activePalette.softRed
internal val SoftPurple get() = activePalette.softPurple
internal val Ink get() = activePalette.ink
internal val MutedText get() = activePalette.mutedText
private val BrandOnPrimary get() =
    if (activePalette == DarkPalette || activePalette == DarkMintPalette || activePalette == DarkRosePalette) {
        Color(0xFF10232C)
    } else {
        Color.White
    }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(234, 243, 247)
        window.navigationBarColor = android.graphics.Color.rgb(234, 243, 247)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                GoodGirlApp()
            }
        }
    }
}

@Composable
private fun GoodGirlApp() {
    val context = LocalContext.current
    val settingsPreferences = remember(context) {
        context.getSharedPreferences("good_girl_settings", android.content.Context.MODE_PRIVATE)
    }
    var darkThemeEnabled by remember {
        mutableStateOf(settingsPreferences.getBoolean("dark_theme_enabled", false))
    }
    var fontScale by remember {
        mutableStateOf(normalizeAppFontScale(settingsPreferences.getFloat("font_scale", 1.0f)))
    }
    var paletteId by remember {
        mutableStateOf(normalizeAppPaletteId(settingsPreferences.getString("palette_id", "ocean") ?: "ocean"))
    }
    var visibleShortcutIds by remember {
        mutableStateOf(
            (settingsPreferences.getStringSet("home_shortcuts", null)?.toSet() ?: defaultHomeShortcutIds)
                .intersect(defaultHomeShortcutIds)
        )
    }
    var homeShortcutLabels by remember {
        mutableStateOf(
            homeShortcutOptions.associate { (id, defaultLabel) ->
                id to (settingsPreferences.getString("home_shortcut_label_$id", defaultLabel) ?: defaultLabel)
            }
        )
    }
    val selectedPalette = appPaletteFor(darkThemeEnabled, paletteId)
    SideEffect {
        activePalette = selectedPalette
        (context as? MainActivity)?.window?.let { window ->
            val systemBarColor = selectedPalette.background.toArgb()
            window.statusBarColor = systemBarColor
            window.navigationBarColor = systemBarColor
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val lightStatusFlag = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                window.decorView.systemUiVisibility = if (darkThemeEnabled) {
                    window.decorView.systemUiVisibility and lightStatusFlag.inv()
                } else {
                    window.decorView.systemUiVisibility or lightStatusFlag
                }
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val lightNavigationFlag = android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                window.decorView.systemUiVisibility = if (darkThemeEnabled) {
                    window.decorView.systemUiVisibility and lightNavigationFlag.inv()
                } else {
                    window.decorView.systemUiVisibility or lightNavigationFlag
                }
            }
        }
    }
    var activePage by remember { mutableStateOf("dashboard") }
    var statementPartyId by remember { mutableStateOf<String?>(null) }
    var statementReturnPage by remember { mutableStateOf("parties") }
    var journalReturnPage by remember { mutableStateOf("journal_book") }
    var salesReturnPage by remember { mutableStateOf("dashboard") }
    var partiesReturnPage by remember { mutableStateOf("dashboard") }
    var unitsProductId by remember { mutableStateOf<String?>(null) }
    var historyProductId by remember { mutableStateOf<String?>(null) }
    var journalEntryDetailId by remember { mutableStateOf<String?>(null) }
    var accountLedgerId by remember { mutableStateOf<String?>(null) }
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    var showUnsavedChangesDialog by remember { mutableStateOf(false) }
    var pendingNavigationPage by remember { mutableStateOf<String?>(null) }
    val requestNavigation: (String) -> Unit = { destination ->
        if (activePage in setOf(
                "sales", "purchases", "products", "cash", "daily_sales",
                "price_settings", "parties", "categories", "product_units", "settings", "statement", "backup"
            ) && hasUnsavedChanges) {
            pendingNavigationPage = destination
            showUnsavedChangesDialog = true
        } else {
            hasUnsavedChanges = false
            activePage = destination
        }
    }
    val backTargetPage = previousAppPage(
        currentPage = activePage,
        statementReturnPage = statementReturnPage,
        journalReturnPage = journalReturnPage,
        salesReturnPage = salesReturnPage,
        partiesReturnPage = partiesReturnPage
    )
    BackHandler(enabled = activePage != "dashboard") {
        requestNavigation(backTargetPage)
    }
    val baseDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = baseDensity.density,
            fontScale = baseDensity.fontScale * fontScale
        )
    ) {
    MaterialTheme(
        colorScheme = if (darkThemeEnabled) {
            darkColorScheme(
                primary = selectedPalette.brandBlue,
                secondary = selectedPalette.softPurple,
                background = selectedPalette.background,
                surface = selectedPalette.cardSurface,
                onPrimary = Color(0xFF10232C),
                onSurface = selectedPalette.ink,
                onBackground = selectedPalette.ink,
                surfaceVariant = selectedPalette.surfaceVariant,
                onSurfaceVariant = selectedPalette.mutedText
            )
        } else {
            lightColorScheme(
                primary = selectedPalette.brandBlue,
                secondary = selectedPalette.softPurple,
                background = selectedPalette.background,
                surface = selectedPalette.cardSurface,
                onPrimary = BrandOnPrimary,
                onSurface = selectedPalette.ink,
                onBackground = selectedPalette.ink,
                surfaceVariant = selectedPalette.surfaceVariant,
                onSurfaceVariant = selectedPalette.mutedText
            )
        }
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = selectedPalette.background) {
            Box(modifier = Modifier.fillMaxSize()) {
            when (activePage) {
                "cash" -> CashManagementScreen(
                    onBack = { requestNavigation("dashboard") },
                    onUnsavedChangesChanged = { hasUnsavedChanges = it }
                )
                "general_accounting" -> GeneralAccountingHubScreen(
                    onBack = { activePage = "dashboard" },
                    onOpenChart = { activePage = "chart_of_accounts" },
                    onOpenJournal = { activePage = "journal_book" }
                )
                "chart_of_accounts" -> ChartOfAccountsScreen(
                    onBack = { activePage = "general_accounting" },
                    onOpenLedger = { id ->
                        accountLedgerId = id
                        activePage = "account_ledger"
                    }
                )
                "account_ledger" -> AccountLedgerScreen(
                    accountId = accountLedgerId.orEmpty(),
                    onBack = { activePage = "chart_of_accounts" },
                    onOpenEntry = { id ->
                        journalEntryDetailId = id
                        journalReturnPage = "account_ledger"
                        activePage = "journal_detail"
                    }
                )
                "journal_book" -> JournalBookScreen(
                    onBack = { activePage = "general_accounting" },
                    onOpenEntry = { id ->
                        journalEntryDetailId = id
                        journalReturnPage = "journal_book"
                        activePage = "journal_detail"
                    }
                )
                "journal_detail" -> JournalEntryDetailScreen(
                    entryId = journalEntryDetailId.orEmpty(),
                    onBack = { activePage = journalReturnPage }
                )
                "reports" -> FinancialReportsScreen(onBack = { activePage = "dashboard" })
                "backup" -> BackupManagementScreen(
                    onBack = { requestNavigation("dashboard") },
                    onUnsavedChangesChanged = { hasUnsavedChanges = it }
                )
                "settings" -> SettingsScreen(
                    onBack = { requestNavigation("dashboard") },
                    isDarkTheme = darkThemeEnabled,
                    onDarkThemeChanged = { enabled ->
                        settingsPreferences.edit().putBoolean("dark_theme_enabled", enabled).apply()
                        darkThemeEnabled = enabled
                    },
                    fontScale = fontScale,
                    onFontScaleChanged = { scale ->
                        val normalized = normalizeAppFontScale(scale)
                        settingsPreferences.edit().putFloat("font_scale", normalized).apply()
                        fontScale = normalized
                    },
                    paletteId = paletteId,
                    onPaletteChanged = { id ->
                        val normalized = normalizeAppPaletteId(id)
                        settingsPreferences.edit().putString("palette_id", normalized).apply()
                        paletteId = normalized
                    },
                    visibleShortcutIds = visibleShortcutIds,
                    onVisibleShortcutIdsChanged = { ids ->
                        val normalized = ids.intersect(defaultHomeShortcutIds)
                        settingsPreferences.edit().putStringSet("home_shortcuts", normalized.toMutableSet()).apply()
                        visibleShortcutIds = normalized
                    },
                    homeShortcutLabels = homeShortcutLabels,
                    onHomeShortcutLabelChanged = { id, label ->
                        val safeLabel = sanitizeHomeShortcutLabel(label)
                        settingsPreferences.edit().putString("home_shortcut_label_$id", safeLabel).apply()
                        homeShortcutLabels = homeShortcutLabels + (id to safeLabel)
                    },
                    onUnsavedChangesChanged = { hasUnsavedChanges = it }
                )
                "sales" -> InvoiceManagementScreen(
                    kind = "SALE",
                    onBack = { requestNavigation(salesReturnPage) },
                    onUnsavedChangesChanged = { hasUnsavedChanges = it }
                )
                "daily_sales" -> DailySalesScreen(
                    onBack = { requestNavigation("dashboard") },
                    onOpenFullInvoice = {
                        salesReturnPage = "daily_sales"
                        requestNavigation("sales")
                    },
                    onUnsavedChangesChanged = { hasUnsavedChanges = it }
                )
                "price_settings" -> ProductPriceManagementScreen(
                    onBack = { requestNavigation("dashboard") },
                    onUnsavedChangesChanged = { hasUnsavedChanges = it }
                )
                "purchases" -> InvoiceManagementScreen(
                    kind = "PURCHASE",
                    onBack = { requestNavigation("dashboard") },
                    onUnsavedChangesChanged = { hasUnsavedChanges = it }
                )
                "products" -> ProductManagementScreen(
                    onBack = { requestNavigation("dashboard") },
                    onManageUnits = { productId ->
                        unitsProductId = productId
                        requestNavigation("product_units")
                    },
                    onManageCategories = { requestNavigation("categories") },
                    onViewHistory = { productId ->
                        historyProductId = productId
                        requestNavigation("stock_history")
                    },
                    onUnsavedChangesChanged = { hasUnsavedChanges = it }
                )
                "categories" -> ProductCategoriesScreen(
                    onBack = { requestNavigation("products") },
                    onUnsavedChangesChanged = { hasUnsavedChanges = it }
                )
                "product_units" -> ProductUnitsScreen(
                    productId = unitsProductId.orEmpty(),
                    onBack = { requestNavigation("products") },
                    onUnsavedChangesChanged = { hasUnsavedChanges = it }
                )
                "stock_history" -> StockHistoryScreen(
                    productId = historyProductId.orEmpty(),
                    onBack = { activePage = "products" }
                )
                "accounts" -> AccountsAndDebtsScreen(
                    onBack = { activePage = "dashboard" },
                    onManageParties = {
                        partiesReturnPage = "accounts"
                        activePage = "parties"
                    },
                    onOpenStatement = { partyId ->
                        statementPartyId = partyId
                        statementReturnPage = "accounts"
                        activePage = "statement"
                    }
                )
                "parties" -> PartyManagementScreen(
                    onBack = { requestNavigation(partiesReturnPage) },
                    onOpenStatement = { partyId ->
                        statementPartyId = partyId
                        statementReturnPage = "parties"
                        requestNavigation("statement")
                    },
                    onUnsavedChangesChanged = { hasUnsavedChanges = it }
                )
                "statement" -> PartyStatementScreen(
                    partyId = statementPartyId.orEmpty(),
                    onBack = { requestNavigation(statementReturnPage) },
                    onUnsavedChangesChanged = { hasUnsavedChanges = it }
                )
                else -> DashboardScreen(
                    visibleShortcutIds = visibleShortcutIds,
                    homeShortcutLabels = homeShortcutLabels,
                    onOpenDailySales = { activePage = "daily_sales" },
                    onOpenPriceSettings = { activePage = "price_settings" },
                    onOpenSales = {
                        salesReturnPage = "dashboard"
                        activePage = "sales"
                    },
                    onOpenPurchases = { activePage = "purchases" },
                    onOpenProducts = { activePage = "products" },
                    onOpenParties = {
                        partiesReturnPage = "dashboard"
                        activePage = "parties"
                    },
                    onOpenAccounts = { activePage = "accounts" },
                    onOpenCash = { activePage = "cash" },
                    onOpenGeneralAccounting = { activePage = "general_accounting" },
                    onOpenReports = { activePage = "reports" },
                    onOpenBackup = { activePage = "backup" },
                    onOpenSettings = { activePage = "settings" }
                )
            }
            if (activePage != "dashboard") {
                TextButton(
                    onClick = { requestNavigation("dashboard") },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                        .background(BrandBlue, RoundedCornerShape(24.dp))
                ) {
                    Text("⌂ الرئيسية", color = BrandOnPrimary, fontWeight = FontWeight.SemiBold)
                }
                TextButton(
                    onClick = { requestNavigation(backTargetPage) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .background(BrandBlue, RoundedCornerShape(24.dp))
                ) {
                    Text("← رجوع", color = BrandOnPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
            if (showUnsavedChangesDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showUnsavedChangesDialog = false
                        pendingNavigationPage = null
                    },
                    title = { Text("لديك بيانات غير محفوظة") },
                    text = {
                        Text("أدخلت بيانات لم يتم حفظها بعد. هل تريد مغادرة الشاشة دون حفظها؟")
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val destination = pendingNavigationPage
                                showUnsavedChangesDialog = false
                                pendingNavigationPage = null
                                hasUnsavedChanges = false
                                if (destination != null) activePage = destination
                            }
                        ) {
                            Text("مغادرة دون حفظ")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showUnsavedChangesDialog = false
                                pendingNavigationPage = null
                            }
                        ) {
                            Text("متابعة التعديل")
                        }
                    }
                )
            }
            }
        }
    }
    }
}

/**
 * Resolves a predictable parent destination for the global back control and
 * Android system back. Return destinations are retained for detail flows.
 */
internal fun previousAppPage(
    currentPage: String,
    statementReturnPage: String = "parties",
    journalReturnPage: String = "journal_book",
    salesReturnPage: String = "dashboard",
    partiesReturnPage: String = "dashboard"
): String = when (currentPage) {
    "chart_of_accounts", "journal_book" -> "general_accounting"
    "account_ledger" -> "chart_of_accounts"
    "journal_detail" -> journalReturnPage
    "categories", "product_units", "stock_history" -> "products"
    "statement" -> statementReturnPage
    "sales" -> salesReturnPage
    "parties" -> partiesReturnPage
    "dashboard" -> "dashboard"
    else -> "dashboard"
}

@Composable
private fun DashboardScreen(
    onOpenDailySales: () -> Unit,
    onOpenPriceSettings: () -> Unit,
    onOpenSales: () -> Unit,
    onOpenPurchases: () -> Unit,
    onOpenProducts: () -> Unit,
    onOpenParties: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenCash: () -> Unit,
    onOpenGeneralAccounting: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenSettings: () -> Unit,
    visibleShortcutIds: Set<String>,
    homeShortcutLabels: Map<String, String>
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

    val dashboardTiles = listOf(
        Triple("daily_sales", homeShortcutLabels["daily_sales"].orEmpty().ifBlank { "المبيعات اليومية" }, "📊") to onOpenDailySales,
        Triple("sales", homeShortcutLabels["sales"].orEmpty().ifBlank { "إضافة مبيع" }, "🛒") to onOpenSales,
        Triple("products", homeShortcutLabels["products"].orEmpty().ifBlank { "إدارة المخزون" }, "📦") to onOpenProducts,
        Triple("accounts", homeShortcutLabels["accounts"].orEmpty().ifBlank { "قائمة الحسابات" }, "📒") to onOpenAccounts,
        Triple("reports", homeShortcutLabels["reports"].orEmpty().ifBlank { "التقارير" }, "📈") to onOpenReports,
        Triple("settings", "الإعدادات", "⚙️") to onOpenSettings,
        Triple("purchases", homeShortcutLabels["purchases"].orEmpty().ifBlank { "المشتريات" }, "🧾") to onOpenPurchases,
        Triple("parties", homeShortcutLabels["parties"].orEmpty().ifBlank { "العملاء والموردون" }, "👥") to onOpenParties,
        Triple("cash", homeShortcutLabels["cash"].orEmpty().ifBlank { "الصندوق والمصروفات" }, "💼") to onOpenCash,
        Triple("general_accounting", homeShortcutLabels["general_accounting"].orEmpty().ifBlank { "المحاسبة العامة" }, "🧮") to onOpenGeneralAccounting,
        Triple("backup", homeShortcutLabels["backup"].orEmpty().ifBlank { "النسخ الاحتياطي" }, "💾") to onOpenBackup,
        Triple("price_settings", homeShortcutLabels["price_settings"].orEmpty().ifBlank { "تحديد سعر البيع" }, "🏷️") to onOpenPriceSettings
    ).filter { (tile, _) -> tile.first == "settings" || tile.first in visibleShortcutIds }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = BrandBlue),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Good Girl", color = BrandOnPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("نظام إدارة ومحاسبة متكامل", color = BrandOnPrimary, fontSize = 14.sp)
                }
                Text("☰", color = BrandOnPrimary, fontSize = 27.sp)
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("▤", color = BrandBlue, fontSize = 35.sp)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("قاعدة البيانات الأساسية", color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        "تُحفظ بياناتك على هذا الجهاز، ويمكنك إدارة النسخ الاحتياطية من هنا",
                        color = MutedText,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onOpenBackup,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SoftGreen)
            ) {
                Text("إجراء نسخة احتياطية", maxLines = 1, fontSize = 12.sp)
            }
            Button(
                onClick = onOpenSettings,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("الإعدادات", maxLines = 1)
            }
        }

        Text("ملخص اليوم", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
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
        SummaryCard("الفواتير المرحلة", postedInvoiceCount.toString(), "فاتورة", BrandBlue, Modifier.fillMaxWidth())

        Text("الأقسام الرئيسية", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
        dashboardTiles.chunked(2).forEach { tileRow ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                tileRow.forEach { (tile, action) ->
                    DashboardTile(
                        title = tile.second,
                        icon = tile.third,
                        onClick = action,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (tileRow.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        if (dashboardTiles.isEmpty()) {
            Text("لا توجد اختصارات ظاهرة. يمكنك اختيار الاختصارات من الإعدادات.", color = MutedText, fontSize = 12.sp)
        }

        Text(
            "تعتمد المؤشرات على الفواتير والحركات المسجلة؛ تبقى أرصدة الليرة والدولار منفصلة.",
            color = MutedText,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )
    }
}

@Composable
private fun DashboardTile(
    title: String,
    icon: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(112.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(icon, fontSize = 27.sp)
            Spacer(Modifier.height(5.dp))
            Text(
                title,
                color = Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun ProductPriceManagementScreen(
    onBack: () -> Unit,
    onUnsavedChangesChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val database = remember(context) { AccountingDatabase.getInstance(context) }
    val repository = remember(database) { ProductRepository(database) }
    val products by repository.observeProducts().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var priceInputs by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var messages by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var savingIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var savedPriceBaselines by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var dirtyPriceIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(dirtyPriceIds) {
        onUnsavedChangesChanged(dirtyPriceIds.isNotEmpty())
    }

    val filteredProducts = products.filter { product ->
        searchQuery.isBlank() ||
            product.name.contains(searchQuery.trim(), ignoreCase = true) ||
            product.sku.contains(searchQuery.trim(), ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) {
            Text("العودة إلى لوحة التحكم")
        }
        Text(
            "تحديد سعر البيع",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Ink
        )
        Text(
            "عدّل سعر البيع الافتراضي للمادة. يُطبّق السعر الجديد على المبيعات القادمة فقط، ولا يغيّر أسعار الفواتير السابقة أو كلفة الشراء أو رصيد المخزون.",
            color = MutedText,
            fontSize = 13.sp
        )
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("البحث باسم المادة أو رمزها") },
            singleLine = true
        )

        if (filteredProducts.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardSurface)
            ) {
                Text(
                    if (products.isEmpty()) "لا توجد مواد مسجلة بعد." else "لا توجد مواد مطابقة للبحث.",
                    modifier = Modifier.padding(16.dp),
                    color = MutedText
                )
            }
        } else {
            filteredProducts.forEach { product ->
                val enteredPrice = priceInputs[product.id] ?: product.salePrice
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(product.name, color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            "السعر الحالي: " + product.salePrice + " " + product.priceCurrencyCode +
                                "  •  الوحدة: " + product.baseUnit,
                            color = MutedText,
                            fontSize = 12.sp
                        )
                        OutlinedTextField(
                            value = enteredPrice,
                            onValueChange = { candidate ->
                                if (
                                    candidate.all { it in '0'..'9' || it == '.' } &&
                                    candidate.count { it == '.' } <= 1
                                ) {
                                    priceInputs = priceInputs + (product.id to candidate)
                                    val savedBaseline = savedPriceBaselines[product.id] ?: product.salePrice
                                    dirtyPriceIds = if (candidate == savedBaseline) {
                                        dirtyPriceIds - product.id
                                    } else {
                                        dirtyPriceIds + product.id
                                    }
                                    messages = messages - product.id
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("سعر البيع الجديد للوحدة") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            enabled = product.id !in savingIds
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    savingIds = savingIds + product.id
                                    messages = messages - product.id
                                    try {
                                        val cleanPrice = ProductInputRules.normalizeNonNegativeDecimal(
                                            enteredPrice,
                                            "سعر البيع"
                                        )
                                        val updated = repository.updateSalePrice(product.id, cleanPrice)
                                        check(updated) { "تعذر العثور على المادة لتحديث سعرها" }
                                        priceInputs = priceInputs + (product.id to cleanPrice)
                                        savedPriceBaselines = savedPriceBaselines + (product.id to cleanPrice)
                                        dirtyPriceIds = dirtyPriceIds - product.id
                                        messages = messages + (product.id to "تم تحديث سعر البيع للمبيعات الجديدة.")
                                    } catch (error: Exception) {
                                        messages = messages + (
                                            product.id to (error.message ?: "تعذر تحديث سعر البيع")
                                        )
                                    } finally {
                                        savingIds = savingIds - product.id
                                    }
                                }
                            },
                            enabled = product.id !in savingIds,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (product.id in savingIds) "جارٍ حفظ السعر..." else "حفظ سعر البيع")
                        }
                        messages[product.id]?.let { message ->
                            Text(
                                message,
                                color = if (message.startsWith("تم")) SoftGreen else SoftRed,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    onBack: () -> Unit,
    isDarkTheme: Boolean,
    onDarkThemeChanged: (Boolean) -> Unit,
    fontScale: Float,
    onFontScaleChanged: (Float) -> Unit,
    paletteId: String,
    onPaletteChanged: (String) -> Unit,
    visibleShortcutIds: Set<String>,
    onVisibleShortcutIdsChanged: (Set<String>) -> Unit,
    homeShortcutLabels: Map<String, String>,
    onHomeShortcutLabelChanged: (String, String) -> Unit,
    onUnsavedChangesChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val database = remember(context) { AccountingDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    val preferences = remember(context) {
        context.getSharedPreferences("good_girl_settings", android.content.Context.MODE_PRIVATE)
    }
    var exchangeRate by remember {
        mutableStateOf(preferences.getString("usd_syp_rate", "1") ?: "1")
    }
    var savedExchangeRate by remember {
        mutableStateOf(preferences.getString("usd_syp_rate", "1") ?: "1")
    }
    var lastUpdated by remember {
        mutableStateOf(preferences.getLong("usd_syp_rate_updated_at", 0L))
    }
    val rateHistory by database.exchangeRateHistoryDao().observeAll().collectAsState(initial = emptyList())
    val rateAudits by database.exchangeRateAuditDao().observeAll().collectAsState(initial = emptyList())
    var message by remember { mutableStateOf<String?>(null) }
    var selectedCorrectionRateId by remember { mutableStateOf<String?>(null) }
    var correctionRate by remember { mutableStateOf("") }
    var correctionReason by remember { mutableStateOf("") }
    var correctionPreview by remember { mutableStateOf<ExchangeRateCorrectionPreview?>(null) }
    var correctionMessage by remember { mutableStateOf<String?>(null) }
    var isPreviewingCorrection by remember { mutableStateOf(false) }
    var isApplyingCorrection by remember { mutableStateOf(false) }
    var confirmCorrection by remember { mutableStateOf(false) }
    var pendingArchiveRateId by remember { mutableStateOf<String?>(null) }
    var archiveMessage by remember { mutableStateOf<String?>(null) }
    val latestRateId = rateHistory.maxWithOrNull(
        compareBy<com.goodgirl.accounting.data.local.ExchangeRateHistoryEntity> { it.effectiveAt }
            .thenBy { it.recordedAt }
            .thenBy { it.id }
    )?.id
    val selectedCorrectionRate = rateHistory.firstOrNull { it.id == selectedCorrectionRateId }
    val hasUnsavedSettingsChanges =
        exchangeRate != savedExchangeRate ||
            (selectedCorrectionRate != null &&
                (correctionRate != selectedCorrectionRate.rate || correctionReason.isNotBlank()))
    LaunchedEffect(hasUnsavedSettingsChanges) {
        onUnsavedChangesChanged(hasUnsavedSettingsChanges)
    }

    LaunchedEffect(database) {
        runCatching {
            ExchangeRateHistoryRepository.importLegacyPreferences(context, database)
        }.onFailure {
            message = it.message ?: "تعذر ترحيل سجل أسعار الصرف القديم"
        }
    }

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
            colors = CardDefaults.cardColors(containerColor = CardSurface)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    Text("المظهر", color = Ink, fontWeight = FontWeight.Bold)
                    Text(
                        if (isDarkTheme) "الوضع الداكن مفعّل" else "الوضع الفاتح مفعّل",
                        color = MutedText,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = isDarkTheme,
                    onCheckedChange = onDarkThemeChanged
                )
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("ألوان التطبيق", color = Ink, fontWeight = FontWeight.Bold)
                Text(
                    "تم اعتماد التصميم الأزرق الجليدي الفاتح مع البطاقات البيضاء واللمسات التركوازية، والوضع الداكن الكحلي. سيُطبّق النمط نفسه على جميع الشاشات.",
                    color = MutedText,
                    fontSize = 12.sp
                )
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("حجم الخط", color = Ink, fontWeight = FontWeight.Bold)
                Text(
                    "${(fontScale * 100).toInt()}% — يتغير حجم النص في جميع شاشات التطبيق",
                    color = MutedText,
                    fontSize = 12.sp
                )
                Slider(
                    value = fontScale,
                    onValueChange = onFontScaleChanged,
                    valueRange = 0.9f..1.2f,
                    steps = 5
                )
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("90%", color = MutedText, fontSize = 11.sp)
                    Text("120%", color = MutedText, fontSize = 11.sp)
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("اختصارات الصفحة الرئيسية", color = Ink, fontWeight = FontWeight.Bold)
                Text(
                    "يمكنك إظهار أو إخفاء كل اختصار وتعديل اسمه الظاهر في لوحة التحكم. لا يؤدي ذلك إلى حذف بيانات القسم.",
                    color = MutedText,
                    fontSize = 12.sp
                )
                homeShortcutOptions.forEach { (id, title) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = homeShortcutLabels[id] ?: title,
                            onValueChange = { onHomeShortcutLabelChanged(id, it) },
                            modifier = Modifier.weight(1f),
                            label = { Text(title) },
                            singleLine = true
                        )
                        Checkbox(
                            checked = id in visibleShortcutIds,
                            onCheckedChange = { checked ->
                                val updated = if (checked) visibleShortcutIds + id else visibleShortcutIds - id
                                onVisibleShortcutIdsChanged(updated)
                            }
                        )
                    }
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("العملات وأسعار الصرف", color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    "حدد قيمة الدولار الواحد بالليرة السورية. يُستخدم السعر المحفوظ كقيمة افتراضية للفواتير الجديدة بالدولار، ويمكن تعديل السعر داخل الفاتورة عند الحاجة.",
                    color = MutedText,
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
                        color = MutedText,
                        fontSize = 12.sp
                    )
                } else {
                    Text("لم يتم حفظ سعر صرف بعد.", color = MutedText, fontSize = 12.sp)
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
                            scope.launch {
                                try {
                                    val savedAt = ExchangeRateHistoryRepository.saveCurrentRate(
                                        context = context,
                                        database = database,
                                        enteredRate = cleanRate
                                    )
                                    exchangeRate = cleanRate
                                    savedExchangeRate = cleanRate
                                    lastUpdated = savedAt
                                    message = "تم حفظ السعر في السجل الدائم. سيُستخدم للعمليات الجديدة فقط، دون تعديل العمليات السابقة."
                                } catch (error: Exception) {
                                    message = error.message ?: "تعذر حفظ سعر الصرف"
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("حفظ سعر الصرف")
                }
                message?.let {
                    Text(
                        it,
                        color = if (it.startsWith("تم")) SoftGreen else SoftRed,
                        fontSize = 13.sp
                    )
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("سجل تعديلات سعر الصرف", color = Ink, fontWeight = FontWeight.Bold)
                if (rateHistory.isEmpty()) {
                    Text("لا يوجد سجل تعديلات حتى الآن.", color = MutedText, fontSize = 12.sp)
                } else {
                    rateHistory.forEach { item ->
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    item.rate + " ل.س / USD" + if (item.id == latestRateId) " — الحالي" else "",
                                    color = BrandBlue,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    if (item.effectiveAt > 0L) SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(item.effectiveAt)) else "",
                                    color = MutedText,
                                    fontSize = 11.sp
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                TextButton(onClick = {
                                    selectedCorrectionRateId = item.id
                                    correctionRate = item.rate
                                    correctionReason = ""
                                    correctionPreview = null
                                    correctionMessage = null
                                    confirmCorrection = false
                                }) {
                                    Text("تصحيح هذا السعر")
                                }
                                if (item.id != latestRateId) {
                                    TextButton(onClick = {
                                        pendingArchiveRateId = item.id
                                        archiveMessage = null
                                    }) {
                                        Text("أرشفة")
                                    }
                                }
                            }
                        }
                        androidx.compose.material3.Divider(color = LightGray.copy(alpha = 0.8f))
                    }
                }
                archiveMessage?.let {
                    Text(it, color = if (it.startsWith("تم")) SoftGreen else SoftRed, fontSize = 12.sp)
                }
            }
        }
        selectedCorrectionRateId?.let { selectedId ->
            val selectedRate = rateHistory.firstOrNull { it.id == selectedId }
            if (selectedRate != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("تصحيح سعر تاريخي", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("السعر المسجل: " + selectedRate.rate + " ل.س / USD", color = MutedText, fontSize = 12.sp)
                        OutlinedTextField(
                            value = correctionRate,
                            onValueChange = {
                                correctionRate = it
                                correctionPreview = null
                                confirmCorrection = false
                                correctionMessage = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("السعر الصحيح الجديد") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = correctionReason,
                            onValueChange = {
                                correctionReason = it
                                correctionMessage = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("سبب التصحيح (مطلوب للتدقيق)") },
                            minLines = 2
                        )
                        Button(
                            enabled = !isPreviewingCorrection && !isApplyingCorrection,
                            onClick = {
                                scope.launch {
                                    isPreviewingCorrection = true
                                    correctionPreview = null
                                    confirmCorrection = false
                                    correctionMessage = null
                                    try {
                                        correctionPreview = previewExchangeRateCorrection(
                                            database = database,
                                            historyId = selectedId,
                                            proposedRate = correctionRate
                                        )
                                    } catch (error: Exception) {
                                        correctionMessage = error.message ?: "تعذرت معاينة العمليات"
                                    } finally {
                                        isPreviewingCorrection = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isPreviewingCorrection) "جارٍ إعداد المعاينة..." else "معاينة العمليات المتأثرة")
                        }
                        correctionPreview?.let { preview ->
                            androidx.compose.material3.Divider(color = LightGray)
                            Text(
                                "الفترة: " + SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(preview.effectiveFrom)) +
                                    (preview.effectiveUntilExclusive?.let {
                                        " حتى " + SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(it))
                                    } ?: " — حتى آخر سجل"),
                                color = MutedText,
                                fontSize = 12.sp
                            )
                            Text(
                                "الفواتير: " + preview.invoiceCount +
                                    " | حركات الحساب: " + preview.ledgerCount +
                                    " | قيود اليومية: " + preview.journalCount,
                                color = BrandBlue,
                                fontWeight = FontWeight.Bold
                            )
                            if (preview.records.isEmpty()) {
                                Text("لا توجد عمليات تحمل السعر القديم ضمن هذه الفترة.", color = MutedText, fontSize = 12.sp)
                            } else {
                                preview.records.take(25).forEach { target ->
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(target.displayName + " — " + target.amountSummary, color = Ink, fontSize = 12.sp)
                                        Text(
                                            target.recordType + " | " + target.sourceType + " / " + target.sourceId,
                                            color = MutedText,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                                if (preview.records.size > 25) {
                                    Text("تم إظهار أول 25 عملية من أصل " + preview.records.size, color = MutedText, fontSize = 11.sp)
                                }
                            }
                            Text(
                                "التصحيح يغيّر لقطة سعر الصرف المرجعية فقط. لن تتغير المبالغ أو إجماليات الفواتير أو أرصدة الحسابات أو أرباح المبيعات.",
                                color = MutedText,
                                fontSize = 12.sp
                            )
                            Button(
                                enabled = !isApplyingCorrection && correctionReason.trim().isNotEmpty(),
                                onClick = { confirmCorrection = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("متابعة لتأكيد التصحيح")
                            }
                        }
                        if (confirmCorrection && correctionPreview != null) {
                            Text(
                                "تأكيد نهائي: سيتم تحديث " + correctionPreview!!.records.size +
                                    " لقطة سعر مرتبطة بالسعر القديم، وتسجيل العملية في سجل التدقيق. لن تتغير أي مبالغ مالية.",
                                color = Ink,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                            Button(
                                enabled = !isApplyingCorrection,
                                onClick = {
                                    scope.launch {
                                        isApplyingCorrection = true
                                        correctionMessage = null
                                        try {
                                            val result = applyExchangeRateCorrection(
                                                context = context,
                                                database = database,
                                                historyId = selectedId,
                                                proposedRate = correctionRate,
                                                reason = correctionReason
                                            )
                                            if (result.currentRateUpdated) {
                                                exchangeRate = preferences.getString("usd_syp_rate", correctionRate) ?: correctionRate
                                                lastUpdated = preferences.getLong("usd_syp_rate_updated_at", System.currentTimeMillis())
                                            }
                                            correctionMessage =
                                                "تم التصحيح وتسجيله. الفواتير: " + result.updatedInvoiceCount +
                                                "، حركات الحساب: " + result.updatedLedgerCount +
                                                "، قيود اليومية: " + result.updatedJournalCount +
                                                ". لم تتغير المبالغ المالية."
                                            message = correctionMessage
                                            selectedCorrectionRateId = null
                                            correctionPreview = null
                                            confirmCorrection = false
                                        } catch (error: Exception) {
                                            correctionMessage = error.message ?: "تعذر تطبيق التصحيح"
                                        } finally {
                                            isApplyingCorrection = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (isApplyingCorrection) "جارٍ تنفيذ التصحيح..." else "تأكيد التصحيح وتسجيل التدقيق")
                            }
                            TextButton(onClick = { confirmCorrection = false }) { Text("إلغاء التأكيد") }
                        }
                        correctionMessage?.let {
                            Text(it, color = if (it.startsWith("تم")) SoftGreen else SoftRed, fontSize = 12.sp)
                        }
                        TextButton(onClick = {
                            selectedCorrectionRateId = null
                            correctionPreview = null
                            confirmCorrection = false
                        }) { Text("إغلاق") }
                    }
                }
            }
        }
        pendingArchiveRateId?.let { archiveId ->
            val archiveRate = rateHistory.firstOrNull { it.id == archiveId }
            if (archiveRate != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("تأكيد أرشفة السعر " + archiveRate.rate, color = Ink, fontWeight = FontWeight.Bold)
                        Text("سيبقى السجل محفوظاً في قاعدة البيانات والنسخ الاحتياطية، ولن تُحذف الحركات السابقة.", color = MutedText, fontSize = 12.sp)
                        Button(
                            onClick = {
                                scope.launch {
                                    try {
                                        archiveExchangeRate(database, archiveId, "أرشفة يدوية من إعدادات أسعار الصرف")
                                        archiveMessage = "تمت أرشفة سجل السعر دون حذف أثره المحاسبي."
                                        pendingArchiveRateId = null
                                    } catch (error: Exception) {
                                        archiveMessage = error.message ?: "تعذرت أرشفة السعر"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("تأكيد الأرشفة") }
                        TextButton(onClick = { pendingArchiveRateId = null }) { Text("إلغاء") }
                    }
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("سجل تدقيق أسعار الصرف", color = Ink, fontWeight = FontWeight.Bold)
                if (rateAudits.isEmpty()) {
                    Text("لا توجد أحداث تدقيق مسجلة حتى الآن.", color = MutedText, fontSize = 12.sp)
                } else {
                    rateAudits.take(20).forEach { audit ->
                        Text(
                            when (audit.eventType) {
                                "RATE_IMPORTED" -> "استيراد سجل قديم"
                                "RATE_SET" -> "اعتماد سعر جديد"
                                "RATE_CORRECTION" -> "تصحيح سعر تاريخي"
                                "RATE_ARCHIVED" -> "أرشفة سعر تاريخي"
                                else -> audit.eventType
                            },
                            color = BrandBlue,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            "السعر السابق: " + audit.oldRate.ifBlank { "—" } +
                                " | السعر الجديد: " + audit.newRate,
                            color = Ink,
                            fontSize = 12.sp
                        )
                        Text(
                            SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(audit.correctedAt)),
                            color = MutedText,
                            fontSize = 11.sp
                        )
                        if (audit.affectedInvoiceCount + audit.affectedLedgerCount + audit.affectedJournalCount > 0) {
                            Text(
                                "عمليات مرتبطة — فواتير: " + audit.affectedInvoiceCount +
                                    " | حركات: " + audit.affectedLedgerCount +
                                    " | قيود: " + audit.affectedJournalCount,
                                color = MutedText,
                                fontSize = 11.sp
                            )
                        }
                        Text(audit.reason, color = MutedText, fontSize = 12.sp)
                        Text(
                            if (audit.financialAmountsChanged) "تغيّرت القيم المالية ضمن عملية منفصلة موثقة."
                            else "لم تتغير المبالغ أو الأرصدة المالية.",
                            color = MutedText,
                            fontSize = 11.sp
                        )
                        androidx.compose.material3.Divider(color = LightGray)
                    }
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("ملاحظات", color = Ink, fontWeight = FontWeight.Bold)
                Text("• سعر الصرف محفوظ محلياً على هذا الجهاز.", color = MutedText, fontSize = 12.sp)
                Text("• الفواتير السابقة تحتفظ بسعر الصرف المسجل وقت إنشائها.", color = MutedText, fontSize = 12.sp)
                Text("• سجل أسعار الصرف محفوظ ضمن بيانات التطبيق والنسخ الاحتياطية، وتغيير السعر الحالي لا يعدّل الحركات السابقة.", color = MutedText, fontSize = 12.sp)
                Text("• العملة السورية تعتمد سعر صرف 1 تلقائياً.", color = MutedText, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun BackupManagementScreen(
    onBack: () -> Unit,
    onUnsavedChangesChanged: (Boolean) -> Unit
) {
    EncryptedBackupManagementScreen(onBack, onUnsavedChangesChanged)
}
@Composable
private fun InvoiceManagementScreen(
    kind: String,
    onBack: () -> Unit,
    onUnsavedChangesChanged: (Boolean) -> Unit
) {
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
    val defaultExchangeRate = remember(context) {
        context.getSharedPreferences("good_girl_settings", android.content.Context.MODE_PRIVATE)
            .getString("usd_syp_rate", "1") ?: "1"
    }
    var exchangeRate by remember { mutableStateOf(defaultExchangeRate) }
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
    val hasUnsavedInvoiceChanges = hasUnsavedInvoiceDraft(
        InvoiceDraftState(
            draftLineCount = draftLines.size,
            hasSelectedProduct = selectedProductId != null,
            hasSelectedParty = selectedPartyId != null,
            quantity = quantity,
            unitPrice = unitPrice,
            lineDiscount = lineDiscount,
            invoiceDiscount = invoiceDiscount,
            paidAmount = paidAmount,
            currencyCode = currencyCode,
            exchangeRate = exchangeRate,
            defaultExchangeRate = defaultExchangeRate,
            note = note,
            paymentPanelOpen = paymentInvoiceId != null,
            paymentAmount = paymentAmount,
            paymentNote = paymentNote
        )
    )
    LaunchedEffect(hasUnsavedInvoiceChanges) {
        onUnsavedChangesChanged(hasUnsavedInvoiceChanges)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("العودة إلى لوحة التحكم") }
        Text(screenTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Ink)
        Text(
            if (kind == "SALE") "اختيار المواد والكميات وتسجيل المقبوض والمتبقي" else "تسجيل المواد الواردة وتحديث كلفة المخزون",
            color = MutedText,
            fontSize = 13.sp
        )

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = CardSurface)) {
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
                    Text("لا يمكن تبديل عملة الفاتورة بعد إضافة المواد. احذف المواد أولاً إذا أردت تغيير العملة.", color = MutedText, fontSize = 12.sp)
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

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = CardSurface)) {
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
                    Text("لا توجد مواد معرفة بهذه العملة. أضف مادة أو عدّل عملتها من شاشة المنتجات.", color = MutedText, fontSize = 12.sp)
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
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = CardSurface)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(productName, color = Ink, fontWeight = FontWeight.Bold)
                            Text(line.quantity + " " + (line.unitName ?: "") + " × " + line.unitPrice + " = " + net + " " + currencyCode, color = MutedText, fontSize = 12.sp)
                        }
                        TextButton(onClick = { draftLines.removeAt(index) }) { Text("حذف") }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = CardSurface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("ملخص الفاتورة", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink)
                Text("المجموع قبل الخصومات: " + subtotal.stripTrailingZeros().toPlainString() + " " + currencyCode, color = Ink)
                Text("خصومات المواد: " + lineDiscountTotal.stripTrailingZeros().toPlainString(), color = MutedText, fontSize = 13.sp)
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
                                quantity = "1"
                                unitPrice = "0"
                                lineDiscount = "0"
                                currencyCode = "SYP"
                                exchangeRate = "1"
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
                    Text(it, color = if (it.startsWith("تم")) SoftGreen else SoftRed, fontSize = 13.sp)
                }
            }
        }

        val recentInvoices = invoices.filter { it.kind == kind }.take(10)
        Text("آخر الفواتير (" + recentInvoices.size + ")", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
        recentInvoices.forEach { invoice ->
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = CardSurface)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(invoice.invoiceNumber, color = Ink, fontWeight = FontWeight.Bold)
                    Text("الإجمالي: " + invoice.total + " " + invoice.currencyCode, color = BrandBlue, fontSize = 13.sp)
                    Text("المدفوع: " + invoice.paid + " — المتبقي: " + invoice.remaining + " " + invoice.currencyCode, color = MutedText, fontSize = 12.sp)
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
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = CardSurface)) {
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
            Text(it, color = if (it.startsWith("تم")) SoftGreen else SoftRed, fontSize = 13.sp)
        }
        paymentMessage?.let {
            Text(it, color = if (it.startsWith("تم")) SoftGreen else SoftRed, fontSize = 13.sp)
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
    val accounts by database.accountDao().observeAll().collectAsState(initial = emptyList())
    val journalEntries by database.journalEntryDao().observeAll().collectAsState(initial = emptyList())
    val journalLines by database.journalLineDao().observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var selectedPeriod by remember { mutableStateOf("TODAY") }
    var showTrialBalance by remember { mutableStateOf(false) }
    var showLedgerStatements by remember { mutableStateOf(true) }
    var showCashFlow by remember { mutableStateOf(true) }
    var isExportingReport by remember { mutableStateOf(false) }
    var reportExportMessage by remember { mutableStateOf<String?>(null) }
    var isExportingStatementsPdf by remember { mutableStateOf(false) }
    var statementsPdfMessage by remember { mutableStateOf<String?>(null) }
    val now = System.currentTimeMillis()
    val trialBalanceResult = remember(accounts, journalEntries, journalLines, now) {
        runCatching {
            TrialBalanceCalculator.calculate(
                asOfInclusive = now,
                accounts = accounts,
                entries = journalEntries,
                lines = journalLines
            )
        }
    }
    val trialBalance = trialBalanceResult.getOrNull()
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
            else -> 0L
        }
    }
    val generalLedgerStatementsResult = remember(
        accounts, journalEntries, journalLines, fromTime, now
    ) {
        runCatching {
            GeneralLedgerFinancialStatementsCalculator.calculate(
                periodStartInclusive = fromTime,
                periodEndInclusive = now,
                asOfInclusive = now,
                accounts = accounts,
                entries = journalEntries,
                lines = journalLines
            )
        }
    }
    val generalLedgerStatements = generalLedgerStatementsResult.getOrNull()
    val cashFlowStatementResult = remember(journalEntries, journalLines, fromTime, now) {
        runCatching {
            CashFlowStatementCalculator.calculate(
                periodStartInclusive = fromTime,
                periodEndInclusive = now,
                asOfInclusive = now,
                cashAccountIds = setOf("SYS-1000"),
                entries = journalEntries,
                lines = journalLines
            )
        }
    }
    val cashFlowStatement = cashFlowStatementResult.getOrNull()
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

    val generalLedgerPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isExportingStatementsPdf = true
                statementsPdfMessage = null
                try {
                    val currentTrialBalance = trialBalance
                        ?: throw IllegalStateException(trialBalanceResult.exceptionOrNull()?.message ?: "لا تتوفر بيانات ميزان المراجعة")
                    val currentStatements = generalLedgerStatements
                        ?: throw IllegalStateException(generalLedgerStatementsResult.exceptionOrNull()?.message ?: "لا تتوفر القوائم المالية")
                    GeneralLedgerStatementsPdfExporter.write(
                        context = context,
                        destination = uri,
                        trialBalance = currentTrialBalance,
                        financialStatements = currentStatements,
                        cashFlowStatement = cashFlowStatement,
                        generatedAt = now
                    )
                    statementsPdfMessage = "تم حفظ القوائم المالية وميزان المراجعة والتدفقات النقدية بصيغة PDF."
                } catch (error: Exception) {
                    statementsPdfMessage = error.message ?: "تعذر إنشاء ملف القوائم المالية"
                } finally {
                    isExportingStatementsPdf = false
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
            Text(if (isExportingReport) "جارٍ تجهيز التقرير..." else "تصدير تقرير المبيعات التفصيلي إلى PDF")
        }
        reportExportMessage?.let {
            Text(it, color = if (it.startsWith("تم")) SoftGreen else SoftRed, fontSize = 13.sp)
        }
        Button(
            onClick = {
                val periodSuffix = when (selectedPeriod) {
                    "TODAY" -> "Today"
                    "MONTH" -> "Month"
                    else -> "All"
                }
                val dateSuffix = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                generalLedgerPdfLauncher.launch("GoodGirl_Financial_Statements_" + periodSuffix + "_" + dateSuffix + ".pdf")
            },
            enabled = !isExportingStatementsPdf && trialBalance != null && generalLedgerStatements != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isExportingStatementsPdf) "جارٍ تجهيز القوائم المالية..." else "تصدير القوائم المالية وميزان المراجعة إلى PDF")
        }
        statementsPdfMessage?.let {
            Text(it, color = if (it.startsWith("تم")) SoftGreen else SoftRed, fontSize = 13.sp)
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
            color = MutedText,
            fontSize = 13.sp
        )

        TextButton(onClick = { showTrialBalance = !showTrialBalance }) {
            Text(if (showTrialBalance) "إخفاء ميزان المراجعة" else "عرض ميزان المراجعة")
        }
        if (showTrialBalance) {
            Text(
                "ميزان المراجعة حتى " + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now)),
                style = MaterialTheme.typography.titleLarge,
                color = Ink,
                fontWeight = FontWeight.Bold
            )
            val trialBalanceError = trialBalanceResult.exceptionOrNull()
            if (trialBalanceError != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardSurface)
                ) {
                    Text(
                        "تعذر حساب ميزان المراجعة: " + (trialBalanceError.message ?: "بيانات القيود غير سليمة"),
                        modifier = Modifier.padding(16.dp),
                        color = SoftRed
                    )
                }
            } else if (trialBalance == null || trialBalance.rows.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardSurface)
                ) {
                    Text(
                        "لا توجد قيود محاسبية مرحلة حتى الآن.",
                        modifier = Modifier.padding(16.dp),
                        color = MutedText
                    )
                }
            } else {
                trialBalance.currencySummaries.forEach { summary ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = CardSurface)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text("ملخص العملة: " + summary.currencyCode, color = BrandBlue, fontWeight = FontWeight.Bold)
                            ReportValueRow("إجمالي الحركات المدينة", summary.totalDebits, summary.currencyCode)
                            ReportValueRow("إجمالي الحركات الدائنة", summary.totalCredits, summary.currencyCode)
                            ReportValueRow("الأرصدة المدينة", summary.debitBalances, summary.currencyCode, strong = true)
                            ReportValueRow("الأرصدة الدائنة", summary.creditBalances, summary.currencyCode, strong = true)
                        }
                    }
                }
                Text("تفاصيل الحسابات", color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                trialBalance.rows.forEach { row ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CardSurface)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(row.accountCode + " — " + row.accountName, color = Ink, fontWeight = FontWeight.Bold)
                            val accountTypeLabel = when (row.accountType) {
                                "ASSET" -> "أصول"
                                "LIABILITY" -> "التزامات"
                                "EQUITY" -> "حقوق ملكية"
                                "REVENUE" -> "إيرادات"
                                "EXPENSE" -> "مصروفات"
                                else -> "غير محدد"
                            }
                            Text("العملة: " + row.currencyCode + "   |   نوع الحساب: " + accountTypeLabel, color = MutedText, fontSize = 12.sp)
                            ReportValueRow("إجمالي مدين", row.totalDebit, row.currencyCode)
                            ReportValueRow("إجمالي دائن", row.totalCredit, row.currencyCode)
                            ReportValueRow("الرصيد المدين", row.debitBalance, row.currencyCode, strong = true)
                            ReportValueRow("الرصيد الدائن", row.creditBalance, row.currencyCode, strong = true)
                        }
                    }
                }
                Text(
                    "كل عملة معروضة بشكل مستقل. ميزان المراجعة مبني على قيود اليومية المرحلة فقط ولا يجمع الليرة والدولار.",
                    color = MutedText,
                    fontSize = 12.sp
                )
            }
        }

        TextButton(onClick = { showLedgerStatements = !showLedgerStatements }) {
            Text(if (showLedgerStatements) "إخفاء القوائم المالية من دفتر الأستاذ" else "عرض القوائم المالية من دفتر الأستاذ")
        }
        if (showLedgerStatements) {
            Text(
                "قائمة الدخل والميزانية العمومية — من دفتر الأستاذ العام",
                style = MaterialTheme.typography.titleLarge,
                color = Ink,
                fontWeight = FontWeight.Bold
            )
            val statementError = generalLedgerStatementsResult.exceptionOrNull()
            if (statementError != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardSurface)
                ) {
                    Text(
                        "تعذر إنشاء القوائم المالية: " + (statementError.message ?: "بيانات القيود غير سليمة"),
                        modifier = Modifier.padding(16.dp),
                        color = SoftRed
                    )
                }
            } else if (generalLedgerStatements == null ||
                (generalLedgerStatements.balanceSheets.isEmpty() && generalLedgerStatements.incomeStatements.isEmpty())
            ) {
                Text("لا توجد قيود محاسبية كافية لإنشاء القوائم المالية.", color = MutedText)
            } else {
                generalLedgerStatements.incomeStatements.forEach { statement ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardSurface)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("قائمة الدخل — " + statement.currencyCode, color = BrandBlue, fontWeight = FontWeight.Bold)
                            Text("الإيرادات", color = Ink, fontWeight = FontWeight.SemiBold)
                            statement.revenues.forEach { row ->
                                ReportValueRow(row.accountCode + " — " + row.accountName, row.amount, statement.currencyCode)
                            }
                            ReportValueRow("إجمالي الإيرادات", statement.totalRevenue, statement.currencyCode, strong = true)
                            androidx.compose.material3.Divider(color = LightGray)
                            Text("المصروفات", color = Ink, fontWeight = FontWeight.SemiBold)
                            statement.expenses.forEach { row ->
                                ReportValueRow(row.accountCode + " — " + row.accountName, row.amount, statement.currencyCode)
                            }
                            ReportValueRow("إجمالي المصروفات", statement.totalExpenses, statement.currencyCode, strong = true)
                            androidx.compose.material3.Divider(color = LightGray)
                            ReportValueRow("صافي ربح / خسارة الفترة", statement.netIncome, statement.currencyCode, strong = true)
                        }
                    }
                }
                generalLedgerStatements.balanceSheets.forEach { statement ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardSurface)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("الميزانية العمومية — " + statement.currencyCode, color = BrandBlue, fontWeight = FontWeight.Bold)
                            Text("الأصول", color = Ink, fontWeight = FontWeight.SemiBold)
                            statement.assets.forEach { row ->
                                ReportValueRow(row.accountCode + " — " + row.accountName, row.amount, statement.currencyCode)
                            }
                            ReportValueRow("إجمالي الأصول", statement.totalAssets, statement.currencyCode, strong = true)
                            androidx.compose.material3.Divider(color = LightGray)
                            Text("الالتزامات", color = Ink, fontWeight = FontWeight.SemiBold)
                            statement.liabilities.forEach { row ->
                                ReportValueRow(row.accountCode + " — " + row.accountName, row.amount, statement.currencyCode)
                            }
                            ReportValueRow("إجمالي الالتزامات", statement.totalLiabilities, statement.currencyCode, strong = true)
                            androidx.compose.material3.Divider(color = LightGray)
                            Text("حقوق الملكية", color = Ink, fontWeight = FontWeight.SemiBold)
                            statement.equity.forEach { row ->
                                ReportValueRow(row.accountCode + " — " + row.accountName, row.amount, statement.currencyCode)
                            }
                            ReportValueRow("إجمالي حقوق الملكية", statement.totalEquity, statement.currencyCode)
                            ReportValueRow("الأرباح المحتجزة / نتيجة الفترات", statement.retainedEarnings, statement.currencyCode)
                            ReportValueRow("إجمالي الالتزامات وحقوق الملكية", statement.totalLiabilitiesAndEquity, statement.currencyCode, strong = true)
                            val balanced = statement.totalAssets.compareTo(statement.totalLiabilitiesAndEquity) == 0
                            Text(
                                if (balanced) "الميزانية متوازنة محاسبياً" else "تنبيه: الميزانية غير متوازنة",
                                color = if (balanced) SoftGreen else SoftRed,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            Text(
                "هذه القوائم مستخرجة من قيود اليومية المرحلة. الأرصدة السابقة لترقية قاعدة البيانات تظهر كأرصدة افتتاحية، ولا تعيد بناء أرباح الفترات التاريخية السابقة للترقية.",
                color = MutedText,
                fontSize = 12.sp
            )
        }

        TextButton(onClick = { showCashFlow = !showCashFlow }) {
            Text(if (showCashFlow) "إخفاء قائمة التدفقات النقدية" else "عرض قائمة التدفقات النقدية")
        }
        if (showCashFlow) {
            Text(
                "قائمة التدفقات النقدية — من حركة الصندوق",
                style = MaterialTheme.typography.titleLarge,
                color = Ink,
                fontWeight = FontWeight.Bold
            )
            val cashFlowError = cashFlowStatementResult.exceptionOrNull()
            if (cashFlowError != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardSurface)
                ) {
                    Text(
                        "تعذر حساب التدفقات النقدية: " + (cashFlowError.message ?: "بيانات القيود غير سليمة"),
                        modifier = Modifier.padding(16.dp),
                        color = SoftRed
                    )
                }
            } else if (cashFlowStatement == null || !cashFlowStatement.coverageAvailable) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardSurface)
                ) {
                    Text(
                        "لا يمكن إعداد تدفقات نقدية موثوقة لهذه الفترة؛ فهي تسبق تاريخ ترحيل الأرصدة الافتتاحية للمحاسبة العامة.",
                        modifier = Modifier.padding(16.dp),
                        color = SoftRed
                    )
                }
            } else if (cashFlowStatement.statements.isEmpty()) {
                Text("لا توجد حركات نقدية في دفتر الأستاذ خلال الفترة المحددة.", color = MutedText)
            } else {
                if (cashFlowStatement.historyTruncated) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardSurface)
                    ) {
                        Text(
                            "تنبيه: لا يمكن إعادة بناء حركة الصندوق قبل ترقية المحاسبة العامة. يبدأ التقرير من تاريخ الرصيد الافتتاحي المرحّل: " +
                                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(cashFlowStatement.effectiveStartInclusive)),
                            modifier = Modifier.padding(14.dp),
                            color = SoftPurple,
                            fontSize = 12.sp
                        )
                    }
                }
                cashFlowStatement.statements.forEach { statement ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardSurface)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("العملة: " + statement.currencyCode, color = BrandBlue, fontWeight = FontWeight.Bold)
                            ReportValueRow("رصيد النقدية الافتتاحي", statement.openingCashBalance, statement.currencyCode, strong = true)
                            statement.activities.forEach { activity ->
                                val sectionLabel = when (activity.section) {
                                    CashFlowStatementCalculator.OPERATING -> "الأنشطة التشغيلية"
                                    CashFlowStatementCalculator.INVESTING -> "الأنشطة الاستثمارية"
                                    CashFlowStatementCalculator.FINANCING -> "الأنشطة التمويلية"
                                    else -> "حركات نقدية غير مصنفة"
                                }
                                Text(sectionLabel, color = Ink, fontWeight = FontWeight.SemiBold)
                                ReportValueRow("المقبوضات", activity.cashInflows, statement.currencyCode)
                                ReportValueRow("المدفوعات", activity.cashOutflows, statement.currencyCode)
                                ReportValueRow("صافي النشاط", activity.netCashFlow, statement.currencyCode, strong = true)
                                if (activity.section == CashFlowStatementCalculator.UNCLASSIFIED && activity.transactionCount > 0) {
                                    Text(
                                        "عدد الحركات التي تحتاج إلى تصنيف محاسبي: " + activity.transactionCount,
                                        color = SoftPurple,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            androidx.compose.material3.Divider(color = LightGray)
                            ReportValueRow("صافي التغير في النقدية", statement.netCashChange, statement.currencyCode, strong = true)
                            ReportValueRow("رصيد النقدية الختامي", statement.closingCashBalance, statement.currencyCode, strong = true)
                            ReportValueRow("رصيد الصندوق الفعلي من الأستاذ", statement.actualClosingCashBalance, statement.currencyCode)
                            val reconciled = statement.reconciliationDifference.compareTo(BigDecimal.ZERO) == 0
                            Text(
                                if (reconciled) "تمت مطابقة رصيد التدفقات مع حساب الصندوق" else
                                    "فرق المطابقة: " + statement.reconciliationDifference.stripTrailingZeros().toPlainString() + " " + statement.currencyCode,
                                color = if (reconciled) SoftGreen else SoftRed,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            Text(
                "يعرض التقرير فقط حساب الصندوق الحالي، ويصنّف مصادر البيع والشراء والتحصيلات والدفعات والإيراد والمصروف كأنشطة تشغيلية. القيود اليدوية غير المعروفة تظهر ضمن غير المصنفة ولا تُخفى.",
                color = MutedText,
                fontSize = 12.sp
            )
        }

        if (report.currencySummaries.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardSurface)) {
                Text("لا توجد بيانات مالية ضمن الفترة المحددة.", modifier = Modifier.padding(18.dp), color = MutedText)
            }
        } else {
            report.currencySummaries.forEach { summary ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = CardSurface)) {
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
            Text("لا توجد مبيعات مواد ضمن الفترة المحددة.", color = MutedText, fontSize = 13.sp)
        } else {
            report.products.take(20).forEach { product ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = CardSurface)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(product.productName, color = Ink, fontWeight = FontWeight.Bold)
                        Text("الكمية بوحدة الأساس: " + product.quantity.stripTrailingZeros().toPlainString(), color = MutedText, fontSize = 12.sp)
                        Text("صافي المبيعات: " + product.revenue.stripTrailingZeros().toPlainString() + " " + product.currencyCode, color = BrandBlue, fontSize = 12.sp)
                        Text("تكلفة البضاعة: " + product.cost.stripTrailingZeros().toPlainString() + " " + product.currencyCode, color = MutedText, fontSize = 12.sp)
                        Text(
                            "ربح المادة: " + product.profit.stripTrailingZeros().toPlainString() + " " + product.currencyCode,
                            color = if (product.profit >= BigDecimal.ZERO) SoftGreen else SoftRed,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        Text(
            "ملاحظة: الأرباح تحسب من فواتير البيع وتكلفة المواد المحفوظة وقت البيع، وتُعرض كل عملة مستقلة دون تحويل تلقائي.",
            color = MutedText,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun ReportValueRow(label: String, value: BigDecimal, currencyCode: String, strong: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = if (strong) Ink else MutedText, fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
        Text(
            value.stripTrailingZeros().toPlainString() + " " + currencyCode,
            color = if (strong) BrandBlue else Ink,
            fontWeight = if (strong) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun CashManagementScreen(
    onBack: () -> Unit,
    onUnsavedChangesChanged: (Boolean) -> Unit
) {
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
    val hasUnsavedCashDraft = hasUnsavedCashDraft(amount, description)
    LaunchedEffect(hasUnsavedCashDraft) {
        onUnsavedChangesChanged(hasUnsavedCashDraft)
    }

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
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("الصندوق والمصروفات", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Ink)
        Text("تسجيل الإيرادات والمصروفات ومتابعة حركات القبض والصرف", color = MutedText, fontSize = 13.sp)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("حركة مالية جديدة", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { selectedKind = "INCOME"; message = null },
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedKind == "INCOME") SoftGreen else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (selectedKind == "INCOME") Color.White else Ink
                        )
                    ) { Text(if (selectedKind == "INCOME") "✓ إيراد" else "إيراد") }
                    Button(
                        onClick = { selectedKind = "EXPENSE"; message = null },
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedKind == "EXPENSE") SoftRed else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (selectedKind == "EXPENSE") Color.White else Ink
                        )
                    ) { Text(if (selectedKind == "EXPENSE") "✓ مصروف" else "مصروف") }
                }

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("المبلغ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { currencyCode = "SYP"; exchangeRate = "1" },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currencyCode == "SYP") BrandBlue else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (currencyCode == "SYP") Color.White else Ink
                        )
                    ) { Text(if (currencyCode == "SYP") "✓ ليرة سورية" else "ليرة سورية", fontSize = 12.sp) }
                    Button(
                        onClick = {
                            currencyCode = "USD"
                            exchangeRate = context.getSharedPreferences(
                                "good_girl_settings",
                                android.content.Context.MODE_PRIVATE
                            ).getString("usd_syp_rate", "1") ?: "1"
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currencyCode == "USD") BrandBlue else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (currencyCode == "USD") Color.White else Ink
                        )
                    ) { Text(if (currencyCode == "USD") "✓ دولار أمريكي" else "دولار أمريكي", fontSize = 12.sp) }
                }

                if (currencyCode == "USD") {
                    OutlinedTextField(
                        value = exchangeRate,
                        onValueChange = { exchangeRate = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("سعر صرف الدولار مقابل الليرة") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("البيان أو سبب الحركة") },
                    minLines = 2,
                    shape = RoundedCornerShape(12.dp)
                )

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
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                ) {
                    Text(if (isSaving) "جارٍ الحفظ..." else if (selectedKind == "INCOME") "تسجيل الإيراد" else "تسجيل المصروف")
                }
                message?.let {
                    Text(it, color = if (it.startsWith("تم")) SoftGreen else SoftRed, fontSize = 13.sp)
                }
            }
        }

        Text("ملخص الصندوق حسب العملة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
        if (totals.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface)
            ) {
                Text("لا توجد حركات نقدية مسجلة بعد.", modifier = Modifier.padding(18.dp), color = MutedText)
            }
        } else {
            totals.forEach { (currency, pair) ->
                val net = pair.first.subtract(pair.second)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(currency, color = BrandBlue, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("إجمالي المقبوض: " + pair.first.stripTrailingZeros().toPlainString(), color = SoftGreen, fontSize = 13.sp)
                        Text("إجمالي المصروف: " + pair.second.stripTrailingZeros().toPlainString(), color = SoftRed, fontSize = 13.sp)
                        Text("صافي الحركات: " + net.stripTrailingZeros().toPlainString(), color = Ink, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Text("آخر حركات الصندوق", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
        if (cashMovements.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface)
            ) {
                Text("لا توجد حركات بعد.", modifier = Modifier.padding(18.dp), color = MutedText)
            }
        } else {
            cashMovements.take(30).forEach { entry ->
                val isIn = entry.entryType == "CASH_IN"
                val date = remember(entry.occurredAt) {
                    SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(entry.occurredAt))
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (isIn) "قبض" else "صرف", color = if (isIn) SoftGreen else SoftRed, fontWeight = FontWeight.Bold)
                        Text(entry.amount + " " + entry.currencyCode, color = Ink, fontWeight = FontWeight.SemiBold)
                        Text(date, color = MutedText, fontSize = 11.sp)
                        if (entry.note.isNotBlank()) Text(entry.note, color = MutedText, fontSize = 12.sp)
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
            color = MutedText,
            fontSize = 13.sp
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface)
            ) {
                Column(modifier = Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ديون العملاء", color = MutedText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(formatCurrencyTotals(customerTotals), color = BrandBlue, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(customerRows.size.toString() + " حساب عليه رصيد", color = MutedText, fontSize = 11.sp)
                }
            }
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface)
            ) {
                Column(modifier = Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ديون الموردين", color = MutedText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(formatCurrencyTotals(supplierTotals), color = SoftRed, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(supplierRows.size.toString() + " حساب له رصيد", color = MutedText, fontSize = 11.sp)
                }
            }
        }

        Text("المبالغ المستحقة لنا", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
        if (customerRows.isEmpty()) {
            Text("لا توجد أرصدة مستحقة على العملاء حالياً.", color = MutedText, fontSize = 13.sp)
        } else {
            customerRows.forEach { (party, balances) ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenStatement(party.id) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface)
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
            Text("لا توجد أرصدة مستحقة للموردين حالياً.", color = MutedText, fontSize = 13.sp)
        } else {
            supplierRows.forEach { (party, balances) ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenStatement(party.id) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(party.name, color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        balances.forEach { (currency, amount) ->
                            Text(
                                amount.stripTrailingZeros().toPlainString() + " " + currency,
                                color = SoftRed,
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
private fun PartyStatementScreen(
    partyId: String,
    onBack: () -> Unit,
    onUnsavedChangesChanged: (Boolean) -> Unit
) {
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
    var showDiscardPaymentDialog by remember { mutableStateOf(false) }
    val hasUnsavedPaymentDraft = hasUnsavedPartyPaymentDraft(
        isFormOpen = showPaymentForm,
        amount = paymentAmount,
        note = paymentNote
    )
    LaunchedEffect(hasUnsavedPaymentDraft) {
        onUnsavedChangesChanged(hasUnsavedPaymentDraft)
    }
    fun requestClosePaymentForm() {
        if (hasUnsavedPartyPaymentDraft(
                isFormOpen = showPaymentForm,
                amount = paymentAmount,
                note = paymentNote
            )
        ) {
            showDiscardPaymentDialog = true
        } else {
            showPaymentForm = false
        }
    }

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
            Text("الحساب غير موجود", color = SoftRed)
        } else {
            Text(party?.name.orEmpty(), color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            val balances = PartyBalanceCalculator.calculate(party?.kind.orEmpty(), entries)
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardSurface)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (party?.kind == "CUSTOMER") "المبالغ المستحقة لنا" else "المبالغ المستحقة علينا", color = MutedText, fontSize = 13.sp)
                    if (balances.isEmpty()) {
                        Text("لا يوجد رصيد مستحق", color = SoftGreen, fontWeight = FontWeight.Bold)
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
                Text(statementPdfMessage.orEmpty(), color = if (statementPdfMessage!!.startsWith("تم")) SoftGreen else SoftRed)
            }
            Button(onClick = {
                if (showPaymentForm) {
                    requestClosePaymentForm()
                } else {
                    showPaymentForm = true
                    paymentMessage = ""
                    if (party?.currencyCode == "SYP") {
                        paymentCurrency = "SYP"
                        paymentRate = "1"
                    } else if (party?.currencyCode == "USD") {
                        paymentCurrency = "USD"
                        paymentRate = context.getSharedPreferences("good_girl_settings", android.content.Context.MODE_PRIVATE).getString("usd_syp_rate", "1") ?: "1"
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text(if (party?.kind == "CUSTOMER") "تسجيل تحصيل مستقل" else "تسجيل دفعة مستقلة")
            }
            if (showPaymentForm) {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardSurface)) {
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
                            Text(paymentMessage, color = if (paymentMessage.startsWith("تم")) SoftGreen else SoftRed)
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
                        TextButton(onClick = { requestClosePaymentForm() }) { Text("إغلاق") }
                    }
                }
            }
            Text("الحركات المالية", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
            if (runningRows.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardSurface)) {
                    Text("لا توجد حركات مالية لهذا الحساب بعد.", modifier = Modifier.padding(18.dp), color = MutedText)
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
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = CardSurface)) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(title, color = Ink, fontWeight = FontWeight.Bold)
                            Text(dateText, color = MutedText, fontSize = 11.sp)
                            Text(
                                (if (increases) "+" else "-") + entry.amount + " " + entry.currencyCode,
                                color = if (increases) SoftRed else SoftGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text("الرصيد بعد الحركة: " + balanceAfter.stripTrailingZeros().toPlainString() + " " + entry.currencyCode, color = BrandBlue, fontSize = 12.sp)
                            if (entry.note.isNotBlank()) Text(entry.note, color = MutedText, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
    if (showDiscardPaymentDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardPaymentDialog = false },
            title = { Text("لديك حركة غير محفوظة") },
            text = { Text("هل تريد إغلاق نموذج الحركة والتخلص من المبلغ أو الملاحظات المدخلة؟") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardPaymentDialog = false
                        showPaymentForm = false
                        paymentAmount = ""
                        paymentNote = ""
                    }
                ) { Text("إغلاق دون حفظ") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardPaymentDialog = false }) {
                    Text("متابعة الإدخال")
                }
            }
        )
    }
}

@Composable
private fun PartyManagementScreen(
    onBack: () -> Unit,
    onOpenStatement: (String) -> Unit,
    onUnsavedChangesChanged: (Boolean) -> Unit
) {
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

    val editingParty = parties.firstOrNull { it.id == editingId }
    val partyDraftValues = PartyFormValues(
        name = name,
        phone = phone,
        address = address,
        note = note,
        openingBalance = openingBalance,
        openingCurrency = openingCurrency,
        openingExchangeRate = openingExchangeRate
    )
    val originalPartyDraftValues = editingParty?.let { party ->
        PartyFormValues(
            name = party.name,
            phone = party.phone,
            address = party.address,
            note = party.note,
            openingBalance = party.openingBalance,
            openingCurrency = party.currencyCode,
            openingExchangeRate = party.openingExchangeRate
        )
    }
    val hasUnsavedPartyChanges =
        (editingId != null && editingParty == null) ||
            hasUnsavedPartyForm(partyDraftValues, originalPartyDraftValues)
    LaunchedEffect(hasUnsavedPartyChanges) {
        onUnsavedChangesChanged(hasUnsavedPartyChanges)
    }

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
        if (hasUnsavedPartyChanges) {
            message = "احفظ أو ألغِ البيانات الحالية قبل فتح جهة تعامل أخرى."
            return
        }
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
        Text("إدارة بيانات جهات التعامل وحفظها محلياً على الهاتف", color = MutedText, fontSize = 13.sp)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (hasUnsavedPartyChanges) {
                        message = "احفظ أو ألغِ البيانات الحالية قبل التبديل بين العملاء والموردين."
                    } else {
                        selectedKind = "CUSTOMER"
                        clearForm()
                        message = null
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("العملاء") }
            Button(
                onClick = {
                    if (hasUnsavedPartyChanges) {
                        message = "احفظ أو ألغِ البيانات الحالية قبل التبديل بين العملاء والموردين."
                    } else {
                        selectedKind = "SUPPLIER"
                        clearForm()
                        message = null
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("الموردون") }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface)
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
                        color = MutedText,
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
                    Text(it, color = if (it.startsWith("تم")) SoftGreen else SoftRed, fontSize = 13.sp)
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
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardSurface)) {
                Text("لا توجد سجلات بعد. أضف أول جهة تعامل من النموذج أعلاه.", modifier = Modifier.padding(18.dp), color = MutedText)
            }
        } else {
            parties.forEach { party ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardSurface)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(party.name, color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            TextButton(onClick = { loadParty(party) }) { Text("تعديل") }
                        }
                        if (party.phone.isNotBlank()) Text("الهاتف: " + party.phone, color = MutedText, fontSize = 12.sp)
                        if (party.address.isNotBlank()) Text("العنوان: " + party.address, color = MutedText, fontSize = 12.sp)
                        val balances = PartyBalanceCalculator.calculate(party.kind, ledgerEntries.filter { it.partyId == party.id })
                        balances.forEach { (currency, balance) ->
                            val label = if (party.kind == "CUSTOMER") "مستحق لنا" else "مستحق علينا"
                            Text(label + ": " + balance.stripTrailingZeros().toPlainString() + " " + currency, color = BrandBlue, fontSize = 12.sp)
                        }
                        TextButton(onClick = { onOpenStatement(party.id) }) { Text("كشف الحساب") }
                        if (party.note.isNotBlank()) Text("ملاحظة: " + party.note, color = MutedText, fontSize = 12.sp)
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
            Text("المنتج غير موجود", color = SoftRed)
        } else {
            Text(product?.name.orEmpty(), color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardSurface)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("الرصيد الحالي", color = MutedText, fontSize = 12.sp)
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
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardSurface)) {
                    Text("لا توجد حركات مسجلة لهذا المنتج بعد.", modifier = Modifier.padding(18.dp), color = MutedText)
                }
            } else {
                movements.forEach { movement ->
                    val isIncrease = movement.quantityDelta.toBigDecimal() > BigDecimal.ZERO
                    val quantity = movement.quantityDelta.toBigDecimal().abs().stripTrailingZeros().toPlainString()
                    val date = remember(movement.occurredAt) {
                        SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(movement.occurredAt))
                    }
                    val isCurrencyCorrection = movement.sourceType == "PRODUCT_CURRENCY_CORRECTION"
                    val sourceLabel = when (movement.sourceType) {
                        "PRODUCT_CURRENCY_CORRECTION" -> "تصحيح عملة المنتج"
                        "OPENING_BALANCE" -> "رصيد افتتاحي"
                        "MANUAL_ADJUSTMENT" -> "تسوية مخزون يدوية"
                        "STOCKTAKE_ADJUSTMENT" -> "تسوية ناتجة عن الجرد"
                        "MIGRATION_RECONCILIATION" -> "تسوية رصيد تاريخية (تحديث التطبيق)"
                        "SALE_INVOICE" -> "فاتورة بيع"
                        "PURCHASE_INVOICE" -> "فاتورة شراء"
                        else -> "حركة مخزون"
                    }
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = CardSurface)) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(sourceLabel, color = Ink, fontWeight = FontWeight.Bold)
                                Text(
                                    if (isCurrencyCorrection) "دون تغيير الكمية" else (if (isIncrease) "+" else "-") + quantity + " " + (product?.baseUnit ?: ""),
                                    color = if (isCurrencyCorrection) BrandBlue else if (isIncrease) SoftGreen else SoftRed,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(date, color = MutedText, fontSize = 11.sp)
                            Text(
                                "كلفة وحدة الأساس: " + movement.unitCostSnapshot + " " + movement.currencyCode,
                                color = MutedText,
                                fontSize = 12.sp
                            )
                            if (movement.note.isNotBlank()) {
                                Text(movement.note, color = MutedText, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductCategoriesScreen(
    onBack: () -> Unit,
    onUnsavedChangesChanged: (Boolean) -> Unit
) {
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

    val editingCategory = categories.firstOrNull { it.id == editingId }
    val categoryDraftValues = CategoryFormValues(name = name, note = note)
    val originalCategoryDraftValues = editingCategory?.let {
        CategoryFormValues(name = it.name, note = it.note)
    }
    val hasUnsavedCategoryChanges =
        (editingId != null && editingCategory == null) ||
            hasUnsavedCategoryForm(categoryDraftValues, originalCategoryDraftValues)
    LaunchedEffect(hasUnsavedCategoryChanges) {
        onUnsavedChangesChanged(hasUnsavedCategoryChanges)
    }

    fun clearForm() {
        editingId = null
        name = ""
        note = ""
    }

    fun loadCategory(category: CategoryEntity) {
        if (hasUnsavedCategoryChanges) {
            message = "احفظ أو ألغِ بيانات التصنيف الحالية قبل فتح تصنيف آخر."
            return
        }
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
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = CardSurface)) {
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
                    Text(it, color = if (it.startsWith("تم")) SoftGreen else SoftRed, fontSize = 13.sp)
                }
            }
        }
        Text("التصنيفات المسجلة (${categories.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
        if (categories.isEmpty()) {
            Text("لم تتم إضافة تصنيفات بعد. يمكنك ترك المنتجات دون تصنيف.", color = MutedText, fontSize = 13.sp)
        } else {
            categories.forEach { category ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = CardSurface)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(category.name, color = Ink, fontWeight = FontWeight.Bold)
                            if (category.note.isNotBlank()) Text(category.note, color = MutedText, fontSize = 12.sp)
                        }
                        TextButton(onClick = { loadCategory(category) }) { Text("تعديل") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductUnitsScreen(
    productId: String,
    onBack: () -> Unit,
    onUnsavedChangesChanged: (Boolean) -> Unit
) {
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
    val hasUnsavedUnitChanges = hasUnsavedUnitDraft(unitName, factor)
    LaunchedEffect(hasUnsavedUnitChanges) {
        onUnsavedChangesChanged(hasUnsavedUnitChanges)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("العودة إلى المنتجات") }
        Text("وحدات قياس المنتج", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Ink)
        if (product == null) {
            Text("المنتج غير موجود", color = SoftRed)
        } else {
            Text(product?.name.orEmpty(), color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                "وحدة الأساس: " + product?.baseUnit + " — معامل التحويل يحدد عدد وحدات الأساس في الوحدة الإضافية.",
                color = MutedText,
                fontSize = 13.sp
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("إضافة وحدة جديدة", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink)
                    OutlinedTextField(unitName, { unitName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("اسم الوحدة الإضافية") }, singleLine = true)
                    OutlinedTextField(factor, { factor = it }, modifier = Modifier.fillMaxWidth(), label = { Text("كم وحدة أساس تحتوي؟") }, singleLine = true)
                    Text(
                        "مثال: وحدة الأساس قطعة، والكرتونة تحتوي 12 قطعة؛ اكتب 12.",
                        color = MutedText,
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
                        Text(it, color = if (it.startsWith("تم")) SoftGreen else SoftRed, fontSize = 13.sp)
                    }
                }
            }
            Text("الوحدات المتاحة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
            units.forEach { unit ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = CardSurface)) {
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
    onViewHistory: (String) -> Unit,
    onUnsavedChangesChanged: (Boolean) -> Unit
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
    var currencyCorrectionReason by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var adjustingProductId by remember { mutableStateOf<String?>(null) }
    var adjustmentDelta by remember { mutableStateOf("") }
    var adjustmentCost by remember { mutableStateOf("0") }
    var stocktakeMode by remember { mutableStateOf(false) }
    var countedQuantity by remember { mutableStateOf("") }
    var adjustmentNote by remember { mutableStateOf("") }
    var stockMessage by remember { mutableStateOf<String?>(null) }
    var isSavingStock by remember { mutableStateOf(false) }

    val editingProduct = products.firstOrNull { it.id == editingId }
    val productFormValues = ProductFormValues(
        categoryId = selectedCategoryId,
        name = name,
        sku = sku,
        baseUnit = unit,
        purchaseCost = purchaseCost,
        salePrice = salePrice,
        currencyCode = priceCurrencyCode,
        openingStock = openingStock,
        lowStockThreshold = lowStockThreshold,
        note = note
    )
    val originalProductFormValues = editingProduct?.let { product ->
        ProductFormValues(
            categoryId = product.categoryId,
            name = product.name,
            sku = product.sku,
            baseUnit = product.baseUnit,
            purchaseCost = product.purchaseCost,
            salePrice = product.salePrice,
            currencyCode = product.priceCurrencyCode,
            openingStock = "0",
            lowStockThreshold = product.lowStockThreshold,
            note = product.note
        )
    }
    val adjustingProduct = products.firstOrNull { it.id == adjustingProductId }
    val hasUnsavedProductChanges =
        (editingId != null && editingProduct == null) ||
            hasUnsavedProductForm(productFormValues, originalProductFormValues) ||
            hasUnsavedStockAdjustment(
                StockAdjustmentDraft(
                    isOpen = adjustingProduct != null,
                    stocktakeMode = stocktakeMode,
                    currentStock = adjustingProduct?.stockQuantity.orEmpty(),
                    countedQuantity = countedQuantity,
                    quantityDelta = adjustmentDelta,
                    unitCost = adjustmentCost,
                    originalUnitCost = adjustingProduct?.purchaseCost.orEmpty(),
                    note = adjustmentNote
                )
            )
    LaunchedEffect(hasUnsavedProductChanges) {
        onUnsavedChangesChanged(hasUnsavedProductChanges)
    }

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
        currencyCorrectionReason = ""
    }

    fun startEditing(product: ProductEntity) {
        if (hasUnsavedProductChanges) {
            if (adjustingProductId != null) {
                stockMessage = "احفظ أو ألغِ حركة المخزون الحالية قبل اختيار منتج آخر."
            } else {
                message = "احفظ أو ألغِ مسودة المنتج الحالية قبل اختيار منتج آخر."
            }
            return
        }
        resetForm()
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
        currencyCorrectionReason = ""
        lowStockThreshold = product.lowStockThreshold
        note = product.note
        message = null
    }

    fun startStockAdjustment(product: ProductEntity) {
        if (hasUnsavedProductChanges) {
            if (adjustingProductId != null) {
                stockMessage = "احفظ أو ألغِ حركة المخزون الحالية قبل اختيار منتج آخر."
            } else {
                message = "احفظ أو ألغِ مسودة المنتج الحالية قبل بدء حركة مخزون."
            }
            return
        }
        resetForm()
        adjustingProductId = product.id
        adjustmentDelta = ""
        adjustmentCost = product.purchaseCost
        stocktakeMode = false
        countedQuantity = product.stockQuantity
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
        Text("إضافة المواد وحفظ أرصدتها على الهاتف دون الحاجة إلى الإنترنت", color = MutedText, fontSize = 13.sp)
        TextButton(onClick = onManageCategories) { Text("إدارة تصنيفات المنتجات") }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface)
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
                val editingProduct = products.firstOrNull { it.id == editingId }
                val canEditPurchaseCost = editingProduct == null ||
                    (editingProduct.stockQuantity.toBigDecimalOrNull()?.compareTo(BigDecimal.ZERO) == 0)
                OutlinedTextField(
                    purchaseCost,
                    { purchaseCost = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("متوسط كلفة الشراء") },
                    enabled = canEditPurchaseCost,
                    singleLine = true
                )
                if (!canEditPurchaseCost) {
                    Text(
                        "متوسط الكلفة لا يُعدّل من بطاقة المنتج أثناء وجود مخزون. سجّل فاتورة شراء أو حركة إضافة مخزون لتحديثه مع حفظ أثر الحركة.",
                        color = MutedText,
                        fontSize = 12.sp
                    )
                }
                OutlinedTextField(salePrice, { salePrice = it }, modifier = Modifier.fillMaxWidth(), label = { Text("سعر البيع") }, singleLine = true)
                Text("عملة الكلفة وسعر البيع", color = MutedText, fontSize = 12.sp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        priceCurrencyCode = "SYP"
                        currencyCorrectionReason = if (editingProduct?.priceCurrencyCode != null && editingProduct.priceCurrencyCode != "SYP") {
                            "تصحيح خطأ في اختيار عملة المنتج"
                        } else ""
                    }) {
                        Text(if (priceCurrencyCode == "SYP") "✓ ليرة سورية" else "ليرة سورية")
                    }
                    TextButton(onClick = {
                        priceCurrencyCode = "USD"
                        currencyCorrectionReason = if (editingProduct?.priceCurrencyCode != null && editingProduct.priceCurrencyCode != "USD") {
                            "تصحيح خطأ في اختيار عملة المنتج"
                        } else ""
                    }) {
                        Text(if (priceCurrencyCode == "USD") "✓ دولار أمريكي" else "دولار أمريكي")
                    }
                }
                if (editingProduct != null && priceCurrencyCode != editingProduct.priceCurrencyCode) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = activePalette.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("تصحيح عملة المنتج", color = Ink, fontWeight = FontWeight.Bold)
                            Text(
                                "سيبقى سعر الشراء وسعر البيع بالقيم الرقمية نفسها، ولن يتم تحويلهما بسعر الصرف. ستُسجل حركة تصحيح للرصيد الحالي دون تغيير الكمية، وتبقى الفواتير وحركات المخزون السابقة كما هي.",
                                color = MutedText,
                                fontSize = 12.sp
                            )
                            OutlinedTextField(
                                value = currencyCorrectionReason,
                                onValueChange = { currencyCorrectionReason = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("سبب التصحيح *") },
                                minLines = 2
                            )
                        }
                    }
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
                    Text("الرصيد الحالي: $currentStock — يُعدّل عبر حركات المخزون وليس من بطاقة المنتج.", color = MutedText, fontSize = 12.sp)
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
                                        lowStockThreshold = lowStockThreshold,
                                        currencyCorrectionReason = if (editingProduct != null && editingProduct.priceCurrencyCode != priceCurrencyCode) currencyCorrectionReason else null,
                                        currencyCorrectionExchangeRate = context.getSharedPreferences(
                                            "good_girl_settings",
                                            android.content.Context.MODE_PRIVATE
                                        ).getString("usd_syp_rate", "1") ?: "1"
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
                                        lowStockThreshold = lowStockThreshold,
                                        openingExchangeRate = if (priceCurrencyCode == "SYP") "1" else (
                                            context.getSharedPreferences(
                                                "good_girl_settings",
                                                android.content.Context.MODE_PRIVATE
                                            ).getString("usd_syp_rate", "1") ?: "1"
                                        )
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
                        color = if (it.startsWith("تم")) SoftGreen else SoftRed,
                        fontSize = 13.sp
                    )
                }
                if (editingId != null) {
                    TextButton(onClick = { resetForm(); message = null }) { Text("إلغاء التعديل") }
                }
            }
        }


        val adjustingProduct = products.firstOrNull { it.id == adjustingProductId }
        val adjustmentDeltaPreview = if (stocktakeMode) {
            val current = adjustingProduct?.stockQuantity?.toBigDecimalOrNull()
            val counted = countedQuantity.trim().toBigDecimalOrNull()
            if (current != null && counted != null) counted.subtract(current) else null
        } else {
            adjustmentDelta.trim().toBigDecimalOrNull()
        }
        val isStockDecrease = adjustmentDeltaPreview?.signum() == -1
        val requiresIncomingCost = adjustmentDeltaPreview?.signum() == 1
        if (adjustingProduct != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Text(
                        (if (stocktakeMode) "جرد مخزون: " else "حركة مخزون: ") + adjustingProduct.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Ink
                    )
                    Text(
                        "الرصيد الحالي: " + adjustingProduct.stockQuantity + " " + adjustingProduct.baseUnit,
                        color = BrandBlue,
                        fontSize = 13.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(onClick = { stocktakeMode = false }) {
                            Text(if (!stocktakeMode) "✓ حركة إضافة أو سحب" else "حركة إضافة أو سحب")
                        }
                        TextButton(onClick = {
                            stocktakeMode = true
                            countedQuantity = adjustingProduct.stockQuantity
                        }) {
                            Text(if (stocktakeMode) "✓ جرد فعلي" else "جرد فعلي")
                        }
                    }
                    if (stocktakeMode) {
                        Text(
                            "أدخل الكمية الموجودة فعلياً بعد العدّ؛ سيحسب التطبيق الفرق ويسجّل حركة تسوية تلقائياً.",
                            color = MutedText,
                            fontSize = 12.sp
                        )
                        OutlinedTextField(
                            countedQuantity,
                            { countedQuantity = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("الكمية الفعلية بعد الجرد") },
                            singleLine = true
                        )
                    } else {
                        Text(
                            "اكتب كمية موجبة للإضافة أو سالبة للسحب، مثل 5 أو -2.",
                            color = MutedText,
                            fontSize = 12.sp
                        )
                        OutlinedTextField(
                            adjustmentDelta,
                            { adjustmentDelta = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("كمية الحركة (+ إضافة / - سحب)") },
                            singleLine = true
                        )
                    }
                    Text(
                        when {
                            isStockDecrease -> "سيُسجّل النقص بمتوسط الكلفة الحالي دون تغيير متوسط كلفة المنتج."
                            requiresIncomingCost == true -> "الزيادة ستدخل في متوسط كلفة المخزون؛ أدخل كلفة الكمية المضافة."
                            stocktakeMode && adjustmentDeltaPreview?.signum() == 0 -> "الكمية الفعلية تطابق الرصيد المسجل؛ لن تُنشأ حركة."
                            else -> "عند إضافة كمية، أدخل كلفتها ليُحدّث التطبيق متوسط كلفة المخزون تلقائياً."
                        },
                        color = MutedText,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        if (requiresIncomingCost == true) adjustmentCost else adjustingProduct.purchaseCost,
                        { adjustmentCost = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(
                                if (requiresIncomingCost == true) "كلفة الوحدة للكمية المضافة"
                                else "متوسط كلفة الوحدة المعتمد"
                            )
                        },
                        enabled = requiresIncomingCost == true,
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
                                    val noteToSave = (
                                        (if (stocktakeMode) "جرد فعلي للمخزون" else "تسوية مخزون يدوية") +
                                            if (adjustmentNote.isBlank()) "" else " — " + adjustmentNote.trim()
                                        )
                                    if (stocktakeMode) {
                                        val result = stockRepository.reconcileStocktake(
                                            productId = adjustingProduct.id,
                                            countedQuantity = countedQuantity,
                                            unitCost = adjustmentCost,
                                            note = noteToSave,
                                            exchangeRateSnapshot = if (adjustingProduct.priceCurrencyCode == "SYP") "1" else (
                                            context.getSharedPreferences(
                                                "good_girl_settings",
                                                android.content.Context.MODE_PRIVATE
                                            ).getString("usd_syp_rate", "1") ?: "1"
                                        )
                                        )
                                        stockMessage = if (result.adjustmentDelta == "0") {
                                            "تم التحقق من الجرد: الكمية مطابقة للرصيد المسجل، ولم تكن هناك حاجة إلى تسوية."
                                        } else {
                                            "تم حفظ الجرد وتسوية المخزون إلى " + result.resultingBalance
                                        }
                                        adjustingProductId = null
                                    } else {
                                        val deltaToSave = StockInputRules.normalizeDelta(adjustmentDelta)
                                        val newBalance = stockRepository.adjustStock(
                                            productId = adjustingProduct.id,
                                            quantityDelta = deltaToSave,
                                            unitCost = adjustmentCost,
                                            note = noteToSave,
                                            exchangeRateSnapshot = if (adjustingProduct.priceCurrencyCode == "SYP") "1" else (
                                            context.getSharedPreferences(
                                                "good_girl_settings",
                                                android.content.Context.MODE_PRIVATE
                                            ).getString("usd_syp_rate", "1") ?: "1"
                                        )
                                        )
                                        stockMessage = "تم تحديث الرصيد إلى " + newBalance
                                        adjustingProductId = null
                                    }
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
                        Text(
                            if (isSavingStock) "جارٍ الحفظ..."
                            else if (stocktakeMode) "حفظ نتيجة الجرد وتسوية المخزون"
                            else "حفظ حركة المخزون"
                        )
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
                color = if (it.startsWith("تم")) SoftGreen else SoftRed,
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
                color = SoftRed,
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
                colors = CardDefaults.cardColors(containerColor = CardSurface)
            ) {
                Text("لا توجد منتجات مسجلة بعد. أضف أول منتج من النموذج أعلاه.", modifier = Modifier.padding(18.dp), color = MutedText)
            }
        } else if (visibleProducts.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface)
            ) {
                Text("لا توجد منتجات مطابقة لمعايير البحث أو التصفية.", modifier = Modifier.padding(18.dp), color = MutedText)
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
        colors = CardDefaults.cardColors(containerColor = CardSurface)
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
                Text("الرمز: ${product.sku}", color = MutedText, fontSize = 12.sp)
            }
            if (!categoryName.isNullOrBlank()) {
                Text("التصنيف: " + categoryName, color = MutedText, fontSize = 12.sp)
            }
            if (isLowStock) {
                Text("تنبيه: المخزون منخفض", color = SoftRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("الرصيد: ${product.stockQuantity} ${product.baseUnit}", color = BrandBlue, fontSize = 13.sp)
                Text("سعر البيع: ${product.salePrice} ${product.priceCurrencyCode}", color = SoftGreen, fontSize = 13.sp)
            }
            Text("الكلفة: ${product.purchaseCost} ${product.priceCurrencyCode}", color = MutedText, fontSize = 12.sp)
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
private fun DailySalesScreen(
    onBack: () -> Unit,
    onOpenFullInvoice: () -> Unit,
    onUnsavedChangesChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val database = remember(context) { AccountingDatabase.getInstance(context) }
    val repository = remember(database) { InvoiceRepository(database) }
    val invoices by database.invoiceDao().observeAll().collectAsState(initial = emptyList())
    val invoiceLines by database.invoiceDao().observeAllLines().collectAsState(initial = emptyList())
    val products by database.productDao().observeActiveProducts().collectAsState(initial = emptyList())
    val categories by database.categoryDao().observeAll().collectAsState(initial = emptyList())
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
    var productSearchQuery by remember { mutableStateOf("") }
    var selectedProductCategoryId by remember { mutableStateOf<String?>(null) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
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
    val filteredProducts = remember(products, productSearchQuery, selectedProductCategoryId) {
        products.filter { product ->
            ProductSearchRules.matches(
                name = product.name,
                sku = product.sku,
                productCategoryId = product.categoryId,
                query = productSearchQuery,
                selectedCategoryId = selectedProductCategoryId
            )
        }
    }
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
    val hasUnsavedDailySaleChanges = hasUnsavedDailySaleDraft(
        DailySaleDraftState(
            hasSelectedProduct = selectedProductId != null,
            hasSelectedParty = selectedPartyId != null,
            quantity = quantity,
            salePrice = salePrice,
            lineDiscount = lineDiscount,
            paidAmountTouched = paidAmountTouched,
            paidAmount = paidAmount,
            note = note
        )
    )
    LaunchedEffect(hasUnsavedDailySaleChanges) {
        onUnsavedChangesChanged(hasUnsavedDailySaleChanges)
    }

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
            colors = CardDefaults.cardColors(containerColor = CardSurface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text("إضافة مبيعة", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                OutlinedTextField(
                    value = productSearchQuery,
                    onValueChange = { productSearchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("البحث باسم المادة أو رمزها") },
                    singleLine = true
                )
                Box {
                    Button(
                        onClick = { categoryMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(categories.firstOrNull { it.id == selectedProductCategoryId }?.name ?: "كل التصنيفات")
                    }
                    DropdownMenu(
                        expanded = categoryMenuExpanded,
                        onDismissRequest = { categoryMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("كل التصنيفات") },
                            onClick = { selectedProductCategoryId = null; categoryMenuExpanded = false }
                        )
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = { selectedProductCategoryId = category.id; categoryMenuExpanded = false }
                            )
                        }
                    }
                }
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
                        filteredProducts.forEach { product ->
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
                if (filteredProducts.isEmpty()) {
                    Text(
                        if (products.isEmpty()) "لا توجد مواد مسجلة." else "لا توجد مواد تطابق البحث أو التصنيف.",
                        color = MutedText,
                        fontSize = 12.sp
                    )
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
                        color = MutedText,
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
                        color = if (previewProfit >= BigDecimal.ZERO) SoftGreen else SoftRed,
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
                        color = MutedText,
                        fontSize = 12.sp
                    )
                }
                message?.let {
                    Text(
                        it,
                        color = if (it.startsWith("تم")) SoftGreen else SoftRed,
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
                colors = CardDefaults.cardColors(containerColor = CardSurface)
            ) {
                Text(
                    "لا توجد مبيعات مرحلة في هذا اليوم.",
                    modifier = Modifier.padding(18.dp),
                    color = MutedText
                )
            }
        } else {
            rows.forEach { row ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(row.productName, color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            "الكمية: " + row.quantity + " " + row.unitName +
                                "  |  سعر المبيع: " + row.unitPrice + " " + row.currencyCode,
                            color = MutedText,
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
                            color = if (row.profit >= BigDecimal.ZERO) SoftGreen else SoftRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            "رقم الفاتورة: " + row.invoiceNumber,
                            color = MutedText,
                            fontSize = 11.sp
                        )
                        Text(
                            "الملاحظات: " + row.note.ifBlank { "لا توجد ملاحظات" },
                            color = MutedText,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
        Text(
            "كل مبيعة تُحفظ كفاتورة بيع عادية من سطر واحد؛ لذلك تُحدّث المخزون وكشف الحساب والصندوق والتقارير من مصدر واحد دون تكرار.",
            color = MutedText,
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
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(title, color = MutedText, fontSize = 12.sp)
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
        colors = CardDefaults.cardColors(containerColor = CardSurface)
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
