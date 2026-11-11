package com.androplus.pwdmgr.worker

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.androplus.pwdmgr.services.ServerSync
import com.androplus.pwdmgr.utils.UploadClient
import com.androplus.pwdmgr.utils.UploadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Uploads the queued messages in batches; keeps them until the server confirms. */
class ServerSyncWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val url = prefs.getString(ServerSync.PREF_URL, "") ?: ""
        val token = prefs.getString(ServerSync.PREF_TOKEN, "") ?: ""
        if (!ServerSync.isEnabled(applicationContext) || url.isBlank() || token.isBlank()) {
            return@withContext Result.success()   // nothing to do; the queue is kept
        }

        val outbox = ServerSync.outbox(applicationContext)
        repeat(MAX_BATCHES_PER_RUN) {
            val batch = outbox.peek(BATCH_SIZE)
            if (batch.isEmpty()) return@withContext Result.success()
            when (val r = UploadClient.upload(url, token, batch)) {
                is UploadResult.Done -> {
                    outbox.remove(r.ids)
                    // The server answered but confirmed none of these: don't loop on them.
                    if (r.ids.isEmpty()) return@withContext Result.retry()
                }
                UploadResult.Unauthorized -> {
                    Log.w(TAG, "Server rejected the token or URL; uploads paused until settings change")
                    return@withContext Result.failure()
                }
                is UploadResult.RetryLater -> {
                    Log.d(TAG, "Upload deferred: ${r.reason}")
                    return@withContext Result.retry()
                }
            }
        }
        // More left than one run should send; continue in a fresh run.
        ServerSync.schedule(applicationContext)
        Result.success()
    }

    companion object {
        private const val TAG = "ServerSyncWorker"
        private const val BATCH_SIZE = 100
        private const val MAX_BATCHES_PER_RUN = 10
    }
}
