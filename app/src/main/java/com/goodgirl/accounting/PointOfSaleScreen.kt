package com.goodgirl.accounting

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.OutlinedTextField as Material3OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.goodgirl.accounting.data.InvoiceLineDraft
import com.goodgirl.accounting.data.DailySalesExpenseDraft
import com.goodgirl.accounting.data.InvoiceRepository
import com.goodgirl.accounting.data.ProductBarcodeRepository
import com.goodgirl.accounting.data.local.AccountingDatabase
import com.goodgirl.accounting.data.local.ProductEntity
import com.goodgirl.accounting.data.local.ProductUnitEntity
import com.goodgirl.accounting.domain.PointOfSaleCartLineInput
import com.goodgirl.accounting.domain.PointOfSaleCartRules
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

private data class PointOfSaleLine(
    val productId: String,
    val quantity: String,
    val unitPrice: String,
    val unitName: String,
    val unitFactor: String,
    val expenseDescription: String = "",
    val expenseAmount: String = "",
    val note: String = ""
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
    var productUnits by remember { mutableStateOf<Map<String, List<ProductUnitEntity>>>(emptyMap()) }
    var showProductPickerDialog by remember { mutableStateOf(false) }
    var unitMenuExpandedForLine by remember { mutableStateOf<Int?>(null) }
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
    var showReviewDialog by remember { mutableStateOf(false) }

    LaunchedEffect(cart.toList()) {
        onUnsavedChangesChanged(cart.isNotEmpty())
    }
    LaunchedEffect(products) {
        productUnits = database.productUnitDao().getAll().groupBy { it.productId }
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
        val existingLine = cart.getOrNull(index)
        val currentQuantity = existingLine?.quantity ?: "0"
        val selectedFactor = existingLine?.unitFactor
            ?: productUnits[product.id]?.firstOrNull { it.unitName == product.baseUnit }?.baseFactor
            ?: "1"
        val availableInSelectedUnit = runCatching {
            BigDecimal(product.stockQuantity).divide(BigDecimal(selectedFactor), 8, RoundingMode.DOWN)
                .stripTrailingZeros().toPlainString()
        }.getOrDefault("0")
        val nextQuantity = try {
            PointOfSaleCartRules.nextScannedQuantity(
                currentQuantity = currentQuantity,
                stockQuantity = availableInSelectedUnit
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
                    unitPrice = product.salePrice,
                    unitName = product.baseUnit,
                    unitFactor = productUnits[product.id]
                        ?.firstOrNull { it.unitName == product.baseUnit }?.baseFactor ?: "1"
                )
            )
        }
        message = "تمت إضافة " + product.name + " إلى دفتر المبيعات"
        searchQuery = ""
        showProductPickerDialog = false
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

    fun validateSaleBeforeReview(): Boolean {
        if (cart.isEmpty()) {
            message = "أضف مادة واحدة على الأقل قبل مراجعة البيع"
            return false
        }
        return try {
            val lineInputs = cart.map { line ->
                val product = products.firstOrNull { it.id == line.productId }
                    ?: throw IllegalArgumentException("إحدى المواد لم تعد موجودة في قائمة المواد")
                require(product.isActive) { "المادة ${product.name} غير فعالة ولا يمكن بيعها" }
                val factor = line.unitFactor.trim().toBigDecimalOrNull()
                    ?.takeIf { it > BigDecimal.ZERO }
                    ?: throw IllegalArgumentException("عامل تحويل الوحدة غير صالح في السطر " + (cart.indexOf(line) + 1))
                val availableInSelectedUnit = BigDecimal(product.stockQuantity)
                    .divide(factor, 8, RoundingMode.DOWN).stripTrailingZeros().toPlainString()
                PointOfSaleCartLineInput(
                    quantity = line.quantity,
                    unitPrice = line.unitPrice,
                    stockQuantity = availableInSelectedUnit
                )
            }
            PointOfSaleCartRules.validateCart(lineInputs)
            cart.forEachIndexed { index, line ->
                val description = line.expenseDescription.trim()
                val amount = line.expenseAmount.trim()
                require(description.isEmpty() == amount.isEmpty()) {
                    "أدخل بيان المصروف ومبلغه معاً في السطر " + (index + 1)
                }
                if (amount.isNotEmpty()) {
                    val value = amount.toBigDecimalOrNull()
                        ?: throw IllegalArgumentException("مبلغ المصروف غير صحيح في السطر " + (index + 1))
                    require(value > BigDecimal.ZERO) {
                        "مبلغ المصروف يجب أن يكون أكبر من صفر في السطر " + (index + 1)
                    }
                }
            }
            val amountPaid = paidAmount.trim().ifEmpty { totalText }
            PointOfSaleCartRules.validatePayment(
                total = totalText,
                paid = amountPaid,
                hasCustomer = selectedCustomerId != null
            )
            val rate = exchangeRate.trim().toBigDecimalOrNull()
            require(rate != null && rate > BigDecimal.ZERO) { "سعر الصرف يجب أن يكون رقماً أكبر من صفر" }
            true
        } catch (error: Exception) {
            message = error.message ?: "راجع الكميات والأسعار والمبالغ قبل متابعة البيع"
            false
        }
    }

    fun postSale() {
        if (isPosting || !validateSaleBeforeReview()) return
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
                unitName = line.unitName,
                associatedExpenses = if (line.expenseAmount.isBlank()) emptyList() else listOf(
                    DailySalesExpenseDraft(
                        id = UUID.randomUUID().toString(),
                        description = line.expenseDescription.trim(),
                        amount = line.expenseAmount.trim(),
                        currencyCode = currencyCode,
                        exchangeRateSnapshot = exchangeRate
                    )
                )
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
                    note = cart.mapIndexedNotNull { index, line ->
                        line.note.trim().takeIf { it.isNotEmpty() }?.let { "السطر " + (index + 1) + ": " + it }
                    }.joinToString(" | ").ifEmpty { "فاتورة نقطة البيع" }
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
                "المبيعات اليومية",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(onClick = { cart.clear(); paidAmount = ""; message = null }) {
                Text("تفريغ الدفتر", color = MaterialTheme.colorScheme.error)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "أدخل تفاصيل كل مبيعة ضمن صفها، ويمكن التمرير أفقياً لرؤية جميع الأعمدة.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            Button(onClick = { showProductPickerDialog = true }, enabled = !isPosting) {
                Text("إضافة سطر")
            }
        }

        val ledgerWidth = 52.dp + 220.dp + 190.dp + 150.dp + 135.dp + 160.dp + 190.dp + 200.dp
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Row(
                    modifier = Modifier.width(ledgerWidth).height(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LedgerHeaderCell("تسلسل", 52.dp)
                    LedgerHeaderCell("المادة", 220.dp)
                    LedgerHeaderCell("الكمية / الوحدة", 190.dp)
                    LedgerHeaderCell("المبيع", 150.dp)
                    LedgerHeaderCell("الأرباح", 135.dp)
                    LedgerHeaderCell("العميل (دائن / لأجل)", 160.dp)
                    LedgerHeaderCell("مصروفات", 190.dp)
                    LedgerHeaderCell("ملاحظات", 200.dp)
                }
                if (cart.isEmpty()) {
                    Box(
                        modifier = Modifier.width(ledgerWidth).height(104.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("الدفتر فارغ. اضغط «إضافة سطر» لاختيار مادة.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    cart.forEachIndexed { index, line ->
                        val product = products.firstOrNull { it.id == line.productId }
                        val factor = line.unitFactor.toBigDecimalOrNull() ?: BigDecimal.ONE
                        val unitCost = product?.purchaseCost?.toBigDecimalOrNull()
                            ?.multiply(factor)?.stripTrailingZeros()?.toPlainString()
                        val estimatedGrossProfit = PointOfSaleCartRules.estimateGrossProfit(
                            quantity = line.quantity,
                            unitPrice = line.unitPrice,
                            unitCost = unitCost
                        )
                        val expense = line.expenseAmount.toBigDecimalOrNull() ?: BigDecimal.ZERO
                        val netEstimate = estimatedGrossProfit?.subtract(expense)?.stripTrailingZeros()
                        val options = productUnits[line.productId].orEmpty()
                        Row(
                            modifier = Modifier.width(ledgerWidth).height(124.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LedgerBodyCell(52.dp) {
                                Text((index + 1).toString(), fontWeight = FontWeight.SemiBold)
                            }
                            LedgerBodyCell(220.dp) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(product?.name ?: "مادة غير متاحة", fontWeight = FontWeight.SemiBold)
                                    Text(product?.sku?.takeIf { it.isNotBlank() } ?: "—", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                    TextButton(onClick = { cart.removeAt(index) }, enabled = !isPosting) {
                                        Text("حذف", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            LedgerBodyCell(190.dp) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Material3OutlinedTextField(
                                        value = line.quantity,
                                        onValueChange = { value -> cart[index] = line.copy(quantity = value) },
                                        modifier = Modifier.fillMaxWidth().testTag("pos-quantity-" + index),
                                        label = { Text("الكمية") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                    )
                                    Box {
                                        TextButton(onClick = { unitMenuExpandedForLine = index }, enabled = !isPosting) {
                                            Text(line.unitName)
                                        }
                                        DropdownMenu(
                                            expanded = unitMenuExpandedForLine == index,
                                            onDismissRequest = { unitMenuExpandedForLine = null }
                                        ) {
                                            options.forEach { unit ->
                                                DropdownMenuItem(
                                                    text = { Text(unit.unitName) },
                                                    onClick = {
                                                        val nextFactor = unit.baseFactor.toBigDecimalOrNull() ?: BigDecimal.ONE
                                                        val nextQuantity = line.quantity.toBigDecimalOrNull() ?: BigDecimal.ZERO
                                                        val available = product?.stockQuantity?.toBigDecimalOrNull() ?: BigDecimal.ZERO
                                                        if (nextQuantity.multiply(nextFactor) > available) {
                                                            message = "الكمية المدخلة تتجاوز المخزون بهذه الوحدة"
                                                        } else {
                                                            val basePrice = product?.salePrice?.toBigDecimalOrNull() ?: BigDecimal.ZERO
                                                            cart[index] = line.copy(
                                                                unitName = unit.unitName,
                                                                unitFactor = unit.baseFactor,
                                                                unitPrice = basePrice.multiply(nextFactor).stripTrailingZeros().toPlainString()
                                                            )
                                                        }
                                                        unitMenuExpandedForLine = null
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            LedgerBodyCell(150.dp) {
                                Material3OutlinedTextField(
                                    value = line.unitPrice,
                                    onValueChange = { value -> cart[index] = line.copy(unitPrice = value) },
                                    modifier = Modifier.fillMaxWidth().testTag("pos-sale-price-" + index),
                                    label = { Text(if (currencyCode == "USD") "دولار" else "ل.س") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                )
                            }
                            LedgerBodyCell(135.dp) {
                                Text(
                                    netEstimate?.toPlainString() ?: "—",
                                    color = if (netEstimate != null && netEstimate.signum() < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            LedgerBodyCell(160.dp) {
                                Text(
                                    selectedCustomer?.name ?: "نقدي",
                                    color = if (selectedCustomer == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            LedgerBodyCell(190.dp) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Material3OutlinedTextField(
                                        value = line.expenseDescription,
                                        onValueChange = { value -> cart[index] = line.copy(expenseDescription = value) },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("بيان المصروف") },
                                        singleLine = true
                                    )
                                    Material3OutlinedTextField(
                                        value = line.expenseAmount,
                                        onValueChange = { value -> cart[index] = line.copy(expenseAmount = value) },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("المبلغ") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                    )
                                }
                            }
                            LedgerBodyCell(200.dp) {
                                Material3OutlinedTextField(
                                    value = line.note,
                                    onValueChange = { value -> cart[index] = line.copy(note = value) },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("ملاحظات السطر") },
                                    singleLine = true
                                )
                            }
                        }
                    }
                }
            }
        }

        val expenseTotal = cart.fold(BigDecimal.ZERO) { sum, line ->
            sum.add(line.expenseAmount.toBigDecimalOrNull() ?: BigDecimal.ZERO)
        }.stripTrailingZeros().toPlainString()
        val estimatedProfitTotal = cart.fold(BigDecimal.ZERO) { sum, line ->
            val product = products.firstOrNull { it.id == line.productId }
            val factor = line.unitFactor.toBigDecimalOrNull() ?: BigDecimal.ONE
            val cost = product?.purchaseCost?.toBigDecimalOrNull()?.multiply(factor)?.toPlainString()
            val profit = PointOfSaleCartRules.estimateGrossProfit(line.quantity, line.unitPrice, cost)
            sum.add(profit ?: BigDecimal.ZERO)
        }.subtract(expenseTotal.toBigDecimal()).stripTrailingZeros().toPlainString()
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("إجماليات دفتر المبيعات", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("المبيعات: " + totalText + " " + if (currencyCode == "USD") "دولار" else "ل.س")
                    Text("الأرباح التقديرية بعد المصروفات: " + estimatedProfitTotal + " " + if (currencyCode == "USD") "دولار" else "ل.س")
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("المصروفات: " + expenseTotal + " " + if (currencyCode == "USD") "دولار" else "ل.س")
                    Text("عدد السطور: " + cart.size)
                }
                Text(
                    "عملة الدفتر الحالية: " + if (currencyCode == "USD") "الدولار الأمريكي" else "الليرة السورية",
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
                Text("التحصيل وحساب العميل", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
                    "عملة الفاتورة: " + if (currencyCode == "USD") "الدولار" else "الليرة السورية",
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
            onClick = { if (validateSaleBeforeReview()) showReviewDialog = true },
            enabled = cart.isNotEmpty() && !isPosting,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text(if (isPosting) "جارٍ ترحيل الفاتورة..." else "مراجعة الفاتورة قبل الترحيل")
        }
        Text(
            "عند التأكيد تُسجّل المبيعة مرة واحدة وتُحدّث حركة المخزون والصندوق أو ذمم العميل والتقارير تلقائياً.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            fontSize = 12.sp
        )
    }

    if (showProductPickerDialog) {
        Dialog(onDismissRequest = { showProductPickerDialog = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("اختيار مادة للدفتر", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth().testTag("pos-product-search"),
                        label = { Text("ابحث باسم المادة أو رمزها") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() })
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { showCameraScanner = true }, modifier = Modifier.weight(1f)) {
                            Text("مسح الباركود بالكاميرا")
                        }
                        TextButton(onClick = { scanBarcode(searchQuery) }, enabled = searchQuery.isNotBlank() && !isLookingUpBarcode) {
                            Text("إضافة بالباركود")
                        }
                    }
                    if (searchQuery.isNotBlank()) {
                        visibleProducts.forEach { product ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { addProduct(product) }.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(product.name, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "الرمز: " + product.sku + " • المتوفر: " + product.stockQuantity + " " + product.baseUnit,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { addProduct(product) }) { Text("اختيار") }
                            }
                            androidx.compose.material3.HorizontalDivider()
                        }
                        if (visibleProducts.isEmpty()) Text("لا توجد مادة مطابقة بالاسم أو الرمز.", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { showProductPickerDialog = false }, modifier = Modifier.align(Alignment.End)) {
                        Text("إغلاق")
                    }
                }
            }
        }
    }


    if (showReviewDialog) {
        val amountToCollect = paidAmount.trim().ifEmpty { totalText }
        val paidValue = amountToCollect.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val remainingValue = total.subtract(paidValue)
        val currencyLabel = if (currencyCode == "USD") "دولار" else "ل.س"
        Dialog(
            onDismissRequest = { if (!isPosting) showReviewDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "مراجعة البيع قبل الترحيل",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "تأكد من المواد والكميات والأسعار. لن تُسجّل أي حركة محاسبية قبل ضغط زر اعتماد البيع.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                    cart.forEachIndexed { index, line ->
                        val product = products.firstOrNull { it.id == line.productId }
                        val quantity = line.quantity.toBigDecimal()
                        val price = line.unitPrice.toBigDecimal()
                        val lineTotal = quantity.multiply(price).stripTrailingZeros().toPlainString()
                        val estimatedProfit = PointOfSaleCartRules.estimateGrossProfit(
                            quantity = line.quantity,
                            unitPrice = line.unitPrice,
                            unitCost = product?.purchaseCost
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(product?.name ?: "مادة غير متاحة", fontWeight = FontWeight.SemiBold)
                            Text(
                                "${line.quantity} × ${line.unitPrice} = $lineTotal $currencyLabel",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                            Text(
                                estimatedProfit?.let { "الربح التقديري: ${it.toPlainString()} $currencyLabel" }
                                    ?: "كلفة الشراء غير محددة",
                                color = if (estimatedProfit != null && estimatedProfit.signum() < 0) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontSize = 12.sp
                            )
                        }
                        if (index < cart.lastIndex) {
                            androidx.compose.material3.HorizontalDivider()
                        }
                    }
                    androidx.compose.material3.HorizontalDivider()
                    Text(
                        "الإجمالي: $totalText $currencyLabel",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text("العميل: ${selectedCustomer?.name ?: "بيع نقدي بدون حساب"}")
                    Text("المقبوض: $amountToCollect $currencyLabel")
                    Text("المتبقي: ${remainingValue.stripTrailingZeros().toPlainString()} $currencyLabel")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { showReviewDialog = false },
                            enabled = !isPosting,
                            modifier = Modifier.weight(1f)
                        ) { Text("عودة للتعديل") }
                        Button(
                            onClick = {
                                showReviewDialog = false
                                postSale()
                            },
                            enabled = !isPosting,
                            modifier = Modifier.weight(1f)
                        ) { Text("اعتماد وترحيل البيع") }
                    }
                }
            }
        }
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

@Composable
private fun LedgerHeaderCell(label: String, cellWidth: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier.width(cellWidth).fillMaxSize()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            textAlign = TextAlign.Center,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun LedgerBodyCell(cellWidth: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.width(cellWidth).fillMaxSize()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            .background(MaterialTheme.colorScheme.surface)
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
