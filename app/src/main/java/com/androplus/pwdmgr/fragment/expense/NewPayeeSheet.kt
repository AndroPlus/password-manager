package com.androplus.pwdmgr.fragment.expense

import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.androplus.pwdmgr.R
import com.androplus.pwdmgr.databinding.SheetNewPayeeBinding
import com.androplus.pwdmgr.services.AutoTransactionRecorder
import com.androplus.pwdmgr.services.RealmService
import com.androplus.pwdmgr.ui.CategoryStyle
import com.androplus.pwdmgr.utils.PayeeSorting
import com.androplus.pwdmgr.utils.PayeeSummary
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

/** Steps through payees without a category: tap one, Save, next. */
class NewPayeeSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "new_payee_sheet"
        const val RESULT = "new_payee_sheet_done"
    }

    private var _binding: SheetNewPayeeBinding? = null
    private val binding get() = _binding!!
    private val money = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply { maximumFractionDigits = 0 }

    private var queue: List<PayeeSummary> = emptyList()
    private var index = 0
    private var choices: MutableList<String> = mutableListOf()
    private var selected: String? = null
    private var filed = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetNewPayeeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        AutoTransactionRecorder.init(requireContext())
        queue = PayeeSortFragment.unsorted()
        binding.sheetSkip.setOnClickListener { next() }
        binding.sheetSave.setOnClickListener { save() }
        show()
    }

    private fun show() {
        if (index >= queue.size) {
            finish()
            return
        }
        val p = queue[index]
        binding.sheetProgressText.text = "${index + 1} of ${queue.size} new payee${if (queue.size == 1) "" else "s"}"
        binding.sheetProgress.max = queue.size
        binding.sheetProgress.setProgressCompat(index + 1, true)
        binding.sheetInitials.text = p.name.split(Regex("""[\s._@-]+""")).filter { it.isNotBlank() }
            .take(2).joinToString("") { it.take(1).uppercase() }
        binding.sheetName.text = p.name
        binding.sheetDetail.text = if (p.count == 1) "1 payment" else "${p.count} payments"
        binding.sheetAmount.text = money.format(p.total)
        binding.sheetRemember.text = if (p.count > 1)
            "Always file ${p.name} here, including its ${p.count} payments"
        else "Always file ${p.name} here"

        val realm = RealmService.getInstance()
        val names = realm.getAllCategories().map { it.name }
        val past = realm.getAllExpenses().filter { it.type == "EXPENSE" }.map { it.categoryName }
        choices = PayeeSorting.quickChoices(p.categoryName, past, names, max = 5).toMutableList()
        selected = choices.firstOrNull()
        renderChoices()
        binding.sheetSave.text = if (index + 1 < queue.size) "Save · next payee" else "Save"
    }

    private fun renderChoices() {
        val rows = listOf(binding.sheetChoiceRow1, binding.sheetChoiceRow2)
        rows.forEach { it.removeAllViews() }
        val labels = choices + "More…"
        val gap = (10 * resources.displayMetrics.density).toInt()
        labels.forEachIndexed { i, label ->
            val isMore = i == labels.lastIndex
            val on = !isMore && label == selected
            val tv = TextView(requireContext()).apply {
                text = label
                gravity = Gravity.CENTER
                textSize = 12f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(gap / 2, gap / 2, gap / 2, gap / 3)
                val textColor = requireContext().getColor(if (on) R.color.accent else R.color.ink_soft)
                setTextColor(textColor)
                val icon = androidx.core.content.ContextCompat.getDrawable(
                    requireContext(), if (isMore) R.drawable.ic_cat_other else CategoryStyle.icon(label))?.mutate()
                icon?.setTint(textColor)
                val size = (22 * resources.displayMetrics.density).toInt()
                icon?.setBounds(0, 0, size, size)
                setCompoundDrawables(null, icon, null, null)
                compoundDrawablePadding = gap / 3
                setBackgroundResource(if (on) R.drawable.bg_choice_selected else R.drawable.bg_choice)
                isClickable = true
                isFocusable = true
                contentDescription = if (on) "$label, selected" else label
                setOnClickListener { if (isMore) pickAny() else { selected = label; renderChoices() } }
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                    .apply { if (i % 3 != 0) marginStart = gap }
            }
            rows[i / 3].addView(tv)
        }
    }

    private fun pickAny() {
        val names = RealmService.getInstance().getAllCategories().map { it.name }.filter { it != "Income" }
        AlertDialog.Builder(requireContext())
            .setTitle("Pick a category")
            .setItems(names.toTypedArray()) { _, which ->
                val name = names[which]
                if (name !in choices) {
                    if (choices.size >= 5) choices[choices.lastIndex] = name else choices.add(name)
                }
                selected = name
                renderChoices()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun save() {
        val p = queue.getOrNull(index) ?: return
        val category = selected ?: return
        val remember = binding.sheetRemember.isChecked
        binding.sheetSave.isEnabled = false
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { AutoTransactionRecorder.learn(p.payee, true, category, remember) }
            filed++
            if (_binding != null) {
                binding.sheetSave.isEnabled = true
                next()
            }
        }
    }

    private fun next() {
        index++
        show()
    }

    private fun finish() {
        if (filed > 0) {
            Toast.makeText(requireContext().applicationContext,
                "Filed $filed payee${if (filed == 1) "" else "s"}", Toast.LENGTH_SHORT).show()
        }
        setFragmentResult(RESULT, Bundle())
        dismissAllowingStateLoss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
