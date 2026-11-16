package com.androplus.pwdmgr.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateDetectorTest {

    private val t0 = 1_700_000_000_000L
    private val minute = 60_000L

    private val bankSms = AutoTxn(150.0, true, t0, "SMS", "Rs 150 debited from a/c XX1234 to VPA swiggy@ybl")

    @Test fun gpayNotificationAfterBankSmsIsADuplicate() {
        val gpay = AutoTxn(150.0, true, t0 + 30_000, "GPay", "₹150 paid to Swiggy\nPayment successful")
        assertTrue(DuplicateDetector.isDuplicate(gpay, listOf(bankSms)))
    }

    @Test fun sameMessageDeliveredTwiceIsADuplicate() {
        assertTrue(DuplicateDetector.isDuplicate(bankSms.copy(timeMillis = t0 + minute), listOf(bankSms)))
    }

    @Test fun twoDifferentBankSmsForSameAmountAreBothKept() {
        val second = AutoTxn(150.0, true, t0 + minute, "SMS", "Rs 150 debited from a/c XX1234 to VPA chai@ybl")
        assertFalse(DuplicateDetector.isDuplicate(second, listOf(bankSms)))
    }

    @Test fun differentAmountDirectionOrTimeIsNotADuplicate() {
        val gpay = AutoTxn(150.0, true, t0, "GPay", "₹150 paid to Swiggy")
        assertFalse(DuplicateDetector.isDuplicate(gpay.copy(amount = 151.0), listOf(bankSms)))
        assertFalse(DuplicateDetector.isDuplicate(gpay.copy(isExpense = false), listOf(bankSms)))
        assertFalse(DuplicateDetector.isDuplicate(gpay.copy(timeMillis = t0 + 11 * minute), listOf(bankSms)))
    }

    @Test fun sourceTagRoundTrips() {
        val tagged = DuplicateDetector.tag("GPay", "Swiggy")
        assertEquals("[GPay] Swiggy", tagged)
        assertEquals("GPay", DuplicateDetector.sourceOf(tagged))
        assertEquals("Auto", DuplicateDetector.sourceOf("[Auto] paytm"))   // entries saved by the old code
        assertNull(DuplicateDetector.sourceOf("Lunch with team"))           // manual entry
    }
}
