package com.androplus.pwdmgr.adapter

import android.graphics.Color
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.androplus.pwdmgr.databinding.TodoItemLayoutBinding
import com.androplus.pwdmgr.model.TodoItem
import java.text.SimpleDateFormat
import java.util.*

class TodoListAdapter(
    private var todos: List<TodoItem>,
    private val onItemClick: (TodoItem) -> Unit,
    private val onToggleClick: (TodoItem) -> Unit
) : RecyclerView.Adapter<TodoListAdapter.TodoViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    inner class TodoViewHolder(val binding: TodoItemLayoutBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TodoViewHolder {
        val binding = TodoItemLayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TodoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TodoViewHolder, position: Int) {
        val todo = todos[position]
        with(holder.binding) {
            todoTitle.text = todo.title
            todoDescription.text = todo.description
            
            todoCheckBox.isChecked = todo.isCompleted
            
            if (todo.isCompleted) {
                todoTitle.paintFlags = todoTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                todoTitle.setTextColor(Color.GRAY)
            } else {
                todoTitle.paintFlags = todoTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                todoTitle.setTextColor(Color.BLACK)
            }

            // Category Binding
            todoCategory.text = todo.category
            val categoryColor = when (todo.category) {
                "WORK" -> "#3F51B5" // Indigo
                "PERSONAL" -> "#00BCD4" // Cyan
                "SHOPPING" -> "#E91E63" // Pink
                "HEALTH" -> "#4CAF50" // Green
                "LEARNING" -> "#FF9800" // Orange
                "BILLS" -> "#F44336" // Red
                else -> "#9E9E9E" // Grey
            }
            todoCategory.background.setTint(Color.parseColor(categoryColor))

            if (todo.dueDate != null) {
                todoDueDate.visibility = android.view.View.VISIBLE
                val dueDateText = dateFormat.format(Date(todo.dueDate!!))
                
                // Overdue Check
                val today = java.util.Calendar.getInstance()
                // Reset to start of day for comparison if we want to be strict, 
                // but usually "overdue" means strictly less than today (yesterday or before).
                // Or sensitive to time? MaterialDatePicker is midnight UTC.
                // Let's rely on simple comparison: if dueDate < today's start of day.
                
                val currentCalendar = java.util.Calendar.getInstance()
                // Normalize current time to start of day to compare with dueDate (which is start of day)
                val year = currentCalendar.get(java.util.Calendar.YEAR)
                val month = currentCalendar.get(java.util.Calendar.MONTH)
                val day = currentCalendar.get(java.util.Calendar.DAY_OF_MONTH)
                
                val todayStartUtc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                todayStartUtc.clear()
                todayStartUtc.set(year, month, day)
                
                if (!todo.isCompleted && todo.dueDate!! < todayStartUtc.timeInMillis) {
                    todoDueDate.text = root.context.getString(com.androplus.pwdmgr.R.string.due_date_label, "$dueDateText (${root.context.getString(com.androplus.pwdmgr.R.string.overdue)})")
                    todoDueDate.setTextColor(Color.RED)
                } else {
                    todoDueDate.text = root.context.getString(com.androplus.pwdmgr.R.string.due_date_label, dueDateText)
                    todoDueDate.setTextColor(root.context.getColor(com.androplus.pwdmgr.R.color.teal_700))
                }
            } else {
                todoDueDate.visibility = android.view.View.GONE
            }

            val priorityColor = when (todo.priority) {
                "HIGH" -> "#FF5252"
                "MEDIUM" -> "#FFAB40"
                "LOW" -> "#4CAF50"
                else -> "#BDBDBD"
            }
            priorityIndicator.background.setTint(Color.parseColor(priorityColor))

            root.setOnClickListener { onItemClick(todo) }
            todoCheckBox.setOnClickListener { onToggleClick(todo) }
        }
    }

    override fun getItemCount(): Int = todos.size

    fun updateTodos(newTodos: List<TodoItem>) {
        todos = newTodos
        notifyDataSetChanged()
    }
}
