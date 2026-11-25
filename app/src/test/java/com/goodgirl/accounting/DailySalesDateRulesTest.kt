package com.goodgirl.accounting

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailySalesDateRulesTest {

    @Test
    fun issuedAtUsesSelectedDateAndCurrentClockTime() {
        val selectedDate = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.NOVEMBER, 20, 0, 0, 0)
        }
        val currentClock = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.NOVEMBER, 25, 17, 43, 12)
            set(Calendar.MILLISECOND, 345)
        }

        val actual = Calendar.getInstance().apply {
            timeInMillis = dailySalesIssuedAt(
                selectedDayStartMillis = selectedDate.timeInMillis,
                currentTimeMillis = currentClock.timeInMillis
            )
        }

        assertEquals(2026, actual.get(Calendar.YEAR))
        assertEquals(Calendar.NOVEMBER, actual.get(Calendar.MONTH))
        assertEquals(20, actual.get(Calendar.DAY_OF_MONTH))
        assertEquals(17, actual.get(Calendar.HOUR_OF_DAY))
        assertEquals(43, actual.get(Calendar.MINUTE))
        assertEquals(12, actual.get(Calendar.SECOND))
        assertEquals(345, actual.get(Calendar.MILLISECOND))
    }

    @Test
    fun nextDayNavigationStopsAtToday() {
        assertTrue(canAdvanceDailySalesDay(100L, 200L))
        assertFalse(canAdvanceDailySalesDay(200L, 200L))
        assertFalse(canAdvanceDailySalesDay(300L, 200L))
    }
}
