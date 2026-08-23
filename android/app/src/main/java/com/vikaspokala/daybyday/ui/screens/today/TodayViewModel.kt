package com.vikaspokala.daybyday.ui.screens.today

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vikaspokala.daybyday.data.local.auth.SessionTokenStore
import com.vikaspokala.daybyday.data.local.planner.PlannerDatabase
import com.vikaspokala.daybyday.data.repository.PlannerRepository
import com.vikaspokala.daybyday.ui.planner.parseDisplayTime
import com.vikaspokala.daybyday.ui.planner.toScheduleTaskItem
import com.vikaspokala.daybyday.ui.screens.schedule.ScheduleTaskItem
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

class TodayViewModel(
    private val database: PlannerDatabase,
    private val repository: PlannerRepository,
    private val plannerTodayProvider: () -> LocalDate = { LocalDate.now() },
    private val onSessionExpired: () -> Unit = {},
    scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
) : ViewModel() {
    private val coroutineScope = scope
    private val _selectedDate = MutableStateFlow(plannerTodayProvider())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    fun getPlannerToday(): LocalDate = plannerTodayProvider()

    @OptIn(ExperimentalCoroutinesApi::class)
    val tasks: StateFlow<List<ScheduleTaskItem>> = _selectedDate.flatMapLatest { date ->
        database.observeScheduledOccurrences(date).map { rows -> rows.map { it.toScheduleTaskItem() } }
    }.stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())

    fun selectPreviousDay() = _selectedDate.update { it.minusDays(1) }
    fun selectNextDay() = _selectedDate.update { it.plusDays(1) }
    fun selectDate(date: LocalDate) { _selectedDate.value = date }
    fun selectToday(todayDate: LocalDate = plannerTodayProvider()) { _selectedDate.value = todayDate }

    fun toggleCompletion(taskId: String, scheduledDate: LocalDate = _selectedDate.value) {
        val task = tasks.value.firstOrNull { it.id == taskId && it.date == scheduledDate } ?: return
        val scheduleId = task.scheduleId ?: return
        val plannerToday = plannerTodayProvider()
        launchAction {
            if (task.isCompleted) repository.undoTask(taskId, scheduleId, scheduledDate.toString())
            else repository.completeTask(
                taskId, plannerToday.toString(),
                if (scheduledDate.isBefore(plannerToday)) scheduledDate.toString() else plannerToday.toString(),
                scheduleId, scheduledDate.toString()
            )
        }
    }

    fun toggleImportant(taskId: String) {
        val task = tasks.value.firstOrNull { it.id == taskId } ?: return
        launchAction { repository.updateTaskContent(taskId, task.title, task.note, !task.isImportant) }
    }

    fun deleteTask(taskId: String, onSuccess: () -> Unit = {}) = launchAction(onSuccess) { repository.deleteTask(taskId) }

    fun getTasksForDate(date: LocalDate, taskList: List<ScheduleTaskItem> = tasks.value): List<ScheduleTaskItem> {
        val dateTasks = taskList.filter { it.date == date }
        val timed = compareBy<ScheduleTaskItem> { parseDisplayTime(it.time) }
        return dateTasks.filter { !it.isCompleted && it.time != null }.sortedWith(timed) +
            dateTasks.filter { !it.isCompleted && it.time == null } +
            dateTasks.filter { it.isCompleted && it.time != null }.sortedWith(timed) +
            dateTasks.filter { it.isCompleted && it.time == null }
    }

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
            return TodayViewModel(db, PlannerRepository(db, SessionTokenStore(context.applicationContext)), onSessionExpired = onSessionExpired) as T
        }
    }

    companion object {
        fun getGreeting(time: LocalTime = LocalTime.now(), name: String = ""): String {
            val minutes = time.hour * 60 + time.minute
            val greeting = when (minutes) {
                in 300..719 -> "Good morning"
                in 720..1019 -> "Good afternoon"
                in 1020..1259 -> "Good evening"
                in 1260..1319 -> "Good night"
                else -> "Time to rest"
            }
            return if (name.isBlank()) greeting else "$greeting, $name"
        }
    }
}
