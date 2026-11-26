package com.androplus.pwdmgr.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {

    private val data = BackupData(
        createdAt = 1_700_000_000_000,
        expenses = listOf(BackupExpense("65a1", 150.0, "EXPENSE", 1_700_000_000_000, "[SMS] swiggy",
            "Rs 150 debited…", "Food & Dining", "#2563EB")),
        categories = listOf(BackupCategory("Tea & Snacks", "food", "#0891B2", false)),
        budgets = listOf(BackupBudget("Food & Dining", 5000.0, "MONTHLY")),
        files = mapOf("learned_categories.json" to """{"out:swiggy":"Food & Dining"}"""),
        prefs = mapOf("monthly_budget" to "40000"),
    )

    @Test fun roundTripWithTheRightPassword() {
        val file = BackupCrypto.encrypt(data, "correct horse")
        assertEquals(data, BackupCrypto.decrypt(file, "correct horse"))
        assertEquals(data.createdAt to 1, BackupCrypto.summary(file))
        // Nothing readable in the file without the password.
        assertTrue("swiggy" !in String(file))
    }

    @Test(expected = WrongPasswordException::class)
    fun wrongPasswordIsRefused() {
        BackupCrypto.decrypt(BackupCrypto.encrypt(data, "right"), "wrong")
    }

    @Test(expected = WrongPasswordException::class)
    fun otherFilesAreRefused() {
        BackupCrypto.decrypt("""{"hello":"world"}""".toByteArray(), "x")
    }

    @Test fun notABackupHasNoSummary() = assertNull(BackupCrypto.summary("photo bytes".toByteArray()))

    @Test fun mergeKeepsThePhonesOwnValues() {
        val merged = BackupMerge.mergeMaps("""{"out:swiggy":"Groceries"}""",
            """{"out:swiggy":"Food & Dining","out:kfc":"Food & Dining"}""")
        assertEquals("""{"out:swiggy":"Groceries","out:kfc":"Food & Dining"}""", merged)
        val r1 = Reminder("a", "Rent", "Rent", 12000.0, 5, anchorMonth = 1)
        val r2 = Reminder("b", "EMI", "EMI", 4500.0, 10, anchorMonth = 1)
        val local = com.google.gson.Gson().toJson(listOf(r1.copy(amount = 13000.0)))
        val incoming = com.google.gson.Gson().toJson(listOf(r1, r2))
        val out = BackupMerge.mergeReminders(local, incoming)
        assertTrue("13000" in out && "EMI" in out && !out.contains("12000.0"))
    }
}
