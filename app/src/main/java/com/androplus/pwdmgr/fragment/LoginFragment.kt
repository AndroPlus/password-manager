package com.androplus.pwdmgr.fragment

import android.os.Build
import android.os.Bundle
import android.text.Editable
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import com.androplus.pwdmgr.R
import com.androplus.pwdmgr.adapter.UserApplicationAdapter
import com.androplus.pwdmgr.databinding.LoginLayoutBinding
import com.androplus.pwdmgr.model.LoginModel
import com.androplus.pwdmgr.model.UserApplication
import com.androplus.pwdmgr.services.EncryptDecrypt
import com.androplus.pwdmgr.services.RealmService
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class LoginFragment : Fragment() {

    private var results: List<UserApplication> = listOf<UserApplication>()
    private var _binding: LoginLayoutBinding? = null
    var userApplicationAdapter: UserApplicationAdapter? = null
    var userApplicationList: List<UserApplication> = listOf<UserApplication>()
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        _binding = LoginLayoutBinding.inflate(inflater, container, false)
        setupUI()
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)




    }

    fun setupUI() {
        val loginData = RealmService.getInstance().getLoginData()
        if (!loginData?.id.isNullOrEmpty()) {
            binding.confirmPin.visibility = View.GONE
            binding.confirmPinLbl.visibility = View.GONE
            binding.firstPinView.addTextChangedListener(
                afterTextChanged = { text: Editable? ->
                    run {
                        if (text.toString().count() == 5) {
                            val decryptedText = EncryptDecrypt.getInstance()
                                .decryptNew(loginData?.user_password, text.toString())
                            if (!decryptedText.isNullOrBlank() && decryptedText.equals(text.toString())) {
                                binding.firstPinView.text?.clear()
                                navgiateToHomePage()
                                Toast.makeText(context, "Logged In.. ", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Pin mismatch.. ", Toast.LENGTH_LONG).show()
                            }

                        }
                    }
                })
        }

        binding.confirmPin.addTextChangedListener(
            afterTextChanged = { text: Editable? ->
                run {
                    if (!binding.firstPinView.text.isNullOrEmpty() && binding.firstPinView.text.toString().equals(text.toString())) {
                        Toast.makeText(context, "Equals..", Toast.LENGTH_LONG).show()
                        val loginModel = LoginModel()
                        loginModel.id = "1"
                        loginModel.user_password = EncryptDecrypt.getInstance().encryptNew(
                            binding.confirmPin.text.toString().toByteArray(),
                            binding.confirmPin.text.toString()
                        )
                        lifecycleScope.launch {
                            RealmService.getInstance().createLogin(loginModel)
                        }
                        navgiateToHomePage()
                    }
                }
            },
        )
    }
    fun navgiateToHomePage() {
        // move to home fragment
        val navigate = activity?.findNavController(R.id.nav_host_fragment_content_main)
        val bundle = Bundle()
        //bundle.putString("master_pass", binding.appPassword.text.toString())
        //if(navigate?.popBackStack(R.id.LoginFragment, true) == true){
            navigate?.navigate(R.id.action_login_home,bundle)
        //}

    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}