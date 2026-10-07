package com.androplus.pwdmgr.services

import android.util.Log
import com.androplus.pwdmgr.model.ExpenseTransaction
import com.androplus.pwdmgr.utils.AutoTxn
import com.androplus.pwdmgr.utils.DuplicateDetector
import com.androplus.pwdmgr.utils.ParsedTransaction
import com.androplus.pwdmgr.utils.TransactionCategorizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Saves transactions captured from bank SMS and payment-app notifications. */
object AutoTransactionRecorder {

    private const val TAG = "AutoTxnRecorder"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // A bank SMS and a GPay notification for one payment can arrive together.
    // Serialising check-then-save stops both passing the duplicate check.
    private val mutex = Mutex()

    fun record(parsed: ParsedTransaction, source: String, rawText: String): Job =
        scope.launch {
            mutex.withLock { save(parsed, source, rawText, System.currentTimeMillis()) }
        }

    private suspend fun save(parsed: ParsedTransaction, source: String, rawText: String, now: Long) {
        val realmService = RealmService.getInstance()

        val recent = realmService
            .getExpensesByDateRange(now - DuplicateDetector.WINDOW_MILLIS, now + DuplicateDetector.WINDOW_MILLIS)
            .mapNotNull { e ->
                val src = DuplicateDetector.sourceOf(e.shortDescription) ?: return@mapNotNull null
                AutoTxn(e.amount, e.type == "EXPENSE", e.date, src, e.description)
            }
        val candidate = AutoTxn(parsed.amount, parsed.isExpense, now, source, rawText)
        if (DuplicateDetector.isDuplicate(candidate, recent)) {
            Log.d(TAG, "Skipped duplicate $source transaction")
            return
        }

        val categories = realmService.getAllCategories()
        val matchedName = TransactionCategorizer.categorize(
            parsed.merchant, rawText, parsed.isExpense, categories.map { it.name },
        )
        val category = categories.find { it.name == matchedName }
            ?: categories.find { it.name.contains("Other", true) }
            ?: categories.find { it.name.contains("Shopping", true) }
            ?: categories.firstOrNull()

        val expense = ExpenseTransaction().apply {
            amount = parsed.amount
            shortDescription = DuplicateDetector.tag(source, parsed.merchant)
            description = rawText
            type = if (parsed.isExpense) "EXPENSE" else "INCOME"
            date = now
            if (category != null) {
                categoryId = category._id.toHexString()
                categoryName = category.name
                categoryColor = category.color
            }
        }
        val saved = realmService.createExpense(expense)
        Log.d(TAG, "Saved $source transaction: $saved")
    }
}
