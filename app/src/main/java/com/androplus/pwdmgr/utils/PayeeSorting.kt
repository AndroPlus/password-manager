package com.androplus.pwdmgr.utils

/** One auto-captured spend, as the payee list needs it. */
data class PayeeTxn(
    val payee: String,          // short description, e.g. "[SMS] KANNAN STORE"
    val amount: Double,
    val isExpense: Boolean,
    val categoryName: String,
    val timeMillis: Long,
)

/** A shop or person you paid that you haven't given a category yet. */
data class PayeeSummary(
    val payee: String,          // newest short description, used to learn
    val name: String,           // clean display name
    val count: Int,
    val total: Double,
    val categoryName: String,   // category of the newest payment (the app's guess)
)

object PayeeSorting {

    /** Categories never offered as a quick choice: not shop spending. */
    private val NOT_A_CHOICE = setOf("income", "transfers", "card bill", "other")

    /**
     * Payees you paid (money out) that have no learned category, biggest total first.
     * Generic names ("UPI payment") and transfers / card bills are left out.
     */
    fun unsorted(txns: List<PayeeTxn>, isLearned: (String) -> Boolean): List<PayeeSummary> =
        txns.asSequence()
            .filter { it.isExpense && it.categoryName !in ExpenseGrouping.NOT_COUNTED }
            .mapNotNull { t -> CategoryMemory.key(t.payee, true)?.let { it to t } }
            .filterNot { (key, _) -> isLearned(key) }
            .groupBy({ it.first }, { it.second })
            .map { (_, items) ->
                val newest = items.maxBy { it.timeMillis }
                PayeeSummary(
                    payee = newest.payee,
                    name = ExpenseGrouping.displayTitle(newest.payee, newest.categoryName),
                    count = items.size,
                    total = items.sumOf { it.amount },
                    categoryName = newest.categoryName,
                )
            }
            .sortedByDescending { it.total }

    /**
     * Up to [max] quick choices for a new payee: the app's guess first (unless
     * it is just "Other"), then the categories you use most for spending.
     */
    fun quickChoices(guess: String?, pastSpendCategories: List<String>, available: List<String>, max: Int = 2): List<String> {
        val ok = available.toSet()
        val out = LinkedHashSet<String>()
        if (guess != null && guess in ok && guess.lowercase() !in NOT_A_CHOICE) out += guess
        pastSpendCategories
            .filter { it in ok && it.lowercase() !in NOT_A_CHOICE }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .forEach { if (out.size < max) out += it.key }
        for (fallback in listOf("Food & Dining", "Groceries", "Shopping")) {
            if (out.size >= max) break
            if (fallback in ok) out += fallback
        }
        return out.take(max)
    }
}
