package com.androplus.pwdmgr

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.androplus.pwdmgr.databinding.ActivityMainBinding
import com.androplus.pwdmgr.fragment.AppListFragment
import com.androplus.pwdmgr.model.UserApplication
import com.androplus.pwdmgr.services.RealmService
import kotlinx.coroutines.launch

import androidx.appcompat.app.AppCompatDelegate

class MainActivity : AppCompatActivity(), AppListFragment.AdapterInteractionListener {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    public var _userApp: UserApplication? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.deleteIcon.setOnClickListener {
            binding.deleteIcon.visibility = View.GONE
            lifecycleScope.launch {
                RealmService.getInstance().removeApplication(binding.deleteIcon.tag as UserApplication)
            }
        }

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)
        
        // Removed Bottom Navigation setup

        // Visibility logic
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.LoginFragment -> {
                    // Logic to hide toolbar if needed, or other UI elements
                }
                else -> {
                    // Default logic
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                val i = Intent(this@MainActivity, SettingsActivity::class.java)
                startActivity(i)
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }

    override fun onItemLongClicked(item: UserApplication) {
        binding.deleteIcon.visibility = View.VISIBLE
        binding.deleteIcon.tag = item
    }

    interface RefreshAdapterListener {
        fun onRefreshAdapter()
    }
}