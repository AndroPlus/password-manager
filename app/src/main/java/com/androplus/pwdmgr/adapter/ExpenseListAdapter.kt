package com.androplus.pwdmgr.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.androplus.pwdmgr.R
import com.androplus.pwdmgr.model.ExpenseTransaction
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class ExpenseListAdapter(
    private var expenses: List<ExpenseTransaction>,
    private val onItemClick: (ExpenseTransaction) -> Unit,
    private val onItemLongClick: (ExpenseTransaction) -> Unit
) : RecyclerView.Adapter<ExpenseListAdapter.ExpenseViewHolder>() {

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    class ExpenseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val categoryColorIndicator: View = itemView.findViewById(R.id.categoryColorIndicator)
        val shortDescriptionText: TextView = itemView.findViewById(R.id.shortDescriptionText)
        val descriptionText: TextView = itemView.findViewById(R.id.descriptionText)
        val dateText: TextView = itemView.findViewById(R.id.dateText)
        val amountText: TextView = itemView.findViewById(R.id.amountText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.expense_item_layout, parent, false)
        return ExpenseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExpenseViewHolder, position: Int) {
        val expense = expenses[position]

        // Set category color
        try {
            holder.categoryColorIndicator.setBackgroundColor(Color.parseColor(expense.categoryColor))
        } catch (e: Exception) {
            holder.categoryColorIndicator.setBackgroundColor(Color.parseColor("#0D47A1"))
        }

        // Set descriptions
        holder.shortDescriptionText.text = expense.shortDescription.ifEmpty { expense.categoryName }
        holder.descriptionText.visibility = View.GONE // Hide full description

        // Set date
        holder.dateText.text = dateFormat.format(Date(expense.date))

        // Set amount with color based on type
        val amountText = currencyFormat.format(expense.amount)
        holder.amountText.text = amountText
        
        val amountColor = if (expense.type == "INCOME") {
            holder.itemView.context.getColor(R.color.income_green)
        } else {
            holder.itemView.context.getColor(R.color.expense_red)
        }
        holder.amountText.setTextColor(amountColor)

        // Click listeners
        holder.itemView.setOnClickListener {
            onItemClick(expense)
        }

        holder.itemView.setOnLongClickListener {
            onItemLongClick(expense)
            true
        }
    }

    override fun getItemCount(): Int = expenses.size

    fun updateExpenses(newExpenses: List<ExpenseTransaction>) {
        expenses = newExpenses
        notifyDataSetChanged()
    }
}
