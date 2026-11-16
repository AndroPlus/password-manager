package com.androplus.pwdmgr.fragment.expense

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.androplus.pwdmgr.R
import com.androplus.pwdmgr.databinding.FragmentInsightsBinding
import com.androplus.pwdmgr.services.RealmService
import com.androplus.pwdmgr.utils.DuplicateDetector
import com.androplus.pwdmgr.utils.ExpenseGrouping
import com.androplus.pwdmgr.utils.ExpenseRow
import com.androplus.pwdmgr.utils.Insights
import java.text.NumberFormat
import java.util.Locale

/** Six months of spent vs earned, payments that repeat, and the most-paid payees. */
class InsightsFragment : Fragment() {

    private var _binding: FragmentInsightsBinding? = null
    private val binding get() = _binding!!
    private val money = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply { maximumFractionDigits = 0 }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentInsightsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ExpenseTabs.setup(this, binding.bottomNav, R.id.tab_insights)
    }

    override fun onResume() {
        super.onResume()
        (activity as? AppCompatActivity)?.supportActionBar?.hide()
        binding.bottomNav.selectedItemId = R.id.tab_insights
        load()
    }

    private fun load() {
        val now = System.currentTimeMillis()
        val rows = RealmService.getInstance().getAllExpenses().map {
            ExpenseRow(
                id = it._id.toHexString(),
                title = ExpenseGrouping.displayTitle(it.shortDescription, it.categoryName),
                source = DuplicateDetector.sourceOf(it.shortDescription),
                amount = it.amount,
                isIncome = it.type == "INCOME",
                timeMillis = it.date,
                categoryName = it.categoryName,
                categoryColor = it.categoryColor,
            )
        }
        drawBars(Insights.monthly(rows, now))

        // Repeats every month
        binding.insightsRecurring.removeAllViews()
        val recurring = Insights.recurring(rows, now)
        if (recurring.isEmpty()) {
            binding.insightsRecurring.addView(note("Nothing yet. Payments seen in 3 of the last 4 months show up here."))
        } else recurring.take(6).forEach { r ->
            binding.insightsRecurring.addView(line(r.name, "Around the ${ordinal(r.dayOfMonth)} · ${r.months} months", money.format(r.typicalAmount)))
        }

        // Most paid this month
        binding.insightsTop.removeAllViews()
        val (start, end) = Insights.monthRange(now, 0)
        val top = Insights.topPayees(rows.filter { it.timeMillis in start..end })
        if (top.isEmpty()) {
            binding.insightsTop.addView(note("No payments this month yet."))
        } else top.forEachIndexed { i, p ->
            binding.insightsTop.addView(line("${i + 1}.  ${p.name}", "${p.count} payment${if (p.count == 1) "" else "s"}", money.format(p.total)))
        }
    }

    private fun drawBars(bars: List<com.androplus.pwdmgr.utils.MonthBar>) {
        binding.insightsBars.removeAllViews()
        binding.insightsLabels.removeAllViews()
        val max = bars.maxOf { maxOf(it.spent, it.earned) }.takeIf { it > 0 } ?: 1.0
        val dp = resources.displayMetrics.density
        val chartHeight = (150 * dp).toInt()
        bars.forEachIndexed { i, b ->
            val group = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.BOTTOM
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                    .apply { if (i > 0) marginStart = (10 * dp).toInt() }
                contentDescription = "${b.label}: spent ${money.format(b.spent)}, earned ${money.format(b.earned)}"
            }
            listOf(b.spent to requireContext().getColor(R.color.ink), b.earned to Color.parseColor("#99D5CB"))
                .forEachIndexed { j, (value, color) ->
                    group.addView(View(requireContext()).apply {
                        setBackgroundColor(color)
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        val h = ((value / max) * chartHeight).toInt().coerceAtLeast(if (value > 0) (2 * dp).toInt() else 0)
                        layoutParams = LinearLayout.LayoutParams(0, h, 1f).apply { if (j > 0) marginStart = (3 * dp).toInt() }
                    })
                }
            binding.insightsBars.addView(group)
            binding.insightsLabels.addView(TextView(requireContext()).apply {
                text = b.label
                gravity = Gravity.CENTER
                textSize = 12f
                setTextColor(requireContext().getColor(if (i == bars.lastIndex) R.color.ink else R.color.ink_muted))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { if (i > 0) marginStart = (10 * dp).toInt() }
            })
        }

        // Judge only finished months; a half-over month makes any ratio misleading.
        val finished = bars.dropLast(1).filter { it.spent > 0 || it.earned > 0 }
        val current = bars.last()
        binding.insightsSummary.text = when {
            bars.all { it.spent == 0.0 && it.earned == 0.0 } -> "No transactions in the last 6 months yet."
            finished.size < 2 ->
                "Insights get sharper after two full months of data. So far in ${current.label}: " +
                    "${money.format(current.spent)} spent, ${money.format(current.earned)} earned."
            else -> {
                val earned = finished.sumOf { it.earned }
                val top = finished.maxBy { it.spent }
                val kept = if (earned > 0) "You kept ${((earned - finished.sumOf { it.spent }) / earned * 100).toInt()}% " +
                    "of your income over the last ${finished.size} full months. " else ""
                kept + "Highest spending: ${top.label} (${money.format(top.spent)})."
            }
        }
    }

    private fun line(title: String, subtitle: String, amount: String): View {
        val v = LayoutInflater.from(requireContext()).inflate(R.layout.home_recent_row, binding.insightsTop, false)
        v.findViewById<TextView>(R.id.recentInitials).visibility = View.GONE
        v.findViewById<TextView>(R.id.recentTitle).text = title
        v.findViewById<TextView>(R.id.recentSubtitle).text = subtitle
        v.findViewById<TextView>(R.id.recentAmount).text = amount
        v.background = null
        return v
    }

    private fun note(text: String) = TextView(requireContext()).apply {
        this.text = text
        textSize = 13f
        setTextColor(requireContext().getColor(R.color.ink_muted))
        setPadding(0, (6 * resources.displayMetrics.density).toInt(), 0, (10 * resources.displayMetrics.density).toInt())
    }

    private fun ordinal(d: Int) = d.toString() + when {
        d in 11..13 -> "th"
        d % 10 == 1 -> "st"
        d % 10 == 2 -> "nd"
        d % 10 == 3 -> "rd"
        else -> "th"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
