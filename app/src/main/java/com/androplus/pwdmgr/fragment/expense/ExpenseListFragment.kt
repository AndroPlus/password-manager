package com.androplus.pwdmgr.fragment.expense

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.androplus.pwdmgr.R
import com.androplus.pwdmgr.adapter.ExpenseGroupAdapter
import com.androplus.pwdmgr.databinding.ExpenseListLayoutBinding
import com.androplus.pwdmgr.model.ExpenseTransaction
import com.androplus.pwdmgr.services.RealmService
import com.androplus.pwdmgr.utils.DuplicateDetector
import com.androplus.pwdmgr.utils.ExpenseGrouping
import com.androplus.pwdmgr.utils.ExpenseRow
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

class ExpenseListFragment : Fragment() {

    private var _binding: ExpenseListLayoutBinding? = null
    private val binding get() = _binding!!
    private lateinit var groupAdapter: ExpenseGroupAdapter
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    private var currentFilter = "THIS_MONTH"
    private var loaded: List<ExpenseTransaction> = emptyList()

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

        // Reload whenever an expense is saved, including ones captured from SMS or
        // GPay/PhonePe while this screen is open. Also covers returning to it.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                RealmService.getInstance().expenseChanges().collect { loadExpenses() }
            }
        }
    }

    private fun setupRecyclerView() {
        groupAdapter = ExpenseGroupAdapter(
            onRowClick = { row ->
                val bundle = Bundle().apply { putString("expense_id", row.id) }
                findNavController().navigate(R.id.ExpenseAddFragment, bundle)
            },
            onRowLongClick = { row -> confirmDelete(row) },
        )
        binding.expenseRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = groupAdapter
        }
    }

    private fun confirmDelete(row: ExpenseRow) {
        val expense = loaded.find { it._id.toHexString() == row.id } ?: return
        AlertDialog.Builder(requireContext())
            .setTitle("Delete transaction?")
            .setMessage("${row.title} · ${currencyFormat.format(row.amount)}")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch { RealmService.getInstance().deleteExpense(expense) }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupListeners() {
        binding.fabAddExpense.setOnClickListener {
            findNavController().navigate(R.id.ExpenseAddFragment)
        }

        binding.chipAllTime.setOnClickListener { setFilter("ALL") }
        binding.chipToday.setOnClickListener { setFilter("TODAY") }
        binding.chipThisMonth.setOnClickListener { setFilter("THIS_MONTH") }
        binding.chipLastMonth.setOnClickListener { setFilter("LAST_MONTH") }

        binding.btnAnalytics.setOnClickListener {
            findNavController().navigate(R.id.action_expense_list_to_analytics)
        }

        binding.btnBudgets.setOnClickListener {
            findNavController().navigate(R.id.action_expense_list_to_budget)
        }
    }

    private fun setFilter(filter: String) {
        currentFilter = filter
        loadExpenses()
    }

    private fun loadExpenses() {
        if (_binding == null) return
        val realm = RealmService.getInstance()
        loaded = when (currentFilter) {
            "TODAY" -> periodRange(Calendar.DAY_OF_MONTH, 0).let { (s, e) -> realm.getExpensesByDateRange(s, e) }
            "THIS_MONTH" -> periodRange(Calendar.MONTH, 0).let { (s, e) -> realm.getExpensesByDateRange(s, e) }
            "LAST_MONTH" -> periodRange(Calendar.MONTH, -1).let { (s, e) -> realm.getExpensesByDateRange(s, e) }
            else -> realm.getAllExpenses()
        }

        groupAdapter.submit(ExpenseGrouping.group(loaded.map { it.toRow() }))
        updateSummary(loaded)
        binding.emptyStateText.visibility = if (loaded.isEmpty()) View.VISIBLE else View.GONE
    }

    /**
     * Start and end (inclusive, whole period) of today, or of this/last month.
     * The end is the period's last millisecond, not "now", so an entry saved a
     * moment after loading still falls inside the range.
     */
    private fun periodRange(unit: Int, offset: Int): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            if (unit == Calendar.MONTH) {
                add(Calendar.MONTH, offset)
                set(Calendar.DAY_OF_MONTH, 1)
            }
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val end = (start.clone() as Calendar).apply {
            add(unit, 1)
            add(Calendar.MILLISECOND, -1)
        }
        return start.timeInMillis to end.timeInMillis
    }

    private fun ExpenseTransaction.toRow() = ExpenseRow(
        id = _id.toHexString(),
        title = ExpenseGrouping.displayTitle(shortDescription, categoryName),
        source = DuplicateDetector.sourceOf(shortDescription),
        amount = amount,
        isIncome = type == "INCOME",
        timeMillis = date,
        categoryName = categoryName,
        categoryColor = categoryColor,
    )

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
