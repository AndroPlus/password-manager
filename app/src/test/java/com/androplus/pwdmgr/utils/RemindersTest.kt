package com.androplus.pwdmgr.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RemindersTest {

    private fun at(y: Int, m: Int, d: Int, h: Int = 10): Long =
        Calendar.getInstance().apply { clear(); set(y, m - 1, d, h, 0) }.timeInMillis

    private fun r(day: Int, amount: Double? = 4500.0, words: String = "", every: Int = 1,
                  anchor: Int = 2026 * 12 + 0, paid: List<String> = emptyList(), before: Int = 2) =
        Reminder("1", "Bike EMI", "EMI", amount, day, every, anchor, before, words, paid)

    private fun pay(amount: Double, t: Long, text: String = "Rs debited to BAJAJ FIN") =
        ReminderTxn(text, amount, t, true)

    @Test fun monthlyDueThisMonthWithDaysLeft() {
        val s = ReminderMath.status(r(5), at(2026, 12, 2), emptyList())
        assertEquals(at(2026, 12, 5, 0), s.dueMillis)
        assertEquals(3, s.daysLeft)
        assertFalse(s.paid)
        assertEquals("Due in 3 days", ReminderMath.label(s))
    }

    @Test fun shortMonthUsesItsLastDay() {
        val s = ReminderMath.status(r(31), at(2027, 2, 10), emptyList())
        assertEquals(at(2027, 2, 28, 0), s.dueMillis)
    }

    @Test fun paidByMatchingAmountThenShowsNextMonth() {
        val txns = listOf(pay(4500.0, at(2026, 12, 4)))
        val onDueDay = ReminderMath.status(r(5), at(2026, 12, 5), txns)
        assertTrue(onDueDay.paid)
        assertFalse(ReminderMath.shouldNotify(onDueDay))
        val after = ReminderMath.status(r(5), at(2026, 12, 6), txns)
        assertEquals(at(2027, 1, 5, 0), after.dueMillis)
        assertFalse(after.paid)
    }

    @Test fun payeeWordsAllowAmountToVaryTenPercent() {
        val rem = r(10, amount = 1000.0, words = "tneb, electricity")
        assertTrue(ReminderMath.status(rem, at(2026, 12, 9), listOf(pay(1080.0, at(2026, 12, 8), "TNEB bill"))).paid)
        assertFalse(ReminderMath.status(rem, at(2026, 12, 9), listOf(pay(1080.0, at(2026, 12, 8), "Grocer"))).paid)
        // Without words, the amount must match closely.
        assertFalse(ReminderMath.status(r(10, 1000.0), at(2026, 12, 9), listOf(pay(1080.0, at(2026, 12, 8)))).paid)
    }

    @Test fun overdueStaysForAWeekThenMovesOn() {
        val s = ReminderMath.status(r(5), at(2026, 12, 7), emptyList())
        assertEquals(-2, s.daysLeft)
        assertEquals("Overdue by 2 days", ReminderMath.label(s))
        val later = ReminderMath.status(r(5), at(2026, 12, 20), emptyList())
        assertEquals(at(2027, 1, 5, 0), later.dueMillis)
    }

    @Test fun markedPaidByHand() {
        val s = ReminderMath.status(r(5, paid = listOf("2026-12")), at(2026, 12, 3), emptyList())
        assertTrue(s.paid)
    }

    @Test fun quarterlyFollowsItsAnchorMonth() {
        // Due in Jan, Apr, Jul, Oct.
        val q = r(15, every = 3, anchor = 2026 * 12 + 0)
        assertEquals(at(2027, 1, 15, 0), ReminderMath.status(q, at(2026, 11, 20), emptyList()).dueMillis)
    }

    @Test fun notifiesBeforeOnTheDayAndDayAfter() {
        fun notifyOn(d: Int) = ReminderMath.shouldNotify(ReminderMath.status(r(10, before = 3), at(2026, 12, d), emptyList()))
        assertTrue(notifyOn(7))
        assertFalse(notifyOn(8))
        assertTrue(notifyOn(10))
        assertTrue(notifyOn(11))
        assertFalse(notifyOn(12))
    }
}
