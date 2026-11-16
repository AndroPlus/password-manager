package com.androplus.pwdmgr.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NotificationLogTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test fun keepsNewestFirstAndCapsSize() {
        val log = NotificationLog(File(tmp.root, "n.jsonl"), keep = 3)
        (1..5).forEach { log.add(SeenNotification(it.toLong(), "GPay", "t$it", "x", "recorded")) }
        assertEquals(listOf("t5", "t4", "t3"), log.recent().map { it.title })
    }

    @Test fun truncatesLongTextAndClears() {
        val log = NotificationLog(File(tmp.root, "n.jsonl"))
        log.add(SeenNotification(1, "PhonePe", "a".repeat(500), "b".repeat(500), "not a payment"))
        assertEquals(120, log.recent()[0].title.length)
        assertEquals(240, log.recent()[0].text.length)
        log.clear()
        assertTrue(log.recent().isEmpty())
    }
}
