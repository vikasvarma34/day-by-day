package com.vikaspokala.daybyday.ui

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.vikaspokala.daybyday.ui.fake.InMemoryDatedTaskStore
import com.vikaspokala.daybyday.ui.screens.later.LaterViewModel
import com.vikaspokala.daybyday.ui.screens.schedule.ScheduleViewModel
import com.vikaspokala.daybyday.ui.screens.today.TodayViewModel

class TaskSaveContractTest {

    private lateinit var store: InMemoryDatedTaskStore
    private lateinit var todayViewModel: TodayViewModel
    private lateinit var scheduleViewModel: ScheduleViewModel
    private lateinit var laterViewModel: LaterViewModel
    private val testToday: LocalDate = LocalDate.of(2026, 8, 19)

    @Before
    fun setUp() {
        store = InMemoryDatedTaskStore(initialDate = testToday)
        todayViewModel = TodayViewModel(taskStore = store, initialDate = testToday)
        scheduleViewModel = ScheduleViewModel(taskStore = store, initialDate = testToday)
        laterViewModel = LaterViewModel()
    }

    private fun handleSaveTask(
        isCreateMode: Boolean,
        taskId: String?,
        sourceScreen: String,
        isLaterTask: Boolean,
        title: String,
        note: String?,
        newDateStr: String?,
        newTimeStr: String?,
        isImp: Boolean
    ) {
        if (isCreateMode) {
            when {
                isLaterTask || sourceScreen == "LATER" -> {
                    laterViewModel.addTask(title = title, note = note, isImportant = isImp)
                }
                sourceScreen == "SCHEDULE" -> {
                    val parsedDate = scheduleViewModel.selectedDate.value
                    scheduleViewModel.addTask(title = title, note = note, date = parsedDate, time = newTimeStr, isImportant = isImp)
                }
                else -> {
                    val parsedDate = todayViewModel.selectedDate.value
                    todayViewModel.addTask(title = title, note = note, date = parsedDate, time = newTimeStr, isImportant = isImp)
                }
            }
        } else {
            taskId?.let { id ->
                when {
                    isLaterTask || sourceScreen == "LATER" -> {
                        laterViewModel.updateTask(id = id, title = title, note = note, isImportant = isImp)
                    }
                    sourceScreen == "SCHEDULE" -> {
                        val parsedDate = scheduleViewModel.selectedDate.value
                        scheduleViewModel.updateTask(id = id, title = title, note = note, date = parsedDate, time = newTimeStr, isImportant = isImp)
                    }
                    else -> {
                        val parsedDate = todayViewModel.selectedDate.value
                        todayViewModel.updateTask(id = id, title = title, note = note, date = parsedDate, time = newTimeStr, isImportant = isImp)
                    }
                }
            }
        }
    }

    @Test
    fun `Today task creation with note passes note to shared store and is visible in Schedule`() {
        handleSaveTask(
            isCreateMode = true,
            taskId = null,
            sourceScreen = "TODAY",
            isLaterTask = false,
            title = "Doctor",
            note = "Bring reports",
            newDateStr = null,
            newTimeStr = "10:00 AM",
            isImp = false
        )

        val createdTaskInToday = todayViewModel.tasks.value.first()
        assertEquals("Doctor", createdTaskInToday.title)
        assertEquals("Bring reports", createdTaskInToday.note)

        // Same task visible in Schedule
        val scheduleTasks = scheduleViewModel.getTasksForDate(testToday)
        assertTrue(scheduleTasks.any { it.title == "Doctor" && it.note == "Bring reports" })
    }

    @Test
    fun `Today task note editing passes updated note and reflects in Schedule`() {
        val existingTask = todayViewModel.tasks.value.first()

        handleSaveTask(
            isCreateMode = false,
            taskId = existingTask.id,
            sourceScreen = "TODAY",
            isLaterTask = false,
            title = existingTask.title,
            note = "Bring reports and prescription",
            newDateStr = null,
            newTimeStr = existingTask.time,
            isImp = existingTask.isImportant
        )

        val updatedTask = todayViewModel.tasks.value.first { it.id == existingTask.id }
        assertEquals("Bring reports and prescription", updatedTask.note)

        val updatedInSchedule = scheduleViewModel.tasks.value.first { it.id == existingTask.id }
        assertEquals("Bring reports and prescription", updatedInSchedule.note)
    }

    @Test
    fun `Schedule task creation with note passes note and is visible in Today for that date`() {
        val targetDate = testToday.plusDays(1)
        scheduleViewModel.selectDate(targetDate)

        handleSaveTask(
            isCreateMode = true,
            taskId = null,
            sourceScreen = "SCHEDULE",
            isLaterTask = false,
            title = "Dentist appointment",
            note = "Check teeth whitening",
            newDateStr = null,
            newTimeStr = "02:00 PM",
            isImp = true
        )

        val createdTask = scheduleViewModel.tasks.value.first()
        assertEquals("Dentist appointment", createdTask.title)
        assertEquals("Check teeth whitening", createdTask.note)

        // Today browsing targetDate sees this task
        todayViewModel.selectDate(targetDate)
        val todayTasksOnTargetDate = todayViewModel.getTasksForDate(targetDate)
        assertTrue(todayTasksOnTargetDate.any { it.title == "Dentist appointment" && it.note == "Check teeth whitening" })
    }
}
