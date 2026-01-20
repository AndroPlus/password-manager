package com.androplus.pwdmgr.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.androplus.pwdmgr.MainActivity
import com.androplus.pwdmgr.R
import com.androplus.pwdmgr.services.RealmService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.TimeZone

class TodoNotificationWorker(
    appContext: Context, 
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            checkOverdueTodos()
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    private fun checkOverdueTodos() {
        val realmService = RealmService.getInstance()
        val allTodos = realmService.getAllTodos() // This runs on main thread if not careful with Realm? 
        // RealmService.getAllTodos() calls realm.query().find() which is synchronous.
        // Since we are in CoroutineWorker (Dispatcher.IO), it might be okay depending on Realm configuration.
        // However, Realm instances are thread-confined. RealmService initializes realm on creation.
        // If RealmService uses a single realm instance created on main thread (or another thread), accessing it here might be an issue.
        // BUT, Realm Kotlin SDK (io.realm.kotlin) is thread-safe for frozen objects, and live objects are thread-confined.
        // Realm.open() gives a realm instance.
        // Let's assume RealmService is safe or we might need to handle it.
        // Actually RealmService getInstance() reuses the instance. 
        // If the instance was created on Main Thread (in Application.onCreate), checking it here might throw if we touch it wrongly.
        // But Realm Kotlin (MongoDB Realm) allows sharing Realm instances across coroutines?
        // Wait, Realm Kotlin IS thread-safe.
        
        val overdueTodos = allTodos.filter { todo ->
            if (todo.isCompleted || todo.dueDate == null) return@filter false
            
            val dueDate = todo.dueDate!!
            val todayStartUtc = getStartOfDayUtc()
            
            dueDate < todayStartUtc
        }

        if (overdueTodos.isNotEmpty()) {
            val count = overdueTodos.size
            val title = "Overdue Tasks"
            val message = "You have $count overdue tasks. Catch up now!"
            
            showNotification(title, message)
        }
    }
    
    private fun getStartOfDayUtc(): Long {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        
        val utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        utcCalendar.clear()
        utcCalendar.set(year, month, day)
        return utcCalendar.timeInMillis
    }

    private fun showNotification(title: String, message: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "todo_channel"

        // Create Channel if necessary (safe to call repeatedly)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Todo Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            applicationContext, 
            0, 
            intent, 
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Ensure this resource exists or use default
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        // Use a unique ID (e.g., 1001) for the notification
        notificationManager.notify(1001, builder.build())
    }
}
