package com.androplus.pwdmgr.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.androplus.pwdmgr.MainActivity
import com.androplus.pwdmgr.R
import com.androplus.pwdmgr.utils.DueStatus
import com.androplus.pwdmgr.utils.Reminder
import com.androplus.pwdmgr.utils.ReminderMath
import com.androplus.pwdmgr.utils.ReminderStore
import com.androplus.pwdmgr.utils.ReminderTxn
import com.androplus.pwdmgr.worker.BillReminderWorker
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Bills & EMI reminders: storage, status from captured payments, and notifications. */
object Bills {

    private const val CHANNEL = "bill_reminders"
    const val EXTRA_OPEN_BILLS = "open_bills"
    private val money = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply { maximumFractionDigits = 0 }
    private val dayFormat = SimpleDateFormat("d MMM", Locale("en", "IN"))

    fun store(context: Context) = ReminderStore(File(context.applicationContext.filesDir, "bill_reminders.json"))

    /** Captured payments from the last ~3 months, for matching. */
    fun recentPayments(): List<ReminderTxn> {
        val from = System.currentTimeMillis() - 100L * 86_400_000L
        return RealmService.getInstance().getExpensesByDateRange(from, System.currentTimeMillis() + 86_400_000L)
            .map { ReminderTxn("${it.shortDescription} ${it.description}", it.amount, it.date, it.type == "EXPENSE") }
    }

    /** Every reminder with its current due date, soonest first (paid ones last). */
    fun statuses(context: Context): List<DueStatus> {
        val txns = recentPayments()
        val now = System.currentTimeMillis()
        return store(context).all().map { ReminderMath.status(it, now, txns) }
            .sortedWith(compareBy<DueStatus> { it.paid }.thenBy { it.dueMillis })
    }

    fun markPaid(context: Context, id: String, dueMillis: Long) {
        val s = store(context)
        val r = s.get(id) ?: return
        val key = ReminderMath.cycleKey(ReminderMath.monthIndex(dueMillis))
        s.save(r.copy(paidCycles = (r.paidCycles + key).distinct().takeLast(24)))
        NotificationManagerCompat.from(context).cancel(notificationId(id))
    }

    fun notificationId(id: String) = ("bill:$id").hashCode()

    /** Runs once a day around 9 AM. */
    fun schedule(context: Context) {
        val now = Calendar.getInstance()
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_MONTH, 1)
        }
        val request = PeriodicWorkRequestBuilder<BillReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(next.timeInMillis - now.timeInMillis, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            BillReminderWorker.WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Posts today's reminders (each due date and day once). Returns how many. */
    fun notifyDue(context: Context): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val sent = prefs.getStringSet("bill_notified", emptySet()).orEmpty().toMutableSet()
        val today = ReminderMath.dayStart(System.currentTimeMillis())
        var n = 0
        for (s in statuses(context)) {
            if (!ReminderMath.shouldNotify(s)) continue
            val key = "${s.reminder.id}:$today"
            if (key in sent) continue
            post(context, s)
            sent += key
            n++
        }
        prefs.edit().putStringSet("bill_notified", sent.toList().takeLast(200).toSet()).apply()
        return n
    }

    private fun post(context: Context, s: DueStatus) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
                NotificationChannel(CHANNEL, "Bill & EMI reminders", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "EMIs, rent, premiums and bills coming due" })
        }
        val r = s.reminder
        val id = notificationId(r.id)
        val open = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java).putExtra(EXTRA_OPEN_BILLS, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val paid = PendingIntent.getBroadcast(
            context, id, Intent(context, BillActionReceiver::class.java)
                .putExtra(BillActionReceiver.EXTRA_ID, r.id).putExtra(BillActionReceiver.EXTRA_DUE, s.dueMillis),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val amount = r.amount?.let { money.format(it) + " " } ?: ""
        val title = "${r.name}: $amount${ReminderMath.label(s).lowercase()}"
        val body = "Due ${dayFormat.format(Date(s.dueMillis))}. Already paid? Tap Mark paid."
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(0, "Mark paid", paid)
            .addAction(0, "Open", open)
            .build()
        NotificationManagerCompat.from(context).notify(id, n)
    }

    fun newId(): String = java.util.UUID.randomUUID().toString()

    fun iconFor(kind: String): Int = when (kind) {
        "EMI", "Credit card" -> R.drawable.ic_cat_card
        "Loan" -> R.drawable.ic_cat_cash
        "Insurance" -> R.drawable.ic_cat_health
        "Rent" -> R.drawable.ic_cat_home
        "Bill" -> R.drawable.ic_cat_bills
        "Subscription" -> R.drawable.ic_cat_fun
        "Savings" -> R.drawable.ic_cat_savings
        else -> R.drawable.ic_cat_other
    }

    /** A sensible starting reminder from a payment found to repeat monthly. */
    fun fromRecurring(name: String, amount: Double, day: Int): Reminder = Reminder(
        id = newId(), name = name, kind = "Other", amount = amount, dayOfMonth = day,
        anchorMonth = ReminderMath.monthIndex(System.currentTimeMillis()),
        payeeWords = name.lowercase(), createdAt = System.currentTimeMillis(),
    )
}
