package com.androplus.pwdmgr.model

import com.google.gson.annotations.SerializedName
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import org.mongodb.kbson.ObjectId

open class ExpenseTransaction : RealmObject {
    @PrimaryKey
    var _id: ObjectId = ObjectId()
    
    @SerializedName("amount")
    var amount: Double = 0.0
    
    @SerializedName("category_id")
    var categoryId: String = "" // Store category ID as string
    
    @SerializedName("category_name")
    var categoryName: String = "" // Denormalized for quick display
    
    @SerializedName("category_color")
    var categoryColor: String = "#0D47A1" // Denormalized for quick display

    @SerializedName("short_description")
    var shortDescription: String = ""
    
    @SerializedName("description")
    var description: String = ""
    
    @SerializedName("date")
    var date: Long = System.currentTimeMillis() // Unix timestamp
    
    @SerializedName("type")
    var type: String = "EXPENSE" // "EXPENSE" or "INCOME"

    @SerializedName("receipt_path")
    var receiptPath: String = ""
}
