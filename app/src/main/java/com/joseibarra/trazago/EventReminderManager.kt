package com.joseibarra.trazago

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object EventReminderManager {

    private const val PREFS_NAME = "event_reminders"
    private const val KEY_SCHEDULED_PREFIX = "scheduled_"

    fun scheduleReminder(context: Context, event: Event, minutesBefore: Long) {
        val eventId = event.id
        val startDateMs = event.startDate?.time ?: return

        val triggerAt = startDateMs - (minutesBefore * 60_000)

        val workData = Data.Builder()
            .putString(EventReminderWorker.KEY_EVENT_ID, eventId)
            .putString(EventReminderWorker.KEY_EVENT_TITLE, event.title)
            .putLong(EventReminderWorker.KEY_EVENT_DATE, startDateMs)
            .putLong(EventReminderWorker.KEY_MINUTES_BEFORE, minutesBefore)
            .build()

        val delay = triggerAt - System.currentTimeMillis()
        if (delay <= 0) return

        val workRequest = OneTimeWorkRequestBuilder<EventReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag("event:$eventId")
            .setInputData(workData)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "event_reminder_$eventId",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )

        markScheduled(context, eventId)
    }

    fun cancelReminder(context: Context, eventId: String) {
        WorkManager.getInstance(context).cancelUniqueWork("event_reminder_$eventId")
        markCancelled(context, eventId)
    }

    fun isScheduled(context: Context, eventId: String): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SCHEDULED_PREFIX + eventId, false)
    }

    fun wasDelivered(context: Context, eventId: String): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("delivered_$eventId", false)
    }

    fun markDelivered(context: Context, eventId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean("delivered_$eventId", true).apply()
        markCancelled(context, eventId)
    }

    private fun markScheduled(context: Context, eventId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SCHEDULED_PREFIX + eventId, true).apply()
    }

    private fun markCancelled(context: Context, eventId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SCHEDULED_PREFIX + eventId, false).apply()
    }
}
