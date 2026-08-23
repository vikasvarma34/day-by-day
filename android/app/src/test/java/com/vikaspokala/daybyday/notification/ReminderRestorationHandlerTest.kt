package com.vikaspokala.daybyday.notification

import com.vikaspokala.daybyday.data.local.planner.entity.ScheduleEntity
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderRestorationHandlerTest {

    private class RecordingScheduler(
        var exactAlarmsAllowed: Boolean = true
    ) : TaskReminderScheduler {
        var cancelAllCallCount = 0
        var rescheduleAllCallCount = 0

        override fun canScheduleExactAlarms(): Boolean = exactAlarmsAllowed
        override fun openExactAlarmSettings(context: android.content.Context) {}
        override fun calculateTriggerEpochMillis(
            startDate: String,
            scheduledTime: String,
            reminderMinutesBefore: Int,
            zoneId: ZoneId
        ): Long? = null

        override fun scheduleOnceReminder(
            taskId: String,
            title: String,
            startDate: String,
            scheduledTime: String?,
            reminderMinutesBefore: Int?
        ): ReminderScheduleResult = ReminderScheduleResult.Scheduled

        override suspend fun scheduleTaskReminder(taskId: String): ReminderScheduleResult =
            ReminderScheduleResult.Scheduled

        override fun cancelReminder(taskId: String) {}

        override suspend fun cancelAllReminders() {
            cancelAllCallCount++
        }

        override suspend fun rescheduleAllFromDatabase() {
            rescheduleAllCallCount++
        }
    }

    @Test
    fun restoreReminders_whenAuthenticatedAndPermissionAllowed_reschedulesAll() = runTest {
        val scheduler = RecordingScheduler(exactAlarmsAllowed = true)
        val result = ReminderRestorationHandler.restoreRemindersIfAuthenticated(
            tokenProvider = { "valid_jwt_session_token" },
            scheduler = scheduler
        )

        assertTrue(result)
        assertEquals(1, scheduler.rescheduleAllCallCount)
        assertEquals(0, scheduler.cancelAllCallCount)
    }

    @Test
    fun restoreReminders_whenSignedOut_cancelsRemindersAndDoesNotReschedule() = runTest {
        val scheduler = RecordingScheduler(exactAlarmsAllowed = true)
        val result = ReminderRestorationHandler.restoreRemindersIfAuthenticated(
            tokenProvider = { null },
            scheduler = scheduler
        )

        assertFalse(result)
        assertEquals(0, scheduler.rescheduleAllCallCount)
        assertEquals(1, scheduler.cancelAllCallCount)
    }

    @Test
    fun restoreReminders_whenBlankToken_cancelsRemindersAndDoesNotReschedule() = runTest {
        val scheduler = RecordingScheduler(exactAlarmsAllowed = true)
        val result = ReminderRestorationHandler.restoreRemindersIfAuthenticated(
            tokenProvider = { "   " },
            scheduler = scheduler
        )

        assertFalse(result)
        assertEquals(0, scheduler.rescheduleAllCallCount)
        assertEquals(1, scheduler.cancelAllCallCount)
    }

    @Test
    fun restoreReminders_whenExactAlarmPermissionDenied_doesNotReschedule() = runTest {
        val scheduler = RecordingScheduler(exactAlarmsAllowed = false)
        val result = ReminderRestorationHandler.restoreRemindersIfAuthenticated(
            tokenProvider = { "valid_jwt_session_token" },
            scheduler = scheduler
        )

        assertFalse(result)
        assertEquals(0, scheduler.rescheduleAllCallCount)
        assertEquals(0, scheduler.cancelAllCallCount)
    }

    @Test
    fun restoreReminders_whenPermissionLaterGranted_restoresReminders() = runTest {
        val scheduler = RecordingScheduler(exactAlarmsAllowed = false)
        
        // 1. When permission is denied, restoration fails
        val resultDenied = ReminderRestorationHandler.restoreRemindersIfAuthenticated(
            tokenProvider = { "valid_jwt_session_token" },
            scheduler = scheduler
        )
        assertFalse(resultDenied)
        assertEquals(0, scheduler.rescheduleAllCallCount)

        // 2. User enables permission in system settings -> exactAlarmsAllowed becomes true
        scheduler.exactAlarmsAllowed = true
        val resultGranted = ReminderRestorationHandler.restoreRemindersIfAuthenticated(
            tokenProvider = { "valid_jwt_session_token" },
            scheduler = scheduler
        )
        assertTrue(resultGranted)
        assertEquals(1, scheduler.rescheduleAllCallCount)
    }

    @Test
    fun timezoneChange_recalculatesEpochTriggerPreservingPlannerLocalTime() {
        val schedule = ScheduleEntity(
            id = "sched_1",
            taskId = "task_1",
            scheduleType = "ONCE",
            startDate = "2026-08-25",
            endDate = null,
            scheduledTime = "18:00:00", // 6:00 PM local
            intervalDays = null,
            intervalAnchorDate = null,
            weekdaysMask = null,
            reminderMinutesBefore = 0,
            createdAt = "2026-08-23T00:00:00Z",
            updatedAt = "2026-08-23T00:00:00Z"
        )

        // Timezone 1: UTC
        val utcZone = ZoneId.of("UTC")
        val nowInUtc = LocalDateTime.of(2026, 8, 25, 10, 0)
            .atZone(utcZone).toInstant().toEpochMilli()

        val candidateUtc = calculateNextEligibleReminder(
            taskId = "task_1",
            title = "Dinner",
            schedules = listOf(schedule),
            completions = emptyList(),
            nowMillis = nowInUtc,
            zoneId = utcZone
        )
        assertNotNull(candidateUtc)
        val expectedUtcTrigger = LocalDateTime.of(2026, 8, 25, 18, 0)
            .atZone(utcZone).toInstant().toEpochMilli()
        assertEquals(expectedUtcTrigger, candidateUtc!!.triggerEpochMillis)

        // Timezone 2: New York (EDT, UTC-4)
        val nyZone = ZoneId.of("America/New_York")
        val nowInNy = LocalDateTime.of(2026, 8, 25, 10, 0)
            .atZone(nyZone).toInstant().toEpochMilli()

        val candidateNy = calculateNextEligibleReminder(
            taskId = "task_1",
            title = "Dinner",
            schedules = listOf(schedule),
            completions = emptyList(),
            nowMillis = nowInNy,
            zoneId = nyZone
        )
        assertNotNull(candidateNy)
        val expectedNyTrigger = LocalDateTime.of(2026, 8, 25, 18, 0)
            .atZone(nyZone).toInstant().toEpochMilli()
        assertEquals(expectedNyTrigger, candidateNy!!.triggerEpochMillis)

        // The planner-local time is preserved as 18:00:00, but epoch milliseconds differ by 4 hours
        assertEquals(4 * 3600 * 1000L, expectedNyTrigger - expectedUtcTrigger)
    }

    @Test
    fun receiverActionBoundaries_areDistinct() {
        val reminderAction = ReminderBroadcastReceiver.ACTION_FIRE_REMINDER
        val exactAlarmChangedAction = ReminderRestorationReceiver.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED

        // Ensure actions are distinct strings and not confused
        assertEquals("com.vikaspokala.daybyday.action.FIRE_REMINDER", reminderAction)
        assertEquals("android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED", exactAlarmChangedAction)
    }
}
