package com.androplus.pwdmgr.fragment.expense

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.androplus.pwdmgr.R
import com.androplus.pwdmgr.databinding.FragmentOnboardingBinding
import com.androplus.pwdmgr.services.AppPrefs
import com.androplus.pwdmgr.worker.SmsHistoryWorker

/** First run: allow SMS, bring in past payments, set a monthly budget. Each step can be skipped. */
class OnboardingFragment : Fragment() {

    private var _binding: FragmentOnboardingBinding? = null
    private val binding get() = _binding!!
    private var step = 0
    private var importing = false

    private val smsPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) {
            PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
                .putBoolean("sms_tracking_enabled", true).apply()
            next()
        } else if (_binding != null) {
            binding.onbStatus.text = "Without SMS access the app can't see payments. You can allow it later in Settings."
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOnboardingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.onbPrimary.setOnClickListener { primary() }
        binding.onbSkip.setOnClickListener { next() }
        // The keyboard's ✓ saves the budget, so nobody has to dismiss it to reach the button.
        binding.onbBudget.setOnEditorActionListener { _, actionId, event ->
            val done = actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                (event?.keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_UP)
            if (done && step == 2) { primary(); true } else false
        }
        show()
    }

    private fun hasSms(): Boolean = listOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS).all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    private fun show() {
        if (_binding == null) return
        binding.onbStep.text = "Step ${step + 1} of 3"
        binding.onbProgress.setProgressCompat(step + 1, true)
        binding.onbStatus.text = ""
        binding.onbBudgetLayout.visibility = View.GONE
        binding.onbSkip.visibility = View.VISIBLE
        when (step) {
            0 -> {
                binding.onbIcon.setImageResource(R.drawable.ic_cat_card)
                binding.onbTitle.text = "Track payments automatically"
                binding.onbBody.text = "Allow SMS access and every bank and card message becomes a transaction, " +
                    "already sorted. OTPs and personal messages are ignored, and your data stays on this phone."
                binding.onbPrimary.text = if (hasSms()) "Continue" else "Allow SMS access"
            }
            1 -> {
                binding.onbIcon.setImageResource(R.drawable.ic_calendar)
                binding.onbTitle.text = "Bring in past payments"
                binding.onbBody.text = "Read the bank SMS already on your phone so this month and the last " +
                    "three show up right away."
                binding.onbPrimary.text = "Import last 90 days"
                if (!hasSms()) binding.onbStatus.text = "Needs SMS access from step 1."
            }
            else -> {
                binding.onbIcon.setImageResource(R.drawable.ic_cat_savings)
                binding.onbTitle.text = "Set a monthly budget"
                binding.onbBody.text = "Home will show what you can still spend each day. You can change it any time."
                binding.onbBudgetLayout.visibility = View.VISIBLE
                binding.onbPrimary.text = "Save and start"
            }
        }
    }

    private fun primary() {
        when (step) {
            0 -> if (hasSms()) {
                PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
                    .putBoolean("sms_tracking_enabled", true).apply()
                next()
            } else smsPermission.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS))
            1 -> importPast()
            else -> {
                AppPrefs.setMonthlyBudget(requireContext(), binding.onbBudget.text?.toString()?.toDoubleOrNull())
                finish()
            }
        }
    }

    private fun importPast() {
        if (!hasSms()) { next(); return }
        if (importing) return
        importing = true
        binding.onbPrimary.isEnabled = false
        binding.onbSkip.visibility = View.GONE
        binding.onbStatus.text = "Reading your bank SMS…"
        val request = OneTimeWorkRequestBuilder<SmsHistoryWorker>()
            .setInputData(workDataOf(SmsHistoryWorker.KEY_DAYS to 90))
            .build()
        val wm = WorkManager.getInstance(requireContext())
        wm.enqueueUniqueWork(SmsHistoryWorker.WORK_NAME, ExistingWorkPolicy.KEEP, request)
        wm.getWorkInfoByIdLiveData(request.id).observe(viewLifecycleOwner) { info ->
            if (info == null || !info.state.isFinished || _binding == null) return@observe
            importing = false
            val found = info.outputData.getInt(SmsHistoryWorker.KEY_RECORDED, 0)
            binding.onbStatus.text = if (found > 0) "Found $found payments." else "No bank payments found in the last 90 days."
            binding.onbPrimary.isEnabled = true
            binding.onbPrimary.text = "Continue"
            binding.onbPrimary.setOnClickListener { binding.onbPrimary.setOnClickListener { primary() }; next() }
        }
    }

    private fun next() {
        if (step >= 2) { finish(); return }
        step++
        show()
    }

    private var finished = false

    private fun finish() {
        if (finished || !isAdded) return
        finished = true
        AppPrefs.setOnboardingDone(requireContext())
        findNavController().navigate(
            R.id.ExpenseHomeFragment, null,
            NavOptions.Builder().setPopUpTo(R.id.OnboardingFragment, true).build(),
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
