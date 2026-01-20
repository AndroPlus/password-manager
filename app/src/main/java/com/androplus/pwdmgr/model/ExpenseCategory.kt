package com.androplus.pwdmgr.model

import com.google.gson.annotations.SerializedName
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import org.mongodb.kbson.ObjectId

open class ExpenseCategory : RealmObject {
    @PrimaryKey
    var _id: ObjectId = ObjectId()
    
    @SerializedName("name")
    var name: String = ""
    
    @SerializedName("icon")
    var icon: String = "" // Icon identifier (e.g., "food", "transport")
    
    @SerializedName("color")
    var color: String = "#0D47A1" // Hex color code
    
    @SerializedName("is_default")
    var isDefault: Boolean = false // True for pre-installed categories
}
