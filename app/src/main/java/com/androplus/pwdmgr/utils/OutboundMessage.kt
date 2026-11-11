package com.androplus.pwdmgr.utils

import com.google.gson.annotations.SerializedName
import java.security.MessageDigest

/**
 * A bank SMS or payment-app notification waiting to be sent to the FinIntel server.
 * Field names match the server's /api/ingest format.
 */
data class OutboundMessage(
    @SerializedName("id") val id: String,
    @SerializedName("kind") val kind: String,            // "sms" | "notification"
    @SerializedName("source") val source: String,        // SMS | GPay | PhonePe | Paytm
    @SerializedName("title") val title: String,
    @SerializedName("text") val text: String,
    @SerializedName("received_at") val receivedAt: Long, // epoch millis
) {
    companion object {
        fun create(kind: String, source: String, title: String, text: String, receivedAt: Long) =
            OutboundMessage(stableId(source, receivedAt, title, text), kind, source, title, text, receivedAt)

        /**
         * Same message → same id, so if Android delivers it twice, or the phone
         * re-sends after a lost response, the server stores it only once.
         */
        fun stableId(source: String, receivedAt: Long, title: String, text: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest("$source|$receivedAt|$title|$text".toByteArray(Charsets.UTF_8))
            return digest.take(16).joinToString("") { "%02x".format(it) }
        }
    }
}

/**
 * Decides which messages may leave the phone. Deliberately looser than the
 * on-phone parser, so the server can catch transactions the app's rules miss,
 * but never sends OTPs, PINs or anything without a rupee amount.
 */
object OutboundFilter {

    private val amount = Regex("""(?:rs|inr|₹)\.?\s*\d""", RegexOption.IGNORE_CASE)

    private val secrets = listOf(
        "otp", "one time password", "one-time password", "verification code",
        "do not share", "don't share", "never share", "cvv", "mpin", " pin ", "passcode",
    )

    fun shouldSend(title: String, text: String): Boolean {
        val full = "$title $text"
        if (!amount.containsMatchIn(full)) return false
        val low = " ${full.lowercase()} "
        return secrets.none { it in low }
    }
}
