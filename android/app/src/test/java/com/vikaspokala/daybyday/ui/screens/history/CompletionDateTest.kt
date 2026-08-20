package com.vikaspokala.daybyday.ui.screens.history

import java.time.LocalDate
import com.vikaspokala.daybyday.ui.fake.InMemoryPlannerDatabase
import com.vikaspokala.daybyday.ui.models.Recurrence
import com.vikaspokala.daybyday.ui.screens.later.LaterViewModel
import com.vikaspokala.daybyday.ui.screens.today.TodayViewModel
import com.vikaspokala.daybyday.ui.screens.schedule.ScheduleViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CompletionDateTest {

    private val actualToday: LocalDate = LocalDate.now()
    private lateinit var database: InMemoryPlannerDatabase
    private lateinit var laterViewModel: LaterViewModel
    private lateinit var todayViewModel: TodayViewModel
    private lateinit var scheduleViewModel: ScheduleViewModel
    private lateinit var historyViewModel: HistoryViewModel

    @Before
    fun setUp() {
        database = InMemoryPlannerDatabase(referenceDate = actualToday)
        laterViewModel = LaterViewModel(database = database, plannerTodayProvider = { actualToday })
        todayViewModel = TodayViewModel(database = database, plannerTodayProvider = { actualToday })
        scheduleViewModel = ScheduleViewModel(database = database, plannerTodayProvider = { actualToday })
        historyViewModel = HistoryViewModel(database = database, referenceDate = actualToday)
    }

    @Test
    fun `1 today = 20 Aug, scheduledDate = 18 Aug, complete historical backfill - completedDate = 18 Aug`() {
        val scheduledDate = actualToday.minusDays(2)
        val taskId = todayViewModel.addTask(title = "Task", date = scheduledDate, isImportant = true)
        
        // Runtime completion via TodayViewModel on historical date completes with completedDate = scheduledDate
        todayViewModel.toggleCompletion(taskId, scheduledDate = scheduledDate)
        
        val comp = database.completions.value.first { it.taskId == taskId }
        assertEquals("completedDate must be historical scheduledDate for backfill", scheduledDate, comp.completedDate)
        assertEquals("scheduledDate remains unchanged", scheduledDate, comp.scheduledDate)
    }

    @Test
    fun `2 today = 20 Aug, scheduledDate = 27 Aug, complete now - completedDate = 20 Aug`() {
        val scheduledDate = actualToday.plusDays(7)
        val taskId = todayViewModel.addTask(title = "Future Task", date = scheduledDate, isImportant = true)
        
        todayViewModel.toggleCompletion(taskId, scheduledDate = scheduledDate)
        
        val comp = database.completions.value.first { it.taskId == taskId }
        assertEquals(actualToday, comp.completedDate)
    }

    @Test
    fun `3 direct Later completion - completedDate = today`() {
        val taskId = laterViewModel.addTask(title = "Later Task", note = null, isImportant = true)
        
        laterViewModel.toggleCompletion(taskId)
        
        val historyTasks = database.getHistoryTasks(plannerToday = actualToday.plusDays(1))
        val completedItem = historyTasks.first { it.id == taskId }
        
        assertEquals(actualToday, completedItem.completedDate)
    }

    @Test
    fun `4 historical seeded completion retains its explicit old completedDate`() {
        val historyTasks = database.getHistoryTasks(plannerToday = actualToday)
        val seededTask = historyTasks.find { it.id == "d_m2_1" }
        
        if (seededTask != null) {
            assertEquals(actualToday.minusDays(2), seededTask.completedDate)
        }
    }

    @Test
    fun `5 important ONCE completed today does not appear in History today`() {
        val taskId = todayViewModel.addTask(title = "Task", date = actualToday, isImportant = true)
        todayViewModel.toggleCompletion(taskId, scheduledDate = actualToday)
        
        val grouped = historyViewModel.getFilteredGroupedHistory()
        assertFalse("Must not appear in history grouped view today", grouped.values.flatten().any { it.id == taskId })
    }

    @Test
    fun `6 scheduledDate remains unchanged by completion`() {
        val scheduledDate = actualToday.minusDays(2)
        val taskId = todayViewModel.addTask(title = "Task", date = scheduledDate, isImportant = true)
        
        todayViewModel.toggleCompletion(taskId, scheduledDate = scheduledDate)
        
        val schedule = database.schedules.value.first { it.taskId == taskId }
        assertEquals(scheduledDate, schedule.startDate)
    }
}
