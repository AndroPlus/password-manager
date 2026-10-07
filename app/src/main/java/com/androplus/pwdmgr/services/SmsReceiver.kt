package com.androplus.pwdmgr.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import androidx.preference.PreferenceManager
import com.androplus.pwdmgr.utils.TransactionParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (!sharedPrefs.getBoolean("sms_tracking_enabled", false)) {
            Log.d("SmsReceiver", "SMS Tracking is disabled in settings.")
            return
        }

        // A long bank SMS arrives in several parts; parse the joined text, not each part.
        val bodiesBySender = linkedMapOf<String, StringBuilder>()
        for (sms in Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return) {
            val sender = sms.displayOriginatingAddress ?: continue
            bodiesBySender.getOrPut(sender) { StringBuilder() }.append(sms.messageBody ?: "")
        }

        val jobs = bodiesBySender.mapNotNull { (sender, body) ->
            if (!looksLikeBankSender(sender)) return@mapNotNull null
            val text = body.toString()
            val parsed = TransactionParser.parseSms(text) ?: return@mapNotNull null
            AutoTransactionRecorder.record(parsed, "SMS", text)
        }
        if (jobs.isEmpty()) return

        // Keep the receiver alive until the save finishes; once onReceive returns,
        // Android may kill the process and lose the transaction.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                jobs.joinAll()
            } finally {
                pending.finish()
            }
        }
    }

    // Banks send from alphanumeric headers (e.g. "AX-AXISBK") or short codes,
    // never from a regular 10-digit mobile number.
    private fun looksLikeBankSender(sender: String): Boolean =
        sender.any { it.isLetter() } || sender.length < 10
}
