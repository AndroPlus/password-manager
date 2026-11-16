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
import androidx.core.util.Pair as AndroidPair
import com.google.android.material.datepicker.MaterialDatePicker
import java.text.SimpleDateFormat
import java.util.TimeZone
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

class ExpenseListFragment : Fragment() {

    private var _binding: ExpenseListLayoutBinding? = null
    private val binding get() = _binding!!
    private lateinit var groupAdapter: ExpenseGroupAdapter
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    // Whole rupees in the summary card so large totals fit on one line.
    private val summaryFormat = (NumberFormat.getCurrencyInstance(Locale("en", "IN")))
        .apply { maximumFractionDigits = 0 }

    private var currentFilter = "THIS_MONTH"
    // Chosen with the date picker: local start of the first day, end of the last day.
    private var customStart = 0L
    private var customEnd = 0L
    private val dayFormat = SimpleDateFormat("d MMM", Locale("en", "IN"))
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
        binding.chipPickDates.setOnClickListener { showDatePicker() }

        binding.btnAnalytics.setOnClickListener {
            findNavController().navigate(R.id.action_expense_list_to_analytics)
        }

        binding.btnCategories.setOnClickListener {
            findNavController().navigate(R.id.CategoryListFragment)
        }

        binding.btnBudgets.setOnClickListener {
            findNavController().navigate(R.id.action_expense_list_to_budget)
        }
    }

    /** Pick one day (tap it twice) or a range; the list then shows only those days. */
    private fun showDatePicker() {
        val builder = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Show transactions from")
        if (currentFilter == "CUSTOM") {
            builder.setSelection(AndroidPair(toUtcDay(customStart), toUtcDay(customEnd)))
        }
        val picker = builder.build()
        picker.addOnPositiveButtonClickListener { sel ->
            val first = sel.first ?: return@addOnPositiveButtonClickListener
            val last = sel.second ?: first
            customStart = localDayStart(first)
            customEnd = localDayStart(last) + DAY_MILLIS - 1
            setFilter("CUSTOM")
        }
        // Cancelled: keep the chip selection in line with what the list shows.
        picker.addOnDismissListener { syncChips() }
        picker.show(childFragmentManager, "expense_date_picker")
    }

    /** The picker works in UTC midnights; the list in the phone's local days. */
    private fun localDayStart(utcMidnight: Long): Long {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMidnight }
        return Calendar.getInstance().apply {
            clear()
            set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }

    private fun toUtcDay(localMillis: Long): Long {
        val local = Calendar.getInstance().apply { timeInMillis = localMillis }
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }

    private fun syncChips() {
        if (_binding == null) return
        when (currentFilter) {
            "ALL" -> binding.chipAllTime
            "TODAY" -> binding.chipToday
            "LAST_MONTH" -> binding.chipLastMonth
            "CUSTOM" -> binding.chipPickDates
            else -> binding.chipThisMonth
        }.isChecked = true
    }

    private fun customLabel(): String {
        val a = dayFormat.format(customStart)
        val b = dayFormat.format(customEnd)
        return if (a == b) a else "$a – $b"
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
            "CUSTOM" -> realm.getExpensesByDateRange(customStart, customEnd)
            else -> realm.getAllExpenses()
        }

        binding.summaryPeriodText.text = when (currentFilter) {
            "TODAY" -> "Today"
            "LAST_MONTH" -> "Last Month"
            "ALL" -> "All Time"
            "CUSTOM" -> customLabel()
            else -> getString(R.string.this_month)
        }
        val rows = loaded.map { it.toRow() }
        groupAdapter.submit(ExpenseGrouping.group(rows))
        updateSummary(rows)
        binding.chipPickDates.text = if (currentFilter == "CUSTOM") customLabel() else "Pick dates"
        syncChips()
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

    private fun updateSummary(rows: List<ExpenseRow>) {
        val (income, expense) = ExpenseGrouping.totals(rows)
        binding.totalIncomeText.text = summaryFormat.format(income)
        binding.totalExpenseText.text = summaryFormat.format(expense)
        binding.balanceText.text = summaryFormat.format(income - expense)
    }

    companion object {
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
