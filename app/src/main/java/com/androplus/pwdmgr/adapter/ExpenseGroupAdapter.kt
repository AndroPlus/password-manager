package com.androplus.pwdmgr.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.androplus.pwdmgr.R
import com.androplus.pwdmgr.utils.CategoryGroup
import com.androplus.pwdmgr.utils.ExpenseRow
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Expense list as collapsible category groups. Tapping a category header shows
 * or hides its transactions; each transaction shows a clean name, where it came
 * from and when, never the raw SMS text.
 */
class ExpenseGroupAdapter(
    private val onRowClick: (ExpenseRow) -> Unit,
    private val onRowLongClick: (ExpenseRow) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed class Item {
        data class Header(val group: CategoryGroup, val expanded: Boolean) : Item()
        data class Row(val row: ExpenseRow) : Item()
    }

    private var groups: List<CategoryGroup> = emptyList()
    private val expanded = mutableSetOf<String>()   // survives data refreshes
    private var items: List<Item> = emptyList()

    private val currency = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    private val timeFormat = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())

    fun submit(newGroups: List<CategoryGroup>) {
        groups = newGroups
        rebuild()
    }

    private fun rebuild() {
        items = groups.flatMap { g ->
            val open = g.name in expanded
            listOf<Item>(Item.Header(g, open)) + if (open) g.rows.map { Item.Row(it) } else emptyList()
        }
        notifyDataSetChanged()
    }

    private fun toggle(name: String) {
        if (!expanded.remove(name)) expanded.add(name)
        rebuild()
    }

    override fun getItemViewType(position: Int) = if (items[position] is Item.Header) HEADER else ROW

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == HEADER) {
            HeaderHolder(inflater.inflate(R.layout.expense_group_header, parent, false))
        } else {
            RowHolder(inflater.inflate(R.layout.expense_group_row, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is Item.Header -> (holder as HeaderHolder).bind(item.group, item.expanded)
            is Item.Row -> (holder as RowHolder).bind(item.row)
        }
    }

    private fun parseColor(hex: String) =
        try { Color.parseColor(hex) } catch (e: IllegalArgumentException) { Color.parseColor("#0D47A1") }

    private inner class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val dot: View = view.findViewById(R.id.groupColorDot)
        private val name: TextView = view.findViewById(R.id.groupNameText)
        private val count: TextView = view.findViewById(R.id.groupCountText)
        private val total: TextView = view.findViewById(R.id.groupTotalText)
        private val chevron: ImageView = view.findViewById(R.id.groupChevron)

        fun bind(group: CategoryGroup, isOpen: Boolean) {
            val ctx = itemView.context
            dot.backgroundTintList = ColorStateList.valueOf(parseColor(group.color))
            name.text = group.name
            val n = group.rows.size
            count.text = if (n == 1) "1 transaction" else "$n transactions"
            if (group.spent > 0 || group.received == 0.0) {
                total.text = currency.format(group.spent)
                total.setTextColor(ctx.getColor(R.color.expense_red))
            } else {
                total.text = "+" + currency.format(group.received)
                total.setTextColor(ctx.getColor(R.color.income_green))
            }
            chevron.rotation = if (isOpen) 180f else 0f
            itemView.setOnClickListener { toggle(group.name) }
        }
    }

    private inner class RowHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.rowTitleText)
        private val subtitle: TextView = view.findViewById(R.id.rowSubtitleText)
        private val amount: TextView = view.findViewById(R.id.rowAmountText)

        fun bind(row: ExpenseRow) {
            val ctx = itemView.context
            title.text = row.title
            val time = timeFormat.format(Date(row.timeMillis))
            subtitle.text = when (row.source) {
                null -> "$time · Added by you"
                "Auto" -> time
                else -> "$time · via ${row.source}"
            }
            amount.text = (if (row.isIncome) "+" else "−") + currency.format(row.amount)
            amount.setTextColor(ctx.getColor(if (row.isIncome) R.color.income_green else R.color.expense_red))
            itemView.setOnClickListener { onRowClick(row) }
            itemView.setOnLongClickListener { onRowLongClick(row); true }
        }
    }

    companion object {
        private const val HEADER = 0
        private const val ROW = 1
    }
}
