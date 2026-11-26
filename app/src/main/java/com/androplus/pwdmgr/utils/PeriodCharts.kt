package com.androplus.pwdmgr.utils

import java.util.Calendar
import java.util.Locale

enum class ChartPeriod { WEEK, MONTH, YEAR }

/** One bar: a day (week / month view) or a month (year view). */
data class ChartBucket(
    val label: String,        // short, under the bar ("Mon", "17", "Mar"); may be blank when crowded
    val fullLabel: String,    // for the tap detail ("Tue, 17 Nov", "March 2026")
    val spent: Double,
    val startMillis: Long,
    val endMillis: Long,
)

/** Everything the Week / Month / Year chart shows for one period. */
data class PeriodReport(
    val period: ChartPeriod,
    val title: String,        // "This week", "Last month", "2024"…
    val range: String,        // "16 – 22 Nov", "November 2026", "2026"
    val startMillis: Long,
    val endMillis: Long,
    val buckets: List<ChartBucket>,
    val spent: Double,        // counted spending: savings and not-counted left out
    val earned: Double,
    val saved: Double,
    val previousSpent: Double,
    val previousLabel: String, // "Last week", "Previous week"…
)

object PeriodCharts {

    private const val DAY = 86_400_000L

    /** Monday-first weeks (India). Offset 0 is the period containing [now]; -1 the one before. */
    fun range(period: ChartPeriod, offset: Int, now: Long): Pair<Long, Long> {
        val start = Calendar.getInstance().apply { timeInMillis = now; clearTime() }
        when (period) {
            ChartPeriod.WEEK -> {
                val daysSinceMonday = (start.get(Calendar.DAY_OF_WEEK) + 5) % 7   // Mon=0 … Sun=6
                start.add(Calendar.DAY_OF_YEAR, -daysSinceMonday + offset * 7)
            }
            ChartPeriod.MONTH -> {
                start.set(Calendar.DAY_OF_MONTH, 1)
                start.add(Calendar.MONTH, offset)
            }
            ChartPeriod.YEAR -> {
                start.set(Calendar.DAY_OF_YEAR, 1)
                start.add(Calendar.YEAR, offset)
            }
        }
        val end = (start.clone() as Calendar).apply {
            when (period) {
                ChartPeriod.WEEK -> add(Calendar.DAY_OF_YEAR, 7)
                ChartPeriod.MONTH -> add(Calendar.MONTH, 1)
                ChartPeriod.YEAR -> add(Calendar.YEAR, 1)
            }
            add(Calendar.MILLISECOND, -1)
        }
        return start.timeInMillis to end.timeInMillis
    }

    fun report(rows: List<ExpenseRow>, period: ChartPeriod, offset: Int, now: Long): PeriodReport {
        val (start, end) = range(period, offset, now)
        val counted = rows.filter { it.categoryName !in ExpenseGrouping.NOT_COUNTED }
        fun spentIn(a: Long, b: Long) = counted
            .filter { !it.isIncome && it.categoryName !in HomeStatsCalc.SAVINGS && it.timeMillis in a..b }
            .sumOf { it.amount }

        val inPeriod = counted.filter { it.timeMillis in start..end }
        val (prevStart, prevEnd) = range(period, offset - 1, now)
        val unit = when (period) { ChartPeriod.WEEK -> "week"; ChartPeriod.MONTH -> "month"; ChartPeriod.YEAR -> "year" }

        return PeriodReport(
            period = period,
            title = when (offset) {
                0 -> "This $unit"
                -1 -> "Last $unit"
                else -> rangeText(period, start, end, now)
            },
            range = rangeText(period, start, end, now),
            startMillis = start,
            endMillis = end,
            buckets = buckets(period, start, end, counted),
            spent = spentIn(start, end),
            earned = inPeriod.filter { it.isIncome }.sumOf { it.amount },
            saved = inPeriod.filter { !it.isIncome && it.categoryName in HomeStatsCalc.SAVINGS }.sumOf { it.amount },
            previousSpent = spentIn(prevStart, prevEnd),
            previousLabel = if (offset == 0) "Last $unit" else "Previous $unit",
        )
    }

    private fun buckets(period: ChartPeriod, start: Long, end: Long, counted: List<ExpenseRow>): List<ChartBucket> {
        val cal = Calendar.getInstance().apply { timeInMillis = start }
        val out = mutableListOf<ChartBucket>()
        val count = when (period) {
            ChartPeriod.WEEK -> 7
            ChartPeriod.MONTH -> cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            ChartPeriod.YEAR -> 12
        }
        repeat(count) { i ->
            val bStart = cal.timeInMillis
            val next = (cal.clone() as Calendar).apply {
                if (period == ChartPeriod.YEAR) add(Calendar.MONTH, 1) else add(Calendar.DAY_OF_YEAR, 1)
            }
            val bEnd = next.timeInMillis - 1
            val label = when (period) {
                ChartPeriod.WEEK -> name(cal, Calendar.DAY_OF_WEEK, Calendar.SHORT)
                // Day 1, every 5th day, and the last day unless it sits right beside a label (31 beside 30).
                ChartPeriod.MONTH -> (i + 1).toString()
                    .takeIf { (i + 1) == 1 || (i + 1) % 5 == 0 || (i == count - 1 && count % 5 >= 3) } ?: ""
                ChartPeriod.YEAR -> name(cal, Calendar.MONTH, Calendar.SHORT)
            }
            val full = when (period) {
                ChartPeriod.YEAR -> name(cal, Calendar.MONTH, Calendar.LONG) + " " + cal.get(Calendar.YEAR)
                else -> name(cal, Calendar.DAY_OF_WEEK, Calendar.SHORT) + ", " + cal.get(Calendar.DAY_OF_MONTH) + " " +
                    name(cal, Calendar.MONTH, Calendar.SHORT)
            }
            val spent = counted
                .filter { !it.isIncome && it.categoryName !in HomeStatsCalc.SAVINGS && it.timeMillis in bStart..bEnd }
                .sumOf { it.amount }
            out += ChartBucket(label, full, spent, bStart, bEnd)
            cal.timeInMillis = next.timeInMillis
        }
        return out
    }

    private fun rangeText(period: ChartPeriod, start: Long, end: Long, now: Long): String {
        val a = Calendar.getInstance().apply { timeInMillis = start }
        val b = Calendar.getInstance().apply { timeInMillis = end }
        val thisYear = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.YEAR)
        val yearSuffix = if (b.get(Calendar.YEAR) != thisYear || a.get(Calendar.YEAR) != thisYear) " ${b.get(Calendar.YEAR)}" else ""
        return when (period) {
            ChartPeriod.WEEK -> {
                val sameMonth = a.get(Calendar.MONTH) == b.get(Calendar.MONTH)
                if (sameMonth) "${a.get(Calendar.DAY_OF_MONTH)} – ${b.get(Calendar.DAY_OF_MONTH)} ${name(b, Calendar.MONTH, Calendar.SHORT)}$yearSuffix"
                else "${a.get(Calendar.DAY_OF_MONTH)} ${name(a, Calendar.MONTH, Calendar.SHORT)} – " +
                    "${b.get(Calendar.DAY_OF_MONTH)} ${name(b, Calendar.MONTH, Calendar.SHORT)}$yearSuffix"
            }
            ChartPeriod.MONTH -> name(a, Calendar.MONTH, Calendar.LONG) + " " + a.get(Calendar.YEAR)
            ChartPeriod.YEAR -> a.get(Calendar.YEAR).toString()
        }
    }

    private fun name(c: Calendar, field: Int, style: Int): String = c.getDisplayName(field, style, Locale.ENGLISH) ?: ""

    private fun Calendar.clearTime() {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
}
