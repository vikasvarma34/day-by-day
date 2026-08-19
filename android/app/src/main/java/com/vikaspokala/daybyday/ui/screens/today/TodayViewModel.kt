package com.vikaspokala.daybyday.ui.screens.today

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class TodayViewModel : ViewModel() {

    private val initialTasks = listOf(
        TaskItem(id = "1", title = "Take medicine", time = "8:00 AM", isImportant = false, isCompleted = false),
        TaskItem(id = "2", title = "Buy groceries", time = "10:30 AM", isImportant = true, isCompleted = false),
        TaskItem(id = "3", title = "Lunch", time = "1:00 PM", isImportant = false, isCompleted = false),
        TaskItem(id = "4", title = "Gym", time = "6:00 PM", isImportant = true, isCompleted = false),
        TaskItem(id = "5", title = "Call the bank", time = null, isImportant = false, isCompleted = false),
        TaskItem(id = "6", title = "Morning walk", time = "7:15 AM", isImportant = false, isCompleted = true)
    )

    private val _tasks = MutableStateFlow(initialTasks)
    val tasks: StateFlow<List<TaskItem>> = _tasks.asStateFlow()

    fun toggleCompletion(taskId: String) {
        _tasks.update { currentList ->
            currentList.map { task ->
                if (task.id == taskId) {
                    task.copy(isCompleted = !task.isCompleted)
                } else {
                    task
                }
            }
        }
    }

    fun toggleImportant(taskId: String) {
        _tasks.update { currentList ->
            currentList.map { task ->
                if (task.id == taskId) {
                    task.copy(isImportant = !task.isImportant)
                } else {
                    task
                }
            }
        }
    }
}
