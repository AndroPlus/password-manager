package com.androplus.pwdmgr.model

import com.google.gson.annotations.SerializedName
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import org.mongodb.kbson.ObjectId
import java.io.Serializable

open class UserApplication : RealmObject {
    @PrimaryKey
    var _id: ObjectId = ObjectId()
    @SerializedName("app_name")
    var app_name: String? = null
    @SerializedName("app_fields_json_array")
    var app_fields_json_array: String? = ""
}