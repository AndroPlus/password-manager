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
import com.androplus.pwdmgr.ui.CategoryStyle
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
        val testPick = { _: String, i: Int ->
            PendingIntent.getBroadcast(
                context, id * 8 + i,
                Intent(context, CategoryActionReceiver::class.java)
                    .putExtra(CategoryActionReceiver.EXTRA_TEST, true)
                    .putExtra(CategoryActionReceiver.EXTRA_NOTIFICATION_ID, id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val sample = listOf("Groceries", "Food & Dining", "Shopping", "Transportation", "Healthcare")
        val colors = com.androplus.pwdmgr.utils.CategoryPalette.COLORS
        val title = "₹250 · Test Shop"
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText("Test: which category? Tap to choose")
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomHeadsUpContentView(categoryTiles(context, R.layout.notif_category_row, 4, title, sample, colors, testPick, open))
            .setCustomBigContentView(categoryTiles(context, R.layout.notif_category_grid, 6, title, sample, colors, testPick, open))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)

        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    private val TILE_IDS = intArrayOf(R.id.tile0, R.id.tile1, R.id.tile2, R.id.tile3, R.id.tile4, R.id.tile5)
    private val ICON_IDS = intArrayOf(R.id.icon0, R.id.icon1, R.id.icon2, R.id.icon3, R.id.icon4, R.id.icon5)
    private val LABEL_IDS = intArrayOf(R.id.label0, R.id.label1, R.id.label2, R.id.label3, R.id.label4, R.id.label5)

    /** [slots] tiles with icon + name: categories first, the last one always "More". */
    private fun categoryTiles(
        context: Context, layout: Int, slots: Int, title: String, choices: List<String>,
        colors: Map<String, String>, pick: (String, Int) -> PendingIntent, more: PendingIntent,
    ): android.widget.RemoteViews {
        val rv = android.widget.RemoteViews(context.packageName, layout)
        rv.setTextViewText(R.id.notifTitle, title)
        val shown = choices.take(slots - 1)
        for (i in 0 until slots) {
            when {
                i < shown.size -> {
                    val c = shown[i]
                    rv.setImageViewResource(ICON_IDS[i], CategoryStyle.icon(c))
                    rv.setInt(ICON_IDS[i], "setColorFilter", CategoryStyle.color(colors[c] ?: "#737373"))
                    rv.setTextViewText(LABEL_IDS[i], c)
                    rv.setContentDescription(TILE_IDS[i], c)
                    rv.setOnClickPendingIntent(TILE_IDS[i], pick(c, i))
                }
                i == shown.size -> {
                    rv.setImageViewResource(ICON_IDS[i], R.drawable.ic_cat_other)
                    rv.setInt(ICON_IDS[i], "setColorFilter", 0xFF808080.toInt())
                    rv.setTextViewText(LABEL_IDS[i], "More")
                    rv.setContentDescription(TILE_IDS[i], "All categories")
                    rv.setOnClickPendingIntent(TILE_IDS[i], more)
                }
                else -> rv.setViewVisibility(TILE_IDS[i], android.view.View.GONE)
            }
        }
        return rv
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

        // Short text for the collapsed view; the category tiles do the rest.
        val title = "$amount · $name"
        val colors = RealmService.getInstance().getAllCategories().associate { it.name to it.color }
        fun pick(category: String, i: Int) = PendingIntent.getBroadcast(
            context, id * 8 + i,
            Intent(context, CategoryActionReceiver::class.java)
                .putExtra(CategoryActionReceiver.EXTRA_PAYEE, payee)
                .putExtra(CategoryActionReceiver.EXTRA_CATEGORY, category)
                .putExtra(CategoryActionReceiver.EXTRA_NOTIFICATION_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        fun tiles(layout: Int, slots: Int) =
            categoryTiles(context, layout, slots, title, choices, colors, ::pick, openEditor)

        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText("Which category? Tap to choose · we'll remember")
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomHeadsUpContentView(tiles(R.layout.notif_category_row, 4))
            .setCustomBigContentView(tiles(R.layout.notif_category_grid, 6))
            .setContentIntent(openEditor)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // Heads-up over whichever app is open (GPay, PhonePe…) needs sound/vibration on older Android.
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)

        // Not listed? Type a new category right in the notification.
        val newCategory = PendingIntent.getBroadcast(
            context, id * 8 + 7,
            Intent(context, CategoryActionReceiver::class.java)
                .putExtra(CategoryActionReceiver.EXTRA_PAYEE, payee)
                .putExtra(CategoryActionReceiver.EXTRA_NOTIFICATION_ID, id),
            // Mutable: Android writes the typed text into this intent.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        builder.addAction(
            NotificationCompat.Action.Builder(0, "+ New category", newCategory)
                .addRemoteInput(
                    androidx.core.app.RemoteInput.Builder(CategoryActionReceiver.KEY_NEW_CATEGORY)
                        .setLabel("New category, e.g. Tea & Snacks")
                        .build()
                )
                .setAllowGeneratedReplies(false)
                .build()
        )

        NotificationManagerCompat.from(context).notify(id, builder.build())
    }
}
