package com.androplus.pwdmgr.utils

/** Default category colours, and the older ones they replace. */
object CategoryPalette {

    /** Distinct on white, readable as text on a 15% tint of themselves. */
    val COLORS: Map<String, String> = linkedMapOf(
        "Food & Dining" to "#2563EB",
        "Groceries" to "#0F766E",
        "Transportation" to "#D97706",
        "Bills & Utilities" to "#7C3AED",
        "Entertainment" to "#DB2777",
        "Housing" to "#92400E",
        "Healthcare" to "#DC2626",
        "Education" to "#4F46E5",
        "Shopping" to "#EA580C",
        "Insurance & Savings" to "#059669",
        "Cash" to "#57534E",
        "Income" to "#16A34A",
        "Transfers" to "#6B7280",
        "Card Bill" to "#475569",
        "Other" to "#737373",
    )

    /** Colours earlier versions gave the same defaults. */
    val OLD: Map<String, String> = mapOf(
        "Food & Dining" to "#FF6B6B",
        "Groceries" to "#8BC34A",
        "Transportation" to "#4ECDC4",
        "Bills & Utilities" to "#FF9800",
        "Entertainment" to "#95E1D3",
        "Housing" to "#F38181",
        "Healthcare" to "#AA96DA",
        "Education" to "#3F51B5",
        "Shopping" to "#FCBAD3",
        "Insurance & Savings" to "#009688",
        "Cash" to "#795548",
        "Income" to "#4CAF50",
        "Transfers" to "#9E9E9E",
        "Card Bill" to "#607D8B",
        "Other" to "#BDBDBD",
    )

    /** New colour for a default category still wearing its old one; null otherwise. */
    fun upgrade(name: String, currentColor: String): String? {
        val old = OLD[name] ?: return null
        return if (old.equals(currentColor, ignoreCase = true)) COLORS[name] else null
    }

    /** Icon key for a category name (custom names matched by keyword). */
    fun iconKey(name: String): String {
        val n = name.lowercase()
        return when {
            "grocer" in n -> "groceries"
            "food" in n || "dining" in n || "restaurant" in n || "tea" in n -> "food"
            "fuel" in n || "petrol" in n -> "fuel"
            "transport" in n || "travel" in n || "cab" in n -> "transport"
            "bill" in n && "card" in n -> "card"
            "bill" in n || "utilit" in n || "recharge" in n -> "bills"
            "entertain" in n || "movie" in n || "fun" in n -> "fun"
            "hous" in n || "rent" in n || "home" in n -> "home"
            "health" in n || "medic" in n || "pharm" in n -> "health"
            "educat" in n || "school" in n -> "education"
            "shop" in n -> "shopping"
            "saving" in n || "insur" in n || "invest" in n -> "savings"
            "cash" in n || "atm" in n -> "cash"
            "income" in n || "salary" in n -> "income"
            "transfer" in n -> "transfer"
            "card" in n -> "card"
            else -> "other"
        }
    }
}
