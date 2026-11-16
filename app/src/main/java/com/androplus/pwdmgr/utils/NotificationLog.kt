package com.androplus.pwdmgr.utils

import com.google.gson.Gson
import java.io.File

/** One payment-app notification the listener saw, and what the app did with it. */
data class SeenNotification(
    val timeMillis: Long,
    val source: String,     // GPay | PhonePe | Paytm
    val title: String,
    val text: String,
    val outcome: String,    // "recorded" | "not a payment"
)

/**
 * The last few payment-app notifications, kept only on the phone, so Settings can
 * show whether the listener receives anything and how each one was read.
 */
class NotificationLog(private val file: File, private val keep: Int = 20) {

    private val gson = Gson()

    @Synchronized
    fun add(entry: SeenNotification) {
        val trimmed = entry.copy(title = entry.title.take(120), text = entry.text.take(240))
        val kept = (readAll() + trimmed).takeLast(keep)
        file.writeText(kept.joinToString("") { gson.toJson(it) + "\n" })
    }

    /** Newest first. */
    @Synchronized
    fun recent(): List<SeenNotification> = readAll().reversed()

    @Synchronized
    fun clear() {
        file.delete()
    }

    private fun readAll(): List<SeenNotification> {
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull { line ->
            runCatching { gson.fromJson(line, SeenNotification::class.java) }.getOrNull()
        }
    }
}
