package com.goodgirl.accounting.domain

import java.math.BigDecimal

data class PointOfSaleCartLineInput(
    val quantity: String,
    val unitPrice: String,
    val stockQuantity: String
)

object PointOfSaleCartRules {
    fun validateCart(lines: List<PointOfSaleCartLineInput>) {
        require(lines.isNotEmpty()) { "أضف مادة واحدة على الأقل إلى دفتر البيع" }
        lines.forEachIndexed { index, line ->
            val lineNumber = index + 1
            val quantity = line.quantity.trim().toBigDecimalOrNull()
                ?: throw IllegalArgumentException("أدخل كمية رقمية صحيحة في السطر $lineNumber")
            require(quantity > BigDecimal.ZERO) {
                "كمية السطر $lineNumber يجب أن تكون أكبر من صفر"
            }

            val stock = line.stockQuantity.trim().toBigDecimalOrNull()
                ?: throw IllegalArgumentException("تعذر قراءة رصيد المخزون في السطر $lineNumber")
            require(stock >= BigDecimal.ZERO && quantity <= stock) {
                "كمية السطر $lineNumber تتجاوز الكمية المتاحة في المخزون"
            }

            val unitPrice = line.unitPrice.trim().toBigDecimalOrNull()
                ?: throw IllegalArgumentException("أدخل سعر وحدة رقمياً صحيحاً في السطر $lineNumber")
            require(unitPrice >= BigDecimal.ZERO) {
                "سعر الوحدة في السطر $lineNumber لا يمكن أن يكون سالباً"
            }
        }
    }

    fun nextScannedQuantity(currentQuantity: String, stockQuantity: String): String {
        val current = currentQuantity.toBigDecimal()
        val available = stockQuantity.toBigDecimal()
        require(current >= BigDecimal.ZERO && available >= BigDecimal.ZERO) {
            "كمية المخزون أو السلة غير صالحة"
        }
        val next = current.add(BigDecimal.ONE)
        require(next <= available) { "الكمية المتاحة في المخزون غير كافية" }
        return next.stripTrailingZeros().toPlainString()
    }

    fun validatePayment(total: String, paid: String, hasCustomer: Boolean) {
        val cleanTotal = total.toBigDecimal()
        val cleanPaid = paid.toBigDecimal()
        require(cleanTotal > BigDecimal.ZERO) { "إجمالي الفاتورة يجب أن يكون أكبر من صفر" }
        require(cleanPaid >= BigDecimal.ZERO) { "المبلغ المقبوض لا يمكن أن يكون سالباً" }
        require(cleanPaid <= cleanTotal) { "المبلغ المقبوض لا يمكن أن يتجاوز إجمالي الفاتورة" }
        require(cleanPaid.compareTo(cleanTotal) == 0 || hasCustomer) {
            "اختر حساب العميل عند تسجيل مبلغ متبقٍ على الحساب"
        }
    }
}
