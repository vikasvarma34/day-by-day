package com.vikaspokala.daybyday.data.local.planner

import com.vikaspokala.daybyday.data.local.planner.dao.TaskWithScheduleRow
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

data class ScheduledOccurrence(
    val taskId: String,
    val scheduleId: String,
    val title: String,
    val note: String?,
    val isImportant: Boolean,
    
    val scheduleType: String,
    val startDate: String,
    val endDate: String?,
    val scheduledTime: String?,
    val intervalDays: Int?,
    val intervalAnchorDate: String?,
    val weekdaysMask: Int?,
    val reminderMinutesBefore: Int?,
    
    val scheduledDate: String,
    val isCompleted: Boolean
)

fun isScheduleOccurringOnDate(row: TaskWithScheduleRow, date: LocalDate): Boolean {
    val startDate = try {
        LocalDate.parse(row.startDate)
    } catch (e: DateTimeParseException) {
        return false
    }

    if (date.isBefore(startDate)) return false

    val endDate = row.endDate?.let {
        try {
            LocalDate.parse(it)
        } catch (e: DateTimeParseException) {
            null
        }
    }
    if (endDate != null && date.isAfter(endDate)) return false

    return when (row.scheduleType) {
        "ONCE" -> date == startDate
        "INTERVAL_DAYS" -> {
            val anchor = row.intervalAnchorDate?.let {
                try {
                    LocalDate.parse(it)
                } catch (e: DateTimeParseException) {
                    null
                }
            } ?: startDate
            if (date.isBefore(anchor)) return false
            val interval = row.intervalDays ?: 1
            val daysDiff = ChronoUnit.DAYS.between(anchor, date)
            (daysDiff % interval) == 0L
        }
        "WEEKDAYS" -> {
            val weekday = date.dayOfWeek.value // 1 = Monday .. 7 = Sunday
            val mask = row.weekdaysMask ?: 0
            (mask and (1 shl (weekday - 1))) != 0
        }
        else -> false
    }
}
