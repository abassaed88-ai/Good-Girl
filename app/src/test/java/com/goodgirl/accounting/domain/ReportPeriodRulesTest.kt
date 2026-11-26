package com.goodgirl.accounting.domain

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportPeriodRulesTest {
    private fun reference(year: Int, month: Int, day: Int): Calendar =
        Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US).apply {
            firstDayOfWeek = Calendar.MONDAY
            set(year, month, day, 16, 27, 42)
            set(Calendar.MILLISECOND, 321)
        }

    private fun expected(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US).apply {
            set(year, month, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun todayStartsAtLocalMidnightWithoutChangingReferenceCalendar() {
        val input = reference(2026, Calendar.NOVEMBER, 26)
        val originalTime = input.timeInMillis

        assertEquals(
            expected(2026, Calendar.NOVEMBER, 26),
            ReportPeriodRules.startInclusive("TODAY", input)
        )
        assertEquals(originalTime, input.timeInMillis)
    }

    @Test
    fun weekStartsOnTheConfiguredFirstDayAndCanCrossIntoPreviousYear() {
        val mondayWeek = reference(2026, Calendar.JANUARY, 1)

        assertEquals(
            expected(2025, Calendar.DECEMBER, 29),
            ReportPeriodRules.startInclusive("WEEK", mondayWeek)
        )
    }

    @Test
    fun monthStartsOnTheFirstDayAtMidnight() {
        assertEquals(
            expected(2026, Calendar.NOVEMBER, 1),
            ReportPeriodRules.startInclusive("MONTH", reference(2026, Calendar.NOVEMBER, 26))
        )
    }

    @Test
    fun yearStartsOnJanuaryFirstAtMidnight() {
        assertEquals(
            expected(2026, Calendar.JANUARY, 1),
            ReportPeriodRules.startInclusive("YEAR", reference(2026, Calendar.NOVEMBER, 26))
        )
    }

    @Test
    fun allTimeStartsAtTheBeginningOfSupportedHistory() {
        assertEquals(0L, ReportPeriodRules.startInclusive("ALL", reference(2026, Calendar.NOVEMBER, 26)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownPeriod() {
        ReportPeriodRules.startInclusive("FORTNIGHT", reference(2026, Calendar.NOVEMBER, 26))
    }
}
