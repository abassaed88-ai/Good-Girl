package com.goodgirl.accounting

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import com.goodgirl.accounting.data.PartyRepository
import com.goodgirl.accounting.data.ProductRepository
import com.goodgirl.accounting.data.StockRepository
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.PartyEntity
import com.goodgirl.accounting.data.local.ProductEntity
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
                "products" -> ProductManagementScreen(onBack = { activePage = "dashboard" })
                "parties" -> PartyManagementScreen(onBack = { activePage = "dashboard" })
                else -> DashboardScreen(
                    onOpenProducts = { activePage = "products" },
                    onOpenParties = { activePage = "parties" }
                )
            }
        }
    }
}

@Composable
private fun DashboardScreen(onOpenProducts: () -> Unit, onOpenParties: () -> Unit) {
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
private fun PartyManagementScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val database = remember(context) { AccountingDatabase.getInstance(context) }
    val repository = remember(database) { PartyRepository(database) }
    var selectedKind by remember { mutableStateOf("CUSTOMER") }
    val parties by repository.observeParties(selectedKind).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var editingId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    fun clearForm() {
        editingId = null
        name = ""
        phone = ""
        address = ""
        note = ""
    }

    fun loadParty(party: PartyEntity) {
        editingId = party.id
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
                                    note = note
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
