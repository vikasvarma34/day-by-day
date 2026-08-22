package com.vikaspokala.daybyday.ui

import com.vikaspokala.daybyday.ui.screens.schedule.ScheduleViewModel
import com.vikaspokala.daybyday.ui.screens.today.TodayViewModel
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowCompletionReactivityTest {
    private val plannerToday = LocalDate.of(2026, 8, 20)

    @Test
    fun recurringD1Completion_sendsExactOccurrence_andD2RemainsIncomplete_onSameViewModel() {
        val d1 = plannerToday
        val d2 = plannerToday.plusDays(1)
        val database = FakePlannerDatabase().apply {
            occurrences.value = mapOf(
                d1 to listOf(scheduledOccurrence("recurring", d1, "segment", type = "INTERVAL_DAYS", intervalDays = 1)),
                d2 to listOf(scheduledOccurrence("recurring", d2, "segment", type = "INTERVAL_DAYS", intervalDays = 1))
            )
        }
        val repository = RecordingPlannerRepository(database)
        val viewModel = TodayViewModel(database, repository, { plannerToday })
        val originalViewModel = viewModel

        viewModel.toggleCompletion("recurring", d1)

        assertSame(originalViewModel, viewModel)
        assertTrue(viewModel.tasks.value.single().isCompleted)
        viewModel.selectDate(d2)
        assertFalse(viewModel.tasks.value.single().isCompleted)
        assertEquals(
            PlannerUiCall.Complete("recurring", plannerToday.toString(), d1.toString(), "segment", d1.toString()),
            repository.calls.single()
        )
    }

    @Test
    fun undoRecurringD1_restoresOnlyD1() {
        val d1 = plannerToday
        val d2 = plannerToday.plusDays(1)
        val database = FakePlannerDatabase().apply {
            occurrences.value = mapOf(
                d1 to listOf(scheduledOccurrence("recurring", d1, "segment", type = "INTERVAL_DAYS", intervalDays = 1, isCompleted = true)),
                d2 to listOf(scheduledOccurrence("recurring", d2, "segment", type = "INTERVAL_DAYS", intervalDays = 1, isCompleted = true))
            )
        }
        val repository = RecordingPlannerRepository(database)
        val viewModel = ScheduleViewModel(database, repository, { plannerToday })

        viewModel.toggleCompletion("recurring", d1)

        assertFalse(viewModel.tasks.value.single().isCompleted)
        viewModel.selectDate(d2)
        assertTrue(viewModel.tasks.value.single().isCompleted)
        assertEquals(PlannerUiCall.Undo("recurring", "segment", d1.toString()), repository.calls.single())
    }

    @Test
    fun futureOnceCompletedEarly_usesTodayAsCompletedDate_butKeepsFutureScheduledDate() {
        val future = plannerToday.plusDays(7)
        val database = FakePlannerDatabase().apply {
            occurrences.value = mapOf(future to listOf(scheduledOccurrence("future", future, "future-schedule")))
        }
        val repository = RecordingPlannerRepository(database)
        val viewModel = ScheduleViewModel(database, repository, { plannerToday })
        viewModel.selectDate(future)

        viewModel.toggleCompletion("future", future)

        assertEquals(
            PlannerUiCall.Complete(
                "future",
                plannerToday.toString(),
                plannerToday.toString(),
                "future-schedule",
                future.toString()
            ),
            repository.calls.single()
        )
        assertTrue(viewModel.tasks.value.single().isCompleted)
        assertEquals(future, viewModel.tasks.value.single().date)
        assertTrue(database.occurrences.value[plannerToday].isNullOrEmpty())
    }

    @Test
    fun historicalSelectedDateCompletion_doesNotReplacePlannerTodayWithBrowsedDate() {
        val historical = plannerToday.minusDays(4)
        val database = FakePlannerDatabase().apply {
            occurrences.value = mapOf(historical to listOf(scheduledOccurrence("past", historical, "past-schedule")))
        }
        val repository = RecordingPlannerRepository(database)
        val viewModel = ScheduleViewModel(database, repository, { plannerToday })
        viewModel.selectDate(historical)

        viewModel.toggleCompletion("past", historical)

        assertEquals(
            PlannerUiCall.Complete(
                "past",
                plannerToday.toString(),
                historical.toString(),
                "past-schedule",
                historical.toString()
            ),
            repository.calls.single()
        )
        assertTrue(viewModel.tasks.value.single().isCompleted)
    }

    @Test
    fun todayCompletion_usesPlannerTodayForBothCompletionAndOccurrenceDate() {
        val database = FakePlannerDatabase().apply {
            occurrences.value = mapOf(plannerToday to listOf(scheduledOccurrence("today", plannerToday, "today-schedule")))
        }
        val repository = RecordingPlannerRepository(database)
        val viewModel = TodayViewModel(database, repository, { plannerToday })

        viewModel.toggleCompletion("today")

        assertEquals(
            PlannerUiCall.Complete(
                "today", plannerToday.toString(), plannerToday.toString(), "today-schedule", plannerToday.toString()
            ),
            repository.calls.single()
        )
    }
}
