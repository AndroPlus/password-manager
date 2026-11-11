package com.androplus.pwdmgr.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.androplus.pwdmgr.services.AutoTransactionRecorder
import com.androplus.pwdmgr.services.ServerSync
import com.androplus.pwdmgr.utils.InboxSms
import com.androplus.pwdmgr.utils.OutboundMessage
import com.androplus.pwdmgr.utils.SmsHistory
import com.androplus.pwdmgr.utils.TransactionParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withContext

/**
 * Imports bank SMS already in the inbox, so the tracker doesn't start empty.
 * Safe to run again: the local duplicate check and the upload ids skip messages
 * that were imported or captured before.
 */
class SmsHistoryWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return@withContext Result.failure(workDataOf(KEY_ERROR to "SMS permission not granted"))
        }
        val days = inputData.getInt(KEY_DAYS, 90)
        val cutoff = SmsHistory.cutoff(System.currentTimeMillis(), days)
        val bankSms = SmsHistory.select(readInbox(cutoff), cutoff)

        val jobs = bankSms.mapNotNull { sms ->
            TransactionParser.parseSms(sms.body)?.let { parsed ->
                AutoTransactionRecorder.record(parsed, "SMS", sms.body, at = sms.sentAt)
            }
        }
        jobs.joinAll()

        val uploaded = ServerSync.submitAll(applicationContext, bankSms.map {
            OutboundMessage.create("sms", "SMS", "", it.body, it.sentAt)
        })
        Result.success(workDataOf(
            KEY_SCANNED to bankSms.size,
            KEY_RECORDED to jobs.size,
            KEY_QUEUED to uploaded,
        ))
    }

    private fun readInbox(sinceMillis: Long): List<InboxSms> {
        val out = mutableListOf<InboxSms>()
        applicationContext.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} >= ?",
            arrayOf(sinceMillis.toString()),
            "${Telephony.Sms.DATE} ASC",
        )?.use { c ->
            val addr = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val body = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val date = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (c.moveToNext()) {
                out += InboxSms(c.getString(addr) ?: "", c.getString(body) ?: "", c.getLong(date))
            }
        }
        return out
    }

    companion object {
        const val WORK_NAME = "sms-history-import"
        const val KEY_DAYS = "days"
        const val KEY_SCANNED = "scanned"
        const val KEY_RECORDED = "recorded"
        const val KEY_QUEUED = "queued"
        const val KEY_ERROR = "error"
    }
}
