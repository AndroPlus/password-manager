package com.androplus.pwdmgr.services

import com.androplus.pwdmgr.model.LoginModel
import com.androplus.pwdmgr.model.UserApplication
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.delete
import io.realm.kotlin.ext.query
import io.realm.kotlin.internal.getRealm
import io.realm.kotlin.query.RealmResults

class RealmService private constructor() {

    private val realm: Realm

    init {
        val config = RealmConfiguration.Builder(schema = setOf(LoginModel::class, UserApplication::class))
            .schemaVersion(1)
            .deleteRealmIfMigrationNeeded()
            .build()

        realm = Realm.open(config)
    }

    companion object {
        private var realmService: RealmService? = null

        fun getInstance(): RealmService {
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

     fun getAllUserApplications(app_name: String?): List<UserApplication> {
        val realmResults: RealmResults<UserApplication> = if (!app_name.isNullOrEmpty()) {
            realm.query<UserApplication>("app_name == $0", app_name).find()
        } else {
            realm.query<UserApplication>().find()
        }
        return realmResults.toList()
    }
}
