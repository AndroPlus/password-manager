package com.androplus.pwdmgr.fragment.expense

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.androplus.pwdmgr.MainActivity
import com.androplus.pwdmgr.R
import com.androplus.pwdmgr.adapter.ExpenseListAdapter
import com.androplus.pwdmgr.databinding.ExpenseListLayoutBinding
import com.androplus.pwdmgr.model.ExpenseTransaction
import com.androplus.pwdmgr.services.RealmService
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.*

class ExpenseListFragment : Fragment() {

    private var _binding: ExpenseListLayoutBinding? = null
    private val binding get() = _binding!!
    private lateinit var expenseAdapter: ExpenseListAdapter
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ExpenseListLayoutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupListeners()
        loadExpenses()
    }

    private fun setupRecyclerView() {
        expenseAdapter = ExpenseListAdapter(
            expenses = emptyList(),
            onItemClick = { expense ->
                // Navigate to edit
                val bundle = Bundle().apply {
                    putString("expense_id", expense._id.toHexString())
                }
                findNavController().navigate(R.id.ExpenseAddFragment, bundle)
            },
            onItemLongClick = { expense ->
                // Show delete confirmation
                lifecycleScope.launch {
                    RealmService.getInstance().deleteExpense(expense)
                    loadExpenses()
                }
            }
        )
        binding.expenseRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = expenseAdapter
        }
    }

    private fun setupListeners() {
        binding.fabAddExpense.setOnClickListener {
            findNavController().navigate(R.id.ExpenseAddFragment)
        }

        binding.chipAllTime.setOnClickListener { loadExpenses("ALL") }
        binding.chipToday.setOnClickListener { loadExpenses("TODAY") }
        binding.chipThisMonth.setOnClickListener { loadExpenses("THIS_MONTH") }
        binding.chipLastMonth.setOnClickListener { loadExpenses("LAST_MONTH") }
        
        binding.btnAnalytics.setOnClickListener {
            findNavController().navigate(R.id.action_expense_list_to_analytics)
        }
        
        binding.btnBudgets.setOnClickListener {
            findNavController().navigate(R.id.action_expense_list_to_budget)
        }
    }

    private fun loadExpenses(filter: String = "THIS_MONTH") {
        val expenses = when (filter) {
            "TODAY" -> getTodayExpenses()
            "THIS_MONTH" -> getThisMonthExpenses()
            "LAST_MONTH" -> getLastMonthExpenses()
            else -> RealmService.getInstance().getAllExpenses()
        }

        expenseAdapter.updateExpenses(expenses)
        updateSummary(expenses)

        binding.emptyStateText.visibility = if (expenses.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun getTodayExpenses(): List<ExpenseTransaction> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val start = calendar.timeInMillis
        val end = System.currentTimeMillis()
        return RealmService.getInstance().getExpensesByDateRange(start, end)
    }

    private fun getThisMonthExpenses(): List<ExpenseTransaction> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val start = calendar.timeInMillis
        val end = System.currentTimeMillis()
        return RealmService.getInstance().getExpensesByDateRange(start, end)
    }

    private fun getLastMonthExpenses(): List<ExpenseTransaction> {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -1)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        val start = calendar.timeInMillis
        
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        val end = calendar.timeInMillis
        return RealmService.getInstance().getExpensesByDateRange(start, end)
    }

    private fun updateSummary(expenses: List<ExpenseTransaction>) {
        var totalIncome = 0.0
        var totalExpense = 0.0

        for (expense in expenses) {
            if (expense.type == "INCOME") {
                totalIncome += expense.amount
            } else {
                totalExpense += expense.amount
            }
        }

        binding.totalIncomeText.text = currencyFormat.format(totalIncome)
        binding.totalExpenseText.text = currencyFormat.format(totalExpense)
        binding.balanceText.text = currencyFormat.format(totalIncome - totalExpense)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
