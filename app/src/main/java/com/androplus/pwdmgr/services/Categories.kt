package com.androplus.pwdmgr.services

import android.content.Context
import com.androplus.pwdmgr.model.ExpenseCategory
import com.androplus.pwdmgr.utils.CategoryPalette

/** Creating a category on the spot (from a notification, the edit screen or the payee sheet). */
object Categories {

    /** Colours for new categories, used in turn so neighbours differ. */
    private val EXTRA_COLORS = listOf(
        "#0891B2", "#9333EA", "#CA8A04", "#BE123C", "#4D7C0F", "#1D4ED8", "#C2410C", "#0F766E",
    )

    /**
     * The category called [rawName], creating it if it doesn't exist yet (names
     * compare without case). Returns its exact name, or null for a blank name.
     */
    suspend fun ensure(context: Context, rawName: String): String? {
        val name = rawName.trim().replace(Regex("""\s+"""), " ").take(30)
        if (name.isEmpty()) return null
        val realm = RealmService.getInstance()
        val existing = realm.getAllCategories()
        existing.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { return it.name }
        val used = existing.map { it.color.uppercase() }.toSet()
        val color = CategoryPalette.COLORS[name]
            ?: EXTRA_COLORS.firstOrNull { it.uppercase() !in used }
            ?: EXTRA_COLORS[existing.size % EXTRA_COLORS.size]
        val ok = realm.createCategory(ExpenseCategory().apply {
            this.name = name
            this.color = color
            this.icon = CategoryPalette.iconKey(name)
            isDefault = false
        })
        if (!ok) return null
        RemovedCategories.remove(context, name)
        return name
    }
}
