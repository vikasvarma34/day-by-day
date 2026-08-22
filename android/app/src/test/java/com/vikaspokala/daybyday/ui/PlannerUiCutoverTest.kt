package com.vikaspokala.daybyday.ui

import com.vikaspokala.daybyday.ui.screens.schedule.ScheduleViewModel
import com.vikaspokala.daybyday.ui.planner.buildCreateSchedule
import com.vikaspokala.daybyday.ui.planner.buildUpdateSchedule
import com.vikaspokala.daybyday.ui.screens.task.PlannerTaskViewModel
import com.vikaspokala.daybyday.ui.screens.today.TodayViewModel
import java.io.IOException
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class PlannerUiCutoverTest {
    private val date = LocalDate.of(2026, 8, 20)

    @Test
    fun canonicalCreateFromTodayOrSchedule_isVisibleInBothRoomProjections() {
        val database = FakePlannerDatabase()
        val repository = RecordingPlannerRepository(database)
        val today = TodayViewModel(database, repository, { date })
        val schedule = ScheduleViewModel(database, repository, { date })
        val taskViewModel = PlannerTaskViewModel(
            repository,
            { date },
            uuidProvider = { UUID.fromString("00000000-0000-4000-8000-000000000123") }
        )

        taskViewModel.createScheduled("Created", "canonical note", false, buildCreateSchedule(date, null, null, null))
        // The backend response is canonicalized into Room; both screens consume that one emission.
        database.occurrences.value = mapOf(
            date to listOf(
                scheduledOccurrence(
                    "00000000-0000-4000-8000-000000000123",
                    date,
                    title = "Created",
                    note = "canonical note"
                )
            )
        )

        assertEquals(listOf("00000000-0000-4000-8000-000000000123"), today.tasks.value.map { it.id })
        assertEquals(today.tasks.value, schedule.tasks.value)
        assertEquals("canonical note", schedule.tasks.value.single().note)
        assertTrue(repository.calls.single() is PlannerUiCall.Create)
    }

    @Test
    fun canonicalContentEdit_isSeenByTodayAndSchedule_withoutManualSynchronization() = runBlocking {
        val database = FakePlannerDatabase().apply {
            occurrences.value = mapOf(date to listOf(scheduledOccurrence("task", date, title = "Old")))
        }
        val repository = RecordingPlannerRepository(database)
        val today = TodayViewModel(database, repository, { date })
        val schedule = ScheduleViewModel(database, repository, { date })
        val taskViewModel = PlannerTaskViewModel(repository, { date })

        taskViewModel.saveExisting(
            "task", "Updated", "new note", true,
            contentChanged = true,
            scheduleChanged = false,
            effectiveDate = date,
            schedule = buildUpdateSchedule(date, null, null, null)
        )

        assertEquals("Updated", today.tasks.value.single().title)
        assertEquals("new note", schedule.tasks.value.single().note)
        assertTrue(today.tasks.value.single().isImportant)
        assertTrue(schedule.tasks.value.single().isImportant)
    }

    @Test
    fun canonicalDelete_removesTaskFromTodayAndSchedule() {
        val database = FakePlannerDatabase().apply {
            occurrences.value = mapOf(date to listOf(scheduledOccurrence("task", date)))
        }
        val repository = RecordingPlannerRepository(database)
        val today = TodayViewModel(database, repository, { date })
        val schedule = ScheduleViewModel(database, repository, { date })

        schedule.deleteTask("task")

        assertTrue(today.tasks.value.isEmpty())
        assertTrue(schedule.tasks.value.isEmpty())
        assertEquals(PlannerUiCall.Delete("task"), repository.calls.single())
    }

    @Test
    fun canonicalCompleteAndUndo_updatesBothViewsThroughSameRoomFlow() {
        val database = FakePlannerDatabase().apply {
            occurrences.value = mapOf(date to listOf(scheduledOccurrence("task", date)))
        }
        val repository = RecordingPlannerRepository(database)
        val today = TodayViewModel(database, repository, { date })
        val schedule = ScheduleViewModel(database, repository, { date })

        today.toggleCompletion("task", date)
        assertTrue(today.tasks.value.single().isCompleted)
        assertTrue(schedule.tasks.value.single().isCompleted)

        schedule.toggleCompletion("task", date)
        assertFalse(today.tasks.value.single().isCompleted)
        assertFalse(schedule.tasks.value.single().isCompleted)
        assertEquals(2, repository.calls.size)
    }

    @Test
    fun unauthorizedExpiresSession_whileOtherFailuresRemainVisibleAndDoNotMutateRoom() {
        val database = FakePlannerDatabase().apply {
            occurrences.value = mapOf(date to listOf(scheduledOccurrence("task", date)))
        }
        var expired = false
        val unauthorized = HttpException(Response.error<Unit>(401, "unauthorized".toResponseBody()))
        TodayViewModel(
            database,
            RecordingPlannerRepository(database, Result.failure(unauthorized)),
            { date },
            { expired = true }
        ).toggleImportant("task")
        assertTrue(expired)

        val networkViewModel = TodayViewModel(
            database,
            RecordingPlannerRepository(database, Result.failure(IOException("offline"))),
            { date }
        )
        networkViewModel.toggleImportant("task")
        assertEquals("Unable to save planner change. Please try again.", networkViewModel.actionError.value)
        assertFalse(database.occurrences.value.getValue(date).single().isImportant)
    }
}
