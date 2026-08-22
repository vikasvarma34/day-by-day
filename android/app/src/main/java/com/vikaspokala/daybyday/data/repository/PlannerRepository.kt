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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

open class PlannerRepository(
    private val plannerDatabase: PlannerDatabase,
    private val tokenProvider: suspend () -> String?,
    private val plannerApi: PlannerApi = NetworkClient.plannerApi,
    private val mutex: Mutex = defaultMutex
) {

    constructor(
        plannerDatabase: PlannerDatabase,
        sessionTokenStore: SessionTokenStore,
        plannerApi: PlannerApi = NetworkClient.plannerApi,
        mutex: Mutex = defaultMutex
    ) : this(
        plannerDatabase = plannerDatabase,
        tokenProvider = { sessionTokenStore.readToken() },
        plannerApi = plannerApi,
        mutex = mutex
    )

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
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        val defaultMutex = Mutex()
    }
}
