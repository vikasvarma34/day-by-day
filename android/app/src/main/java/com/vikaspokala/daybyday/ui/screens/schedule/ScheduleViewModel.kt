package com.vikaspokala.daybyday.ui.screens.schedule

import java.time.LocalDate
import java.time.YearMonth
import androidx.lifecycle.ViewModel
import com.vikaspokala.daybyday.ui.fake.InMemoryDatedTaskStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ScheduleViewModel(
    private val taskStore: InMemoryDatedTaskStore = InMemoryDatedTaskStore.defaultStore,
    initialDate: LocalDate = LocalDate.now()
) : ViewModel() {

    private val _currentMonth = MutableStateFlow(YearMonth.from(initialDate))
    val currentMonth: StateFlow<YearMonth> = _currentMonth.asStateFlow()

    private val _selectedDate = MutableStateFlow(initialDate)
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    val tasks: StateFlow<List<ScheduleTaskItem>> = taskStore.tasks

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

    fun selectToday(todayDate: LocalDate = LocalDate.now()) {
        _selectedDate.value = todayDate
        _currentMonth.value = YearMonth.from(todayDate)
    }

    fun resetToToday(todayDate: LocalDate = LocalDate.now()) {
        _selectedDate.value = todayDate
        _currentMonth.value = YearMonth.from(todayDate)
    }

    fun toggleCompletion(taskId: String) {
        taskStore.toggleCompletion(taskId)
    }

    fun toggleImportant(taskId: String) {
        taskStore.toggleImportant(taskId)
    }

    fun deleteTask(taskId: String) {
        taskStore.deleteTask(taskId)
    }

    fun addTask(
        title: String,
        note: String? = null,
        date: LocalDate,
        time: String? = null,
        reminder: String? = null,
        recurrence: com.vikaspokala.daybyday.ui.models.Recurrence? = null,
        isImportant: Boolean = false
    ): String {
        return taskStore.addTask(
            title = title,
            note = note,
            date = date,
            time = time,
            reminder = reminder,
            recurrence = recurrence,
            isImportant = isImportant
        )
    }

    fun updateTask(
        id: String,
        title: String,
        note: String? = null,
        date: LocalDate,
        time: String? = null,
        reminder: String? = null,
        recurrence: com.vikaspokala.daybyday.ui.models.Recurrence? = null,
        isImportant: Boolean = false
    ) {
        taskStore.updateTask(
            id = id,
            title = title,
            note = note,
            date = date,
            time = time,
            reminder = reminder,
            recurrence = recurrence,
            isImportant = isImportant
        )
    }

    fun hasImportantTask(date: LocalDate, taskList: List<ScheduleTaskItem> = tasks.value): Boolean {
        return taskList.any { it.date == date && it.isImportant }
    }

    fun getTasksForDate(date: LocalDate, taskList: List<ScheduleTaskItem> = tasks.value): List<ScheduleTaskItem> {
        val dateTasks = taskList.filter { it.date == date }

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
