package com.androplus.pwdmgr.fragment

import android.app.AlertDialog
import android.content.Context
import android.net.Uri
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.androplus.pwdmgr.services.CloudBackup
import com.androplus.pwdmgr.utils.BackupCrypto
import com.androplus.pwdmgr.utils.WrongPasswordException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Dialogs shared by Settings and first-run setup for backing up and restoring. */
object BackupUi {

    private val when_ = SimpleDateFormat("d MMM yyyy, h:mm a", Locale("en", "IN"))

    private fun passwordField(ctx: Context, hint: String) = EditText(ctx).apply {
        this.hint = hint
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }

    private fun padded(ctx: Context, vararg views: android.view.View): FrameLayout {
        val pad = (20 * ctx.resources.displayMetrics.density).toInt()
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; views.forEach { addView(it) } }
        return FrameLayout(ctx).apply { setPadding(pad, pad / 2, pad, 0); addView(col) }
    }

    /** Asks for a new backup password (twice), then calls [onSet]. */
    fun askNewPassword(fragment: Fragment, onSet: (String) -> Unit) {
        val ctx = fragment.requireContext()
        val first = passwordField(ctx, "Backup password (min 6)")
        val second = passwordField(ctx, "Repeat password")
        AlertDialog.Builder(ctx)
            .setTitle("Protect your backup")
            .setMessage("The backup is encrypted with this password. You'll need it to restore. " +
                "If you forget it, the backup can't be opened.")
            .setView(padded(ctx, first, second))
            .setPositiveButton("Continue") { _, _ ->
                val p = first.text.toString()
                when {
                    p.length < 6 -> Toast.makeText(ctx, "Use at least 6 characters", Toast.LENGTH_LONG).show()
                    p != second.text.toString() -> Toast.makeText(ctx, "The passwords don't match", Toast.LENGTH_LONG).show()
                    else -> onSet(p)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Backs up in the background and reports the result. */
    fun backupNow(fragment: Fragment, onDone: () -> Unit = {}) {
        val app = fragment.requireContext().applicationContext
        Toast.makeText(app, "Backing up…", Toast.LENGTH_SHORT).show()
        fragment.lifecycleScope.launch {
            val error = withContext(Dispatchers.IO) { CloudBackup.backupNow(app) }
            Toast.makeText(app, error ?: "Backup saved", Toast.LENGTH_LONG).show()
            onDone()
        }
    }

    /** Reads the picked file, asks its password, and merges it in. */
    fun restoreFrom(fragment: Fragment, uri: Uri, onDone: (Boolean) -> Unit = {}) {
        val ctx = fragment.requireContext()
        val bytes = runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        val summary = bytes?.let { BackupCrypto.summary(it) }
        if (bytes == null || summary == null) {
            Toast.makeText(ctx, "That file isn't an Expense Tracker backup", Toast.LENGTH_LONG).show()
            onDone(false)
            return
        }
        val field = passwordField(ctx, "Backup password")
        AlertDialog.Builder(ctx)
            .setTitle("Restore backup")
            .setMessage("Backup from ${when_.format(Date(summary.first))} with ${summary.second} transactions. " +
                "Nothing on this phone is removed; only missing entries are added.")
            .setView(padded(ctx, field))
            .setPositiveButton("Restore") { _, _ ->
                val app = ctx.applicationContext
                val password = field.text.toString()
                fragment.lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { CloudBackup.restore(app, bytes, password) }
                    }
                    result.onSuccess {
                        Toast.makeText(app, "Restored ${it.added} transactions" +
                            (if (it.skipped > 0) " (${it.skipped} were already here)" else ""), Toast.LENGTH_LONG).show()
                        // Keep backing up to the same file with the same password.
                        if (CloudBackup.location(app) == null) CloudBackup.setLocation(app, uri, password)
                        onDone(true)
                    }.onFailure {
                        Toast.makeText(app, if (it is WrongPasswordException) "Wrong password" else
                            "Restore failed: ${it.message}", Toast.LENGTH_LONG).show()
                        onDone(false)
                    }
                }
            }
            .setNegativeButton("Cancel") { _, _ -> onDone(false) }
            .show()
    }

    fun status(ctx: Context): String {
        val name = CloudBackup.locationName(ctx) ?: return "Not set up. Tap to choose where to keep backups (Google Drive recommended)."
        // Saved location but no permission to write it (after a reinstall): say so, don't wait for a failed backup.
        if (!CloudBackup.hasAccess(ctx)) return "$name · ${CloudBackup.LOST_ACCESS}"
        val last = CloudBackup.lastBackup(ctx)
        val error = CloudBackup.lastError(ctx)
        return "$name · " + when {
            error != null -> "last attempt failed: $error"
            last > 0 -> "last backup ${when_.format(Date(last))}"
            else -> "no backup yet"
        }
    }
}
