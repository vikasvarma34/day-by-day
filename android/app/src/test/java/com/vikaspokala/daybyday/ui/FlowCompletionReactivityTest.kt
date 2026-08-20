package com.vikaspokala.daybyday.ui

import java.time.LocalDate
import com.vikaspokala.daybyday.ui.fake.InMemoryPlannerDatabase
import com.vikaspokala.daybyday.ui.models.Recurrence
import com.vikaspokala.daybyday.ui.screens.schedule.ScheduleTaskItem
import com.vikaspokala.daybyday.ui.screens.schedule.ScheduleViewModel
import com.vikaspokala.daybyday.ui.screens.today.TodayViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FlowCompletionReactivityTest {

    private lateinit var database: InMemoryPlannerDatabase
    private lateinit var todayViewModel: TodayViewModel
    private lateinit var scheduleViewModel: ScheduleViewModel
    private val testToday: LocalDate = LocalDate.of(2026, 8, 19)

    @Before
    fun setUp() {
        database = InMemoryPlannerDatabase(referenceDate = testToday)
        todayViewModel = TodayViewModel(database = database, plannerTodayProvider = { testToday })
        scheduleViewModel = ScheduleViewModel(database = database, plannerTodayProvider = { testToday })
    }

    @Test
    fun `1 Today completion emits isCompleted=true immediately`() = runBlocking {
        val taskId = todayViewModel.addTask(
            title = "Water Plants",
            date = testToday,
            time = "09:00 AM"
        )

        val emissions = mutableListOf<List<ScheduleTaskItem>>()
        val job = CoroutineScope(Dispatchers.Unconfined).launch {
            todayViewModel.tasks.collect { emissions.add(it) }
        }

        // Initial emission has incomplete task
        val initialList = emissions.last()
        assertFalse(initialList.first { it.id == taskId }.isCompleted)

        // Complete the task
        todayViewModel.toggleCompletion(taskId)

        // Flow collector must have received a new emission immediately
        val updatedList = emissions.last()
        assertTrue(updatedList.first { it.id == taskId }.isCompleted)

        job.cancel()
    }

    @Test
    fun `2 Today undo emits isCompleted=false immediately`() = runBlocking {
        val taskId = todayViewModel.addTask(
            title = "Morning Coffee",
            date = testToday,
            time = "08:00 AM"
        )
        todayViewModel.toggleCompletion(taskId)

        val emissions = mutableListOf<List<ScheduleTaskItem>>()
        val job = CoroutineScope(Dispatchers.Unconfined).launch {
            todayViewModel.tasks.collect { emissions.add(it) }
        }

        // Initial collected state is completed
        assertTrue(emissions.last().first { it.id == taskId }.isCompleted)

        // Undo completion
        todayViewModel.toggleCompletion(taskId)

        // Flow collector must receive new emission with isCompleted = false
        val updatedList = emissions.last()
        assertFalse(updatedList.first { it.id == taskId }.isCompleted)

        job.cancel()
    }

    @Test
    fun `3 Schedule completion emits immediately without selected-date or navigation change`() = runBlocking {
        val targetDate = testToday.plusDays(2)
        val taskId = scheduleViewModel.addTask(
            title = "Doctor Appointment",
            date = targetDate,
            time = "02:00 PM"
        )
        scheduleViewModel.selectDate(targetDate)

        val emissions = mutableListOf<List<ScheduleTaskItem>>()
        val job = CoroutineScope(Dispatchers.Unconfined).launch {
            scheduleViewModel.tasks.collect { emissions.add(it) }
        }

        assertEquals(1, emissions.size)
        assertFalse(emissions.last().first { it.id == taskId }.isCompleted)

        // Complete the occurrence while staying on the same selectedDate without navigating
        scheduleViewModel.toggleCompletion(taskId, scheduledDate = targetDate)

        // Verifying emission happened immediately
        assertTrue(emissions.size >= 2)
        assertTrue(emissions.last().first { it.id == taskId }.isCompleted)

        job.cancel()
    }

    @Test
    fun `4 recurring occurrence completion emits immediately`() = runBlocking {
        val recurrence = Recurrence.IntervalDays(1) // Daily
        val taskId = scheduleViewModel.addTask(
            title = "Daily Walk",
            date = testToday,
            recurrence = recurrence
        )
        scheduleViewModel.selectDate(testToday)

        val emissions = mutableListOf<List<ScheduleTaskItem>>()
        val job = CoroutineScope(Dispatchers.Unconfined).launch {
            scheduleViewModel.tasks.collect { emissions.add(it) }
        }

        assertFalse(emissions.last().first { it.id == taskId }.isCompleted)

        // Complete today's occurrence
        scheduleViewModel.toggleCompletion(taskId, scheduledDate = testToday)

        // Flow collector receives immediate emission with isCompleted=true
        assertTrue(emissions.last().first { it.id == taskId }.isCompleted)

        job.cancel()
    }

    @Test
    fun `5 completing recurring occurrence A does not change occurrence B`() = runBlocking {
        val recurrence = Recurrence.IntervalDays(1) // Daily
        val dateA = testToday
        val dateB = testToday.plusDays(1)

        val taskId = scheduleViewModel.addTask(
            title = "Daily Stretching",
            date = dateA,
            recurrence = recurrence
        )

        // Collect date A
        scheduleViewModel.selectDate(dateA)
        val emissionsA = mutableListOf<List<ScheduleTaskItem>>()
        val jobA = CoroutineScope(Dispatchers.Unconfined).launch {
            scheduleViewModel.tasks.collect { emissionsA.add(it) }
        }

        // Complete date A
        scheduleViewModel.toggleCompletion(taskId, scheduledDate = dateA)
        assertTrue(emissionsA.last().first { it.id == taskId }.isCompleted)
        jobA.cancel()

        // Collect date B
        scheduleViewModel.selectDate(dateB)
        val emissionsB = mutableListOf<List<ScheduleTaskItem>>()
        val jobB = CoroutineScope(Dispatchers.Unconfined).launch {
            scheduleViewModel.tasks.collect { emissionsB.add(it) }
        }

        // Occurrence B remains incomplete
        assertFalse(emissionsB.last().first { it.id == taskId }.isCompleted)
        jobB.cancel()
    }

    @Test
    fun `6 historical occurrence completion emits immediately`() = runBlocking {
        val pastDate = testToday.minusDays(2)
        val taskId = scheduleViewModel.addTask(
            title = "Historical Workout",
            date = pastDate,
            time = "06:00 PM"
        )
        scheduleViewModel.selectDate(pastDate)

        val emissions = mutableListOf<List<ScheduleTaskItem>>()
        val job = CoroutineScope(Dispatchers.Unconfined).launch {
            scheduleViewModel.tasks.collect { emissions.add(it) }
        }

        assertFalse(emissions.last().first { it.id == taskId }.isCompleted)

        // Backfill completion on historical date
        scheduleViewModel.toggleCompletion(taskId, scheduledDate = pastDate)

        // Flow emits immediately
        assertTrue(emissions.last().first { it.id == taskId }.isCompleted)

        job.cancel()
    }

    @Test
    fun `7 no navigation or reconstruction of ViewModel is required`() = runBlocking {
        val taskId = todayViewModel.addTask(
            title = "Static ViewModel Test",
            date = testToday
        )

        var emittedCount = 0
        var lastCompletedState = false
        val job = CoroutineScope(Dispatchers.Unconfined).launch {
            todayViewModel.tasks.collect { list ->
                emittedCount++
                val item = list.firstOrNull { it.id == taskId }
                if (item != null) {
                    lastCompletedState = item.isCompleted
                }
            }
        }

        assertEquals(false, lastCompletedState)
        val countInitial = emittedCount

        // Complete without ViewModel re-creation or screen navigation
        todayViewModel.toggleCompletion(taskId)
        assertEquals(true, lastCompletedState)
        assertTrue(emittedCount > countInitial)

        // Undo without ViewModel re-creation or screen navigation
        todayViewModel.toggleCompletion(taskId)
        assertEquals(false, lastCompletedState)

        job.cancel()
    }
}
