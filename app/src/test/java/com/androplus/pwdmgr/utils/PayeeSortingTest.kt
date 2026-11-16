package com.androplus.pwdmgr.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class PayeeSortingTest {

    private fun t(payee: String, amount: Double, cat: String = "Other", expense: Boolean = true, time: Long = 1) =
        PayeeTxn(payee, amount, expense, cat, time)

    @Test fun groupsByPayeeBiggestFirstAndSkipsLearnedGenericAndTransfers() {
        val txns = listOf(
            t("[SMS] KANNAN STORE", 100.0, time = 1),
            t("[GPay] Kannan Store", 50.0, time = 5),
            t("[SMS] RAJA TEASTALL", 400.0, "Food & Dining"),
            t("[SMS] SWIGGY", 900.0, "Food & Dining"),          // learned below
            t("[SMS] UPI payment", 5000.0),                       // generic
            t("[SMS] TATA PAYMENTS", 9000.0, "Card Bill"),        // not shop spending
            t("[SMS] ACME SALARY", 85000.0, "Income", expense = false),
        )
        val learned = setOf(CategoryMemory.key("swiggy", true))
        val out = PayeeSorting.unsorted(txns) { it in learned }
        assertEquals(listOf("RAJA TEASTALL", "Kannan Store"), out.map { it.name })
        assertEquals(2, out[1].count)
        assertEquals(150.0, out[1].total, 0.001)
        assertEquals("[GPay] Kannan Store", out[1].payee)   // newest wins
    }

    @Test fun quickChoicesPutGuessFirstThenMostUsed() {
        val available = listOf("Food & Dining", "Groceries", "Shopping", "Health", "Other", "Income")
        val past = listOf("Groceries", "Groceries", "Health", "Other", "Other", "Other", "Income")
        assertEquals(listOf("Health", "Groceries"), PayeeSorting.quickChoices("Health", past, available))
        // "Other" is never a quick choice; most used fills in.
        assertEquals(listOf("Groceries", "Health"), PayeeSorting.quickChoices("Other", past, available))
        // Nothing used yet: sensible defaults.
        assertEquals(listOf("Food & Dining", "Groceries"), PayeeSorting.quickChoices(null, emptyList(), available))
    }
}
