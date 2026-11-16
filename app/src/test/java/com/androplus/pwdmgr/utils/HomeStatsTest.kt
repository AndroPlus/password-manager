package com.androplus.pwdmgr.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class HomeStatsTest {

    private fun at(y: Int, m: Int, d: Int, h: Int = 12): Long =
        Calendar.getInstance().apply { clear(); set(y, m - 1, d, h, 0) }.timeInMillis

    private fun row(title: String, amount: Double, cat: String, income: Boolean = false, t: Long = at(2026, 11, 10)) =
        ExpenseRow(title, title, "SMS", amount, income, t, cat, "#000000")

    private val nov = Insights.monthRange(at(2026, 11, 16), 0)

    @Test fun splitsSpendingSavingsAndNotCounted() {
        val rows = listOf(
            row("Grocer", 600.0, "Groceries"),
            row("Cafe", 400.0, "Food & Dining"),
            row("RD", 2000.0, "Insurance & Savings"),
            row("Card bill", 9000.0, "Card Bill"),
            row("Salary", 50000.0, "Income", income = true),
        )
        val s = HomeStatsCalc.compute(rows, budget = 31000.0, now = at(2026, 11, 16), periodStart = nov.first, periodEnd = nov.second)
        assertEquals(1000.0, s.spent, 0.01)
        assertEquals(2000.0, s.saved, 0.01)
        assertEquals(9000.0, s.notCounted, 0.01)
        assertEquals(50000.0, s.income, 0.01)
        assertEquals(15, s.daysLeft)                    // 16..30 Nov
        assertEquals(2000.0, s.dailyLeft!!, 0.01)       // (31000 - 1000) / 15
        assertEquals(listOf("Groceries", "Food & Dining"), s.top.map { it.name })
        assertEquals(0.6, s.top[0].share, 0.001)
    }

    @Test fun noBudgetOrPastMonthHasNoDailyFigure() {
        val s = HomeStatsCalc.compute(emptyList(), null, at(2026, 11, 16), nov.first, nov.second)
        assertNull(s.dailyLeft)
        val past = Insights.monthRange(at(2026, 11, 16), -1)
        val p = HomeStatsCalc.compute(emptyList(), 1000.0, at(2026, 11, 16), past.first, past.second)
        assertEquals(0, p.daysLeft)
        assertNull(p.dailyLeft)
    }

    @Test fun monthlyBarsOldestFirst() {
        val rows = listOf(
            row("Grocer", 100.0, "Groceries", t = at(2026, 10, 5)),
            row("Salary", 900.0, "Income", income = true, t = at(2026, 11, 1)),
            row("RD", 500.0, "Insurance & Savings", t = at(2026, 11, 3)),
        )
        val bars = Insights.monthly(rows, at(2026, 11, 16), months = 3)
        assertEquals(listOf("Sep", "Oct", "Nov"), bars.map { it.label })
        assertEquals(100.0, bars[1].spent, 0.01)
        assertEquals(0.0, bars[2].spent, 0.01)
        assertEquals(900.0, bars[2].earned, 0.01)
    }

    @Test fun recurringNeedsThreeMonthsOfSimilarAmounts() {
        val rows = listOf(
            row("[SMS] HOUSE OWNER", 12000.0, "Housing", t = at(2026, 9, 5)),
            row("[SMS] HOUSE OWNER", 12000.0, "Housing", t = at(2026, 10, 4)),
            row("[SMS] HOUSE OWNER", 12500.0, "Housing", t = at(2026, 11, 5)),
            row("[SMS] Grocer", 50.0, "Groceries", t = at(2026, 10, 1)),
            row("[SMS] Grocer", 900.0, "Groceries", t = at(2026, 11, 1)),
            row("[SMS] Grocer", 3000.0, "Groceries", t = at(2026, 9, 1)),
        )
        val r = Insights.recurring(rows, at(2026, 11, 16))
        assertEquals(1, r.size)
        assertEquals("[SMS] HOUSE OWNER", r[0].name)
        assertEquals(12000.0, r[0].typicalAmount, 0.01)
        assertEquals(5, r[0].dayOfMonth)
    }

    @Test fun topPayeesMergeSpellings() {
        val rows = listOf(
            row("[SMS] CORNER GROCERY", 100.0, "Groceries"),
            row("[GPay] Corner Grocery", 50.0, "Groceries", t = at(2026, 11, 12)),
            row("[SMS] Cafe", 120.0, "Food & Dining"),
        )
        val top = Insights.topPayees(rows)
        assertEquals(150.0, top[0].total, 0.01)
        assertEquals(2, top[0].count)
        assertEquals("[GPay] Corner Grocery", top[0].name)
    }
}
