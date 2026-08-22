package com.vikaspokala.daybyday.ui.screens.schedule

import com.vikaspokala.daybyday.ui.FakePlannerDatabase
import com.vikaspokala.daybyday.ui.RecordingPlannerRepository
import com.vikaspokala.daybyday.ui.scheduledOccurrence
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleViewModelTest {
    private val today = LocalDate.of(2026, 8, 20)

    @Test
    fun initialStateUsesPlannerTodayAndItsMonth() {
        val database = FakePlannerDatabase()
        val viewModel = ScheduleViewModel(database, RecordingPlannerRepository(database), { today })

        assertEquals(today, viewModel.selectedDate.value)
        assertEquals(YearMonth.of(2026, 8), viewModel.currentMonth.value)
    }

    @Test
    fun selectedDaySwitchesRoomOccurrenceProjection_andMapsFields() {
        val future = today.plusDays(5)
        val database = FakePlannerDatabase().apply {
            occurrences.value = mapOf(
                today to listOf(scheduledOccurrence("today", today)),
                future to listOf(
                    scheduledOccurrence(
                        "future",
                        future,
                        title = "Future",
                        note = "note",
                        time = "14:05",
                        reminderMinutes = 30,
                        isImportant = true
                    )
                )
            )
        }
        val viewModel = ScheduleViewModel(database, RecordingPlannerRepository(database), { today })

        viewModel.selectDate(future)

        val task = viewModel.tasks.value.single()
        assertEquals("future", task.id)
        assertEquals("schedule-future", task.scheduleId)
        assertEquals("Future", task.title)
        assertEquals("note", task.note)
        assertEquals("2:05 PM", task.time)
        assertEquals("30 minutes before", task.reminder)
        assertTrue(task.isImportant)
        assertEquals(YearMonth.from(future), viewModel.currentMonth.value)
    }

    @Test
    fun importantDotsComeFromBoundedRoomProjection() {
        val important = today.plusDays(2)
        val database = FakePlannerDatabase().apply { importantDates.value = setOf(important) }
        val viewModel = ScheduleViewModel(database, RecordingPlannerRepository(database), { today })

        assertTrue(viewModel.hasImportantTask(important))
        assertFalse(viewModel.hasImportantTask(today))
        assertEquals(
            YearMonth.from(today).atDay(1).minusDays(7) to YearMonth.from(today).atEndOfMonth().plusDays(7),
            database.importantRanges.last()
        )
    }

    @Test
    fun previousAndNextMonthHandleYearBoundaries() {
        val january = LocalDate.of(2027, 1, 15)
        val database = FakePlannerDatabase()
        val viewModel = ScheduleViewModel(database, RecordingPlannerRepository(database), { january })

        viewModel.previousMonth()
        assertEquals(YearMonth.of(2026, 12), viewModel.currentMonth.value)
        viewModel.nextMonth()
        viewModel.nextMonth()
        assertEquals(YearMonth.of(2027, 2), viewModel.currentMonth.value)
    }

    @Test
    fun resetAndSelectTodayUpdateSelectedDateAndMonth() {
        val targetToday = LocalDate.of(2027, 1, 3)
        val database = FakePlannerDatabase()
        val viewModel = ScheduleViewModel(database, RecordingPlannerRepository(database), { today })
        viewModel.selectDate(today.plusMonths(3))

        viewModel.resetToToday(targetToday)

        assertEquals(targetToday, viewModel.selectedDate.value)
        assertEquals(YearMonth.from(targetToday), viewModel.currentMonth.value)
    }

    @Test
    fun incompleteGroupsPrecedeCompleted_andTimedGroupsRemainSorted() {
        val database = FakePlannerDatabase().apply {
            occurrences.value = mapOf(
                today to listOf(
                    scheduledOccurrence("done-anytime", today, isCompleted = true),
                    scheduledOccurrence("active-11", today, time = "11:00"),
                    scheduledOccurrence("done-10", today, time = "10:00", isCompleted = true),
                    scheduledOccurrence("active-anytime", today),
                    scheduledOccurrence("active-08", today, time = "08:00")
                )
            )
        }
        val viewModel = ScheduleViewModel(database, RecordingPlannerRepository(database), { today })

        assertEquals(
            listOf("active-08", "active-11", "active-anytime", "done-10", "done-anytime"),
            viewModel.getTasksForDate(today).map { it.id }
        )
    }

    @Test
    fun completedFutureOnceRemainsDisplayedOnItsScheduledDate() {
        val future = today.plusDays(3)
        val database = FakePlannerDatabase().apply {
            occurrences.value = mapOf(future to listOf(scheduledOccurrence("future", future, isCompleted = true)))
        }
        val viewModel = ScheduleViewModel(database, RecordingPlannerRepository(database), { today })

        viewModel.selectDate(future)

        assertEquals(future, viewModel.tasks.value.single().date)
        assertTrue(viewModel.tasks.value.single().isCompleted)
        assertTrue(database.occurrences.value[today].isNullOrEmpty())
    }
}
