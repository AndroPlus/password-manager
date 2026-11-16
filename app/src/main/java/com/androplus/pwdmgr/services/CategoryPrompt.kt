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
    private const val CHANNEL = "category_prompt"

    fun isEnabled(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREF_ASK, true)

    fun notificationId(payee: String): Int =
        (CategoryMemory.key(payee, true) ?: payee).hashCode()

    fun ask(context: Context, expense: ExpenseTransaction, payee: String, choices: List<String>) {
        if (!isEnabled(context)) return
        // Small payments don't interrupt: they wait in the Home banner instead.
        if (expense.amount < AppPrefs.promptMinAmount(context)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Category for new shops", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Asks once which category a new shop or person belongs to" }
            )
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
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

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
