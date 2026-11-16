package com.androplus.pwdmgr.utils

import java.util.Calendar
import kotlin.math.ceil

data class CategoryShare(
    val name: String,
    val color: String,
    val amount: Double,
    val share: Double,      // 0..1 of spending
    val count: Int,
)

/** Everything the Home card shows for one period. */
data class HomeStats(
    val spent: Double,          // counted spending, savings excluded
    val income: Double,
    val saved: Double,          // RD, LIC, pension, investments
    val notCounted: Double,     // card bills and own transfers (both directions)
    val budget: Double?,        // null when no budget is set
    val daysLeft: Int,          // including today; 0 for a past period
    val dailyLeft: Double?,     // what can still be spent per day, null without budget/days
    val usedPercent: Int?,
    val top: List<CategoryShare>,
)

object HomeStatsCalc {

    /** Money put away, shown as "Saved" rather than spending. */
    val SAVINGS = setOf("Insurance & Savings", "Investments")

    fun compute(
        rows: List<ExpenseRow>,
        budget: Double?,
        now: Long,
        periodStart: Long,
        periodEnd: Long,
    ): HomeStats {
        val notCounted = rows.filter { it.categoryName in ExpenseGrouping.NOT_COUNTED }.sumOf { it.amount }
        val counted = rows.filter { it.categoryName !in ExpenseGrouping.NOT_COUNTED }
        val income = counted.filter { it.isIncome }.sumOf { it.amount }
        val outgoing = counted.filterNot { it.isIncome }
        val saved = outgoing.filter { it.categoryName in SAVINGS }.sumOf { it.amount }
        val spending = outgoing.filter { it.categoryName !in SAVINGS }
        val spent = spending.sumOf { it.amount }

        val daysLeft = if (now in periodStart..periodEnd) daysBetween(now, periodEnd) else 0
        val b = budget?.takeIf { it > 0 }
        val dailyLeft = if (b != null && daysLeft > 0) maxOf(0.0, (b - spent) / daysLeft) else null
        val used = b?.let { ((spent / it) * 100).toInt() }

        val top = spending.groupBy { it.categoryName.ifBlank { ExpenseGrouping.UNCATEGORIZED } }
            .map { (name, items) ->
                CategoryShare(
                    name = name,
                    color = items.first().categoryColor,
                    amount = items.sumOf { it.amount },
                    share = if (spent > 0) items.sumOf { it.amount } / spent else 0.0,
                    count = items.size,
                )
            }
            .sortedByDescending { it.amount }

        return HomeStats(spent, income, saved, notCounted, b, daysLeft, dailyLeft, used, top)
    }

    /** Whole days from [from]'s day to [to]'s day, both included. */
    fun daysBetween(from: Long, to: Long): Int {
        val a = dayStart(from)
        val b = dayStart(to)
        return (ceil((b - a) / 86_400_000.0).toInt() + 1).coerceAtLeast(1)
    }

    private fun dayStart(t: Long): Long = Calendar.getInstance().apply {
        timeInMillis = t
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
