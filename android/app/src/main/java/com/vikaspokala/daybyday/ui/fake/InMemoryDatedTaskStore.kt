package com.vikaspokala.daybyday.ui.fake

import java.time.LocalDate
import com.vikaspokala.daybyday.ui.screens.schedule.ScheduleTaskItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class InMemoryDatedTaskStore(
    initialDate: LocalDate = LocalDate.now()
) {
    private val _tasks = MutableStateFlow(FakePlannerData.getScheduleDemoTasks(initialDate))
    val tasks: StateFlow<List<ScheduleTaskItem>> = _tasks.asStateFlow()

    fun reset(referenceDate: LocalDate = LocalDate.now()) {
        _tasks.value = FakePlannerData.getScheduleDemoTasks(referenceDate)
    }

    fun toggleCompletion(taskId: String) {
        _tasks.update { list ->
            list.map { task ->
                if (task.id == taskId) task.copy(isCompleted = !task.isCompleted) else task
            }
        }
    }

    fun toggleImportant(taskId: String) {
        _tasks.update { list ->
            list.map { task ->
                if (task.id == taskId) task.copy(isImportant = !task.isImportant) else task
            }
        }
    }

    fun deleteTask(taskId: String) {
        _tasks.update { list ->
            list.filter { it.id != taskId }
        }
    }

    fun addTask(
        title: String,
        note: String? = null,
        date: LocalDate,
        time: String? = null,
        reminder: String? = null,
        isImportant: Boolean = false
    ): String {
        val newId = "task_" + System.currentTimeMillis()
        val newTask = ScheduleTaskItem(
            id = newId,
            title = title.trim(),
            note = note?.takeIf { it.isNotBlank() },
            date = date,
            time = time,
            reminder = if (time != null) reminder else null,
            isImportant = isImportant,
            isCompleted = false
        )
        _tasks.update { list -> listOf(newTask) + list }
        return newId
    }

    fun updateTask(
        id: String,
        title: String,
        note: String? = null,
        date: LocalDate,
        time: String? = null,
        reminder: String? = null,
        isImportant: Boolean = false
    ) {
        _tasks.update { list ->
            val exists = list.any { it.id == id }
            if (exists) {
                list.map { task ->
                    if (task.id == id) {
                        task.copy(
                            title = title.trim(),
                            note = note?.takeIf { it.isNotBlank() },
                            date = date,
                            time = time,
                            reminder = if (time != null) reminder else null,
                            isImportant = isImportant
                        )
                    } else {
                        task
                    }
                }
            } else {
                val newTask = ScheduleTaskItem(
                    id = id,
                    title = title.trim(),
                    note = note?.takeIf { it.isNotBlank() },
                    date = date,
                    time = time,
                    reminder = if (time != null) reminder else null,
                    isImportant = isImportant,
                    isCompleted = false
                )
                listOf(newTask) + list
            }
        }
    }

    companion object {
        val defaultStore = InMemoryDatedTaskStore()
    }
}
