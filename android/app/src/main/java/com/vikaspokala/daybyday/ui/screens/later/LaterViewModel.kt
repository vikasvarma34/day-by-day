package com.vikaspokala.daybyday.ui.screens.later

import androidx.lifecycle.ViewModel
import com.vikaspokala.daybyday.ui.fake.FakePlannerData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class LaterViewModel : ViewModel() {

    private val _tasks = MutableStateFlow(FakePlannerData.getLaterDemoTasks())
    val tasks: StateFlow<List<LaterTaskItem>> = _tasks.asStateFlow()

    private val _isCompletedExpanded = MutableStateFlow(false)
    val isCompletedExpanded: StateFlow<Boolean> = _isCompletedExpanded.asStateFlow()

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

    fun deleteTask(taskId: String) {
        _tasks.update { currentList ->
            currentList.filter { it.id != taskId }
        }
    }

    fun addTask(title: String, note: String?, isImportant: Boolean): String {
        val newId = "later_" + System.currentTimeMillis()
        val newTask = LaterTaskItem(
            id = newId,
            title = title.trim(),
            note = note?.takeIf { it.isNotBlank() },
            isImportant = isImportant,
            isCompleted = false
        )
        _tasks.update { currentList -> listOf(newTask) + currentList }
        return newId
    }

    fun updateTask(id: String, title: String, note: String?, isImportant: Boolean) {
        _tasks.update { currentList ->
            currentList.map { task ->
                if (task.id == id) {
                    task.copy(title = title.trim(), note = note?.takeIf { it.isNotBlank() }, isImportant = isImportant)
                } else {
                    task
                }
            }
        }
    }

    fun toggleCompletedExpanded() {
        _isCompletedExpanded.update { !it }
    }

    fun collapseCompleted() {
        _isCompletedExpanded.value = false
    }

    fun getActiveTasks(taskList: List<LaterTaskItem> = _tasks.value): List<LaterTaskItem> {
        return taskList.filter { !it.isCompleted }
    }

    fun getCompletedTasks(taskList: List<LaterTaskItem> = _tasks.value): List<LaterTaskItem> {
        return taskList.filter { it.isCompleted }
    }
}
