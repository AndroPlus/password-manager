package com.androplus.pwdmgr.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import androidx.preference.PreferenceManager
import com.androplus.pwdmgr.model.ExpenseTransaction
import com.androplus.pwdmgr.utils.TransactionParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
            val isSmsTrackingEnabled = sharedPrefs.getBoolean("sms_tracking_enabled", false)

            if (!isSmsTrackingEnabled) {
                Log.d("SmsReceiver", "SMS Tracking is disabled in settings.")
                return
            }

            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val messageBody = sms.messageBody
                val sender = sms.displayOriginatingAddress

                Log.d("SmsReceiver", "Received SMS from $sender: $messageBody")

                val isPotentialBankSms = sender.any { it.isLetter() } || sender.length < 10

                if (isPotentialBankSms) {
                    val parsed = TransactionParser.parseSms(messageBody)
                    if (parsed != null) {
                        saveTransaction(parsed.amount, parsed.merchant, messageBody, parsed.isExpense)
                    }
                }
            }
        }
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
            Log.d("SmsReceiver", "Saved automated transaction: $success")
        }
    }
}
