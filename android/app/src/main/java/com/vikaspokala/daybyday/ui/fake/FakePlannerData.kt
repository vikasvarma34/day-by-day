package com.vikaspokala.daybyday.ui.fake

import java.time.LocalDate

/**
 * ============================================================================
 * TEMPORARY TASK 8 DEMO / FAKE DATA PROVIDER
 * ============================================================================
 * WARNING FOR TASK 9:
 * This file contains hardcoded demo datasets used exclusively for Task 8 Android UI
 * shell development and testing.
 *
 * MUST BE REMOVED OR REPLACED WITH REAL ROOM DATABASE / REPOSITORY IN TASK 9.
 * ============================================================================
 */
object FakePlannerData {

    fun getCanonicalSeed(
        referenceDate: LocalDate = LocalDate.now(),
        zoneId: java.time.ZoneId = java.time.ZoneId.systemDefault()
    ): CanonicalPlannerSeed {
        val today = referenceDate
        val tasks = mutableListOf<TaskEntity>()
        val schedules = mutableListOf<ScheduleEntity>()
        val completions = mutableListOf<CompletionEntity>()
        var taskOrder = 0L

        fun addScheduledTask(
            id: String,
            title: String,
            note: String? = null,
            date: LocalDate,
            time: java.time.LocalTime? = null,
            reminderMinutesBefore: Int? = null,
            recurrence: com.vikaspokala.daybyday.ui.models.Recurrence? = null,
            isImportant: Boolean = false,
            completed: Boolean = false,
            completedDate: LocalDate = date,
            completedTime: java.time.LocalTime = time ?: java.time.LocalTime.of(12, 0)
        ) {
            tasks.add(
                TaskEntity(
                    id = id,
                    title = title,
                    note = note,
                    isImportant = isImportant,
                    createdAtEpochMillis = 1787100000000L + (++taskOrder) * 1000L
                )
            )
            val schedId = "sched_$id"
            val type: ScheduleType
            val intervalDays: Int?
            val intervalAnchorDate: LocalDate?
            val weekdays: Set<Int>?

            when (recurrence) {
                is com.vikaspokala.daybyday.ui.models.Recurrence.IntervalDays -> {
                    type = ScheduleType.INTERVAL_DAYS
                    intervalDays = recurrence.intervalDays
                    intervalAnchorDate = date
                    weekdays = null
                }
                is com.vikaspokala.daybyday.ui.models.Recurrence.Weekdays -> {
                    type = ScheduleType.WEEKDAYS
                    intervalDays = null
                    intervalAnchorDate = null
                    weekdays = recurrence.days
                }
                else -> {
                    type = ScheduleType.ONCE
                    intervalDays = null
                    intervalAnchorDate = null
                    weekdays = null
                }
            }

            schedules.add(
                ScheduleEntity(
                    id = schedId,
                    taskId = id,
                    scheduleType = type,
                    startDate = date,
                    endDate = if (type == ScheduleType.ONCE) date else recurrence?.endDate,
                    scheduledTime = time,
                    reminderMinutesBefore = reminderMinutesBefore,
                    intervalDays = intervalDays,
                    intervalAnchorDate = intervalAnchorDate,
                    weekdays = weekdays
                )
            )
            if (completed) {
                val epochMillis = completedDate.atTime(completedTime).atZone(zoneId).toInstant().toEpochMilli()
                completions.add(
                    CompletionEntity(
                        id = "comp_$id",
                        taskId = id,
                        scheduleId = schedId,
                        scheduledDate = date,
                        completedDate = completedDate,
                        completedAtEpochMillis = epochMillis,
                        titleSnapshot = title,
                        isImportantSnapshot = isImportant
                    )
                )
            }
        }

        fun addLaterTask(
            id: String,
            title: String,
            note: String? = null,
            isImportant: Boolean = false
        ) {
            tasks.add(
                TaskEntity(
                    id = id,
                    title = title,
                    note = note,
                    isImportant = isImportant,
                    createdAtEpochMillis = 1787100000000L + (++taskOrder) * 1000L
                )
            )
        }

        // Historical tasks (-2 days)
        addScheduledTask("d_m2_1", "Prep presentation slides", "Include Q3 metrics", today.minusDays(2), java.time.LocalTime.of(11, 0), null, null, isImportant = true, completed = true, completedDate = today.minusDays(2), completedTime = java.time.LocalTime.of(11, 0))
        addScheduledTask("d_m2_2", "Order office supplies", null, today.minusDays(2), null, null, null, isImportant = false, completed = true, completedDate = today.minusDays(2), completedTime = java.time.LocalTime.of(13, 0))

        // Historical tasks (-1 day)
        addScheduledTask("d_m1_1", "Dentist checkup", "Dr. Smith clinic", today.minusDays(1), java.time.LocalTime.of(9, 30), null, null, isImportant = false, completed = true, completedDate = today.minusDays(1), completedTime = java.time.LocalTime.of(9, 30))
        addScheduledTask("d_m1_2", "Review pull request", "Check auth flow", today.minusDays(1), java.time.LocalTime.of(16, 0), null, null, isImportant = true, completed = false)
        addScheduledTask("d_m1_3", "Water balcony plants", null, today.minusDays(1), null, null, null, isImportant = false, completed = false)

        // Today tasks
        addScheduledTask("s1", "Morning walk", null, today, java.time.LocalTime.of(7, 30), null, null, isImportant = false, completed = false)
        addScheduledTask("s2", "Plan weekly meals", "Check pantry first", today, java.time.LocalTime.of(9, 0), null, null, isImportant = true, completed = false)
        addScheduledTask("s3", "Pick up parcel", "Code 4821", today, java.time.LocalTime.of(15, 0), null, null, isImportant = false, completed = false)
        addScheduledTask("s4", "Call parents", null, today, java.time.LocalTime.of(18, 30), null, null, isImportant = false, completed = false)
        addScheduledTask("s5", "Review insurance", "Compare policies", today, null, null, null, isImportant = true, completed = false)
        addScheduledTask("s6", "Prepare documents", null, today, null, null, null, isImportant = false, completed = false)

        // Tomorrow (+1 day)
        addScheduledTask("d_p1_1", "Team sync meeting", "Discuss Sprint 4 goals", today.plusDays(1), java.time.LocalTime.of(10, 0), null, null, isImportant = true, completed = false)
        addScheduledTask("d_p1_2", "Car wash", null, today.plusDays(1), java.time.LocalTime.of(14, 0), null, null, isImportant = false, completed = false)
        addScheduledTask("d_p1_3", "Renew library books", null, today.plusDays(1), null, null, null, isImportant = false, completed = false)

        // +2 days
        addScheduledTask("d_p2_1", "Doctor appointment", "Bring blood test results", today.plusDays(2), java.time.LocalTime.of(11, 30), null, null, isImportant = true, completed = false)
        addScheduledTask("d_p2_2", "Grocery shopping", "Fruits, milk, bread", today.plusDays(2), java.time.LocalTime.of(17, 0), null, null, isImportant = false, completed = false)
        addScheduledTask("d_p2_3", "Clean kitchen cabinets", null, today.plusDays(2), null, null, null, isImportant = false, completed = false)

        // Nearby dot test tasks
        addScheduledTask("s7", "Car service", null, today.minusDays(12), java.time.LocalTime.of(10, 0), null, null, isImportant = true, completed = false)
        addScheduledTask("s8", "Water plants", null, today.minusDays(6), null, null, null, isImportant = false, completed = false)
        addScheduledTask("s9", "Eye exam", null, today.plusDays(5), java.time.LocalTime.of(11, 0), null, null, isImportant = true, completed = false)
        addScheduledTask("s10", "Tax filing", null, today.plusDays(12), null, null, null, isImportant = true, completed = false)

        // Later tasks
        addLaterTask("l1", "Read Atomic Habits", "A chapter or two when there is time", isImportant = true)
        addLaterTask("l2", "Compare new headphones", "Look at comfort and battery life", isImportant = false)
        addLaterTask("l3", "Watch Interstellar", "Weekend idea", isImportant = false)
        addLaterTask("l4", "Call college friend", "Catch up when free", isImportant = true)
        addLaterTask("l5", "Research weekend trip", "Somewhere quiet", isImportant = false)

        return CanonicalPlannerSeed(tasks = tasks, schedules = schedules, completions = completions)
    }
}
