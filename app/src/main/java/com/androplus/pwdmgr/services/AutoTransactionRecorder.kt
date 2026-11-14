package com.androplus.pwdmgr.services

import android.util.Log
import com.androplus.pwdmgr.model.ExpenseTransaction
import com.androplus.pwdmgr.utils.AutoReclassifier
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

    /** [at] is when the payment happened; past SMS imports pass the SMS's own time. */
    fun record(
        parsed: ParsedTransaction,
        source: String,
        rawText: String,
        at: Long = System.currentTimeMillis(),
    ): Job = scope.launch {
        mutex.withLock { save(parsed, source, rawText, at) }
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
        val matchedName = TransactionCategorizer.categorizeOrOther(
            parsed.merchant, rawText, parsed.isExpense, categories.map { it.name },
        )
        val category = categories.find { it.name == matchedName }

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

    /**
     * Re-reads every auto-captured entry with the current rules and fixes its
     * name, direction and category. Manual entries are never touched.
     * Returns how many entries changed.
     */
    suspend fun reclassifyAll(): Int = mutex.withLock {
        val realmService = RealmService.getInstance()
        val categories = realmService.getAllCategories()
        val names = categories.map { it.name }
        var changed = 0
        for (e in realmService.getAllExpenses()) {
            val source = DuplicateDetector.sourceOf(e.shortDescription) ?: continue
            val r = AutoReclassifier.reclassify(source, e.description, names) ?: continue
            val cat = categories.find { it.name == r.category } ?: continue
            val newShort = DuplicateDetector.tag(if (source == "Auto") "SMS" else source, r.merchant)
            val newType = if (r.isExpense) "EXPENSE" else "INCOME"
            if (newShort == e.shortDescription && cat.name == e.categoryName && newType == e.type) continue
            val updated = ExpenseTransaction().apply {
                _id = e._id
                amount = e.amount
                description = e.description
                date = e.date
                shortDescription = newShort
                type = newType
                categoryId = cat._id.toHexString()
                categoryName = cat.name
                categoryColor = cat.color
            }
            if (realmService.updateExpense(updated)) changed++
        }
        Log.d(TAG, "Re-checked categories: $changed changed")
        changed
    }
}
