package com.androplus.pwdmgr;

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.androplus.pwdmgr.model.LoginModel
import com.androplus.pwdmgr.services.RealmService
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import java.security.SecureRandom


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

    }
}

