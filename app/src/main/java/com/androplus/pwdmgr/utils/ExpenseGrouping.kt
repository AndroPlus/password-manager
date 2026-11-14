package com.androplus.pwdmgr.utils

/** One transaction as the list shows it: a clean name, never the raw SMS text. */
data class ExpenseRow(
    val id: String,
    val title: String,
    val source: String?,        // "SMS", "GPay", ... ; null for a manual entry
    val amount: Double,
    val isIncome: Boolean,
    val timeMillis: Long,
    val categoryName: String,
    val categoryColor: String,
)

data class CategoryGroup(
    val name: String,
    val color: String,
    val rows: List<ExpenseRow>,
) {
    val spent: Double get() = rows.filterNot { it.isIncome }.sumOf { it.amount }
    val received: Double get() = rows.filter { it.isIncome }.sumOf { it.amount }
}

object ExpenseGrouping {

    const val UNCATEGORIZED = "Uncategorized"

    /**
     * "[GPay] swiggy" → "Swiggy". Auto entries carry a source tag and often a
     * lower-case UPI handle; manual entries are shown as the user typed them.
     */
    fun displayTitle(shortDescription: String, categoryName: String): String {
        val source = DuplicateDetector.sourceOf(shortDescription)
        var name = shortDescription.replace(Regex("""^\[[A-Za-z]+]\s*"""), "").trim()
        if (name.isEmpty()) return categoryName.ifBlank { "Transaction" }
        if (source != null && name == name.lowercase()) {
            // UPI handles: "swiggy.in" / "hp_pay" → "Swiggy In" / "Hp Pay"
            name = name.split(Regex("""[\s._-]+""")).filter { it.isNotBlank() }
                .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
        }
        return name
    }

    /**
     * Groups by category: biggest spending first, then categories with only
     * income. Inside a group, newest first.
     */
    fun group(rows: List<ExpenseRow>): List<CategoryGroup> =
        rows.groupBy { it.categoryName.ifBlank { UNCATEGORIZED } }
            .map { (name, items) ->
                CategoryGroup(
                    name = name,
                    color = items.first().categoryColor,
                    rows = items.sortedByDescending { it.timeMillis },
                )
            }
            .sortedWith(compareByDescending<CategoryGroup> { it.spent }.thenByDescending { it.received })
}
