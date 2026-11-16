package com.androplus.pwdmgr.ui

import android.graphics.Color
import com.androplus.pwdmgr.R
import com.androplus.pwdmgr.utils.CategoryPalette

/** Icon and colours for showing a category. */
object CategoryStyle {

    fun icon(name: String): Int = when (CategoryPalette.iconKey(name)) {
        "groceries" -> R.drawable.ic_cat_groceries
        "food" -> R.drawable.ic_cat_food
        "fuel" -> R.drawable.ic_cat_fuel
        "transport" -> R.drawable.ic_cat_transport
        "bills" -> R.drawable.ic_cat_bills
        "fun" -> R.drawable.ic_cat_fun
        "home" -> R.drawable.ic_cat_home
        "health" -> R.drawable.ic_cat_health
        "education" -> R.drawable.ic_cat_education
        "shopping" -> R.drawable.ic_cat_shopping
        "savings" -> R.drawable.ic_cat_savings
        "cash" -> R.drawable.ic_cat_cash
        "income" -> R.drawable.ic_cat_income
        "transfer" -> R.drawable.ic_cat_transfer
        "card" -> R.drawable.ic_cat_card
        else -> R.drawable.ic_cat_other
    }

    fun color(hex: String): Int = try { Color.parseColor(hex) } catch (e: IllegalArgumentException) { Color.GRAY }

    /** 15% tint of the colour, for the square behind an icon. */
    fun tint(c: Int): Int = (c and 0x00FFFFFF) or 0x26000000

    /** The colour made dark enough to read on its own tint. */
    fun ink(c: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(c, hsv)
        hsv[2] = minOf(hsv[2], 0.55f)
        return Color.HSVToColor(hsv)
    }
}
