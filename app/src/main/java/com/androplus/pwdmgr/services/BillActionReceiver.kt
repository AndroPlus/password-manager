package com.androplus.pwdmgr.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/** "Mark paid" on a bill reminder notification. */
class BillActionReceiver : BroadcastReceiver() {
    companion object {
        const val EXTRA_ID = "reminder_id"
        const val EXTRA_DUE = "due_millis"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        Bills.markPaid(context, id, intent.getLongExtra(EXTRA_DUE, System.currentTimeMillis()))
        Toast.makeText(context, "Marked paid", Toast.LENGTH_SHORT).show()
    }
}
