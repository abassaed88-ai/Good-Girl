package com.goodgirl.accounting

import java.util.Calendar

/**
 * Applies the current clock time to the selected local calendar day.
 * The business date remains the day selected in the daily sales screen.
 */
internal fun dailySalesIssuedAt(
    selectedDayStartMillis: Long,
    currentTimeMillis: Long
): Long {
    val currentClock = Calendar.getInstance().apply {
        timeInMillis = currentTimeMillis
    }
    return Calendar.getInstance().apply {
        timeInMillis = selectedDayStartMillis
        set(Calendar.HOUR_OF_DAY, currentClock.get(Calendar.HOUR_OF_DAY))
        set(Calendar.MINUTE, currentClock.get(Calendar.MINUTE))
        set(Calendar.SECOND, currentClock.get(Calendar.SECOND))
        set(Calendar.MILLISECOND, currentClock.get(Calendar.MILLISECOND))
    }.timeInMillis
}

/** Only allow moving forward while the selected day is earlier than today. */
internal fun canAdvanceDailySalesDay(
    selectedDayStartMillis: Long,
    todayStartMillis: Long
): Boolean = selectedDayStartMillis < todayStartMillis
