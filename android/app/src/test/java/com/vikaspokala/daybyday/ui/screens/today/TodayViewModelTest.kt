package com.vikaspokala.daybyday.ui.screens.today

import java.time.LocalDate
import java.time.LocalTime
import com.vikaspokala.daybyday.ui.fake.InMemoryPlannerDatabase
import com.vikaspokala.daybyday.ui.models.Recurrence
import com.vikaspokala.daybyday.ui.screens.schedule.ScheduleViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TodayViewModelTest {

    private lateinit var database: InMemoryPlannerDatabase
    private lateinit var viewModel: TodayViewModel
    private val testToday: LocalDate = LocalDate.of(2026, 8, 19)

    @Before
    fun setUp() {
        database = InMemoryPlannerDatabase(referenceDate = testToday)
        viewModel = TodayViewModel(database = database, plannerTodayProvider = { testToday })
    }

    @Test
    fun `1 Today observes canonical plannerToday tasks with expected dataset`() {
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
    fun `2 toggleCompletion updates task completion state`() {
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
    fun `3 toggleImportant updates task importance state`() {
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
    fun `4 create from Today creates exactly one canonical task and schedule`() {
        val initialTaskCount = database.tasks.value.size
        val initialSchedCount = database.schedules.value.size

        val newId = viewModel.addTask(
            title = "Test Note Task",
            note = "This is a test note for today task",
            date = testToday,
            time = "10:00 AM",
            isImportant = true
        )

        assertEquals(initialTaskCount + 1, database.tasks.value.size)
        assertEquals(initialSchedCount + 1, database.schedules.value.size)

        val addedTask = viewModel.tasks.value.first { it.id == newId }
        assertEquals("Test Note Task", addedTask.title)
        assertEquals("This is a test note for today task", addedTask.note)
        assertEquals("10:00 AM", addedTask.time)
        assertTrue(addedTask.isImportant)
    }

    @Test
    fun `5 created Today task automatically appears in Schedule for same date`() {
        val scheduleViewModel = ScheduleViewModel(database = database, plannerTodayProvider = { testToday })
        scheduleViewModel.selectDate(testToday)

        val newId = viewModel.addTask(
            title = "Sync Task",
            date = testToday,
            time = "11:30 AM"
        )

        val scheduleTasks = scheduleViewModel.tasks.value
        assertTrue(scheduleTasks.any { it.id == newId && it.title == "Sync Task" })
    }

    @Test
    fun `6 edit from one view is visible in the other`() {
        val scheduleViewModel = ScheduleViewModel(database = database, plannerTodayProvider = { testToday })
        scheduleViewModel.selectDate(testToday)

        val newId = viewModel.addTask(
            title = "Before Edit",
            note = "Initial Note",
            date = testToday
        )

        viewModel.updateTask(
            id = newId,
            title = "After Edit",
            note = "Updated Note",
            date = testToday,
            time = "02:00 PM",
            isImportant = true
        )

        val taskInSchedule = scheduleViewModel.tasks.value.first { it.id == newId }
        assertEquals("After Edit", taskInSchedule.title)
        assertEquals("Updated Note", taskInSchedule.note)
        assertEquals("2:00 PM", taskInSchedule.time)
        assertTrue(taskInSchedule.isImportant)
    }

    @Test
    fun `7 delete from one view removes it from both`() {
        val scheduleViewModel = ScheduleViewModel(database = database, plannerTodayProvider = { testToday })
        scheduleViewModel.selectDate(testToday)

        val newId = viewModel.addTask(title = "To Delete", date = testToday)
        assertTrue(viewModel.tasks.value.any { it.id == newId })
        assertTrue(scheduleViewModel.tasks.value.any { it.id == newId })

        viewModel.deleteTask(newId)

        assertFalse(viewModel.tasks.value.any { it.id == newId })
        assertFalse(scheduleViewModel.tasks.value.any { it.id == newId })
    }

    @Test
    fun `8 completion in Today updates Schedule same occurrence`() {
        val scheduleViewModel = ScheduleViewModel(database = database, plannerTodayProvider = { testToday })
        scheduleViewModel.selectDate(testToday)

        assertFalse(scheduleViewModel.tasks.value.first { it.id == "s1" }.isCompleted)

        viewModel.toggleCompletion("s1")

        assertTrue(scheduleViewModel.tasks.value.first { it.id == "s1" }.isCompleted)
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
