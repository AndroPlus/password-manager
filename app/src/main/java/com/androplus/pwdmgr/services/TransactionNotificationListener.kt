package com.androplus.pwdmgr.services

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.preference.PreferenceManager
import com.androplus.pwdmgr.utils.TransactionParser

class TransactionNotificationListener : NotificationListenerService() {

    private val sourceByPackage = mapOf(
        "com.google.android.apps.nbu.paisa.user" to "GPay",
        "com.phonepe.app" to "PhonePe",
        "net.one97.paytm" to "Paytm",
    )

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val source = sourceByPackage[sbn.packageName] ?: return
        if (!isTrackingEnabled()) return

        val extras = sbn.notification?.extras ?: return
        // Titles are often styled text (SpannableString), which getString() returns as null.
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        val parsed = TransactionParser.parseNotification(title, text) ?: return
        AutoTransactionRecorder.record(parsed, source, "$title\n$text")
    }

    private fun isTrackingEnabled(): Boolean {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        return sharedPrefs.getBoolean("payment_app_tracking_enabled", false)
    }
}
