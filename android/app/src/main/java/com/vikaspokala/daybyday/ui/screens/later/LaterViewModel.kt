package com.vikaspokala.daybyday.ui.screens.later

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.lifecycle.ViewModel
import com.vikaspokala.daybyday.ui.fake.InMemoryPlannerDatabase
import com.vikaspokala.daybyday.ui.models.Recurrence
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
private class DerivedLaterStateFlow(
    private val database: InMemoryPlannerDatabase
) : StateFlow<List<LaterTaskItem>> {
    override val value: List<LaterTaskItem> get() = database.getLaterTasks()
    override val replayCache: List<List<LaterTaskItem>> get() = listOf(value)
    override suspend fun collect(collector: FlowCollector<List<LaterTaskItem>>): Nothing {
        database.observeLaterTasks().collect(collector)
        awaitCancellation()
    }
}

class LaterViewModel(
    private val database: InMemoryPlannerDatabase = InMemoryPlannerDatabase.defaultDatabase,
    private val plannerTodayProvider: () -> LocalDate = { LocalDate.now() }
) : ViewModel() {

    val tasks: StateFlow<List<LaterTaskItem>> = DerivedLaterStateFlow(database)

    private val _isCompletedExpanded = MutableStateFlow(false)
    val isCompletedExpanded: StateFlow<Boolean> = _isCompletedExpanded.asStateFlow()

    fun toggleCompletion(taskId: String, completionDate: LocalDate = plannerTodayProvider()) {
        val currentTask = tasks.value.find { it.id == taskId } ?: return
        val nextCompleted = !currentTask.isCompleted
        if (nextCompleted) {
            database.completeTask(
                taskId = taskId,
                scheduleId = null,
                scheduledDate = null,
                completedDate = completionDate,
                plannerToday = plannerTodayProvider()
            )
        } else {
            database.undoTask(
                taskId = taskId,
                scheduleId = null,
                scheduledDate = null
            )
        }
    }

    fun toggleImportant(taskId: String) {
        val currentTask = tasks.value.find { it.id == taskId } ?: return
        val nextImportant = !currentTask.isImportant
        database.updateTask(
            taskId = taskId,
            title = currentTask.title,
            note = currentTask.note,
            isImportant = nextImportant,
            plannerToday = plannerTodayProvider()
        )
    }

    fun deleteTask(taskId: String) {
        database.deleteTask(taskId)
    }

    fun addTask(title: String, note: String?, isImportant: Boolean): String {
        return database.createLaterTask(
            title = title,
            note = note,
            isImportant = isImportant
        )
    }

    fun updateTask(id: String, title: String, note: String?, isImportant: Boolean) {
        database.updateTask(
            taskId = id,
            title = title,
            note = note,
            isImportant = isImportant,
            plannerToday = plannerTodayProvider()
        )
    }

    fun scheduleTask(
        taskId: String,
        title: String? = null,
        note: String? = null,
        isImportant: Boolean? = null,
        date: LocalDate,
        time: String? = null,
        reminder: String? = null,
        recurrence: Recurrence? = null,
        plannerToday: LocalDate = plannerTodayProvider()
    ) {
        val parsedTime = parseDisplayStringToLocalTime(time)
        val parsedReminder = parseDisplayStringToReminderMinutes(reminder)
        database.scheduleLaterTask(
            taskId = taskId,
            title = title,
            note = note,
            isImportant = isImportant,
            date = date,
            time = parsedTime,
            reminderMinutesBefore = parsedReminder,
            recurrence = recurrence,
            plannerToday = plannerToday
        )
    }

    fun scheduleTaskWithLocalTime(
        taskId: String,
        title: String? = null,
        note: String? = null,
        isImportant: Boolean? = null,
        date: LocalDate,
        time: LocalTime? = null,
        reminderMinutesBefore: Int? = null,
        recurrence: Recurrence? = null,
        plannerToday: LocalDate = plannerTodayProvider()
    ) {
        database.scheduleLaterTask(
            taskId = taskId,
            title = title,
            note = note,
            isImportant = isImportant,
            date = date,
            time = time,
            reminderMinutesBefore = reminderMinutesBefore,
            recurrence = recurrence,
            plannerToday = plannerToday
        )
    }

    fun toggleCompletedExpanded() {
        _isCompletedExpanded.update { !it }
    }

    fun collapseCompleted() {
        _isCompletedExpanded.value = false
    }

    fun getActiveTasks(taskList: List<LaterTaskItem> = tasks.value): List<LaterTaskItem> {
        return taskList.filter { !it.isCompleted }
    }

    fun getCompletedTasks(taskList: List<LaterTaskItem> = tasks.value): List<LaterTaskItem> {
        return taskList.filter { it.isCompleted }
    }

    companion object {
        fun parseDisplayStringToLocalTime(timeStr: String?): LocalTime? {
            if (timeStr.isNullOrBlank()) return null
            return try {
                val upper = timeStr.trim().uppercase(Locale.US)
                val formatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
                LocalTime.parse(upper, formatter)
            } catch (e: Exception) {
                try {
                    LocalTime.parse(timeStr.trim())
                } catch (e2: Exception) {
                    null
                }
            }
        }

        fun parseDisplayStringToReminderMinutes(display: String?): Int? {
            return when (display?.trim()) {
                "At task time" -> 0
                "5 minutes before" -> 5
                "10 minutes before" -> 10
                "15 minutes before" -> 15
                "30 minutes before" -> 30
                "1 hour before" -> 60
                "1 day before" -> 1440
                "2 days before" -> 2880
                else -> null
            }
        }
    }
}
