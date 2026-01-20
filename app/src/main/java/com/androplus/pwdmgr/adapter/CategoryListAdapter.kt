package com.androplus.pwdmgr.adapter

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.androplus.pwdmgr.R
import com.androplus.pwdmgr.model.ExpenseCategory

class CategoryListAdapter(
    private var categories: List<ExpenseCategory>,
    private val onDeleteClick: (ExpenseCategory) -> Unit
) : RecyclerView.Adapter<CategoryListAdapter.CategoryViewHolder>() {

    class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val colorCircle: View = itemView.findViewById(R.id.categoryColorCircle)
        val categoryName: TextView = itemView.findViewById(R.id.categoryName)
        val deleteIcon: ImageView = itemView.findViewById(R.id.deleteCategory)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.category_item_layout, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categories[position]
        holder.categoryName.text = category.name
        
        // Set color circle
        val bgDrawable = holder.colorCircle.background as GradientDrawable
        try {
            bgDrawable.setColor(Color.parseColor(category.color))
        } catch (e: Exception) {
            bgDrawable.setColor(Color.LTGRAY)
        }

        // Show delete only for non-default categories
        if (!category.isDefault) {
            holder.deleteIcon.visibility = View.VISIBLE
            holder.deleteIcon.setOnClickListener {
                onDeleteClick(category)
            }
        } else {
            holder.deleteIcon.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = categories.size

    fun updateCategories(newCategories: List<ExpenseCategory>) {
        categories = newCategories
        notifyDataSetChanged()
    }
}
