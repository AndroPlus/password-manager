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
import com.androplus.pwdmgr.ui.CategoryStyle
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
        data class Row(val row: ExpenseRow, val last: Boolean) : Item()
        data class Section(val title: String) : Item()
    }

    private var groups: List<CategoryGroup> = emptyList()
    private val expanded = mutableSetOf<String>()   // survives data refreshes
    private var items: List<Item> = emptyList()

    private val currency = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply { maximumFractionDigits = 0 }
    private val timeFormat = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())

    fun submit(newGroups: List<CategoryGroup>) {
        groups = newGroups
        rebuild()
    }

    private fun rebuild() {
        val firstNotCounted = groups.indexOfFirst { !it.counted }
        items = groups.flatMapIndexed { i, g ->
            val open = g.name in expanded
            val section = if (i == firstNotCounted) listOf<Item>(Item.Section("Not counted in spending")) else emptyList()
            section + listOf<Item>(Item.Header(g, open)) + if (open) g.rows.mapIndexed { j, r -> Item.Row(r, j == g.rows.lastIndex) } else emptyList()
        }
        notifyDataSetChanged()
    }

    private fun toggle(name: String) {
        if (!expanded.remove(name)) expanded.add(name)
        rebuild()
    }

    override fun getItemViewType(position: Int) = when (items[position]) {
        is Item.Header -> HEADER
        is Item.Row -> ROW
        is Item.Section -> SECTION
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            HEADER -> HeaderHolder(inflater.inflate(R.layout.expense_group_header, parent, false))
            SECTION -> object : RecyclerView.ViewHolder(inflater.inflate(R.layout.expense_group_section, parent, false)) {}
            else -> RowHolder(inflater.inflate(R.layout.expense_group_row, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is Item.Header -> (holder as HeaderHolder).bind(item.group, item.expanded)
            is Item.Row -> (holder as RowHolder).bind(item.row, item.last)
            is Item.Section -> (holder.itemView as TextView).text = item.title
        }
    }

    private fun parseColor(hex: String) =
        try { Color.parseColor(hex) } catch (e: IllegalArgumentException) { Color.parseColor("#0D47A1") }

    private inner class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val dot: ImageView = view.findViewById(R.id.groupColorDot)
        private val name: TextView = view.findViewById(R.id.groupNameText)
        private val count: TextView = view.findViewById(R.id.groupCountText)
        private val total: TextView = view.findViewById(R.id.groupTotalText)
        private val chevron: ImageView = view.findViewById(R.id.groupChevron)

        fun bind(group: CategoryGroup, isOpen: Boolean) {
            val ctx = itemView.context
            val color = parseColor(group.color)
            // Category icon in its colour, on a soft tint of that colour.
            dot.backgroundTintList = ColorStateList.valueOf(CategoryStyle.tint(color))
            dot.setImageResource(CategoryStyle.icon(group.name))
            dot.imageTintList = ColorStateList.valueOf(CategoryStyle.ink(color))
            // Open: the card continues into its payments below.
            itemView.setBackgroundResource(if (isOpen && group.rows.isNotEmpty()) R.drawable.bg_card_top else R.drawable.bg_card)
            name.text = group.name
            val n = group.rows.size
            count.text = if (n == 1) "1 payment" else "$n payments"
            if (!group.counted) {
                total.text = currency.format(group.spent + group.received)
                total.setTextColor(ctx.getColor(R.color.ink_muted))
            } else if (group.spent > 0 || group.received == 0.0) {
                total.text = currency.format(group.spent)
                total.setTextColor(ctx.getColor(R.color.ink))
            } else {
                total.text = "+" + currency.format(group.received)
                total.setTextColor(ctx.getColor(R.color.accent))
            }
            chevron.rotation = if (isOpen) 270f else 90f
            itemView.contentDescription = "${group.name}, ${total.text}, ${count.text}. " +
                if (isOpen) "Tap to hide payments" else "Tap to show payments"
            itemView.setOnClickListener { toggle(group.name) }
        }
    }

    private inner class RowHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.rowTitleText)
        private val subtitle: TextView = view.findViewById(R.id.rowSubtitleText)
        private val amount: TextView = view.findViewById(R.id.rowAmountText)

        fun bind(row: ExpenseRow, last: Boolean) {
            val ctx = itemView.context
            itemView.setBackgroundResource(if (last) R.drawable.bg_card_bottom else R.drawable.bg_card_middle)
            title.text = row.title
            val time = timeFormat.format(Date(row.timeMillis))
            subtitle.text = when (row.source) {
                null -> "$time · Added by you"
                "Auto" -> time
                else -> "$time · via ${row.source}"
            }
            amount.text = (if (row.isIncome) "+" else "−") + currency.format(row.amount)
            amount.setTextColor(ctx.getColor(if (row.isIncome) R.color.accent else R.color.ink))
            itemView.setOnClickListener { onRowClick(row) }
            itemView.setOnLongClickListener { onRowLongClick(row); true }
        }
    }

    /** Readable text colour from a light category colour. */
    private fun darken(c: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(c, hsv)
        hsv[1] = maxOf(hsv[1], 0.5f)
        hsv[2] = minOf(hsv[2], 0.5f)
        return Color.HSVToColor(hsv)
    }

    companion object {
        private const val HEADER = 0
        private const val ROW = 1
        private const val SECTION = 2
    }
}
