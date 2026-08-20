package com.vikaspokala.daybyday.ui

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.vikaspokala.daybyday.ui.fake.InMemoryPlannerDatabase
import com.vikaspokala.daybyday.ui.screens.later.LaterViewModel
import com.vikaspokala.daybyday.ui.screens.schedule.ScheduleViewModel
import com.vikaspokala.daybyday.ui.screens.today.TodayViewModel
import com.vikaspokala.daybyday.ui.models.Recurrence
import com.vikaspokala.daybyday.ui.models.toDisplayString

class TaskSaveContractTest {

    private lateinit var database: InMemoryPlannerDatabase
    private lateinit var todayViewModel: TodayViewModel
    private lateinit var scheduleViewModel: ScheduleViewModel
    private lateinit var laterViewModel: LaterViewModel
    private val testToday: LocalDate = LocalDate.of(2026, 8, 19)

    @Before
    fun setUp() {
        database = InMemoryPlannerDatabase(referenceDate = testToday)
        todayViewModel = TodayViewModel(database = database, plannerTodayProvider = { testToday })
        scheduleViewModel = ScheduleViewModel(database = database, plannerTodayProvider = { testToday })
        laterViewModel = LaterViewModel(database = database, plannerTodayProvider = { testToday })
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
        newReminderStr: String?,
        newRecurrenceStr: Recurrence?,
        isImp: Boolean
    ) {
        if (isCreateMode) {
            when {
                isLaterTask || sourceScreen == "LATER" -> {
                    laterViewModel.addTask(title = title, note = note, isImportant = isImp)
                }
                sourceScreen == "SCHEDULE" -> {
                    val parsedDate = scheduleViewModel.selectedDate.value
                    scheduleViewModel.addTask(title = title, note = note, date = parsedDate, time = newTimeStr, reminder = newReminderStr, recurrence = newRecurrenceStr, isImportant = isImp)
                }
                else -> {
                    val parsedDate = todayViewModel.selectedDate.value
                    todayViewModel.addTask(title = title, note = note, date = parsedDate, time = newTimeStr, reminder = newReminderStr, recurrence = newRecurrenceStr, isImportant = isImp)
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
                        scheduleViewModel.updateTask(id = id, title = title, note = note, date = parsedDate, time = newTimeStr, reminder = newReminderStr, recurrence = newRecurrenceStr, isImportant = isImp)
                    }
                    else -> {
                        val parsedDate = todayViewModel.selectedDate.value
                        todayViewModel.updateTask(id = id, title = title, note = note, date = parsedDate, time = newTimeStr, reminder = newReminderStr, recurrence = newRecurrenceStr, isImportant = isImp)
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
            newReminderStr = "15 minutes before",
            newRecurrenceStr = null,
            isImp = false
        )

        val createdTaskInToday = todayViewModel.tasks.value.first { it.title == "Doctor" }
        assertEquals("Doctor", createdTaskInToday.title)
        assertEquals("Bring reports", createdTaskInToday.note)
        assertEquals("15 minutes before", createdTaskInToday.reminder)

        // Same task visible in Schedule
        val scheduleTasks = scheduleViewModel.getTasksForDate(testToday)
        assertTrue(scheduleTasks.any { it.title == "Doctor" && it.note == "Bring reports" && it.reminder == "15 minutes before" })
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
            newReminderStr = existingTask.reminder,
            newRecurrenceStr = existingTask.recurrence,
            isImp = existingTask.isImportant
        )

        val updatedTask = todayViewModel.tasks.value.first { it.id == existingTask.id }
        assertEquals("Bring reports and prescription", updatedTask.note)

        val updatedInSchedule = scheduleViewModel.tasks.value.first { it.id == existingTask.id }
        assertEquals("Bring reports and prescription", updatedInSchedule.note)
    }

    @Test
    fun `Schedule task creation with note and reminder passes both to store`() {
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
            newReminderStr = "30 minutes before",
            newRecurrenceStr = Recurrence.IntervalDays(1),
            isImp = true
        )

        val createdTask = scheduleViewModel.tasks.value.first { it.title == "Dentist appointment" }
        assertEquals("Dentist appointment", createdTask.title)
        assertEquals("Check teeth whitening", createdTask.note)
        assertEquals("2:00 PM", createdTask.time)
        assertEquals("30 minutes before", createdTask.reminder)
        assertEquals(Recurrence.IntervalDays(1), createdTask.recurrence)

        // Today browsing targetDate sees this task
        todayViewModel.selectDate(targetDate)
        val todayTasksOnTargetDate = todayViewModel.getTasksForDate(targetDate)
        assertTrue(todayTasksOnTargetDate.any { it.title == "Dentist appointment" && it.reminder == "30 minutes before" && it.recurrence == Recurrence.IntervalDays(1) })
    }

    @Test
    fun `removing task time automatically resets reminder in store`() {
        val existingTask = todayViewModel.tasks.value.first { it.time != null }

        handleSaveTask(
            isCreateMode = false,
            taskId = existingTask.id,
            sourceScreen = "TODAY",
            isLaterTask = false,
            title = existingTask.title,
            note = existingTask.note,
            newDateStr = null,
            newTimeStr = null, // removed time
            newReminderStr = "15 minutes before", // even if attempted
            newRecurrenceStr = existingTask.recurrence,
            isImp = existingTask.isImportant
        )

        val updatedTask = todayViewModel.tasks.value.first { it.id == existingTask.id }
        assertNull(updatedTask.time)
        assertNull(updatedTask.reminder)
    }

    private fun isSaveBlocked(
        isCreateMode: Boolean,
        targetDate: LocalDate?,
        originalDate: LocalDate?,
        originalTime: String? = null,
        newTime: String? = null,
        originalReminder: String? = null,
        newReminder: String? = null,
        originalRecurrence: Recurrence? = null,
        newRecurrence: Recurrence? = null
    ): Boolean {
        if (targetDate == null) return false
        val isEndDateInvalid = newRecurrence?.endDate != null && newRecurrence.endDate!!.isBefore(targetDate)
        if (isEndDateInvalid) return true
        val isDateDirty = targetDate != originalDate
        val isTimeDirty = newTime != originalTime
        val isReminderDirty = newReminder != (if (newTime != null) originalReminder else null)
        val isRecurrenceDirty = newRecurrence != originalRecurrence
        val isScheduleChanged = isDateDirty || isTimeDirty || isReminderDirty || isRecurrenceDirty
        val isPastDate = targetDate.isBefore(testToday)
        return isPastDate && (isCreateMode || isScheduleChanged)
    }

    @Test
    fun `1 historical task + title edit is allowed`() {
        val pastDate = testToday.minusDays(1)
        val taskId = scheduleViewModel.addTask(title = "Original Title", note = "Note", date = pastDate, isImportant = false)
        val original = scheduleViewModel.getTasksForDate(pastDate).first { it.id == taskId }

        val blocked = isSaveBlocked(
            isCreateMode = false,
            targetDate = pastDate,
            originalDate = original.date,
            originalTime = original.time,
            newTime = original.time,
            originalReminder = original.reminder,
            newReminder = original.reminder,
            originalRecurrence = original.recurrence,
            newRecurrence = original.recurrence
        )
        assertFalse("Title-only change on historical task must not be blocked", blocked)

        scheduleViewModel.updateTask(id = taskId, title = "Updated Title", note = original.note, date = original.date, isImportant = original.isImportant)
        val updated = scheduleViewModel.getTasksForDate(pastDate).first { it.id == taskId }
        assertEquals("Updated Title", updated.title)
    }

    @Test
    fun `2 historical task + note edit is allowed`() {
        val pastDate = testToday.minusDays(1)
        val taskId = scheduleViewModel.addTask(title = "Past Task", note = "Old Note", date = pastDate, isImportant = false)
        val original = scheduleViewModel.getTasksForDate(pastDate).first { it.id == taskId }

        val blocked = isSaveBlocked(
            isCreateMode = false,
            targetDate = pastDate,
            originalDate = original.date,
            originalTime = original.time,
            newTime = original.time,
            originalReminder = original.reminder,
            newReminder = original.reminder,
            originalRecurrence = original.recurrence,
            newRecurrence = original.recurrence
        )
        assertFalse("Note-only change on historical task must not be blocked", blocked)

        scheduleViewModel.updateTask(id = taskId, title = original.title, note = "Updated Note", date = original.date, isImportant = original.isImportant)
        val updated = scheduleViewModel.getTasksForDate(pastDate).first { it.id == taskId }
        assertEquals("Updated Note", updated.note)
    }

    @Test
    fun `3 historical task + Important edit is allowed`() {
        val pastDate = testToday.minusDays(1)
        val taskId = scheduleViewModel.addTask(title = "Past Task", note = "Note", date = pastDate, isImportant = false)
        val original = scheduleViewModel.getTasksForDate(pastDate).first { it.id == taskId }

        val blocked = isSaveBlocked(
            isCreateMode = false,
            targetDate = pastDate,
            originalDate = original.date,
            originalTime = original.time,
            newTime = original.time,
            originalReminder = original.reminder,
            newReminder = original.reminder,
            originalRecurrence = original.recurrence,
            newRecurrence = original.recurrence
        )
        assertFalse("Important-only change on historical task must not be blocked", blocked)

        scheduleViewModel.updateTask(id = taskId, title = original.title, note = original.note, date = original.date, isImportant = true)
        val updated = scheduleViewModel.getTasksForDate(pastDate).first { it.id == taskId }
        assertTrue(updated.isImportant)
    }

    @Test
    fun `4 historical task + time change is blocked`() {
        val pastDate = testToday.minusDays(1)
        val taskId = scheduleViewModel.addTask(title = "Past Task", note = "Note", date = pastDate, time = "10:00 AM")
        val original = scheduleViewModel.getTasksForDate(pastDate).first { it.id == taskId }

        val blocked = isSaveBlocked(
            isCreateMode = false,
            targetDate = pastDate,
            originalDate = original.date,
            originalTime = original.time,
            newTime = "11:00 AM",
            originalReminder = original.reminder,
            newReminder = original.reminder,
            originalRecurrence = original.recurrence,
            newRecurrence = original.recurrence
        )
        assertTrue("Time change on historical task must be blocked", blocked)
    }

    @Test
    fun `5 historical task + reminder change is blocked`() {
        val pastDate = testToday.minusDays(1)
        val taskId = scheduleViewModel.addTask(title = "Past Task", note = "Note", date = pastDate, time = "10:00 AM")
        val original = scheduleViewModel.getTasksForDate(pastDate).first { it.id == taskId }

        val blocked = isSaveBlocked(
            isCreateMode = false,
            targetDate = pastDate,
            originalDate = original.date,
            originalTime = original.time,
            newTime = original.time,
            originalReminder = original.reminder,
            newReminder = "30 minutes before",
            originalRecurrence = original.recurrence,
            newRecurrence = original.recurrence
        )
        assertTrue("Reminder change on historical task must be blocked", blocked)
    }

    @Test
    fun `6 historical task + recurrence change is blocked`() {
        val pastDate = testToday.minusDays(1)
        val taskId = scheduleViewModel.addTask(title = "Past Task", note = "Note", date = pastDate, recurrence = null)
        val original = scheduleViewModel.getTasksForDate(pastDate).first { it.id == taskId }

        val blocked = isSaveBlocked(
            isCreateMode = false,
            targetDate = pastDate,
            originalDate = original.date,
            originalTime = original.time,
            newTime = original.time,
            originalReminder = original.reminder,
            newReminder = original.reminder,
            originalRecurrence = original.recurrence,
            newRecurrence = Recurrence.IntervalDays(1)
        )
        assertTrue("Recurrence change on historical task must be blocked", blocked)
    }

    @Test
    fun `7 historical task moved to another past date is blocked`() {
        val pastDate = testToday.minusDays(1)
        val earlierDate = testToday.minusDays(2)
        val taskId = scheduleViewModel.addTask(title = "Past Task", note = "Note", date = pastDate)
        val original = scheduleViewModel.getTasksForDate(pastDate).first { it.id == taskId }

        val blocked = isSaveBlocked(
            isCreateMode = false,
            targetDate = earlierDate,
            originalDate = original.date,
            originalTime = original.time,
            newTime = original.time,
            originalReminder = original.reminder,
            newReminder = original.reminder,
            originalRecurrence = original.recurrence,
            newRecurrence = original.recurrence
        )
        assertTrue("Moving historical task to another past date must be blocked", blocked)
    }

    @Test
    fun `8 historical task moved to today or future is allowed`() {
        val pastDate = testToday.minusDays(1)
        val taskId = scheduleViewModel.addTask(title = "Past Task", note = "Note", date = pastDate)
        val original = scheduleViewModel.getTasksForDate(pastDate).first { it.id == taskId }

        val blockedToday = isSaveBlocked(
            isCreateMode = false,
            targetDate = testToday,
            originalDate = original.date,
            originalTime = original.time,
            newTime = original.time,
            originalReminder = original.reminder,
            newReminder = original.reminder,
            originalRecurrence = original.recurrence,
            newRecurrence = original.recurrence
        )
        assertFalse("Moving historical task to today must be allowed", blockedToday)

        val blockedFuture = isSaveBlocked(
            isCreateMode = false,
            targetDate = testToday.plusDays(3),
            originalDate = original.date,
            originalTime = original.time,
            newTime = original.time,
            originalReminder = original.reminder,
            newReminder = original.reminder,
            originalRecurrence = original.recurrence,
            newRecurrence = original.recurrence
        )
        assertFalse("Moving historical task to future must be allowed", blockedFuture)
    }

    @Test
    fun `9 new dated task in past is blocked while today and future are allowed`() {
        val pastDate = testToday.minusDays(1)
        val blockedPast = isSaveBlocked(isCreateMode = true, targetDate = pastDate, originalDate = null)
        assertTrue("Creating new dated task in past must be blocked", blockedPast)

        val blockedToday = isSaveBlocked(isCreateMode = true, targetDate = testToday, originalDate = null)
        assertFalse("Creating new dated task today must be allowed", blockedToday)

        val blockedFuture = isSaveBlocked(isCreateMode = true, targetDate = testToday.plusDays(1), originalDate = null)
        assertFalse("Creating new dated task in future must be allowed", blockedFuture)

        val blockedLater = isSaveBlocked(isCreateMode = true, targetDate = null, originalDate = null)
        assertFalse("Creating unscheduled Later task must be allowed", blockedLater)
    }

    @Test
    fun `reopening task from database restores exact recurrence and optional end date`() {
        val endDate = testToday.plusDays(30)
        val recurrence = Recurrence.IntervalDays(intervalDays = 3, endDate = endDate)

        handleSaveTask(
            isCreateMode = true,
            taskId = null,
            sourceScreen = "TODAY",
            isLaterTask = false,
            title = "Water plants",
            note = "Check soil",
            newDateStr = null,
            newTimeStr = "08:00 AM",
            newReminderStr = "At task time",
            newRecurrenceStr = recurrence,
            isImp = false
        )

        val taskInStore = todayViewModel.tasks.value.first { it.title == "Water plants" }
        assertEquals(recurrence, taskInStore.recurrence)

        // When reopening as Screen.Task (e.g. from TodayScreen/ScheduleScreen)
        val reopenedScreenTask = com.vikaspokala.daybyday.ui.navigation.Screen.Task(
            isCreateMode = false,
            taskId = taskInStore.id,
            initialTitle = taskInStore.title,
            initialNote = taskInStore.note,
            initialIsImportant = taskInStore.isImportant,
            dateString = "Wednesday, 19 August",
            timeString = taskInStore.time,
            reminderString = taskInStore.reminder,
            recurrence = taskInStore.recurrence,
            isLaterTask = false,
            sourceScreen = "TODAY"
        )

        assertEquals(recurrence, reopenedScreenTask.recurrence)
        val interval = reopenedScreenTask.recurrence as Recurrence.IntervalDays
        assertEquals(3, interval.intervalDays)
        assertEquals(endDate, interval.endDate)
    }

    @Test
    fun `Recurrence toDisplayString correctly formats all V1 options and optional end dates`() {
        val anchorDate = LocalDate.of(2026, 8, 19) // Wednesday (3)
        val endDate = LocalDate.of(2026, 8, 31)

        val once: Recurrence = Recurrence.Once
        assertEquals("Does not repeat", once.toDisplayString(anchorDate))

        val daily: Recurrence = Recurrence.IntervalDays(1)
        assertEquals("Every day", daily.toDisplayString(anchorDate))

        val every3Days: Recurrence = Recurrence.IntervalDays(3)
        assertEquals("Every 3 days", every3Days.toDisplayString(anchorDate))

        val every4DaysWithEnd: Recurrence = Recurrence.IntervalDays(4, endDate = endDate)
        assertEquals("Every 4 days, until 31 Aug", every4DaysWithEnd.toDisplayString(anchorDate))

        // Weekdays matching anchor day
        val everyWed: Recurrence = Recurrence.Weekdays(setOf(3))
        assertEquals("Every Wednesday", everyWed.toDisplayString(anchorDate))

        // Custom Weekdays: Mon(1), Wed(3), Fri(5)
        val customWeekdays: Recurrence = Recurrence.Weekdays(setOf(1, 3, 5))
        assertEquals("Mon, Wed, Fri", customWeekdays.toDisplayString(anchorDate))

        val customWithEnd: Recurrence = Recurrence.Weekdays(setOf(1, 3, 5), endDate = endDate)
        assertEquals("Mon, Wed, Fri, until 31 Aug", customWithEnd.toDisplayString(anchorDate))
    }

    @Test
    fun `1 start 25 Aug + end 24 Aug is invalid`() {
        val start = LocalDate.of(2026, 8, 25)
        val end = LocalDate.of(2026, 8, 24)
        val recurrence = Recurrence.IntervalDays(1, endDate = end)

        val blocked = isSaveBlocked(isCreateMode = true, targetDate = start, originalDate = null, newRecurrence = recurrence)
        assertTrue("End date before start date must be blocked", blocked)
    }

    @Test
    fun `2 start 25 Aug + end 25 Aug is valid`() {
        val start = LocalDate.of(2026, 8, 25)
        val end = LocalDate.of(2026, 8, 25)
        val recurrence = Recurrence.IntervalDays(1, endDate = end)

        val blocked = isSaveBlocked(isCreateMode = true, targetDate = start, originalDate = null, newRecurrence = recurrence)
        assertFalse("End date equal to start date must be allowed", blocked)
    }

    @Test
    fun `3 start 25 Aug + end 31 Aug is valid`() {
        val start = LocalDate.of(2026, 8, 25)
        val end = LocalDate.of(2026, 8, 31)
        val recurrence = Recurrence.IntervalDays(2, endDate = end)

        val blocked = isSaveBlocked(isCreateMode = true, targetDate = start, originalDate = null, newRecurrence = recurrence)
        assertFalse("End date after start date must be allowed", blocked)
    }

    @Test
    fun `4 existing valid end date becomes earlier than newly changed start date blocks Save`() {
        val originalStart = LocalDate.of(2026, 8, 25)
        val originalEnd = LocalDate.of(2026, 8, 31)
        val recurrence = Recurrence.IntervalDays(1, endDate = originalEnd)

        val newStart = LocalDate.of(2026, 9, 5) // Later than originalEnd (Aug 31)
        val blocked = isSaveBlocked(
            isCreateMode = false,
            targetDate = newStart,
            originalDate = originalStart,
            originalRecurrence = recurrence,
            newRecurrence = recurrence
        )
        assertTrue("Changing start date beyond end date must block Save", blocked)
    }

    @Test
    fun `5 removing invalid end date allows Save`() {
        val originalStart = LocalDate.of(2026, 8, 25)
        val originalEnd = LocalDate.of(2026, 8, 31)
        val originalRecurrence = Recurrence.IntervalDays(1, endDate = originalEnd)

        val newStart = LocalDate.of(2026, 9, 5)
        val clearedEndRecurrence = Recurrence.IntervalDays(1, endDate = null) // Removed end date

        val blocked = isSaveBlocked(
            isCreateMode = false,
            targetDate = newStart,
            originalDate = originalStart,
            originalRecurrence = originalRecurrence,
            newRecurrence = clearedEndRecurrence
        )
        assertFalse("Removing invalid end date must allow Save with new start date", blocked)
    }

    @Test
    fun `6 UI validation blocks invalid end date and database stores recurrence without silent mutations`() {
        val start = LocalDate.of(2026, 8, 25)
        val invalidEnd = LocalDate.of(2026, 8, 20)
        val invalidRecurrence = Recurrence.IntervalDays(1, endDate = invalidEnd)

        val blocked = isSaveBlocked(isCreateMode = true, targetDate = start, originalDate = null, newRecurrence = invalidRecurrence)
        assertTrue("UI validation must block saving invalid end date before start date", blocked)

        // Store preserves valid recurrence without hidden mutation
        val validRecurrence = Recurrence.IntervalDays(1, endDate = LocalDate.of(2026, 8, 30))
        val taskId = scheduleViewModel.addTask(
            title = "Task with valid end",
            date = start,
            recurrence = validRecurrence
        )

        val storedTask = scheduleViewModel.getTasksForDate(start).first { it.id == taskId }
        assertEquals(validRecurrence, storedTask.recurrence)
    }
}
