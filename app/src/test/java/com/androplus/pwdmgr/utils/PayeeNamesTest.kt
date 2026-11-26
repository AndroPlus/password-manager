package com.androplus.pwdmgr.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PayeeNamesTest {

    @get:Rule val tmp = TemporaryFolder()

    @After fun reset() { ExpenseGrouping.nicknameOf = { null } }

    @Test fun namesAPayeeAcrossSpellingsAndSources() {
        val names = PayeeNames(File(tmp.root, "names.json"))
        names.set("[SMS] zpgl", "KFC")
        assertEquals("KFC", names.get("[GPay] ZPGL"))
        assertEquals("KFC", PayeeNames(File(tmp.root, "names.json")).get("zpgl"))   // survives restart
        names.set("zpgl", "  ")
        assertNull(names.get("zpgl"))                                              // blank clears
        assertFalse(names.set("[SMS] UPI payment", "KFC"))                         // generic: not nameable
    }

    @Test fun displayUsesNicknameOnlyForCapturedPayees() {
        val names = PayeeNames(File(tmp.root, "names.json"))
        names.set("[SMS] zpgl", "Pizza Hut")
        ExpenseGrouping.nicknameOf = { names.get(it) }
        assertEquals("Pizza Hut", ExpenseGrouping.displayTitle("[SMS] zpgl", "Food & Dining"))
        assertEquals("zpgl", ExpenseGrouping.displayTitle("zpgl", "Food & Dining"))   // manual entry as typed
        assertEquals("zpgl", ExpenseGrouping.capturedName("[SMS] zpgl"))
    }
}
