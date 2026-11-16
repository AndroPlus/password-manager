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
        // Screens that draw their own header have no toolbar.
        val noToolbar = setOf(
            R.id.ModuleSelectionFragment, R.id.LoginFragment,
            R.id.ExpenseHomeFragment, R.id.ExpenseListFragment, R.id.InsightsFragment,
        )
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id in noToolbar) supportActionBar?.hide() else supportActionBar?.show()
        }

        if (savedInstanceState == null) openExpenseFrom(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        openExpenseFrom(intent)
    }

    /** From the "new payee" notification: open that transaction to pick its category. */
    private fun openExpenseFrom(intent: android.content.Intent?) {
        if (intent?.getBooleanExtra(com.androplus.pwdmgr.services.CategoryPrompt.EXTRA_OPEN_PAYEE_SORT, false) == true) {
            intent.removeExtra(com.androplus.pwdmgr.services.CategoryPrompt.EXTRA_OPEN_PAYEE_SORT)
            binding.root.post {
                findNavController(R.id.nav_host_fragment_content_main).navigate(R.id.PayeeSortFragment)
            }
            return
        }
        val id = intent?.getStringExtra(com.androplus.pwdmgr.services.CategoryPrompt.EXTRA_EDIT_EXPENSE_ID) ?: return
        intent.removeExtra(com.androplus.pwdmgr.services.CategoryPrompt.EXTRA_EDIT_EXPENSE_ID)
        // After the start screen has opened the Expense Tracker, so Back returns there.
        binding.root.post {
            findNavController(R.id.nav_host_fragment_content_main)
                .navigate(R.id.ExpenseAddFragment, Bundle().apply { putString("expense_id", id) })
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