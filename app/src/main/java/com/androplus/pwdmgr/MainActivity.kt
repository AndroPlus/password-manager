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
import kotlin.random.Random

class MainActivity : AppCompatActivity(), AppListFragment.AdapterInteractionListener {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    public var _userApp: UserApplication? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.deleteIcon.setOnClickListener {
            binding.deleteIcon.visibility = View.GONE
            lifecycleScope.launch {
                RealmService.getInstance().removeApplication(binding.deleteIcon.tag as UserApplication)
            }
        }

      //  setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        /*binding.fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
        }*/
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        //menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
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