package com.vikaspokala.daybyday.ui

import com.vikaspokala.daybyday.ui.models.Recurrence
import com.vikaspokala.daybyday.ui.models.toDisplayString
import com.vikaspokala.daybyday.ui.planner.buildCreateSchedule
import com.vikaspokala.daybyday.ui.planner.buildUpdateSchedule
import com.vikaspokala.daybyday.ui.planner.toScheduleTaskItem
import com.vikaspokala.daybyday.ui.screens.task.PlannerTaskViewModel
import com.vikaspokala.daybyday.ui.screens.task.initialTaskScheduleFormState
import com.vikaspokala.daybyday.ui.screens.task.isTaskSaveBlockedByDate
import com.vikaspokala.daybyday.ui.screens.task.parseTaskDate
import com.vikaspokala.daybyday.ui.screens.task.shouldShowTaskReminderAndRepeat
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskSaveContractTest {
    private val today = LocalDate.of(2026, 8, 20)

    @Test
    fun contentOnlyUsesExactlyUpdateTaskContent() {
        val database = FakePlannerDatabase()
        val repository = RecordingPlannerRepository(database)
        val viewModel = PlannerTaskViewModel(repository, { today })

        viewModel.saveExisting(
            "task", "Updated", "note", true,
            contentChanged = true,
            scheduleChanged = false,
            effectiveDate = today,
            schedule = buildUpdateSchedule(today, null, null, null)
        )

        assertEquals(PlannerUiCall.Content("task", "Updated", "note", true), repository.calls.single())
    }

    @Test
    fun scheduleOnlyUsesExactlyUpdateTaskSchedule() {
        val database = FakePlannerDatabase()
        val repository = RecordingPlannerRepository(database)
        val schedule = buildUpdateSchedule(today.plusDays(1), "10:00 AM", "15 minutes before", null)
        val viewModel = PlannerTaskViewModel(repository, { today })

        viewModel.saveExisting(
            "task", "Same", null, false,
            contentChanged = false,
            scheduleChanged = true,
            effectiveDate = today,
            schedule = schedule
        )

        assertEquals(PlannerUiCall.Schedule("task", today.toString(), today.toString(), schedule), repository.calls.single())
    }

    @Test
    fun contentAndScheduleUseExactlyOneAtomicUpdateTask() {
        val database = FakePlannerDatabase()
        val repository = RecordingPlannerRepository(database)
        val schedule = buildUpdateSchedule(today.plusDays(2), "1:30 PM", "30 minutes before", Recurrence.IntervalDays(3))
        val viewModel = PlannerTaskViewModel(repository, { today })

        viewModel.saveExisting(
            "task", "Both", "new note", true,
            contentChanged = true,
            scheduleChanged = true,
            effectiveDate = today.plusDays(1),
            schedule = schedule
        )

        assertEquals(
            PlannerUiCall.Combined(
                "task", "Both", "new note", true, today.toString(), today.plusDays(1).toString(), schedule
            ),
            repository.calls.single()
        )
    }

    @Test
    fun createLaterHasNoSchedule_andScheduledCreateHasSchedule() {
        val database = FakePlannerDatabase()
        val repository = RecordingPlannerRepository(database)
        val ids = ArrayDeque(
            listOf(
                UUID.fromString("00000000-0000-4000-8000-000000000001"),
                UUID.fromString("00000000-0000-4000-8000-000000000002")
            )
        )
        val viewModel = PlannerTaskViewModel(repository, { today }, uuidProvider = { ids.removeFirst() })
        val schedule = buildCreateSchedule(today, "9:00 AM", "At task time", null)

        viewModel.createLater("Later", "later note", true)
        viewModel.createScheduled("Scheduled", "scheduled note", false, schedule)

        val later = repository.calls[0] as PlannerUiCall.Create
        assertEquals("00000000-0000-4000-8000-000000000001", later.taskId)
        assertEquals("later note", later.note)
        assertNull(later.plannerToday)
        assertNull(later.schedule)
        val scheduled = repository.calls[1] as PlannerUiCall.Create
        assertEquals("00000000-0000-4000-8000-000000000002", scheduled.taskId)
        assertEquals(today.toString(), scheduled.plannerToday)
        assertEquals(schedule, scheduled.schedule)
    }

    @Test
    fun oneLogicalCreateAttemptGeneratesUuidExactlyOnceAndPassesThatIdToRepository() {
        val database = FakePlannerDatabase()
        val repository = RecordingPlannerRepository(database)
        val expected = UUID.fromString("00000000-0000-4000-8000-000000000099")
        var generations = 0
        val viewModel = PlannerTaskViewModel(repository, { today }, uuidProvider = {
            generations += 1
            expected
        })

        viewModel.createScheduled("One attempt", null, false, buildCreateSchedule(today, null, null, null))

        assertEquals(1, generations)
        assertEquals(expected.toString(), (repository.calls.single() as PlannerUiCall.Create).taskId)
    }

    @Test
    fun laterToScheduledPreservesOriginalId_andDeleteWorksRegardlessOfSource() {
        val database = FakePlannerDatabase()
        val repository = RecordingPlannerRepository(database)
        val viewModel = PlannerTaskViewModel(repository, { today })
        val schedule = buildUpdateSchedule(today.plusDays(1), null, null, null)

        viewModel.scheduleLater("original-id", "Later", null, false, false, schedule)
        viewModel.deleteTask("original-id")

        assertEquals("original-id", (repository.calls[0] as PlannerUiCall.Schedule).taskId)
        assertEquals(PlannerUiCall.Delete("original-id"), repository.calls[1])
    }

    @Test
    fun occurrenceMappingRestoresNoteTimeReminderIntervalAndEndDate() {
        val end = today.plusDays(30)
        val item = scheduledOccurrence(
            "task",
            today,
            title = "Water plants",
            note = "Check soil",
            time = "08:05",
            reminderMinutes = 15,
            type = "INTERVAL_DAYS",
            endDate = end,
            intervalDays = 3,
            isImportant = true
        ).toScheduleTaskItem()

        assertEquals("Check soil", item.note)
        assertEquals("8:05 AM", item.time)
        assertEquals("15 minutes before", item.reminder)
        assertEquals(Recurrence.IntervalDays(3, end), item.recurrence)
        assertTrue(item.isImportant)
    }

    @Test
    fun weekdayMappingRoundTripsMaskAndEndDate() {
        val end = today.plusWeeks(4)
        val recurrence = Recurrence.Weekdays(setOf(1, 3, 7), end)
        val create = buildCreateSchedule(today, "6:00 PM", "1 hour before", recurrence)
        val reopened = scheduledOccurrence(
            "task", today,
            type = "WEEKDAYS",
            endDate = end,
            time = create.scheduledTime,
            reminderMinutes = create.reminderMinutesBefore,
            weekdaysMask = create.weekdaysMask
        ).toScheduleTaskItem()

        assertEquals((1 shl 0) or (1 shl 2) or (1 shl 6), create.weekdaysMask)
        assertEquals(recurrence, reopened.recurrence)
        assertEquals("6:00 PM", reopened.time)
        assertEquals("1 hour before", reopened.reminder)
    }

    @Test
    fun clearingTimeClearsIncompatibleReminderInCreateAndUpdateMappings() {
        val create = buildCreateSchedule(today, null, "15 minutes before", null)
        val update = buildUpdateSchedule(today, null, "30 minutes before", null)

        assertNull(create.scheduledTime)
        assertNull(create.reminderMinutesBefore)
        assertNull(update.scheduledTime)
        assertNull(update.reminderMinutesBefore)
    }

    @Test
    fun recurrenceDisplayFormattingCoversOnceIntervalWeekdaysAndEnds() {
        val end = LocalDate.of(2026, 8, 31)
        assertEquals("Does not repeat", Recurrence.Once.toDisplayString(today))
        assertEquals("Every day", Recurrence.IntervalDays(1).toDisplayString(today))
        assertEquals("Every 3 days, until 31 Aug", Recurrence.IntervalDays(3, end).toDisplayString(today))
        assertEquals("Every Thursday", Recurrence.Weekdays(setOf(4)).toDisplayString(today))
        assertEquals("Sun, Mon, Wed", Recurrence.Weekdays(setOf(1, 3, 7)).toDisplayString(today))
    }

    @Test
    fun historicalCreateKeepsTime_butClearsAndHidesReminderAndRepeat() {
        val past = today.minusDays(2)
        val state = initialTaskScheduleFormState(
            isCreateMode = true,
            targetDate = past,
            plannerToday = today,
            time = "10:30 AM",
            reminder = "15 minutes before",
            recurrence = Recurrence.IntervalDays(2)
        )

        assertNull(state.reminder)
        assertNull(state.recurrence)
        assertFalse(shouldShowTaskReminderAndRepeat(true, past, today))
        assertEquals(past, parseTaskDate(past.toString(), today))
        assertEquals("10:30", buildCreateSchedule(past, "10:30 AM", state.reminder, state.recurrence).scheduledTime)
    }

    @Test
    fun todayAndFutureCreateStillExposeReminderAndRepeat() {
        assertTrue(shouldShowTaskReminderAndRepeat(true, today, today))
        assertTrue(shouldShowTaskReminderAndRepeat(true, today.plusDays(1), today))
        assertFalse(
            isTaskSaveBlockedByDate(
                true, today, null, today, false, "15 minutes before", Recurrence.IntervalDays(1)
            )
        )
    }

    @Test
    fun historicalCreateAllowsOnceContentAndOptionalTime_butRejectsReminderOrRecurrence() {
        val past = today.minusDays(1)
        assertFalse(isTaskSaveBlockedByDate(true, past, null, today, false, null, Recurrence.Once))
        assertTrue(isTaskSaveBlockedByDate(true, past, null, today, false, "At task time", Recurrence.Once))
        assertTrue(isTaskSaveBlockedByDate(true, past, null, today, false, null, Recurrence.IntervalDays(1)))
    }

    @Test
    fun existingHistoricalContentEditPreservesSchedule_andReschedulingIntoPastIsBlocked() {
        val past = today.minusDays(3)
        val recurrence = Recurrence.IntervalDays(2, today.plusDays(10))
        val state = initialTaskScheduleFormState(
            isCreateMode = false,
            targetDate = past,
            plannerToday = today,
            time = "9:00 AM",
            reminder = "15 minutes before",
            recurrence = recurrence
        )

        assertEquals("15 minutes before", state.reminder)
        assertEquals(recurrence, state.recurrence)
        assertFalse(isTaskSaveBlockedByDate(false, past, past, today, false, state.reminder, state.recurrence))
        assertTrue(isTaskSaveBlockedByDate(false, past.minusDays(1), past, today, true, null, Recurrence.Once))
        assertTrue(isTaskSaveBlockedByDate(false, past, today, today, true, null, Recurrence.Once))
    }

    @Test
    fun recurrenceEndBeforeStartBlocksSave_whileEqualOrLaterEndIsAllowed() {
        val start = today.plusDays(5)
        assertTrue(
            isTaskSaveBlockedByDate(
                true, start, null, today, false, null, Recurrence.IntervalDays(2, start.minusDays(1))
            )
        )
        assertFalse(
            isTaskSaveBlockedByDate(true, start, null, today, false, null, Recurrence.IntervalDays(2, start))
        )
        assertFalse(
            isTaskSaveBlockedByDate(
                true, start, null, today, false, null, Recurrence.IntervalDays(2, start.plusDays(1))
            )
        )
    }
}
