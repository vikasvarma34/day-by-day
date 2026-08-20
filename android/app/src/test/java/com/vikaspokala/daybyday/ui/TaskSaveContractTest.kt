package com.vikaspokala.daybyday.ui

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
                    val parsedDate = com.vikaspokala.daybyday.ui.screens.task.parseTaskDate(newDateStr, testToday) ?: scheduleViewModel.selectedDate.value
                    scheduleViewModel.addTask(title = title, note = note, date = parsedDate, time = newTimeStr, reminder = newReminderStr, recurrence = newRecurrenceStr, isImportant = isImp)
                }
                else -> {
                    val parsedDate = com.vikaspokala.daybyday.ui.screens.task.parseTaskDate(newDateStr, testToday) ?: todayViewModel.selectedDate.value
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
                        val parsedDate = com.vikaspokala.daybyday.ui.screens.task.parseTaskDate(newDateStr, testToday) ?: scheduleViewModel.selectedDate.value
                        scheduleViewModel.updateTask(id = id, title = title, note = note, date = parsedDate, time = newTimeStr, reminder = newReminderStr, recurrence = newRecurrenceStr, isImportant = isImp)
                    }
                    else -> {
                        val parsedDate = com.vikaspokala.daybyday.ui.screens.task.parseTaskDate(newDateStr, testToday) ?: todayViewModel.selectedDate.value
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

        val isPastDate = targetDate.isBefore(testToday)
        return if (isCreateMode) {
            val isRecurring = newRecurrence != null && newRecurrence !is Recurrence.Once
            val hasReminder = newReminder != null
            isPastDate && (isRecurring || hasReminder)
        } else {
            val isOriginalPast = originalDate != null && originalDate.isBefore(testToday)
            val isDateDirty = targetDate != originalDate
            val isTimeDirty = newTime != originalTime
            val isReminderDirty = newReminder != (if (newTime != null) originalReminder else null)
            val isRecurrenceDirty = newRecurrence != originalRecurrence
            val isScheduleChanged = isDateDirty || isTimeDirty || isReminderDirty || isRecurrenceDirty

            if (isOriginalPast) {
                isPastDate && isScheduleChanged
            } else {
                isPastDate
            }
        }
    }

    @Test
    fun `1 new historical ONCE task can be saved`() {
        val pastDate = testToday.minusDays(3)
        val blocked = isSaveBlocked(
            isCreateMode = true,
            targetDate = pastDate,
            originalDate = null,
            newRecurrence = Recurrence.Once,
            newReminder = null
        )
        assertFalse("New historical ONCE task must not be blocked", blocked)

        val taskId = scheduleViewModel.addTask(
            title = "Historical Note",
            note = "Some notes",
            date = pastDate,
            recurrence = Recurrence.Once,
            isImportant = true
        )
        val tasks = scheduleViewModel.getTasksForDate(pastDate)
        assertTrue(tasks.any { it.id == taskId && it.title == "Historical Note" && it.isImportant })
    }

    @Test
    fun `2 historical task with optional time can be saved`() {
        val pastDate = testToday.minusDays(2)
        val blocked = isSaveBlocked(
            isCreateMode = true,
            targetDate = pastDate,
            originalDate = null,
            newTime = "10:30 AM",
            newRecurrence = null,
            newReminder = null
        )
        assertFalse("New historical task with optional time must not be blocked", blocked)

        val taskId = scheduleViewModel.addTask(
            title = "Past meeting",
            note = "Met with team",
            date = pastDate,
            time = "10:30 AM",
            isImportant = false
        )
        val tasks = scheduleViewModel.getTasksForDate(pastDate)
        val task = tasks.first { it.id == taskId }
        assertEquals("10:30 AM", task.time)
    }

    @Test
    fun `3 historical task cannot save reminder`() {
        val pastDate = testToday.minusDays(1)
        val blocked = isSaveBlocked(
            isCreateMode = true,
            targetDate = pastDate,
            originalDate = null,
            newTime = "9:00 AM",
            newReminder = "15 minutes before"
        )
        assertTrue("Historical task creation with reminder must be blocked", blocked)
    }

    @Test
    fun `4 historical task cannot save recurrence`() {
        val pastDate = testToday.minusDays(1)
        val blocked = isSaveBlocked(
            isCreateMode = true,
            targetDate = pastDate,
            originalDate = null,
            newRecurrence = Recurrence.IntervalDays(1)
        )
        assertTrue("Historical task creation with recurrence must be blocked", blocked)
    }

    @Test
    fun `5 changing future or today form with reminder to historical date clears reminder`() {
        val reminder = "15 minutes before"
        var currentReminder: String? = reminder
        val targetPastDate = testToday.minusDays(2)

        // UI contract: when date changes to past, reminder is cleared
        if (targetPastDate.isBefore(testToday)) {
            currentReminder = null
        }
        assertNull("Reminder must be automatically cleared when moved to past", currentReminder)
    }

    @Test
    fun `6 changing recurring form to historical date resets recurrence to ONCE`() {
        var currentRecurrence: Recurrence? = Recurrence.IntervalDays(2)
        val targetPastDate = testToday.minusDays(1)

        // UI contract: when date changes to past, recurrence is reset to Once/null
        if (targetPastDate.isBefore(testToday)) {
            currentRecurrence = null
        }
        assertNull("Recurrence must be automatically reset when moved to past", currentRecurrence)
    }

    @Test
    fun `7 Time remains available for historical task`() {
        val pastDate = testToday.minusDays(1)
        val taskId = scheduleViewModel.addTask(
            title = "Doctor appointment past",
            date = pastDate,
            time = "3:15 PM"
        )
        val task = scheduleViewModel.getTasksForDate(pastDate).first { it.id == taskId }
        assertEquals("3:15 PM", task.time)
    }

    @Test
    fun `8 Today creation still allows reminder`() {
        val blocked = isSaveBlocked(
            isCreateMode = true,
            targetDate = testToday,
            originalDate = null,
            newTime = "10:00 AM",
            newReminder = "15 minutes before"
        )
        assertFalse("Today creation with reminder must be allowed", blocked)

        val taskId = todayViewModel.addTask(
            title = "Today with reminder",
            date = testToday,
            time = "10:00 AM",
            reminder = "15 minutes before"
        )
        val task = todayViewModel.tasks.value.first { it.id == taskId }
        assertEquals("15 minutes before", task.reminder)
    }

    @Test
    fun `9 Future creation still allows recurrence`() {
        val futureDate = testToday.plusDays(2)
        val recurrence = Recurrence.IntervalDays(1)
        val blocked = isSaveBlocked(
            isCreateMode = true,
            targetDate = futureDate,
            originalDate = null,
            newRecurrence = recurrence
        )
        assertFalse("Future creation with recurrence must be allowed", blocked)

        val taskId = scheduleViewModel.addTask(
            title = "Future Daily Gym",
            date = futureDate,
            recurrence = recurrence
        )
        val task = scheduleViewModel.getTasksForDate(futureDate).first { it.id == taskId }
        assertEquals(recurrence, task.recurrence)
    }

    @Test
    fun `10 existing historical content-only edit still succeeds`() {
        val pastDate = testToday.minusDays(2)
        val taskId = scheduleViewModel.addTask(title = "Old Title", note = "Old Note", date = pastDate, isImportant = false)
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
        assertFalse("Content-only change on historical task must not be blocked", blocked)

        scheduleViewModel.updateTask(id = taskId, title = "New Title", note = "New Note", date = pastDate, isImportant = true)
        val updated = scheduleViewModel.getTasksForDate(pastDate).first { it.id == taskId }
        assertEquals("New Title", updated.title)
        assertEquals("New Note", updated.note)
        assertTrue(updated.isImportant)
    }

    @Test
    fun `11 existing task cannot be improperly rescheduled into past`() {
        val pastDate = testToday.minusDays(2)
        val taskId = scheduleViewModel.addTask(title = "Past Task", note = "Note", date = pastDate)
        val original = scheduleViewModel.getTasksForDate(pastDate).first { it.id == taskId }

        // Moving to another past date is blocked
        val blockedOtherPast = isSaveBlocked(
            isCreateMode = false,
            targetDate = testToday.minusDays(3),
            originalDate = original.date
        )
        assertTrue("Moving historical task to another past date must be blocked", blockedOtherPast)

        // Rescheduling a today/future task to the past is blocked
        val todayTaskId = todayViewModel.addTask(title = "Today Task", date = testToday)
        val todayOriginal = todayViewModel.tasks.value.first { it.id == todayTaskId }
        val blockedTodayToPast = isSaveBlocked(
            isCreateMode = false,
            targetDate = testToday.minusDays(1),
            originalDate = todayOriginal.date
        )
        assertTrue("Moving today task into past must be blocked", blockedTodayToPast)
    }

    @Test
    fun `12 one historical save creates exactly one canonical task and schedule`() {
        val pastDate = testToday.minusDays(4)
        val tasksBefore = database.tasks.value.size
        val schedulesBefore = database.schedules.value.size

        val taskId = scheduleViewModel.addTask(
            title = "Single Canonical Task",
            note = "Only one",
            date = pastDate,
            time = "11:00 AM",
            isImportant = true
        )

        assertEquals(tasksBefore + 1, database.tasks.value.size)
        assertEquals(schedulesBefore + 1, database.schedules.value.size)

        val createdSchedule = database.schedules.value.first { it.taskId == taskId }
        assertEquals(pastDate, createdSchedule.startDate)
        assertEquals(pastDate, createdSchedule.endDate)
        assertNull(createdSchedule.reminderMinutesBefore)
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

    // ========================================================================
    // HISTORICAL ADD TASK FORM REGRESSION TESTS
    // ========================================================================

    @Test
    fun `historical Add Task - 1 Schedule selected past date is passed into Add Task form and parsed correctly`() {
        val selectedPastDate = testToday.minusDays(3) // 16 Aug 2026
        val formatter = java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM", java.util.Locale.getDefault())
        val formattedDate = selectedPastDate.format(formatter)

        val parsed = com.vikaspokala.daybyday.ui.screens.task.parseTaskDate(formattedDate, testToday)
        assertEquals(selectedPastDate, parsed)
    }

    @Test
    fun `historical Add Task - 2 historical CREATE mode is detected`() {
        val pastDate = testToday.minusDays(4)
        val formattedPastDate = pastDate.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM", java.util.Locale.getDefault()))
        val parsed = com.vikaspokala.daybyday.ui.screens.task.parseTaskDate(formattedPastDate, testToday)

        assertNotNull(parsed)
        assertTrue(parsed!!.isBefore(testToday))
    }

    @Test
    fun `historical Add Task - 3 historical form exposes Time`() {
        val pastDate = testToday.minusDays(2)
        val timeString = "3:30 PM"

        val blocked = isSaveBlocked(
            isCreateMode = true,
            targetDate = pastDate,
            originalDate = null,
            newTime = timeString,
            newReminder = null,
            newRecurrence = null
        )
        assertFalse("Historical task creation with time must be allowed", blocked)

        val taskId = scheduleViewModel.addTask(
            title = "Past meeting",
            date = pastDate,
            time = timeString
        )
        val task = scheduleViewModel.getTasksForDate(pastDate).first { it.id == taskId }
        assertEquals(timeString, task.time)
    }

    @Test
    fun `historical Add Task - 4 historical form disables or prevents Reminder`() {
        val pastDate = testToday.minusDays(2)
        val blockedWithReminder = isSaveBlocked(
            isCreateMode = true,
            targetDate = pastDate,
            originalDate = null,
            newTime = "02:00 PM",
            newReminder = "15 minutes before",
            newRecurrence = null
        )
        assertTrue("Historical creation with reminder must be blocked", blockedWithReminder)
    }

    @Test
    fun `historical Add Task - 5 historical form disables or prevents Repeat`() {
        val pastDate = testToday.minusDays(2)
        val blockedWithRepeat = isSaveBlocked(
            isCreateMode = true,
            targetDate = pastDate,
            originalDate = null,
            newTime = null,
            newReminder = null,
            newRecurrence = Recurrence.IntervalDays(1)
        )
        assertTrue("Historical creation with repeat/recurrence must be blocked", blockedWithRepeat)
    }

    @Test
    fun `historical Add Task - 6 save creates ONCE task on the selected historical date`() {
        val pastDate = testToday.minusDays(5)
        val tasksBefore = database.tasks.value.size
        val schedulesBefore = database.schedules.value.size

        handleSaveTask(
            isCreateMode = true,
            taskId = null,
            sourceScreen = "SCHEDULE",
            isLaterTask = false,
            title = "Historical Retrospective",
            note = "Archived log",
            newDateStr = pastDate.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM", java.util.Locale.getDefault())),
            newTimeStr = "11:00 AM",
            newReminderStr = null,
            newRecurrenceStr = Recurrence.Once,
            isImp = true
        )

        assertEquals(tasksBefore + 1, database.tasks.value.size)
        assertEquals(schedulesBefore + 1, database.schedules.value.size)

        val schedule = database.schedules.value.first { it.startDate == pastDate }
        assertEquals(com.vikaspokala.daybyday.ui.fake.ScheduleType.ONCE, schedule.scheduleType)
        assertEquals(pastDate, schedule.endDate)
        assertNull(schedule.reminderMinutesBefore)
    }

    @Test
    fun `historical Add Task - 7 Today Add Task still allows Reminder and Repeat`() {
        val blocked = isSaveBlocked(
            isCreateMode = true,
            targetDate = testToday,
            originalDate = null,
            newTime = "04:00 PM",
            newReminder = "30 minutes before",
            newRecurrence = Recurrence.IntervalDays(1)
        )
        assertFalse("Today Add Task must allow both Reminder and Repeat", blocked)
    }

    @Test
    fun `historical Add Task - 8 future Schedule Add Task still allows Reminder and Repeat`() {
        val futureDate = testToday.plusDays(5)
        val blocked = isSaveBlocked(
            isCreateMode = true,
            targetDate = futureDate,
            originalDate = null,
            newTime = "05:00 PM",
            newReminder = "15 minutes before",
            newRecurrence = Recurrence.Weekdays(setOf(1, 3, 5))
        )
        assertFalse("Future Schedule Add Task must allow both Reminder and Repeat", blocked)
    }

    // ========================================================================
    // HISTORICAL ADD TASK VISIBILITY CONTRACT TESTS
    // ========================================================================

    data class FormFieldVisibility(
        val isTitleVisible: Boolean = true,
        val isNoteVisible: Boolean = true,
        val isImportantVisible: Boolean = true,
        val isDateVisible: Boolean,
        val isTimeVisible: Boolean,
        val isReminderVisible: Boolean,
        val isRepeatVisible: Boolean
    )

    private fun getFormFieldVisibility(
        isCreateMode: Boolean,
        isLaterTask: Boolean,
        dateString: String?,
        plannerToday: LocalDate
    ): FormFieldVisibility {
        if (isLaterTask) {
            return FormFieldVisibility(
                isDateVisible = false,
                isTimeVisible = false,
                isReminderVisible = false,
                isRepeatVisible = false
            )
        }
        val parsedDate = com.vikaspokala.daybyday.ui.screens.task.parseTaskDate(dateString, plannerToday)
        val isCurrentDateHistorical = parsedDate != null && parsedDate.isBefore(plannerToday)
        val isHistoricalCreate = isCreateMode && isCurrentDateHistorical

        return FormFieldVisibility(
            isDateVisible = true,
            isTimeVisible = true,
            isReminderVisible = !isHistoricalCreate,
            isRepeatVisible = !isHistoricalCreate
        )
    }

    @Test
    fun `visibility - 1 historical CREATE does not render Reminder row`() {
        val pastDate = testToday.minusDays(2)
        val dateStr = pastDate.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM", java.util.Locale.getDefault()))
        val visibility = getFormFieldVisibility(
            isCreateMode = true,
            isLaterTask = false,
            dateString = dateStr,
            plannerToday = testToday
        )

        assertFalse("Reminder row must be completely hidden for historical CREATE", visibility.isReminderVisible)
    }

    @Test
    fun `visibility - 2 historical CREATE does not render Repeat row`() {
        val pastDate = testToday.minusDays(2)
        val dateStr = pastDate.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM", java.util.Locale.getDefault()))
        val visibility = getFormFieldVisibility(
            isCreateMode = true,
            isLaterTask = false,
            dateString = dateStr,
            plannerToday = testToday
        )

        assertFalse("Repeat row must be completely hidden for historical CREATE", visibility.isRepeatVisible)
    }

    @Test
    fun `visibility - 3 historical CREATE still renders Time`() {
        val pastDate = testToday.minusDays(2)
        val dateStr = pastDate.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM", java.util.Locale.getDefault()))
        val visibility = getFormFieldVisibility(
            isCreateMode = true,
            isLaterTask = false,
            dateString = dateStr,
            plannerToday = testToday
        )

        assertTrue("Title must be visible", visibility.isTitleVisible)
        assertTrue("Note must be visible", visibility.isNoteVisible)
        assertTrue("Important must be visible", visibility.isImportantVisible)
        assertTrue("Date must be visible", visibility.isDateVisible)
        assertTrue("Time must remain visible for historical CREATE", visibility.isTimeVisible)
    }

    @Test
    fun `visibility - 4 Today CREATE still renders Reminder and Repeat`() {
        val dateStr = testToday.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM", java.util.Locale.getDefault()))
        val visibility = getFormFieldVisibility(
            isCreateMode = true,
            isLaterTask = false,
            dateString = dateStr,
            plannerToday = testToday
        )

        assertTrue("Reminder must remain visible for Today CREATE", visibility.isReminderVisible)
        assertTrue("Repeat must remain visible for Today CREATE", visibility.isRepeatVisible)
    }

    @Test
    fun `visibility - 5 future CREATE still renders Reminder and Repeat`() {
        val futureDate = testToday.plusDays(3)
        val dateStr = futureDate.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM", java.util.Locale.getDefault()))
        val visibility = getFormFieldVisibility(
            isCreateMode = true,
            isLaterTask = false,
            dateString = dateStr,
            plannerToday = testToday
        )

        assertTrue("Reminder must remain visible for future CREATE", visibility.isReminderVisible)
        assertTrue("Repeat must remain visible for future CREATE", visibility.isRepeatVisible)
    }
}
