package com.androplus.pwdmgr.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.androplus.pwdmgr.model.ExpenseBudget
import com.androplus.pwdmgr.model.ExpenseCategory
import com.androplus.pwdmgr.model.ExpenseTransaction
import com.androplus.pwdmgr.utils.BackupBudget
import com.androplus.pwdmgr.utils.BackupCategory
import com.androplus.pwdmgr.utils.BackupCrypto
import com.androplus.pwdmgr.utils.BackupData
import com.androplus.pwdmgr.utils.BackupExpense
import com.androplus.pwdmgr.utils.BackupMerge
import com.androplus.pwdmgr.worker.BackupWorker
import org.mongodb.kbson.ObjectId
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * WhatsApp-style backup: one password-protected file in a place the user picks
 * once (Google Drive through Android's file picker, or any folder), updated by
 * "Back up now" and once a day. Restore merges it back after a reinstall.
 * Only expense data: passwords and todos are never included.
 */
object CloudBackup {

    private const val KEY_URI = "backup_uri"
    private const val KEY_PASSWORD = "backup_password"
    const val KEY_AUTO = "backup_auto"
    private const val KEY_LAST = "backup_last"
    private const val KEY_LAST_ERROR = "backup_last_error"
    const val DEFAULT_FILE_NAME = "ExpenseTracker-backup.etb"

    private val FILES = listOf("learned_categories.json", "payee_names.json", "bill_reminders.json")
    private val PREF_KEYS = listOf("monthly_budget", "prompt_min_amount")

    private fun prefs(c: Context) = PreferenceManager.getDefaultSharedPreferences(c)

    fun location(c: Context): Uri? = prefs(c).getString(KEY_URI, null)?.let(Uri::parse)
    fun lastBackup(c: Context): Long = prefs(c).getLong(KEY_LAST, 0)
    fun lastError(c: Context): String? = prefs(c).getString(KEY_LAST_ERROR, null)
    fun isAuto(c: Context): Boolean = prefs(c).getBoolean(KEY_AUTO, true)

    /** The file name and its app (e.g. "Drive") as the picker reports it. */
    fun locationName(c: Context): String? = location(c)?.let { uri ->
        runCatching {
            c.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment
    }

    /** Remembers the picked file (keeping access across restarts) and the password, then backs up. */
    fun setLocation(c: Context, uri: Uri, password: String) {
        runCatching {
            c.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        prefs(c).edit().putString(KEY_URI, uri.toString()).putString(KEY_PASSWORD, password).apply()
        schedule(c)
    }

    fun schedule(c: Context) {
        val wm = WorkManager.getInstance(c)
        if (location(c) == null || !isAuto(c)) {
            wm.cancelUniqueWork(BackupWorker.WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        wm.enqueueUniquePeriodicWork(BackupWorker.WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Collects everything into one [BackupData]. */
    fun collect(c: Context): BackupData {
        val realm = RealmService.getInstance()
        val categories = realm.getAllCategories()
        val byId = categories.associateBy { it._id.toHexString() }
        val dir = c.applicationContext.filesDir
        return BackupData(
            createdAt = System.currentTimeMillis(),
            expenses = realm.getAllExpenses().map {
                BackupExpense(it._id.toHexString(), it.amount, it.type, it.date, it.shortDescription,
                    it.description, it.categoryName, it.categoryColor, it.receiptPath)
            },
            categories = categories.map { BackupCategory(it.name, it.icon, it.color, it.isDefault) },
            budgets = realm.getAllBudgets().mapNotNull { b ->
                byId[b.categoryId]?.let { BackupBudget(it.name, b.amount, b.period) }
            },
            files = FILES.mapNotNull { name -> File(dir, name).takeIf { it.exists() }?.let { name to it.readText() } }.toMap(),
            prefs = PREF_KEYS.mapNotNull { k -> prefs(c).getString(k, null)?.let { k to it } }.toMap() +
                ("removed_categories" to RemovedCategories.get(c).joinToString("\n")),
        )
    }

    /** Writes the backup to the chosen place. Returns null on success, else a message. */
    fun backupNow(c: Context): String? {
        val uri = location(c) ?: return "Choose where to keep backups first."
        val password = prefs(c).getString(KEY_PASSWORD, null) ?: return "Set a backup password first."
        val error = runCatching {
            val bytes = BackupCrypto.encrypt(collect(c), password)
            c.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                ?: error("The backup location can't be opened")
        }.exceptionOrNull()?.let { it.message ?: "Backup failed" }
        prefs(c).edit().apply {
            if (error == null) putLong(KEY_LAST, System.currentTimeMillis()).remove(KEY_LAST_ERROR)
            else putString(KEY_LAST_ERROR, error)
        }.apply()
        return error
    }

    data class RestoreResult(val added: Int, val skipped: Int, val categories: Int)

    /**
     * Adds what the backup has and the phone doesn't. Payments already on the phone
     * (same id) are skipped; the phone's own settings and names win.
     * Throws [com.androplus.pwdmgr.utils.WrongPasswordException].
     */
    suspend fun restore(c: Context, bytes: ByteArray, password: String): RestoreResult {
        val data = BackupCrypto.decrypt(bytes, password)
        val realm = RealmService.getInstance()

        // Categories by name.
        val have = realm.getAllCategories().map { it.name.lowercase() }.toSet()
        var newCats = 0
        for (cat in data.categories) {
            if (cat.name.lowercase() in have) continue
            if (realm.createCategory(ExpenseCategory().apply {
                    name = cat.name; icon = cat.icon; color = cat.color; isDefault = cat.isDefault
                })) newCats++
        }
        val cats = realm.getAllCategories().associateBy { it.name }

        // Payments by id.
        val existing = realm.getAllExpenses().map { it._id.toHexString() }.toSet()
        var added = 0
        var skipped = 0
        for (e in data.expenses) {
            if (e.id in existing) { skipped++; continue }
            val cat = cats[e.categoryName]
            val ok = realm.createExpense(ExpenseTransaction().apply {
                runCatching { _id = ObjectId(e.id) }
                amount = e.amount; type = e.type; date = e.date
                shortDescription = e.shortDescription; description = e.description
                receiptPath = e.receiptPath
                categoryName = cat?.name ?: e.categoryName
                categoryColor = cat?.color ?: e.categoryColor
                categoryId = cat?._id?.toHexString() ?: ""
            })
            if (ok) added++ else skipped++
        }

        // Budgets for categories that have none.
        val budgeted = realm.getAllBudgets().map { it.categoryId }.toSet()
        for (b in data.budgets) {
            val cat = cats[b.categoryName] ?: continue
            if (cat._id.toHexString() in budgeted) continue
            realm.setBudget(ExpenseBudget().apply {
                categoryId = cat._id.toHexString(); amount = b.amount; period = b.period
            })
        }

        // Learned categories, payee names, reminders: merged, the phone's own entries win.
        val dir = c.applicationContext.filesDir
        for ((name, incoming) in data.files) {
            if (name !in FILES) continue
            val f = File(dir, name)
            val local = f.takeIf { it.exists() }?.readText()
            f.writeText(if (name == "bill_reminders.json") BackupMerge.mergeReminders(local, incoming)
                        else BackupMerge.mergeMaps(local, incoming))
        }
        AutoTransactionRecorder.reload(c)
        Nicknames.reload(c)

        // Settings only where the phone has none yet.
        val p = prefs(c).edit()
        for ((k, v) in data.prefs) {
            if (k == "removed_categories") {
                v.split('\n').filter { it.isNotBlank() }.forEach { RemovedCategories.add(c, it) }
            } else if (k in PREF_KEYS && prefs(c).getString(k, null) == null) {
                p.putString(k, v)
            }
        }
        p.apply()
        return RestoreResult(added, skipped, newCats)
    }
}
