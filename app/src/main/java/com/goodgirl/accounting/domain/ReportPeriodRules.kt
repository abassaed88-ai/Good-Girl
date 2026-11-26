package com.goodgirl.accounting.domain

import java.util.Calendar

/**
 * Resolves the inclusive start timestamp for a financial-report period.
 * Weekly boundaries follow the device's configured Calendar.firstDayOfWeek.
 */
object ReportPeriodRules {
    fun startInclusive(period: String, reference: Calendar): Long {
        val start = reference.clone() as Calendar
        when (period) {
            "TODAY" -> Unit
            "WEEK" -> {
                val firstDay = start.firstDayOfWeek
                val offset = (start.get(Calendar.DAY_OF_WEEK) - firstDay + 7) % 7
                start.add(Calendar.DAY_OF_MONTH, -offset)
            }
            "MONTH" -> start.set(Calendar.DAY_OF_MONTH, 1)
            "YEAR" -> {
                start.set(Calendar.MONTH, Calendar.JANUARY)
                start.set(Calendar.DAY_OF_MONTH, 1)
            }
            "ALL" -> return 0L
            else -> throw IllegalArgumentException("فترة التقرير غير مدعومة")
        }
        start.set(Calendar.HOUR_OF_DAY, 0)
        start.set(Calendar.MINUTE, 0)
        start.set(Calendar.SECOND, 0)
        start.set(Calendar.MILLISECOND, 0)
        return start.timeInMillis
    }
}
