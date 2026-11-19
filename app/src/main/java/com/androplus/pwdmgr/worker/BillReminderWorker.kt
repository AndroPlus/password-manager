package com.androplus.pwdmgr.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.androplus.pwdmgr.services.Bills

/** Daily: remind about EMIs, rent, premiums and bills that are due soon and not yet paid. */
class BillReminderWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    companion object { const val WORK_NAME = "bill-reminders" }

    override suspend fun doWork(): Result {
        runCatching { Bills.notifyDue(applicationContext) }
        return Result.success()
    }
}
