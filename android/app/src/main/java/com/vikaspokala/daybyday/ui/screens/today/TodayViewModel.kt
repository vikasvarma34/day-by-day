package com.vikaspokala.daybyday.ui.screens.today

import androidx.lifecycle.ViewModel
import com.vikaspokala.daybyday.ui.fake.FakePlannerData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class TodayViewModel : ViewModel() {

    private val _tasks = MutableStateFlow(FakePlannerData.getTodayDemoTasks())
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
