package com.androplus.pwdmgr.utils

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed class UploadResult {
    /** Server stored these ids (added, duplicate, ignored or invalid): drop them from the queue. */
    data class Done(val ids: Set<String>) : UploadResult()
    /** Wrong token or server not configured for uploads: retrying won't help. */
    object Unauthorized : UploadResult()
    /** Network error, server asleep or failing: keep the messages and try later. */
    data class RetryLater(val reason: String) : UploadResult()
}

/** Sends queued messages to the FinIntel server's POST /api/ingest. */
object UploadClient {

    private val gson = Gson()

    /** Only https, so bank messages never travel unencrypted. */
    fun isValidServerUrl(url: String): Boolean {
        val u = url.trim()
        return u.startsWith("https://") && u.length > "https://".length && " " !in u
    }

    fun upload(
        serverUrl: String,
        token: String,
        messages: List<OutboundMessage>,
        allowHttpForTests: Boolean = false,
    ): UploadResult {
        if (messages.isEmpty()) return UploadResult.Done(emptySet())
        val base = serverUrl.trim().trimEnd('/')
        if (!isValidServerUrl(base) && !(allowHttpForTests && base.startsWith("http://"))) {
            return UploadResult.Unauthorized
        }
        val conn = try {
            URL("$base/api/ingest").openConnection() as HttpURLConnection
        } catch (e: IOException) {
            return UploadResult.RetryLater("bad URL: ${e.message}")
        }
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 30_000
            conn.readTimeout = 90_000          // free hosting can take ~a minute to wake up
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("X-FinIntel-Token", token.trim())
            val body = gson.toJson(mapOf("messages" to messages)).toByteArray(Charsets.UTF_8)
            conn.outputStream.use { it.write(body) }

            when (val code = conn.responseCode) {
                200 -> {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    val results = JsonParser.parseString(text).asJsonObject.getAsJsonObject("results")
                    UploadResult.Done(results?.keySet()?.toSet() ?: emptySet())
                }
                401, 403 -> UploadResult.Unauthorized
                else -> UploadResult.RetryLater("HTTP $code")
            }
        } catch (e: Exception) {
            UploadResult.RetryLater(e.javaClass.simpleName)
        } finally {
            conn.disconnect()
        }
    }
}
