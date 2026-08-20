package com.vikaspokala.daybyday.ui.screens.schedule

import java.time.LocalDate
import java.time.YearMonth
import androidx.lifecycle.ViewModel
import com.vikaspokala.daybyday.ui.fake.InMemoryPlannerDatabase
import com.vikaspokala.daybyday.ui.models.Recurrence
import com.vikaspokala.daybyday.ui.screens.later.LaterViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update

@OptIn(ExperimentalForInheritanceCoroutinesApi::class, ExperimentalCoroutinesApi::class)
private class DerivedScheduleTasksStateFlow(
    private val database: InMemoryPlannerDatabase,
    private val selectedDateFlow: StateFlow<LocalDate>
) : StateFlow<List<ScheduleTaskItem>> {
    override val value: List<ScheduleTaskItem> get() = database.getTasksForDate(selectedDateFlow.value)
    override val replayCache: List<List<ScheduleTaskItem>> get() = listOf(value)
    override suspend fun collect(collector: FlowCollector<List<ScheduleTaskItem>>): Nothing {
        selectedDateFlow.flatMapLatest { date ->
            database.observeTasksForDate(date)
        }.distinctUntilChanged().collect(collector)
        awaitCancellation()
    }
}

@OptIn(ExperimentalForInheritanceCoroutinesApi::class, ExperimentalCoroutinesApi::class)
private class DerivedImportantDatesStateFlow(
    private val database: InMemoryPlannerDatabase,
    private val monthFlow: StateFlow<YearMonth>
) : StateFlow<Set<LocalDate>> {
    override val value: Set<LocalDate> get() {
        val m = monthFlow.value
        val start = m.atDay(1).minusDays(7)
        val end = m.atEndOfMonth().plusDays(7)
        return database.getImportantDates(start, end)
    }
    override val replayCache: List<Set<LocalDate>> get() = listOf(value)
    override suspend fun collect(collector: FlowCollector<Set<LocalDate>>): Nothing {
        monthFlow.flatMapLatest { m ->
            val start = m.atDay(1).minusDays(7)
            val end = m.atEndOfMonth().plusDays(7)
            database.observeImportantDates(start, end)
        }.distinctUntilChanged().collect(collector)
        awaitCancellation()
    }
}

class ScheduleViewModel(
    private val database: InMemoryPlannerDatabase = InMemoryPlannerDatabase.defaultDatabase,
    private val plannerTodayProvider: () -> LocalDate = { LocalDate.now() }
) : ViewModel() {

    constructor(referenceDate: LocalDate) : this(
        database = InMemoryPlannerDatabase.defaultDatabase,
        plannerTodayProvider = { referenceDate }
    )

    constructor(database: InMemoryPlannerDatabase, referenceDate: LocalDate) : this(
        database = database,
        plannerTodayProvider = { referenceDate }
    )

    private val _currentMonth = MutableStateFlow(YearMonth.from(plannerTodayProvider()))
    val currentMonth: StateFlow<YearMonth> = _currentMonth.asStateFlow()

    private val _selectedDate = MutableStateFlow(plannerTodayProvider())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    val tasks: StateFlow<List<ScheduleTaskItem>> = DerivedScheduleTasksStateFlow(database, _selectedDate)

    val importantDates: StateFlow<Set<LocalDate>> = DerivedImportantDatesStateFlow(database, _currentMonth)

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        _currentMonth.value = YearMonth.from(date)
    }

    fun previousMonth() {
        _currentMonth.update { it.minusMonths(1) }
    }

    fun nextMonth() {
        _currentMonth.update { it.plusMonths(1) }
    }

    fun selectToday(todayDate: LocalDate = plannerTodayProvider()) {
        _selectedDate.value = todayDate
        _currentMonth.value = YearMonth.from(todayDate)
    }

    fun resetToToday(todayDate: LocalDate = plannerTodayProvider()) {
        _selectedDate.value = todayDate
        _currentMonth.value = YearMonth.from(todayDate)
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
        date: LocalDate,
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

    fun hasImportantTask(date: LocalDate, taskList: List<ScheduleTaskItem> = database.getTasksForDate(date)): Boolean {
        return database.hasImportantTask(date)
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
}
