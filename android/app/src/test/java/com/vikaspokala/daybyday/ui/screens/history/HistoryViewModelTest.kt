package com.vikaspokala.daybyday.ui.screens.history

import java.time.LocalDate
import java.time.LocalTime
import com.vikaspokala.daybyday.ui.fake.CanonicalPlannerSeed
import com.vikaspokala.daybyday.ui.fake.FakePlannerData
import com.vikaspokala.daybyday.ui.fake.InMemoryPlannerDatabase
import com.vikaspokala.daybyday.ui.models.Recurrence
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HistoryViewModelTest {

    private val referenceDate: LocalDate = LocalDate.of(2026, 8, 20)
    private lateinit var database: InMemoryPlannerDatabase
    private lateinit var viewModel: HistoryViewModel

    @Before
    fun setUp() {
        database = InMemoryPlannerDatabase(referenceDate = referenceDate)
        viewModel = HistoryViewModel(database = database, referenceDate = referenceDate)
    }

    @Test
    fun `1 canonical important historical ONCE completion appears in History`() {
        val pastDate = referenceDate.minusDays(2)
        val taskId = database.createDatedTask(
            title = "Crucial Report",
            date = pastDate,
            recurrence = Recurrence.Once,
            isImportant = true,
            plannerToday = referenceDate
        )
        val scheduleId = database.schedules.value.first { it.taskId == taskId }.id

        database.completeTask(
            taskId = taskId,
            scheduleId = scheduleId,
            scheduledDate = pastDate,
            completedDate = pastDate,
            plannerToday = referenceDate
        )

        val grouped = viewModel.getFilteredGroupedHistory()
        assertTrue("Past completion date should be present in history", grouped.containsKey(pastDate))
        val itemsOnDate = grouped[pastDate].orEmpty()
        assertTrue(itemsOnDate.any { it.id == taskId && it.title == "Crucial Report" })
    }

    @Test
    fun `2 non-important completion does not appear`() {
        val pastDate = referenceDate.minusDays(2)
        val taskId = database.createDatedTask(
            title = "Casual Errand",
            date = pastDate,
            recurrence = Recurrence.Once,
            isImportant = false,
            plannerToday = referenceDate
        )
        val scheduleId = database.schedules.value.first { it.taskId == taskId }.id

        database.completeTask(
            taskId = taskId,
            scheduleId = scheduleId,
            scheduledDate = pastDate,
            completedDate = pastDate,
            plannerToday = referenceDate
        )

        val allHistory = viewModel.tasks.value
        assertFalse("Non-important completion is excluded from History", allHistory.any { it.id == taskId })
        assertFalse("Non-important completion is excluded from History screen", viewModel.getFilteredGroupedHistory().values.flatten().any { it.id == taskId })
    }

    @Test
    fun `3 recurring completion does not appear`() {
        val pastDate = referenceDate.minusDays(2)
        val taskId = database.createDatedTask(
            title = "Daily Important Standup",
            date = pastDate,
            recurrence = Recurrence.IntervalDays(1),
            isImportant = true,
            plannerToday = pastDate
        )
        val scheduleId = database.schedules.value.first { it.taskId == taskId }.id

        database.completeTask(
            taskId = taskId,
            scheduleId = scheduleId,
            scheduledDate = pastDate,
            completedDate = pastDate,
            plannerToday = referenceDate
        )

        val allHistory = viewModel.tasks.value
        assertFalse("Recurring completion must never reach History even if important", allHistory.any { it.id == taskId })
    }

    @Test
    fun `4 completion on plannerToday does not appear`() {
        val today = referenceDate
        val taskId = database.createDatedTask(
            title = "Finished today's important task",
            date = today,
            recurrence = Recurrence.Once,
            isImportant = true,
            plannerToday = today
        )
        val scheduleId = database.schedules.value.first { it.taskId == taskId }.id

        database.completeTask(
            taskId = taskId,
            scheduleId = scheduleId,
            scheduledDate = today,
            completedDate = today,
            plannerToday = today
        )

        val grouped = viewModel.getFilteredGroupedHistory(plannerToday = today)
        assertFalse("Tasks completed today must not appear in history before tomorrow", grouped.containsKey(today))
        assertFalse(grouped.values.flatten().any { it.id == taskId })
    }

    @Test
    fun `5 marking completed eligible task Important updates History reactively`() {
        val pastDate = referenceDate.minusDays(2)
        val taskId = database.createDatedTask(
            title = "Order office supplies",
            date = pastDate,
            isImportant = false,
            plannerToday = referenceDate
        )
        val scheduleId = database.schedules.value.first { it.taskId == taskId }.id

        database.completeTask(
            taskId = taskId,
            scheduleId = scheduleId,
            scheduledDate = pastDate,
            completedDate = pastDate,
            plannerToday = referenceDate
        )

        // Initially hidden
        assertFalse(viewModel.tasks.value.any { it.id == taskId })

        // Mark important
        database.updateTask(taskId = taskId, title = "Order office supplies", isImportant = true, plannerToday = referenceDate)

        // Reactively visible in History
        val visibleAfterStar = viewModel.getFilteredGroupedHistory()
        val item = visibleAfterStar[pastDate]?.find { it.id == taskId }
        assertNotNull("Becomes visible in History under original completedDate", item)
        assertEquals("Order office supplies", item?.title)
        assertEquals(pastDate, item?.completedDate)
    }

    @Test
    fun `6 unmarking removes it reactively`() {
        val pastDate = referenceDate.minusDays(2)
        val taskId = database.createDatedTask(
            title = "Starred Item",
            date = pastDate,
            isImportant = true,
            plannerToday = referenceDate
        )
        val scheduleId = database.schedules.value.first { it.taskId == taskId }.id

        database.completeTask(
            taskId = taskId,
            scheduleId = scheduleId,
            scheduledDate = pastDate,
            completedDate = pastDate,
            plannerToday = referenceDate
        )

        assertTrue(viewModel.tasks.value.any { it.id == taskId })

        // Unmark importance
        database.updateTask(taskId = taskId, title = "Starred Item", isImportant = false, plannerToday = referenceDate)

        assertFalse(viewModel.tasks.value.any { it.id == taskId })
    }

    @Test
    fun `7 title search still filters correctly`() {
        val filteredLower = viewModel.getFilteredGroupedHistory("presentation")
        assertEquals(1, filteredLower.size)
        assertEquals(1, filteredLower.values.first().size)
        assertEquals("Prep presentation slides", filteredLower.values.first().first().title)

        val filteredUpper = viewModel.getFilteredGroupedHistory("PRESENTATION")
        assertEquals(1, filteredUpper.size)
        assertEquals("Prep presentation slides", filteredUpper.values.first().first().title)

        val filteredTrimmed = viewModel.getFilteredGroupedHistory("   presentation   ")
        assertEquals(1, filteredTrimmed.size)
        assertEquals("Prep presentation slides", filteredTrimmed.values.first().first().title)

        val filteredNone = viewModel.getFilteredGroupedHistory("NonExistentTaskQuery")
        assertTrue(filteredNone.isEmpty())
    }

    @Test
    fun `8 titleSnapshot remains visible after live task title changes`() {
        val pastDate = referenceDate.minusDays(2)
        val taskId = database.createDatedTask(
            title = "Original Title at Completion",
            date = pastDate,
            isImportant = true,
            plannerToday = referenceDate
        )
        val scheduleId = database.schedules.value.first { it.taskId == taskId }.id

        database.completeTask(
            taskId = taskId,
            scheduleId = scheduleId,
            scheduledDate = pastDate,
            completedDate = pastDate,
            plannerToday = referenceDate
        )

        // Live task title updated
        database.updateTask(
            taskId = taskId,
            title = "Edited Live Title Afterwards",
            plannerToday = referenceDate
        )

        val historyItem = viewModel.tasks.value.first { it.id == taskId }
        assertEquals("Original Title at Completion", historyItem.title)
    }

    @Test
    fun `9 undo removes History item reactively`() {
        val pastDate = referenceDate.minusDays(2)
        val taskId = database.createDatedTask(
            title = "Task To Undo",
            date = pastDate,
            isImportant = true,
            plannerToday = referenceDate
        )
        val scheduleId = database.schedules.value.first { it.taskId == taskId }.id

        database.completeTask(
            taskId = taskId,
            scheduleId = scheduleId,
            scheduledDate = pastDate,
            completedDate = pastDate,
            plannerToday = referenceDate
        )

        assertTrue(viewModel.tasks.value.any { it.id == taskId })

        // Undo
        database.undoTask(taskId = taskId, scheduleId = scheduleId, scheduledDate = pastDate)

        assertFalse(viewModel.tasks.value.any { it.id == taskId })
    }

    @Test
    fun `10 ViewModel uses the shared database state rather than legacy store`() {
        val sharedDb = InMemoryPlannerDatabase(referenceDate = referenceDate)
        val vm = HistoryViewModel(database = sharedDb, referenceDate = referenceDate)

        val pastDate = referenceDate.minusDays(1)
        val taskId = sharedDb.createDatedTask(
            title = "Shared DB Task",
            date = pastDate,
            isImportant = true,
            plannerToday = referenceDate
        )
        val scheduleId = sharedDb.schedules.value.first { it.taskId == taskId }.id

        sharedDb.completeTask(
            taskId = taskId,
            scheduleId = scheduleId,
            scheduledDate = pastDate,
            completedDate = pastDate,
            plannerToday = referenceDate
        )

        // ViewModel built against sharedDb sees the completion immediately
        assertTrue(vm.tasks.value.any { it.id == taskId && it.title == "Shared DB Task" })
    }

    @Test
    fun `seeded history item Prep presentation slides is visible initially`() {
        val grouped = viewModel.getFilteredGroupedHistory()
        val minus2Tasks = grouped[referenceDate.minusDays(2)].orEmpty()
        assertTrue(minus2Tasks.any { it.title == "Prep presentation slides" })
    }

    @Test
    fun `flow collection works reactively with observeHistoryTasks`() = runBlocking {
        val tasksFromFlow = viewModel.tasks.first()
        assertTrue(tasksFromFlow.any { it.title == "Prep presentation slides" })
    }
}
