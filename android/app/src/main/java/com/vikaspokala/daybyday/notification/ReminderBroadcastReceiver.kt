package com.vikaspokala.daybyday.notification

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vikaspokala.daybyday.MainActivity
import com.vikaspokala.daybyday.R
import com.vikaspokala.daybyday.data.local.planner.PlannerDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE_REMINDER) {
            return
        }

        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE)?.takeIf { it.isNotBlank() } ?: "Reminder"
        val occurrenceDate = intent.getStringExtra(EXTRA_OCCURRENCE_DATE)
        val offsetMinutes = if (intent.hasExtra(EXTRA_OFFSET_MINUTES)) intent.getIntExtra(EXTRA_OFFSET_MINUTES, -1) else null

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val appNotificationManager = DefaultAppNotificationManager(context)
        if (appNotificationManager.areNotificationsAllowed()) {
        appNotificationManager.createNotificationChannel()

        val notificationId = if (occurrenceDate != null && offsetMinutes != null) {
            ("$taskId:$occurrenceDate:$offsetMinutes".hashCode() and 0x7FFFFFFF)
        } else {
            (taskId.hashCode() and 0x7FFFFFFF)
        }
        val notification = buildReminderNotification(context, taskId, taskTitle, notificationId)
        try {
            notificationManager.notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Ignore if notification permission revoked
        } catch (_: Exception) {
            // Safe fallback
        }

        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = PlannerDatabase.getInstance(context.applicationContext)
                val scheduler = DefaultTaskReminderScheduler(context.applicationContext, db)
                scheduler.scheduleTaskReminder(taskId)
            } catch (_: Exception) {
                // Safe fallback
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE_REMINDER = "com.vikaspokala.daybyday.action.FIRE_REMINDER"
        const val ACTION_OPEN_TODAY = "com.vikaspokala.daybyday.action.OPEN_TODAY"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_OCCURRENCE_DATE = "extra_occurrence_date"
        const val EXTRA_OFFSET_MINUTES = "extra_offset_minutes"

        fun buildReminderNotification(
            context: Context,
            taskId: String,
            taskTitle: String,
            notificationId: Int = (taskId.hashCode() and 0x7FFFFFFF)
        ): Notification {
            val contentIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN_TODAY
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_TASK_ID, taskId)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            return Notification.Builder(context, AppNotificationManager.CHANNEL_REMINDERS_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Day by Day")
                .setContentText(taskTitle)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
        }
    }
}
