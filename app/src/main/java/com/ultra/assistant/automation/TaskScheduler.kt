package com.ultra.assistant.automation

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Schedules user-requested reminders using WorkManager (survives process death/reboot). */
class TaskScheduler(context: Context) {
    private val appContext = context.applicationContext

    fun scheduleReminder(title: String, delayMinutes: Long): String {
        require(title.isNotBlank()) { "Reminder title is required" }
        require(delayMinutes in 1..525600) { "Delay must be between 1 minute and 365 days" }
        val request = OneTimeWorkRequestBuilder<TaskReminderWorker>()
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .setInputData(Data.Builder().putString(TaskReminderWorker.KEY_TITLE, title.trim()).build())
            .build()
        WorkManager.getInstance(appContext).enqueue(request)
        return request.id.toString()
    }

    fun cancel(id: String) {
        runCatching { WorkManager.getInstance(appContext).cancelWorkById(java.util.UUID.fromString(id)) }
    }
}
