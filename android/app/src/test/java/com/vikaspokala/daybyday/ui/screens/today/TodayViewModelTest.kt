package com.vikaspokala.daybyday.ui.screens.today

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.vikaspokala.daybyday.ui.fake.InMemoryDatedTaskStore
import com.vikaspokala.daybyday.ui.screens.schedule.ScheduleViewModel

class TodayViewModelTest {

    private lateinit var store: InMemoryDatedTaskStore
    private lateinit var viewModel: TodayViewModel
    private val testToday: LocalDate = LocalDate.of(2026, 8, 19)

    @Before
    fun setUp() {
        store = InMemoryDatedTaskStore(initialDate = testToday)
        viewModel = TodayViewModel(taskStore = store, initialDate = testToday)
    }

    @Test
    fun `initial tasks for testToday contains expected demo dataset`() {
        val tasksForToday = viewModel.getTasksForDate(testToday)
        assertEquals(6, tasksForToday.size)

        val timedActive = tasksForToday.filter { !it.isCompleted && it.time != null }
        assertEquals(4, timedActive.size)

        val anytimeActive = tasksForToday.filter { !it.isCompleted && it.time == null }
        assertEquals(2, anytimeActive.size)

        val completed = tasksForToday.filter { it.isCompleted }
        assertEquals(0, completed.size)
    }

    @Test
    fun `toggleCompletion updates task completion state`() {
        val initialTask = viewModel.tasks.value.first { it.id == "s1" }
        assertFalse(initialTask.isCompleted)

        viewModel.toggleCompletion("s1")
        val updatedTask = viewModel.tasks.value.first { it.id == "s1" }
        assertTrue(updatedTask.isCompleted)

        viewModel.toggleCompletion("s1")
        val restoredTask = viewModel.tasks.value.first { it.id == "s1" }
        assertFalse(restoredTask.isCompleted)
    }

    @Test
    fun `toggleImportant updates task importance state`() {
        val initialTask = viewModel.tasks.value.first { it.id == "s1" }
        assertFalse(initialTask.isImportant)

        viewModel.toggleImportant("s1")
        val updatedTask = viewModel.tasks.value.first { it.id == "s1" }
        assertTrue(updatedTask.isImportant)

        viewModel.toggleImportant("s1")
        val restoredTask = viewModel.tasks.value.first { it.id == "s1" }
        assertFalse(restoredTask.isImportant)
    }

    @Test
    fun `addTask and updateTask persist note correctly`() {
        val newId = viewModel.addTask(
            title = "Test Note Task",
            note = "This is a test note for today task",
            date = testToday,
            time = "10:00 AM",
            isImportant = true
        )

        val addedTask = viewModel.tasks.value.first { it.id == newId }
        assertEquals("Test Note Task", addedTask.title)
        assertEquals("This is a test note for today task", addedTask.note)
        assertEquals("10:00 AM", addedTask.time)

        viewModel.updateTask(
            id = newId,
            title = "Updated Note Task",
            note = "Updated note content",
            date = testToday,
            time = "11:00 AM",
            isImportant = false
        )

        val updatedTask = viewModel.tasks.value.first { it.id == newId }
        assertEquals("Updated Note Task", updatedTask.title)
        assertEquals("Updated note content", updatedTask.note)

        viewModel.updateTask(
            id = newId,
            title = "Updated Note Task",
            note = "   ",
            date = testToday,
            time = "11:00 AM",
            isImportant = false
        )
        val emptyNoteTask = viewModel.tasks.value.first { it.id == newId }
        assertNull(emptyNoteTask.note)
    }

    @Test
    fun `next day updates selectedDate by +1 day`() {
        assertEquals(testToday, viewModel.selectedDate.value)
        viewModel.selectNextDay()
        assertEquals(LocalDate.of(2026, 8, 20), viewModel.selectedDate.value)
        viewModel.selectNextDay()
        assertEquals(LocalDate.of(2026, 8, 21), viewModel.selectedDate.value)
    }

    @Test
    fun `previous day updates selectedDate by -1 day`() {
        assertEquals(testToday, viewModel.selectedDate.value)
        viewModel.selectPreviousDay()
        assertEquals(LocalDate.of(2026, 8, 18), viewModel.selectedDate.value)
        viewModel.selectPreviousDay()
        assertEquals(LocalDate.of(2026, 8, 17), viewModel.selectedDate.value)
    }

    @Test
    fun `crossing month boundary correctly navigates dates`() {
        viewModel.selectDate(LocalDate.of(2026, 8, 31))
        viewModel.selectNextDay()
        assertEquals(LocalDate.of(2026, 9, 1), viewModel.selectedDate.value)

        viewModel.selectDate(LocalDate.of(2026, 9, 1))
        viewModel.selectPreviousDay()
        assertEquals(LocalDate.of(2026, 8, 31), viewModel.selectedDate.value)
    }

    @Test
    fun `crossing year boundary correctly navigates dates`() {
        viewModel.selectDate(LocalDate.of(2026, 12, 31))
        viewModel.selectNextDay()
        assertEquals(LocalDate.of(2027, 1, 1), viewModel.selectedDate.value)

        viewModel.selectDate(LocalDate.of(2027, 1, 1))
        viewModel.selectPreviousDay()
        assertEquals(LocalDate.of(2026, 12, 31), viewModel.selectedDate.value)
    }

    @Test
    fun `selected-date task filtering returns only tasks for selected date with proper ordering`() {
        val tasksForToday = viewModel.getTasksForDate(testToday)
        assertEquals(6, tasksForToday.size)

        // Verify task ordering: timed active ascending -> anytime active
        assertEquals("Morning walk", tasksForToday[0].title)      // 7:30 AM
        assertEquals("Plan weekly meals", tasksForToday[1].title) // 9:00 AM
        assertEquals("Pick up parcel", tasksForToday[2].title)    // 3:00 PM
        assertEquals("Call parents", tasksForToday[3].title)      // 6:30 PM
        assertEquals("Review insurance", tasksForToday[4].title)  // Anytime
        assertEquals("Prepare documents", tasksForToday[5].title) // Anytime

        // Completing a task moves it to the bottom
        viewModel.toggleCompletion("s1")
        val reorderedTasks = viewModel.getTasksForDate(testToday)
        assertEquals("Plan weekly meals", reorderedTasks[0].title)
        assertEquals("Morning walk", reorderedTasks.last().title)
        assertTrue(reorderedTasks.last().isCompleted)

        // Yesterday tasks (minus 1 day) has 3 tasks
        val yesterdayTasks = viewModel.getTasksForDate(testToday.minusDays(1))
        assertEquals(3, yesterdayTasks.size)

        // Tomorrow tasks (plus 1 day) has 3 tasks
        val tomorrowTasks = viewModel.getTasksForDate(testToday.plusDays(1))
        assertEquals(3, tomorrowTasks.size)
    }

    @Test
    fun `Today and Schedule share the same dated task state`() {
        val scheduleViewModel = ScheduleViewModel(taskStore = store, initialDate = testToday)
        val targetDate = testToday.plusDays(1)

        // Create task in Schedule for targetDate
        val newId = scheduleViewModel.addTask(
            title = "Shared Team Meeting",
            note = "Important sync",
            date = targetDate,
            time = "10:00 AM",
            isImportant = true
        )

        // Today browsing targetDate immediately sees the same task
        val todayTasksOnTargetDate = viewModel.getTasksForDate(targetDate)
        assertTrue(todayTasksOnTargetDate.any { it.id == newId && it.title == "Shared Team Meeting" })

        // Complete the task from Today
        viewModel.toggleCompletion(newId)

        // Schedule immediately reflects completed state
        val scheduleTasksOnTargetDate = scheduleViewModel.getTasksForDate(targetDate)
        val taskInSchedule = scheduleTasksOnTargetDate.first { it.id == newId }
        assertTrue(taskInSchedule.isCompleted)

        // Delete from Today -> disappears from Schedule
        viewModel.deleteTask(newId)
        assertFalse(scheduleViewModel.getTasksForDate(targetDate).any { it.id == newId })
    }

    @Test
    fun `greeting logic follows defined current-time ranges`() {
        // 05:00 to 11:59 -> Good morning
        assertEquals("Good morning, Vicky", TodayViewModel.getGreeting(LocalTime.of(5, 0)))
        assertEquals("Good morning, Vicky", TodayViewModel.getGreeting(LocalTime.of(8, 30)))
        assertEquals("Good morning, Vicky", TodayViewModel.getGreeting(LocalTime.of(11, 59)))

        // 12:00 to 16:59 -> Good afternoon
        assertEquals("Good afternoon, Vicky", TodayViewModel.getGreeting(LocalTime.of(12, 0)))
        assertEquals("Good afternoon, Vicky", TodayViewModel.getGreeting(LocalTime.of(14, 15)))
        assertEquals("Good afternoon, Vicky", TodayViewModel.getGreeting(LocalTime.of(16, 59)))

        // 17:00 to 20:59 -> Good evening
        assertEquals("Good evening, Vicky", TodayViewModel.getGreeting(LocalTime.of(17, 0)))
        assertEquals("Good evening, Vicky", TodayViewModel.getGreeting(LocalTime.of(19, 0)))
        assertEquals("Good evening, Vicky", TodayViewModel.getGreeting(LocalTime.of(20, 59)))

        // 21:00 to 04:59 -> Good night
        assertEquals("Good night, Vicky", TodayViewModel.getGreeting(LocalTime.of(21, 0)))
        assertEquals("Good night, Vicky", TodayViewModel.getGreeting(LocalTime.of(23, 30)))
        assertEquals("Good night, Vicky", TodayViewModel.getGreeting(LocalTime.of(0, 0)))
        assertEquals("Good night, Vicky", TodayViewModel.getGreeting(LocalTime.of(4, 59)))
    }
}
