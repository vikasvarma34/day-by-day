package com.vikaspokala.daybyday.notification

import com.vikaspokala.daybyday.data.local.planner.PlannerDatabase
import com.vikaspokala.daybyday.data.local.planner.entity.CompletionEntity
import com.vikaspokala.daybyday.data.local.planner.entity.ScheduleEntity
import com.vikaspokala.daybyday.data.local.planner.entity.TaskEntity
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskReminderSchedulerTest {

    private val fixedZone = ZoneId.of("UTC")
    private val baseEpoch = 1755940000000L // arbitrary fixed test epoch

    private class FakeTestReminderScheduler(
        var exactAlarmsAllowed: Boolean = true,
        var nowMillis: Long = 1000L,
        db: PlannerDatabase? = null
    ) : TaskReminderScheduler {

        val scheduledAlarms = mutableMapOf<String, Long>()
        var cancelCallCount = 0
        val cancelledTaskIds = mutableListOf<String>()

        override fun canScheduleExactAlarms(): Boolean = exactAlarmsAllowed

        override fun openExactAlarmSettings(context: android.content.Context) {}

        override fun calculateTriggerEpochMillis(
            startDate: String,
            scheduledTime: String,
            reminderMinutesBefore: Int,
            zoneId: ZoneId
        ): Long? {
            val date = try {
                java.time.LocalDate.parse(startDate.trim())
            } catch (_: Exception) {
                return null
            }
            val time = com.vikaspokala.daybyday.ui.planner.parseDisplayTime(scheduledTime) ?: return null
            val taskDateTime = LocalDateTime.of(date, time)
            val trigger = taskDateTime.minusMinutes(reminderMinutesBefore.toLong())
            return trigger.atZone(zoneId).toInstant().toEpochMilli()
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

            val trigger = calculateTriggerEpochMillis(
                startDate,
                scheduledTime,
                reminderMinutesBefore,
                ZoneId.of("UTC")
            ) ?: run {
                cancelReminder(taskId)
                return ReminderScheduleResult.NotApplicable
            }

            if (trigger <= nowMillis) {
                cancelReminder(taskId)
                return ReminderScheduleResult.PastTrigger
            }

            if (!canScheduleExactAlarms()) {
                cancelReminder(taskId)
                return ReminderScheduleResult.ExactAlarmPermissionDenied
            }

            scheduledAlarms[taskId] = trigger
            return ReminderScheduleResult.Scheduled
        }

        override suspend fun scheduleTaskReminder(taskId: String): ReminderScheduleResult {
            return ReminderScheduleResult.Scheduled
        }

        override fun cancelReminder(taskId: String) {
            cancelCallCount++
            cancelledTaskIds.add(taskId)
            scheduledAlarms.remove(taskId)
        }

        override suspend fun cancelAllReminders() {
            cancelCallCount += scheduledAlarms.size
            cancelledTaskIds.addAll(scheduledAlarms.keys)
            scheduledAlarms.clear()
        }

        override suspend fun rescheduleAllFromDatabase() {
            // Test stub
        }
    }

    @Test
    fun calculateTriggerEpochMillis_offsets() {
        val scheduler = FakeTestReminderScheduler()
        val startDate = "2026-08-25"
        val time = "14:00:00"

        // 0 minutes before (task time: 2026-08-25T14:00:00Z)
        val trigger0 = scheduler.calculateTriggerEpochMillis(startDate, time, 0, fixedZone)
        assertNotNull(trigger0)

        // 5 minutes before (2026-08-25T13:55:00Z -> diff = 5 * 60 * 1000)
        val trigger5 = scheduler.calculateTriggerEpochMillis(startDate, time, 5, fixedZone)
        assertNotNull(trigger5)
        assertEquals(5 * 60 * 1000L, trigger0!! - trigger5!!)

        // 10 minutes before
        val trigger10 = scheduler.calculateTriggerEpochMillis(startDate, time, 10, fixedZone)
        assertEquals(10 * 60 * 1000L, trigger0 - trigger10!!)

        // 15 minutes before
        val trigger15 = scheduler.calculateTriggerEpochMillis(startDate, time, 15, fixedZone)
        assertEquals(15 * 60 * 1000L, trigger0 - trigger15!!)

        // 30 minutes before
        val trigger30 = scheduler.calculateTriggerEpochMillis(startDate, time, 30, fixedZone)
        assertEquals(30 * 60 * 1000L, trigger0 - trigger30!!)

        // 60 minutes (1 hour) before
        val trigger60 = scheduler.calculateTriggerEpochMillis(startDate, time, 60, fixedZone)
        assertEquals(60 * 60 * 1000L, trigger0 - trigger60!!)

        // 1440 minutes (1 day) before
        val trigger1440 = scheduler.calculateTriggerEpochMillis(startDate, time, 1440, fixedZone)
        assertEquals(1440 * 60 * 1000L, trigger0 - trigger1440!!)

        // 2880 minutes (2 days) before
        val trigger2880 = scheduler.calculateTriggerEpochMillis(startDate, time, 2880, fixedZone)
        assertEquals(2880 * 60 * 1000L, trigger0 - trigger2880!!)
    }

    @Test
    fun calculateTriggerEpochMillis_invalidInputs() {
        val scheduler = FakeTestReminderScheduler()
        assertNull(scheduler.calculateTriggerEpochMillis("invalid-date", "14:00:00", 0, fixedZone))
        assertNull(scheduler.calculateTriggerEpochMillis("2026-08-25", "invalid-time", 0, fixedZone))
    }

    @Test
    fun calculateNextEligibleReminder_daily_returnsEarliestFutureOccurrence() {
        val schedule = ScheduleEntity(
            id = "sched_1",
            taskId = "task_1",
            scheduleType = "INTERVAL_DAYS",
            startDate = "2026-08-23",
            endDate = null,
            scheduledTime = "14:00:00",
            intervalDays = 1,
            intervalAnchorDate = "2026-08-23",
            weekdaysMask = null,
            reminderMinutesBefore = 10,
            createdAt = "2026-08-23T00:00:00Z",
            updatedAt = "2026-08-23T00:00:00Z"
        )
        val nowMillis = LocalDateTime.of(2026, 8, 23, 10, 0)
            .atZone(fixedZone).toInstant().toEpochMilli()

        val candidate = calculateNextEligibleReminder(
            taskId = "task_1",
            title = "Daily Standup",
            schedules = listOf(schedule),
            completions = emptyList(),
            nowMillis = nowMillis,
            zoneId = fixedZone
        )

        assertNotNull(candidate)
        assertEquals("2026-08-23", candidate!!.occurrenceDate.toString())
        val expectedTrigger = LocalDateTime.of(2026, 8, 23, 13, 50)
            .atZone(fixedZone).toInstant().toEpochMilli()
        assertEquals(expectedTrigger, candidate.triggerEpochMillis)
    }

    @Test
    fun calculateNextEligibleReminder_daily_pastTriggerToday_advancesToTomorrow() {
        val schedule = ScheduleEntity(
            id = "sched_1",
            taskId = "task_1",
            scheduleType = "INTERVAL_DAYS",
            startDate = "2026-08-23",
            endDate = null,
            scheduledTime = "14:00:00",
            intervalDays = 1,
            intervalAnchorDate = "2026-08-23",
            weekdaysMask = null,
            reminderMinutesBefore = 10,
            createdAt = "2026-08-23T00:00:00Z",
            updatedAt = "2026-08-23T00:00:00Z"
        )
        // Clock is 14:05 on Aug 23 (trigger at 13:50 is already past)
        val nowMillis = LocalDateTime.of(2026, 8, 23, 14, 5)
            .atZone(fixedZone).toInstant().toEpochMilli()

        val candidate = calculateNextEligibleReminder(
            taskId = "task_1",
            title = "Daily Standup",
            schedules = listOf(schedule),
            completions = emptyList(),
            nowMillis = nowMillis,
            zoneId = fixedZone
        )

        assertNotNull(candidate)
        assertEquals("2026-08-24", candidate!!.occurrenceDate.toString())
        val expectedTrigger = LocalDateTime.of(2026, 8, 24, 13, 50)
            .atZone(fixedZone).toInstant().toEpochMilli()
        assertEquals(expectedTrigger, candidate.triggerEpochMillis)
    }

    @Test
    fun calculateNextEligibleReminder_intervalDays_preservesAnchorAcrossMonthBoundaries() {
        val schedule = ScheduleEntity(
            id = "sched_1",
            taskId = "task_1",
            scheduleType = "INTERVAL_DAYS",
            startDate = "2026-08-28",
            endDate = null,
            scheduledTime = "10:00:00",
            intervalDays = 3,
            intervalAnchorDate = "2026-08-28",
            weekdaysMask = null,
            reminderMinutesBefore = 0,
            createdAt = "2026-08-28T00:00:00Z",
            updatedAt = "2026-08-28T00:00:00Z"
        )
        // Occurrences: Aug 28, Aug 31, Sep 3, Sep 6...
        val nowMillis1 = LocalDateTime.of(2026, 8, 29, 0, 0)
            .atZone(fixedZone).toInstant().toEpochMilli()

        val candidate1 = calculateNextEligibleReminder(
            taskId = "task_1",
            title = "Water Plants",
            schedules = listOf(schedule),
            completions = emptyList(),
            nowMillis = nowMillis1,
            zoneId = fixedZone
        )
        assertNotNull(candidate1)
        assertEquals("2026-08-31", candidate1!!.occurrenceDate.toString())

        // When clock is past Aug 31
        val nowMillis2 = LocalDateTime.of(2026, 8, 31, 11, 0)
            .atZone(fixedZone).toInstant().toEpochMilli()

        val candidate2 = calculateNextEligibleReminder(
            taskId = "task_1",
            title = "Water Plants",
            schedules = listOf(schedule),
            completions = emptyList(),
            nowMillis = nowMillis2,
            zoneId = fixedZone
        )
        assertNotNull(candidate2)
        assertEquals("2026-09-03", candidate2!!.occurrenceDate.toString())
    }

    @Test
    fun calculateNextEligibleReminder_weekdays_choosesCorrectNextDate() {
        // 2026-08-24 is Monday. Weekdays mask = Tuesday (bit 1 = 2) and Thursday (bit 3 = 8) -> 10
        val schedule = ScheduleEntity(
            id = "sched_1",
            taskId = "task_1",
            scheduleType = "WEEKDAYS",
            startDate = "2026-08-24",
            endDate = null,
            scheduledTime = "09:00:00",
            intervalDays = null,
            intervalAnchorDate = null,
            weekdaysMask = 10,
            reminderMinutesBefore = 15,
            createdAt = "2026-08-24T00:00:00Z",
            updatedAt = "2026-08-24T00:00:00Z"
        )
        val nowMillis = LocalDateTime.of(2026, 8, 24, 10, 0)
            .atZone(fixedZone).toInstant().toEpochMilli()

        val candidate = calculateNextEligibleReminder(
            taskId = "task_1",
            title = "Team Sync",
            schedules = listOf(schedule),
            completions = emptyList(),
            nowMillis = nowMillis,
            zoneId = fixedZone
        )
        assertNotNull(candidate)
        assertEquals("2026-08-25", candidate!!.occurrenceDate.toString()) // Tuesday
        val expectedTrigger = LocalDateTime.of(2026, 8, 25, 8, 45)
            .atZone(fixedZone).toInstant().toEpochMilli()
        assertEquals(expectedTrigger, candidate.triggerEpochMillis)
    }

    @Test
    fun calculateNextEligibleReminder_endDate_stopsGeneration() {
        val schedule = ScheduleEntity(
            id = "sched_1",
            taskId = "task_1",
            scheduleType = "INTERVAL_DAYS",
            startDate = "2026-08-23",
            endDate = "2026-08-24",
            scheduledTime = "10:00:00",
            intervalDays = 1,
            intervalAnchorDate = "2026-08-23",
            weekdaysMask = null,
            reminderMinutesBefore = 0,
            createdAt = "2026-08-23T00:00:00Z",
            updatedAt = "2026-08-23T00:00:00Z"
        )
        // Clock is past Aug 24 10:00 AM
        val nowMillis = LocalDateTime.of(2026, 8, 24, 11, 0)
            .atZone(fixedZone).toInstant().toEpochMilli()

        val candidate = calculateNextEligibleReminder(
            taskId = "task_1",
            title = "Short Workshop",
            schedules = listOf(schedule),
            completions = emptyList(),
            nowMillis = nowMillis,
            zoneId = fixedZone
        )
        assertNull(candidate)
    }

    @Test
    fun calculateNextEligibleReminder_completedOccurrence_isSkipped() {
        val schedule = ScheduleEntity(
            id = "sched_1",
            taskId = "task_1",
            scheduleType = "INTERVAL_DAYS",
            startDate = "2026-08-23",
            endDate = null,
            scheduledTime = "14:00:00",
            intervalDays = 1,
            intervalAnchorDate = "2026-08-23",
            weekdaysMask = null,
            reminderMinutesBefore = 10,
            createdAt = "2026-08-23T00:00:00Z",
            updatedAt = "2026-08-23T00:00:00Z"
        )
        val completion = CompletionEntity(
            id = "comp_1",
            taskId = "task_1",
            scheduleId = "sched_1",
            scheduledDate = "2026-08-23",
            completedDate = "2026-08-23",
            completedAt = "2026-08-23T08:00:00Z",
            titleSnapshot = "Daily Standup",
            isImportantSnapshot = false
        )
        val nowMillis = LocalDateTime.of(2026, 8, 23, 10, 0)
            .atZone(fixedZone).toInstant().toEpochMilli()

        val candidate = calculateNextEligibleReminder(
            taskId = "task_1",
            title = "Daily Standup",
            schedules = listOf(schedule),
            completions = listOf(completion),
            nowMillis = nowMillis,
            zoneId = fixedZone
        )
        assertNotNull(candidate)
        assertEquals("2026-08-24", candidate!!.occurrenceDate.toString()) // Skipped Aug 23
    }

    @Test
    fun calculateNextEligibleReminder_reminderOffset_crossesIntoPreviousDay() {
        val schedule = ScheduleEntity(
            id = "sched_1",
            taskId = "task_1",
            scheduleType = "ONCE",
            startDate = "2026-08-25",
            endDate = null,
            scheduledTime = "09:00:00",
            intervalDays = null,
            intervalAnchorDate = null,
            weekdaysMask = null,
            reminderMinutesBefore = 1440, // 1 day before
            createdAt = "2026-08-23T00:00:00Z",
            updatedAt = "2026-08-23T00:00:00Z"
        )
        val nowMillis = LocalDateTime.of(2026, 8, 23, 12, 0)
            .atZone(fixedZone).toInstant().toEpochMilli()

        val candidate = calculateNextEligibleReminder(
            taskId = "task_1",
            title = "Flight",
            schedules = listOf(schedule),
            completions = emptyList(),
            nowMillis = nowMillis,
            zoneId = fixedZone
        )
        assertNotNull(candidate)
        assertEquals("2026-08-25", candidate!!.occurrenceDate.toString())
        val expectedTrigger = LocalDateTime.of(2026, 8, 24, 9, 0)
            .atZone(fixedZone).toInstant().toEpochMilli()
        assertEquals(expectedTrigger, candidate.triggerEpochMillis)
    }

    @Test
    fun calculateNextEligibleReminder_futureEditSegments_usesActiveFutureSegment() {
        val oldSegment = ScheduleEntity(
            id = "sched_old",
            taskId = "task_1",
            scheduleType = "INTERVAL_DAYS",
            startDate = "2026-08-01",
            endDate = "2026-08-22",
            scheduledTime = "10:00:00",
            intervalDays = 1,
            intervalAnchorDate = "2026-08-01",
            weekdaysMask = null,
            reminderMinutesBefore = 10,
            createdAt = "2026-08-01T00:00:00Z",
            updatedAt = "2026-08-01T00:00:00Z"
        )
        val newSegment = ScheduleEntity(
            id = "sched_new",
            taskId = "task_1",
            scheduleType = "INTERVAL_DAYS",
            startDate = "2026-08-23",
            endDate = null,
            scheduledTime = "16:00:00",
            intervalDays = 1,
            intervalAnchorDate = "2026-08-23",
            weekdaysMask = null,
            reminderMinutesBefore = 30,
            createdAt = "2026-08-23T00:00:00Z",
            updatedAt = "2026-08-23T00:00:00Z"
        )
        val nowMillis = LocalDateTime.of(2026, 8, 23, 11, 0)
            .atZone(fixedZone).toInstant().toEpochMilli()

        val candidate = calculateNextEligibleReminder(
            taskId = "task_1",
            title = "Daily Catchup",
            schedules = listOf(oldSegment, newSegment),
            completions = emptyList(),
            nowMillis = nowMillis,
            zoneId = fixedZone
        )
        assertNotNull(candidate)
        assertEquals("sched_new", candidate!!.scheduleId)
        assertEquals("2026-08-23", candidate.occurrenceDate.toString())
        val expectedTrigger = LocalDateTime.of(2026, 8, 23, 15, 30)
            .atZone(fixedZone).toInstant().toEpochMilli()
        assertEquals(expectedTrigger, candidate.triggerEpochMillis)
    }

    @Test
    fun scheduleOnceReminder_futureTrigger_succeeds() {
        val scheduler = FakeTestReminderScheduler(
            exactAlarmsAllowed = true,
            nowMillis = 1000L
        )
        val result = scheduler.scheduleOnceReminder(
            taskId = "task_1",
            title = "Doctor Appointment",
            startDate = "2026-08-25",
            scheduledTime = "14:00:00",
            reminderMinutesBefore = 15
        )
        assertEquals(ReminderScheduleResult.Scheduled, result)
        assertTrue(scheduler.scheduledAlarms.containsKey("task_1"))
    }

    @Test
    fun scheduleOnceReminder_pastTrigger_returnsPastTriggerAndCancels() {
        // Set clock far in the future
        val scheduler = FakeTestReminderScheduler(
            exactAlarmsAllowed = true,
            nowMillis = 2000000000000L
        )
        val result = scheduler.scheduleOnceReminder(
            taskId = "task_1",
            title = "Doctor Appointment",
            startDate = "2026-08-25",
            scheduledTime = "14:00:00",
            reminderMinutesBefore = 15
        )
        assertEquals(ReminderScheduleResult.PastTrigger, result)
        assertTrue(scheduler.cancelledTaskIds.contains("task_1"))
    }

    @Test
    fun scheduleOnceReminder_permissionDenied_returnsExactAlarmPermissionDenied() {
        val scheduler = FakeTestReminderScheduler(
            exactAlarmsAllowed = false,
            nowMillis = 1000L
        )
        val result = scheduler.scheduleOnceReminder(
            taskId = "task_1",
            title = "Doctor Appointment",
            startDate = "2026-08-25",
            scheduledTime = "14:00:00",
            reminderMinutesBefore = 15
        )
        assertEquals(ReminderScheduleResult.ExactAlarmPermissionDenied, result)
        assertTrue(scheduler.cancelledTaskIds.contains("task_1"))
    }

    @Test
    fun scheduleOnceReminder_noReminderOrUntimed_returnsNotApplicable() {
        val scheduler = FakeTestReminderScheduler(nowMillis = 1000L)

        // Untimed
        val result1 = scheduler.scheduleOnceReminder(
            taskId = "task_1",
            title = "Test",
            startDate = "2026-08-25",
            scheduledTime = null,
            reminderMinutesBefore = 15
        )
        assertEquals(ReminderScheduleResult.NotApplicable, result1)

        // No reminder
        val result2 = scheduler.scheduleOnceReminder(
            taskId = "task_2",
            title = "Test",
            startDate = "2026-08-25",
            scheduledTime = "14:00:00",
            reminderMinutesBefore = null
        )
        assertEquals(ReminderScheduleResult.NotApplicable, result2)
    }

    @Test
    fun cancelReminder_removesAlarm() {
        val scheduler = FakeTestReminderScheduler(nowMillis = 1000L)
        scheduler.scheduleOnceReminder("task_1", "Test", "2026-08-25", "14:00:00", 15)
        assertTrue(scheduler.scheduledAlarms.containsKey("task_1"))

        scheduler.cancelReminder("task_1")
        assertTrue(!scheduler.scheduledAlarms.containsKey("task_1"))
        assertTrue(scheduler.cancelledTaskIds.contains("task_1"))
    }

    @Test
    fun cancelAllReminders_clearsAll() = runTest {
        val scheduler = FakeTestReminderScheduler(nowMillis = 1000L)
        scheduler.scheduleOnceReminder("task_1", "Test 1", "2026-08-25", "14:00:00", 15)
        scheduler.scheduleOnceReminder("task_2", "Test 2", "2026-08-25", "15:00:00", 15)

        assertEquals(2, scheduler.scheduledAlarms.size)
        scheduler.cancelAllReminders()
        assertEquals(0, scheduler.scheduledAlarms.size)
    }

    @Test
    fun lifecycle_completingOneOccurrence_advancesToNextFutureOccurrence() {
        val schedule = ScheduleEntity(
            id = "sched_daily",
            taskId = "task_daily",
            scheduleType = "INTERVAL_DAYS",
            startDate = "2026-08-23",
            endDate = null,
            scheduledTime = "09:00:00",
            intervalDays = 1,
            intervalAnchorDate = "2026-08-23",
            weekdaysMask = null,
            reminderMinutesBefore = 15,
            createdAt = "2026-08-23T00:00:00Z",
            updatedAt = "2026-08-23T00:00:00Z"
        )
        val nowMillis = LocalDateTime.of(2026, 8, 23, 7, 0)
            .atZone(fixedZone).toInstant().toEpochMilli()

        // 1. Initial state: uncompleted, candidate is Aug 23
        val initialCandidate = calculateNextEligibleReminder(
            taskId = "task_daily",
            title = "Standup",
            schedules = listOf(schedule),
            completions = emptyList(),
            nowMillis = nowMillis,
            zoneId = fixedZone
        )
        assertNotNull(initialCandidate)
        assertEquals("2026-08-23", initialCandidate!!.occurrenceDate.toString())

        // 2. Complete Aug 23: candidate advances to Aug 24
        val comp = CompletionEntity(
            id = "comp_1",
            taskId = "task_daily",
            scheduleId = "sched_daily",
            scheduledDate = "2026-08-23",
            completedDate = "2026-08-23",
            completedAt = "2026-08-23T07:30:00Z",
            titleSnapshot = "Standup",
            isImportantSnapshot = false
        )
        val afterCompletionCandidate = calculateNextEligibleReminder(
            taskId = "task_daily",
            title = "Standup",
            schedules = listOf(schedule),
            completions = listOf(comp),
            nowMillis = nowMillis,
            zoneId = fixedZone
        )
        assertNotNull(afterCompletionCandidate)
        assertEquals("2026-08-24", afterCompletionCandidate!!.occurrenceDate.toString())
        val expectedNextTrigger = LocalDateTime.of(2026, 8, 24, 8, 45)
            .atZone(fixedZone).toInstant().toEpochMilli()
        assertEquals(expectedNextTrigger, afterCompletionCandidate.triggerEpochMillis)
    }

    @Test
    fun lifecycle_stopRecurrence_leavesNoInvalidFutureAlarm() {
        // Stopped recurrence sets endDate = 2026-08-23
        val schedule = ScheduleEntity(
            id = "sched_daily",
            taskId = "task_daily",
            scheduleType = "INTERVAL_DAYS",
            startDate = "2026-08-20",
            endDate = "2026-08-23",
            scheduledTime = "09:00:00",
            intervalDays = 1,
            intervalAnchorDate = "2026-08-20",
            weekdaysMask = null,
            reminderMinutesBefore = 15,
            createdAt = "2026-08-20T00:00:00Z",
            updatedAt = "2026-08-23T00:00:00Z"
        )
        // Clock is past Aug 23 trigger (e.g. 2026-08-23 10:00 AM)
        val nowMillis = LocalDateTime.of(2026, 8, 23, 10, 0)
            .atZone(fixedZone).toInstant().toEpochMilli()

        val candidate = calculateNextEligibleReminder(
            taskId = "task_daily",
            title = "Standup",
            schedules = listOf(schedule),
            completions = emptyList(),
            nowMillis = nowMillis,
            zoneId = fixedZone
        )
        assertNull(candidate)
    }

    @Test
    fun lifecycle_editRecurrence_replacesStaleAlarm() {
        val oldSchedule = ScheduleEntity(
            id = "sched_1",
            taskId = "task_1",
            scheduleType = "INTERVAL_DAYS",
            startDate = "2026-08-23",
            endDate = null,
            scheduledTime = "09:00:00",
            intervalDays = 1,
            intervalAnchorDate = "2026-08-23",
            weekdaysMask = null,
            reminderMinutesBefore = 10,
            createdAt = "2026-08-23T00:00:00Z",
            updatedAt = "2026-08-23T00:00:00Z"
        )
        val newSchedule = ScheduleEntity(
            id = "sched_1",
            taskId = "task_1",
            scheduleType = "INTERVAL_DAYS",
            startDate = "2026-08-23",
            endDate = null,
            scheduledTime = "15:00:00", // edited time to 15:00
            intervalDays = 1,
            intervalAnchorDate = "2026-08-23",
            weekdaysMask = null,
            reminderMinutesBefore = 20, // edited reminder to 20m before
            createdAt = "2026-08-23T00:00:00Z",
            updatedAt = "2026-08-23T00:00:00Z"
        )
        val nowMillis = LocalDateTime.of(2026, 8, 23, 10, 0)
            .atZone(fixedZone).toInstant().toEpochMilli()

        val candidate = calculateNextEligibleReminder(
            taskId = "task_1",
            title = "Task",
            schedules = listOf(newSchedule),
            completions = emptyList(),
            nowMillis = nowMillis,
            zoneId = fixedZone
        )
        assertNotNull(candidate)
        assertEquals("2026-08-23", candidate!!.occurrenceDate.toString())
        val expectedTrigger = LocalDateTime.of(2026, 8, 23, 14, 40)
            .atZone(fixedZone).toInstant().toEpochMilli()
        assertEquals(expectedTrigger, candidate.triggerEpochMillis)
    }
}
