package com.vikaspokala.daybyday.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.vikaspokala.daybyday.data.local.planner.PlannerDatabase
import com.vikaspokala.daybyday.ui.planner.parseDisplayTime
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

sealed interface ReminderScheduleResult {
    data object Scheduled : ReminderScheduleResult
    data object PastTrigger : ReminderScheduleResult
    data object NotApplicable : ReminderScheduleResult
    data object ExactAlarmPermissionDenied : ReminderScheduleResult
}

interface TaskReminderScheduler {
    fun canScheduleExactAlarms(): Boolean
    fun openExactAlarmSettings(context: Context)
    fun calculateTriggerEpochMillis(
        startDate: String,
        scheduledTime: String,
        reminderMinutesBefore: Int,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long?
    fun scheduleOnceReminder(
        taskId: String,
        title: String,
        startDate: String,
        scheduledTime: String?,
        reminderMinutesBefore: Int?
    ): ReminderScheduleResult
    fun cancelReminder(taskId: String)
    suspend fun cancelAllReminders()
    suspend fun rescheduleAllFromDatabase()
}

open class DefaultTaskReminderScheduler(
    private val context: Context,
    private val database: PlannerDatabase? = null,
    private val currentTimeMillisProvider: () -> Long = { System.currentTimeMillis() },
    private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() }
) : TaskReminderScheduler {

    private val alarmManager: AlarmManager?
        get() = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    override fun canScheduleExactAlarms(): Boolean {
        val manager = alarmManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    override fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (_: Exception) {
                val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(fallback)
                } catch (_: Exception) {
                    // Safe fallback
                }
            }
        }
    }

    override fun calculateTriggerEpochMillis(
        startDate: String,
        scheduledTime: String,
        reminderMinutesBefore: Int,
        zoneId: ZoneId
    ): Long? {
        val date = try {
            LocalDate.parse(startDate.trim())
        } catch (_: DateTimeParseException) {
            return null
        }

        val time = parseDisplayTime(scheduledTime) ?: return null
        val taskDateTime = LocalDateTime.of(date, time)
        val triggerDateTime = taskDateTime.minusMinutes(reminderMinutesBefore.toLong())

        return triggerDateTime.atZone(zoneId).toInstant().toEpochMilli()
    }

    override fun scheduleOnceReminder(
        taskId: String,
        title: String,
        startDate: String,
        scheduledTime: String?,
        reminderMinutesBefore: Int?
    ): ReminderScheduleResult {
        if (scheduledTime.isNullOrBlank() || reminderMinutesBefore == null || reminderMinutesBefore < 0) {
            cancelReminder(taskId)
            return ReminderScheduleResult.NotApplicable
        }

        val triggerMillis = calculateTriggerEpochMillis(
            startDate = startDate,
            scheduledTime = scheduledTime,
            reminderMinutesBefore = reminderMinutesBefore,
            zoneId = zoneIdProvider()
        ) ?: run {
            cancelReminder(taskId)
            return ReminderScheduleResult.NotApplicable
        }

        if (triggerMillis <= currentTimeMillisProvider()) {
            cancelReminder(taskId)
            return ReminderScheduleResult.PastTrigger
        }

        if (!canScheduleExactAlarms()) {
            cancelReminder(taskId)
            return ReminderScheduleResult.ExactAlarmPermissionDenied
        }

        val manager = alarmManager ?: return ReminderScheduleResult.ExactAlarmPermissionDenied
        val pendingIntent = createPendingIntent(taskId, title)

        return try {
            manager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerMillis,
                pendingIntent
            )
            ReminderScheduleResult.Scheduled
        } catch (_: SecurityException) {
            ReminderScheduleResult.ExactAlarmPermissionDenied
        } catch (_: Exception) {
            ReminderScheduleResult.NotApplicable
        }
    }

    override fun cancelReminder(taskId: String) {
        val manager = alarmManager ?: return
        val pendingIntent = createPendingIntent(taskId, "")
        try {
            manager.cancel(pendingIntent)
            pendingIntent.cancel()
        } catch (_: Exception) {
            // Safe fallback
        }
    }

    override suspend fun cancelAllReminders() {
        val db = database ?: return
        try {
            val tasks = db.taskDao().getAll()
            for (task in tasks) {
                cancelReminder(task.id)
            }
        } catch (_: Exception) {
            // Safe fallback
        }
    }

    override suspend fun rescheduleAllFromDatabase() {
        val db = database ?: return
        try {
            val tasks = db.taskDao().getAll().associateBy { it.id }
            val schedules = db.scheduleDao().getAll()
            val completedSchedules = db.completionDao().getAll().mapNotNull { it.scheduleId }.toSet()

            for (schedule in schedules) {
                if (schedule.scheduleType == "ONCE" &&
                    !schedule.scheduledTime.isNullOrBlank() &&
                    schedule.reminderMinutesBefore != null &&
                    schedule.id !in completedSchedules
                ) {
                    val task = tasks[schedule.taskId]
                    if (task != null) {
                        scheduleOnceReminder(
                            taskId = task.id,
                            title = task.title,
                            startDate = schedule.startDate,
                            scheduledTime = schedule.scheduledTime,
                            reminderMinutesBefore = schedule.reminderMinutesBefore
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Safe fallback
        }
    }

    private fun createPendingIntent(taskId: String, title: String): PendingIntent {
        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = ReminderBroadcastReceiver.ACTION_FIRE_REMINDER
            putExtra(ReminderBroadcastReceiver.EXTRA_TASK_ID, taskId)
            putExtra(ReminderBroadcastReceiver.EXTRA_TASK_TITLE, title)
        }
        val requestCode = (taskId.hashCode() and 0x7FFFFFFF)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
