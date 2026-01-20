package com.androplus.pwdmgr.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.androplus.pwdmgr.R
import com.androplus.pwdmgr.model.ExpenseBudget
import java.text.NumberFormat
import java.util.Locale

class BudgetAdapter(
    private var budgets: List<ExpenseBudget>,
    private val spentAmounts: Map<String, Double>,
    private val categoryNames: Map<String, String>,
    private val onItemClick: (ExpenseBudget) -> Unit
) : RecyclerView.Adapter<BudgetAdapter.BudgetViewHolder>() {

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    inner class BudgetViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val categoryName: TextView = view.findViewById(R.id.categoryName)
        val budgetAmount: TextView = view.findViewById(R.id.budgetAmount)
        val spentAmount: TextView = view.findViewById(R.id.spentAmount)
        val remainingAmount: TextView = view.findViewById(R.id.remainingAmount)
        val progressBar: ProgressBar = view.findViewById(R.id.budgetProgress)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BudgetViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_budget, parent, false)
        return BudgetViewHolder(view)
    }

    override fun onBindViewHolder(holder: BudgetViewHolder, position: Int) {
        val budget = budgets[position]
        val spent = spentAmounts[budget.categoryId] ?: 0.0
        val remaining = budget.amount - spent
        val progress = if (budget.amount > 0) ((spent / budget.amount) * 100).toInt() else 0

        holder.categoryName.text = categoryNames[budget.categoryId] ?: "Unknown Category"
        holder.budgetAmount.text = currencyFormat.format(budget.amount)
        holder.spentAmount.text = "Spent: ${currencyFormat.format(spent)}"
        holder.remainingAmount.text = "Left: ${currencyFormat.format(remaining)}"
        holder.progressBar.progress = progress

        // Change color if over budget (optional, handled by progress drawable logic via simple color filter or just rely on text)
        // For simplicity, we stick to the progress bar for now.
        
        holder.itemView.setOnClickListener { onItemClick(budget) }
    }

    override fun getItemCount() = budgets.size

    fun updateData(newBudgets: List<ExpenseBudget>) {
        budgets = newBudgets
        notifyDataSetChanged()
    }
}
