package com.androplus.pwdmgr.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.androplus.pwdmgr.MainActivity
import com.androplus.pwdmgr.R
import com.androplus.pwdmgr.model.ExpenseTransaction
import com.androplus.pwdmgr.utils.CategoryMemory
import com.androplus.pwdmgr.utils.ExpenseGrouping
import java.text.NumberFormat
import java.util.Locale

/**
 * "₹250 to Sri Ganesh Stores — Groceries | Food & Dining | Other…"
 * Shown once per new payee; a tap files it and the app remembers that payee.
 */
object CategoryPrompt {

    const val PREF_ASK = "ask_category_for_new_payees"
    const val EXTRA_EDIT_EXPENSE_ID = "edit_expense_id"
    const val EXTRA_OPEN_PAYEE_SORT = "open_payee_sort"
    // v2: high importance so it pops up; the first channel was created at default
    // importance, and Android keeps a channel's importance once created.
    private const val CHANNEL = "category_prompt_v2"
    private const val OLD_CHANNEL = "category_prompt"

    fun isEnabled(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREF_ASK, true)

    fun notificationId(payee: String): Int =
        (CategoryMemory.key(payee, true) ?: payee).hashCode()

    /** Why prompts can't be shown right now, in words for the user; null when they can. */
    fun blockedReason(context: Context): String? {
        if (!isEnabled(context)) return "Turned off: switch on \"Ask category for new payees\"."
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return "Notifications are not allowed for this app. Tap to allow them."
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled())
            return "Notifications are switched off for this app. Tap to switch them on."
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE)
                return "The \"Category for new payees\" notification type is switched off. Tap to switch it on."
        }
        return null
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.deleteNotificationChannel(OLD_CHANNEL)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Category for new payees", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Asks once which category a new shop or person belongs to" }
        )
    }

    /** A sample prompt, so the user can see what it looks like and that it arrives. */
    fun sendTest(context: Context) {
        ensureChannel(context)
        val id = 4242
        val open = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("₹250 to Test Shop")
            .setContentText("Test: this is how a new payee asks for its category.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        listOf("Groceries", "Food & Dining").forEachIndexed { i, label ->
            val pi = PendingIntent.getBroadcast(
                context, id * 4 + i,
                Intent(context, CategoryActionReceiver::class.java)
                    .putExtra(CategoryActionReceiver.EXTRA_TEST, true)
                    .putExtra(CategoryActionReceiver.EXTRA_NOTIFICATION_ID, id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, label, pi)
        }
        builder.addAction(0, "Other…", open)
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    fun ask(context: Context, expense: ExpenseTransaction, payee: String, choices: List<String>) {
        // Small payments don't interrupt: they wait in the Home banner instead.
        if (expense.amount < AppPrefs.promptMinAmount(context)) {
            android.util.Log.d("CategoryPrompt", "Not asking: below the minimum amount")
            return
        }
        blockedReason(context)?.let {
            android.util.Log.d("CategoryPrompt", "Not asking: $it")
            return
        }

        val id = notificationId(payee)
        val name = ExpenseGrouping.displayTitle(payee, expense.categoryName)
        val amount = NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(expense.amount)
        val expenseId = expense._id.toHexString()

        val openEditor = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_EDIT_EXPENSE_ID, expenseId)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$amount to $name")
            .setContentText("Saved under ${expense.categoryName.ifBlank { "Other" }}. Pick its category once; the app will remember.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "Saved under ${expense.categoryName.ifBlank { "Other" }}. " +
                    "Pick the right category once — later payments to $name will go there automatically."))
            .setContentIntent(openEditor)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        choices.forEachIndexed { i, category ->
            val pi = PendingIntent.getBroadcast(
                context, id * 4 + i,
                Intent(context, CategoryActionReceiver::class.java)
                    .putExtra(CategoryActionReceiver.EXTRA_PAYEE, payee)
                    .putExtra(CategoryActionReceiver.EXTRA_CATEGORY, category)
                    .putExtra(CategoryActionReceiver.EXTRA_NOTIFICATION_ID, id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, category, pi)
        }
        builder.addAction(0, "Other…", openEditor)

        NotificationManagerCompat.from(context).notify(id, builder.build())
    }
}
