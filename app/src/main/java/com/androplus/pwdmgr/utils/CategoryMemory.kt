package com.androplus.pwdmgr.utils

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Categories the user chose for a payee. When you file "Kannan Store" under
 * Groceries once, every later SMS or notification from Kannan Store goes there.
 * Kept in a small JSON file on the phone (no database change).
 */
class CategoryMemory(private val file: File) {

    private val gson = Gson()
    private var map: MutableMap<String, String>? = null

    /** Remembers [category] for [payee]. Returns false for names too generic to learn. */
    @Synchronized
    fun learn(payee: String, isExpense: Boolean, category: String): Boolean {
        val k = key(payee, isExpense) ?: return false
        val m = load()
        if (m[k] == category) return true
        m[k] = category
        file.writeText(gson.toJson(m))
        return true
    }

    @Synchronized
    fun categoryFor(payee: String, isExpense: Boolean): String? =
        key(payee, isExpense)?.let { load()[it] }

    @Synchronized
    fun size(): Int = load().size

    @Synchronized
    fun clear() {
        map = mutableMapOf()
        file.delete()
    }

    private fun load(): MutableMap<String, String> {
        map?.let { return it }
        val loaded: MutableMap<String, String> = runCatching {
            if (!file.exists()) null
            else gson.fromJson<MutableMap<String, String>>(
                file.readText(), object : TypeToken<MutableMap<String, String>>() {}.type,
            )
        }.getOrNull() ?: mutableMapOf()
        map = loaded
        return loaded
    }

    companion object {
        /** Fallback names the parser uses when it finds no payee: never learned. */
        private val GENERIC = setOf(
            "upi payment", "upi transfer", "card payment", "bank transfer", "bank transaction",
            "transaction", "atm withdrawal", "payment",
        )

        /** "[SMS] KANNAN  STORE." and "Kannan Store" give the same key. */
        fun normalize(payee: String): String =
            payee.replace(Regex("""^\[[A-Za-z]+]\s*"""), "")
                .lowercase()
                .replace(Regex("""[^a-z0-9@]+"""), " ")
                .trim()

        fun key(payee: String, isExpense: Boolean): String? {
            val n = normalize(payee)
            if (n.length < 3 || n in GENERIC || n.all { it.isDigit() || it == ' ' }) return null
            return (if (isExpense) "out:" else "in:") + n
        }
    }
}
