package com.vikaspokala.daybyday.ui.screens.task

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vikaspokala.daybyday.data.local.auth.SessionTokenStore
import com.vikaspokala.daybyday.data.local.planner.PlannerDatabase
import com.vikaspokala.daybyday.data.remote.dto.CreateTaskScheduleRequestDto
import com.vikaspokala.daybyday.data.remote.dto.UpdateTaskScheduleDto
import com.vikaspokala.daybyday.data.repository.PlannerRepository
import com.vikaspokala.daybyday.notification.DefaultTaskReminderScheduler
import com.vikaspokala.daybyday.notification.TaskReminderScheduler
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

class PlannerTaskViewModel(
    private val repository: PlannerRepository,
    private val plannerTodayProvider: () -> LocalDate = { LocalDate.now() },
    private val reminderScheduler: TaskReminderScheduler? = null,
    private val onSessionExpired: () -> Unit = {},
    private val uuidProvider: () -> UUID = { UUID.randomUUID() },
    scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
) : ViewModel() {
    private val coroutineScope = scope
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private val _reminderNotice = MutableStateFlow<String?>(null)
    val reminderNotice: StateFlow<String?> = _reminderNotice.asStateFlow()

    fun dismissReminderNotice() {
        _reminderNotice.value = null
    }

    fun openExactAlarmSettings(context: Context) {
        reminderScheduler?.openExactAlarmSettings(context)
    }

    private fun isExactAlarmDeniedForSchedule(time: String?, reminder: Int?): Boolean {
        if (!time.isNullOrBlank() && reminder != null) {
            val scheduler = reminderScheduler
            if (scheduler != null && !scheduler.canScheduleExactAlarms()) {
                return true
            }
        }
        return false
    }

    fun createLater(title: String, note: String?, isImportant: Boolean, onSuccess: () -> Unit = {}) {
        val taskId = uuidProvider().toString()
        launchAction(onSuccess) { repository.createTask(taskId, title, note, isImportant) }
    }

    fun createScheduled(
        title: String, note: String?, isImportant: Boolean, schedule: CreateTaskScheduleRequestDto,
        onSuccess: () -> Unit = {}
    ) {
        if (isExactAlarmDeniedForSchedule(
                time = schedule.scheduledTime,
                reminder = schedule.reminderMinutesBefore
            )
        ) {
            _actionError.value = null
            _reminderNotice.value = "Turn on Alarms & reminders to use reminders."
            return
        }

        val taskId = uuidProvider().toString()
        launchAction(onSuccess) {
            repository.createTask(taskId, title, note, isImportant, plannerTodayProvider().toString(), schedule)
        }
    }

    fun saveExisting(
        taskId: String,
        title: String,
        note: String?,
        isImportant: Boolean,
        contentChanged: Boolean,
        scheduleChanged: Boolean,
        effectiveDate: LocalDate,
        schedule: UpdateTaskScheduleDto,
        onSuccess: () -> Unit = {}
    ) {
        if (scheduleChanged && isExactAlarmDeniedForSchedule(
                time = schedule.scheduledTime,
                reminder = schedule.reminderMinutesBefore
            )
        ) {
            _actionError.value = null
            _reminderNotice.value = "Turn on Alarms & reminders to use reminders."
            return
        }

        val plannerToday = plannerTodayProvider().toString()
        launchAction(onSuccess) {
            when {
                contentChanged && scheduleChanged -> repository.updateTask(
                    taskId, title, note, isImportant, plannerToday, effectiveDate.toString(), schedule
                )
                scheduleChanged -> repository.updateTaskSchedule(taskId, plannerToday, effectiveDate.toString(), schedule)
                contentChanged -> repository.updateTaskContent(taskId, title, note, isImportant)
                else -> Result.success(Unit)
            }
        }
    }

    fun scheduleLater(
        taskId: String,
        title: String,
        note: String?,
        isImportant: Boolean,
        contentChanged: Boolean,
        schedule: UpdateTaskScheduleDto,
        onSuccess: () -> Unit = {}
    ) {
        if (isExactAlarmDeniedForSchedule(
                time = schedule.scheduledTime,
                reminder = schedule.reminderMinutesBefore
            )
        ) {
            _actionError.value = null
            _reminderNotice.value = "Turn on Alarms & reminders to use reminders."
            return
        }

        val plannerToday = plannerTodayProvider().toString()
        launchAction(onSuccess) {
            if (contentChanged) repository.updateTask(
                taskId, title, note, isImportant, plannerToday, plannerToday, schedule
            ) else repository.updateTaskSchedule(taskId, plannerToday, plannerToday, schedule)
        }
    }

    fun deleteTask(taskId: String, onSuccess: () -> Unit = {}) =
        launchAction(onSuccess) { repository.deleteTask(taskId) }

    private fun launchAction(onSuccess: () -> Unit, action: suspend () -> Result<Unit>) {
        coroutineScope.launch {
            _actionError.value = null
            _reminderNotice.value = null
            action().fold(
                onSuccess = { onSuccess() },
                onFailure = { error ->
                    if (error is HttpException && error.code() == 401) onSessionExpired()
                    else _actionError.value = "Unable to save task. Please try again."
                }
            )
        }
    }

    class Factory(private val context: Context, private val onSessionExpired: () -> Unit = {}) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val appContext = context.applicationContext
            val db = PlannerDatabase.getInstance(appContext)
            val sessionTokenStore = SessionTokenStore(appContext)
            val reminderScheduler = DefaultTaskReminderScheduler(appContext, db)
            val repository = PlannerRepository(db, sessionTokenStore, reminderScheduler = reminderScheduler)
            return PlannerTaskViewModel(
                repository = repository,
                reminderScheduler = reminderScheduler,
                onSessionExpired = onSessionExpired
            ) as T
        }
    }
}
