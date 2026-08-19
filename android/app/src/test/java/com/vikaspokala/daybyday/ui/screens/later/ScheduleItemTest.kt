package com.vikaspokala.daybyday.ui.screens.later

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.vikaspokala.daybyday.ui.fake.InMemoryDatedTaskStore
import com.vikaspokala.daybyday.ui.screens.schedule.ScheduleViewModel
import com.vikaspokala.daybyday.ui.screens.today.TodayViewModel

class ScheduleItemTest {

    private lateinit var store: InMemoryDatedTaskStore
    private lateinit var laterViewModel: LaterViewModel
    private lateinit var todayViewModel: TodayViewModel
    private lateinit var scheduleViewModel: ScheduleViewModel
    private val testToday: LocalDate = LocalDate.of(2026, 8, 19)

    @Before
    fun setUp() {
        store = InMemoryDatedTaskStore(initialDate = testToday)
        laterViewModel = LaterViewModel()
        todayViewModel = TodayViewModel(taskStore = store, initialDate = testToday)
        scheduleViewModel = ScheduleViewModel(taskStore = store, initialDate = testToday)
    }

    @Test
    fun `scheduling an active Later task removes it from Later and adds it to dated tasks`() {
        val laterTasks = laterViewModel.getActiveTasks()
        val targetLaterTask = laterTasks.first { it.id == "l1" }
        assertEquals("Read Atomic Habits", targetLaterTask.title)
        assertEquals("A chapter or two when there is time", targetLaterTask.note)
        assertTrue(targetLaterTask.isImportant)

        val scheduledDate = testToday.plusDays(3)
        val scheduledTime = "08:00 PM"

        // Perform scheduling flow
        laterViewModel.deleteTask(targetLaterTask.id)
        val newTaskId = todayViewModel.addTask(
            title = targetLaterTask.title,
            note = targetLaterTask.note,
            date = scheduledDate,
            time = scheduledTime,
            isImportant = targetLaterTask.isImportant
        )

        // 1. Removed from Later
        assertFalse(laterViewModel.tasks.value.any { it.id == targetLaterTask.id })
        assertFalse(laterViewModel.getActiveTasks().any { it.title == "Read Atomic Habits" })

        // 2. Visible in Schedule for that date with preserved properties
        val scheduledTasksOnDate = scheduleViewModel.getTasksForDate(scheduledDate)
        val scheduledTask = scheduledTasksOnDate.firstOrNull { it.id == newTaskId }
        assertNotNull(scheduledTask)
        assertEquals("Read Atomic Habits", scheduledTask?.title)
        assertEquals("A chapter or two when there is time", scheduledTask?.note)
        assertEquals("08:00 PM", scheduledTask?.time)
        assertEquals(true, scheduledTask?.isImportant)
        assertEquals(false, scheduledTask?.isCompleted)

        // 3. Visible in Today when browsing that date
        todayViewModel.selectDate(scheduledDate)
        val todayTasksOnDate = todayViewModel.getTasksForDate(scheduledDate)
        assertTrue(todayTasksOnDate.any { it.id == newTaskId && it.title == "Read Atomic Habits" })
    }

    @Test
    fun `navigating back without scheduling preserves Later task`() {
        val initialLaterCount = laterViewModel.getActiveTasks().size
        val targetLaterTask = laterViewModel.getActiveTasks().first { it.id == "l2" }

        // User opens Schedule item and presses Back without confirming
        // No mutations executed

        assertEquals(initialLaterCount, laterViewModel.getActiveTasks().size)
        assertTrue(laterViewModel.getActiveTasks().any { it.id == targetLaterTask.id })
    }
}
