package com.ultra.assistant.automation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters

class TaskReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE)?.takeIf { it.isNotBlank() } ?: "ULTRA AI reminder"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "ULTRA AI reminders", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("ULTRA AI Reminder")
            .setContentText(title)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(id.hashCode(), notification)
        applicationContext.getSharedPreferences("ultra_scheduled_tasks", Context.MODE_PRIVATE)
            .edit().remove(id.toString()).apply()
        return Result.success()
    }

    companion object {
        const val KEY_TITLE = "reminder_title"
        private const val CHANNEL = "ultra_reminders"
    }
}
