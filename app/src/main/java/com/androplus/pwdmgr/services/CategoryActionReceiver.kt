package com.androplus.pwdmgr.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A category button on the "new payee" notification was tapped. */
class CategoryActionReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_PAYEE = "payee"
        const val EXTRA_CATEGORY = "category"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_TEST = "test"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_TEST, false)) {
            NotificationManagerCompat.from(context).cancel(intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))
            Toast.makeText(context, "Test prompt works. Real ones file the payee.", Toast.LENGTH_SHORT).show()
            return
        }
        val payee = intent.getStringExtra(EXTRA_PAYEE) ?: return
        val category = intent.getStringExtra(EXTRA_CATEGORY) ?: return
        NotificationManagerCompat.from(context).cancel(intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))

        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AutoTransactionRecorder.init(app)
                // Learns the payee and moves this and any earlier entries from it.
                AutoTransactionRecorder.learn(payee, true, category)
                withContext(Dispatchers.Main) {
                    Toast.makeText(app, "Saved. Future payments here go to $category.", Toast.LENGTH_SHORT).show()
                }
            } finally {
                pending.finish()
            }
        }
    }
}
