package com.vikaspokala.daybyday.data.repository

import com.vikaspokala.daybyday.data.local.auth.SessionTokenStore
import com.vikaspokala.daybyday.data.local.planner.PlannerDatabase
import com.vikaspokala.daybyday.data.local.planner.entity.toEntity
import com.vikaspokala.daybyday.data.remote.NetworkClient
import com.vikaspokala.daybyday.data.remote.api.PlannerApi
import com.vikaspokala.daybyday.data.remote.dto.CompleteTaskRequestDto
import com.vikaspokala.daybyday.data.remote.dto.CreateTaskRequestDto
import com.vikaspokala.daybyday.data.remote.dto.CreateTaskScheduleRequestDto
import com.vikaspokala.daybyday.data.remote.dto.UndoTaskRequestDto
import com.vikaspokala.daybyday.data.remote.dto.UpdateTaskContentRequestDto
import com.vikaspokala.daybyday.data.remote.dto.UpdateTaskRequestDto
import com.vikaspokala.daybyday.data.remote.dto.UpdateTaskScheduleDto
import com.vikaspokala.daybyday.data.remote.dto.UpdateTaskSchedulePayloadDto
import com.vikaspokala.daybyday.notification.TaskReminderScheduler
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

open class PlannerRepository(
    private val plannerDatabase: PlannerDatabase,
    private val tokenProvider: suspend () -> String?,
    private val plannerApi: PlannerApi = NetworkClient.plannerApi,
    private val mutex: Mutex = defaultMutex,
    private val reminderScheduler: TaskReminderScheduler? = null
) {

    constructor(
        plannerDatabase: PlannerDatabase,
        sessionTokenStore: SessionTokenStore,
        plannerApi: PlannerApi = NetworkClient.plannerApi,
        mutex: Mutex = defaultMutex,
        reminderScheduler: TaskReminderScheduler? = null
    ) : this(
        plannerDatabase = plannerDatabase,
        tokenProvider = { sessionTokenStore.readToken() },
        plannerApi = plannerApi,
        mutex = mutex,
        reminderScheduler = reminderScheduler
    )

    open suspend fun updateTask(
        taskId: String,
        title: String,
        note: String? = null,
        isImportant: Boolean,
        plannerToday: String,
        effectiveDate: String,
        schedule: UpdateTaskScheduleDto
    ): Result<Unit> = mutex.withLock {
        val token = tokenProvider()
            ?: return Result.failure(IllegalStateException("Missing authentication session token"))

        try {
            val request = UpdateTaskRequestDto(
                title = title,
                note = note,
                isImportant = isImportant,
                plannerToday = plannerToday,
                effectiveDate = effectiveDate,
                schedule = schedule
            )
            val response = plannerApi.updateTask("Bearer $token", taskId, request)
            val taskEntity = response.task.toEntity()
            val scheduleEntities = response.task.schedules.map { it.toEntity(taskEntity.id) }
            val completionEntities = response.completions.map { it.toEntity() }
            plannerDatabase.applyUpdateTaskSchedule(taskEntity, scheduleEntities, completionEntities)

            safelyScheduleTaskReminder(taskEntity.id)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun updateTaskSchedule(
        taskId: String,
        plannerToday: String,
        effectiveDate: String,
        schedule: UpdateTaskScheduleDto
    ): Result<Unit> = mutex.withLock {
        val token = tokenProvider()
            ?: return Result.failure(IllegalStateException("Missing authentication session token"))

        try {
            val request = UpdateTaskSchedulePayloadDto(
                plannerToday = plannerToday,
                effectiveDate = effectiveDate,
                schedule = schedule
            )
            val response = plannerApi.updateTaskSchedule("Bearer $token", taskId, request)
            val taskEntity = response.task.toEntity()
            val scheduleEntities = response.task.schedules.map { it.toEntity(taskEntity.id) }
            val completionEntities = response.completions.map { it.toEntity() }
            plannerDatabase.applyUpdateTaskSchedule(taskEntity, scheduleEntities, completionEntities)

            safelyScheduleTaskReminder(taskEntity.id)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun updateTaskContent(
        taskId: String,
        title: String,
        note: String? = null,
        isImportant: Boolean
    ): Result<Unit> = mutex.withLock {
        val token = tokenProvider()
            ?: return Result.failure(IllegalStateException("Missing authentication session token"))

        try {
            val request = UpdateTaskContentRequestDto(
                title = title,
                note = note,
                isImportant = isImportant
            )
            val response = plannerApi.updateTaskContent("Bearer $token", taskId, request)
            val taskEntity = response.task.toEntity()
            val completionEntities = response.completions.map { it.toEntity() }
            plannerDatabase.applyUpdateTaskContent(taskEntity, completionEntities)

            safelyScheduleTaskReminder(taskEntity.id)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun createTask(
        taskId: String,
        title: String,
        note: String? = null,
        isImportant: Boolean = false,
        plannerToday: String? = null,
        schedule: CreateTaskScheduleRequestDto? = null
    ): Result<Unit> = mutex.withLock {
        val token = tokenProvider()
            ?: return Result.failure(IllegalStateException("Missing authentication session token"))

        try {
            val request = CreateTaskRequestDto(
                id = taskId,
                title = title,
                note = note,
                isImportant = isImportant,
                plannerToday = plannerToday,
                schedule = schedule
            )
            val response = plannerApi.createTask("Bearer $token", request)
            val taskEntity = response.task.toEntity()
            val scheduleEntities = response.task.schedules.map { it.toEntity(response.task.id) }
            plannerDatabase.applyCreateTask(taskEntity, scheduleEntities)

            safelyScheduleTaskReminder(taskEntity.id)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun deleteTask(taskId: String): Result<Unit> = mutex.withLock {
        val token = tokenProvider()
            ?: return Result.failure(IllegalStateException("Missing authentication session token"))

        try {
            val response = plannerApi.deleteTask("Bearer $token", taskId)
            plannerDatabase.applyDeleteTask(response.deletedTaskId)
            safelyCancelReminder(response.deletedTaskId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun refresh(): Result<Unit> = mutex.withLock {
        val token = tokenProvider()
            ?: return Result.failure(IllegalStateException("Missing authentication session token"))

        try {
            val response = plannerApi.getRefresh("Bearer $token")
            val taskEntities = response.tasks.map { it.toEntity() }
            val scheduleEntities = response.schedules.map { it.toEntity() }
            val completionEntities = response.completions.map { it.toEntity() }

            plannerDatabase.replaceSnapshot(
                tasks = taskEntities,
                schedules = scheduleEntities,
                completions = completionEntities
            )
            safelyCancelAllReminders()
            safelyRescheduleAllFromDatabase()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun completeTask(
        taskId: String,
        plannerToday: String,
        completedDate: String,
        scheduleId: String? = null,
        scheduledDate: String? = null
    ): Result<Unit> = mutex.withLock {
        val token = tokenProvider()
            ?: return Result.failure(IllegalStateException("Missing authentication session token"))

        try {
            val request = CompleteTaskRequestDto(
                plannerToday = plannerToday,
                completedDate = completedDate,
                scheduleId = scheduleId,
                scheduledDate = scheduledDate
            )
            val response = plannerApi.completeTask("Bearer $token", taskId, request)
            plannerDatabase.applyComplete(response.toEntity())
            safelyScheduleTaskReminder(taskId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun undoTask(
        taskId: String,
        scheduleId: String? = null,
        scheduledDate: String? = null
    ): Result<Unit> = mutex.withLock {
        val token = tokenProvider()
            ?: return Result.failure(IllegalStateException("Missing authentication session token"))

        try {
            val request = UndoTaskRequestDto(
                scheduleId = scheduleId,
                scheduledDate = scheduledDate
            )
            plannerApi.undoTask("Bearer $token", taskId, request)
            plannerDatabase.applyUndo(
                taskId = taskId,
                scheduleId = scheduleId,
                scheduledDate = scheduledDate
            )

            safelyScheduleTaskReminder(taskId)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun safelyScheduleTaskReminder(taskId: String) {
        try {
            reminderScheduler?.scheduleTaskReminder(taskId)
        } catch (_: Exception) {
            // Reminder scheduling failure must never fail the planner task mutation
        }
    }

    private fun safelyScheduleOnceReminder(
        taskId: String,
        title: String,
        startDate: String,
        scheduledTime: String?,
        reminderMinutesBefore: Int?
    ) {
        try {
            reminderScheduler?.scheduleOnceReminder(
                taskId = taskId,
                title = title,
                startDate = startDate,
                scheduledTime = scheduledTime,
                reminderMinutesBefore = reminderMinutesBefore
            )
        } catch (_: Exception) {
            // Reminder scheduling failure must never fail the planner task mutation
        }
    }

    private fun safelyCancelReminder(taskId: String) {
        try {
            reminderScheduler?.cancelReminder(taskId)
        } catch (_: Exception) {
            // Reminder cancellation failure must never fail the planner task mutation
        }
    }

    private suspend fun safelyCancelAllReminders() {
        try {
            reminderScheduler?.cancelAllReminders()
        } catch (_: Exception) {
            // Safe fallback
        }
    }

    private suspend fun safelyRescheduleAllFromDatabase() {
        try {
            reminderScheduler?.rescheduleAllFromDatabase()
        } catch (_: Exception) {
            // Safe fallback
        }
    }

    open fun observeScheduledOccurrences(date: java.time.LocalDate): kotlinx.coroutines.flow.Flow<List<com.vikaspokala.daybyday.data.local.planner.ScheduledOccurrence>> {
        return plannerDatabase.observeScheduledOccurrences(date)
    }

    companion object {
        val defaultMutex = Mutex()
    }
}
