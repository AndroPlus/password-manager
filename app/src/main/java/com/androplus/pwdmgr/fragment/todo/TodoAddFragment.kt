package com.androplus.pwdmgr.fragment.todo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.androplus.pwdmgr.R
import com.androplus.pwdmgr.databinding.TodoAddLayoutBinding
import com.androplus.pwdmgr.model.TodoItem
import com.androplus.pwdmgr.services.RealmService
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class TodoAddFragment : Fragment() {

    private var _binding: TodoAddLayoutBinding? = null
    private val binding get() = _binding!!
    private var selectedDueDate: Long? = MaterialDatePicker.todayInUtcMilliseconds()
    private var todoId: String? = null
    private var existingTodo: TodoItem? = null
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = TodoAddLayoutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        todoId = arguments?.getString("todo_id")
        if (todoId != null) {
            loadExistingTodo()
        } else {
            // Set default date for new tasks
            binding.btnSelectDate.text = dateFormat.format(Date(selectedDueDate!!))
        }

        setupListeners()
    }

    private fun loadExistingTodo() {
        existingTodo = RealmService.getInstance().getTodoById(todoId!!)
        existingTodo?.let { todo ->
            binding.titleEditText.setText(todo.title)
            binding.descriptionEditText.setText(todo.description)
            
            when (todo.priority) {
                "HIGH" -> binding.chipHigh.isChecked = true
                "LOW" -> binding.chipLow.isChecked = true
                else -> binding.chipMedium.isChecked = true
            }

            when (todo.category) {
                "WORK" -> binding.chipCatWork.isChecked = true
                "SHOPPING" -> binding.chipCatShopping.isChecked = true
                "HEALTH" -> binding.chipCatHealth.isChecked = true
                "LEARNING" -> binding.chipCatLearning.isChecked = true
                "BILLS" -> binding.chipCatBills.isChecked = true
                else -> binding.chipCatPersonal.isChecked = true
            }

            if (todo.dueDate != null) {
                selectedDueDate = todo.dueDate
                binding.btnSelectDate.text = dateFormat.format(Date(todo.dueDate!!))
            }
            binding.btnSaveTodo.text = getString(R.string.update_task)
        }
    }

    private fun setupListeners() {
        binding.btnSelectDate.setOnClickListener {
            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.select_due_date)
                .setSelection(selectedDueDate ?: MaterialDatePicker.todayInUtcMilliseconds())
                .build()

            datePicker.addOnPositiveButtonClickListener { selection ->
                selectedDueDate = selection
                binding.btnSelectDate.text = dateFormat.format(Date(selection))
            }
            datePicker.show(parentFragmentManager, "DATE_PICKER")
        }

        binding.btnSaveTodo.setOnClickListener {
            saveTodo()
        }
    }

    private fun saveTodo() {
        val title = binding.titleEditText.text.toString().trim()
        val description = binding.descriptionEditText.text.toString().trim()
        
        if (title.isEmpty()) {
            binding.titleInputLayout.error = getString(R.string.title_required)
            return
        }

        val priority = when (binding.priorityChipGroup.checkedChipId) {
            R.id.chipHigh -> "HIGH"
            R.id.chipLow -> "LOW"
            else -> "MEDIUM"
        }

        val category = when (binding.categoryChipGroup.checkedChipId) {
            R.id.chipCatWork -> "WORK"
            R.id.chipCatShopping -> "SHOPPING"
            R.id.chipCatHealth -> "HEALTH"
            R.id.chipCatLearning -> "LEARNING"
            R.id.chipCatBills -> "BILLS"
            else -> "PERSONAL"
        }

        lifecycleScope.launch {
            val success = if (existingTodo != null) {
                val updatedTodo = TodoItem().apply {
                    _id = existingTodo!!._id
                    this.title = title
                    this.description = description
                    this.priority = priority
                    this.category = category
                    this.dueDate = selectedDueDate
                    this.isCompleted = existingTodo!!.isCompleted
                    this.createdAt = existingTodo!!.createdAt
                }
                RealmService.getInstance().updateTodo(updatedTodo)
            } else {
                val newTodo = TodoItem().apply {
                    this.title = title
                    this.description = description
                    this.priority = priority
                    this.category = category
                    this.dueDate = selectedDueDate
                }
                RealmService.getInstance().createTodo(newTodo)
            }

            if (success) {
                findNavController().popBackStack()
            } else {
                Toast.makeText(context, R.string.error_saving_task, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
