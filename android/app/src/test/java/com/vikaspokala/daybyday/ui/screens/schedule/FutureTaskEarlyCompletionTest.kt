package com.vikaspokala.daybyday.ui.screens.schedule

import java.time.LocalDate
import com.vikaspokala.daybyday.ui.fake.InMemoryPlannerDatabase
import com.vikaspokala.daybyday.ui.models.Recurrence
import com.vikaspokala.daybyday.ui.screens.history.HistoryViewModel
import com.vikaspokala.daybyday.ui.screens.today.TodayViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FutureTaskEarlyCompletionTest {

    private val testToday: LocalDate = LocalDate.of(2026, 8, 20)
    private lateinit var database: InMemoryPlannerDatabase
    private lateinit var scheduleViewModel: ScheduleViewModel
    private lateinit var todayViewModel: TodayViewModel
    private lateinit var historyViewModel: HistoryViewModel

    @Before
    fun setUp() {
        database = InMemoryPlannerDatabase(referenceDate = testToday)
        scheduleViewModel = ScheduleViewModel(database = database, plannerTodayProvider = { testToday })
        todayViewModel = TodayViewModel(database = database, plannerTodayProvider = { testToday })
        historyViewModel = HistoryViewModel(database = database, referenceDate = testToday)
    }

    @Test
    fun `1-5 future ONCE task can be completed early with correct dates and non-rescheduling`() {
        val futureDate = testToday.plusDays(1) // 2026-08-21
        val taskId = scheduleViewModel.addTask(
            title = "Prepare quarterly review",
            date = futureDate,
            recurrence = Recurrence.Once,
            isImportant = true
        )

        scheduleViewModel.selectDate(futureDate)

        // Initial state on future date
        val initialTask = scheduleViewModel.tasks.value.first { it.id == taskId }
        assertFalse(initialTask.isCompleted)

        // User opens Schedule -> Aug 21 and taps Complete TODAY
        scheduleViewModel.toggleCompletion(taskId, scheduledDate = futureDate)

        // 1. future ONCE task can be completed early
        // 4. future occurrence appears completed immediately in Schedule
        val completedTask = scheduleViewModel.tasks.value.first { it.id == taskId }
        assertTrue("Future occurrence must appear completed immediately in Schedule", completedTask.isCompleted)

        // Verify database completion entity
        val comp = database.completions.value.first { it.taskId == taskId }
        // 2. scheduledDate remains the future date
        assertEquals("scheduledDate remains the future date", futureDate, comp.scheduledDate)
        // 3. completedDate is plannerToday
        assertEquals("completedDate is plannerToday", testToday, comp.completedDate)

        // 5. task is NOT moved/rescheduled to plannerToday
        val schedule = database.schedules.value.first { it.taskId == taskId }
        assertEquals("Schedule startDate must remain the future date", futureDate, schedule.startDate)
        assertEquals("Schedule endDate must remain the future date", futureDate, schedule.endDate)

        // Not present on Today screen
        val todayTasks = todayViewModel.tasks.value
        assertFalse("Task must not be moved/rescheduled to plannerToday", todayTasks.any { it.id == taskId })
    }

    @Test
    fun `6 Important future ONCE completed early appears in History under plannerToday`() {
        val futureDate = testToday.plusDays(3) // 2026-08-23
        val taskId = scheduleViewModel.addTask(
            title = "Important Future Deliverable",
            date = futureDate,
            recurrence = Recurrence.Once,
            isImportant = true
        )

        scheduleViewModel.toggleCompletion(taskId, scheduledDate = futureDate)

        val grouped = historyViewModel.getFilteredGroupedHistory()
        assertTrue("Must appear in History grouped under plannerToday", grouped.containsKey(testToday))
        val itemsToday = grouped[testToday].orEmpty()
        assertTrue(itemsToday.any { it.id == taskId && it.title == "Important Future Deliverable" })
    }

    @Test
    fun `7 non-important future ONCE completed early stays out of History`() {
        val futureDate = testToday.plusDays(2) // 2026-08-22
        val taskId = scheduleViewModel.addTask(
            title = "Casual Future Errand",
            date = futureDate,
            recurrence = Recurrence.Once,
            isImportant = false
        )

        scheduleViewModel.toggleCompletion(taskId, scheduledDate = futureDate)

        val grouped = historyViewModel.getFilteredGroupedHistory()
        assertFalse(grouped.values.flatten().any { it.id == taskId })
    }

    @Test
    fun `8 recurring future occurrence can be completed early but stays out of History`() {
        val futureDate = testToday.plusDays(1)
        val taskId = scheduleViewModel.addTask(
            title = "Daily Recurring Task",
            date = testToday,
            recurrence = Recurrence.IntervalDays(1),
            isImportant = true
        )

        scheduleViewModel.selectDate(futureDate)
        scheduleViewModel.toggleCompletion(taskId, scheduledDate = futureDate)

        // Completed in Schedule for that future occurrence
        val occurrence = scheduleViewModel.tasks.value.first { it.id == taskId }
        assertTrue(occurrence.isCompleted)

        val comp = database.completions.value.first { it.taskId == taskId && it.scheduledDate == futureDate }
        assertEquals(futureDate, comp.scheduledDate)
        assertEquals(testToday, comp.completedDate)

        // Stays out of History because it's recurring
        val historyItems = historyViewModel.tasks.value
        assertFalse(historyItems.any { it.id == taskId })
    }

    @Test
    fun `9 undo removes only that future occurrence completion`() {
        val futureDate = testToday.plusDays(2)
        val taskId = scheduleViewModel.addTask(
            title = "Future Task To Undo",
            date = futureDate,
            recurrence = Recurrence.Once,
            isImportant = true
        )

        scheduleViewModel.selectDate(futureDate)
        scheduleViewModel.toggleCompletion(taskId, scheduledDate = futureDate)
        assertTrue(scheduleViewModel.tasks.value.first { it.id == taskId }.isCompleted)
        assertEquals(1, database.completions.value.count { it.taskId == taskId })

        // Undo
        scheduleViewModel.toggleCompletion(taskId, scheduledDate = futureDate)
        assertFalse(scheduleViewModel.tasks.value.first { it.id == taskId }.isCompleted)
        assertEquals(0, database.completions.value.count { it.taskId == taskId })

        // Schedule remains untouched
        val schedule = database.schedules.value.first { it.taskId == taskId }
        assertEquals(futureDate, schedule.startDate)
    }

    @Test
    fun `10 historical completion still records completedDate = historical selectedDate`() {
        val historicalDate = testToday.minusDays(3) // 2026-08-17
        val taskId = scheduleViewModel.addTask(
            title = "Historical Log",
            date = historicalDate,
            recurrence = Recurrence.Once,
            isImportant = true
        )

        scheduleViewModel.selectDate(historicalDate)
        scheduleViewModel.toggleCompletion(taskId, scheduledDate = historicalDate)

        val comp = database.completions.value.first { it.taskId == taskId }
        assertEquals(historicalDate, comp.scheduledDate)
        assertEquals("Historical backfill completedDate must equal historical selectedDate", historicalDate, comp.completedDate)
    }

    @Test
    fun `11 Today completion still records completedDate = plannerToday`() {
        val taskId = todayViewModel.addTask(
            title = "Today Log",
            date = testToday,
            recurrence = Recurrence.Once,
            isImportant = true
        )

        todayViewModel.toggleCompletion(taskId, scheduledDate = testToday)

        val comp = database.completions.value.first { it.taskId == taskId }
        assertEquals(testToday, comp.scheduledDate)
        assertEquals(testToday, comp.completedDate)
    }
}
