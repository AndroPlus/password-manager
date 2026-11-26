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
        const val EXTRA_EXPENSE_ID = "expense_id"
        /** Text typed into the notification's "+ New category" box. */
        const val KEY_NEW_CATEGORY = "new_category"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_TEST, false)) {
            NotificationManagerCompat.from(context).cancel(intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))
            Toast.makeText(context, "Test prompt works. Real ones file the payee.", Toast.LENGTH_SHORT).show()
            return
        }
        val payee = intent.getStringExtra(EXTRA_PAYEE) ?: return
        val typed = androidx.core.app.RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(KEY_NEW_CATEGORY)?.toString()
        val picked = intent.getStringExtra(EXTRA_CATEGORY)
        if (typed.isNullOrBlank() && picked == null) return
        NotificationManagerCompat.from(context).cancel(intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))

        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AutoTransactionRecorder.init(app)
                // A typed name creates the category (or reuses one with that name).
                val category = if (!typed.isNullOrBlank()) Categories.ensure(app, typed) else picked
                if (category == null) return@launch
                val expenseId = intent.getStringExtra(EXTRA_EXPENSE_ID)
                val message = if (com.androplus.pwdmgr.utils.CategoryMemory.key(payee, true) == null) {
                    // The SMS named no payee: file just this payment.
                    if (expenseId != null) AutoTransactionRecorder.setCategory(expenseId, category)
                    "Saved this payment under $category."
                } else {
                    // Learns the payee and moves this and any earlier entries from it.
                    AutoTransactionRecorder.learn(payee, true, category)
                    "Saved. Future payments here go to $category."
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(app, message, Toast.LENGTH_SHORT).show()
                }
            } finally {
                pending.finish()
            }
        }
    }
}
