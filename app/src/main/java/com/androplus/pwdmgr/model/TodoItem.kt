package com.androplus.pwdmgr.model

import com.google.gson.annotations.SerializedName
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import org.mongodb.kbson.ObjectId

open class TodoItem : RealmObject {
    @PrimaryKey
    var _id: ObjectId = ObjectId()
    
    @SerializedName("title")
    var title: String = ""
    
    @SerializedName("description")
    var description: String = ""
    
    @SerializedName("is_completed")
    var isCompleted: Boolean = false
    
    @SerializedName("priority")
    var priority: String = "MEDIUM" // "HIGH", "MEDIUM", "LOW"

    @SerializedName("category")
    var category: String = "PERSONAL" // "WORK", "PERSONAL", "SHOPPING", "HEALTH", "LEARNING", "BILLS"
        
    @SerializedName("due_date")
    var dueDate: Long? = null
    
    @SerializedName("created_at")
    var createdAt: Long = System.currentTimeMillis()
}
