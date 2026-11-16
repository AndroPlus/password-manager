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

    private val newDefaults = listOf(
        "Food & Dining", "Groceries", "Transportation", "Bills & Utilities", "Entertainment",
        "Housing", "Healthcare", "Education", "Shopping", "Insurance & Savings", "Cash",
        "Income", "Transfers", "Card Bill", "Other",
    )

    @Test fun cardBillsAndSelfTransfersAreSeparated() {
        assertEquals("Card Bill", expense("TATA PAYMENTS", "UPI/P2M/1/TATA PAYMENTS/", newDefaults))
        assertEquals("Card Bill", expense("Bank transaction", "CREDITCARD PAYMENT XX 1234", newDefaults))
        assertEquals("Transfers", expense("Bank transfer", "MOB/SELFFT/123/xfer", newDefaults))
    }

    @Test fun localShopWordsAreRecognised() {
        assertEquals("Food & Dining", expense("SRI MURUGAN TEA STALL", "", newDefaults))
        assertEquals("Food & Dining", expense("ANNAPOORNA MESS", "", newDefaults))
        assertEquals("Groceries", expense("SRI GANESH STORES", "", newDefaults))
        assertEquals("Groceries", expense("LAKSHMI AGENCIES", "", newDefaults))
        assertEquals("Entertainment", expense("RAJA TALKIES", "", newDefaults))
        assertEquals("Healthcare", expense("SAKTHI MEDICALS", "", newDefaults))
        assertEquals("Insurance & Savings", expense("Bank transaction", "APY/123/contribution", newDefaults))
    }

    @Test fun unknownFallsBackToOtherNotShopping() {
        assertEquals("Other", TransactionCategorizer.categorizeOrOther("Some Person", "", true, newDefaults))
        // Without an "Other" category, the first one is used.
        assertEquals("Food & Dining", TransactionCategorizer.categorizeOrOther("Some Person", "", true, defaults))
    }

    @Test fun hdfcCardUpiAlertGoesToOther() {
        val sms = "Txn Rs.30.00 On HDFC Bank Card 1234 At *abcd@ptys by UPI 100000000001 On 14-11 " +
            "Not You? Call 18002586161/SMS BLOCK CC 1234 to 7308080808"
        val r = AutoReclassifier.reclassify("SMS", sms, newDefaults)!!
        assertEquals(true, r.isExpense)
        assertEquals("Other", r.category)
    }

    @Test fun reclassifierFixesOldShoppingEntries() {
        val sms = "Txn Rs.30.00\nOn HDFC Bank Card 1234\nAt *abcd@ptys \nby UPI 1\nOn 14-11"
        val r = AutoReclassifier.reclassify("SMS", sms, newDefaults)!!
        assertEquals("Other", r.category)
        assertEquals("abcd", r.merchant)
        val tea = AutoReclassifier.reclassify("SMS",
            "INR 20.00 debited\nA/c no. XX1234\nUPI/P2M/1/SRI MURUGAN TEA STALL\nAxis Bank", newDefaults)!!
        assertEquals("Food & Dining", tea.category)
        assertEquals("SRI MURUGAN TEA STALL", tea.merchant)
        val gpay = AutoReclassifier.reclassify("GPay", "₹150 paid to Swiggy\nPayment successful", newDefaults)!!
        assertEquals("Food & Dining", gpay.category)
        assertNull(AutoReclassifier.reclassify("SMS", "Your a/c XX1234 avl bal is Rs 5000", newDefaults))
    }
}
