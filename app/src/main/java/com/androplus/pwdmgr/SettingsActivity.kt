package com.androplus.pwdmgr

import android.os.Bundle
import com.google.android.material.appbar.MaterialToolbar
import androidx.appcompat.app.AppCompatActivity
import com.androplus.pwdmgr.fragment.SettingsFragment

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // The app theme has no action bar, so the screen carries its own title and back arrow.
        findViewById<MaterialToolbar>(R.id.settingsToolbar).setNavigationOnClickListener { finish() }

        // Only add the fragment the first time; after a rotation Android restores it.
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.idFrameLayout, SettingsFragment())
                .commit()
        }
    }
}
