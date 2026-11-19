package com.androplus.pwdmgr;

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.androplus.pwdmgr.model.LoginModel
import com.androplus.pwdmgr.services.RealmService
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import java.security.SecureRandom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class ApplicationManager : Application() {

    override fun onCreate() {
        super.onCreate()

        fun generate64CharacterString(): String {
            val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
            return (1..64)
                .map { chars.random() }
                .joinToString("")
        }

        fun storeEncryptedKey() {
            val randomString = generate64CharacterString()
            val sharedPreferences: SharedPreferences = applicationContext.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
            with(sharedPreferences.edit()) {
                putString("realm_encryption_key", randomString)
                apply()
            }
        }

        fun getDecryptedKey(): ByteArray? {
            val sharedPreferences: SharedPreferences = applicationContext.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
            val encryptedKey = sharedPreferences.getString("realm_encryption_key", null)
            return encryptedKey?.toByteArray()
        }
        var key = getDecryptedKey();
        if(key == null) {
            storeEncryptedKey();
            key = getDecryptedKey();
        }
        RealmService.getInstance(key);
        com.androplus.pwdmgr.services.AutoTransactionRecorder.init(this)
        com.androplus.pwdmgr.services.Nicknames.init(this)
        com.androplus.pwdmgr.services.Bills.schedule(this)

        // Initialize default expense categories
        CoroutineScope(Dispatchers.IO).launch {
            RealmService.getInstance().initializeDefaultCategories(
                com.androplus.pwdmgr.services.RemovedCategories.get(this@ApplicationManager))
            // One time after an update that improves the rules: fix entries saved
            // earlier (e.g. everything unrecognised filed under Shopping).
            RealmService.getInstance().refreshDefaultCategoryColors()
            val prefs = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
            if (prefs.getInt(RULES_VERSION_KEY, 0) < RULES_VERSION) {
                com.androplus.pwdmgr.services.AutoTransactionRecorder.reclassifyAll()
                prefs.edit().putInt(RULES_VERSION_KEY, RULES_VERSION).apply()
            }
        }

        setupNotificationWorker()
    }

    private fun setupNotificationWorker() {
        val workRequest = androidx.work.PeriodicWorkRequestBuilder<com.androplus.pwdmgr.worker.TodoNotificationWorker>(
            1, java.util.concurrent.TimeUnit.DAYS
        ).build()

        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "TodoNotificationWork",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    companion object {
        /** Bump when categorization rules change enough to re-check saved entries. */
        private const val RULES_VERSION = 3   // 3: digits-only UPI ids kept as payees
        private const val RULES_VERSION_KEY = "auto_rules_version"
    }
}
