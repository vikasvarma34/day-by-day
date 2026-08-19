package com.vikaspokala.daybyday.ui.screens.today

import java.time.LocalDate
import java.time.LocalTime
import androidx.lifecycle.ViewModel
import com.vikaspokala.daybyday.ui.fake.InMemoryDatedTaskStore
import com.vikaspokala.daybyday.ui.screens.schedule.ScheduleTaskItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class TodayViewModel(
    private val taskStore: InMemoryDatedTaskStore = InMemoryDatedTaskStore.defaultStore,
    initialDate: LocalDate = LocalDate.now()
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(initialDate)
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    val tasks: StateFlow<List<ScheduleTaskItem>> = taskStore.tasks

    fun selectPreviousDay() {
        _selectedDate.update { it.minusDays(1) }
    }

    fun selectNextDay() {
        _selectedDate.update { it.plusDays(1) }
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun selectToday(todayDate: LocalDate = LocalDate.now()) {
        _selectedDate.value = todayDate
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
        date: LocalDate = _selectedDate.value,
        time: String? = null,
        isImportant: Boolean = false
    ): String {
        return taskStore.addTask(
            title = title,
            note = note,
            date = date,
            time = time,
            isImportant = isImportant
        )
    }

    fun updateTask(
        id: String,
        title: String,
        note: String? = null,
        date: LocalDate? = null,
        time: String? = null,
        isImportant: Boolean = false
    ) {
        val targetDate = date ?: tasks.value.firstOrNull { it.id == id }?.date ?: _selectedDate.value
        taskStore.updateTask(
            id = id,
            title = title,
            note = note,
            date = targetDate,
            time = time,
            isImportant = isImportant
        )
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
