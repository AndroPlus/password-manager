package com.androplus.pwdmgr.services

import android.content.Context
import androidx.preference.PreferenceManager

/** Small settings shared by several screens. */
object AppPrefs {
    private const val MONTHLY_BUDGET = "monthly_budget"
    const val ONBOARDING_DONE = "onboarding_done"
    const val PROMPT_MIN_AMOUNT = "prompt_min_amount"

    private fun prefs(c: Context) = PreferenceManager.getDefaultSharedPreferences(c)

    /** Whole-month budget set on Home; null when not set. */
    fun monthlyBudget(c: Context): Double? =
        prefs(c).getString(MONTHLY_BUDGET, null)?.toDoubleOrNull()?.takeIf { it > 0 }

    fun setMonthlyBudget(c: Context, value: Double?) {
        prefs(c).edit().apply {
            if (value == null || value <= 0) remove(MONTHLY_BUDGET) else putString(MONTHLY_BUDGET, value.toLong().toString())
        }.apply()
    }

    fun onboardingDone(c: Context): Boolean = prefs(c).getBoolean(ONBOARDING_DONE, false)

    fun setOnboardingDone(c: Context) = prefs(c).edit().putBoolean(ONBOARDING_DONE, true).apply()

    /** New-payee notifications only for payments of at least this much (rest wait on Home). */
    fun promptMinAmount(c: Context): Double =
        prefs(c).getString(PROMPT_MIN_AMOUNT, null)?.toDoubleOrNull() ?: 200.0
}
