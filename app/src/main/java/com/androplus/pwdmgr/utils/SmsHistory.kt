package com.androplus.pwdmgr.utils

/** One SMS read from the phone's inbox. */
data class InboxSms(val sender: String, val body: String, val sentAt: Long)

object BankSender {
    /**
     * Banks send from alphanumeric headers (e.g. "AX-AXISBK") or short codes,
     * never from a regular 10-digit mobile number.
     */
    fun looksLikeBank(sender: String): Boolean =
        sender.any { it.isLetter() } || sender.length < 10
}

/** Picks which past SMS to import, oldest first. */
object SmsHistory {

    const val DAY_MILLIS = 24L * 60 * 60 * 1000

    fun cutoff(nowMillis: Long, days: Int): Long = nowMillis - days * DAY_MILLIS

    fun select(messages: List<InboxSms>, cutoffMillis: Long): List<InboxSms> =
        messages
            .filter { it.sentAt >= cutoffMillis && it.body.isNotBlank() && BankSender.looksLikeBank(it.sender) }
            .sortedBy { it.sentAt }
}
