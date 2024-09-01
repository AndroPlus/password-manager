package com.androplus.pwdmgr.fragment

import android.os.Build
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import com.androplus.pwdmgr.adapter.UserApplicationAdapter
import com.androplus.pwdmgr.model.UserApplication
import com.androplus.pwdmgr.services.CopyPasteUtils
import com.androplus.pwdmgr.services.EncryptDecrypt
import com.androplus.pwdmgr.services.RealmService
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter
import androidx.lifecycle.lifecycleScope
import com.androplus.pwdmgr.R
import com.androplus.pwdmgr.databinding.HomeLayoutBinding

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class HomeFragment : Fragment() {

    private var results: List<UserApplication> = listOf<UserApplication>()
    private var _binding: HomeLayoutBinding? = null
    var userApplicationAdapter: UserApplicationAdapter? = null
    var userApplicationList: List<UserApplication> = listOf<UserApplication>()
    // This property is only valid between onCreateView and
    // onDestroyView.12
    private val binding get() = _binding!!


    @RequiresApi(Build.VERSION_CODES.KITKAT)
    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {

        _binding = HomeLayoutBinding.inflate(inflater, container, false)
        setUI()
        return binding.root

    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    fun setUI() {

    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

//        binding.appLoginIdCopy.setOnClickListener {
//            CopyPasteUtils.getInstance(activity).copyText(binding.sourceAppLoginId);
//        }
//
//        binding.appLoginPasswordCopy.setOnClickListener {
//            CopyPasteUtils.getInstance(activity).copyText(binding.sourceAppPassword);
//        }
//        binding.saveBt.setOnClickListener {
//            val masterpass = arguments?.getString("master_pass")
//            var userApplication = UserApplication()
//           // userApplication.id = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
//            userApplication.app_name = binding.sourceApp.text.toString()
//
//           // userApplication.app_login_id  = EncryptDecrypt.getInstance().encryptNew(binding.sourceAppLoginId.text.toString().toByteArray(), masterpass)
//          //  userApplication.app_password = EncryptDecrypt.getInstance().encryptNew(binding.sourceAppPassword.text.toString().toByteArray(), masterpass)
//            lifecycleScope.launch {
//                if(RealmService.getInstance().createApplication(userApplication))
//                {
//                    binding.sourceApp.text.clear()
//                    binding.sourceAppLoginId.text.clear()
//                    binding.sourceAppPassword.text.clear()
//                    setUI()
//                }
//            }
//        }
//
//        binding.showAppIdBtn.setOnClickListener {
//            if(binding.sourceAppLoginId.transformationMethod == PasswordTransformationMethod.getInstance()) {
//                binding.showAppIdBtn.setImageResource(R.drawable.ic_custom_hide)
//                binding.sourceAppLoginId.transformationMethod = HideReturnsTransformationMethod.getInstance();
//            } else {
//                binding.showAppIdBtn.setImageResource(R.drawable.ic_custom_show)
//                binding.sourceAppLoginId.transformationMethod = PasswordTransformationMethod.getInstance();
//            }
//        }
//
//        binding.showAppPassBtn.setOnClickListener {
//            if(binding.sourceAppPassword.transformationMethod == PasswordTransformationMethod.getInstance()) {
//                binding.showAppPassBtn.setImageResource(R.drawable.ic_custom_hide)
//                binding.sourceAppPassword.transformationMethod = HideReturnsTransformationMethod.getInstance();
//            } else {
//                binding.showAppPassBtn.setImageResource(R.drawable.ic_custom_show)
//                binding.sourceAppPassword.transformationMethod = PasswordTransformationMethod.getInstance();
//            }
//        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}