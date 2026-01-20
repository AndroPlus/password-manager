package com.androplus.pwdmgr.fragment.expense

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.androplus.pwdmgr.R
import com.androplus.pwdmgr.services.RealmService
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import java.util.Calendar

class ExpenseAnalyticsFragment : Fragment() {

    private lateinit var pieChart: PieChart
    private lateinit var barChart: BarChart

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_expense_analytics, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        pieChart = view.findViewById(R.id.categoryPieChart)
        barChart = view.findViewById(R.id.incomeExpenseBarChart)

        setupPieChart()
        setupBarChart()
        loadData()
    }

    private fun setupPieChart() {
        pieChart.description.isEnabled = false
        pieChart.isDrawHoleEnabled = true
        pieChart.setHoleColor(Color.WHITE)
        pieChart.setTransparentCircleAlpha(110)
        pieChart.holeRadius = 58f
        pieChart.transparentCircleRadius = 61f
        pieChart.setDrawCenterText(true)
        pieChart.centerText = "Expenses"
        pieChart.setEntryLabelColor(Color.BLACK)
        pieChart.setEntryLabelTextSize(12f)
    }

    private fun setupBarChart() {
        barChart.description.isEnabled = false
        barChart.setPinchZoom(false)
        barChart.setDrawGridBackground(false)
        
        val xAxis = barChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.granularity = 1f
        xAxis.valueFormatter = IndexAxisValueFormatter(listOf("Income", "Expense"))

        barChart.axisLeft.setDrawGridLines(false)
        barChart.axisRight.isEnabled = false
    }

    private fun loadData() {
        // Load This Month Data
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val start = calendar.timeInMillis
        val end = System.currentTimeMillis()

        val expenses = RealmService.getInstance().getExpensesByDateRange(start, end)

        // 1. Pie Chart Data
        val categoryMap = HashMap<String, Double>()
        expenses.filter { it.type == "EXPENSE" }.forEach { 
             val current = categoryMap.getOrDefault(it.categoryName, 0.0)
             categoryMap[it.categoryName] = current + it.amount
        }

        val pieEntries = ArrayList<PieEntry>()
        categoryMap.forEach { (name, amount) ->
            pieEntries.add(PieEntry(amount.toFloat(), name))
        }

        val pieDataSet = PieDataSet(pieEntries, "Expenses Categories")
        pieDataSet.colors = ColorTemplate.MATERIAL_COLORS.toList()
        pieDataSet.valueTextSize = 14f
        pieDataSet.valueTextColor = Color.WHITE

        val pieData = PieData(pieDataSet)
        pieChart.data = pieData
        pieChart.invalidate()

        // 2. Bar Chart Data
        var totalIncome = 0.0
        var totalExpense = 0.0
        expenses.forEach { 
            if (it.type == "INCOME") totalIncome += it.amount
            else totalExpense += it.amount
        }

        val barEntries = ArrayList<BarEntry>()
        barEntries.add(BarEntry(0f, totalIncome.toFloat()))
        barEntries.add(BarEntry(1f, totalExpense.toFloat()))

        val barDataSet = BarDataSet(barEntries, "Income vs Expense")
        barDataSet.colors = listOf(Color.GREEN, Color.RED)
        barDataSet.valueTextColor = Color.BLACK
        barDataSet.valueTextSize = 14f

        val barData = BarData(barDataSet)
        barChart.data = barData
        barChart.invalidate()
    }
}
