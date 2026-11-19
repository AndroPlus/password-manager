package com.androplus.pwdmgr.fragment.expense

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.util.Pair as AndroidPair
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.androplus.pwdmgr.R
import com.androplus.pwdmgr.databinding.FragmentExpenseHomeBinding
import com.androplus.pwdmgr.services.AppPrefs
import com.androplus.pwdmgr.services.AutoTransactionRecorder
import com.androplus.pwdmgr.services.RealmService
import com.androplus.pwdmgr.utils.DuplicateDetector
import com.androplus.pwdmgr.utils.ExpenseGrouping
import com.androplus.pwdmgr.utils.ExpenseRow
import com.androplus.pwdmgr.utils.HomeStatsCalc
import com.androplus.pwdmgr.utils.Insights
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Home: this month's spending against budget, where it went, and the latest payments. */
class ExpenseHomeFragment : Fragment() {

    private var _binding: FragmentExpenseHomeBinding? = null
    private val binding get() = _binding!!

    private val money = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply { maximumFractionDigits = 0 }
    private val dayFormat = SimpleDateFormat("d MMM", Locale("en", "IN"))
    private val timeFormat = SimpleDateFormat("d MMM, h:mm a", Locale("en", "IN"))

    private var monthOffset = 0
    private var customRange: kotlin.Pair<Long, Long>? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentExpenseHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        AutoTransactionRecorder.init(requireContext())
        ExpenseTabs.setup(this, binding.bottomNav, R.id.tab_home)
        askNotificationPermission()

        binding.homePrevMonth.setOnClickListener { customRange = null; monthOffset--; load() }
        binding.homeNextMonth.setOnClickListener { customRange = null; if (monthOffset < 0) monthOffset++; load() }
        binding.homeMonthText.setOnClickListener { customRange = null; monthOffset = 0; load() }
        binding.homeDatesButton.setOnClickListener { pickDates() }
        binding.homeAddButton.setOnClickListener { findNavController().navigate(R.id.ExpenseAddFragment) }
        binding.homeAllBills.setOnClickListener { findNavController().navigate(R.id.BillsFragment) }
        binding.homeAllCategories.setOnClickListener { binding.bottomNav.selectedItemId = R.id.tab_spending }
        binding.homeBudgetText.setOnClickListener { editBudget() }
        binding.homeDailyText.setOnClickListener { if (AppPrefs.monthlyBudget(requireContext()) == null) editBudget() }
        binding.homeNotCountedTile.setOnClickListener {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Not counted")
                .setMessage("Credit-card bill payments and money moved between your own accounts.\n\n" +
                    "They aren't spending: the card purchases themselves are already counted, and a " +
                    "transfer to yourself only moves money. So they're kept out of your totals.")
                .setPositiveButton("OK", null)
                .show()
        }
        binding.homePayeeBanner.setOnClickListener {
            NewPayeeSheet().show(childFragmentManager, NewPayeeSheet.TAG)
        }
        // Refresh when the sheet files payees.
        childFragmentManager.setFragmentResultListener(NewPayeeSheet.RESULT, viewLifecycleOwner) { _, _ -> load() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                RealmService.getInstance().expenseChanges().collect { load() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? AppCompatActivity)?.supportActionBar?.hide()
        if (_binding != null) {
            binding.bottomNav.selectedItemId = R.id.tab_home
            load()
        }
    }

    // The "which category is this new shop?" prompt is a notification (Android 13+ asks).
    private val notificationPermission =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }

    private fun askNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
        val permission = android.Manifest.permission.POST_NOTIFICATIONS
        if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), permission)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(permission)
    }

    private fun period(now: Long): kotlin.Pair<Long, Long> = customRange ?: Insights.monthRange(now, monthOffset)

    private fun load() {
        if (_binding == null) return
        val now = System.currentTimeMillis()
        val (start, end) = period(now)
        val realm = RealmService.getInstance()
        val rows = realm.getExpensesByDateRange(start, end).map { it.toRow() }
        val budget = AppPrefs.monthlyBudget(requireContext())
            ?: realm.getAllBudgets().sumOf { it.amount }.takeIf { it > 0 }
        val stats = HomeStatsCalc.compute(rows, budget, now, start, end)

        // Header
        val cal = Calendar.getInstance().apply { timeInMillis = start }
        val sameYear = cal.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)
        val monthName = cal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.ENGLISH) ?: ""
        binding.homeMonthText.text = when {
            customRange != null -> rangeLabel(start, end)
            sameYear -> monthName
            else -> "$monthName ${cal.get(Calendar.YEAR)}"
        }
        binding.homeNextMonth.isEnabled = customRange != null || monthOffset < 0
        binding.homeNextMonth.alpha = if (binding.homeNextMonth.isEnabled) 1f else 0.3f
        binding.homeSpentLabel.text = when {
            customRange != null -> "Spent in these days"
            monthOffset == 0 -> "Spent this month"
            else -> "Spent in $monthName"
        }

        // Hero
        binding.homeSpentText.text = money.format(stats.spent)
        if (stats.budget != null) {
            binding.homeBudgetText.text = "Budget ${money.format(stats.budget)}"
            binding.homeBudgetProgress.visibility = View.VISIBLE
            binding.homeBudgetProgress.setProgressCompat((stats.usedPercent ?: 0).coerceIn(0, 100), false)
            val over = stats.spent > stats.budget
            binding.homeBudgetProgress.setIndicatorColor(
                requireContext().getColor(if (over || (stats.usedPercent ?: 0) >= 90) R.color.warn_bar else R.color.mint))
            binding.homeUsedText.text = "${stats.usedPercent}% used"
            binding.homeDailyText.text = when {
                over -> "Over budget by ${money.format(stats.spent - stats.budget)}"
                stats.dailyLeft != null ->
                    "${money.format(stats.dailyLeft)} a day left · ${stats.daysLeft} day${if (stats.daysLeft == 1) "" else "s"}"
                else -> "${money.format(stats.budget - stats.spent)} under budget"
            }
        } else {
            binding.homeBudgetText.text = ""
            binding.homeBudgetProgress.visibility = View.GONE
            binding.homeUsedText.text = ""
            binding.homeDailyText.text = "Set a monthly budget to see what you can spend per day ›"
        }

        // Tiles
        binding.homeIncomeText.text = money.format(stats.income)
        binding.homeSavedText.text = money.format(stats.saved)
        binding.homeNotCountedText.text = money.format(stats.notCounted)

        // New payees
        val todo = PayeeSortFragment.unsorted()
        if (todo.isEmpty()) {
            binding.homePayeeBanner.visibility = View.GONE
        } else {
            binding.homePayeeBanner.visibility = View.VISIBLE
            val total = todo.sumOf { it.total }
            binding.homePayeeBannerText.text = if (todo.size == 1)
                "1 new payee needs a category · ${money.format(total)}"
            else "${todo.size} new payees need a category · ${money.format(total)}"
        }

        // Coming up: unpaid bills due within 10 days (or overdue)
        binding.homeBillsList.removeAllViews()
        val bills = com.androplus.pwdmgr.services.Bills.statuses(requireContext())
        val soon = bills.filter { !it.paid && it.daysLeft <= 10 }.take(3)
        val inflaterBills = LayoutInflater.from(requireContext())
        if (soon.isEmpty()) {
            val row = inflaterBills.inflate(R.layout.home_recent_row, binding.homeBillsList, false)
            row.findViewById<TextView>(R.id.recentInitials).visibility = View.GONE
            row.findViewById<TextView>(R.id.recentTitle).text =
                if (bills.isEmpty()) "Add EMIs, rent and bills to get reminders" else "Nothing due in the next 10 days"
            row.findViewById<TextView>(R.id.recentSubtitle).text =
                if (bills.isEmpty()) "Tap to add your first one" else "${bills.size} reminder${if (bills.size == 1) "" else "s"} set"
            row.findViewById<TextView>(R.id.recentAmount).text = "›"
            row.setOnClickListener { findNavController().navigate(R.id.BillsFragment) }
            binding.homeBillsList.addView(row)
        } else soon.forEach { st ->
            val row = inflaterBills.inflate(R.layout.home_recent_row, binding.homeBillsList, false)
            row.findViewById<TextView>(R.id.recentInitials).apply {
                text = st.reminder.kind.take(1)
                setTextColor(requireContext().getColor(if (st.daysLeft < 0) R.color.expense_red else R.color.accent))
            }
            row.findViewById<TextView>(R.id.recentTitle).text = st.reminder.name
            row.findViewById<TextView>(R.id.recentSubtitle).apply {
                text = com.androplus.pwdmgr.utils.ReminderMath.label(st)
                setTextColor(requireContext().getColor(when {
                    st.daysLeft < 0 -> R.color.expense_red
                    st.daysLeft <= 3 -> R.color.warn_fg
                    else -> R.color.ink_muted
                }))
            }
            row.findViewById<TextView>(R.id.recentAmount).text = st.reminder.amount?.let { money.format(it) } ?: "Varies"
            row.setOnClickListener { findNavController().navigate(R.id.BillsFragment) }
            binding.homeBillsList.addView(row)
        }

        // Where it went
        val inflater = LayoutInflater.from(requireContext())
        binding.homeShareBar.removeAllViews()
        binding.homeTopList.removeAllViews()
        binding.homeNoSpendText.visibility = if (stats.top.isEmpty()) View.VISIBLE else View.GONE
        binding.homeShareBar.visibility = if (stats.top.isEmpty()) View.GONE else View.VISIBLE
        val shown = stats.top.take(4)
        val restShare = stats.top.drop(4).sumOf { it.share }
        val gap = (2 * resources.displayMetrics.density).toInt()
        shown.forEachIndexed { i, c ->
            binding.homeShareBar.addView(View(requireContext()).apply {
                setBackgroundColor(parseColor(c.color))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, c.share.toFloat())
                    .apply { if (i > 0) marginStart = gap }
            })
        }
        if (restShare > 0) {
            binding.homeShareBar.addView(View(requireContext()).apply {
                setBackgroundColor(requireContext().getColor(R.color.outline))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, restShare.toFloat())
                    .apply { marginStart = gap }
            })
        }
        stats.top.take(3).forEach { c ->
            val row = inflater.inflate(R.layout.home_top_row, binding.homeTopList, false)
            row.findViewById<View>(R.id.topDot).backgroundTintList = ColorStateList.valueOf(parseColor(c.color))
            row.findViewById<TextView>(R.id.topName).text = c.name
            row.findViewById<TextView>(R.id.topShare).text = "${(c.share * 100).toInt()}%"
            row.findViewById<TextView>(R.id.topAmount).text = money.format(c.amount)
            row.setOnClickListener { binding.bottomNav.selectedItemId = R.id.tab_spending }
            binding.homeTopList.addView(row)
        }

        // Recent
        binding.homeRecentList.removeAllViews()
        val recent = rows.sortedByDescending { it.timeMillis }.take(5)
        binding.homeRecentEmpty.visibility = if (recent.isEmpty()) View.VISIBLE else View.GONE
        recent.forEachIndexed { i, r ->
            val row = inflater.inflate(R.layout.home_recent_row, binding.homeRecentList, false)
            row.findViewById<TextView>(R.id.recentInitials).text = initials(r.title)
            row.findViewById<TextView>(R.id.recentTitle).text = r.title
            val via = r.source?.let { if (it == "Auto") "" else " · $it" } ?: " · added by you"
            row.findViewById<TextView>(R.id.recentSubtitle).text =
                "${r.categoryName.ifBlank { "Other" }} · ${timeFormat.format(Date(r.timeMillis))}$via"
            row.findViewById<TextView>(R.id.recentAmount).apply {
                text = (if (r.isIncome) "+" else "−") + money.format(r.amount)
                setTextColor(requireContext().getColor(if (r.isIncome) R.color.accent else R.color.ink))
            }
            if (i > 0) row.setBackgroundResource(0)
            row.setOnClickListener {
                findNavController().navigate(R.id.ExpenseAddFragment, Bundle().apply { putString("expense_id", r.id) })
            }
            binding.homeRecentList.addView(row)
        }
    }

    /** One number for the whole month; per-category budgets stay under More › Budgets. */
    private fun editBudget() {
        val ctx = requireContext()
        val input = android.widget.EditText(ctx).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "e.g. 40000"
            AppPrefs.monthlyBudget(ctx)?.let { setText(it.toLong().toString()) }
        }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val box = android.widget.FrameLayout(ctx).apply { setPadding(pad, pad / 2, pad, 0); addView(input) }
        android.app.AlertDialog.Builder(ctx)
            .setTitle("Monthly budget (₹)")
            .setMessage("How much do you want to spend in a month? Home then shows what's left per day.")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                AppPrefs.setMonthlyBudget(ctx, input.text.toString().toDoubleOrNull())
                load()
            }
            .setNeutralButton("Remove") { _, _ -> AppPrefs.setMonthlyBudget(ctx, null); load() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickDates() {
        val builder = MaterialDatePicker.Builder.dateRangePicker().setTitleText("Show spending from")
        customRange?.let { (s, e) -> builder.setSelection(AndroidPair(toUtcDay(s), toUtcDay(e))) }
        val picker = builder.build()
        picker.addOnPositiveButtonClickListener { sel ->
            val first = sel.first ?: return@addOnPositiveButtonClickListener
            val last = sel.second ?: first
            customRange = localDayStart(first) to (localDayStart(last) + 86_400_000L - 1)
            load()
        }
        picker.show(childFragmentManager, "home_dates")
    }

    private fun rangeLabel(s: Long, e: Long): String {
        val a = dayFormat.format(Date(s)); val b = dayFormat.format(Date(e))
        return if (a == b) a else "$a – $b"
    }

    private fun localDayStart(utcMidnight: Long): Long {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMidnight }
        return Calendar.getInstance().apply {
            clear(); set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }

    private fun toUtcDay(local: Long): Long {
        val c = Calendar.getInstance().apply { timeInMillis = local }
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear(); set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }

    private fun initials(name: String): String =
        name.split(Regex("""[\s._@-]+""")).filter { it.isNotBlank() && it[0].isLetterOrDigit() }
            .take(2).joinToString("") { it.take(1).uppercase() }.ifEmpty { "₹" }

    private fun parseColor(hex: String) =
        try { Color.parseColor(hex) } catch (e: IllegalArgumentException) { Color.GRAY }

    private fun com.androplus.pwdmgr.model.ExpenseTransaction.toRow() = ExpenseRow(
        id = _id.toHexString(),
        title = ExpenseGrouping.displayTitle(shortDescription, categoryName),
        source = DuplicateDetector.sourceOf(shortDescription),
        amount = amount,
        isIncome = type == "INCOME",
        timeMillis = date,
        categoryName = categoryName,
        categoryColor = categoryColor,
    )

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
