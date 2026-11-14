package com.androplus.pwdmgr.services

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.preference.PreferenceManager
import com.androplus.pwdmgr.utils.NotificationLog
import com.androplus.pwdmgr.utils.SeenNotification
import com.androplus.pwdmgr.utils.TransactionParser
import java.io.File

class TransactionNotificationListener : NotificationListenerService() {

    companion object {
        val sourceByPackage = mapOf(
            "com.google.android.apps.nbu.paisa.user" to "GPay",
            "com.phonepe.app" to "PhonePe",
            "net.one97.paytm" to "Paytm",
        )

        /** Recent payment-app notifications, kept on the phone only. */
        fun log(context: Context) = NotificationLog(File(context.filesDir, "seen_notifications.jsonl"))

        fun hasAccess(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            return enabled?.contains(context.packageName) == true
        }

        /** Asks Android to reconnect the listener, e.g. after access was re-granted. */
        fun rebind(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                NotificationListenerService.requestRebind(ComponentName(context, TransactionNotificationListener::class.java))
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val source = sourceByPackage[sbn.packageName] ?: return

        val extras = sbn.notification?.extras ?: return
        // Titles are often styled text (SpannableString), which getString() returns as null.
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = (extras.getCharSequence("android.bigText") ?: extras.getCharSequence("android.text"))
            ?.toString() ?: ""

        if (!isTrackingEnabled()) {
            remember(source, title, text, "tracking is off")
            return
        }

        ServerSync.submit(this, "notification", source, title, text, sbn.postTime)

        val parsed = TransactionParser.parseNotification(title, text)
        remember(source, title, text, if (parsed != null) "recorded ₹${parsed.amount}" else "not a payment")
        if (parsed != null) AutoTransactionRecorder.record(parsed, source, "$title\n$text")
    }

    private fun remember(source: String, title: String, text: String, outcome: String) {
        runCatching {
            log(this).add(SeenNotification(System.currentTimeMillis(), source, title, text, outcome))
        }
    }

    private fun isTrackingEnabled(): Boolean {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        return sharedPrefs.getBoolean("payment_app_tracking_enabled", false)
    }
}
