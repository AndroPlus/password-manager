package com.androplus.pwdmgr.utils

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** One expense as stored in a backup (plain values, no database objects). */
data class BackupExpense(
    val id: String, val amount: Double, val type: String, val date: Long,
    val shortDescription: String, val description: String,
    val categoryName: String, val categoryColor: String, val receiptPath: String = "",
)

data class BackupCategory(val name: String, val icon: String, val color: String, val isDefault: Boolean)

data class BackupBudget(val categoryName: String, val amount: Double, val period: String)

/** Everything the Expense Tracker keeps, in one file. Passwords and todos are never included. */
data class BackupData(
    val format: String = FORMAT,
    val version: Int = 1,
    val createdAt: Long,
    val expenses: List<BackupExpense>,
    val categories: List<BackupCategory>,
    val budgets: List<BackupBudget>,
    /** Small JSON files the app keeps (learned categories, payee names, reminders). */
    val files: Map<String, String> = emptyMap(),
    /** A few settings (monthly budget, removed categories…). */
    val prefs: Map<String, String> = emptyMap(),
) {
    companion object { const val FORMAT = "expense-tracker-backup" }
}

class WrongPasswordException : Exception("Wrong password, or not an Expense Tracker backup")

/**
 * Password-protected backup files: AES-256-GCM with a key from PBKDF2 (SHA-256).
 * The file is a small JSON envelope, so it can be recognised and versioned.
 */
object BackupCrypto {
    private const val ITERATIONS = 120_000
    private val random = SecureRandom()
    private val gson = Gson()
    private val b64e = Base64.getEncoder()
    private val b64d = Base64.getDecoder()

    private fun key(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    fun encrypt(data: BackupData, password: String): ByteArray {
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        val sealed = cipher.doFinal(gson.toJson(data).toByteArray(Charsets.UTF_8))
        val envelope = JsonObject().apply {
            addProperty("format", BackupData.FORMAT)
            addProperty("v", 1)
            addProperty("createdAt", data.createdAt)
            addProperty("expenses", data.expenses.size)
            addProperty("salt", b64e.encodeToString(salt))
            addProperty("iv", b64e.encodeToString(iv))
            addProperty("data", b64e.encodeToString(sealed))
        }
        return envelope.toString().toByteArray(Charsets.UTF_8)
    }

    /** Throws [WrongPasswordException] for a wrong password or a file that isn't a backup. */
    fun decrypt(file: ByteArray, password: String): BackupData {
        val env = runCatching { JsonParser.parseString(String(file, Charsets.UTF_8)).asJsonObject }
            .getOrNull()?.takeIf { it.get("format")?.asString == BackupData.FORMAT }
            ?: throw WrongPasswordException()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, b64d.decode(env.get("salt").asString)),
            GCMParameterSpec(128, b64d.decode(env.get("iv").asString)))
        val plain = try {
            cipher.doFinal(b64d.decode(env.get("data").asString))
        } catch (e: AEADBadTagException) {
            throw WrongPasswordException()
        }
        return gson.fromJson(String(plain, Charsets.UTF_8), BackupData::class.java)
    }

    /** Creation time and expense count, readable without the password. */
    fun summary(file: ByteArray): Pair<Long, Int>? = runCatching {
        val env = JsonParser.parseString(String(file, Charsets.UTF_8)).asJsonObject
        if (env.get("format")?.asString != BackupData.FORMAT) null
        else env.get("createdAt").asLong to env.get("expenses").asInt
    }.getOrNull()
}

/** Merging a restored backup into what's on the phone: nothing on the phone is lost. */
object BackupMerge {
    private val gson = com.google.gson.GsonBuilder().disableHtmlEscaping().create()

    /** Key/value JSON files (learned categories, payee names): the phone's own values win. */
    fun mergeMaps(local: String?, incoming: String?): String {
        val type = object : TypeToken<Map<String, String>>() {}.type
        val a: Map<String, String> = local?.let { runCatching { gson.fromJson<Map<String, String>>(it, type) }.getOrNull() }.orEmpty()
        val b: Map<String, String> = incoming?.let { runCatching { gson.fromJson<Map<String, String>>(it, type) }.getOrNull() }.orEmpty()
        return gson.toJson(b + a)
    }

    /** Reminder lists: union by id, the phone's own version wins. */
    fun mergeReminders(local: String?, incoming: String?): String {
        val type = object : TypeToken<List<Reminder>>() {}.type
        val a: List<Reminder> = local?.let { runCatching { gson.fromJson<List<Reminder>>(it, type) }.getOrNull() }.orEmpty()
        val b: List<Reminder> = incoming?.let { runCatching { gson.fromJson<List<Reminder>>(it, type) }.getOrNull() }.orEmpty()
        val ids = a.map { it.id }.toSet()
        return gson.toJson(a + b.filter { it.id !in ids })
    }
}
