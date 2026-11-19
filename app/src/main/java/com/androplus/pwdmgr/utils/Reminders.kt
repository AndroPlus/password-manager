package com.androplus.pwdmgr.utils

import java.util.Calendar

/** A payment that comes back every month (or every few months): EMI, rent, premium, bill… */
data class Reminder(
    val id: String,
    val name: String,
    val kind: String,                 // one of ReminderMath.KINDS
    val amount: Double?,              // null when it varies (electricity…)
    val dayOfMonth: Int,              // 1..31; short months use their last day
    val everyMonths: Int = 1,         // 1 monthly, 3 quarterly, 6 half-yearly, 12 yearly
    val anchorMonth: Int,             // year * 12 + month (0-based) of one due month
    val remindDaysBefore: Int = 2,
    val payeeWords: String = "",      // words in the bank SMS that identify the payment, comma-separated
    val paidCycles: List<String> = emptyList(),   // "yyyy-MM" of due months marked paid by hand
    val createdAt: Long = 0,
)

/** What a captured payment looks like to reminder matching. */
data class ReminderTxn(val text: String, val amount: Double, val timeMillis: Long, val isExpense: Boolean)

/** The reminder's current due date and whether it's settled. */
data class DueStatus(
    val reminder: Reminder,
    val dueMillis: Long,      // start of the due day
    val paid: Boolean,
    val paidMillis: Long?,    // when the matching payment happened (null: marked by hand / unpaid)
    val daysLeft: Int,        // negative = overdue
)

object ReminderMath {

    val KINDS = listOf("EMI", "Loan", "Insurance", "Rent", "Bill", "Credit card", "Subscription", "Savings", "Other")

    private const val DAY = 86_400_000L
    /** An unpaid due date stays "current" (overdue) this long before moving on. */
    private const val GRACE_DAYS = 7
    /** A payment this many days before the due date already counts for it. */
    private const val EARLY_DAYS = 15

    fun monthIndex(t: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = t }
        return c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH)
    }

    fun cycleKey(monthIndex: Int): String = "%04d-%02d".format(monthIndex / 12, monthIndex % 12 + 1)

    fun isDueMonth(r: Reminder, monthIndex: Int): Boolean {
        val every = r.everyMonths.coerceAtLeast(1)
        return Math.floorMod(monthIndex - r.anchorMonth, every) == 0
    }

    /** Start of the due day in that month (day clamped to the month's length). */
    fun dueDate(r: Reminder, monthIndex: Int): Long {
        val c = Calendar.getInstance().apply {
            clear()
            set(monthIndex / 12, monthIndex % 12, 1)
        }
        c.set(Calendar.DAY_OF_MONTH, r.dayOfMonth.coerceIn(1, c.getActualMaximum(Calendar.DAY_OF_MONTH)))
        return c.timeInMillis
    }

    fun dayStart(t: Long): Long = Calendar.getInstance().apply {
        timeInMillis = t
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Whole days from today to [due]; uses calendar days, so DST-safe enough for India. */
    fun daysBetween(today: Long, due: Long): Int = Math.round((dayStart(due) - dayStart(today)) / DAY.toDouble()).toInt()

    /** A captured payment that settles this reminder, if any, around [due]. */
    fun matchingPayment(r: Reminder, due: Long, txns: List<ReminderTxn>): ReminderTxn? {
        val words = r.payeeWords.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (words.isEmpty() && r.amount == null) return null            // nothing to match on: by hand only
        val from = due - EARLY_DAYS * DAY
        val to = due + (GRACE_DAYS + 1) * DAY
        return txns.filter { it.isExpense && it.timeMillis in from until to }.firstOrNull { t ->
            val amountOk = r.amount == null ||
                kotlin.math.abs(t.amount - r.amount) <= r.amount * (if (words.isEmpty()) 0.01 else 0.10)
            val wordsOk = words.isEmpty() || words.any { it in t.text.lowercase() }
            amountOk && wordsOk
        }
    }

    /**
     * The due date to show now: the earliest one from a week ago onwards that isn't
     * paid yet, or (when it's paid) the one just settled if it is still ahead.
     */
    fun status(r: Reminder, now: Long, txns: List<ReminderTxn>): DueStatus {
        val today = dayStart(now)
        val start = monthIndex(now) - 1
        for (m in start..start + 24) {
            if (!isDueMonth(r, m)) continue
            val due = dueDate(r, m)
            val days = daysBetween(today, due)
            if (days < -GRACE_DAYS) continue
            val match = matchingPayment(r, due, txns)
            val paid = match != null || cycleKey(m) in r.paidCycles
            if (paid && days < 0) continue      // settled and past: look at the next one
            if (paid && days > 25 && r.everyMonths == 1) continue
            return DueStatus(r, due, paid, match?.timeMillis, days)
        }
        val m = generateSequence(start + 25) { it + 1 }.first { isDueMonth(r, it) }
        return DueStatus(r, dueDate(r, m), false, null, daysBetween(today, dueDate(r, m)))
    }

    /** Remind on [Reminder.remindDaysBefore] days before, on the day, and the day after if still unpaid. */
    fun shouldNotify(s: DueStatus): Boolean =
        !s.paid && (s.daysLeft == s.reminder.remindDaysBefore || s.daysLeft == 0 || s.daysLeft == -1)

    /** "Due today", "Due tomorrow", "Due in 5 days", "Overdue by 2 days", "Paid". */
    fun label(s: DueStatus): String = when {
        s.paid -> "Paid"
        s.daysLeft == 0 -> "Due today"
        s.daysLeft == 1 -> "Due tomorrow"
        s.daysLeft > 1 -> "Due in ${s.daysLeft} days"
        s.daysLeft == -1 -> "Overdue by 1 day"
        else -> "Overdue by ${-s.daysLeft} days"
    }
}
