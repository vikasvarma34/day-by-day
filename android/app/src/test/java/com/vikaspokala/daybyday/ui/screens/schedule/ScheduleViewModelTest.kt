package com.vikaspokala.daybyday.ui.screens.schedule

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScheduleViewModelTest {

    private lateinit var viewModel: ScheduleViewModel
    private val testToday: LocalDate = LocalDate.of(2026, 8, 19)

    @Before
    fun setUp() {
        viewModel = ScheduleViewModel(initialDate = testToday)
    }

    @Test
    fun `initial state defaults to initialDate parameter and its YearMonth`() {
        assertEquals(YearMonth.from(testToday), viewModel.currentMonth.value)
        assertEquals(testToday, viewModel.selectedDate.value)
    }

    @Test
    fun `resetToToday resets selectedDate and currentMonth to target date`() {
        // User selects a different date (e.g., testToday + 10 days)
        val alternateDate = testToday.plusDays(10)
        viewModel.selectDate(alternateDate)
        assertEquals(alternateDate, viewModel.selectedDate.value)

        // Bottom nav re-entry triggers resetToToday()
        viewModel.resetToToday(testToday)
        assertEquals(testToday, viewModel.selectedDate.value)
        assertEquals(YearMonth.from(testToday), viewModel.currentMonth.value)
    }

    @Test
    fun `tasks for testToday return 6 tasks with incomplete first and completed at bottom`() {
        val tasks = viewModel.getTasksForDate(testToday)
        assertEquals(6, tasks.size)

        // Incomplete timed tasks ascending by time
        assertEquals("Morning walk", tasks[0].title)
        assertEquals("7:30 AM", tasks[0].time)
        assertFalse(tasks[0].isCompleted)

        assertEquals("Plan weekly meals", tasks[1].title)
        assertEquals("9:00 AM", tasks[1].time)
        assertFalse(tasks[1].isCompleted)

        assertEquals("Pick up parcel", tasks[2].title)
        assertEquals("3:00 PM", tasks[2].time)
        assertFalse(tasks[2].isCompleted)

        assertEquals("Call parents", tasks[3].title)
        assertEquals("6:30 PM", tasks[3].time)
        assertFalse(tasks[3].isCompleted)

        // Incomplete Anytime tasks
        assertEquals("Review insurance", tasks[4].title)
        assertEquals(null, tasks[4].time)
        assertFalse(tasks[4].isCompleted)

        assertEquals("Prepare documents", tasks[5].title)
        assertEquals(null, tasks[5].time)
        assertFalse(tasks[5].isCompleted)
    }

    @Test
    fun `completing a task moves it to the bottom and undoing restores its position`() {
        // s2 ("Plan weekly meals", 9:00 AM) is initially at index 1
        var tasks = viewModel.getTasksForDate(testToday)
        assertEquals("Plan weekly meals", tasks[1].title)
        assertFalse(tasks[1].isCompleted)

        // Complete s2
        viewModel.toggleCompletion("s2")
        tasks = viewModel.getTasksForDate(testToday)

        val completedTask = tasks.first { it.id == "s2" }
        assertTrue(completedTask.isCompleted)

        // Verify it moved below all incomplete tasks
        val completedList = tasks.filter { it.isCompleted }
        assertEquals("s2", completedList.first().id)
        assertEquals("Plan weekly meals", completedList.first().title)

        // Undo completion
        viewModel.toggleCompletion("s2")
        tasks = viewModel.getTasksForDate(testToday)

        val restoredTask = tasks[1]
        assertEquals("s2", restoredTask.id)
        assertFalse(restoredTask.isCompleted)
    }

    @Test
    fun `toggleImportant updates task importance and dynamic indicator`() {
        val datePast = testToday.minusDays(12)
        assertTrue(viewModel.hasImportantTask(datePast))

        // s7 ("Car service") is the only important task on datePast -> toggle it
        viewModel.toggleImportant("s7")
        val updatedTask = viewModel.getTasksForDate(datePast).first { it.id == "s7" }
        assertFalse(updatedTask.isImportant)

        // Now datePast should no longer have an important task indicator dot
        assertFalse(viewModel.hasImportantTask(datePast))
    }

    @Test
    fun `hasImportantTask derives correctly from fake task state`() {
        assertTrue(viewModel.hasImportantTask(testToday.minusDays(12)))
        assertTrue(viewModel.hasImportantTask(testToday))
        assertTrue(viewModel.hasImportantTask(testToday.plusDays(5)))
        assertTrue(viewModel.hasImportantTask(testToday.plusDays(12)))

        // testToday.minusDays(6) has a task ("Water plants"), but it is not important -> false
        assertFalse(viewModel.hasImportantTask(testToday.minusDays(6)))
        // testToday.minusDays(1) has no tasks -> false
        assertFalse(viewModel.hasImportantTask(testToday.minusDays(1)))
    }

    @Test
    fun `month navigation handles month and year boundaries correctly`() {
        val startMonth = YearMonth.from(testToday)
        viewModel.previousMonth()
        assertEquals(startMonth.minusMonths(1), viewModel.currentMonth.value)

        repeat(7) { viewModel.previousMonth() }
        assertEquals(startMonth.minusMonths(8), viewModel.currentMonth.value)

        viewModel.nextMonth()
        assertEquals(startMonth.minusMonths(7), viewModel.currentMonth.value)
    }

    @Test
    fun `selectToday updates selectedDate and currentMonth`() {
        val targetDate = LocalDate.of(2027, 3, 15)
        viewModel.selectToday(targetDate)

        assertEquals(targetDate, viewModel.selectedDate.value)
        assertEquals(YearMonth.of(2027, 3), viewModel.currentMonth.value)
    }
}
