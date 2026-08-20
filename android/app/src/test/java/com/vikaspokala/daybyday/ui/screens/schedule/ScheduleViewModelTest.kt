package com.vikaspokala.daybyday.ui.screens.schedule

import java.time.LocalDate
import java.time.YearMonth
import com.vikaspokala.daybyday.ui.fake.InMemoryPlannerDatabase
import com.vikaspokala.daybyday.ui.models.Recurrence
import com.vikaspokala.daybyday.ui.screens.today.TodayViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScheduleViewModelTest {

    private lateinit var database: InMemoryPlannerDatabase
    private lateinit var viewModel: ScheduleViewModel
    private val testToday: LocalDate = LocalDate.of(2026, 8, 19)

    @Before
    fun setUp() {
        database = InMemoryPlannerDatabase(referenceDate = testToday)
        viewModel = ScheduleViewModel(database = database, plannerTodayProvider = { testToday })
    }

    @Test
    fun `1 initial state defaults to testToday parameter and its YearMonth`() {
        assertEquals(YearMonth.from(testToday), viewModel.currentMonth.value)
        assertEquals(testToday, viewModel.selectedDate.value)
    }

    @Test
    fun `2 resetToToday resets selectedDate and currentMonth to target date`() {
        val alternateDate = testToday.plusDays(10)
        viewModel.selectDate(alternateDate)
        assertEquals(alternateDate, viewModel.selectedDate.value)

        viewModel.resetToToday(testToday)
        assertEquals(testToday, viewModel.selectedDate.value)
        assertEquals(YearMonth.from(testToday), viewModel.currentMonth.value)
    }

    @Test
    fun `3 Schedule observes canonical selectedDate tasks and ordering`() {
        val tasks = viewModel.getTasksForDate(testToday)
        assertEquals(6, tasks.size)

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

        assertEquals("Review insurance", tasks[4].title)
        assertEquals(null, tasks[4].time)
        assertFalse(tasks[4].isCompleted)

        assertEquals("Prepare documents", tasks[5].title)
        assertEquals(null, tasks[5].time)
        assertFalse(tasks[5].isCompleted)
    }

    @Test
    fun `4 changing selectedDate switches Schedule projection correctly`() {
        assertEquals(6, viewModel.tasks.value.size)

        viewModel.selectDate(testToday.plusDays(1))
        val tomorrowTasks = viewModel.tasks.value
        assertEquals(3, tomorrowTasks.size)
        assertTrue(tomorrowTasks.all { it.date == testToday.plusDays(1) })
    }

    @Test
    fun `5 completing a task moves it to bottom and undoing restores position`() {
        var tasks = viewModel.getTasksForDate(testToday)
        assertEquals("Plan weekly meals", tasks[1].title)
        assertFalse(tasks[1].isCompleted)

        viewModel.toggleCompletion("s2")
        tasks = viewModel.getTasksForDate(testToday)

        val completedTask = tasks.first { it.id == "s2" }
        assertTrue(completedTask.isCompleted)

        val completedList = tasks.filter { it.isCompleted }
        assertEquals("s2", completedList.first().id)
        assertEquals("Plan weekly meals", completedList.first().title)

        viewModel.toggleCompletion("s2")
        tasks = viewModel.getTasksForDate(testToday)

        val restoredTask = tasks[1]
        assertEquals("s2", restoredTask.id)
        assertFalse(restoredTask.isCompleted)
    }

    @Test
    fun `6 completion in Schedule updates Today when same date`() {
        val todayViewModel = TodayViewModel(database = database, plannerTodayProvider = { testToday })

        assertFalse(todayViewModel.tasks.value.first { it.id == "s1" }.isCompleted)

        viewModel.toggleCompletion("s1")

        assertTrue(todayViewModel.tasks.value.first { it.id == "s1" }.isCompleted)
    }

    @Test
    fun `7 create from Schedule automatically appears in Today when date is testToday`() {
        val todayViewModel = TodayViewModel(database = database, plannerTodayProvider = { testToday })

        val newId = viewModel.addTask(
            title = "Created in Schedule",
            date = testToday,
            time = "04:00 PM"
        )

        assertTrue(todayViewModel.tasks.value.any { it.id == newId && it.title == "Created in Schedule" })
    }

    @Test
    fun `8 recurring completion affects only selected occurrence`() {
        val newId = viewModel.addTask(
            title = "Daily Walk",
            date = testToday,
            recurrence = Recurrence.IntervalDays(1)
        )

        viewModel.selectDate(testToday)
        viewModel.toggleCompletion(newId, scheduledDate = testToday)

        // Today's occurrence is completed
        val todayOcc = viewModel.getTasksForDate(testToday).first { it.id == newId }
        assertTrue(todayOcc.isCompleted)

        // Tomorrow's occurrence remains incomplete
        val tomorrowOcc = viewModel.getTasksForDate(testToday.plusDays(1)).first { it.id == newId }
        assertFalse(tomorrowOcc.isCompleted)
    }

    @Test
    fun `9 historical backfill completion uses historical completedDate`() {
        val pastDate = testToday.minusDays(12)
        viewModel.selectDate(pastDate)

        // Task s7 on pastDate
        val task = viewModel.tasks.value.first { it.id == "s7" }
        assertFalse(task.isCompleted)

        viewModel.toggleCompletion("s7", scheduledDate = pastDate)

        val comp = database.completions.value.first { it.taskId == "s7" }
        assertEquals(pastDate, comp.completedDate)
        assertEquals(pastDate, comp.scheduledDate)
    }

    @Test
    fun `10 calendar Important dots derive from canonical state`() {
        assertTrue(viewModel.hasImportantTask(testToday.minusDays(12)))
        assertTrue(viewModel.hasImportantTask(testToday))
        assertTrue(viewModel.hasImportantTask(testToday.plusDays(5)))
        assertTrue(viewModel.hasImportantTask(testToday.plusDays(12)))

        assertFalse(viewModel.hasImportantTask(testToday.minusDays(6)))
        assertFalse(viewModel.hasImportantTask(testToday.minusDays(10)))

        // Toggle important updates dot dynamically
        val datePast = testToday.minusDays(12)
        viewModel.toggleImportant("s7")
        assertFalse(viewModel.hasImportantTask(datePast))
    }

    @Test
    fun `11 month navigation handles month and year boundaries correctly`() {
        val startMonth = YearMonth.from(testToday)
        viewModel.previousMonth()
        assertEquals(startMonth.minusMonths(1), viewModel.currentMonth.value)

        repeat(7) { viewModel.previousMonth() }
        assertEquals(startMonth.minusMonths(8), viewModel.currentMonth.value)

        viewModel.nextMonth()
        assertEquals(startMonth.minusMonths(7), viewModel.currentMonth.value)
    }

    @Test
    fun `12 selectToday updates selectedDate and currentMonth`() {
        val targetDate = LocalDate.of(2027, 3, 15)
        viewModel.selectToday(targetDate)

        assertEquals(targetDate, viewModel.selectedDate.value)
        assertEquals(YearMonth.of(2027, 3), viewModel.currentMonth.value)
    }

    @Test
    fun `13 addTask and updateTask persist note correctly`() {
        val newId = viewModel.addTask(
            title = "Test Schedule Note",
            note = "Schedule note test string",
            date = testToday,
            time = "02:00 PM",
            isImportant = true
        )

        val addedTask = viewModel.tasks.value.first { it.id == newId }
        assertEquals("Test Schedule Note", addedTask.title)
        assertEquals("Schedule note test string", addedTask.note)

        viewModel.updateTask(
            id = newId,
            title = "Updated Schedule Note",
            note = "Modified note string",
            date = testToday,
            time = "03:00 PM",
            isImportant = false
        )

        val updatedTask = viewModel.tasks.value.first { it.id == newId }
        assertEquals("Updated Schedule Note", updatedTask.title)
        assertEquals("Modified note string", updatedTask.note)
    }
}
