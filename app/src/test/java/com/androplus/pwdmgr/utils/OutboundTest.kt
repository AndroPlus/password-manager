package com.androplus.pwdmgr.utils

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OutboundTest {

    @Test fun sendsMoneyMessages() {
        assertTrue(OutboundFilter.shouldSend("", "Rs 150.00 debited from a/c XX1234 to VPA swiggy@ybl"))
        assertTrue(OutboundFilter.shouldSend("₹150 paid to Swiggy", "Payment successful"))
        // Looser than the parser on purpose: the server decides about this one.
        assertTrue(OutboundFilter.shouldSend("", "INR 2,000 sent via NEFT, ref 123"))
    }

    @Test fun neverSendsSecretsOrNonMoneyMessages() {
        assertFalse(OutboundFilter.shouldSend("", "123456 is your OTP for txn of Rs 2000. Do not share."))
        assertFalse(OutboundFilter.shouldSend("", "Use verification code 4411 for Rs 500 payment"))
        assertFalse(OutboundFilter.shouldSend("", "Your UPI PIN for Rs 1 transaction was changed"))
        assertFalse(OutboundFilter.shouldSend("Mom", "Come home early today"))
    }

    @Test fun stableIdIsDeterministicAndDistinct() {
        val a = OutboundMessage.create("sms", "SMS", "", "Rs 150 debited", 1000L)
        val b = OutboundMessage.create("sms", "SMS", "", "Rs 150 debited", 1000L)
        val c = OutboundMessage.create("sms", "SMS", "", "Rs 150 debited", 2000L)
        assertEquals(a.id, b.id)
        assertNotEquals(a.id, c.id)
        assertEquals(32, a.id.length)
    }

    @Test fun serializesToServerFieldNames() {
        val m = OutboundMessage.create("notification", "GPay", "₹150 paid to Swiggy", "Done", 1773122400000L)
        val json = JsonParser.parseString(Gson().toJson(m)).asJsonObject
        assertEquals(setOf("id", "kind", "source", "title", "text", "received_at"), json.keySet())
        assertEquals(1773122400000L, json["received_at"].asLong)
    }
}
