package com.androplus.pwdmgr.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransactionCategorizerTest {

    // The app's built-in default categories.
    private val defaults = listOf(
        "Food & Dining", "Transportation", "Entertainment", "Housing",
        "Healthcare", "Shopping", "Income",
    )

    private fun expense(merchant: String, text: String = "", available: List<String> = defaults) =
        TransactionCategorizer.categorize(merchant, text, isExpense = true, available = available)

    @Test fun mapsCommonMerchantsToDefaults() {
        assertEquals("Food & Dining", expense("swiggy"))
        assertEquals("Transportation", expense("UBER INDIA"))
        assertEquals("Transportation", expense("hppay", "fuel"))
        assertEquals("Entertainment", expense("NETFLIX"))
        assertEquals("Healthcare", expense("APOLLO PHARMACY"))
        assertEquals("Shopping", expense("AMAZON"))
        assertEquals("Housing", expense("AIRTEL"))
    }

    @Test fun groceriesFallBackToFoodUnlessUserHasGroceries() {
        assertEquals("Food & Dining", expense("DMART"))
        assertEquals("Groceries", expense("DMART", available = defaults + "Groceries"))
    }

    @Test fun matchesWholeWordsOnly() {
        // "ola" inside a person's name must not become a cab ride.
        assertNull(expense("Ravi Kola"))
        // "jio" rule must not fire for JioMart; JioMart is groceries.
        assertEquals("Food & Dining", expense("jiomart"))
    }

    @Test fun categoryMatchingIsCaseInsensitiveAndReturnsUsersSpelling() {
        assertEquals("food & dining", expense("zomato", available = listOf("food & dining")))
    }

    @Test fun unknownMerchantReturnsNull() = assertNull(expense("Some Person"))

    @Test fun creditsGoToIncome() {
        assertEquals("Income", TransactionCategorizer.categorize("NEFT", "ACME SALARY", false, defaults))
        assertEquals("Income", TransactionCategorizer.categorize("Anitha S", "", false, defaults))
        assertEquals(
            "Salary",
            TransactionCategorizer.categorize("NEFT", "ACME SALARY", false, defaults + "Salary"),
        )
    }
}
