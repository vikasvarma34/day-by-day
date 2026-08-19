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
