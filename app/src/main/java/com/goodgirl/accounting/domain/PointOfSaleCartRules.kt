package com.goodgirl.accounting.domain

import java.math.BigDecimal

object PointOfSaleCartRules {
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
