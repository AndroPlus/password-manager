package com.androplus.pwdmgr.services

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.preference.PreferenceManager
import com.androplus.pwdmgr.model.ExpenseTransaction
import com.androplus.pwdmgr.utils.TransactionParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TransactionNotificationListener : NotificationListenerService() {

    private val supportedPackages = setOf(
        "com.google.android.apps.nbu.paisa.user", // Google Pay (GPay)
        "com.phonepe.app" // PhonePe
    )

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!isTrackingEnabled()) return

        val packageName = sbn.packageName
        if (packageName in supportedPackages) {
            val notification = sbn.notification
            val title = notification.extras.getString("android.title") ?: ""
            val text = notification.extras.getCharSequence("android.text")?.toString() ?: ""

            Log.d("NotificationListener", "Notification from $packageName: $title - $text")

            val fullContent = "$title $text"
            val parsed = TransactionParser.parseSms(fullContent) // Reuse SMS parser for now

            if (parsed != null) {
                saveTransaction(parsed.amount, parsed.merchant, text, parsed.isExpense)
            }
        }
    }

    private fun isTrackingEnabled(): Boolean {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        return sharedPrefs.getBoolean("payment_app_tracking_enabled", false)
    }

    private fun saveTransaction(amount: Double, merchant: String, fullDescription: String, isExpense: Boolean) {
        val type = if (isExpense) "EXPENSE" else "INCOME"

        val realmService = RealmService.getInstance()
        val categories = realmService.getAllCategories()
        val defaultCategory = categories.find { it.name.contains("Shopping", true) || it.name.contains("Other", true) } 
            ?: categories.firstOrNull()

        val expense = ExpenseTransaction().apply {
            this.amount = amount
            this.shortDescription = "[Auto] $merchant"
            this.description = fullDescription
            this.type = type
            this.date = System.currentTimeMillis()
            if (defaultCategory != null) {
                this.categoryId = defaultCategory._id.toHexString()
                this.categoryName = defaultCategory.name
                this.categoryColor = defaultCategory.color
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            val success = realmService.createExpense(expense)
            Log.d("NotificationListener", "Saved automated transaction: $success")
        }
    }
}
