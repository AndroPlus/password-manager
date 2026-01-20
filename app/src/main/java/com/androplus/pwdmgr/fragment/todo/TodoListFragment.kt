package com.androplus.pwdmgr.fragment.todo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.androplus.pwdmgr.R
import com.androplus.pwdmgr.adapter.TodoListAdapter
import com.androplus.pwdmgr.databinding.TodoListLayoutBinding
import com.androplus.pwdmgr.services.RealmService
import kotlinx.coroutines.launch

class TodoListFragment : Fragment() {

    private var _binding: TodoListLayoutBinding? = null
    private val binding get() = _binding!!
    private lateinit var todoAdapter: TodoListAdapter
    private var currentFilter = "ALL"

    private val requestPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted
        } else {
            // Permission denied
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = TodoListLayoutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(permission)
            }
        }

        setupRecyclerView()
        setupListeners()
        loadTodos()
    }

    private fun setupRecyclerView() {
        todoAdapter = TodoListAdapter(
            todos = emptyList(),
            onItemClick = { todo ->
                val bundle = Bundle().apply {
                    putString("todo_id", todo._id.toHexString())
                }
                findNavController().navigate(R.id.TodoAddFragment, bundle)
            },
            onToggleClick = { todo ->
                lifecycleScope.launch {
                    RealmService.getInstance().toggleTodoCompletion(todo._id)
                    loadTodos()
                }
            }
        )
        binding.todoRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = todoAdapter
        }
    }

    private fun setupListeners() {
        binding.fabAddTodo.setOnClickListener {
            findNavController().navigate(R.id.TodoAddFragment)
        }

        binding.filterChipGroup.setOnCheckedChangeListener { _, checkedId ->
            currentFilter = when (checkedId) {
                R.id.chipPending -> "PENDING"
                R.id.chipCompleted -> "COMPLETED"
                R.id.chipToday -> "TODAY"
                R.id.chipTomorrow -> "TOMORROW"
                R.id.chipUpcoming -> "UPCOMING"
                R.id.chipWeekend -> "WEEKEND"
                R.id.chipMonthEnd -> "MONTH_END"
                R.id.chipYearEnd -> "YEAR_END"
                else -> "ALL"
            }
            loadTodos()
        }
    }

    private fun loadTodos() {
        val allTodos = RealmService.getInstance().getAllTodos()
        
        val todayUtc = getStartOfDayUtc(0)
        val tomorrowUtc = getStartOfDayUtc(1)

        val filteredTodos = when (currentFilter) {
            "PENDING" -> allTodos.filter { !it.isCompleted }
            "COMPLETED" -> allTodos.filter { it.isCompleted }
            "TODAY" -> allTodos.filter { it.dueDate != null && isSameDay(it.dueDate!!, todayUtc) }
            "TOMORROW" -> allTodos.filter { it.dueDate != null && isSameDay(it.dueDate!!, tomorrowUtc) }
            "UPCOMING" -> allTodos.filter { it.dueDate != null && it.dueDate!! > tomorrowUtc }
            "WEEKEND" -> allTodos.filter { it.dueDate != null && isThisWeekend(it.dueDate!!) }
            "MONTH_END" -> allTodos.filter { it.dueDate != null && isMonthEnd(it.dueDate!!) }
            "YEAR_END" -> allTodos.filter { it.dueDate != null && isYearEnd(it.dueDate!!) }
            else -> allTodos
        }

        todoAdapter.updateTodos(filteredTodos)
        binding.emptyStateText.visibility = if (filteredTodos.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun isThisWeekend(date: Long): Boolean {
        // Logic: Is Friday, Saturday or Sunday? Or just Saturday/Sunday?
        // Usually "Weekend" means Saturday and Sunday.
        // We want to check if the date falls on the UPCOMING weekend (or today if today is weekend).
        // Let's implement: date >= Today AND date is within next 7 days AND day is SAT or SUN.
        
        val todayUtc = getStartOfDayUtc(0)
        if (date < todayUtc) return false // Past
        
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = date
        // Note: date passed in is UTC midnight. We need to check the day of week.
        // Since it's UTC midnight, we can set a UTC calendar.
        val utcCalendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        utcCalendar.timeInMillis = date
        
        val dayOfWeek = utcCalendar.get(java.util.Calendar.DAY_OF_WEEK)
        val isWeekendDay = dayOfWeek == java.util.Calendar.SATURDAY || dayOfWeek == java.util.Calendar.SUNDAY
        
        // Ensure it is THIS weekend (e.g. within next 7 days)
        val diff = date - todayUtc
        val daysDiff = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diff)
        
        return isWeekendDay && daysDiff < 7
    }

    private fun isMonthEnd(date: Long): Boolean {
        // Date is the last day of the current month?
        // Or "Month End" meaning THIS month's end.
        val calendar = java.util.Calendar.getInstance() // Local now
        val currentMaxDay = calendar.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        
        val targetMonthEnd = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        targetMonthEnd.clear()
        targetMonthEnd.set(calendar.get(java.util.Calendar.YEAR), calendar.get(java.util.Calendar.MONTH), currentMaxDay)
        
        return date == targetMonthEnd.timeInMillis
    }
    
    private fun isYearEnd(date: Long): Boolean {
        // Date is Dec 31st of CURRENT Year
        val calendar = java.util.Calendar.getInstance() // Local now
        
        val targetYearEnd = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        targetYearEnd.clear()
        targetYearEnd.set(calendar.get(java.util.Calendar.YEAR), java.util.Calendar.DECEMBER, 31)
        
        return date == targetYearEnd.timeInMillis
    }

    private fun getStartOfDayUtc(daysToAdd: Int): Long {
        val calendar = java.util.Calendar.getInstance() // Local time
        calendar.add(java.util.Calendar.DAY_OF_YEAR, daysToAdd)
        
        // Extract local year, month, day
        val year = calendar.get(java.util.Calendar.YEAR)
        val month = calendar.get(java.util.Calendar.MONTH)
        val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
        
        // Create UTC equivalent
        val utcCalendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        utcCalendar.clear()
        utcCalendar.set(year, month, day) // Sets to 00:00:00 UTC of that local day
        
        return utcCalendar.timeInMillis
    }

    private fun isSameDay(date1: Long, date2: Long): Boolean {
        // Since we are comparing against normalized UTC midnights, direct comparison might work if date1 is also normalized.
        // MaterialDatePicker returns normalized UTC midnight.
        // However, to be safe against slight potential deviations if any, we can check equality.
        // But logic relies on date1 being exactly matching the start of day.
        return date1 == date2
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
