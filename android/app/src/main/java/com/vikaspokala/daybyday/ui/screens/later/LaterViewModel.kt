package com.vikaspokala.daybyday.ui.screens.later

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vikaspokala.daybyday.data.local.auth.SessionTokenStore
import com.vikaspokala.daybyday.data.local.planner.PlannerDatabase
import com.vikaspokala.daybyday.data.repository.PlannerRepository
import com.vikaspokala.daybyday.ui.planner.toLaterTaskItem
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

class LaterViewModel(
    private val database: PlannerDatabase,
    private val repository: PlannerRepository,
    private val plannerTodayProvider: () -> LocalDate = { LocalDate.now() },
    private val onSessionExpired: () -> Unit = {},
    scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
) : ViewModel() {
    private val coroutineScope = scope
    val tasks: StateFlow<List<LaterTaskItem>> = database.observeLaterTasks()
        .map { rows -> rows.map { it.toLaterTaskItem() } }
        .stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())

    private val _isCompletedExpanded = MutableStateFlow(false)
    val isCompletedExpanded: StateFlow<Boolean> = _isCompletedExpanded.asStateFlow()
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    fun toggleCompletion(taskId: String, completionDate: LocalDate = plannerTodayProvider()) {
        val task = tasks.value.firstOrNull { it.id == taskId } ?: return
        val plannerToday = plannerTodayProvider()
        launchAction {
            if (task.isCompleted) repository.undoTask(taskId)
            else repository.completeTask(taskId, plannerToday.toString(), completionDate.toString())
        }
    }

    fun toggleImportant(taskId: String) {
        val task = tasks.value.firstOrNull { it.id == taskId } ?: return
        launchAction { repository.updateTaskContent(taskId, task.title, task.note, !task.isImportant) }
    }

    fun deleteTask(taskId: String, onSuccess: () -> Unit = {}) = launchAction(onSuccess) { repository.deleteTask(taskId) }
    fun toggleCompletedExpanded() = _isCompletedExpanded.update { !it }
    fun collapseCompleted() { _isCompletedExpanded.value = false }
    fun getActiveTasks(taskList: List<LaterTaskItem> = tasks.value) = taskList.filter { !it.isCompleted }
    fun getCompletedTasks(taskList: List<LaterTaskItem> = tasks.value) = taskList.filter { it.isCompleted }

    private fun launchAction(onSuccess: () -> Unit = {}, action: suspend () -> Result<Unit>) {
        coroutineScope.launch {
            _actionError.value = null
            action().fold(onSuccess = { onSuccess() }, onFailure = ::handleFailure)
        }
    }

    private fun handleFailure(error: Throwable) {
        if (error is HttpException && error.code() == 401) onSessionExpired()
        else _actionError.value = "Unable to save planner change. Please try again."
    }

    class Factory(private val context: Context, private val onSessionExpired: () -> Unit = {}) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val db = PlannerDatabase.getInstance(context.applicationContext)
            return LaterViewModel(db, PlannerRepository(db, SessionTokenStore(context.applicationContext)), onSessionExpired = onSessionExpired) as T
        }
    }
}
