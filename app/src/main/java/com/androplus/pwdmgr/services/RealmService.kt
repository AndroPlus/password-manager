package com.androplus.pwdmgr.services

import android.util.Log
import com.androplus.pwdmgr.MainActivity
import com.androplus.pwdmgr.model.LoginModel
import com.androplus.pwdmgr.model.UserApplication
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.ext.query
import io.realm.kotlin.query.RealmResults
import kotlinx.coroutines.runBlocking
import java.security.SecureRandom

class RealmService private constructor() {

    private lateinit var realm: Realm


    init {
        val config = RealmConfiguration.Builder(schema = setOf(LoginModel::class, UserApplication::class))
            .deleteRealmIfMigrationNeeded()
            .name("pwdDB")
            .encryptionKey(key!!)
            .build()

        try {
            realm = Realm.open(config)
            Log.d("RealmInit", "Realm initialized successfully.")
        } catch (e: Exception) {
            Log.e("RealmInit", "Error initializing Realm: ${e.message}")
        }
    }

    companion object {
        private var realmService: RealmService? = null
        var key:ByteArray? = null
        fun getInstance(key: ByteArray? = null): RealmService {
            this.key = key
            if (realmService == null) {
                realmService = RealmService()
            }
            return realmService!!
        }
    }

   suspend  fun createLogin(loginModel: LoginModel): Boolean {
        return try {
            realm.write {
                copyToRealm(loginModel)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

     fun getLoginData(): LoginModel? {
        return realm.query<LoginModel>("id == $0", "1").first().find()
    }

   suspend fun createApplication(userApplication: UserApplication): Boolean {
        return try {
            realm.write {
                copyToRealm(userApplication)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun updateApplication(userApp: UserApplication): Boolean {
        return try {
            realm.write {
                val userApplication = query<UserApplication>("_id == $0", userApp._id).find().first()
                userApplication.app_name = userApp.app_name
                userApplication.app_fields_json_array = userApp.app_fields_json_array
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun removeApplication(userApp: UserApplication): Boolean {
        return try {
            realm.write {
                val objectToDelete = query<UserApplication>("_id == $0", userApp._id).find().first()
               delete(objectToDelete)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

     fun getAllUserApplications(userApp: UserApplication? = UserApplication()): List<UserApplication> {
        val realmResults: RealmResults<UserApplication> = if (!userApp?.app_name.isNullOrEmpty()) {
            realm.query<UserApplication>("_id == $0", userApp?._id).find()
        } else {
            realm.query<UserApplication>().find()
        }
        return realmResults.toList()
    }
}
