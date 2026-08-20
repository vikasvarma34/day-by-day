package com.vikaspokala.daybyday.ui.screens.today

import java.time.LocalDate
import java.time.LocalTime
import androidx.lifecycle.ViewModel
import com.vikaspokala.daybyday.ui.fake.InMemoryPlannerDatabase
import com.vikaspokala.daybyday.ui.models.Recurrence
import com.vikaspokala.daybyday.ui.screens.later.LaterViewModel
import com.vikaspokala.daybyday.ui.screens.schedule.ScheduleTaskItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class TodayViewModel(
    private val database: InMemoryPlannerDatabase = InMemoryPlannerDatabase.defaultDatabase,
    private val plannerTodayProvider: () -> LocalDate = { LocalDate.now() },
    scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
) : ViewModel() {

    constructor(referenceDate: LocalDate) : this(
        database = InMemoryPlannerDatabase.defaultDatabase,
        plannerTodayProvider = { referenceDate }
    )

    constructor(database: InMemoryPlannerDatabase, referenceDate: LocalDate) : this(
        database = database,
        plannerTodayProvider = { referenceDate }
    )

    private val _selectedDate = MutableStateFlow(plannerTodayProvider())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    fun getPlannerToday(): LocalDate = plannerTodayProvider()

    @OptIn(ExperimentalCoroutinesApi::class)
    val tasks: StateFlow<List<ScheduleTaskItem>> = _selectedDate
        .flatMapLatest { date -> database.observeTasksForDate(date) }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = database.getTasksForDate(_selectedDate.value)
        )

    fun selectPreviousDay() {
        _selectedDate.update { it.minusDays(1) }
    }

    fun selectNextDay() {
        _selectedDate.update { it.plusDays(1) }
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun selectToday(todayDate: LocalDate = plannerTodayProvider()) {
        _selectedDate.value = todayDate
    }

    fun toggleCompletion(taskId: String, scheduledDate: LocalDate = _selectedDate.value) {
        val occurs = database.getTasksForDate(scheduledDate).any { it.id == taskId }
        if (!occurs && database.tasks.value.none { it.id == taskId }) return
        val isCompleted = database.completions.value.any {
            it.taskId == taskId && it.scheduledDate == scheduledDate
        }
        val nextCompleted = !isCompleted
        if (nextCompleted) {
            val plannerToday = plannerTodayProvider()
            val completedDate = if (scheduledDate.isBefore(plannerToday)) scheduledDate else plannerToday
            database.completeScheduledOccurrence(
                taskId = taskId,
                scheduledDate = scheduledDate,
                completedDate = completedDate,
                plannerToday = plannerToday
            )
        } else {
            database.undoScheduledOccurrence(
                taskId = taskId,
                scheduledDate = scheduledDate
            )
        }
    }

    fun toggleImportant(taskId: String) {
        val task = database.tasks.value.find { it.id == taskId } ?: return
        val nextImportant = !task.isImportant
        database.updateTask(
            taskId = taskId,
            title = task.title,
            note = task.note,
            isImportant = nextImportant,
            plannerToday = plannerTodayProvider()
        )
    }

    fun deleteTask(taskId: String) {
        database.deleteTask(taskId)
    }

    fun addTask(
        title: String,
        note: String? = null,
        date: LocalDate = _selectedDate.value,
        time: String? = null,
        reminder: String? = null,
        recurrence: Recurrence? = null,
        isImportant: Boolean = false
    ): String {
        val parsedTime = LaterViewModel.parseDisplayStringToLocalTime(time)
        val parsedReminder = if (parsedTime != null) LaterViewModel.parseDisplayStringToReminderMinutes(reminder) else null
        return database.createDatedTask(
            title = title,
            note = note,
            date = date,
            time = parsedTime,
            reminderMinutesBefore = parsedReminder,
            recurrence = recurrence,
            isImportant = isImportant,
            plannerToday = plannerTodayProvider()
        )
    }

    fun updateTask(
        id: String,
        title: String,
        note: String? = null,
        date: LocalDate,
        time: String? = null,
        reminder: String? = null,
        recurrence: Recurrence? = null,
        isImportant: Boolean = false
    ) {
        val parsedTime = LaterViewModel.parseDisplayStringToLocalTime(time)
        val parsedReminder = if (parsedTime != null) LaterViewModel.parseDisplayStringToReminderMinutes(reminder) else null
        database.updateTask(
            taskId = id,
            title = title,
            note = note,
            date = date,
            time = parsedTime,
            reminderMinutesBefore = parsedReminder,
            recurrence = recurrence,
            isImportant = isImportant,
            plannerToday = plannerTodayProvider()
        )
    }

    fun getTasksForDate(date: LocalDate, taskList: List<ScheduleTaskItem> = database.getTasksForDate(date)): List<ScheduleTaskItem> {
        val dateTasks = if (taskList.isNotEmpty() && taskList.first().date == date) taskList else database.getTasksForDate(date)

        val incompleteTimed = dateTasks.filter { !it.isCompleted && it.time != null }
            .sortedBy { parseTimeToMinutes(it.time) }
        val incompleteAnytime = dateTasks.filter { !it.isCompleted && it.time == null }

        val completedTimed = dateTasks.filter { it.isCompleted && it.time != null }
            .sortedBy { parseTimeToMinutes(it.time) }
        val completedAnytime = dateTasks.filter { it.isCompleted && it.time == null }

        return incompleteTimed + incompleteAnytime + completedTimed + completedAnytime
    }

    private fun parseTimeToMinutes(timeStr: String?): Int {
        if (timeStr.isNullOrEmpty()) return Int.MAX_VALUE
        return try {
            val parts = timeStr.trim().split(" ")
            val timeParts = parts[0].split(":")
            var hour = timeParts[0].toInt()
            val minute = timeParts[1].toInt()
            val amPm = if (parts.size > 1) parts[1].uppercase() else "AM"
            if (amPm == "PM" && hour < 12) hour += 12
            if (amPm == "AM" && hour == 12) hour = 0
            hour * 60 + minute
        } catch (e: Exception) {
            Int.MAX_VALUE
        }
    }

    companion object {
        fun getGreeting(time: LocalTime = LocalTime.now(), name: String = "Vicky"): String {
            val hour = time.hour
            val minute = time.minute
            val totalMinutes = hour * 60 + minute

            val greetingText = when (totalMinutes) {
                in (5 * 60)..(11 * 60 + 59) -> "Good morning"
                in (12 * 60)..(16 * 60 + 59) -> "Good afternoon"
                in (17 * 60)..(20 * 60 + 59) -> "Good evening"
                else -> "Good night"
            }
            return if (name.isNotBlank()) "$greetingText, $name" else greetingText
        }
    }
}
