package com.androplus.pwdmgr.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.androplus.pwdmgr.R
import com.androplus.pwdmgr.databinding.ModuleSelectionLayoutBinding

class ModuleSelectionFragment : Fragment() {

    private var _binding: ModuleSelectionLayoutBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ModuleSelectionLayoutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.passwordManagerTile.setOnClickListener {
            findNavController().navigate(R.id.action_selection_to_login)
        }

        binding.expenseTrackerTile.setOnClickListener {
            findNavController().navigate(R.id.action_selection_to_expenses)
        }

        binding.todoModuleTile.setOnClickListener {
            findNavController().navigate(R.id.action_selection_to_todo)
        }

        // Expense Tracker is the main module: open it straight away when the app
        // starts. Back from it returns here for Todo List and Password Manager.
        if (!openedExpensesAtLaunch) {
            openedExpensesAtLaunch = true
            if (com.androplus.pwdmgr.services.AppPrefs.onboardingDone(requireContext())) {
                findNavController().navigate(R.id.action_selection_to_expenses)
            } else {
                findNavController().navigate(R.id.OnboardingFragment)
            }
        }
    }

    companion object {
        // Once per app process, so pressing Back to this menu doesn't reopen expenses.
        private var openedExpensesAtLaunch = false
    }



    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
