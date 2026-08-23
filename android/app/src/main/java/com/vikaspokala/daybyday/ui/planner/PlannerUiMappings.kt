package com.vikaspokala.daybyday.ui.planner

import com.vikaspokala.daybyday.data.local.planner.LaterTaskProjection
import com.vikaspokala.daybyday.data.local.planner.ScheduledOccurrence
import com.vikaspokala.daybyday.data.remote.dto.CreateTaskScheduleRequestDto
import com.vikaspokala.daybyday.data.remote.dto.UpdateTaskScheduleDto
import com.vikaspokala.daybyday.ui.models.Recurrence
import com.vikaspokala.daybyday.ui.screens.later.LaterTaskItem
import com.vikaspokala.daybyday.ui.screens.schedule.ScheduleTaskItem
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

fun ScheduledOccurrence.toScheduleTaskItem(): ScheduleTaskItem = ScheduleTaskItem(
    id = taskId,
    scheduleId = scheduleId,
    title = title,
    note = note,
    date = LocalDate.parse(scheduledDate),
    time = scheduledTime.toDisplayTime(),
    reminder = reminderMinutesBefore.toDisplayReminder(),
    recurrence = toRecurrence(),
    isImportant = isImportant,
    isCompleted = isCompleted
)

fun LaterTaskProjection.toLaterTaskItem(): LaterTaskItem = LaterTaskItem(
    id = taskId,
    title = title,
    note = note,
    isImportant = isImportant,
    isCompleted = directCompletionId != null,
    directCompletionId = directCompletionId,
    createdAt = createdAt,
    completedAt = completedAt
)

private val apiTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)

fun buildCreateSchedule(
    date: LocalDate,
    time: String?,
    reminder: String?,
    recurrence: Recurrence?
): CreateTaskScheduleRequestDto {
    val parsedTime = parseDisplayTime(time)
    val formattedTime = parsedTime?.format(apiTimeFormatter)
    return when (recurrence) {
        is Recurrence.IntervalDays -> CreateTaskScheduleRequestDto(
            type = "INTERVAL_DAYS", startDate = date.toString(), endDate = recurrence.endDate?.toString(),
            scheduledTime = formattedTime, intervalDays = recurrence.intervalDays,
            intervalAnchorDate = date.toString(), reminderMinutesBefore = if (parsedTime == null) null else parseReminder(reminder)
        )
        is Recurrence.Weekdays -> CreateTaskScheduleRequestDto(
            type = "WEEKDAYS", startDate = date.toString(), endDate = recurrence.endDate?.toString(),
            scheduledTime = formattedTime, weekdaysMask = recurrence.days.toWeekdaysMask(),
            reminderMinutesBefore = if (parsedTime == null) null else parseReminder(reminder)
        )
        else -> CreateTaskScheduleRequestDto(
            type = "ONCE", startDate = date.toString(), endDate = date.toString(),
            scheduledTime = formattedTime, reminderMinutesBefore = if (parsedTime == null) null else parseReminder(reminder)
        )
    }
}

fun buildUpdateSchedule(date: LocalDate, time: String?, reminder: String?, recurrence: Recurrence?): UpdateTaskScheduleDto {
    val create = buildCreateSchedule(date, time, reminder, recurrence)
    return UpdateTaskScheduleDto(
        type = create.type, startDate = create.startDate, endDate = create.endDate,
        scheduledTime = create.scheduledTime, reminderMinutesBefore = create.reminderMinutesBefore,
        intervalDays = create.intervalDays, weekdaysMask = create.weekdaysMask
    )
}

private fun ScheduledOccurrence.toRecurrence(): Recurrence = when (scheduleType) {
    "INTERVAL_DAYS" -> Recurrence.IntervalDays(intervalDays ?: 1, endDate?.let(LocalDate::parse))
    "WEEKDAYS" -> Recurrence.Weekdays(
        days = (1..7).filterTo(linkedSetOf()) { day -> ((weekdaysMask ?: 0) and (1 shl (day - 1))) != 0 },
        endDate = endDate?.let(LocalDate::parse)
    )
    else -> Recurrence.Once
}

private fun Set<Int>.toWeekdaysMask(): Int = fold(0) { mask, day -> mask or (1 shl (day - 1)) }

fun parseDisplayTime(value: String?): LocalTime? {
    if (value.isNullOrBlank()) return null
    return runCatching { LocalTime.parse(value.trim()) }.getOrElse {
        runCatching { LocalTime.parse(value.trim().uppercase(Locale.US), DateTimeFormatter.ofPattern("h:mm a", Locale.US)) }.getOrNull()
    }
}

private fun String?.toDisplayTime(): String? = parseDisplayTime(this)?.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))

fun parseReminder(value: String?): Int? = when (value?.trim()) {
    "At task time" -> 0
    "5 minutes before" -> 5
    "10 minutes before" -> 10
    "15 minutes before" -> 15
    "30 minutes before" -> 30
    "1 hour before" -> 60
    "1 day before" -> 1440
    "2 days before" -> 2880
    else -> null
}

private fun Int?.toDisplayReminder(): String? = when (this) {
    0 -> "At task time"
    5 -> "5 minutes before"
    10 -> "10 minutes before"
    15 -> "15 minutes before"
    30 -> "30 minutes before"
    60 -> "1 hour before"
    1440 -> "1 day before"
    2880 -> "2 days before"
    else -> null
}
