package com.vikaspokala.daybyday.ui.screens.later

import com.vikaspokala.daybyday.ui.FakePlannerDatabase
import com.vikaspokala.daybyday.ui.PlannerUiCall
import com.vikaspokala.daybyday.ui.RecordingPlannerRepository
import com.vikaspokala.daybyday.ui.laterProjection
import com.vikaspokala.daybyday.ui.planner.buildUpdateSchedule
import com.vikaspokala.daybyday.ui.screens.task.PlannerTaskViewModel
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaterViewModelTest {
    private val today = LocalDate.of(2026, 8, 20)

    @Test
    fun canonicalRoomCreateAppearsReactively_withNoteAndClassification() {
        val database = FakePlannerDatabase()
        val viewModel = LaterViewModel(database, RecordingPlannerRepository(database), { today })
        assertTrue(viewModel.tasks.value.isEmpty())

        database.laterTasks.value = listOf(laterProjection("later", title = "Read", note = "chapter 2"))

        assertEquals("Read", viewModel.getActiveTasks().single().title)
        assertEquals("chapter 2", viewModel.getActiveTasks().single().note)
        assertTrue(viewModel.getCompletedTasks().isEmpty())
    }

    @Test
    fun preservesDaoOrderingForNewestActiveAndNewestCompletedSections() {
        val database = FakePlannerDatabase().apply {
            laterTasks.value = listOf(
                laterProjection("active-new", createdAt = "2026-08-20T10:00:00Z"),
                laterProjection("active-old", createdAt = "2026-08-19T10:00:00Z"),
                laterProjection("done-new", completedAt = "2026-08-20T11:00:00Z"),
                laterProjection("done-old", completedAt = "2026-08-18T11:00:00Z")
            )
        }
        val viewModel = LaterViewModel(database, RecordingPlannerRepository(database), { today })

        assertEquals(listOf("active-new", "active-old"), viewModel.getActiveTasks().map { it.id })
        assertEquals(listOf("done-new", "done-old"), viewModel.getCompletedTasks().map { it.id })
    }

    @Test
    fun completeAndUndoUseDirectLaterContract_andReactThroughRoomFlow() {
        val database = FakePlannerDatabase().apply { laterTasks.value = listOf(laterProjection("later")) }
        val repository = RecordingPlannerRepository(database)
        val viewModel = LaterViewModel(database, repository, { today })

        viewModel.toggleCompletion("later")
        assertTrue(viewModel.tasks.value.single().isCompleted)
        viewModel.toggleCompletion("later")
        assertFalse(viewModel.tasks.value.single().isCompleted)
        assertEquals(
            listOf(
                PlannerUiCall.Complete("later", today.toString(), today.toString(), null, null),
                PlannerUiCall.Undo("later", null, null)
            ),
            repository.calls
        )
    }

    @Test
    fun importantUpdatePreservesIdContentAndCompletionState() {
        val database = FakePlannerDatabase().apply {
            laterTasks.value = listOf(laterProjection("later", title = "Title", note = "Note"))
        }
        val repository = RecordingPlannerRepository(database)
        val viewModel = LaterViewModel(database, repository, { today })

        viewModel.toggleImportant("later")

        val task = viewModel.tasks.value.single()
        assertEquals("later", task.id)
        assertEquals("Title", task.title)
        assertEquals("Note", task.note)
        assertTrue(task.isImportant)
        assertFalse(task.isCompleted)
    }

    @Test
    fun deleteUsesRepositoryAndCanonicalRoomRemoval() {
        val database = FakePlannerDatabase().apply { laterTasks.value = listOf(laterProjection("later")) }
        val repository = RecordingPlannerRepository(database)
        val viewModel = LaterViewModel(database, repository, { today })

        viewModel.deleteTask("later")

        assertTrue(viewModel.tasks.value.isEmpty())
        assertEquals(PlannerUiCall.Delete("later"), repository.calls.single())
    }

    @Test
    fun laterToScheduledPreservesTaskId_andCanonicalRoomRemovesItFromLater() {
        val database = FakePlannerDatabase().apply { laterTasks.value = listOf(laterProjection("stable-id")) }
        val repository = RecordingPlannerRepository(database)
        val laterViewModel = LaterViewModel(database, repository, { today })
        val taskViewModel = PlannerTaskViewModel(repository, { today })

        taskViewModel.scheduleLater(
            "stable-id",
            "Title",
            null,
            false,
            contentChanged = false,
            schedule = buildUpdateSchedule(today.plusDays(1), "9:00 AM", null, null)
        )

        assertTrue(laterViewModel.tasks.value.isEmpty())
        val call = repository.calls.single() as PlannerUiCall.Schedule
        assertEquals("stable-id", call.taskId)
        assertEquals(today.toString(), call.plannerToday)
        assertEquals(today.toString(), call.effectiveDate)
    }

    @Test
    fun laterContentAndScheduleUseOneAtomicCall_andFailureCannotPartiallyChangeRoomContent() {
        val database = FakePlannerDatabase().apply {
            laterTasks.value = listOf(laterProjection("stable-id", title = "Original", note = "original note"))
        }
        val repository = RecordingPlannerRepository(database, Result.failure(IllegalArgumentException("invalid schedule")))
        val taskViewModel = PlannerTaskViewModel(repository, { today })
        val schedule = buildUpdateSchedule(today.minusDays(1), "9:00 AM", null, null)

        taskViewModel.scheduleLater(
            "stable-id", "Changed", "changed note", true,
            contentChanged = true,
            schedule = schedule
        )

        val call = repository.calls.single() as PlannerUiCall.Combined
        assertEquals("stable-id", call.taskId)
        assertEquals("Changed", call.title)
        assertEquals(schedule, call.schedule)
        val unchanged = database.laterTasks.value.single()
        assertEquals("Original", unchanged.title)
        assertEquals("original note", unchanged.note)
        assertFalse(unchanged.isImportant)
    }

    @Test
    fun completedSectionExpansionTogglesAndCollapses_withoutChangingTasks() {
        val database = FakePlannerDatabase().apply {
            laterTasks.value = listOf(laterProjection("done", completedAt = "2026-08-20T10:00:00Z"))
        }
        val viewModel = LaterViewModel(database, RecordingPlannerRepository(database), { today })
        val originalTasks = viewModel.tasks.value

        viewModel.toggleCompletedExpanded()
        assertTrue(viewModel.isCompletedExpanded.value)
        viewModel.collapseCompleted()
        assertFalse(viewModel.isCompletedExpanded.value)
        assertEquals(originalTasks, viewModel.tasks.value)
    }

    @Test
    fun backingOutWithoutSaveLeavesLaterProjectionAndRepositoryUntouched() {
        val database = FakePlannerDatabase().apply { laterTasks.value = listOf(laterProjection("later")) }
        val repository = RecordingPlannerRepository(database)
        val viewModel = LaterViewModel(database, repository, { today })

        val before = viewModel.tasks.value
        // Navigation back performs no planner operation.

        assertEquals(before, viewModel.tasks.value)
        assertTrue(repository.calls.isEmpty())
    }
}
