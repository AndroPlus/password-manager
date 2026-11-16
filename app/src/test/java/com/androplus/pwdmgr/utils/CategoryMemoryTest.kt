package com.androplus.pwdmgr.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CategoryMemoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun memory() = CategoryMemory(File(tmp.root, "learned.json"))

    @Test fun remembersPayeeIgnoringCaseTagAndPunctuation() {
        val m = memory()
        m.learn("[SMS] KANNAN STORE.", isExpense = true, category = "Groceries")
        assertEquals("Groceries", m.categoryFor("Kannan  Store", true))
        // Survives a restart (new instance reads the file).
        assertEquals("Groceries", memory().categoryFor("kannan store", true))
    }

    @Test fun moneyInAndOutAreSeparate() {
        val m = memory()
        m.learn("Ravi Kumar", isExpense = true, category = "Family")
        assertNull(m.categoryFor("Ravi Kumar", false))
    }

    @Test fun genericNamesAreNotLearned() {
        val m = memory()
        assertFalse(m.learn("UPI payment", true, "Food & Dining"))
        assertFalse(m.learn("[GPay] UPI transfer", true, "Food & Dining"))
        assertFalse(m.learn("12345", true, "Food & Dining"))
        assertEquals(0, m.size())
    }

    @Test fun laterChoiceReplacesEarlierOne() {
        val m = memory()
        m.learn("Raja Teastall", true, "Shopping")
        m.learn("Raja Teastall", true, "Food & Dining")
        assertEquals("Food & Dining", m.categoryFor("RAJA TEASTALL", true))
    }
}
