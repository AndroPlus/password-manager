package com.androplus.pwdmgr.utils

import com.google.gson.Gson
import java.io.File

/**
 * Messages waiting to be uploaded, one JSON object per line in a private file.
 *
 * A plain file rather than Realm: adding a Realm model would trigger
 * deleteRealmIfMigrationNeeded() and wipe the password vault.
 */
class OutboxStore(private val file: File, private val maxMessages: Int = 5000) {

    private val gson = Gson()

    @Synchronized
    fun enqueue(message: OutboundMessage) {
        val existing = readAll()
        if (existing.any { it.id == message.id }) return   // same message delivered twice
        // If the server is unreachable for weeks, drop the oldest rather than grow forever.
        val kept = (existing + message).takeLast(maxMessages)
        if (kept.size == existing.size + 1) {
            file.appendText(gson.toJson(message) + "\n")
        } else {
            rewrite(kept)
        }
    }

    @Synchronized
    fun peek(limit: Int): List<OutboundMessage> = readAll().take(limit)

    @Synchronized
    fun remove(ids: Set<String>) {
        if (ids.isEmpty()) return
        rewrite(readAll().filterNot { it.id in ids })
    }

    @Synchronized
    fun size(): Int = readAll().size

    private fun readAll(): List<OutboundMessage> {
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull { line ->
            if (line.isBlank()) null
            else runCatching { gson.fromJson(line, OutboundMessage::class.java) }.getOrNull()
        }
    }

    private fun rewrite(messages: List<OutboundMessage>) {
        // Write to a temp file, then rename, so a crash mid-write can't corrupt the queue.
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(messages.joinToString("") { gson.toJson(it) + "\n" })
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }
}
