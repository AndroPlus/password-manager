package com.androplus.pwdmgr.utils

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Your own names for payees, e.g. "zpgl" → "KFC". The name from the bank
 * message stays the payee's identity (it is what categories are learned by);
 * this only changes what the app shows. One name per payee, so every payment
 * to it, past and future, shows the same name. Kept on the phone.
 */
class PayeeNames(private val file: File) {

    private val gson = Gson()
    private var map: MutableMap<String, String>? = null

    /** Sets or, with a blank/null [name], clears the name. False for generic payees. */
    @Synchronized
    fun set(payee: String, name: String?): Boolean {
        val k = key(payee) ?: return false
        val m = load()
        val clean = name?.trim()?.takeIf { it.isNotEmpty() }
        if (clean == null) m.remove(k) else m[k] = clean.take(40)
        file.writeText(gson.toJson(m))
        return true
    }

    @Synchronized
    fun get(payee: String): String? = key(payee)?.let { load()[it] }

    private fun key(payee: String): String? =
        CategoryMemory.key(payee, true)?.removePrefix("out:")

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
}
