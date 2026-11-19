package com.androplus.pwdmgr.fragment.expense

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.util.Pair as AndroidPair
import androidx.fragment.app.Fragment
import com.androplus.pwdmgr.R
import com.androplus.pwdmgr.databinding.DialogBillBinding
import com.androplus.pwdmgr.databinding.FragmentBillsBinding
import com.androplus.pwdmgr.services.Bills
import com.androplus.pwdmgr.services.RealmService
import com.androplus.pwdmgr.utils.DueStatus
import com.androplus.pwdmgr.utils.DuplicateDetector
import com.androplus.pwdmgr.utils.ExpenseGrouping
import com.androplus.pwdmgr.utils.ExpenseRow
import com.androplus.pwdmgr.utils.Insights
import com.androplus.pwdmgr.utils.Reminder
import com.androplus.pwdmgr.utils.ReminderMath
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.MaterialDatePicker
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** EMIs, loans, insurance, rent and bills: when each is due and whether it's paid. */
class BillsFragment : Fragment() {

    private var _binding: FragmentBillsBinding? = null
    private val binding get() = _binding!!
    private val money = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply { maximumFractionDigits = 0 }
    private val dayFormat = SimpleDateFormat("d MMM", Locale("en", "IN"))
    private val repeats = listOf(1 to "Monthly", 3 to "Quarterly", 6 to "Every 6 months", 12 to "Yearly")
    private val befores = listOf(0 to "On the day", 1 to "1 day before", 2 to "2 days before", 3 to "3 days before", 7 to "A week before")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBillsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.billsAdd.setOnClickListener { edit(null) }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        if (_binding == null) return
        val ctx = requireContext()
        val statuses = Bills.statuses(ctx)
        val monthEnd = Insights.monthRange(System.currentTimeMillis(), 0).second
        val dueThisMonth = statuses.filter { !it.paid && it.dueMillis <= monthEnd }
        binding.billsSummary.text = when {
            statuses.isEmpty() -> "Never miss an EMI or bill"
            dueThisMonth.isEmpty() -> "All paid for this month"
            else -> "${dueThisMonth.size} still due this month" +
                dueThisMonth.mapNotNull { it.reminder.amount }.sum().takeIf { it > 0 }?.let { " · ${money.format(it)}" }.orEmpty()
        }
        binding.billsList.removeAllViews()
        binding.billsEmpty.visibility = if (statuses.isEmpty()) View.VISIBLE else View.GONE
        statuses.forEach { binding.billsList.addView(row(it)) }
        suggestions(statuses.map { it.reminder })
    }

    private fun row(s: DueStatus): View {
        val v = LayoutInflater.from(requireContext()).inflate(R.layout.item_bill, binding.billsList, false)
        val r = s.reminder
        val ctx = requireContext()
        v.findViewById<ImageView>(R.id.billIcon).apply {
            setImageResource(Bills.iconFor(r.kind))
            imageTintList = ColorStateList.valueOf(ctx.getColor(R.color.accent))
        }
        v.findViewById<TextView>(R.id.billName).text = r.name
        val repeat = repeats.firstOrNull { it.first == r.everyMonths }?.second ?: "Monthly"
        v.findViewById<TextView>(R.id.billDetail).text = "${r.kind} · $repeat · due ${dayFormat.format(Date(s.dueMillis))}"
        v.findViewById<TextView>(R.id.billAmount).text = r.amount?.let { money.format(it) } ?: "Varies"
        v.findViewById<TextView>(R.id.billStatus).apply {
            text = ReminderMath.label(s)
            setTextColor(ctx.getColor(when {
                s.paid -> R.color.accent
                s.daysLeft < 0 -> R.color.expense_red
                s.daysLeft <= 3 -> R.color.warn_bar
                else -> R.color.ink_muted
            }))
        }
        v.contentDescription = "${r.name}, ${v.findViewById<TextView>(R.id.billAmount).text}, ${ReminderMath.label(s)}"
        v.setOnClickListener { options(s) }
        return v
    }

    private fun options(s: DueStatus) {
        val items = mutableListOf<Pair<String, () -> Unit>>()
        if (!s.paid) items += "Mark paid for ${dayFormat.format(Date(s.dueMillis))}" to {
            Bills.markPaid(requireContext(), s.reminder.id, s.dueMillis); load()
        }
        items += "Edit" to { edit(s.reminder) }
        items += "Delete" to {
            AlertDialog.Builder(requireContext())
                .setTitle("Delete \"${s.reminder.name}\"?")
                .setPositiveButton("Delete") { _, _ -> Bills.store(requireContext()).delete(s.reminder.id); load() }
                .setNegativeButton("Cancel", null)
                .show()
        }
        AlertDialog.Builder(requireContext())
            .setTitle(s.reminder.name)
            .setItems(items.map { it.first }.toTypedArray()) { _, i -> items[i].second() }
            .show()
    }

    /** Payments that repeat monthly and have no reminder yet: one tap to add. */
    private fun suggestions(existing: List<Reminder>) {
        binding.billsSuggestions.removeAllViews()
        val rows = RealmService.getInstance().getAllExpenses().map {
            ExpenseRow(it._id.toHexString(), ExpenseGrouping.displayTitle(it.shortDescription, it.categoryName),
                DuplicateDetector.sourceOf(it.shortDescription), it.amount, it.type == "INCOME", it.date,
                it.categoryName, it.categoryColor)
        }
        val known = existing.flatMap { r -> r.payeeWords.split(',').map { it.trim().lowercase() } + r.name.lowercase() }
        val found = Insights.recurring(rows, System.currentTimeMillis())
            .filter { rec -> known.none { it.isNotEmpty() && (it in rec.name.lowercase() || rec.name.lowercase() in it) } }
            .take(5)
        binding.billsSuggestTitle.visibility = if (found.isEmpty()) View.GONE else View.VISIBLE
        found.forEach { rec ->
            val v = LayoutInflater.from(requireContext()).inflate(R.layout.item_bill, binding.billsSuggestions, false)
            v.findViewById<ImageView>(R.id.billIcon).apply {
                setImageResource(R.drawable.ic_plus)
                imageTintList = ColorStateList.valueOf(requireContext().getColor(R.color.accent))
            }
            v.findViewById<TextView>(R.id.billName).text = rec.name
            v.findViewById<TextView>(R.id.billDetail).text = "Paid around the ${rec.dayOfMonth} · ${rec.months} months in a row"
            v.findViewById<TextView>(R.id.billAmount).text = money.format(rec.typicalAmount)
            v.findViewById<TextView>(R.id.billStatus).apply {
                text = "Add reminder"
                setTextColor(requireContext().getColor(R.color.accent))
            }
            v.setOnClickListener { edit(Bills.fromRecurring(rec.name, rec.typicalAmount, rec.dayOfMonth), isNew = true) }
            binding.billsSuggestions.addView(v)
        }
    }

    /** Add (null) or edit a reminder. */
    private fun edit(existing: Reminder?, isNew: Boolean = existing == null) {
        val ctx = requireContext()
        val d = DialogBillBinding.inflate(LayoutInflater.from(ctx))
        val start = existing ?: Reminder(
            id = Bills.newId(), name = "", kind = "EMI", amount = null,
            dayOfMonth = Calendar.getInstance().get(Calendar.DAY_OF_MONTH),
            anchorMonth = ReminderMath.monthIndex(System.currentTimeMillis()),
            createdAt = System.currentTimeMillis(),
        )
        var kind = start.kind
        var every = start.everyMonths
        var before = start.remindDaysBefore
        var nextDue = ReminderMath.status(start, System.currentTimeMillis(), emptyList()).dueMillis

        d.billNameInput.setText(start.name)
        start.amount?.let { d.billAmountInput.setText(if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()) }
        d.billWordsInput.setText(start.payeeWords)
        fun showDue() { d.billDueButton.text = "Due " + SimpleDateFormat("d MMM yyyy", Locale("en", "IN")).format(Date(nextDue)) }
        showDue()

        ReminderMath.KINDS.forEach { k ->
            d.billKinds.addView(choiceChip(k, Bills.iconFor(k), k == kind) { kind = k })
        }
        repeats.forEach { (n, label) -> d.billRepeat.addView(choiceChip(label, null, n == every) { every = n }) }
        befores.forEach { (n, label) -> d.billBefore.addView(choiceChip(label, null, n == before) { before = n }) }

        d.billDueButton.setOnClickListener {
            val picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Next due date")
                .setSelection(toUtcDay(nextDue))
                .build()
            picker.addOnPositiveButtonClickListener { sel -> nextDue = localDayStart(sel); showDue() }
            picker.show(childFragmentManager, "bill_due")
        }

        AlertDialog.Builder(ctx)
            .setTitle(if (isNew) "New reminder" else "Edit reminder")
            .setView(d.root)
            .setPositiveButton("Save") { _, _ ->
                val name = d.billNameInput.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) {
                    Toast.makeText(ctx, "Give it a name", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val due = Calendar.getInstance().apply { timeInMillis = nextDue }
                Bills.store(ctx).save(start.copy(
                    name = name,
                    kind = kind,
                    amount = d.billAmountInput.text?.toString()?.toDoubleOrNull()?.takeIf { it > 0 },
                    dayOfMonth = due.get(Calendar.DAY_OF_MONTH),
                    everyMonths = every,
                    anchorMonth = ReminderMath.monthIndex(nextDue),
                    remindDaysBefore = before,
                    payeeWords = d.billWordsInput.text?.toString()?.trim().orEmpty(),
                ))
                Bills.notifyDue(ctx)   // due today or soon: remind right away
                load()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun choiceChip(label: String, icon: Int?, checked: Boolean, onPick: () -> Unit) = Chip(requireContext()).apply {
        id = View.generateViewId()
        text = label
        isCheckable = true
        isCheckedIconVisible = false
        isChecked = checked
        if (icon != null) {
            chipIcon = ContextCompat.getDrawable(requireContext(), icon)
            chipIconTint = ColorStateList.valueOf(requireContext().getColor(R.color.accent))
            isChipIconVisible = true
        }
        chipBackgroundColor = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(requireContext().getColor(R.color.accent_soft), android.graphics.Color.WHITE))
        chipStrokeWidth = resources.displayMetrics.density
        chipStrokeColor = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(requireContext().getColor(R.color.accent), requireContext().getColor(R.color.outline)))
        setOnClickListener { onPick() }
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
