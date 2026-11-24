package com.goodgirl.accounting.domain

import java.math.BigDecimal
import java.math.RoundingMode

data class HistoricalStockCostCurrentState(
    val productId: String,
    val quantity: String,
    val averageCost: String,
    val currencyCode: String
)

/**
 * Reconstructs a product's inventory state at the accounting-opening boundary
 * by reversing every later recorded movement. A failed reconstruction must be
 * handled by the repository with a later snapshot baseline, never by guessing.
 */
object HistoricalStockCostBaselineReconstructor {
    private const val SCALE = 8
    private const val TOLERANCE_SCALE = 8
    private val ZERO = BigDecimal.ZERO
    private val supportedCurrencies = setOf("SYP", "USD")
    private val supportedMovementTypes = setOf(
        "OPENING_BALANCE",
        "SALE_INVOICE",
        "PURCHASE_INVOICE",
        "MANUAL_ADJUSTMENT",
        "STOCKTAKE_ADJUSTMENT",
        "MIGRATION_RECONCILIATION",
        "PRODUCT_CURRENCY_CORRECTION"
    )

    fun reconstruct(
        current: HistoricalStockCostCurrentState,
        accountingBoundaryAt: Long,
        postBoundaryEvents: List<HistoricalStockCostEvent>,
        previousCurrencyByCorrectionId: Map<String, String> = emptyMap()
    ): HistoricalStockCostBaseline {
        require(current.productId.isNotBlank()) { "معرّف المنتج مطلوب" }
        require(accountingBoundaryAt > 0L) { "تاريخ بداية المحاسبة غير صالح" }

        var quantity = nonNegative(current.quantity, "رصيد المنتج الحالي")
        var averageCost = nonNegative(current.averageCost, "متوسط كلفة المنتج الحالي")
        var currency = current.currencyCode.trim().uppercase()
        require(currency in supportedCurrencies) { "عملة المنتج الحالي غير مدعومة" }
        require(postBoundaryEvents.map { it.id }.toSet().size == postBoundaryEvents.size) {
            "معرّفات حركات المخزون يجب أن تكون فريدة"
        }

        postBoundaryEvents.forEach { event ->
            require(event.recordedAt > accountingBoundaryAt) {
                "توجد حركة مسجلة قبل بداية دفتر الأستاذ؛ لا يمكن عكسها ضمن رصيد البداية"
            }
            require(event.occurredAt >= accountingBoundaryAt) {
                "توجد حركة مسجلة بعد بداية دفتر الأستاذ بتاريخ أقدم منها؛ يلزم تصحيحها قبل إعادة بناء الرصيد"
            }
            require(event.sourceType.trim().uppercase() in supportedMovementTypes) {
                "نوع حركة المخزون غير معروف لإعادة بناء الرصيد: ${event.sourceType}"
            }
        }

        val reverseOrdered = postBoundaryEvents.sortedWith(
            compareByDescending<HistoricalStockCostEvent> { it.occurredAt }
                .thenByDescending { it.recordedAt }
                .thenByDescending { it.id }
        )

        reverseOrdered.forEach { event ->
            val type = event.sourceType.trim().uppercase()
            val eventCurrency = event.currencyCode.trim().uppercase()
            require(eventCurrency in supportedCurrencies) { "عملة حركة المخزون غير مدعومة" }
            val delta = decimal(event.quantityDelta, "كمية حركة المخزون")
            val unitCost = nonNegative(event.unitCostSnapshot, "كلفة حركة المخزون")

            if (type == "PRODUCT_CURRENCY_CORRECTION") {
                require(delta.compareTo(ZERO) == 0) {
                    "حركة تصحيح العملة لا يجوز أن تغيّر كمية المخزون"
                }
                require(currency == eventCurrency) {
                    "تسلسل تصحيح عملة المنتج غير متوافق مع العملة الحالية"
                }
                val oldCurrency = previousCurrencyByCorrectionId[event.id]
                    ?.trim()?.uppercase()
                    ?: throw IllegalArgumentException("سجل تصحيح العملة لا يحتوي على العملة السابقة")
                require(oldCurrency in supportedCurrencies && oldCurrency != eventCurrency) {
                    "العملة السابقة في سجل التصحيح غير صالحة"
                }
                if (quantity > ZERO) {
                    require(closeEnough(averageCost, unitCost)) {
                        "كلفة حركة تصحيح العملة لا تطابق القيمة الدفترية الحالية"
                    }
                } else {
                    // With zero on-hand quantity, a later manual cost edit has
                    // no inventory carrying value to reconstruct.
                    averageCost = ZERO
                }
                currency = oldCurrency
                return@forEach
            }

            if (delta.compareTo(ZERO) == 0) {
                throw IllegalArgumentException("حركة مخزون صفرية من نوع غير مدعوم لإعادة بناء الرصيد")
            }

            require(currency == eventCurrency) {
                "تسلسل عملات حركات المخزون غير متوافق أثناء إعادة بناء الرصيد"
            }

            if (delta < ZERO) {
                val outgoing = delta.abs()
                // A weighted-average stock-out does not change the remaining
                // average cost, so its saved unit cost must match this state.
                require(closeEnough(averageCost, unitCost)) {
                    "كلفة حركة الصرف لا تطابق متوسط الكلفة بعد عكس الحركات اللاحقة"
                }
                quantity = quantity.add(outgoing)
            } else {
                val beforeQuantity = quantity.subtract(delta)
                require(beforeQuantity >= ZERO) {
                    "لا يمكن عكس حركة إضافة أكبر من رصيد المخزون الناتج"
                }
                if (beforeQuantity.compareTo(ZERO) == 0) {
                    require(closeEnough(averageCost, unitCost)) {
                        "كلفة أول حركة إضافة لا تطابق كلفة الرصيد الناتج"
                    }
                    averageCost = ZERO
                } else {
                    val valueAfter = quantity.multiply(averageCost)
                    val valueAdded = delta.multiply(unitCost)
                    val valueBefore = valueAfter.subtract(valueAdded)
                    require(valueBefore >= ZERO) {
                        "إعادة بناء كلفة المخزون أنتجت قيمة سالبة"
                    }
                    averageCost = valueBefore.divide(
                        beforeQuantity,
                        SCALE,
                        RoundingMode.HALF_UP
                    )
                }
                quantity = beforeQuantity
            }
        }

        val baseline = HistoricalStockCostBaseline(
            productId = current.productId,
            quantity = quantity.stripTrailingZeros().toPlainString(),
            averageCost = averageCost.stripTrailingZeros().toPlainString(),
            currencyCode = currency,
            effectiveAt = accountingBoundaryAt,
            recordedAt = accountingBoundaryAt
        )

        val forward = HistoricalStockCostCalculator.recalculate(baseline, postBoundaryEvents)
        require(decimal(forward.endingQuantity, "الرصيد المعاد احتسابه").compareTo(
            decimal(current.quantity, "رصيد المنتج الحالي")
        ) == 0) {
            "الرصيد المعاد احتسابه لا يطابق رصيد المنتج الحالي"
        }
        require(forward.currencyCode == current.currencyCode.trim().uppercase()) {
            "عملة الرصيد المعاد احتسابه لا تطابق عملة المنتج الحالية"
        }
        if (quantity.compareTo(ZERO) > 0) {
            require(closeEnough(
                decimal(forward.endingAverageCost, "متوسط الكلفة المعاد احتسابه"),
                decimal(current.averageCost, "متوسط كلفة المنتج الحالي")
            )) {
                "متوسط الكلفة المعاد احتسابه لا يطابق كلفة المنتج الحالية"
            }
        }

        return baseline
    }

    private fun closeEnough(left: BigDecimal, right: BigDecimal): Boolean =
        left.subtract(right).abs() <= BigDecimal.ONE.movePointLeft(TOLERANCE_SCALE)

    private fun nonNegative(value: String, label: String): BigDecimal {
        val parsed = decimal(value, label)
        require(parsed >= ZERO) { "$label لا يمكن أن يكون سالباً" }
        return parsed
    }

    private fun decimal(value: String, label: String): BigDecimal =
        try {
            BigDecimal(value.trim())
        } catch (error: NumberFormatException) {
            throw IllegalArgumentException("$label غير صالحة", error)
        }
}
