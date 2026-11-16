package com.androplus.pwdmgr.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class ExpenseGroupingTest {

    private fun row(id: String, cat: String, amount: Double, t: Long, income: Boolean = false) =
        ExpenseRow(id, id, "SMS", amount, income, t, cat, "#000000")

    @Test fun titlesAreCleanNotRawSms() {
        assertEquals("Swiggy", ExpenseGrouping.displayTitle("[GPay] swiggy", "Food & Dining"))
        assertEquals("Hp Pay", ExpenseGrouping.displayTitle("[SMS] hp_pay", "Transportation"))
        assertEquals("AMAZON", ExpenseGrouping.displayTitle("[SMS] AMAZON", "Shopping"))
        assertEquals("Ravi Kumar", ExpenseGrouping.displayTitle("[PhonePe] Ravi Kumar", "Other"))
        assertEquals("Paytm", ExpenseGrouping.displayTitle("[Auto] paytm", "Shopping"))  // old entries
        assertEquals("Lunch with team", ExpenseGrouping.displayTitle("Lunch with team", "Food"))  // manual
        assertEquals("Food & Dining", ExpenseGrouping.displayTitle("[SMS] ", "Food & Dining"))
    }

    @Test fun groupsByCategoryBiggestSpendFirstNewestFirst() {
        val groups = ExpenseGrouping.group(listOf(
            row("a", "Food & Dining", 150.0, 1),
            row("b", "Shopping", 1899.0, 2),
            row("c", "Food & Dining", 200.0, 3),
            row("d", "Income", 85000.0, 4, income = true),
        ))
        assertEquals(listOf("Shopping", "Food & Dining", "Income"), groups.map { it.name })
        assertEquals(listOf("c", "a"), groups[1].rows.map { it.id })
        assertEquals(350.0, groups[1].spent, 0.001)
        assertEquals(85000.0, groups[2].received, 0.001)
    }

    @Test fun blankCategoryGoesToUncategorized() {
        assertEquals("Uncategorized", ExpenseGrouping.group(listOf(row("a", "", 10.0, 1)))[0].name)
    }
}
