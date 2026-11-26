package com.androplus.pwdmgr.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class PeriodChartsTest {

    private fun at(y: Int, m: Int, d: Int, h: Int = 12): Long =
        Calendar.getInstance().apply { clear(); set(y, m - 1, d, h, 0) }.timeInMillis

    private fun row(amount: Double, t: Long, cat: String = "Groceries", income: Boolean = false) =
        ExpenseRow("id", "x", "SMS", amount, income, t, cat, "#000000")

    // Mon 16 Nov 2026 … Sun 22 Nov 2026. 18 Nov is a Wednesday.
    private val wed = at(2026, 11, 18)

    @Test fun weekRunsMondayToSundayAndSundayBelongsToTheWeekBefore() {
        val sunday = at(2026, 11, 22)
        val (start, end) = PeriodCharts.range(ChartPeriod.WEEK, 0, sunday)
        assertEquals(at(2026, 11, 16, 0), start)
        assertEquals(at(2026, 11, 23, 0) - 1, end)
        assertEquals(PeriodCharts.range(ChartPeriod.WEEK, 0, wed), PeriodCharts.range(ChartPeriod.WEEK, 0, sunday))
        // Next Monday starts a new week.
        assertEquals(at(2026, 11, 23, 0), PeriodCharts.range(ChartPeriod.WEEK, 0, at(2026, 11, 23)).first)
    }

    @Test fun weekBucketsAreSevenDaysWithTheRightSpend() {
        val rows = listOf(
            row(100.0, at(2026, 11, 16)), row(50.0, at(2026, 11, 16, 20)),     // Mon
            row(300.0, at(2026, 11, 18)),                                        // Wed
            row(999.0, at(2026, 11, 23)),                                        // next week: not counted here
        )
        val r = PeriodCharts.report(rows, ChartPeriod.WEEK, 0, wed)
        assertEquals(listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"), r.buckets.map { it.label })
        assertEquals(listOf(150.0, 0.0, 300.0, 0.0, 0.0, 0.0, 0.0), r.buckets.map { it.spent })
        assertEquals(450.0, r.spent, 0.001)
        assertEquals("This week", r.title)
        assertEquals("16 – 22 Nov", r.range)
        assertEquals("Mon, 16 Nov", r.buckets[0].fullLabel)
    }

    @Test fun monthHasOneBucketPerDayIncludingLeapFebruary() {
        assertEquals(30, PeriodCharts.report(emptyList(), ChartPeriod.MONTH, 0, wed).buckets.size)
        assertEquals(29, PeriodCharts.report(emptyList(), ChartPeriod.MONTH, 0, at(2028, 2, 10)).buckets.size)
        assertEquals(28, PeriodCharts.report(emptyList(), ChartPeriod.MONTH, 0, at(2027, 2, 10)).buckets.size)
        val r = PeriodCharts.report(listOf(row(80.0, at(2026, 11, 30, 23))), ChartPeriod.MONTH, 0, wed)
        assertEquals(80.0, r.buckets.last().spent, 0.001)                   // last moment of the month
        assertEquals("November 2026", r.range)
        // Labels only every 5th day to stay readable.
        assertEquals(listOf("1", "5", "10", "15", "20", "25", "30"), r.buckets.map { it.label }.filter { it.isNotEmpty() })
    }

    @Test fun monthLabelsNeverCrowdTheLastDay() {
        fun labels(y: Int, m: Int) = PeriodCharts.report(emptyList(), ChartPeriod.MONTH, 0, at(y, m, 10))
            .buckets.map { it.label }.filter { it.isNotEmpty() }
        assertEquals(listOf("1", "5", "10", "15", "20", "25", "30"), labels(2026, 10))      // 31 days: no "31" beside "30"
        assertEquals(listOf("1", "5", "10", "15", "20", "25", "28"), labels(2027, 2))       // 28 days: 28 is 3 days past 25
        assertEquals(listOf("1", "5", "10", "15", "20", "25", "29"), labels(2028, 2))       // leap February
    }

    @Test fun yearHasTwelveMonthsAndKeepsSavingsIncomeAndTransfersOut() {
        val rows = listOf(
            row(1000.0, at(2026, 1, 5)),
            row(2000.0, at(2026, 3, 20)),
            row(500.0, at(2026, 3, 21), "Insurance & Savings"),                // saved, not spent
            row(9000.0, at(2026, 3, 22), "Card Bill"),                          // not counted at all
            row(85000.0, at(2026, 3, 1), "Income", income = true),
            row(70.0, at(2025, 12, 31)),                                        // last year
        )
        val r = PeriodCharts.report(rows, ChartPeriod.YEAR, 0, wed)
        assertEquals(12, r.buckets.size)
        assertEquals("Jan", r.buckets.first().label)
        assertEquals("March 2026", r.buckets[2].fullLabel)
        assertEquals(1000.0, r.buckets[0].spent, 0.001)
        assertEquals(2000.0, r.buckets[2].spent, 0.001)
        assertEquals(3000.0, r.spent, 0.001)
        assertEquals(500.0, r.saved, 0.001)
        assertEquals(85000.0, r.earned, 0.001)
        assertEquals(70.0, r.previousSpent, 0.001)
        assertEquals("Last year", r.previousLabel)
        assertEquals("This year", r.title)
        assertEquals("2026", r.range)
    }

    @Test fun navigatingBackwardsAndTitles() {
        val prev = PeriodCharts.report(emptyList(), ChartPeriod.WEEK, -1, wed)
        assertEquals("Last week", prev.title)
        assertEquals("9 – 15 Nov", prev.range)
        assertEquals("Previous week", prev.previousLabel)
        val older = PeriodCharts.report(emptyList(), ChartPeriod.MONTH, -3, wed)
        assertEquals("August 2026", older.title)
        val lastYear = PeriodCharts.report(emptyList(), ChartPeriod.YEAR, -1, wed)
        assertEquals("Last year", lastYear.title)
        assertEquals("2025", lastYear.range)
    }

    @Test fun weekAcrossMonthsAndYears() {
        // Mon 28 Dec 2026 … Sun 3 Jan 2027
        val r = PeriodCharts.report(listOf(row(40.0, at(2027, 1, 1))), ChartPeriod.WEEK, 0, at(2026, 12, 30))
        assertEquals("28 Dec – 3 Jan 2027", r.range)
        assertEquals(40.0, r.spent, 0.001)
        assertTrue(r.buckets[4].spent == 40.0)                                  // Fri 1 Jan
    }
}
