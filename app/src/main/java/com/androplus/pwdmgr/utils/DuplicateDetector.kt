package com.androplus.pwdmgr.utils

import kotlin.math.abs

/** An auto-captured transaction, reduced to the fields duplicate detection needs. */
data class AutoTxn(
    val amount: Double,
    val isExpense: Boolean,
    val timeMillis: Long,
    val source: String,   // "SMS", "GPay", "PhonePe", "Paytm"
    val rawText: String,
)

/**
 * One UPI payment usually produces two signals: the bank's SMS and the payment
 * app's notification. Without this check, every such payment would be saved twice.
 *
 * A candidate is a duplicate of a recent auto-captured transaction when the amount
 * and direction match within the time window AND either the source differs (SMS vs
 * app notification for the same payment) or the text is identical (the same
 * message delivered or posted again).
 *
 * Trade-off: two genuinely separate payments of the same amount, seen via different
 * sources within the window, would collapse into one. That is rare compared to the
 * SMS + notification pair this exists to catch.
 */
object DuplicateDetector {

    const val WINDOW_MILLIS: Long = 10 * 60 * 1000L

    // The source travels in ExpenseTransaction.shortDescription ("[SMS] Swiggy")
    // because adding a Realm field would trigger deleteRealmIfMigrationNeeded().
    private val tagRegex = Regex("""^\[([A-Za-z]+)]\s*""")

    fun tag(source: String, merchant: String): String = "[$source] $merchant"

    /** The source in a tagged short description, or null for a manual entry. */
    fun sourceOf(shortDescription: String): String? =
        tagRegex.find(shortDescription)?.groupValues?.get(1)

    fun isDuplicate(
        candidate: AutoTxn,
        recent: List<AutoTxn>,
        windowMillis: Long = WINDOW_MILLIS,
    ): Boolean = recent.any { r ->
        r.isExpense == candidate.isExpense &&
            abs(r.amount - candidate.amount) < 0.005 &&
            abs(r.timeMillis - candidate.timeMillis) <= windowMillis &&
            (r.source != candidate.source || r.rawText.trim() == candidate.rawText.trim())
    }
}
