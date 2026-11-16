package com.androplus.pwdmgr.fragment.expense

import android.content.Intent
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.androplus.pwdmgr.R
import com.androplus.pwdmgr.SettingsActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

/** Bottom bar shared by Home, Spending and Insights; "More" holds the other modules. */
object ExpenseTabs {

    fun setup(fragment: Fragment, nav: BottomNavigationView, current: Int) {
        nav.setOnItemSelectedListener(null)
        nav.selectedItemId = current
        nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                current -> true
                R.id.tab_home -> {
                    val nc = fragment.findNavController()
                    if (!nc.popBackStack(R.id.ExpenseHomeFragment, false)) nc.navigate(R.id.ExpenseHomeFragment)
                    true
                }
                R.id.tab_spending -> go(fragment, R.id.ExpenseListFragment)
                R.id.tab_insights -> go(fragment, R.id.InsightsFragment)
                R.id.tab_more -> {
                    showMore(fragment, nav.findViewById(R.id.tab_more) ?: nav)
                    false
                }
                else -> false
            }
        }
    }

    private fun go(fragment: Fragment, dest: Int): Boolean {
        val options = NavOptions.Builder()
            .setPopUpTo(R.id.ExpenseHomeFragment, false)
            .setLaunchSingleTop(true)
            .build()
        fragment.findNavController().navigate(dest, null, options)
        return true
    }

    private fun showMore(fragment: Fragment, anchor: View) {
        val menu = PopupMenu(fragment.requireContext(), anchor)
        val items = listOf(
            "Sort your payees" to { fragment.findNavController().navigate(R.id.PayeeSortFragment) },
            "Categories" to { fragment.findNavController().navigate(R.id.CategoryListFragment) },
            "Budgets" to { fragment.findNavController().navigate(R.id.BudgetFragment) },
            "Todo list" to { fragment.findNavController().navigate(R.id.TodoListFragment) },
            "Password manager" to { fragment.findNavController().navigate(R.id.LoginFragment) },
            "Settings" to {
                fragment.startActivity(Intent(fragment.requireContext(), SettingsActivity::class.java))
            },
        )
        items.forEachIndexed { i, (title, _) -> menu.menu.add(0, i, i, title) }
        menu.setOnMenuItemClickListener { mi -> items[mi.itemId].second(); true }
        menu.show()
    }
}
