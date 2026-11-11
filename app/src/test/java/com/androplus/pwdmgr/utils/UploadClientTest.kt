package com.androplus.pwdmgr.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadClientTest {

    @Test fun onlyHttpsServerUrlsAreAccepted() {
        assertTrue(UploadClient.isValidServerUrl("https://finintel.onrender.com"))
        assertFalse(UploadClient.isValidServerUrl("http://finintel.onrender.com"))
        assertFalse(UploadClient.isValidServerUrl("https://"))
        assertFalse(UploadClient.isValidServerUrl("finintel.onrender.com"))
    }

    @Test fun plainHttpIsRefusedWithoutSending() {
        val r = UploadClient.upload("http://example.com", "t",
            listOf(OutboundMessage.create("sms", "SMS", "", "Rs 1 debited", 1)))
        assertEquals(UploadResult.Unauthorized, r)
    }

    @Test fun unreachableServerMeansRetryLater() {
        // Port 9 on localhost: nothing listens, so the connection is refused.
        val r = UploadClient.upload("http://127.0.0.1:9", "t",
            listOf(OutboundMessage.create("sms", "SMS", "", "Rs 1 debited", 1)),
            allowHttpForTests = true)
        assertTrue(r is UploadResult.RetryLater)
    }

    @Test fun emptyQueueSendsNothing() {
        assertEquals(UploadResult.Done(emptySet()), UploadClient.upload("https://x.example", "t", emptyList()))
    }
}
