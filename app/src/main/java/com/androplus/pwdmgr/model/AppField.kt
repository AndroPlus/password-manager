package com.androplus.pwdmgr.model

import com.google.gson.annotations.SerializedName
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

class AppField  : RealmObject {
    @PrimaryKey
    var id: String? = null
    @SerializedName("source_user_app_id")
    var user_app_id: String? = null
    @SerializedName("source_app_login_id")
    var app_login_id: String? = null
    @SerializedName("source_app_password")
    var app_password: String? = null
}