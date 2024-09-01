package com.androplus.pwdmgr.fragment

import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import com.androplus.pwdmgr.MainActivity
import com.androplus.pwdmgr.R
import com.androplus.pwdmgr.databinding.AppsCreateLayoutBinding
import com.androplus.pwdmgr.model.AppField
import com.androplus.pwdmgr.model.UserApplication
import com.androplus.pwdmgr.services.EncryptDecrypt
import com.androplus.pwdmgr.services.RealmService
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import kotlinx.coroutines.launch


class AppCreateFragment : Fragment(), View.OnClickListener {
    private var _binding: AppsCreateLayoutBinding? = null
    private var results: List<UserApplication> = listOf<UserApplication>()
    private var application: UserApplication? = null
    private var selectApplication: UserApplication? = null
    val dynamicLayouts: MutableList<View> = mutableListOf()
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = AppsCreateLayoutBinding.inflate(inflater, container, false)
        setUpUI(container)
        return binding.root
    }

    fun setUpUI(container: ViewGroup?) {
       var isEdit : Boolean = false
          val inflater = LayoutInflater.from(context)

        if((activity as MainActivity)._userApp !=null) {
            val userApplicationArray =  RealmService.getInstance().getAllUserApplications((activity as MainActivity)._userApp)

            if(userApplicationArray.size > 0) {
                isEdit = true
                selectApplication = userApplicationArray[0]
                binding.sourceApp.setText(selectApplication!!.app_name)

                val appFieldsString = selectApplication!!.app_fields_json_array
                val jsonParser = JsonParser()
                jsonParser.parse(appFieldsString).asJsonArray.forEachIndexed { index, jsonElement ->
                    val gson = Gson()
                    val jsonObject = jsonElement.asString
                    val appField = gson.fromJson(jsonObject, AppField::class.java)
                    val dynamicLayout = inflater.inflate(R.layout.dynamic_layout, container, false)
                    dynamicLayout.tag = appField;
                    dynamicLayout.findViewById<EditText>(R.id.source_app_title).setText(appField!!.app_title)
                    dynamicLayout.findViewById<EditText>(R.id.source_app_login_id).setText(appField!!.app_login_id)
                    dynamicLayout.findViewById<EditText>(R.id.source_app_password).setText(appField!!.app_password)
                    binding.containerLayout?.addView(dynamicLayout)
                    dynamicLayouts.add(dynamicLayout)
                    dynamicLayout.findViewById<ImageView>(R.id.show_app_pass_btn).setOnClickListener(this)
                    dynamicLayout.findViewById<ImageView>(R.id.show_app_pass_btn).tag = dynamicLayout
                    dynamicLayout.findViewById<ImageView>(R.id.show_app_id_btn).setOnClickListener(this)
                    dynamicLayout.findViewById<ImageView>(R.id.show_app_id_btn).tag = dynamicLayout
                    dynamicLayout.findViewById<ImageView>(R.id.app_field_delete).setOnClickListener(this)
                    dynamicLayout.findViewById<ImageView>(R.id.app_field_delete).tag = index
                }
            }

        }

        binding.saveApp.setOnClickListener(View.OnClickListener {
            application = UserApplication()
            application!!.app_name = binding.sourceApp.text.toString()
            val appFields = JsonArray()
            dynamicLayouts.forEach {   view->
                var appField = AppField()
                appField.app_login_id = view.findViewById<EditText>(R.id.source_app_login_id).text.toString()
                appField.app_password = view.findViewById<EditText>(R.id.source_app_password).text.toString()
                appField.app_title = view.findViewById<EditText>(R.id.source_app_title).text.toString()
                val gson = Gson()
                appFields.add(gson.toJson(appField))
            }
            application!!.app_fields_json_array = appFields.toString()
            lifecycleScope.launch {
                if(isEdit) {
                    application!!._id = selectApplication!!._id
                    RealmService.getInstance().updateApplication(application!!)
                }else {
                    RealmService.getInstance().createApplication(application!!)
                }

                val navigate = activity?.findNavController(R.id.nav_host_fragment_content_main)
                navigate?.popBackStack()
           }
        })

        binding.addMoreApp.setOnClickListener {
            val dynamicLayout = inflater.inflate(R.layout.dynamic_layout, container, false)
            binding.containerLayout?.addView(dynamicLayout)
            dynamicLayouts.add(dynamicLayout)
            dynamicLayout.findViewById<ImageView>(R.id.show_app_pass_btn).setOnClickListener(this)
            dynamicLayout.findViewById<ImageView>(R.id.show_app_pass_btn).tag = dynamicLayout
            dynamicLayout.findViewById<ImageView>(R.id.show_app_id_btn).setOnClickListener(this)
            dynamicLayout.findViewById<ImageView>(R.id.show_app_id_btn).tag = dynamicLayout
            dynamicLayout.findViewById<ImageView>(R.id.app_field_delete).setOnClickListener(this)
            dynamicLayout.findViewById<ImageView>(R.id.app_field_delete).tag = dynamicLayouts.size -1
        }
    }

    override fun onClick(p0: View?) {
        var loginIDPass: EditText? = null
        if ((p0 as ImageView).id == R.id.show_app_id_btn) {
            loginIDPass = (p0?.tag as View).findViewById(R.id.source_app_login_id)
        } else if ((p0 as ImageView).id == R.id.show_app_pass_btn) {
            loginIDPass = (p0?.tag as View).findViewById(R.id.source_app_password)
        }
        if(loginIDPass !=null) {
            if (loginIDPass?.transformationMethod == PasswordTransformationMethod.getInstance()) {
                p0.setImageResource(R.drawable.ic_custom_hide)
                loginIDPass?.transformationMethod = HideReturnsTransformationMethod.getInstance();
            } else {
                p0.setImageResource(R.drawable.ic_custom_show)
                loginIDPass?.transformationMethod = PasswordTransformationMethod.getInstance();
            }
        }


        if (p0.id == R.id.app_field_delete) {
            dynamicLayouts.removeAt(p0.tag as Int)
            binding.containerLayout.removeViewAt(p0.tag as Int)
            binding.containerLayout.invalidate()
        }
    }
}