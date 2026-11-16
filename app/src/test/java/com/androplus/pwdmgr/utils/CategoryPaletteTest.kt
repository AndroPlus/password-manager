package com.androplus.pwdmgr.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryPaletteTest {
    @Test fun upgradesOnlyUntouchedDefaults() {
        assertEquals("#EA580C", CategoryPalette.upgrade("Shopping", "#fcbad3"))
        assertNull(CategoryPalette.upgrade("Shopping", "#123456"))   // user picked a colour
        assertNull(CategoryPalette.upgrade("My Pets", "#FCBAD3"))     // not a default
    }

    @Test fun iconsForDefaultsAndCustomNames() {
        assertEquals("groceries", CategoryPalette.iconKey("Groceries"))
        assertEquals("card", CategoryPalette.iconKey("Card Bill"))
        assertEquals("bills", CategoryPalette.iconKey("Bills & Utilities"))
        assertEquals("food", CategoryPalette.iconKey("Tea & Snacks"))
        assertEquals("other", CategoryPalette.iconKey("My Pets"))
        CategoryPalette.COLORS.keys.forEach { assertEquals(true, CategoryPalette.iconKey(it).isNotEmpty()) }
    }
}
