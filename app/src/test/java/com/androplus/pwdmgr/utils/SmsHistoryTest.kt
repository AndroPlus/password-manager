package com.androplus.pwdmgr.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SmsHistoryTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test fun bankSenders() {
        assertTrue(BankSender.looksLikeBank("AX-AXISBK"))
        assertTrue(BankSender.looksLikeBank("56767"))
        assertFalse(BankSender.looksLikeBank("+919876543210"))
        assertFalse(BankSender.looksLikeBank("9876543210"))
    }

    @Test fun selectsRecentBankSmsOldestFirst() {
        val now = 100 * SmsHistory.DAY_MILLIS
        val msgs = listOf(
            InboxSms("AX-AXISBK", "Rs 10 debited", now - 1 * SmsHistory.DAY_MILLIS),
            InboxSms("AX-AXISBK", "Rs 20 debited", now - 40 * SmsHistory.DAY_MILLIS),  // too old
            InboxSms("9876543210", "Rs 30 sent", now - 2 * SmsHistory.DAY_MILLIS),     // a person
            InboxSms("VM-HDFCBK", "Rs 40 debited", now - 5 * SmsHistory.DAY_MILLIS),
            InboxSms("VM-HDFCBK", "  ", now),
        )
        val picked = SmsHistory.select(msgs, SmsHistory.cutoff(now, 30))
        assertEquals(listOf("Rs 40 debited", "Rs 10 debited"), picked.map { it.body })
    }

    @Test fun enqueueAllSkipsAlreadyQueuedAndRepeats() {
        val store = OutboxStore(File(tmp.root, "q.jsonl"))
        val a = OutboundMessage.create("sms", "SMS", "", "Rs 1 debited", 1)
        val b = OutboundMessage.create("sms", "SMS", "", "Rs 2 debited", 2)
        store.enqueue(a)
        store.enqueueAll(listOf(a, b, b))
        assertEquals(listOf(a, b), store.peek(10))
    }
}
