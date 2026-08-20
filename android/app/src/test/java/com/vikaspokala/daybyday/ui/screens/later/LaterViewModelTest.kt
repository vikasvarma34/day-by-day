package com.vikaspokala.daybyday.ui.screens.later

import java.time.LocalDate
import java.time.LocalTime
import com.vikaspokala.daybyday.ui.fake.CanonicalPlannerSeed
import com.vikaspokala.daybyday.ui.fake.InMemoryPlannerDatabase
import com.vikaspokala.daybyday.ui.models.Recurrence
import com.vikaspokala.daybyday.ui.screens.history.HistoryViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class LaterViewModelTest {

    private val fixedToday: LocalDate = LocalDate.of(2026, 8, 20)
    private lateinit var database: InMemoryPlannerDatabase
    private lateinit var viewModel: LaterViewModel
    private lateinit var historyViewModel: HistoryViewModel

    @Before
    fun setUp() {
        database = InMemoryPlannerDatabase(referenceDate = fixedToday)
        viewModel = LaterViewModel(database = database, plannerTodayProvider = { fixedToday })
        historyViewModel = HistoryViewModel(database = database, referenceDate = fixedToday)
    }

    @Test
    fun `1 canonical Later seed appears with 5 initial items`() {
        val tasks = viewModel.tasks.value
        assertEquals(5, tasks.size)
        assertEquals(5, viewModel.getActiveTasks().size)
        assertEquals(0, viewModel.getCompletedTasks().size)
    }

    @Test
    fun `2 create Later appears reactively`() {
        val taskId = viewModel.addTask(title = "Read Clean Architecture", note = "Chapters 1-3", isImportant = true)
        val activeTasks = viewModel.getActiveTasks()
        assertEquals(6, activeTasks.size)
        val created = activeTasks.find { it.id == taskId }
        assertNotNull(created)
        assertEquals("Read Clean Architecture", created?.title)
        assertEquals("Chapters 1-3", created?.note)
        assertTrue(created?.isImportant == true)
        assertFalse(created?.isCompleted == true)
    }

    @Test
    fun `3 edit preserves task ID`() {
        val taskId = viewModel.addTask(title = "Initial Title", note = "Initial Note", isImportant = false)
        viewModel.updateTask(id = taskId, title = "Updated Title", note = "Updated Note", isImportant = true)

        val updated = viewModel.tasks.value.first { it.id == taskId }
        assertEquals("Updated Title", updated.title)
        assertEquals("Updated Note", updated.note)
        assertTrue(updated.isImportant)
        assertEquals(taskId, updated.id)
    }

    @Test
    fun `4 toggle Important updates canonical task`() {
        val taskId = viewModel.addTask(title = "Star Task", note = null, isImportant = false)
        assertFalse(viewModel.tasks.value.first { it.id == taskId }.isImportant)

        viewModel.toggleImportant(taskId)
        assertTrue(viewModel.tasks.value.first { it.id == taskId }.isImportant)
        assertTrue(database.tasks.value.first { it.id == taskId }.isImportant)

        viewModel.toggleImportant(taskId)
        assertFalse(viewModel.tasks.value.first { it.id == taskId }.isImportant)
        assertFalse(database.tasks.value.first { it.id == taskId }.isImportant)
    }

    @Test
    fun `5 complete sets Later isCompleted`() {
        val taskId = viewModel.addTask(title = "Task To Complete", note = null, isImportant = false)
        assertEquals(6, viewModel.getActiveTasks().size)
        assertEquals(0, viewModel.getCompletedTasks().size)

        viewModel.toggleCompletion(taskId, completionDate = fixedToday)

        assertEquals(5, viewModel.getActiveTasks().size)
        assertEquals(1, viewModel.getCompletedTasks().size)
        val completedTask = viewModel.getCompletedTasks().first()
        assertEquals(taskId, completedTask.id)
        assertTrue(completedTask.isCompleted)
    }

    @Test
    fun `6 undo clears Later isCompleted`() {
        val taskId = viewModel.addTask(title = "Task To Toggle", note = null, isImportant = false)

        viewModel.toggleCompletion(taskId, completionDate = fixedToday)
        assertEquals(1, viewModel.getCompletedTasks().size)

        viewModel.toggleCompletion(taskId, completionDate = fixedToday)
        assertEquals(0, viewModel.getCompletedTasks().size)
        assertEquals(6, viewModel.getActiveTasks().size)
        assertFalse(viewModel.tasks.value.first { it.id == taskId }.isCompleted)
    }

    @Test
    fun `7 completed Important Later becomes History eligible on subsequent days`() {
        val taskId = viewModel.addTask(title = "Important Later", note = null, isImportant = true)

        viewModel.toggleCompletion(taskId, completionDate = fixedToday)

        // Today: visible in completed Later, but not in history yet
        assertEquals(1, viewModel.getCompletedTasks().size)
        assertFalse(historyViewModel.getFilteredGroupedHistory().containsKey(fixedToday))

        // Subsequent day (tomorrow): History screen created on tomorrow sees the item!
        val historyViewModelTomorrow = HistoryViewModel(database = database, referenceDate = fixedToday.plusDays(1))
        val historyTomorrow = historyViewModelTomorrow.getFilteredGroupedHistory()
        assertTrue(historyTomorrow.containsKey(fixedToday))
        val item = historyTomorrow[fixedToday]?.find { it.id == taskId }
        assertNotNull(item)
        assertEquals("Important Later", item?.title)
    }

    @Test
    fun `8 completed non-important Later becomes History eligible after star`() {
        val taskId = viewModel.addTask(title = "Unimportant Later", note = null, isImportant = false)

        viewModel.toggleCompletion(taskId, completionDate = fixedToday)
        val historyViewModelTomorrow = HistoryViewModel(database = database, referenceDate = fixedToday.plusDays(1))
        assertFalse(historyViewModelTomorrow.getFilteredGroupedHistory().values.flatten().any { it.id == taskId })

        // Star it later
        viewModel.toggleImportant(taskId)

        val historyTomorrow = historyViewModelTomorrow.getFilteredGroupedHistory()
        assertTrue(historyTomorrow.values.flatten().any { it.id == taskId && it.title == "Unimportant Later" })
    }

    @Test
    fun `9 unstar hides it from History`() {
        val taskId = viewModel.addTask(title = "Starred Later", note = null, isImportant = true)

        viewModel.toggleCompletion(taskId, completionDate = fixedToday)
        val historyViewModelTomorrow = HistoryViewModel(database = database, referenceDate = fixedToday.plusDays(1))
        assertTrue(historyViewModelTomorrow.getFilteredGroupedHistory().values.flatten().any { it.id == taskId })

        // Unstar
        viewModel.toggleImportant(taskId)
        assertFalse(historyViewModelTomorrow.getFilteredGroupedHistory().values.flatten().any { it.id == taskId })
    }

    @Test
    fun `10 delete removes canonical task`() {
        val taskId = viewModel.addTask(title = "To Delete", note = null, isImportant = false)
        assertTrue(viewModel.tasks.value.any { it.id == taskId })
        assertTrue(database.tasks.value.any { it.id == taskId })

        viewModel.deleteTask(taskId)

        assertFalse(viewModel.tasks.value.any { it.id == taskId })
        assertFalse(database.tasks.value.any { it.id == taskId })
    }

    @Test
    fun `11 Later to Schedule preserves same task ID`() {
        val taskId = viewModel.addTask(title = "Move To Schedule", note = "Keep this ID", isImportant = true)

        viewModel.scheduleTask(
            taskId = taskId,
            date = fixedToday.plusDays(2),
            time = "10:30 AM",
            reminder = "15 minutes before"
        )

        // Same task ID in database tasks
        val dbTask = database.tasks.value.find { it.id == taskId }
        assertNotNull(dbTask)
        assertEquals("Move To Schedule", dbTask?.title)

        // Schedule references same task ID
        val schedule = database.schedules.value.find { it.taskId == taskId }
        assertNotNull(schedule)
        assertEquals(fixedToday.plusDays(2), schedule?.startDate)
        assertEquals(LocalTime.of(10, 30), schedule?.scheduledTime)
        assertEquals(15, schedule?.reminderMinutesBefore)
    }

    @Test
    fun `12 Later to Schedule removes it from Later projection`() {
        val taskId = viewModel.addTask(title = "Scheduled Out", note = null, isImportant = false)
        assertTrue(viewModel.tasks.value.any { it.id == taskId })

        viewModel.scheduleTask(
            taskId = taskId,
            date = fixedToday.plusDays(1)
        )

        // Reactively disappears from Later tasks
        assertFalse(viewModel.tasks.value.any { it.id == taskId })
        // Appears in dated projection for tomorrow
        val datedItems = database.getTasksForDate(fixedToday.plusDays(1))
        assertTrue(datedItems.any { it.id == taskId && it.title == "Scheduled Out" })
    }

    @Test
    fun `13 Later to past Schedule is rejected`() {
        val taskId = viewModel.addTask(title = "Past Move", note = null, isImportant = false)

        try {
            viewModel.scheduleTask(
                taskId = taskId,
                date = fixedToday.minusDays(1),
                plannerToday = fixedToday
            )
            fail("Expected IllegalArgumentException when scheduling to past date")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("historical") == true)
        }

        // Task remains in Later
        assertTrue(viewModel.tasks.value.any { it.id == taskId })
    }

    @Test
    fun `14 ViewModel uses shared database state rather than private collection`() {
        val sharedDb = InMemoryPlannerDatabase(referenceDate = fixedToday)
        val vm1 = LaterViewModel(database = sharedDb, plannerTodayProvider = { fixedToday })
        val vm2 = LaterViewModel(database = sharedDb, plannerTodayProvider = { fixedToday })

        val newId = vm1.addTask(title = "Cross VM Task", note = null, isImportant = true)
        // vm2 sees the same task immediately through sharedDb
        assertTrue(vm2.tasks.value.any { it.id == newId && it.title == "Cross VM Task" })
    }

    @Test
    fun `15 no duplicate task is created during scheduling`() {
        val initialTaskCount = database.tasks.value.size
        val taskId = viewModel.addTask(title = "Single Identity Task", note = null, isImportant = false)
        assertEquals(initialTaskCount + 1, database.tasks.value.size)

        viewModel.scheduleTask(taskId = taskId, date = fixedToday.plusDays(3))

        // Total task count remains unchanged; no duplicate created
        assertEquals(initialTaskCount + 1, database.tasks.value.size)
        assertEquals(1, database.tasks.value.count { it.id == taskId })
    }

    @Test
    fun `isCompletedExpanded toggles and collapses correctly without altering task state`() {
        assertFalse(viewModel.isCompletedExpanded.value)

        viewModel.toggleCompletedExpanded()
        assertTrue(viewModel.isCompletedExpanded.value)

        val taskId = viewModel.tasks.value.first().id
        viewModel.toggleCompletion(taskId)
        assertEquals(1, viewModel.getCompletedTasks().size)

        viewModel.collapseCompleted()
        assertFalse(viewModel.isCompletedExpanded.value)
        assertEquals(1, viewModel.getCompletedTasks().size)
    }

    // ========================================================================
    // ATOMIC LATER -> SCHEDULE TESTS
    // ========================================================================

    @Test
    fun `Later to Schedule updates content and schedule atomically`() {
        val taskId = viewModel.addTask(title = "Old Title", note = "Old Note", isImportant = false)

        viewModel.scheduleTask(
            taskId = taskId,
            title = "New Title",
            note = "New Note",
            isImportant = true,
            date = fixedToday.plusDays(2),
            time = "2:00 PM",
            reminder = "30 minutes before"
        )

        // Reactively absent from Later
        assertFalse(viewModel.tasks.value.any { it.id == taskId })

        // Present in database with updated content and schedule
        val task = database.tasks.value.first { it.id == taskId }
        assertEquals("New Title", task.title)
        assertEquals("New Note", task.note)
        assertTrue(task.isImportant)

        val schedule = database.schedules.value.first { it.taskId == taskId }
        assertEquals(fixedToday.plusDays(2), schedule.startDate)
        assertEquals(LocalTime.of(14, 0), schedule.scheduledTime)
        assertEquals(30, schedule.reminderMinutesBefore)
    }

    @Test
    fun `invalid past date leaves title note and isImportant unchanged`() {
        val taskId = viewModel.addTask(title = "Original Title", note = "Original Note", isImportant = false)

        try {
            viewModel.scheduleTask(
                taskId = taskId,
                title = "Attempted New Title",
                note = "Attempted New Note",
                isImportant = true,
                date = fixedToday.minusDays(1)
            )
            fail("Expected exception on historical schedule date")
        } catch (e: IllegalArgumentException) {
            // Expected
        }

        // Entire transaction rolled back / uncommitted: task remains in Later with original values
        val task = database.tasks.value.first { it.id == taskId }
        assertEquals("Original Title", task.title)
        assertEquals("Original Note", task.note)
        assertFalse(task.isImportant)
        assertTrue(viewModel.tasks.value.any { it.id == taskId })
        assertEquals(0, database.schedules.value.count { it.taskId == taskId })
    }

    @Test
    fun `invalid reminder leaves task unchanged`() {
        val taskId = viewModel.addTask(title = "Original Title", note = "Original Note", isImportant = false)

        try {
            // Reminder specified without time -> illegal
            viewModel.scheduleTask(
                taskId = taskId,
                title = "Attempted Title",
                note = "Attempted Note",
                isImportant = true,
                date = fixedToday.plusDays(1),
                time = null,
                reminder = "15 minutes before"
            )
            fail("Expected exception when reminder has no time")
        } catch (e: IllegalArgumentException) {
            // Expected
        }

        val task = database.tasks.value.first { it.id == taskId }
        assertEquals("Original Title", task.title)
        assertFalse(task.isImportant)
        assertTrue(viewModel.tasks.value.any { it.id == taskId })
        assertEquals(0, database.schedules.value.count { it.taskId == taskId })
    }

    @Test
    fun `invalid recurrence leaves task unchanged`() {
        val taskId = viewModel.addTask(title = "Original Title", note = "Original Note", isImportant = false)

        try {
            // End date before start date -> illegal
            viewModel.scheduleTask(
                taskId = taskId,
                title = "Attempted Title",
                date = fixedToday.plusDays(5),
                recurrence = Recurrence.IntervalDays(intervalDays = 2, endDate = fixedToday.plusDays(2))
            )
            fail("Expected exception when recurrence endDate < startDate")
        } catch (e: IllegalArgumentException) {
            // Expected
        }

        val task = database.tasks.value.first { it.id == taskId }
        assertEquals("Original Title", task.title)
        assertTrue(viewModel.tasks.value.any { it.id == taskId })
        assertEquals(0, database.schedules.value.count { it.taskId == taskId })
    }
}
