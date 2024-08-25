package com.androplus.pwdmgr.model

import com.google.gson.annotations.SerializedName
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.ObjectId

open class LoginModel : RealmObject {
    @PrimaryKey
    var id: String? = null
    @SerializedName("user_password")
    var user_password: String? = null
}