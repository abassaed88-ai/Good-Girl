package com.goodgirl.accounting.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Allocates a document-level discount proportionally to each line's net value.
 * The final positive line receives the rounding remainder so allocations add
 * up to the exact document discount.
 */
object InvoiceDiscountAllocator {
    fun allocate(lineNetAmounts: List<BigDecimal>, discount: BigDecimal): List<BigDecimal> {
        require(discount >= BigDecimal.ZERO) { "خصم الفاتورة لا يمكن أن يكون سالباً" }
        require(lineNetAmounts.all { it >= BigDecimal.ZERO }) { "قيمة السطر لا يمكن أن تكون سالبة" }
        val total = lineNetAmounts.fold(BigDecimal.ZERO, BigDecimal::add)
        require(discount <= total) { "خصم الفاتورة أكبر من صافي البنود" }
        if (lineNetAmounts.isEmpty()) return emptyList()
        if (discount.compareTo(BigDecimal.ZERO) == 0) {
            return List(lineNetAmounts.size) { BigDecimal.ZERO }
        }
        require(total > BigDecimal.ZERO) { "لا يمكن توزيع خصم على بنود قيمتها صفر" }

        val positiveIndices = lineNetAmounts.indices.filter { lineNetAmounts[it] > BigDecimal.ZERO }
        val result = MutableList(lineNetAmounts.size) { BigDecimal.ZERO }
        var allocated = BigDecimal.ZERO
        positiveIndices.forEachIndexed { position, index ->
            val share = if (position == positiveIndices.lastIndex) {
                discount.subtract(allocated)
            } else {
                discount.multiply(lineNetAmounts[index])
                    .divide(total, 8, RoundingMode.HALF_UP)
            }
            result[index] = share
            allocated = allocated.add(share)
        }
        return result
    }
}
