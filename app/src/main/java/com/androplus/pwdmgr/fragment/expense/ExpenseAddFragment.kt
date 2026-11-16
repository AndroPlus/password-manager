package com.androplus.pwdmgr.fragment.expense

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.androplus.pwdmgr.R
import com.androplus.pwdmgr.databinding.ExpenseAddLayoutBinding
import com.androplus.pwdmgr.model.ExpenseCategory
import com.androplus.pwdmgr.model.ExpenseTransaction
import com.androplus.pwdmgr.services.RealmService
import com.androplus.pwdmgr.services.AutoTransactionRecorder
import com.androplus.pwdmgr.utils.DuplicateDetector
import com.androplus.pwdmgr.utils.ExpenseGrouping
import kotlinx.coroutines.launch
import org.mongodb.kbson.ObjectId
import java.text.SimpleDateFormat
import java.util.*
import androidx.activity.result.contract.ActivityResultContracts
import com.bumptech.glide.Glide
import android.net.Uri

class ExpenseAddFragment : Fragment() {

    private var _binding: ExpenseAddLayoutBinding? = null
    private val binding get() = _binding!!
    private var selectedCalendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    private lateinit var categories: List<ExpenseCategory>
    private var editingExpenseId: String? = null
    private var currentReceiptPath: String = ""
    // Payee name and category as captured, to learn from the user's correction.
    private var originalShortDescription: String = ""
    private var originalCategoryName: String = ""

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            currentReceiptPath = it.toString()
            binding.receiptImageView.visibility = View.VISIBLE
            Glide.with(this)
                .load(it)
                .into(binding.receiptImageView)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ExpenseAddLayoutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadCategories()
        setupUI()
        handleArguments()
    }

    private var selectedCategoryIndex = 0
    // "SMS", "GPay"…: kept on save so an edited auto entry stays recognisable.
    private var sourceTag: String? = null

    /** Every category as a tappable chip with its icon. */
    private fun loadCategories() {
        categories = RealmService.getInstance().getAllCategories()
        val group = binding.categoryChips
        group.removeAllViews()
        categories.forEachIndexed { i, cat ->
            val color = com.androplus.pwdmgr.ui.CategoryStyle.color(cat.color)
            val chip = com.google.android.material.chip.Chip(requireContext()).apply {
                id = View.generateViewId()
                text = cat.name
                isCheckable = true
                isCheckedIconVisible = false
                chipIcon = androidx.core.content.ContextCompat.getDrawable(
                    requireContext(), com.androplus.pwdmgr.ui.CategoryStyle.icon(cat.name))
                chipIconTint = android.content.res.ColorStateList.valueOf(com.androplus.pwdmgr.ui.CategoryStyle.ink(color))
                isChipIconVisible = true
                chipBackgroundColor = android.content.res.ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(com.androplus.pwdmgr.ui.CategoryStyle.tint(color), android.graphics.Color.WHITE))
                chipStrokeWidth = resources.displayMetrics.density
                chipStrokeColor = android.content.res.ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(com.androplus.pwdmgr.ui.CategoryStyle.ink(color), requireContext().getColor(R.color.outline)))
                setTextColor(requireContext().getColor(R.color.ink))
                tag = i
                setOnClickListener { selectedCategoryIndex = i }
            }
            group.addView(chip)
        }
        selectCategory(categories.indexOfFirst { it.name == "Other" }.takeIf { it >= 0 } ?: 0)
    }

    private fun selectCategory(index: Int) {
        if (index !in categories.indices) return
        selectedCategoryIndex = index
        (binding.categoryChips.getChildAt(index) as? com.google.android.material.chip.Chip)?.isChecked = true
    }

    private fun setupUI() {
        binding.dateButton.text = dateFormat.format(selectedCalendar.time)
        binding.dateButton.setOnClickListener { showDatePicker() }

        binding.saveButton.setOnClickListener { saveExpense() }
        binding.cancelButton.setOnClickListener { findNavController().popBackStack() }
        
        binding.manageCategoriesButton.setOnClickListener {
            findNavController().navigate(R.id.action_manage_categories)
        }

        binding.attachReceiptButton.setOnClickListener {
             pickImage.launch("image/*")
        }
    }

    private fun handleArguments() {
        editingExpenseId = arguments?.getString("expense_id")
        if (editingExpenseId != null) {
            val editingExpense = RealmService.getInstance().getAllExpenses().find { it._id.toHexString() == editingExpenseId }
            editingExpense?.let { expense ->
                originalShortDescription = expense.shortDescription
                originalCategoryName = expense.categoryName
                binding.amountEditText.setText(expense.amount.toString())
                binding.descriptionEditText.setText(expense.description)
                sourceTag = DuplicateDetector.sourceOf(expense.shortDescription)
                binding.shortDescriptionEditText.setText(
                    expense.shortDescription.replace(Regex("""^\[[A-Za-z]+]\s*"""), ""))
                if (sourceTag != null) binding.descriptionLayout.hint = "Original message"
                selectedCalendar.timeInMillis = expense.date
                binding.dateButton.text = dateFormat.format(selectedCalendar.time)
                
                if (expense.type == "INCOME") {
                    binding.radioIncome.isChecked = true
                } else {
                    binding.radioExpense.isChecked = true
                }
                
                if (expense.receiptPath.isNotEmpty()) {
                    currentReceiptPath = expense.receiptPath
                    binding.receiptImageView.visibility = View.VISIBLE
                    Glide.with(this)
                        .load(Uri.parse(expense.receiptPath))
                        .into(binding.receiptImageView)
                }

                val catIndex = categories.indexOfFirst { it._id.toHexString() == expense.categoryId }
                    .takeIf { it >= 0 } ?: categories.indexOfFirst { it.name == expense.categoryName }
                if (catIndex != -1) selectCategory(catIndex)
                
                binding.saveButton.text = getString(R.string.save)
            }
        }
    }

    private fun showDatePicker() {
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                selectedCalendar.set(year, month, dayOfMonth)
                binding.dateButton.text = dateFormat.format(selectedCalendar.time)
            },
            selectedCalendar.get(Calendar.YEAR),
            selectedCalendar.get(Calendar.MONTH),
            selectedCalendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun saveExpense() {
        val amountStr = binding.amountEditText.text.toString()
        if (amountStr.isEmpty()) {
            Toast.makeText(context, "Please enter amount", Toast.LENGTH_SHORT).show()
            return
        }

        val amount = amountStr.toDoubleOrNull() ?: 0.0
        val category = categories.getOrNull(selectedCategoryIndex) ?: run {
            Toast.makeText(context, "Pick a category", Toast.LENGTH_SHORT).show()
            return
        }
        val description = binding.descriptionEditText.text.toString()
        val typedName = binding.shortDescriptionEditText.text.toString().trim()
        val shortDescription = sourceTag?.let { DuplicateDetector.tag(it, typedName) } ?: typedName
        val type = if (binding.radioIncome.isChecked) "INCOME" else "EXPENSE"

        val expenseToSave = ExpenseTransaction().apply {
            if (editingExpenseId != null) {
                _id = ObjectId(editingExpenseId!!)
            }
            this.amount = amount
            this.categoryId = category._id.toHexString()
            this.categoryName = category.name
            this.categoryColor = category.color
            this.description = description
            this.shortDescription = shortDescription
            this.date = selectedCalendar.timeInMillis
            this.type = type
            this.receiptPath = currentReceiptPath
        }

        lifecycleScope.launch {
            val success = if (editingExpenseId != null) {
                RealmService.getInstance().updateExpense(expenseToSave)
            } else {
                RealmService.getInstance().createExpense(expenseToSave)
            }

            if (success) {
                rememberCategory(shortDescription, category.name, type == "EXPENSE")
                findNavController().popBackStack()
            } else {
                Toast.makeText(context, "Error saving expense", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * When the user picks a category for an entry, use it automatically for later
     * SMS / notifications from the same payee, and for earlier ones too.
     */
    private fun rememberCategory(shortDescription: String, categoryName: String, isExpense: Boolean) {
        val isAuto = DuplicateDetector.sourceOf(originalShortDescription) != null
        // Auto entries: learn only when the category was actually changed.
        if (isAuto && categoryName == originalCategoryName) return
        val payee = if (isAuto) originalShortDescription else shortDescription
        if (payee.isBlank()) return
        val appContext = requireContext().applicationContext
        val name = ExpenseGrouping.displayTitle(payee, categoryName)
        // Answered here instead of on the "new payee" notification.
        androidx.core.app.NotificationManagerCompat.from(appContext)
            .cancel(com.androplus.pwdmgr.services.CategoryPrompt.notificationId(payee))
        AutoTransactionRecorder.init(appContext)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val moved = AutoTransactionRecorder.learn(payee, isExpense, categoryName)
            // A renamed payee should also match under its new name.
            if (isAuto && shortDescription.isNotBlank() && shortDescription != originalShortDescription) {
                AutoTransactionRecorder.learn(shortDescription, isExpense, categoryName)
            }
            if (moved == null) return@launch   // too generic to learn, e.g. "UPI payment"
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                val extra = if (moved > 0) " Moved $moved earlier one(s) too." else ""
                Toast.makeText(appContext, "$name will now go to $categoryName automatically.$extra",
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
