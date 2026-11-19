package com.androplus.pwdmgr.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Merchants' own "payment successful" SMS (no bank account named). Fictional order ids. */
class MerchantReceiptTest {

    private val lpg = "Dear Customer, Transaction of Rs. 954.00 for your LPG booking  is successful  " +
        "bearing LPG Refill Order ID.  1000000000000001  .  Regards, HP PAY Team"

    @Test fun lpgBookingIsASpendFromTheSigner() {
        assertNull("not a bank message", TransactionParser.parseSms(lpg))
        val r = TransactionParser.parseMerchantReceipt(lpg, "VM-HPPAYS")
        assertNotNull(r)
        assertEquals(954.0, r!!.amount, 0.001)
        assertEquals(true, r.isExpense)
        assertEquals("HP PAY", r.merchant)
    }

    @Test fun lpgGoesToBillsNotFuel() {
        val names = listOf("Fuel", "Transportation", "Bills & Utilities", "Other")
        assertEquals("Bills & Utilities",
            TransactionCategorizer.categorizeOrOther("HP PAY", lpg, true, names))
        // Real fuel through HP PAY still counts as fuel.
        assertEquals("Fuel",
            TransactionCategorizer.categorizeOrOther("HP PAY", "Payment of Rs 500 at petrol pump successful", true, names))
    }

    @Test fun otherReceiptShapes() {
        val paid = TransactionParser.parseMerchantReceipt(
            "We have received your payment of Rs 799 for order 12345. Thank you - FreshMart", "AD-FRESHM")
        assertEquals(799.0, paid!!.amount, 0.001)
        assertEquals("FreshMart", paid.merchant)
        val byHeader = TransactionParser.parseMerchantReceipt(
            "Your payment of INR 1,250.50 is successful. Order 778.", "JD-ZOMATO-S")
        assertEquals(1250.5, byHeader!!.amount, 0.001)
        assertEquals("ZOMATO", byHeader.merchant)
    }

    @Test fun promosRefundsAndFailuresAreIgnored() {
        listOf(
            "Get cashback of Rs 50 on your next transaction of Rs 500! Offer valid today.",
            "Refund of Rs 954.00 for your LPG booking is successful. Regards, HP PAY Team",
            "Transaction of Rs 954.00 failed. Amount will be reversed. Regards, HP PAY Team",
            "Your payment of Rs 300 is pending. Regards, Shop",
            "Rs 500 credited to your wallet. Transaction successful",
            "Your LPG booking is confirmed. Delivery in 2 days.",            // no amount
        ).forEach { assertNull(it, TransactionParser.parseMerchantReceipt(it, "VM-SHOPPY")) }
    }

    @Test fun receiptAndBankSmsForOnePaymentAreOneTransaction() {
        val bank = AutoTxn(954.0, true, 1_000_000L, "SMS", "Rs 954 debited from a/c XX1234 to VPA hppay@ybl")
        val receipt = AutoTxn(954.0, true, 1_060_000L, RECEIPT_SOURCE, lpg)
        assertEquals(true, DuplicateDetector.isDuplicate(receipt, listOf(bank)))
    }

    @Test fun reclassifierReadsReceipts() {
        val r = AutoReclassifier.reclassify(RECEIPT_SOURCE, lpg, listOf("Bills & Utilities", "Other"))
        assertEquals("Bills & Utilities", r!!.category)
    }
}
