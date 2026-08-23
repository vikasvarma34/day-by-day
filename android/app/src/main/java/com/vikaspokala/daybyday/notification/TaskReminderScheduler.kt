package com.vikaspokala.daybyday.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.vikaspokala.daybyday.data.local.planner.PlannerDatabase
import com.vikaspokala.daybyday.data.local.planner.entity.CompletionEntity
import com.vikaspokala.daybyday.data.local.planner.entity.ScheduleEntity
import com.vikaspokala.daybyday.ui.planner.parseDisplayTime
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

sealed interface ReminderScheduleResult {
    data object Scheduled : ReminderScheduleResult
    data object PastTrigger : ReminderScheduleResult
    data object NotApplicable : ReminderScheduleResult
    data object ExactAlarmPermissionDenied : ReminderScheduleResult
}

data class ReminderCandidate(
    val taskId: String,
    val title: String,
    val triggerEpochMillis: Long,
    val occurrenceDate: LocalDate,
    val scheduleId: String
)

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
    suspend fun scheduleTaskReminder(taskId: String): ReminderScheduleResult
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

    override suspend fun scheduleTaskReminder(taskId: String): ReminderScheduleResult {
        val db = database ?: return ReminderScheduleResult.NotApplicable
        val task = db.taskDao().getById(taskId)
        if (task == null) {
            cancelReminder(taskId)
            return ReminderScheduleResult.NotApplicable
        }

        val schedules = db.scheduleDao().getByTaskId(taskId)
        val completions = db.completionDao().getByTaskId(taskId)
        val candidate = calculateNextEligibleReminder(
            taskId = task.id,
            title = task.title,
            schedules = schedules,
            completions = completions,
            nowMillis = currentTimeMillisProvider(),
            zoneId = zoneIdProvider()
        )

        if (candidate == null) {
            cancelReminder(taskId)
            return ReminderScheduleResult.NotApplicable
        }

        if (!canScheduleExactAlarms()) {
            cancelReminder(taskId)
            return ReminderScheduleResult.ExactAlarmPermissionDenied
        }

        val manager = alarmManager ?: return ReminderScheduleResult.ExactAlarmPermissionDenied
        val pendingIntent = createPendingIntent(taskId, task.title)

        return try {
            manager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                candidate.triggerEpochMillis,
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
            val tasks = db.taskDao().getAll()
            for (task in tasks) {
                scheduleTaskReminder(task.id)
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

fun calculateNextEligibleReminder(
    taskId: String,
    title: String,
    schedules: List<ScheduleEntity>,
    completions: List<CompletionEntity>,
    nowMillis: Long,
    zoneId: ZoneId
): ReminderCandidate? {
    val nowLocalDate = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()

    val completedDatesByScheduleId = mutableMapOf<String, MutableSet<String>>()
    for (comp in completions) {
        val sId = comp.scheduleId
        val sDate = comp.scheduledDate
        if (sId != null && sDate != null) {
            completedDatesByScheduleId.getOrPut(sId) { mutableSetOf() }.add(sDate)
        }
    }

    var bestCandidate: ReminderCandidate? = null

    for (schedule in schedules) {
        val scheduledTimeStr = schedule.scheduledTime
        val reminderMinutes = schedule.reminderMinutesBefore
        if (scheduledTimeStr.isNullOrBlank() || reminderMinutes == null || reminderMinutes < 0) {
            continue
        }

        val localTime = parseDisplayTime(scheduledTimeStr) ?: continue
        val startDate = try {
            LocalDate.parse(schedule.startDate)
        } catch (_: DateTimeParseException) {
            continue
        }

        val endDate = schedule.endDate?.let {
            try {
                LocalDate.parse(it)
            } catch (_: DateTimeParseException) {
                null
            }
        }

        val completedDates = completedDatesByScheduleId[schedule.id].orEmpty()

        when (schedule.scheduleType) {
            "ONCE" -> {
                val occurrenceDate = startDate
                val taskDateTime = LocalDateTime.of(occurrenceDate, localTime)
                val triggerMillis = taskDateTime.minusMinutes(reminderMinutes.toLong())
                    .atZone(zoneId).toInstant().toEpochMilli()

                if (triggerMillis > nowMillis && !completedDates.contains(occurrenceDate.toString())) {
                    if (bestCandidate == null || triggerMillis < bestCandidate.triggerEpochMillis) {
                        bestCandidate = ReminderCandidate(
                            taskId = taskId,
                            title = title,
                            triggerEpochMillis = triggerMillis,
                            occurrenceDate = occurrenceDate,
                            scheduleId = schedule.id
                        )
                    }
                }
            }
            "INTERVAL_DAYS" -> {
                val interval = (schedule.intervalDays ?: 1).coerceAtLeast(1)
                val anchor = schedule.intervalAnchorDate?.let {
                    try {
                        LocalDate.parse(it)
                    } catch (_: DateTimeParseException) {
                        null
                    }
                } ?: startDate

                val searchStart = if (nowLocalDate.isAfter(startDate)) nowLocalDate else startDate

                var current = if (searchStart.isBefore(anchor)) {
                    anchor
                } else {
                    val daysFromAnchor = ChronoUnit.DAYS.between(anchor, searchStart)
                    val rem = daysFromAnchor % interval
                    if (rem == 0L) searchStart else searchStart.plusDays(interval - rem)
                }

                if (current.isBefore(startDate)) {
                    val days = ChronoUnit.DAYS.between(current, startDate)
                    val steps = (days + interval - 1) / interval
                    current = current.plusDays(steps * interval)
                }

                var count = 0
                while ((endDate == null || !current.isAfter(endDate)) && count < 366) {
                    val taskDateTime = LocalDateTime.of(current, localTime)
                    val triggerMillis = taskDateTime.minusMinutes(reminderMinutes.toLong())
                        .atZone(zoneId).toInstant().toEpochMilli()

                    if (triggerMillis > nowMillis) {
                        if (!completedDates.contains(current.toString())) {
                            if (bestCandidate == null || triggerMillis < bestCandidate.triggerEpochMillis) {
                                bestCandidate = ReminderCandidate(
                                    taskId = taskId,
                                    title = title,
                                    triggerEpochMillis = triggerMillis,
                                    occurrenceDate = current,
                                    scheduleId = schedule.id
                                )
                            }
                            break
                        }
                    }
                    current = current.plusDays(interval.toLong())
                    count++
                }
            }
            "WEEKDAYS" -> {
                val mask = schedule.weekdaysMask ?: 0
                if (mask == 0) continue

                val searchStart = if (nowLocalDate.isAfter(startDate)) nowLocalDate else startDate
                var current = searchStart
                var count = 0

                while ((endDate == null || !current.isAfter(endDate)) && count < 366) {
                    val weekday = current.dayOfWeek.value // 1 = Mon .. 7 = Sun
                    val isMatch = (mask and (1 shl (weekday - 1))) != 0

                    if (isMatch) {
                        val taskDateTime = LocalDateTime.of(current, localTime)
                        val triggerMillis = taskDateTime.minusMinutes(reminderMinutes.toLong())
                            .atZone(zoneId).toInstant().toEpochMilli()

                        if (triggerMillis > nowMillis) {
                            if (!completedDates.contains(current.toString())) {
                                if (bestCandidate == null || triggerMillis < bestCandidate.triggerEpochMillis) {
                                    bestCandidate = ReminderCandidate(
                                        taskId = taskId,
                                        title = title,
                                        triggerEpochMillis = triggerMillis,
                                        occurrenceDate = current,
                                        scheduleId = schedule.id
                                    )
                                }
                                break
                            }
                        }
                    }
                    current = current.plusDays(1)
                    count++
                }
            }
        }
    }

    return bestCandidate
}
