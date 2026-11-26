package com.goodgirl.accounting

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.goodgirl.accounting.data.InvoiceLineDraft
import com.goodgirl.accounting.data.InvoiceRepository
import com.goodgirl.accounting.data.ProductBarcodeRepository
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.ProductEntity
import com.goodgirl.accounting.domain.PointOfSaleCartRules
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.UUID

private data class PointOfSaleLine(
    val productId: String,
    val quantity: String,
    val unitPrice: String
)

@Composable
fun PointOfSaleScreen(
    onBack: () -> Unit,
    onUnsavedChangesChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val database = remember(context) { AccountingDatabase.getInstance(context) }
    val invoiceRepository = remember(database) { InvoiceRepository(database) }
    val barcodeRepository = remember(database) { ProductBarcodeRepository(database) }
    val products by database.productDao().observeActiveProducts().collectAsState(initial = emptyList())
    val customers by database.partyDao().observeByKind("CUSTOMER").collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val barcodeFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val savedRate = remember(context) {
        context.getSharedPreferences("good_girl_settings", android.content.Context.MODE_PRIVATE)
            .getString("usd_syp_rate", "1") ?: "1"
    }
    val cart = remember { mutableStateListOf<PointOfSaleLine>() }
    var currencyCode by remember { mutableStateOf("SYP") }
    var exchangeRate by remember { mutableStateOf("1") }
    var barcodeInput by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCustomerId by remember { mutableStateOf<String?>(null) }
    var customerMenuExpanded by remember { mutableStateOf(false) }
    var paidAmount by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var isLookingUpBarcode by remember { mutableStateOf(false) }
    var isPosting by remember { mutableStateOf(false) }
    var showCameraScanner by remember { mutableStateOf(false) }

    LaunchedEffect(cart.toList()) {
        onUnsavedChangesChanged(cart.isNotEmpty())
    }
    LaunchedEffect(Unit) {
        barcodeFocusRequester.requestFocus()
        keyboardController?.hide()
    }

    val total = cart.fold(BigDecimal.ZERO) { sum, line ->
        val quantity = line.quantity.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val price = line.unitPrice.toBigDecimalOrNull() ?: BigDecimal.ZERO
        sum.add(quantity.multiply(price))
    }
    val totalText = total.stripTrailingZeros().toPlainString()
    val selectedCustomer = customers.firstOrNull { it.id == selectedCustomerId }
    val visibleProducts = products.filter { product ->
        searchQuery.isBlank() ||
            product.name.contains(searchQuery.trim(), ignoreCase = true) ||
            product.sku.contains(searchQuery.trim(), ignoreCase = true)
    }.take(6)

    fun addProduct(product: ProductEntity) {
        if (!product.isActive) {
            message = "هذه المادة غير فعالة ولا يمكن بيعها"
            return
        }
        if (cart.isEmpty()) {
            currencyCode = product.priceCurrencyCode
            exchangeRate = if (product.priceCurrencyCode == "USD") savedRate else "1"
        } else if (product.priceCurrencyCode != currencyCode) {
            message = "عملة هذه المادة مختلفة عن عملة السلة. أنهِ الفاتورة الحالية أولاً."
            return
        }

        val index = cart.indexOfFirst { it.productId == product.id }
        val currentQuantity = cart.getOrNull(index)?.quantity ?: "0"
        val nextQuantity = try {
            PointOfSaleCartRules.nextScannedQuantity(
                currentQuantity = currentQuantity,
                stockQuantity = product.stockQuantity
            )
        } catch (error: Exception) {
            message = error.message ?: "الكمية المتاحة غير كافية"
            return
        }

        if (index >= 0) {
            cart[index] = cart[index].copy(quantity = nextQuantity)
        } else {
            cart.add(
                PointOfSaleLine(
                    productId = product.id,
                    quantity = nextQuantity,
                    unitPrice = product.salePrice
                )
            )
        }
        message = "تمت إضافة ${product.name} إلى السلة"
        searchQuery = ""
    }

    fun scanBarcode(value: String) {
        val code = value.trim()
        if (code.isEmpty() || isLookingUpBarcode) return
        isLookingUpBarcode = true
        barcodeInput = ""
        scope.launch {
            try {
                val product = barcodeRepository.findProductByBarcode(code)
                    ?.takeIf { it.isActive }
                    ?: throw IllegalArgumentException(
                        "الباركود غير مسجل. ابحث عن المادة يدوياً أو اربط الرمز بها من إدارة المنتجات."
                    )
                addProduct(product)
            } catch (error: Exception) {
                message = error.message ?: "تعذرت قراءة الباركود"
            } finally {
                isLookingUpBarcode = false
                barcodeInput = ""
                runCatching { barcodeFocusRequester.requestFocus() }
                keyboardController?.hide()
            }
        }
    }

    fun updateQuantity(index: Int, delta: Int) {
        val line = cart.getOrNull(index) ?: return
        val product = products.firstOrNull { it.id == line.productId } ?: return
        val current = line.quantity.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val next = current.add(BigDecimal.valueOf(delta.toLong()))
        if (next <= BigDecimal.ZERO) {
            cart.removeAt(index)
            return
        }
        if (next > (product.stockQuantity.toBigDecimalOrNull() ?: BigDecimal.ZERO)) {
            message = "لا يمكن تجاوز الكمية المتاحة في المخزون"
            return
        }
        cart[index] = line.copy(quantity = next.stripTrailingZeros().toPlainString())
    }

    fun postSale() {
        if (cart.isEmpty() || isPosting) return
        val amountPaid = paidAmount.trim().ifEmpty { totalText }
        try {
            PointOfSaleCartRules.validatePayment(
                total = totalText,
                paid = amountPaid,
                hasCustomer = selectedCustomerId != null
            )
        } catch (error: Exception) {
            message = error.message ?: "تحقق من المبلغ المدفوع وحساب العميل"
            return
        }

        val invoiceLines = cart.map { line ->
            InvoiceLineDraft(
                productId = line.productId,
                quantity = line.quantity,
                unitPrice = line.unitPrice.trim(),
                discount = "0",
                unitName = products.firstOrNull { it.id == line.productId }?.baseUnit
            )
        }
        isPosting = true
        scope.launch {
            try {
                val invoiceNumber = "POS-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(6).uppercase()}"
                val result = invoiceRepository.postInvoice(
                    invoiceNumber = invoiceNumber,
                    kind = "SALE",
                    partyId = selectedCustomerId,
                    currencyCode = currencyCode,
                    exchangeRateSnapshot = exchangeRate,
                    lines = invoiceLines,
                    paidAmount = amountPaid,
                    issuedAt = System.currentTimeMillis(),
                    note = "فاتورة نقطة البيع"
                )
                cart.clear()
                paidAmount = ""
                searchQuery = ""
                barcodeInput = ""
                message = "تم تسجيل الفاتورة ${result.invoiceNumber} بنجاح. الإجمالي: ${result.total} ${if (currencyCode == "USD") "دولار" else "ل.س"}"
            } catch (error: Exception) {
                message = error.message ?: "تعذر ترحيل فاتورة نقطة البيع"
            } finally {
                isPosting = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onBack) { Text("رجوع") }
            Text(
                "نقطة البيع",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(onClick = { cart.clear(); paidAmount = ""; message = null }) {
                Text("تفريغ السلة", color = MaterialTheme.colorScheme.error)
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("مسح الباركود", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(
                    value = barcodeInput,
                    onValueChange = { barcodeInput = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(barcodeFocusRequester).onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                            scanBarcode(barcodeInput)
                            true
                        } else false
                    },
                    label = { Text("امسح الرمز أو أدخله ثم اضغط إدخال") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { scanBarcode(barcodeInput) })
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { scanBarcode(barcodeInput) }, enabled = barcodeInput.isNotBlank() && !isLookingUpBarcode) {
                        Text("إضافة الباركود")
                    }
                    TextButton(onClick = { showCameraScanner = true }) { Text("فتح كاميرا المسح") }
                }
                Text(
                    "يمكن استخدام قارئ USB أو Bluetooth الذي يكتب الرمز ثم يرسل زر الإدخال، أو كاميرا الهاتف.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("البحث اليدوي عن مادة", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("اسم المادة أو رمزها") },
                    singleLine = true
                )
                if (searchQuery.isNotBlank()) {
                    visibleProducts.forEach { product ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { addProduct(product) }.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(product.name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    "المتوفر: ${product.stockQuantity} ${product.baseUnit}  •  سعر البيع: ${product.salePrice} ${if (product.priceCurrencyCode == "USD") "دولار" else "ل.س"}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                            TextButton(onClick = { addProduct(product) }) { Text("إضافة") }
                        }
                    }
                    if (visibleProducts.isEmpty()) {
                        Text("لا توجد مادة مطابقة. تحقق من الاسم أو الرمز.", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        Text("سلة المبيعات", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        if (cart.isEmpty()) {
            Text("السلة فارغة. امسح باركود مادة أو ابحث عنها لإضافتها.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            cart.forEachIndexed { index, line ->
                val product = products.firstOrNull { it.id == line.productId }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(product?.name ?: "مادة غير متاحة", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            TextButton(onClick = { cart.removeAt(index) }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                        }
                        Text(
                            "المخزون المتاح: ${product?.stockQuantity ?: "0"} ${product?.baseUnit.orEmpty()}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { updateQuantity(index, -1) }) { Text("−") }
                            Text(line.quantity, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { updateQuantity(index, 1) }) { Text("+") }
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(
                                value = line.unitPrice,
                                onValueChange = { value ->
                                    cart[index] = line.copy(unitPrice = value)
                                },
                                modifier = Modifier.weight(1f),
                                label = { Text("سعر الوحدة") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("الحساب والتحصيل", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("إجمالي الفاتورة: $totalText ${if (currencyCode == "USD") "دولار" else "ل.س"}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Box {
                    OutlinedTextField(
                        value = selectedCustomer?.name ?: "",
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth().clickable { customerMenuExpanded = true },
                        readOnly = true,
                        label = { Text("حساب العميل (اختياري عند الدفع الكامل)") }
                    )
                    DropdownMenu(expanded = customerMenuExpanded, onDismissRequest = { customerMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text("بيع نقدي بدون حساب عميل") }, onClick = {
                            selectedCustomerId = null
                            customerMenuExpanded = false
                        })
                        customers.forEach { customer ->
                            DropdownMenuItem(text = { Text(customer.name) }, onClick = {
                                selectedCustomerId = customer.id
                                customerMenuExpanded = false
                            })
                        }
                    }
                }
                OutlinedTextField(
                    value = paidAmount,
                    onValueChange = { paidAmount = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("المبلغ المقبوض (اتركه فارغاً للدفع الكامل)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Text(
                    "عملة الفاتورة: ${if (currencyCode == "USD") "الدولار" else "الليرة السورية"}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }

        message?.let {
            Text(
                it,
                color = if (it.startsWith("تم")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                fontSize = 13.sp
            )
        }
        Button(
            onClick = { postSale() },
            enabled = cart.isNotEmpty() && !isPosting,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text(if (isPosting) "جارٍ ترحيل الفاتورة..." else "تأكيد البيع وترحيل الفاتورة")
        }
        Text(
            "عند التأكيد تُسجّل المبيعة مرة واحدة وتُحدّث حركة المخزون والصندوق أو ذمم العميل والتقارير تلقائياً.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            fontSize = 12.sp
        )
    }

    if (showCameraScanner) {
        Dialog(
            onDismissRequest = { showCameraScanner = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                com.goodgirl.accounting.ui.BarcodeScannerScreen(
                    onBarcodeDetected = { value, _ ->
                        showCameraScanner = false
                        scanBarcode(value)
                    },
                    onClose = { showCameraScanner = false }
                )
            }
        }
    }
}
