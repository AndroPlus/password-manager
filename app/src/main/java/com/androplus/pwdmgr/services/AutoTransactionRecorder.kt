package com.androplus.pwdmgr.services

import android.content.Context
import android.util.Log
import com.androplus.pwdmgr.utils.CategoryMemory
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
import java.io.File

/** Saves transactions captured from bank SMS and payment-app notifications. */
object AutoTransactionRecorder {

    private const val TAG = "AutoTxnRecorder"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // A bank SMS and a GPay notification for one payment can arrive together.
    // Serialising check-then-save stops both passing the duplicate check.
    private val mutex = Mutex()

    /** Categories the user picked for a payee; used before the built-in rules. */
    @Volatile private var memory: CategoryMemory? = null

    fun init(context: Context) {
        if (memory == null) {
            memory = CategoryMemory(File(context.applicationContext.filesDir, "learned_categories.json"))
        }
    }

    fun learnedCount(): Int = memory?.size() ?: 0

    fun forgetLearned() {
        memory?.clear()
    }

    /**
     * The user filed [payee] under [categoryName]: remember it for future
     * SMS/notifications, and move other auto-captured entries from the same payee.
     * Returns how many other entries were moved, or null when the name is too
     * generic to learn (e.g. "UPI payment").
     */
    suspend fun learn(payee: String, isExpense: Boolean, categoryName: String): Int? = mutex.withLock {
        val mem = memory ?: return@withLock null
        if (!mem.learn(payee, isExpense, categoryName)) return@withLock null
        val key = CategoryMemory.key(payee, isExpense)
        val realmService = RealmService.getInstance()
        val cat = realmService.getAllCategories().find { it.name == categoryName } ?: return@withLock 0
        var moved = 0
        for (e in realmService.getAllExpenses()) {
            if (DuplicateDetector.sourceOf(e.shortDescription) == null) continue
            if (e.categoryName == categoryName) continue
            if (CategoryMemory.key(e.shortDescription, e.type == "EXPENSE") != key) continue
            val updated = copyOf(e).apply {
                categoryId = cat._id.toHexString()
                this.categoryName = cat.name
                categoryColor = cat.color
            }
            if (realmService.updateExpense(updated)) moved++
        }
        Log.d(TAG, "Learned category for a payee; moved $moved other entries")
        moved
    }

    private fun copyOf(e: ExpenseTransaction) = ExpenseTransaction().apply {
        _id = e._id
        amount = e.amount
        description = e.description
        shortDescription = e.shortDescription
        type = e.type
        date = e.date
        receiptPath = e.receiptPath
        categoryId = e.categoryId
        categoryName = e.categoryName
        categoryColor = e.categoryColor
    }

    /** The user's own choice for this payee if any, else the built-in rules. */
    private fun chooseCategory(merchant: String, rawText: String, isExpense: Boolean, names: List<String>): String? =
        memory?.categoryFor(merchant, isExpense)?.takeIf { it in names }
            ?: TransactionCategorizer.categorizeOrOther(merchant, rawText, isExpense, names)

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
        val matchedName = chooseCategory(parsed.merchant, rawText, parsed.isExpense, categories.map { it.name })
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
            val chosen = memory?.categoryFor(r.merchant, r.isExpense)?.takeIf { it in names } ?: r.category
            val cat = categories.find { it.name == chosen } ?: continue
            val newShort = DuplicateDetector.tag(if (source == "Auto") "SMS" else source, r.merchant)
            val newType = if (r.isExpense) "EXPENSE" else "INCOME"
            if (newShort == e.shortDescription && cat.name == e.categoryName && newType == e.type) continue
            val updated = copyOf(e).apply {
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
