package com.androplus.pwdmgr.fragment.expense

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.androplus.pwdmgr.R
import com.androplus.pwdmgr.adapter.CategoryListAdapter
import com.androplus.pwdmgr.databinding.CategoryListLayoutBinding
import com.androplus.pwdmgr.services.RealmService
import com.androplus.pwdmgr.services.RemovedCategories
import com.androplus.pwdmgr.model.ExpenseCategory
import android.app.AlertDialog
import kotlinx.coroutines.launch

class CategoryListFragment : Fragment() {

    private var _binding: CategoryListLayoutBinding? = null
    private val binding get() = _binding!!
    private lateinit var categoryAdapter: CategoryListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = CategoryListLayoutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupListeners()
        loadCategories()
    }

    override fun onResume() {
        super.onResume()
        loadCategories()   // e.g. after adding one
    }

    private fun setupRecyclerView() {
        categoryAdapter = CategoryListAdapter(
            categories = emptyList(),
            onDeleteClick = { category -> confirmRemove(category) }
        )
        binding.categoryRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = categoryAdapter
        }
    }

    private fun confirmRemove(category: ExpenseCategory) {
        val count = RealmService.getInstance().getAllExpenses()
            .count { it.categoryId == category._id.toHexString() || it.categoryName == category.name }
        val message = if (count == 0) "It has no transactions."
            else "Its $count transaction(s) will move to Other."
        AlertDialog.Builder(requireContext())
            .setTitle("Remove \"${category.name}\"?")
            .setMessage(message)
            .setPositiveButton("Remove") { _, _ ->
                val appContext = requireContext().applicationContext
                lifecycleScope.launch {
                    val moved = RealmService.getInstance().deleteCategoryMovingTransactions(category)
                    if (moved == null) {
                        Toast.makeText(appContext, "Could not remove ${category.name}", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    RemovedCategories.add(appContext, category.name)
                    val extra = if (moved > 0) "; $moved transaction(s) moved to Other" else ""
                    Toast.makeText(appContext, "Removed ${category.name}$extra", Toast.LENGTH_LONG).show()
                    if (_binding != null) loadCategories()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupListeners() {
        binding.fabAddCategory.setOnClickListener {
            findNavController().navigate(R.id.CategoryAddFragment)
        }
    }

    private fun loadCategories() {
        val categories = RealmService.getInstance().getAllCategories()
        categoryAdapter.updateCategories(categories)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
