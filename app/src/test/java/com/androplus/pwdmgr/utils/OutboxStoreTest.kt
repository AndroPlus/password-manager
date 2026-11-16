package com.androplus.pwdmgr.utils

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class OutboxStoreTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun msg(n: Int) = OutboundMessage.create("sms", "SMS", "", "Rs $n debited", n.toLong())

    @Test fun queuesInOrderAndRemovesOnlyConfirmed() {
        val store = OutboxStore(File(tmp.root, "outbox.jsonl"))
        (1..3).forEach { store.enqueue(msg(it)) }
        assertEquals(listOf(msg(1), msg(2)), store.peek(2))
        store.remove(setOf(msg(1).id, msg(3).id))
        assertEquals(listOf(msg(2)), store.peek(10))
    }

    @Test fun survivesReopenAndIgnoresDuplicates() {
        val f = File(tmp.root, "outbox.jsonl")
        OutboxStore(f).apply { enqueue(msg(1)); enqueue(msg(1)) }
        assertEquals(1, OutboxStore(f).size())
    }

    @Test fun keepsOnlyNewestWhenFull() {
        val store = OutboxStore(File(tmp.root, "outbox.jsonl"), maxMessages = 3)
        (1..5).forEach { store.enqueue(msg(it)) }
        assertEquals(listOf(msg(3), msg(4), msg(5)), store.peek(10))
    }

    @Test fun skipsCorruptLines() {
        val f = File(tmp.root, "outbox.jsonl")
        OutboxStore(f).enqueue(msg(1))
        f.appendText("{not json\n")
        assertEquals(listOf(msg(1)), OutboxStore(f).peek(10))
    }
}
