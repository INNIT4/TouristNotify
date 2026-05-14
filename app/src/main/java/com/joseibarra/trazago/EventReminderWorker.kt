package com.joseibarra.trazago

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.text.SimpleDateFormat
import java.util.Locale

class EventReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    companion object {
        const val KEY_EVENT_ID = "event_id"
        const val KEY_EVENT_TITLE = "event_title"
        const val KEY_EVENT_DATE = "event_date"
        const val KEY_MINUTES_BEFORE = "minutes_before"
        const val CHANNEL_ID = "event_reminders"
        private const val NOTIFICATION_ID_BASE = 3000
    }

    override fun doWork(): Result {
        val eventId = inputData.getString(KEY_EVENT_ID) ?: return Result.failure()
        val title = inputData.getString(KEY_EVENT_TITLE) ?: return Result.failure()
        val eventDate = inputData.getLong(KEY_EVENT_DATE, 0L)
        val minutesBefore = inputData.getLong(KEY_MINUTES_BEFORE, 60L)

        createNotificationChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    applicationContext, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) return Result.failure()
        }

        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(eventDate))
        val body = applicationContext.getString(
            R.string.event_reminder_body, title, timeStr
        )

        val intent = Intent(applicationContext, EventDetailsActivity::class.java).apply {
            putExtra(EventDetailsActivity.EXTRA_EVENT_ID, eventId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, eventId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_travel)
            .setContentTitle(applicationContext.getString(R.string.event_reminder_title))
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(applicationContext)
            .notify(NOTIFICATION_ID_BASE + eventId.hashCode(), notification)

        EventReminderManager.markDelivered(applicationContext, eventId)
        return Result.success()
    }

    private fun createNotificationChannel() {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (manager?.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.event_reminder_title),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Recordatorios de eventos"
            }
            manager.createNotificationChannel(channel)
        }
    }
}
