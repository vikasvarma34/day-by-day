package com.vikaspokala.daybyday.ui.screens.history

import java.time.LocalDate
import com.vikaspokala.daybyday.ui.fake.InMemoryPlannerDatabase
import com.vikaspokala.daybyday.ui.models.Recurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HistorySnapshotSemanticsTest {

    private val referenceDate: LocalDate = LocalDate.of(2026, 8, 20)
    private lateinit var database: InMemoryPlannerDatabase
    private lateinit var historyViewModel: HistoryViewModel

    @Before
    fun setUp() {
        database = InMemoryPlannerDatabase(referenceDate = referenceDate)
        historyViewModel = HistoryViewModel(database = database, referenceDate = referenceDate)
    }

    @Test
    fun `1-6 non-important ONCE completion, star later, unstar, restar lifecycle`() {
        val pastDate = referenceDate.minusDays(2)
        val taskId = database.createDatedTask(title = "Prepare slides", date = pastDate, isImportant = false, plannerToday = referenceDate)
        val scheduleId = database.schedules.value.first { it.taskId == taskId }.id
        database.completeTask(taskId, scheduleId, pastDate, pastDate, referenceDate)

        // 1. Non-important completion record retained internally, but not visible in History
        val internalRecord = database.completions.value.find { it.taskId == taskId }
        assertNotNull("Internal completion record must be retained", internalRecord)
        assertFalse(internalRecord!!.isImportantSnapshot)
        assertEquals("Prepare slides", internalRecord.titleSnapshot)
        assertEquals(pastDate, internalRecord.completedDate)

        val initialVisible = historyViewModel.getFilteredGroupedHistory()
        assertFalse("Must not be visible in History while isImportant is false", initialVisible.values.flatten().any { it.id == taskId })

        val originalCompletedDate = internalRecord.completedDate
        val originalCompletedAtEpoch = internalRecord.completedAtEpochMillis

        // 2. Later mark it Important
        database.updateTask(taskId, title = "Prepare slides", isImportant = true, plannerToday = referenceDate)
        assertTrue("Live task is now important", database.tasks.value.first { it.id == taskId }.isImportant)

        val visibleAfterStar = historyViewModel.getFilteredGroupedHistory()
        assertTrue("Task must now appear in History", visibleAfterStar.containsKey(pastDate))
        val historyItem = visibleAfterStar[pastDate]?.find { it.id == taskId }
        assertNotNull(historyItem)

        // 3. Verify original completedDate unchanged (18 Aug, not 20 Aug)
        assertEquals(originalCompletedDate, historyItem?.completedDate)

        // 4. Verify original completion timestamp and ordering unchanged
        assertEquals("Prepare slides", historyItem?.title)

        // 5. Mark Important OFF again -> disappears from visible History
        database.updateTask(taskId, title = "Prepare slides", isImportant = false, plannerToday = referenceDate)
        val visibleAfterUnstar = historyViewModel.getFilteredGroupedHistory()
        assertFalse("Disappears from visible History after unstar", visibleAfterUnstar.values.flatten().any { it.id == taskId })

        // Internal completion record still retained
        assertNotNull(database.completions.value.find { it.taskId == taskId })

        // 6. Mark ON again -> same completion becomes visible without new timestamp/date
        database.updateTask(taskId, title = "Prepare slides", isImportant = true, plannerToday = referenceDate)
        val visibleAfterRestar = historyViewModel.getFilteredGroupedHistory()
        val itemRestar = visibleAfterRestar[pastDate]?.find { it.id == taskId }
        assertNotNull("Reappears in History", itemRestar)
        assertEquals(originalCompletedDate, itemRestar?.completedDate)
        assertEquals(originalCompletedAtEpoch, database.completions.value.first { it.taskId == taskId }.completedAtEpochMillis)
    }

    @Test
    fun `7 important ONCE completion initially visible when historical`() {
        val pastDate = referenceDate.minusDays(2)
        val taskId = database.createDatedTask(title = "Submit Taxes", date = pastDate, isImportant = true, plannerToday = referenceDate)
        val scheduleId = database.schedules.value.first { it.taskId == taskId }.id
        database.completeTask(taskId, scheduleId, pastDate, pastDate, referenceDate)

        val visible = historyViewModel.getFilteredGroupedHistory()
        assertTrue("Visible in History", visible.values.flatten().any { it.id == taskId && it.title == "Submit Taxes" })
    }

    @Test
    fun `8 title edit after completion does not change History title snapshot`() {
        val pastDate = referenceDate.minusDays(2)
        val taskId = database.createDatedTask(title = "Original Title at Completion", date = pastDate, isImportant = true, plannerToday = referenceDate)
        val scheduleId = database.schedules.value.first { it.taskId == taskId }.id
        database.completeTask(taskId, scheduleId, pastDate, pastDate, referenceDate)

        // Update live task title
        database.updateTask(taskId = taskId, title = "Changed Live Title Later", plannerToday = referenceDate)

        assertEquals("Changed Live Title Later", database.tasks.value.first { it.id == taskId }.title)
        val snapshot = database.completions.value.first { it.taskId == taskId }
        assertEquals("Original Title at Completion", snapshot.titleSnapshot)

        // Toggling importance also preserves the original snapshot title
        database.updateTask(taskId = taskId, title = "Changed Live Title Later", isImportant = false, plannerToday = referenceDate)
        database.updateTask(taskId = taskId, title = "Changed Live Title Later", isImportant = true, plannerToday = referenceDate)
        val snapshotAfterToggle = database.completions.value.first { it.taskId == taskId }
        assertEquals("Original Title at Completion", snapshotAfterToggle.titleSnapshot)
    }

    @Test
    fun `9 direct-Later non-important completion, star later, becomes visible with original completion metadata`() {
        val pastDate = referenceDate.minusDays(3)
        val taskId = database.createLaterTask(title = "Read Chapter 1", note = null, isImportant = false)
        database.completeTask(taskId, scheduleId = null, scheduledDate = null, completedDate = pastDate, plannerToday = pastDate)

        // Initially not visible in History
        val initialHistory = historyViewModel.getFilteredGroupedHistory()
        assertFalse(initialHistory.values.flatten().any { it.id == taskId })

        val storedRecord = database.completions.value.first { it.taskId == taskId }
        assertEquals(pastDate, storedRecord.completedDate)

        // Star it later
        database.updateTask(taskId, title = "Read Chapter 1", isImportant = true, plannerToday = referenceDate)

        val historyAfterStar = historyViewModel.getFilteredGroupedHistory()
        assertTrue(historyAfterStar.containsKey(pastDate))
        val item = historyAfterStar[pastDate]?.find { it.id == taskId }
        assertNotNull(item)
        assertEquals("Read Chapter 1", item?.title)
        assertEquals(pastDate, item?.completedDate)
    }

    @Test
    fun `10 recurring completion star or unstar never appears in History`() {
        val pastDate = referenceDate.minusDays(2)
        val taskId = database.createDatedTask(
            title = "Daily Recurring Standup",
            date = pastDate,
            recurrence = Recurrence.IntervalDays(1),
            isImportant = false,
            plannerToday = pastDate
        )
        val scheduleId = database.schedules.value.first { it.taskId == taskId }.id
        database.completeTask(taskId, scheduleId, pastDate, pastDate, referenceDate)

        val history = historyViewModel.getFilteredGroupedHistory()
        assertFalse(history.values.flatten().any { it.id == taskId })

        // Star it later
        database.updateTask(taskId, title = "Daily Recurring Standup", isImportant = true, plannerToday = referenceDate)

        val historyAfterStar = historyViewModel.getFilteredGroupedHistory()
        assertFalse(historyAfterStar.values.flatten().any { it.id == taskId })
    }

    @Test
    fun `11 incomplete task star or unstar creates no History completion record`() {
        val taskId = database.createDatedTask(title = "Unfinished Task", date = referenceDate, isImportant = false, plannerToday = referenceDate)

        database.updateTask(taskId, title = "Unfinished Task", isImportant = true, plannerToday = referenceDate)
        assertEquals(0, database.completions.value.count { it.taskId == taskId })

        database.updateTask(taskId, title = "Unfinished Task", isImportant = false, plannerToday = referenceDate)
        assertEquals(0, database.completions.value.count { it.taskId == taskId })
    }

    @Test
    fun `12 Undo completion removes completion record entirely`() {
        val pastDate = referenceDate.minusDays(2)
        val taskId = database.createDatedTask(title = "Task To Undo", date = pastDate, isImportant = true, plannerToday = referenceDate)
        val scheduleId = database.schedules.value.first { it.taskId == taskId }.id
        database.completeTask(taskId, scheduleId, pastDate, pastDate, referenceDate)

        assertNotNull(database.completions.value.find { it.taskId == taskId })

        // Undo
        database.undoTask(taskId, scheduleId, pastDate)
        assertNull("Undo must completely remove completion record", database.completions.value.find { it.taskId == taskId })
    }

    @Test
    fun `13 re-complete creates new snapshot from NEW completion event`() {
        val pastDate1 = referenceDate.minusDays(4)
        val pastDate2 = referenceDate.minusDays(1)
        val taskId = database.createDatedTask(title = "Initial Title", date = pastDate1, isImportant = true, plannerToday = pastDate1)
        val scheduleId = database.schedules.value.first { it.taskId == taskId }.id

        database.completeTask(taskId, scheduleId, pastDate1, pastDate1, referenceDate)
        assertEquals(pastDate1, database.completions.value.first { it.taskId == taskId }.completedDate)

        // Undo
        database.undoTask(taskId, scheduleId, pastDate1)
        assertEquals(0, database.completions.value.count { it.taskId == taskId })

        // Edit title & re-complete on pastDate2
        database.updateTask(taskId = taskId, title = "New Title At Re-completion", plannerToday = referenceDate)
        database.completeTask(taskId, scheduleId, pastDate1, pastDate2, referenceDate)

        val newRecord = database.completions.value.first { it.taskId == taskId }
        assertEquals("New Title At Re-completion", newRecord.titleSnapshot)
        assertEquals(pastDate2, newRecord.completedDate)
    }

    @Test
    fun `14 completion made today remains invisible from History today even if Important`() {
        val today = referenceDate
        val taskId = database.createDatedTask(title = "Task Completed Today", date = today, isImportant = true, plannerToday = today)
        val scheduleId = database.schedules.value.first { it.taskId == taskId }.id
        database.completeTask(taskId, scheduleId, today, today, today)

        // Retained in database
        assertNotNull(database.completions.value.find { it.taskId == taskId })

        // But invisible in History because completedDate is not before plannerToday
        val visible = historyViewModel.getFilteredGroupedHistory(plannerToday = today)
        assertFalse("Completed today must not appear in history today", visible.values.flatten().any { it.id == taskId })
    }
}
