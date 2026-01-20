package com.androplus.pwdmgr.fragment.expense

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.androplus.pwdmgr.R
import com.androplus.pwdmgr.adapter.BudgetAdapter
import com.androplus.pwdmgr.model.ExpenseBudget
import com.androplus.pwdmgr.model.ExpenseCategory
import com.androplus.pwdmgr.services.RealmService
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import java.util.Calendar

class BudgetFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var fab: FloatingActionButton
    private lateinit var adapter: BudgetAdapter
    private var categories: List<ExpenseCategory> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_budget, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(R.id.budgetRecyclerView)
        fab = view.findViewById(R.id.fabAddBudget)

        setupRecyclerView()
        loadData()

        fab.setOnClickListener {
            showAddBudgetDialog()
        }
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(context)
    }

    private fun loadData() {
        categories = RealmService.getInstance().getAllCategories()
        val budgets = RealmService.getInstance().getAllBudgets()
        val expenses = getThisMonthExpenses()
        
        val spentMap = mutableMapOf<String, Double>()
        expenses.forEach { expense ->
            val current = spentMap.getOrDefault(expense.categoryId, 0.0)
            spentMap[expense.categoryId] = current + expense.amount
        }

        val categoryNameMap = categories.associate { it._id.toHexString() to it.name }

        adapter = BudgetAdapter(budgets, spentMap, categoryNameMap) { budget ->
            showAddBudgetDialog(budget) // Edit existing
        }
        recyclerView.adapter = adapter
        
        view?.findViewById<View>(R.id.emptyStateText)?.visibility = if (budgets.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun getThisMonthExpenses(): List<com.androplus.pwdmgr.model.ExpenseTransaction> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val start = calendar.timeInMillis
        val end = System.currentTimeMillis()
        return RealmService.getInstance().getExpensesByDateRange(start, end)
            .filter { it.type == "EXPENSE" }
    }

    private fun showAddBudgetDialog(existingBudget: ExpenseBudget? = null) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_budget, null)
        val categorySpinner = dialogView.findViewById<Spinner>(R.id.categorySpinner)
        val amountInput = dialogView.findViewById<EditText>(R.id.amountInput)

        val availableCategories = categories.filter { category ->
             // If creating new, only show categories without budget.
             // If editing, show current category.
             // For simplicity, showing all but we should check duplicates logic.
             // Actually, setBudget updates if exists, so listing all is fine.
             true
        }
        
        val categoryAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            availableCategories.map { it.name }
        )
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        categorySpinner.adapter = categoryAdapter

        if (existingBudget != null) {
            val index = availableCategories.indexOfFirst { it._id.toHexString() == existingBudget.categoryId }
            if (index != -1) categorySpinner.setSelection(index)
            categorySpinner.isEnabled = false // Don't change category on edit for simplicity
            amountInput.setText(existingBudget.amount.toString())
        }

        AlertDialog.Builder(context)
            .setTitle(if (existingBudget == null) "Set Budget" else "Edit Budget")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val amountStr = amountInput.text.toString()
                if (amountStr.isNotEmpty()) {
                    val amount = amountStr.toDouble()
                    val category = availableCategories[categorySpinner.selectedItemPosition]
                    
                    val budget = ExpenseBudget().apply {
                         categoryId = category._id.toHexString()
                         this.amount = amount
                         period = "MONTHLY"
                    }
                    
                    lifecycleScope.launch {
                        RealmService.getInstance().setBudget(budget)
                        loadData()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
