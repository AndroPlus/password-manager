package com.androplus.pwdmgr.utils

import java.util.Calendar
import java.util.Locale

data class MonthBar(val label: String, val spent: Double, val earned: Double)

data class Recurring(val name: String, val typicalAmount: Double, val dayOfMonth: Int, val months: Int)

data class TopPayee(val name: String, val count: Int, val total: Double)

object Insights {

    /** Spent (savings and not-counted excluded) and earned for the last [months] months, oldest first. */
    fun monthly(rows: List<ExpenseRow>, now: Long, months: Int = 6): List<MonthBar> {
        val counted = rows.filter { it.categoryName !in ExpenseGrouping.NOT_COUNTED }
        return (months - 1 downTo 0).map { back ->
            val (start, end) = monthRange(now, -back)
            val inMonth = counted.filter { it.timeMillis in start..end }
            val label = Calendar.getInstance().apply { timeInMillis = start }
                .getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.ENGLISH) ?: ""
            MonthBar(
                label = label,
                spent = inMonth.filter { !it.isIncome && it.categoryName !in HomeStatsCalc.SAVINGS }.sumOf { it.amount },
                earned = inMonth.filter { it.isIncome }.sumOf { it.amount },
            )
        }
    }

    /**
     * Payments to the same payee in at least 3 of the last 4 months, at a
     * similar amount (within 25% of the typical one): rent, RD, recharges, EMIs.
     */
    fun recurring(rows: List<ExpenseRow>, now: Long): List<Recurring> {
        val window = monthRange(now, -3).first
        return rows.asSequence()
            .filter { !it.isIncome && it.timeMillis >= window && it.categoryName !in ExpenseGrouping.NOT_COUNTED }
            .mapNotNull { r -> CategoryMemory.key(r.title, true)?.let { it to r } }
            .groupBy({ it.first }, { it.second })
            .mapNotNull { (_, items) ->
                val typical = median(items.map { it.amount })
                val similar = items.filter { kotlin.math.abs(it.amount - typical) <= typical * 0.25 }
                val months = similar.map { monthIndex(it.timeMillis) }.toSet().size
                if (months < 3) return@mapNotNull null
                val day = median(similar.map {
                    Calendar.getInstance().apply { timeInMillis = it.timeMillis }.get(Calendar.DAY_OF_MONTH).toDouble()
                }).toInt()
                Recurring(similar.maxBy { it.timeMillis }.title, typical, day, months)
            }
            .sortedByDescending { it.typicalAmount }
    }

    /** Most paid payees in [rows] by total, spending only. */
    fun topPayees(rows: List<ExpenseRow>, n: Int = 5): List<TopPayee> =
        rows.filter { !it.isIncome && it.categoryName !in ExpenseGrouping.NOT_COUNTED }
            .groupBy { CategoryMemory.normalize(it.title) }
            .map { (_, items) -> TopPayee(items.maxBy { it.timeMillis }.title, items.size, items.sumOf { it.amount }) }
            .sortedByDescending { it.total }
            .take(n)

    /** Start and end (inclusive) of the month [offset] months from [now]'s month. */
    fun monthRange(now: Long, offset: Int): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.MONTH, offset)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1); add(Calendar.MILLISECOND, -1) }
        return start.timeInMillis to end.timeInMillis
    }

    private fun monthIndex(t: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = t }
        return c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH)
    }

    private fun median(xs: List<Double>): Double {
        val s = xs.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }
}
