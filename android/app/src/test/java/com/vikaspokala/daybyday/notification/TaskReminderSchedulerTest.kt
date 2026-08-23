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
}
