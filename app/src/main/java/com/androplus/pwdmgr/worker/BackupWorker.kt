package com.androplus.pwdmgr.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.androplus.pwdmgr.services.CloudBackup

/** Daily automatic backup to the place the user picked. */
class BackupWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    companion object { const val WORK_NAME = "daily-backup" }

    override suspend fun doWork(): Result =
        if (CloudBackup.backupNow(applicationContext) == null) Result.success() else Result.retry()
}
