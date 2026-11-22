package com.goodgirl.accounting

import android.os.Bundle
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
import com.goodgirl.accounting.data.InvoiceLineDraft
import com.goodgirl.accounting.data.InvoiceRepository
import com.goodgirl.accounting.data.PartyRepository
import com.goodgirl.accounting.data.ProductRepository
import com.goodgirl.accounting.data.StockRepository
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.PartyEntity
import com.goodgirl.accounting.data.local.ProductEntity
import com.goodgirl.accounting.domain.InvoicePostingRules
import com.goodgirl.accounting.domain.PartyBalanceCalculator
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

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
                "sales" -> InvoiceManagementScreen(kind = "SALE", onBack = { activePage = "dashboard" })
                "purchases" -> InvoiceManagementScreen(kind = "PURCHASE", onBack = { activePage = "dashboard" })
                "products" -> ProductManagementScreen(onBack = { activePage = "dashboard" })
                "parties" -> PartyManagementScreen(
                    onBack = { activePage = "dashboard" },
                    onOpenStatement = { partyId ->
                        statementPartyId = partyId
                        activePage = "statement"
                    }
                )
                "statement" -> PartyStatementScreen(
                    partyId = statementPartyId.orEmpty(),
                    onBack = { activePage = "parties" }
                )
                else -> DashboardScreen(
                    onOpenSales = { activePage = "sales" },
                    onOpenPurchases = { activePage = "purchases" },
                    onOpenProducts = { activePage = "products" },
                    onOpenParties = { activePage = "parties" }
                )
            }
        }
    }
}

@Composable
private fun DashboardScreen(
    onOpenSales: () -> Unit,
    onOpenPurchases: () -> Unit,
    onOpenProducts: () -> Unit,
    onOpenParties: () -> Unit
) {
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

        Text("لوحة التحكم", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SummaryCard("مبيعات اليوم", "0", "ل.س", BrandBlue, Modifier.weight(1f))
            SummaryCard("مقبوضات اليوم", "0", "ل.س", SoftGreen, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SummaryCard("ديون مستحقة", "0", "ل.س", SoftRed, Modifier.weight(1f))
            SummaryCard("عدد الفواتير", "0", "فاتورة", SoftPurple, Modifier.weight(1f))
        }

        Text("الأقسام الرئيسية", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
        val sections = listOf(
            "المبيعات والفواتير",
            "المشتريات",
            "المنتجات والمخزون",
            "العملاء والموردون",
            "الحسابات والديون",
            "الصندوق والمصروفات",
            "التقارير والأرباح",
            "العملات وأسعار الصرف"
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
private fun InvoiceManagementScreen(kind: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val database = remember(context) { AccountingDatabase.getInstance(context) }
    val repository = remember(database) { InvoiceRepository(database) }
    val products by database.productDao().observeActiveProducts().collectAsState(initial = emptyList())
    val partyKind = if (kind == "SALE") "CUSTOMER" else "SUPPLIER"
    val parties by database.partyDao().observeByKind(partyKind).collectAsState(initial = emptyList())
    val invoices by repository.observeInvoices().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val draftLines = remember(kind) { mutableStateListOf<InvoiceLineDraft>() }

    var selectedProductId by remember { mutableStateOf<String?>(null) }
    var productMenuExpanded by remember { mutableStateOf(false) }
    var selectedPartyId by remember { mutableStateOf<String?>(null) }
    var partyMenuExpanded by remember { mutableStateOf(false) }
    var quantity by remember { mutableStateOf("1") }
    var unitPrice by remember { mutableStateOf("0") }
    var lineDiscount by remember { mutableStateOf("0") }
    var invoiceDiscount by remember { mutableStateOf("0") }
    var paidAmount by remember { mutableStateOf("0") }
    var currencyCode by remember { mutableStateOf("SYP") }
    var exchangeRate by remember { mutableStateOf("1") }
    var note by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var paymentInvoiceId by remember { mutableStateOf<String?>(null) }
    var paymentAmount by remember { mutableStateOf("") }
    var paymentNote by remember { mutableStateOf("") }
    var isSavingPayment by remember { mutableStateOf(false) }
    var paymentMessage by remember { mutableStateOf<String?>(null) }

    val selectedProduct = products.firstOrNull { it.id == selectedProductId }
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
                    TextButton(onClick = { currencyCode = "SYP"; exchangeRate = "1" }) { Text(if (currencyCode == "SYP") "✓ الليرة السورية" else "الليرة السورية") }
                    TextButton(onClick = { currencyCode = "USD"; if (exchangeRate == "1") exchangeRate = "" }) { Text(if (currencyCode == "USD") "✓ الدولار الأمريكي" else "الدولار الأمريكي") }
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
                    Button(onClick = { productMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedProduct?.name ?: "اختيار المادة")
                    }
                    DropdownMenu(expanded = productMenuExpanded, onDismissRequest = { productMenuExpanded = false }) {
                        products.forEach { product ->
                            DropdownMenuItem(
                                text = { Text(product.name + " — المتوفر: " + product.stockQuantity + " " + product.baseUnit) },
                                onClick = {
                                    selectedProductId = product.id
                                    unitPrice = if (kind == "SALE") product.salePrice else product.purchaseCost
                                    productMenuExpanded = false
                                }
                            )
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
                                    discount = cleanDiscount
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
                            Text(line.quantity + " × " + line.unitPrice + " = " + net + " " + currencyCode, color = Color(0xFF64748B), fontSize = 12.sp)
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
        paymentMessage?.let {
            Text(it, color = if (it.startsWith("تم")) Color(0xFF287A4B) else Color(0xFFB42332), fontSize = 13.sp)
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
                        TextButton(onClick = { openingCurrency = "USD"; openingExchangeRate = "" }) {
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
private fun ProductManagementScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val database = remember(context) { AccountingDatabase.getInstance(context) }
    val repository = remember(database) { ProductRepository(database) }
    val stockRepository = remember(database) { StockRepository(database) }
    val products by repository.observeProducts().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var sku by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("قطعة") }
    var purchaseCost by remember { mutableStateOf("0") }
    var salePrice by remember { mutableStateOf("0") }
    var openingStock by remember { mutableStateOf("0") }
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
        name = ""
        sku = ""
        unit = "قطعة"
        purchaseCost = "0"
        salePrice = "0"
        openingStock = "0"
        note = ""
    }

    fun startEditing(product: ProductEntity) {
        adjustingProductId = null
        stockMessage = null
        editingId = product.id
        name = product.name
        sku = product.sku
        unit = product.baseUnit
        purchaseCost = product.purchaseCost
        salePrice = product.salePrice
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
                OutlinedTextField(unit, { unit = it }, modifier = Modifier.fillMaxWidth(), label = { Text("وحدة القياس") }, singleLine = true)
                OutlinedTextField(purchaseCost, { purchaseCost = it }, modifier = Modifier.fillMaxWidth(), label = { Text("كلفة الوحدة") }, singleLine = true)
                OutlinedTextField(salePrice, { salePrice = it }, modifier = Modifier.fillMaxWidth(), label = { Text("سعر البيع") }, singleLine = true)
                if (editingId == null) {
                    OutlinedTextField(openingStock, { openingStock = it }, modifier = Modifier.fillMaxWidth(), label = { Text("الرصيد الافتتاحي") }, singleLine = true)
                } else {
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
                                        note = note
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
                                        note = note
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

        Text("قائمة المنتجات (${products.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
        if (products.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Text("لا توجد منتجات مسجلة بعد. أضف أول منتج من النموذج أعلاه.", modifier = Modifier.padding(18.dp), color = Color(0xFF64748B))
            }
        } else {
            products.forEach { product ->
                ProductListItem(
                    product,
                    onEdit = { startEditing(product) },
                    onStockAdjustment = { startStockAdjustment(product) }
                )
            }
        }
    }
}

@Composable
private fun ProductListItem(
    product: ProductEntity,
    onEdit: () -> Unit,
    onStockAdjustment: () -> Unit
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
                }
            }
            if (product.sku.isNotBlank()) {
                Text("الرمز: ${product.sku}", color = Color(0xFF64748B), fontSize = 12.sp)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("الرصيد: ${product.stockQuantity} ${product.baseUnit}", color = BrandBlue, fontSize = 13.sp)
                Text("سعر البيع: ${product.salePrice}", color = Color(0xFF287A4B), fontSize = 13.sp)
            }
            Text("الكلفة: ${product.purchaseCost}", color = Color(0xFF64748B), fontSize = 12.sp)
        }
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
            Text(value, color = Ink, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text(unit, color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SectionRow(number: Int, title: String, onClick: (() -> Unit)? = null) {
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
            Text(number.toString(), color = BrandBlue, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (onClick != null) {
                Spacer(Modifier.weight(1f))
                Text("فتح", color = BrandBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
