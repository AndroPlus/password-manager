package com.androplus.pwdmgr.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.androplus.pwdmgr.MainActivity
import com.androplus.pwdmgr.R
import com.androplus.pwdmgr.adapter.AppsListAdapter
import com.androplus.pwdmgr.databinding.AppsListBinding
import com.androplus.pwdmgr.model.UserApplication
import com.androplus.pwdmgr.services.RealmService
import io.realm.kotlin.mongodb.User

class AppListFragment : Fragment() {
    private var _appListAdapter: AppsListAdapter? = null
    private var _binding: AppsListBinding? = null
    private var results: List<UserApplication> = listOf<UserApplication>()
    private lateinit var listener: AdapterInteractionListener
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
        _binding = AppsListBinding.inflate(inflater, container, false)

        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().addMenuProvider( object: MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_main, menu)
            }
            override fun onPrepareMenu(menu: Menu) {
               // menu.findItem(R.id.action_settings).isVisible = true
            }
            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        setUpUI()
    }

    fun setUpUI() {
        binding.addApp.setOnClickListener(View.OnClickListener {
            navgiateToAppCreatePage()
        })
        results  = RealmService.getInstance().getAllUserApplications();
        binding.appList.layoutManager = LinearLayoutManager(context)
        _appListAdapter = AppsListAdapter(results, {
            item -> onListItemClick(item)
        },
        {
        item -> listener.onItemLongClicked(item)
        })

        binding.appList.adapter = _appListAdapter
    }

    fun onListItemClick( item:UserApplication){
        Toast.makeText(context, "Clicked ${item.app_name}", Toast.LENGTH_SHORT).show()
        navgiateToAppCreatePage(item)
    }

    fun navgiateToAppCreatePage(userApplication: UserApplication?=null) {
        // move to home fragment
        val navigate = activity?.findNavController(R.id.nav_host_fragment_content_main)
        (activity as MainActivity)._userApp = userApplication
        val bundle = Bundle()
        //bundle.putString("master_pass", binding.appPassword.text.toString())
        //if(navigate?.popBackStack(R.id.LoginFragment, true) == true){
        navigate?.navigate(R.id.action_app_add,bundle)
        //}

    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is AdapterInteractionListener) {
            listener = context
        } else {
            throw RuntimeException("$context must implement AdapterInteractionListener")
        }
    }


    interface AdapterInteractionListener {
        fun onItemLongClicked(item: UserApplication)
    }
}
