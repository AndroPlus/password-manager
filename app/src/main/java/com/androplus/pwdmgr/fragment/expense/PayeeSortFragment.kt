package com.androplus.pwdmgr.fragment.expense

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.androplus.pwdmgr.R
import com.androplus.pwdmgr.databinding.FragmentPayeeSortBinding
import com.androplus.pwdmgr.services.AutoTransactionRecorder
import com.androplus.pwdmgr.services.RealmService
import com.androplus.pwdmgr.utils.DuplicateDetector
import com.androplus.pwdmgr.utils.PayeeSorting
import com.androplus.pwdmgr.utils.PayeeSummary
import com.androplus.pwdmgr.utils.PayeeTxn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

/** One screen to give every regular shop / person a category in a few minutes. */
class PayeeSortFragment : Fragment() {

    private var _binding: FragmentPayeeSortBinding? = null
    private val binding get() = _binding!!
    private val money = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply { maximumFractionDigits = 0 }
    private val adapter = PayeeAdapter { pickCategory(it) }

    companion object {
        /** Payees still to sort, for the banner on the expense list. */
        fun unsorted(): List<PayeeSummary> {
            val txns = RealmService.getInstance().getAllExpenses()
                .filter { DuplicateDetector.sourceOf(it.shortDescription) != null }
                .map { PayeeTxn(it.shortDescription, it.amount, it.type == "EXPENSE", it.categoryName, it.date) }
            return PayeeSorting.unsorted(txns) { AutoTransactionRecorder.isLearned(it) }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPayeeSortBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        AutoTransactionRecorder.init(requireContext())
        binding.payeeRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.payeeRecyclerView.adapter = adapter
        load()
    }

    private fun load() {
        if (_binding == null) return
        val items = unsorted()
        adapter.submit(items)
        binding.payeeEmptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun pickCategory(p: PayeeSummary) {
        val names = RealmService.getInstance().getAllCategories().map { it.name }
            .filter { it != "Income" }
        val current = names.indexOf(p.categoryName)
        AlertDialog.Builder(requireContext())
            .setTitle("${p.name} · ${money.format(p.total)}")
            .setSingleChoiceItems(names.toTypedArray(), current) { dialog, which ->
                dialog.dismiss()
                save(p, names[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun save(p: PayeeSummary, category: String) {
        val app = requireContext().applicationContext
        lifecycleScope.launch {
            val moved = withContext(Dispatchers.IO) { AutoTransactionRecorder.learn(p.payee, true, category) }
            val msg = if (moved == null) "Couldn't remember ${p.name}"
                else "${p.name} → $category" + if (moved > 0) " ($moved moved)" else ""
            Toast.makeText(app, msg, Toast.LENGTH_SHORT).show()
            load()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class PayeeAdapter(private val onClick: (PayeeSummary) -> Unit) :
        RecyclerView.Adapter<PayeeAdapter.Holder>() {

        private var items: List<PayeeSummary> = emptyList()

        fun submit(newItems: List<PayeeSummary>) {
            items = newItems
            notifyDataSetChanged()
        }

        inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.payeeName)
            val detail: TextView = v.findViewById(R.id.payeeDetail)
            val total: TextView = v.findViewById(R.id.payeeTotal)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
            LayoutInflater.from(parent.context).inflate(R.layout.payee_sort_item, parent, false)
        )

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: Holder, position: Int) {
            val p = items[position]
            h.name.text = p.name
            h.detail.text = "${p.count} payment(s) · now in ${p.categoryName.ifBlank { "Other" }} · tap to set"
            h.total.text = money.format(p.total)
            h.itemView.setOnClickListener { onClick(p) }
        }
    }
}
