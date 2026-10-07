package com.androplus.pwdmgr.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// All sample messages are fictional; formats mirror real Indian bank SMS.
class TransactionParserTest {

    private fun sms(text: String): ParsedTransaction {
        val parsed = TransactionParser.parseSms(text)
        assertNotNull(text, parsed)
        return parsed!!
    }

    private fun notification(title: String, text: String): ParsedTransaction {
        val parsed = TransactionParser.parseNotification(title, text)
        assertNotNull(title, parsed)
        return parsed!!
    }

    // ---- real transactions -------------------------------------------------

    @Test fun upiDebit() {
        val t = sms("Rs 2000.00 debited from a/c XX1234 on 01-04-25 to VPA shop@ybl UPI Ref 5091. -Axis Bank")
        assertEquals(2000.0, t.amount, 0.001)
        assertTrue(t.isExpense)
        assertEquals("UPI", t.channel)
        assertEquals("1234", t.last4)
        assertEquals("shop", t.merchant)
    }

    @Test fun upiCredit() {
        val t = sms("INR 500.00 credited to A/c XX1234 on 02Apr25 by UPI from ravi@oksbi")
        assertFalse(t.isExpense)
        assertEquals(500.0, t.amount, 0.001)
    }

    @Test fun cardSpendMerchantIsTrimmed() {
        val t = sms("Rs.1899 spent on your Axis Bank Credit Card XX5678 at AMAZON on 08-04-25")
        assertTrue(t.isExpense)
        assertEquals("Card", t.channel)
        assertEquals("AMAZON", t.merchant)
        assertEquals("5678", t.last4)
    }

    @Test fun creditCardPhraseIsNotACredit() {
        val t = sms("Your Credit Card XX5678 has been used for Rs 750 at ZOMATO on 02-04-25")
        assertTrue("'credit card' must not read as money credited", t.isExpense)
        assertEquals("ZOMATO", t.merchant)
    }

    @Test fun balanceTrailerDoesNotBlockRealDebit() {
        val t = sms("Rs 500.00 debited from a/c **1234 on 01-04-25 to VPA swiggy@ybl (UPI Ref 123). Avl bal: Rs 10,000.00")
        assertEquals("the transaction amount, not the balance", 500.0, t.amount, 0.001)
        assertEquals("swiggy", t.merchant)
    }

    @Test fun iciciStylePayee() {
        val t = sms("ICICI Bank Acct XX123 debited for Rs 500.00 on 01-Apr-25; RAVI KUMAR credited. UPI:123")
        assertTrue(t.isExpense)
        assertEquals("RAVI KUMAR", t.merchant)
    }

    @Test fun ownAccountIsNotTheMerchant() {
        val t = sms("Rs 300 debited from A/C X1234 for UPI txn to DMART on 03-04-25")
        assertEquals("DMART", t.merchant)
    }

    @Test fun debitedThenCreditedToBeneficiaryIsAnExpense() {
        val t = sms("Rs 5000 debited from a/c XX1234 and credited to a/c XX9876 via IMPS")
        assertTrue(t.isExpense)
    }

    @Test fun indianDigitGrouping() {
        val t = sms("Rs.1,23,456.00 debited from A/c XX1234 to VPA x@ybl UPI")
        assertEquals(123456.0, t.amount, 0.001)
    }

    @Test fun atmWithdrawal() {
        val t = sms("Rs 5000 withdrawn from ATM A/c XX1234 on 20-04-25")
        assertEquals("ATM", t.channel)
        assertEquals("ATM Withdrawal", t.merchant)
    }

    @Test fun phoneNumberHandleIsNotShownAsMerchant() {
        val t = sms("Rs 100 debited from a/c XX1234 to VPA 9876543210@ybl UPI")
        assertEquals("UPI transfer", t.merchant)
    }

    // ---- messages that must be ignored --------------------------------------

    @Test fun ignoresOtp() =
        assertNull(TransactionParser.parseSms("123456 is your OTP for txn of Rs 2000 at Amazon. Do not share."))

    @Test fun ignoresBalanceAlert() =
        assertNull(TransactionParser.parseSms("Your a/c XX1234 avl bal is Rs 50703.00 as on 01-04-25"))

    @Test fun ignoresFutureMandateDebit() =
        assertNull(TransactionParser.parseSms("Rs 649 will be debited on 14-04 for NETFLIX e-mandate"))

    @Test fun ignoresPromo() =
        assertNull(TransactionParser.parseSms("Get cashback of Rs 500 on your next recharge! Offer valid today"))

    @Test fun ignoresPaymentRequest() =
        assertNull(TransactionParser.parseSms("ravi@oksbi has requested Rs 300 from you on GPay"))

    @Test fun ignoresMessageWithoutAccountReference() =
        assertNull(TransactionParser.parseSms("Rs 200 paid to someone"))

    // ---- payment-app notifications ------------------------------------------

    @Test fun gpayPaid() {
        val t = notification("₹150 paid to Swiggy", "Payment successful")
        assertEquals(150.0, t.amount, 0.001)
        assertTrue(t.isExpense)
        assertEquals("Swiggy", t.merchant)
    }

    @Test fun phonepeReceived() {
        val t = notification("Received ₹500 from Anitha S", "")
        assertFalse(t.isExpense)
        assertEquals("Anitha S", t.merchant)
    }

    @Test fun notificationIgnoresRequestsRewardsPromptsAndFailures() {
        assertNull(TransactionParser.parseNotification("Ravi requested ₹300", "Tap to pay"))
        assertNull(TransactionParser.parseNotification("You won a scratch card", "Cashback up to ₹100"))
        assertNull(TransactionParser.parseNotification("Pay ₹499 to Airtel", "Your bill is due"))
        assertNull(TransactionParser.parseNotification("Payment failed", "₹250 to Zomato could not be completed"))
    }
}
