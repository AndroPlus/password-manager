package com.androplus.pwdmgr.services

import android.content.Context

/** Names of default categories the user removed, so app start doesn't add them back. */
object RemovedCategories {
    private const val PREFS = "MyPrefs"
    private const val KEY = "removed_categories"

    fun get(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY, emptySet()) ?: emptySet()

    fun add(context: Context, name: String) = update(context) { it + name.lowercase() }

    /** The user created a category with this name again. */
    fun remove(context: Context, name: String) = update(context) { it - name.lowercase() }

    private fun update(context: Context, change: (Set<String>) -> Set<String>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY, HashSet(change(get(context)))).apply()
    }
}
