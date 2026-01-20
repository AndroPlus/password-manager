package com.androplus.pwdmgr.fragment.expense

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.androplus.pwdmgr.R
import com.androplus.pwdmgr.databinding.CategoryAddLayoutBinding
import com.androplus.pwdmgr.model.ExpenseCategory
import com.androplus.pwdmgr.services.RealmService
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch

class CategoryAddFragment : Fragment() {

    private var _binding: CategoryAddLayoutBinding? = null
    private val binding get() = _binding!!
    private var selectedColor: String = "#0D47A1"

    private val colorPresets = listOf(
        "#F44336", "#E91E63", "#9C27B0", "#673AB7", 
        "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4",
        "#009688", "#4CAF50", "#8BC34A", "#CDDC39",
        "#FFEB3B", "#FFC107", "#FF9800", "#FF5722"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = CategoryAddLayoutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupColorSelection()
        setupListeners()
    }

    private fun setupColorSelection() {
        colorPresets.forEachIndexed { index, colorHex ->
            val chip = Chip(requireContext()).apply {
                isCheckable = true
                chipCornerRadius = 100f
                chipMinHeight = 48f
                chipStartPadding = 12f
                chipEndPadding = 12f
                chipBackgroundColor = ColorStateList.valueOf(Color.parseColor(colorHex))
                setOnClickListener {
                    selectedColor = colorHex
                }
            }
            binding.colorChipGroup.addView(chip)
            if (index == 0) {
                chip.isChecked = true
                selectedColor = colorHex
            }
        }
    }

    private fun setupListeners() {
        binding.saveCategoryButton.setOnClickListener {
            saveCategory()
        }
    }

    private fun saveCategory() {
        val name = binding.categoryNameInput.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(context, R.string.enter_category_name, Toast.LENGTH_SHORT).show()
            return
        }

        val category = ExpenseCategory().apply {
            this.name = name
            this.color = selectedColor
            this.isDefault = false
            this.icon = "custom"
        }

        lifecycleScope.launch {
            val success = RealmService.getInstance().createCategory(category)
            if (success) {
                Toast.makeText(context, R.string.category_saved, Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            } else {
                Toast.makeText(context, "Error saving category", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
