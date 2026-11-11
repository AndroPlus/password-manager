package com.androplus.pwdmgr.services

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.androplus.pwdmgr.utils.OutboundFilter
import com.androplus.pwdmgr.utils.OutboundMessage
import com.androplus.pwdmgr.utils.OutboxStore
import com.androplus.pwdmgr.worker.ServerSyncWorker
import java.io.File
import java.util.concurrent.TimeUnit

/** Queues bank SMS / payment-app notifications and schedules their upload. */
object ServerSync {

    const val PREF_ENABLED = "server_sync_enabled"
    const val PREF_URL = "server_url"
    const val PREF_TOKEN = "server_token"
    private const val WORK_NAME = "finintel-server-sync"

    @Volatile private var store: OutboxStore? = null

    fun outbox(context: Context): OutboxStore =
        store ?: synchronized(this) {
            store ?: OutboxStore(File(context.applicationContext.filesDir, "server_outbox.jsonl"))
                .also { store = it }
        }

    fun isEnabled(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREF_ENABLED, false)

    /** Queue a message if sync is on and it passes the privacy filter; then upload. */
    fun submit(context: Context, kind: String, source: String, title: String, text: String,
               receivedAt: Long) {
        if (!isEnabled(context) || !OutboundFilter.shouldSend(title, text)) return
        outbox(context).enqueue(OutboundMessage.create(kind, source, title, text, receivedAt))
        schedule(context)
    }

    fun schedule(context: Context) {
        val request = OneTimeWorkRequestBuilder<ServerSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // APPEND_OR_REPLACE: a message queued while an upload is running gets its
        // own follow-up run instead of waiting for the next message.
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}
