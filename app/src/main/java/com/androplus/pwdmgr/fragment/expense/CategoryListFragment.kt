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

    private fun setupRecyclerView() {
        categoryAdapter = CategoryListAdapter(
            categories = emptyList(),
            onDeleteClick = { category ->
                lifecycleScope.launch {
                    val success = RealmService.getInstance().deleteCategory(category)
                    if (success) {
                        Toast.makeText(context, R.string.category_deleted, Toast.LENGTH_SHORT).show()
                        loadCategories()
                    }
                }
            }
        )
        binding.categoryRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = categoryAdapter
        }
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
