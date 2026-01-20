package com.androplus.pwdmgr.model

import com.google.gson.annotations.SerializedName
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import org.mongodb.kbson.ObjectId

open class ExpenseBudget : RealmObject {
    @PrimaryKey
    var _id: ObjectId = ObjectId()

    @SerializedName("category_id")
    var categoryId: String = ""

    @SerializedName("amount")
    var amount: Double = 0.0

    @SerializedName("period")
    var period: String = "MONTHLY" // Currently only supporting MONTHLY
}
