package com.vikaspokala.daybyday.data.repository

import androidx.room.DatabaseConfiguration
import androidx.room.InvalidationTracker
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.vikaspokala.daybyday.data.local.planner.PlannerDatabase
import com.vikaspokala.daybyday.data.local.planner.dao.CompletionDao
import com.vikaspokala.daybyday.data.local.planner.dao.HistoryCompletionRow
import com.vikaspokala.daybyday.data.local.planner.dao.ScheduleDao
import com.vikaspokala.daybyday.data.local.planner.dao.TaskDao
import com.vikaspokala.daybyday.data.local.planner.entity.CompletionEntity
import com.vikaspokala.daybyday.data.local.planner.entity.ScheduleEntity
import com.vikaspokala.daybyday.data.local.planner.entity.TaskEntity
import com.vikaspokala.daybyday.data.remote.api.PlannerApi
import com.vikaspokala.daybyday.data.remote.dto.CompleteTaskRequestDto
import com.vikaspokala.daybyday.data.remote.dto.CompleteTaskResponseDto
import com.vikaspokala.daybyday.data.remote.dto.CreateTaskRequestDto
import com.vikaspokala.daybyday.data.remote.dto.CreateTaskResponseDto
import com.vikaspokala.daybyday.data.remote.dto.CreateTaskScheduleRequestDto
import com.vikaspokala.daybyday.data.remote.dto.DeleteTaskResponseDto
import com.vikaspokala.daybyday.data.remote.dto.PlannerRefreshResponseDto
import com.vikaspokala.daybyday.data.remote.dto.RefreshCompletionDto
import com.vikaspokala.daybyday.data.remote.dto.RefreshScheduleDto
import com.vikaspokala.daybyday.data.remote.dto.RefreshTaskDto
import com.vikaspokala.daybyday.data.remote.dto.TaskActionResponseDto
import com.vikaspokala.daybyday.data.remote.dto.TaskResponseDto
import com.vikaspokala.daybyday.data.remote.dto.TaskScheduleResponseDto
import com.vikaspokala.daybyday.data.remote.dto.UndoTaskRequestDto
import com.vikaspokala.daybyday.data.remote.dto.UpdateTaskContentRequestDto
import com.vikaspokala.daybyday.data.remote.dto.UpdateTaskRequestDto
import com.vikaspokala.daybyday.data.remote.dto.UpdateTaskResponseDto
import com.vikaspokala.daybyday.data.remote.dto.UpdateTaskScheduleDto
import com.vikaspokala.daybyday.data.remote.dto.UpdateTaskSchedulePayloadDto
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class PlannerRepositoryTest {

    private class FakePlannerApi(
        var createResult: Result<CreateTaskResponseDto>? = null,
        var refreshResult: Result<PlannerRefreshResponseDto>? = null,
        var completeResult: Result<CompleteTaskResponseDto> = Result.success(
            CompleteTaskResponseDto(
                id = "default-comp-id",
                taskId = "default-task-id",
                completedDate = "2026-08-18",
                completedAt = "2026-08-18T10:00:00.000Z",
                isImportantSnapshot = false
            )
        ),
        var undoResult: Result<TaskActionResponseDto> = Result.success(TaskActionResponseDto(true)),
        var deleteResult: Result<DeleteTaskResponseDto> = Result.success(DeleteTaskResponseDto("default-task-id")),
        var updateContentResult: Result<UpdateTaskResponseDto>? = null,
        var updateScheduleResult: Result<UpdateTaskResponseDto>? = null,
        var updateTaskResult: Result<UpdateTaskResponseDto>? = null,
        var delayRefreshDeferred: CompletableDeferred<Unit>? = null
    ) : PlannerApi {
        var lastAuthorizationHeader: String? = null
        var lastCreateRequest: CreateTaskRequestDto? = null
        var lastCompleteTaskId: String? = null
        var lastCompleteRequest: CompleteTaskRequestDto? = null
        var lastUndoTaskId: String? = null
        var lastUndoRequest: UndoTaskRequestDto? = null
        var lastDeleteTaskId: String? = null
        var lastUpdateContentTaskId: String? = null
        var lastUpdateContentRequest: UpdateTaskContentRequestDto? = null
        var lastUpdateScheduleTaskId: String? = null
        var lastUpdateScheduleRequest: UpdateTaskSchedulePayloadDto? = null
        var lastUpdateTaskId: String? = null
        var lastUpdateTaskRequest: UpdateTaskRequestDto? = null
        var createCallCount = 0
        var getRefreshCallCount = 0
        var completeCallCount = 0
        var undoCallCount = 0
        var deleteCallCount = 0
        var updateContentCallCount = 0
        var updateScheduleCallCount = 0
        var updateTaskCallCount = 0

        override suspend fun updateTask(
            authorization: String,
            taskId: String,
            request: UpdateTaskRequestDto
        ): UpdateTaskResponseDto {
            updateTaskCallCount++
            lastAuthorizationHeader = authorization
            lastUpdateTaskId = taskId
            lastUpdateTaskRequest = request
            return updateTaskResult?.getOrThrow() ?: error("updateTaskResult not configured")
        }

        override suspend fun updateTaskSchedule(
            authorization: String,
            taskId: String,
            request: UpdateTaskSchedulePayloadDto
        ): UpdateTaskResponseDto {
            updateScheduleCallCount++
            lastAuthorizationHeader = authorization
            lastUpdateScheduleTaskId = taskId
            lastUpdateScheduleRequest = request
            return updateScheduleResult?.getOrThrow() ?: error("updateScheduleResult not configured")
        }

        override suspend fun updateTaskContent(
            authorization: String,
            taskId: String,
            request: UpdateTaskContentRequestDto
        ): UpdateTaskResponseDto {
            updateContentCallCount++
            lastAuthorizationHeader = authorization
            lastUpdateContentTaskId = taskId
            lastUpdateContentRequest = request
            return updateContentResult?.getOrThrow() ?: error("updateContentResult not configured")
        }

        override suspend fun deleteTask(
            authorization: String,
            taskId: String
        ): DeleteTaskResponseDto {
            deleteCallCount++
            lastAuthorizationHeader = authorization
            lastDeleteTaskId = taskId
            return deleteResult.getOrThrow()
        }

        override suspend fun createTask(
            authorization: String,
            request: CreateTaskRequestDto
        ): CreateTaskResponseDto {
            createCallCount++
            lastAuthorizationHeader = authorization
            lastCreateRequest = request
            return createResult?.getOrThrow() ?: error("createResult not configured")
        }

        override suspend fun getRefresh(authorization: String): PlannerRefreshResponseDto {
            getRefreshCallCount++
            lastAuthorizationHeader = authorization
            delayRefreshDeferred?.await()
            return refreshResult?.getOrThrow() ?: error("refreshResult not configured")
        }

        override suspend fun completeTask(
            authorization: String,
            taskId: String,
            request: CompleteTaskRequestDto
        ): CompleteTaskResponseDto {
            completeCallCount++
            lastAuthorizationHeader = authorization
            lastCompleteTaskId = taskId
            lastCompleteRequest = request
            return completeResult.getOrThrow()
        }

        override suspend fun undoTask(
            authorization: String,
            taskId: String,
            request: UndoTaskRequestDto
        ): TaskActionResponseDto {
            undoCallCount++
            lastAuthorizationHeader = authorization
            lastUndoTaskId = taskId
            lastUndoRequest = request
            return undoResult.getOrThrow()
        }
    }

    private class InMemoryTestDatabase : PlannerDatabase() {
        val tasksMap = mutableMapOf<String, TaskEntity>()
        val schedulesMap = mutableMapOf<String, ScheduleEntity>()
        val completionsList = mutableListOf<CompletionEntity>()

        var applyCreateTaskCalled = false
        var applyCompleteCalled = false
        var applyUndoCalled = false
        var applyDeleteTaskCalled = false
        var applyUpdateTaskContentCalled = false
        var applyUpdateTaskScheduleCalled = false

        private val fakeTaskDao = object : TaskDao {
            override suspend fun insertAll(tasks: List<TaskEntity>) {
                tasks.forEach { tasksMap[it.id] = it }
            }
            override suspend fun insert(task: TaskEntity) {
                tasksMap[task.id] = task
            }
            override suspend fun deleteAll() { tasksMap.clear() }
            override suspend fun deleteById(taskId: String) {
                tasksMap.remove(taskId)
            }
            override suspend fun getAll(): List<TaskEntity> = tasksMap.values.toList()
            override suspend fun getById(id: String): TaskEntity? = tasksMap[id]
        }

        private val fakeScheduleDao = object : ScheduleDao {
            override suspend fun insertAll(schedules: List<ScheduleEntity>) {
                schedules.forEach { schedulesMap[it.id] = it }
            }
            override suspend fun insert(schedule: ScheduleEntity) {
                schedulesMap[schedule.id] = schedule
            }
            override suspend fun deleteAll() { schedulesMap.clear() }
            override suspend fun deleteByTaskId(taskId: String) {
                val toRemove = schedulesMap.values.filter { it.taskId == taskId }.map { it.id }
                toRemove.forEach { schedulesMap.remove(it) }
            }
            override suspend fun getByTaskId(taskId: String): List<ScheduleEntity> {
                return schedulesMap.values.filter { it.taskId == taskId }
            }
            override suspend fun deleteByIds(ids: List<String>) {
                ids.forEach { schedulesMap.remove(it) }
            }
            override suspend fun getAll(): List<ScheduleEntity> = schedulesMap.values.toList()
            override suspend fun getById(id: String): ScheduleEntity? = schedulesMap[id]
        }

        private val fakeCompletionDao = object : CompletionDao {
            override suspend fun insertAll(completions: List<CompletionEntity>) {
                completions.forEach { insert(it) }
            }
            override suspend fun insert(completion: CompletionEntity) {
                completionsList.removeAll { it.id == completion.id }
                completionsList.add(completion)
            }
            override suspend fun deleteAll() { completionsList.clear() }
            override suspend fun deleteByTaskId(taskId: String) {
                completionsList.removeAll { it.taskId == taskId }
            }
            override suspend fun deleteByScheduleIds(scheduleIds: List<String>) {
                completionsList.removeAll { it.scheduleId in scheduleIds }
            }
            override suspend fun getAll(): List<CompletionEntity> = completionsList.toList()
            override suspend fun findLaterCompletion(taskId: String): CompletionEntity? {
                return completionsList.find { it.taskId == taskId && it.scheduleId == null && it.scheduledDate == null }
            }
            override suspend fun findScheduledCompletion(taskId: String, scheduleId: String, scheduledDate: String): CompletionEntity? {
                return completionsList.find { it.taskId == taskId && it.scheduleId == scheduleId && it.scheduledDate == scheduledDate }
            }
            override suspend fun deleteLaterCompletion(taskId: String) {
                completionsList.removeAll { it.taskId == taskId && it.scheduleId == null && it.scheduledDate == null }
            }
            override suspend fun deleteScheduledCompletion(taskId: String, scheduleId: String, scheduledDate: String) {
                completionsList.removeAll { it.taskId == taskId && it.scheduleId == scheduleId && it.scheduledDate == scheduledDate }
            }
            override fun observeHistory(plannerToday: String): Flow<List<HistoryCompletionRow>> = emptyFlow()
        }

        override fun taskDao(): TaskDao = fakeTaskDao
        override fun scheduleDao(): ScheduleDao = fakeScheduleDao
        override fun completionDao(): CompletionDao = fakeCompletionDao

        override fun createInvalidationTracker(): InvalidationTracker = error("Not needed")
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun createOpenHelper(config: DatabaseConfiguration): SupportSQLiteOpenHelper = error("Not needed")
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun clearAllTables() {}

        override suspend fun replaceSnapshot(
            tasks: List<TaskEntity>,
            schedules: List<ScheduleEntity>,
            completions: List<CompletionEntity>
        ) {
            tasksMap.clear()
            tasks.forEach { tasksMap[it.id] = it }
            schedulesMap.clear()
            schedules.forEach { schedulesMap[it.id] = it }
            completionsList.clear()
            completionsList.addAll(completions)
        }

        override suspend fun applyCreateTask(task: TaskEntity, schedules: List<ScheduleEntity>) {
            applyCreateTaskCalled = true
            fakeTaskDao.insert(task)
            if (schedules.isNotEmpty()) {
                fakeScheduleDao.insertAll(schedules)
            }
        }

        override suspend fun applyComplete(completion: CompletionEntity) {
            applyCompleteCalled = true
            super.applyComplete(completion)
        }

        override suspend fun applyUndo(taskId: String, scheduleId: String?, scheduledDate: String?) {
            applyUndoCalled = true
            super.applyUndo(taskId, scheduleId, scheduledDate)
        }

        override suspend fun applyDeleteTask(taskId: String) {
            applyDeleteTaskCalled = true
            fakeCompletionDao.deleteByTaskId(taskId)
            fakeScheduleDao.deleteByTaskId(taskId)
            fakeTaskDao.deleteById(taskId)
        }

        override suspend fun applyUpdateTaskContent(task: TaskEntity, completions: List<CompletionEntity>) {
            applyUpdateTaskContentCalled = true
            fakeTaskDao.insert(task)
            if (completions.isNotEmpty()) {
                fakeCompletionDao.insertAll(completions)
            }
        }

        override suspend fun applyUpdateTaskSchedule(
            task: TaskEntity,
            schedules: List<ScheduleEntity>,
            completions: List<CompletionEntity>
        ) {
            applyUpdateTaskScheduleCalled = true
            fakeTaskDao.insert(task)
            val currentSchedules = fakeScheduleDao.getByTaskId(task.id)
            val responseScheduleIds = schedules.map { it.id }.toSet()
            val staleScheduleIds = currentSchedules.map { it.id }.filter { it !in responseScheduleIds }
            if (staleScheduleIds.isNotEmpty()) {
                fakeCompletionDao.deleteByScheduleIds(staleScheduleIds)
                fakeScheduleDao.deleteByIds(staleScheduleIds)
            }
            if (schedules.isNotEmpty()) {
                fakeCompletionDao.deleteLaterCompletion(task.id)
                fakeScheduleDao.insertAll(schedules)
            }
            if (completions.isNotEmpty()) {
                fakeCompletionDao.insertAll(completions)
            }
        }
    }

    @Test
    fun createTask_missingToken_returnsFailureWithoutCallingNetwork() = runTest {
        val fakeApi = FakePlannerApi()
        val fakeDb = InMemoryTestDatabase()
        val repository = PlannerRepository(fakeDb, tokenProvider = { null }, fakeApi)

        val result = repository.createTask(
            taskId = "client-uuid-1",
            title = "Task without auth"
        )

        assertTrue(result.isFailure)
        assertEquals("Missing authentication session token", result.exceptionOrNull()?.message)
        assertEquals(0, fakeApi.createCallCount)
        assertNull(fakeApi.lastAuthorizationHeader)
    }

    @Test
    fun createTask_laterTask_sendsExactContractAndStoresCanonicalTaskInRoom() = runTest {
        val canonicalTask = TaskResponseDto(
            id = "caller-uuid-100",
            title = "Later Task",
            note = "Some note",
            isImportant = true,
            createdAt = "2026-08-22T10:00:00.000Z",
            updatedAt = "2026-08-22T10:00:00.000Z",
            schedules = emptyList()
        )
        val fakeApi = FakePlannerApi(createResult = Result.success(CreateTaskResponseDto(canonicalTask)))
        val fakeDb = InMemoryTestDatabase()
        val repository = PlannerRepository(fakeDb, tokenProvider = { "token_123" }, fakeApi)

        val result = repository.createTask(
            taskId = "caller-uuid-100",
            title = "Later Task",
            note = "Some note",
            isImportant = true
        )

        assertTrue(result.isSuccess)
        assertEquals("Bearer token_123", fakeApi.lastAuthorizationHeader)
        assertEquals(1, fakeApi.createCallCount)
        assertEquals(0, fakeApi.getRefreshCallCount) // No follow up GET

        assertEquals(
            CreateTaskRequestDto(
                id = "caller-uuid-100",
                title = "Later Task",
                note = "Some note",
                isImportant = true,
                plannerToday = null,
                schedule = null
            ),
            fakeApi.lastCreateRequest
        )

        assertTrue(fakeDb.applyCreateTaskCalled)
        assertEquals(1, fakeDb.tasksMap.size)
        val task = fakeDb.tasksMap["caller-uuid-100"]
        assertNotNull(task)
        assertEquals("caller-uuid-100", task?.id)
        assertEquals("Later Task", task?.title)
        assertEquals("Some note", task?.note)
        assertEquals(true, task?.isImportant)
        assertEquals("2026-08-22T10:00:00.000Z", task?.createdAt)
        assertEquals("2026-08-22T10:00:00.000Z", task?.updatedAt)
        assertEquals(0, fakeDb.schedulesMap.size)
    }

    @Test
    fun createTask_scheduledOnceTask_sendsExactContractAndStoresCanonicalTaskAndSchedule() = runTest {
        val canonicalSchedule = TaskScheduleResponseDto(
            id = "server-sched-uuid-555",
            type = "ONCE",
            startDate = "2026-08-22",
            endDate = "2026-08-22",
            scheduledTime = "14:00:00",
            intervalDays = null,
            intervalAnchorDate = null,
            weekdaysMask = null,
            reminderMinutesBefore = 10,
            createdAt = "2026-08-22T10:00:00.000Z",
            updatedAt = "2026-08-22T10:00:00.000Z"
        )
        val canonicalTask = TaskResponseDto(
            id = "caller-uuid-200",
            title = "Once Task",
            note = null,
            isImportant = false,
            createdAt = "2026-08-22T10:00:00.000Z",
            updatedAt = "2026-08-22T10:00:00.000Z",
            schedules = listOf(canonicalSchedule)
        )
        val fakeApi = FakePlannerApi(createResult = Result.success(CreateTaskResponseDto(canonicalTask)))
        val fakeDb = InMemoryTestDatabase()
        val repository = PlannerRepository(fakeDb, tokenProvider = { "token_123" }, fakeApi)

        val scheduleReq = CreateTaskScheduleRequestDto(
            type = "ONCE",
            startDate = "2026-08-22",
            endDate = "2026-08-22",
            scheduledTime = "14:00:00",
            reminderMinutesBefore = 10
        )
        val result = repository.createTask(
            taskId = "caller-uuid-200",
            title = "Once Task",
            plannerToday = "2026-08-22",
            schedule = scheduleReq
        )

        assertTrue(result.isSuccess)
        assertEquals(1, fakeApi.createCallCount)
        assertEquals(0, fakeApi.getRefreshCallCount)

        assertEquals("caller-uuid-200", fakeApi.lastCreateRequest?.id)
        assertEquals("2026-08-22", fakeApi.lastCreateRequest?.plannerToday)
        assertEquals(scheduleReq, fakeApi.lastCreateRequest?.schedule)

        val task = fakeDb.tasksMap["caller-uuid-200"]
        assertNotNull(task)
        assertEquals("Once Task", task?.title)
        assertEquals("2026-08-22T10:00:00.000Z", task?.createdAt)

        val schedule = fakeDb.schedulesMap["server-sched-uuid-555"]
        assertNotNull(schedule)
        assertEquals("server-sched-uuid-555", schedule?.id)
        assertEquals("caller-uuid-200", schedule?.taskId)
        assertEquals("ONCE", schedule?.scheduleType)
        assertEquals("2026-08-22", schedule?.startDate)
        assertEquals("14:00:00", schedule?.scheduledTime)
        assertEquals(10, schedule?.reminderMinutesBefore)
        assertEquals("2026-08-22T10:00:00.000Z", schedule?.createdAt)
    }

    @Test
    fun createTask_recurringWeekdays_preservesRecurrenceFields() = runTest {
        val canonicalSchedule = TaskScheduleResponseDto(
            id = "server-sched-weekdays",
            type = "WEEKDAYS",
            startDate = "2026-08-22",
            endDate = null,
            scheduledTime = null,
            intervalDays = null,
            intervalAnchorDate = null,
            weekdaysMask = 65,
            reminderMinutesBefore = null,
            createdAt = "2026-08-22T10:00:00.000Z",
            updatedAt = "2026-08-22T10:00:00.000Z"
        )
        val canonicalTask = TaskResponseDto(
            id = "caller-uuid-300",
            title = "Weekdays Task",
            note = null,
            isImportant = false,
            createdAt = "2026-08-22T10:00:00.000Z",
            updatedAt = "2026-08-22T10:00:00.000Z",
            schedules = listOf(canonicalSchedule)
        )
        val fakeApi = FakePlannerApi(createResult = Result.success(CreateTaskResponseDto(canonicalTask)))
        val fakeDb = InMemoryTestDatabase()
        val repository = PlannerRepository(fakeDb, tokenProvider = { "token_123" }, fakeApi)

        val scheduleReq = CreateTaskScheduleRequestDto(
            type = "WEEKDAYS",
            startDate = "2026-08-22",
            weekdaysMask = 65
        )
        val result = repository.createTask(
            taskId = "caller-uuid-300",
            title = "Weekdays Task",
            plannerToday = "2026-08-22",
            schedule = scheduleReq
        )

        assertTrue(result.isSuccess)
        assertEquals(65, fakeApi.lastCreateRequest?.schedule?.weekdaysMask)

        val storedSched = fakeDb.schedulesMap["server-sched-weekdays"]
        assertNotNull(storedSched)
        assertEquals(65, storedSched?.weekdaysMask)
        assertEquals("WEEKDAYS", storedSched?.scheduleType)
    }

    @Test
    fun createTask_idempotentRetry_safelyUpdatesRoomWithoutDuplicates() = runTest {
        val canonicalSchedule = TaskScheduleResponseDto(
            id = "server-sched-1",
            type = "ONCE",
            startDate = "2026-08-22",
            createdAt = "2026-08-22T10:00:00.000Z",
            updatedAt = "2026-08-22T10:00:00.000Z"
        )
        val canonicalTask = TaskResponseDto(
            id = "caller-uuid-400",
            title = "Original Title",
            note = null,
            isImportant = false,
            createdAt = "2026-08-22T10:00:00.000Z",
            updatedAt = "2026-08-22T10:00:00.000Z",
            schedules = listOf(canonicalSchedule)
        )
        val fakeApi = FakePlannerApi(createResult = Result.success(CreateTaskResponseDto(canonicalTask)))
        val fakeDb = InMemoryTestDatabase()
        val repository = PlannerRepository(fakeDb, tokenProvider = { "token_123" }, fakeApi)

        // 1. First invocation
        val res1 = repository.createTask(
            taskId = "caller-uuid-400",
            title = "Original Title",
            plannerToday = "2026-08-22",
            schedule = CreateTaskScheduleRequestDto(type = "ONCE", startDate = "2026-08-22")
        )
        assertTrue(res1.isSuccess)
        assertEquals(1, fakeDb.tasksMap.size)
        assertEquals(1, fakeDb.schedulesMap.size)

        // 2. Retry invocation reusing the same UUID
        val res2 = repository.createTask(
            taskId = "caller-uuid-400",
            title = "Original Title",
            plannerToday = "2026-08-22",
            schedule = CreateTaskScheduleRequestDto(type = "ONCE", startDate = "2026-08-22")
        )
        assertTrue(res2.isSuccess)
        // Prove: no duplicates
        assertEquals(1, fakeDb.tasksMap.size)
        assertEquals(1, fakeDb.schedulesMap.size)
        assertEquals("Original Title", fakeDb.tasksMap["caller-uuid-400"]?.title)
    }

    @Test
    fun createTask_retryDoesNotDestroyReferencedCompletionsOrSchedules() = runTest {
        val canonicalSchedule = TaskScheduleResponseDto(
            id = "sched-450",
            type = "ONCE",
            startDate = "2026-08-22",
            createdAt = "2026-08-22T10:00:00.000Z",
            updatedAt = "2026-08-22T10:00:00.000Z"
        )
        val canonicalTask = TaskResponseDto(
            id = "task-450",
            title = "Task with Completion",
            note = null,
            isImportant = false,
            createdAt = "2026-08-22T10:00:00.000Z",
            updatedAt = "2026-08-22T10:00:00.000Z",
            schedules = listOf(canonicalSchedule)
        )
        val fakeApi = FakePlannerApi(createResult = Result.success(CreateTaskResponseDto(canonicalTask)))
        val fakeDb = InMemoryTestDatabase()

        // 1. Task exists
        fakeDb.tasksMap["task-450"] = TaskEntity(
            id = "task-450",
            title = "Task with Completion",
            note = null,
            isImportant = false,
            createdAt = "2026-08-22T10:00:00.000Z",
            updatedAt = "2026-08-22T10:00:00.000Z"
        )
        // 2. Schedule exists
        fakeDb.schedulesMap["sched-450"] = ScheduleEntity(
            id = "sched-450",
            taskId = "task-450",
            scheduleType = "ONCE",
            startDate = "2026-08-22",
            endDate = null,
            scheduledTime = null,
            intervalDays = null,
            intervalAnchorDate = null,
            weekdaysMask = null,
            reminderMinutesBefore = null,
            createdAt = "2026-08-22T10:00:00.000Z",
            updatedAt = "2026-08-22T10:00:00.000Z"
        )
        // 3. Completion exists referencing that task/schedule
        val existingCompletion = CompletionEntity(
            id = "comp-450",
            taskId = "task-450",
            scheduleId = "sched-450",
            scheduledDate = "2026-08-22",
            completedDate = "2026-08-22",
            completedAt = "2026-08-22T10:30:00.000Z",
            titleSnapshot = "Task with Completion",
            isImportantSnapshot = false
        )
        fakeDb.completionsList.add(existingCompletion)

        val repository = PlannerRepository(fakeDb, tokenProvider = { "token_123" }, fakeApi)

        // 4. Same canonical Create aggregate is applied again
        val retryResult = repository.createTask(
            taskId = "task-450",
            title = "Task with Completion",
            plannerToday = "2026-08-22",
            schedule = CreateTaskScheduleRequestDto(type = "ONCE", startDate = "2026-08-22")
        )

        assertTrue(retryResult.isSuccess)
        // 5. Task still exists
        assertEquals(1, fakeDb.tasksMap.size)
        assertNotNull(fakeDb.tasksMap["task-450"])
        // 6. Schedule still exists
        assertEquals(1, fakeDb.schedulesMap.size)
        assertNotNull(fakeDb.schedulesMap["sched-450"])
        // 7. Completion still exists
        assertEquals(1, fakeDb.completionsList.size)
        assertEquals(existingCompletion, fakeDb.completionsList.first())
        // 8. No duplicates exist
        assertEquals(1, fakeDb.tasksMap.size)
        assertEquals(1, fakeDb.schedulesMap.size)
        assertEquals(1, fakeDb.completionsList.size)
    }

    @Test
    fun createTask_networkFailure_leavesRoomUntouched() = runTest {
        val fakeApi = FakePlannerApi(createResult = Result.failure(IOException("No network")))
        val fakeDb = InMemoryTestDatabase()
        val repository = PlannerRepository(fakeDb, tokenProvider = { "token_123" }, fakeApi)

        val result = repository.createTask(
            taskId = "caller-uuid-fail",
            title = "Failing Task"
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals(0, fakeDb.tasksMap.size)
        assertEquals(0, fakeDb.schedulesMap.size)
    }

    @Test
    fun createTask_401Unauthorized_propagatesHttpException() = runTest {
        val http401 = HttpException(
            Response.error<CreateTaskResponseDto>(
                401,
                "{}".toResponseBody("application/json".toMediaType())
            )
        )
        val fakeApi = FakePlannerApi(createResult = Result.failure(http401))
        val fakeDb = InMemoryTestDatabase()
        val repository = PlannerRepository(fakeDb, tokenProvider = { "token_123" }, fakeApi)

        val result = repository.createTask(
            taskId = "caller-uuid-401",
            title = "Unauthorized Task"
        )

        assertTrue(result.isFailure)
        val err = result.exceptionOrNull()
        assertTrue(err is HttpException)
        assertEquals(401, (err as HttpException).code())
        assertEquals(0, fakeDb.tasksMap.size)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun sharedMutex_serializesRefreshAndCreate_staleRefreshCannotOverwriteNewerCreate() = runTest {
        val sharedMutex = Mutex()
        val refreshDeferred = CompletableDeferred<Unit>()

        val staleSnapshot = PlannerRefreshResponseDto(
            tasks = listOf(
                RefreshTaskDto(
                    id = "existing-task",
                    title = "Existing Task",
                    note = null,
                    isImportant = false,
                    createdAt = "2026-08-22T10:00:00.000Z",
                    updatedAt = "2026-08-22T10:00:00.000Z"
                )
            ),
            schedules = emptyList(),
            completions = emptyList()
        )

        val canonicalTask = TaskResponseDto(
            id = "created-task-1",
            title = "New Created Task",
            note = null,
            isImportant = false,
            createdAt = "2026-08-22T10:00:00.000Z",
            updatedAt = "2026-08-22T10:00:00.000Z",
            schedules = emptyList()
        )

        val fakeApi = FakePlannerApi(
            refreshResult = Result.success(staleSnapshot),
            createResult = Result.success(CreateTaskResponseDto(canonicalTask)),
            delayRefreshDeferred = refreshDeferred
        )
        val fakeDb = InMemoryTestDatabase()

        val repo1 = PlannerRepository(fakeDb, { "token" }, fakeApi, sharedMutex)
        val repo2 = PlannerRepository(fakeDb, { "token" }, fakeApi, sharedMutex)

        // 1. Start Refresh first (acquires lock, pauses on refreshDeferred)
        val refreshJob = async { repo1.refresh() }
        testScheduler.runCurrent()

        assertEquals(1, fakeApi.getRefreshCallCount)
        assertTrue(sharedMutex.isLocked)

        // 2. Trigger Create while Refresh is in-flight
        val createJob = async {
            repo2.createTask(
                taskId = "created-task-1",
                title = "New Created Task"
            )
        }
        testScheduler.runCurrent()

        // Create is queued behind Mutex and has NOT called network yet
        assertEquals(0, fakeApi.createCallCount)

        // 3. Complete Refresh response
        refreshDeferred.complete(Unit)
        testScheduler.runCurrent()
        refreshJob.await()

        // 4. Create finishes and applies to Room
        createJob.await()

        assertEquals(1, fakeApi.createCallCount)
        assertEquals("created-task-1", fakeDb.tasksMap["created-task-1"]?.id)
    }

    @Test
    fun refresh_missingToken_returnsFailureWithoutCallingNetwork() = runTest {
        val fakeApi = FakePlannerApi()
        val fakeDb = InMemoryTestDatabase()
        val repository = PlannerRepository(fakeDb, tokenProvider = { null }, fakeApi)

        val result = repository.refresh()

        assertTrue(result.isFailure)
        assertEquals("Missing authentication session token", result.exceptionOrNull()?.message)
        assertNull(fakeApi.lastAuthorizationHeader)
    }

    @Test
    fun refresh_authenticated_fetchesAndReplacesSnapshotAtomically() = runTest {
        val sampleResponse = PlannerRefreshResponseDto(
            tasks = listOf(
                RefreshTaskDto(
                    id = "task-1",
                    title = "Read Room Docs",
                    note = "Study Room transactions",
                    isImportant = true,
                    createdAt = "2026-08-18T10:00:00.000Z",
                    updatedAt = "2026-08-18T10:00:00.000Z"
                )
            ),
            schedules = listOf(
                RefreshScheduleDto(
                    id = "sched-1",
                    taskId = "task-1",
                    scheduleType = "WEEKDAYS",
                    startDate = "2026-08-18",
                    endDate = null,
                    scheduledTime = "09:00:00",
                    intervalDays = null,
                    intervalAnchorDate = null,
                    weekdaysMask = 31,
                    reminderMinutesBefore = 10,
                    createdAt = "2026-08-18T10:00:00.000Z",
                    updatedAt = "2026-08-18T10:00:00.000Z"
                )
            ),
            completions = listOf(
                RefreshCompletionDto(
                    id = "comp-1",
                    taskId = "task-1",
                    scheduleId = "sched-1",
                    scheduledDate = "2026-08-18",
                    completedDate = "2026-08-18",
                    completedAt = "2026-08-18T09:30:00.000Z",
                    titleSnapshot = "Read Room Docs",
                    isImportantSnapshot = true
                )
            )
        )
        val fakeApi = FakePlannerApi(refreshResult = Result.success(sampleResponse))
        val fakeDb = InMemoryTestDatabase()
        val repository = PlannerRepository(fakeDb, tokenProvider = { "session_token_123" }, fakeApi)

        val result = repository.refresh()

        assertTrue(result.isSuccess)
        assertEquals("Bearer session_token_123", fakeApi.lastAuthorizationHeader)

        assertEquals(1, fakeDb.tasksMap.size)
        assertEquals("Read Room Docs", fakeDb.tasksMap["task-1"]?.title)

        assertEquals(1, fakeDb.schedulesMap.size)
        assertEquals(31, fakeDb.schedulesMap["sched-1"]?.weekdaysMask)

        assertEquals(1, fakeDb.completionsList.size)
        assertEquals("comp-1", fakeDb.completionsList.first().id)
    }

    @Test
    fun completeTask_scheduledTask_sendsExactContractAndStoresCanonicalResponseInRoom() = runTest {
        val canonicalResponse = CompleteTaskResponseDto(
            id = "server-comp-id-999",
            taskId = "task-1",
            scheduleId = "sched-1",
            scheduledDate = "2026-08-18",
            completedDate = "2026-08-18",
            completedAt = "2026-08-18T10:00:00.000Z",
            titleSnapshot = "Canonical Title Snapshot",
            isImportantSnapshot = true
        )
        val fakeApi = FakePlannerApi(completeResult = Result.success(canonicalResponse))
        val fakeDb = InMemoryTestDatabase()

        val repository = PlannerRepository(fakeDb, tokenProvider = { "session_token_123" }, fakeApi)

        val result = repository.completeTask(
            taskId = "task-1",
            plannerToday = "2026-08-18",
            completedDate = "2026-08-18",
            scheduleId = "sched-1",
            scheduledDate = "2026-08-18"
        )

        assertTrue(result.isSuccess)
        assertEquals("Bearer session_token_123", fakeApi.lastAuthorizationHeader)
        assertEquals("task-1", fakeApi.lastCompleteTaskId)
        assertEquals(
            CompleteTaskRequestDto(
                plannerToday = "2026-08-18",
                completedDate = "2026-08-18",
                scheduleId = "sched-1",
                scheduledDate = "2026-08-18"
            ),
            fakeApi.lastCompleteRequest
        )
        // No follow up GET
        assertEquals(0, fakeApi.getRefreshCallCount)

        assertTrue(fakeDb.applyCompleteCalled)
        val completion = fakeDb.completionsList.firstOrNull()
        assertNotNull(completion)
        assertEquals("server-comp-id-999", completion?.id)
        assertEquals("task-1", completion?.taskId)
        assertEquals("sched-1", completion?.scheduleId)
        assertEquals("2026-08-18", completion?.scheduledDate)
        assertEquals("2026-08-18", completion?.completedDate)
        assertEquals("2026-08-18T10:00:00.000Z", completion?.completedAt)
        assertEquals("Canonical Title Snapshot", completion?.titleSnapshot)
        assertTrue(completion?.isImportantSnapshot == true)
    }

    @Test
    fun completeTask_idempotentRetry_safelyInsertsWithoutDuplicateRoomRows() = runTest {
        val canonicalResponse = CompleteTaskResponseDto(
            id = "server-comp-id-999",
            taskId = "task-1",
            scheduleId = "sched-1",
            scheduledDate = "2026-08-18",
            completedDate = "2026-08-18",
            completedAt = "2026-08-18T10:00:00.000Z",
            titleSnapshot = "Canonical Title Snapshot",
            isImportantSnapshot = true
        )
        val fakeApi = FakePlannerApi(completeResult = Result.success(canonicalResponse))
        val fakeDb = InMemoryTestDatabase()
        val repository = PlannerRepository(fakeDb, tokenProvider = { "session_token_123" }, fakeApi)

        // 1. Initial complete
        val res1 = repository.completeTask(
            taskId = "task-1",
            plannerToday = "2026-08-18",
            completedDate = "2026-08-18",
            scheduleId = "sched-1",
            scheduledDate = "2026-08-18"
        )
        assertTrue(res1.isSuccess)
        assertEquals(1, fakeDb.completionsList.size)

        // 2. Retry complete with identical canonical response
        val res2 = repository.completeTask(
            taskId = "task-1",
            plannerToday = "2026-08-18",
            completedDate = "2026-08-18",
            scheduleId = "sched-1",
            scheduledDate = "2026-08-18"
        )
        assertTrue(res2.isSuccess)
        // Prove: exactly 1 completion row in Room, no duplicate rows
        assertEquals(1, fakeDb.completionsList.size)
        assertEquals("server-comp-id-999", fakeDb.completionsList.first().id)
        assertEquals("Canonical Title Snapshot", fakeDb.completionsList.first().titleSnapshot)
    }

    @Test
    fun undoTask_scheduledTask_sendsExactContractAndUpdatesRoom() = runTest {
        val fakeApi = FakePlannerApi()
        val fakeDb = InMemoryTestDatabase()
        fakeDb.completionsList.add(
            CompletionEntity(
                id = "comp-1",
                taskId = "task-1",
                scheduleId = "sched-1",
                scheduledDate = "2026-08-18",
                completedDate = "2026-08-18",
                completedAt = "2026-08-18T10:00:00.000Z",
                titleSnapshot = "Title",
                isImportantSnapshot = true
            )
        )

        val repository = PlannerRepository(fakeDb, tokenProvider = { "session_token_123" }, fakeApi)

        val result = repository.undoTask(
            taskId = "task-1",
            scheduleId = "sched-1",
            scheduledDate = "2026-08-18"
        )

        assertTrue(result.isSuccess)
        assertEquals("Bearer session_token_123", fakeApi.lastAuthorizationHeader)
        assertEquals("task-1", fakeApi.lastUndoTaskId)
        assertEquals(
            UndoTaskRequestDto(
                scheduleId = "sched-1",
                scheduledDate = "2026-08-18"
            ),
            fakeApi.lastUndoRequest
        )
        assertEquals(0, fakeApi.getRefreshCallCount)

        assertTrue(fakeDb.applyUndoCalled)
        assertEquals(0, fakeDb.completionsList.size)
    }

    @Test
    fun completeTask_networkFailure_leavesRoomUntouched() = runTest {
        val fakeApi = FakePlannerApi(completeResult = Result.failure(IOException("No connection")))
        val fakeDb = InMemoryTestDatabase()
        val repository = PlannerRepository(fakeDb, tokenProvider = { "session_token_123" }, fakeApi)

        val result = repository.completeTask(
            taskId = "task-1",
            plannerToday = "2026-08-18",
            completedDate = "2026-08-18"
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals(0, fakeDb.completionsList.size)
        assertTrue(!fakeDb.applyCompleteCalled)
    }

    @Test
    fun completeTask_401Unauthorized_propagatesHttpException() = runTest {
        val http401 = HttpException(
            Response.error<CompleteTaskResponseDto>(
                401,
                "{}".toResponseBody("application/json".toMediaType())
            )
        )
        val fakeApi = FakePlannerApi(completeResult = Result.failure(http401))
        val fakeDb = InMemoryTestDatabase()
        val repository = PlannerRepository(fakeDb, tokenProvider = { "session_token_123" }, fakeApi)

        val result = repository.completeTask(
            taskId = "task-1",
            plannerToday = "2026-08-18",
            completedDate = "2026-08-18"
        )

        assertTrue(result.isFailure)
        val err = result.exceptionOrNull()
        assertTrue(err is HttpException)
        assertEquals(401, (err as HttpException).code())
        assertEquals(0, fakeDb.completionsList.size)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun sharedMutex_serializesRefreshAndComplete_staleRefreshCannotOverwriteNewerWrite() = runTest {
        val sharedMutex = Mutex()
        val refreshDeferred = CompletableDeferred<Unit>()

        val staleSnapshot = PlannerRefreshResponseDto(
            tasks = listOf(
                RefreshTaskDto(
                    id = "task-1",
                    title = "Task One",
                    note = null,
                    isImportant = false,
                    createdAt = "2026-08-18T10:00:00.000Z",
                    updatedAt = "2026-08-18T10:00:00.000Z"
                )
            ),
            schedules = emptyList(),
            completions = emptyList() // Stale: no completion
        )

        val canonicalResponse = CompleteTaskResponseDto(
            id = "canonical-comp-id",
            taskId = "task-1",
            scheduleId = null,
            scheduledDate = null,
            completedDate = "2026-08-18",
            completedAt = "2026-08-18T10:00:00.000Z",
            titleSnapshot = "Task One",
            isImportantSnapshot = false
        )

        val fakeApi = FakePlannerApi(
            refreshResult = Result.success(staleSnapshot),
            completeResult = Result.success(canonicalResponse),
            delayRefreshDeferred = refreshDeferred
        )
        val fakeDb = InMemoryTestDatabase()

        val repo1 = PlannerRepository(fakeDb, { "token" }, fakeApi, sharedMutex)
        val repo2 = PlannerRepository(fakeDb, { "token" }, fakeApi, sharedMutex)

        // 1. Start Refresh first (it acquires the mutex, then suspends on refreshDeferred)
        val refreshJob = async { repo1.refresh() }
        testScheduler.runCurrent()

        // Verify refresh started and acquired lock
        assertEquals(1, fakeApi.getRefreshCallCount)
        assertTrue(sharedMutex.isLocked)

        // 2. Trigger Complete while Refresh is still in-flight
        val completeJob = async {
            repo2.completeTask(
                taskId = "task-1",
                plannerToday = "2026-08-18",
                completedDate = "2026-08-18"
            )
        }
        testScheduler.runCurrent()

        // Complete is queued behind the Mutex and has NOT executed completeTask network call yet
        assertEquals(0, fakeApi.completeCallCount)

        // 3. Complete the Refresh network response -> Refresh finishes and applies Room snapshot
        refreshDeferred.complete(Unit)
        testScheduler.runCurrent()
        refreshJob.await()

        // 4. Now Complete executes and applies its canonical completion to Room
        completeJob.await()

        assertEquals(1, fakeApi.completeCallCount)
        assertEquals(1, fakeDb.completionsList.size)
        assertEquals("task-1", fakeDb.completionsList.first().taskId)
        assertEquals("canonical-comp-id", fakeDb.completionsList.first().id)
    }

    @Test
    fun deleteTask_missingToken_returnsFailureWithoutCallingNetwork() = runTest {
        val fakeApi = FakePlannerApi()
        val fakeDb = InMemoryTestDatabase()
        val repository = PlannerRepository(fakeDb, tokenProvider = { null }, fakeApi)

        val result = repository.deleteTask("task-1")

        assertTrue(result.isFailure)
        assertEquals("Missing authentication session token", result.exceptionOrNull()?.message)
        assertEquals(0, fakeApi.deleteCallCount)
    }

    @Test
    fun deleteTask_authenticated_sendsExactContractAndAtomicallyDeletesFromRoom() = runTest {
        val fakeApi = FakePlannerApi(deleteResult = Result.success(DeleteTaskResponseDto("task-delete-1")))
        val fakeDb = InMemoryTestDatabase()

        fakeDb.tasksMap["task-delete-1"] = TaskEntity(
            id = "task-delete-1",
            title = "To Delete",
            note = null,
            isImportant = false,
            createdAt = "2026-08-22T10:00:00.000Z",
            updatedAt = "2026-08-22T10:00:00.000Z"
        )
        fakeDb.schedulesMap["sched-del-1"] = ScheduleEntity(
            id = "sched-del-1",
            taskId = "task-delete-1",
            scheduleType = "ONCE",
            startDate = "2026-08-22",
            endDate = null,
            scheduledTime = null,
            intervalDays = null,
            intervalAnchorDate = null,
            weekdaysMask = null,
            reminderMinutesBefore = null,
            createdAt = "2026-08-22T10:00:00.000Z",
            updatedAt = "2026-08-22T10:00:00.000Z"
        )
        fakeDb.completionsList.add(
            CompletionEntity(
                id = "comp-del-1",
                taskId = "task-delete-1",
                scheduleId = "sched-del-1",
                scheduledDate = "2026-08-22",
                completedDate = "2026-08-22",
                completedAt = "2026-08-22T10:00:00.000Z",
                titleSnapshot = "To Delete",
                isImportantSnapshot = false
            )
        )

        val repository = PlannerRepository(fakeDb, tokenProvider = { "token_123" }, fakeApi)

        val result = repository.deleteTask("task-delete-1")

        assertTrue(result.isSuccess)
        assertEquals("Bearer token_123", fakeApi.lastAuthorizationHeader)
        assertEquals("task-delete-1", fakeApi.lastDeleteTaskId)
        assertEquals(1, fakeApi.deleteCallCount)
        assertEquals(0, fakeApi.getRefreshCallCount) // No follow-up GET

        assertTrue(fakeDb.applyDeleteTaskCalled)
        assertEquals(0, fakeDb.tasksMap.size)
        assertEquals(0, fakeDb.schedulesMap.size)
        assertEquals(0, fakeDb.completionsList.size)
    }

    @Test
    fun deleteTask_removesTaskSchedulesAndCompletionsLeavingUnrelatedDataIntact() = runTest {
        val fakeApi = FakePlannerApi(deleteResult = Result.success(DeleteTaskResponseDto("target-task")))
        val fakeDb = InMemoryTestDatabase()

        // Target task data
        fakeDb.tasksMap["target-task"] = TaskEntity("target-task", "Target", null, false, "", "")
        fakeDb.schedulesMap["target-sched"] = ScheduleEntity("target-sched", "target-task", "ONCE", "2026-08-22", null, null, null, null, null, null, "", "")
        fakeDb.completionsList.add(CompletionEntity("target-comp", "target-task", "target-sched", "2026-08-22", "2026-08-22", "", null, false))

        // Unrelated task data
        fakeDb.tasksMap["unrelated-task"] = TaskEntity("unrelated-task", "Unrelated", null, true, "", "")
        fakeDb.schedulesMap["unrelated-sched"] = ScheduleEntity("unrelated-sched", "unrelated-task", "ONCE", "2026-08-22", null, null, null, null, null, null, "", "")
        fakeDb.completionsList.add(CompletionEntity("unrelated-comp", "unrelated-task", "unrelated-sched", "2026-08-22", "2026-08-22", "", null, true))

        val repository = PlannerRepository(fakeDb, tokenProvider = { "token_123" }, fakeApi)

        val result = repository.deleteTask("target-task")

        assertTrue(result.isSuccess)
        // Target removed
        assertNull(fakeDb.tasksMap["target-task"])
        assertNull(fakeDb.schedulesMap["target-sched"])
        assertTrue(fakeDb.completionsList.none { it.taskId == "target-task" })

        // Unrelated preserved
        assertNotNull(fakeDb.tasksMap["unrelated-task"])
        assertNotNull(fakeDb.schedulesMap["unrelated-sched"])
        assertTrue(fakeDb.completionsList.any { it.taskId == "unrelated-task" })
        assertEquals(1, fakeDb.tasksMap.size)
        assertEquals(1, fakeDb.schedulesMap.size)
        assertEquals(1, fakeDb.completionsList.size)
    }

    @Test
    fun deleteTask_idempotentRetry_safelyDeletesAlreadyAbsentRows() = runTest {
        val fakeApi = FakePlannerApi(deleteResult = Result.success(DeleteTaskResponseDto("absent-task")))
        val fakeDb = InMemoryTestDatabase()
        val repository = PlannerRepository(fakeDb, tokenProvider = { "token_123" }, fakeApi)

        // 1. First delete
        val res1 = repository.deleteTask("absent-task")
        assertTrue(res1.isSuccess)

        // 2. Retry delete for already deleted/absent task
        val res2 = repository.deleteTask("absent-task")
        assertTrue(res2.isSuccess)
        assertEquals(2, fakeApi.deleteCallCount)
        assertEquals(0, fakeDb.tasksMap.size)
    }

    @Test
    fun deleteTask_networkFailure_leavesRoomUntouched() = runTest {
        val fakeApi = FakePlannerApi(deleteResult = Result.failure(IOException("Connection failed")))
        val fakeDb = InMemoryTestDatabase()

        fakeDb.tasksMap["task-stay"] = TaskEntity("task-stay", "Stay", null, false, "", "")
        val repository = PlannerRepository(fakeDb, tokenProvider = { "token_123" }, fakeApi)

        val result = repository.deleteTask("task-stay")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals(1, fakeDb.tasksMap.size)
        assertNotNull(fakeDb.tasksMap["task-stay"])
    }

    @Test
    fun deleteTask_401Unauthorized_propagatesHttpException() = runTest {
        val http401 = HttpException(
            Response.error<DeleteTaskResponseDto>(
                401,
                "{}".toResponseBody("application/json".toMediaType())
            )
        )
        val fakeApi = FakePlannerApi(deleteResult = Result.failure(http401))
        val fakeDb = InMemoryTestDatabase()
        fakeDb.tasksMap["task-stay"] = TaskEntity("task-stay", "Stay", null, false, "", "")

        val repository = PlannerRepository(fakeDb, tokenProvider = { "token_123" }, fakeApi)

        val result = repository.deleteTask("task-stay")

        assertTrue(result.isFailure)
        val err = result.exceptionOrNull()
        assertTrue(err is HttpException)
        assertEquals(401, (err as HttpException).code())
        assertEquals(1, fakeDb.tasksMap.size)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun sharedMutex_serializesRefreshAndDelete_staleRefreshCannotResurrectDeletedTask() = runTest {
        val sharedMutex = Mutex()
        val refreshDeferred = CompletableDeferred<Unit>()

        val staleSnapshot = PlannerRefreshResponseDto(
            tasks = listOf(
                RefreshTaskDto(
                    id = "task-to-delete",
                    title = "Stale Task To Delete",
                    note = null,
                    isImportant = false,
                    createdAt = "2026-08-18T10:00:00.000Z",
                    updatedAt = "2026-08-18T10:00:00.000Z"
                )
            ),
            schedules = emptyList(),
            completions = emptyList()
        )

        val fakeApi = FakePlannerApi(
            refreshResult = Result.success(staleSnapshot),
            deleteResult = Result.success(DeleteTaskResponseDto("task-to-delete")),
            delayRefreshDeferred = refreshDeferred
        )
        val fakeDb = InMemoryTestDatabase()

        val repo1 = PlannerRepository(fakeDb, { "token" }, fakeApi, sharedMutex)
        val repo2 = PlannerRepository(fakeDb, { "token" }, fakeApi, sharedMutex)

        // 1. Start Refresh first (acquires lock, pauses on refreshDeferred)
        val refreshJob = async { repo1.refresh() }
        testScheduler.runCurrent()

        assertEquals(1, fakeApi.getRefreshCallCount)
        assertTrue(sharedMutex.isLocked)

        // 2. Trigger Delete while Refresh is in-flight
        val deleteJob = async { repo2.deleteTask("task-to-delete") }
        testScheduler.runCurrent()

        // Delete is queued behind Mutex and has NOT executed network call
        assertEquals(0, fakeApi.deleteCallCount)

        // 3. Complete Refresh response -> Refresh finishes and applies snapshot (including task-to-delete)
        refreshDeferred.complete(Unit)
        testScheduler.runCurrent()
        refreshJob.await()

        // 4. Now Delete executes and removes task-to-delete from Room
        deleteJob.await()

        assertEquals(1, fakeApi.deleteCallCount)
        assertEquals(0, fakeDb.tasksMap.size)
        assertNull(fakeDb.tasksMap["task-to-delete"])
    }

    @Test
    fun updateTaskContent_missingToken_returnsFailureWithoutCallingNetwork() = runTest {
        val fakeApi = FakePlannerApi()
        val fakeDb = InMemoryTestDatabase()
        val repository = PlannerRepository(fakeDb, tokenProvider = { null }, fakeApi)

        val result = repository.updateTaskContent("task-1", "New Title", "New Note", true)

        assertTrue(result.isFailure)
        assertEquals("Missing authentication session token", result.exceptionOrNull()?.message)
        assertEquals(0, fakeApi.updateContentCallCount)
    }

    @Test
    fun updateTaskContent_authenticated_sendsExactContentFieldsAndUpdatesRoom() = runTest {
        val canonicalTaskDto = TaskResponseDto(
            id = "task-edit-1",
            title = "Updated Title",
            note = "Updated Note",
            isImportant = true,
            createdAt = "2026-08-22T10:00:00.000Z",
            updatedAt = "2026-08-22T10:05:00.000Z"
        )
        val canonicalResponse = UpdateTaskResponseDto(
            task = canonicalTaskDto,
            completions = emptyList()
        )
        val fakeApi = FakePlannerApi(updateContentResult = Result.success(canonicalResponse))
        val fakeDb = InMemoryTestDatabase()

        fakeDb.tasksMap["task-edit-1"] = TaskEntity(
            id = "task-edit-1",
            title = "Old Title",
            note = "Old Note",
            isImportant = false,
            createdAt = "2026-08-22T10:00:00.000Z",
            updatedAt = "2026-08-22T10:00:00.000Z"
        )

        val repository = PlannerRepository(fakeDb, tokenProvider = { "token_123" }, fakeApi)

        val result = repository.updateTaskContent(
            taskId = "task-edit-1",
            title = "Updated Title",
            note = "Updated Note",
            isImportant = true
        )

        assertTrue(result.isSuccess)
        assertEquals("Bearer token_123", fakeApi.lastAuthorizationHeader)
        assertEquals("task-edit-1", fakeApi.lastUpdateContentTaskId)
        assertEquals("Updated Title", fakeApi.lastUpdateContentRequest?.title)
        assertEquals("Updated Note", fakeApi.lastUpdateContentRequest?.note)
        assertEquals(true, fakeApi.lastUpdateContentRequest?.isImportant)
        assertEquals(1, fakeApi.updateContentCallCount)
        assertEquals(0, fakeApi.getRefreshCallCount) // Zero follow-up GET

        assertTrue(fakeDb.applyUpdateTaskContentCalled)
        val storedTask = fakeDb.tasksMap["task-edit-1"]
        assertNotNull(storedTask)
        assertEquals("Updated Title", storedTask?.title)
        assertEquals("Updated Note", storedTask?.note)
        assertEquals(true, storedTask?.isImportant)
        assertEquals("2026-08-22T10:05:00.000Z", storedTask?.updatedAt)
    }

    @Test
    fun updateTaskContent_withNullNote_sendsExplicitNullNote() = runTest {
        val canonicalTaskDto = TaskResponseDto(
            id = "task-clear-note",
            title = "Same Title",
            note = null,
            isImportant = false,
            createdAt = "2026-08-22T10:00:00.000Z",
            updatedAt = "2026-08-22T10:06:00.000Z"
        )
        val canonicalResponse = UpdateTaskResponseDto(
            task = canonicalTaskDto,
            completions = emptyList()
        )
        val fakeApi = FakePlannerApi(updateContentResult = Result.success(canonicalResponse))
        val fakeDb = InMemoryTestDatabase()

        fakeDb.tasksMap["task-clear-note"] = TaskEntity(
            id = "task-clear-note",
            title = "Same Title",
            note = "Existing Note",
            isImportant = false,
            createdAt = "2026-08-22T10:00:00.000Z",
            updatedAt = "2026-08-22T10:00:00.000Z"
        )

        val repository = PlannerRepository(fakeDb, tokenProvider = { "token_123" }, fakeApi)

        val result = repository.updateTaskContent(
            taskId = "task-clear-note",
            title = "Same Title",
            note = null,
            isImportant = false
        )

        assertTrue(result.isSuccess)
        assertNull(fakeApi.lastUpdateContentRequest?.note)
        assertNull(fakeDb.tasksMap["task-clear-note"]?.note)
    }

    @Test
    fun updateTaskContent_withAffectedCompletions_updatesRoomCompletionsAndPreservesSchedules() = runTest {
        val canonicalTaskDto = TaskResponseDto(
            id = "task-star-completed",
            title = "Renamed Task",
            note = null,
            isImportant = true,
            createdAt = "2026-08-22T10:00:00.000Z",
            updatedAt = "2026-08-22T10:10:00.000Z"
        )
        val canonicalCompDto = CompleteTaskResponseDto(
            id = "comp-1",
            taskId = "task-star-completed",
            scheduleId = "sched-1",
            scheduledDate = "2026-08-22",
            completedDate = "2026-08-22",
            completedAt = "2026-08-22T10:00:00.000Z",
            titleSnapshot = "Original Title", // Frozen completion title snapshot
            isImportantSnapshot = true // Updated from backend response
        )
        val canonicalResponse = UpdateTaskResponseDto(
            task = canonicalTaskDto,
            completions = listOf(canonicalCompDto)
        )
        val fakeApi = FakePlannerApi(updateContentResult = Result.success(canonicalResponse))
        val fakeDb = InMemoryTestDatabase()

        // Existing Room task
        fakeDb.tasksMap["task-star-completed"] = TaskEntity(
            id = "task-star-completed",
            title = "Original Title",
            note = null,
            isImportant = false,
            createdAt = "2026-08-22T10:00:00.000Z",
            updatedAt = "2026-08-22T10:00:00.000Z"
        )
        // Existing Room schedule (must remain untouched)
        val existingSchedule = ScheduleEntity(
            id = "sched-1",
            taskId = "task-star-completed",
            scheduleType = "ONCE",
            startDate = "2026-08-22",
            endDate = null,
            scheduledTime = "09:00:00",
            intervalDays = null,
            intervalAnchorDate = null,
            weekdaysMask = null,
            reminderMinutesBefore = null,
            createdAt = "2026-08-22T10:00:00.000Z",
            updatedAt = "2026-08-22T10:00:00.000Z"
        )
        fakeDb.schedulesMap["sched-1"] = existingSchedule

        // Existing Room completion
        fakeDb.completionsList.add(
            CompletionEntity(
                id = "comp-1",
                taskId = "task-star-completed",
                scheduleId = "sched-1",
                scheduledDate = "2026-08-22",
                completedDate = "2026-08-22",
                completedAt = "2026-08-22T10:00:00.000Z",
                titleSnapshot = "Original Title",
                isImportantSnapshot = false
            )
        )

        val repository = PlannerRepository(fakeDb, tokenProvider = { "token_123" }, fakeApi)

        val result = repository.updateTaskContent(
            taskId = "task-star-completed",
            title = "Renamed Task",
            note = null,
            isImportant = true
        )

        assertTrue(result.isSuccess)
        // Task updated
        assertEquals("Renamed Task", fakeDb.tasksMap["task-star-completed"]?.title)
        assertEquals(true, fakeDb.tasksMap["task-star-completed"]?.isImportant)

        // Schedule untouched
        assertEquals(1, fakeDb.schedulesMap.size)
        assertEquals(existingSchedule, fakeDb.schedulesMap["sched-1"])

        // Completion updated from canonical response
        assertEquals(1, fakeDb.completionsList.size)
        val updatedComp = fakeDb.completionsList.first()
        assertEquals(true, updatedComp.isImportantSnapshot)
        assertEquals("Original Title", updatedComp.titleSnapshot) // Frozen titleSnapshot preserved
        assertEquals("2026-08-22", updatedComp.completedDate)
        assertEquals("2026-08-22T10:00:00.000Z", updatedComp.completedAt)
    }

    @Test
    fun updateTaskContent_networkFailure_leavesRoomUntouched() = runTest {
        val fakeApi = FakePlannerApi(updateContentResult = Result.failure(IOException("Network error")))
        val fakeDb = InMemoryTestDatabase()

        fakeDb.tasksMap["task-fail"] = TaskEntity(
            id = "task-fail",
            title = "Original Title",
            note = "Original Note",
            isImportant = false,
            createdAt = "2026-08-22T10:00:00.000Z",
            updatedAt = "2026-08-22T10:00:00.000Z"
        )
        val repository = PlannerRepository(fakeDb, tokenProvider = { "token_123" }, fakeApi)

        val result = repository.updateTaskContent("task-fail", "New Title", "New Note", true)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals("Original Title", fakeDb.tasksMap["task-fail"]?.title)
        assertEquals(false, fakeDb.tasksMap["task-fail"]?.isImportant)
    }

    @Test
    fun updateTaskContent_401Unauthorized_propagatesHttpException() = runTest {
        val http401 = HttpException(
            Response.error<UpdateTaskResponseDto>(
                401,
                "{}".toResponseBody("application/json".toMediaType())
            )
        )
        val fakeApi = FakePlannerApi(updateContentResult = Result.failure(http401))
        val fakeDb = InMemoryTestDatabase()
        fakeDb.tasksMap["task-401"] = TaskEntity("task-401", "Title", null, false, "", "")

        val repository = PlannerRepository(fakeDb, tokenProvider = { "token_123" }, fakeApi)

        val result = repository.updateTaskContent("task-401", "New Title", null, true)

        assertTrue(result.isFailure)
        val err = result.exceptionOrNull()
        assertTrue(err is HttpException)
        assertEquals(401, (err as HttpException).code())
        assertEquals("Title", fakeDb.tasksMap["task-401"]?.title)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun sharedMutex_serializesRefreshAndUpdateTaskContent_staleRefreshCannotOverwriteNewerUpdate() = runTest {
        val sharedMutex = Mutex()
        val refreshDeferred = CompletableDeferred<Unit>()

        val staleSnapshot = PlannerRefreshResponseDto(
            tasks = listOf(
                RefreshTaskDto(
                    id = "task-edit-lock",
                    title = "Stale Old Title",
                    note = null,
                    isImportant = false,
                    createdAt = "2026-08-18T10:00:00.000Z",
                    updatedAt = "2026-08-18T10:00:00.000Z"
                )
            ),
            schedules = emptyList(),
            completions = emptyList()
        )

        val canonicalUpdate = UpdateTaskResponseDto(
            task = TaskResponseDto(
                id = "task-edit-lock",
                title = "New Canonical Title",
                note = "New Note",
                isImportant = true,
                createdAt = "2026-08-18T10:00:00.000Z",
                updatedAt = "2026-08-22T10:00:00.000Z"
            ),
            completions = emptyList()
        )

        val fakeApi = FakePlannerApi(
            refreshResult = Result.success(staleSnapshot),
            updateContentResult = Result.success(canonicalUpdate),
            delayRefreshDeferred = refreshDeferred
        )
        val fakeDb = InMemoryTestDatabase()

        val repo1 = PlannerRepository(fakeDb, { "token" }, fakeApi, sharedMutex)
        val repo2 = PlannerRepository(fakeDb, { "token" }, fakeApi, sharedMutex)

        // 1. Start Refresh first (acquires lock, pauses on refreshDeferred)
        val refreshJob = async { repo1.refresh() }
        testScheduler.runCurrent()

        assertEquals(1, fakeApi.getRefreshCallCount)
        assertTrue(sharedMutex.isLocked)

        // 2. Trigger Update while Refresh is in-flight
        val updateJob = async { repo2.updateTaskContent("task-edit-lock", "New Canonical Title", "New Note", true) }
        testScheduler.runCurrent()

        // Update is queued behind Mutex and has NOT executed network call
        assertEquals(0, fakeApi.updateContentCallCount)

        // 3. Complete Refresh response -> Refresh finishes, then Update executes and updates Room
        refreshDeferred.complete(Unit)
        testScheduler.runCurrent()
        refreshJob.await()
        updateJob.await()

        assertEquals(1, fakeApi.updateContentCallCount)
        assertEquals("New Canonical Title", fakeDb.tasksMap["task-edit-lock"]?.title)
        assertEquals(true, fakeDb.tasksMap["task-edit-lock"]?.isImportant)
    }

    @Test
    fun updateTaskSchedule_missingToken_returnsFailureWithoutCallingNetwork() = runTest {
        val fakeApi = FakePlannerApi()
        val fakeDb = InMemoryTestDatabase()
        val repository = PlannerRepository(fakeDb, tokenProvider = { null }, fakeApi)

        val result = repository.updateTaskSchedule(
            taskId = "task-1",
            plannerToday = "2026-08-22",
            effectiveDate = "2026-08-22",
            schedule = UpdateTaskScheduleDto(type = "ONCE", startDate = "2026-08-22")
        )

        assertTrue(result.isFailure)
        assertEquals("Missing authentication session token", result.exceptionOrNull()?.message)
        assertEquals(0, fakeApi.updateScheduleCallCount)
    }

    @Test
    fun updateTaskSchedule_laterToOnce_storesCanonicalTaskAndSchedule() = runTest {
        val canonicalTaskDto = TaskResponseDto(
            id = "task-later-1",
            title = "Later Task",
            note = null,
            isImportant = false,
            createdAt = "2026-08-22T10:00:00.000Z",
            updatedAt = "2026-08-22T10:05:00.000Z",
            schedules = listOf(
                TaskScheduleResponseDto(
                    id = "sched-new-1",
                    type = "ONCE",
                    startDate = "2026-08-22",
                    endDate = "2026-08-22",
                    scheduledTime = "14:30:00",
                    intervalDays = null,
                    intervalAnchorDate = null,
                    weekdaysMask = null,
                    reminderMinutesBefore = 15,
                    createdAt = "2026-08-22T10:05:00.000Z",
                    updatedAt = "2026-08-22T10:05:00.000Z"
                )
            )
        )
        val canonicalResponse = UpdateTaskResponseDto(
            task = canonicalTaskDto,
            completions = emptyList()
        )
        val fakeApi = FakePlannerApi(updateScheduleResult = Result.success(canonicalResponse))
        val fakeDb = InMemoryTestDatabase()

        fakeDb.tasksMap["task-later-1"] = TaskEntity(
            id = "task-later-1",
            title = "Later Task",
            note = null,
            isImportant = false,
            createdAt = "2026-08-22T10:00:00.000Z",
            updatedAt = "2026-08-22T10:00:00.000Z"
        )

        val repository = PlannerRepository(fakeDb, tokenProvider = { "token_123" }, fakeApi)

        val result = repository.updateTaskSchedule(
            taskId = "task-later-1",
            plannerToday = "2026-08-22",
            effectiveDate = "2026-08-22",
            schedule = UpdateTaskScheduleDto(
                type = "ONCE",
                startDate = "2026-08-22",
                scheduledTime = "14:30:00",
                reminderMinutesBefore = 15
            )
        )

        assertTrue(result.isSuccess)
        assertEquals("Bearer token_123", fakeApi.lastAuthorizationHeader)
        assertEquals("task-later-1", fakeApi.lastUpdateScheduleTaskId)
        assertEquals("2026-08-22", fakeApi.lastUpdateScheduleRequest?.plannerToday)
        assertEquals("2026-08-22", fakeApi.lastUpdateScheduleRequest?.effectiveDate)
        assertEquals("ONCE", fakeApi.lastUpdateScheduleRequest?.schedule?.type)
        assertEquals("2026-08-22", fakeApi.lastUpdateScheduleRequest?.schedule?.startDate)
        assertEquals(1, fakeApi.updateScheduleCallCount)
        assertEquals(0, fakeApi.getRefreshCallCount) // Zero follow-up GET

        assertTrue(fakeDb.applyUpdateTaskScheduleCalled)
        assertEquals(1, fakeDb.schedulesMap.size)
        val savedSched = fakeDb.schedulesMap["sched-new-1"]
        assertNotNull(savedSched)
        assertEquals("task-later-1", savedSched?.taskId)
        assertEquals("ONCE", savedSched?.scheduleType)
        assertEquals("2026-08-22", savedSched?.startDate)
        assertEquals("14:30:00", savedSched?.scheduledTime)
        assertEquals(15, savedSched?.reminderMinutesBefore)
    }

    @Test
    fun updateTaskSchedule_onceReschedule_preservesBackendScheduleIdAndCanonicalFields() = runTest {
        val canonicalTaskDto = TaskResponseDto(
            id = "task-once-1",
            title = "Once Task",
            note = null,
            isImportant = false,
            createdAt = "2026-08-22T10:00:00.000Z",
            updatedAt = "2026-08-22T10:15:00.000Z",
            schedules = listOf(
                TaskScheduleResponseDto(
                    id = "sched-orig-1", // In-place update preserves ID
                    type = "ONCE",
                    startDate = "2026-08-25",
                    endDate = "2026-08-25",
                    scheduledTime = "16:00:00",
                    intervalDays = null,
                    intervalAnchorDate = null,
                    weekdaysMask = null,
                    reminderMinutesBefore = 30,
                    createdAt = "2026-08-22T10:00:00.000Z",
                    updatedAt = "2026-08-22T10:15:00.000Z"
                )
            )
        )
        val canonicalResponse = UpdateTaskResponseDto(
            task = canonicalTaskDto,
            completions = emptyList()
        )
        val fakeApi = FakePlannerApi(updateScheduleResult = Result.success(canonicalResponse))
        val fakeDb = InMemoryTestDatabase()

        fakeDb.tasksMap["task-once-1"] = TaskEntity("task-once-1", "Once Task", null, false, "", "")
        fakeDb.schedulesMap["sched-orig-1"] = ScheduleEntity(
            id = "sched-orig-1",
            taskId = "task-once-1",
            scheduleType = "ONCE",
            startDate = "2026-08-22",
            endDate = "2026-08-22",
            scheduledTime = null,
            intervalDays = null,
            intervalAnchorDate = null,
            weekdaysMask = null,
            reminderMinutesBefore = null,
            createdAt = "2026-08-22T10:00:00.000Z",
            updatedAt = "2026-08-22T10:00:00.000Z"
        )

        val repository = PlannerRepository(fakeDb, tokenProvider = { "token_123" }, fakeApi)

        val result = repository.updateTaskSchedule(
            taskId = "task-once-1",
            plannerToday = "2026-08-22",
            effectiveDate = "2026-08-22",
            schedule = UpdateTaskScheduleDto(
                type = "ONCE",
                startDate = "2026-08-25",
                scheduledTime = "16:00:00",
                reminderMinutesBefore = 30
            )
        )

        assertTrue(result.isSuccess)
        assertEquals(1, fakeDb.schedulesMap.size)
        val updatedSched = fakeDb.schedulesMap["sched-orig-1"]
        assertNotNull(updatedSched)
        assertEquals("2026-08-25", updatedSched?.startDate)
        assertEquals("16:00:00", updatedSched?.scheduledTime)
        assertEquals(30, updatedSched?.reminderMinutesBefore)
    }

    @Test
    fun updateTaskSchedule_recurringFutureEdit_reconcilesMultipleSegmentsAndRemovesSupersededSchedule() = runTest {
        // Backend returns:
        // 1. Truncated historical segment (sched-hist-1 with end_date 2026-08-24)
        // 2. New future segment (sched-fut-2 starting 2026-08-25)
        // Stale future segment (sched-stale-3) is deleted by backend and absent from response
        val canonicalTaskDto = TaskResponseDto(
            id = "task-recur-1",
            title = "Recurring Task",
            note = null,
            isImportant = false,
            createdAt = "2026-08-01T10:00:00.000Z",
            updatedAt = "2026-08-22T10:00:00.000Z",
            schedules = listOf(
                TaskScheduleResponseDto(
                    id = "sched-hist-1",
                    type = "INTERVAL_DAYS",
                    startDate = "2026-08-01",
                    endDate = "2026-08-24", // Truncated by backend
                    scheduledTime = null,
                    intervalDays = 2,
                    intervalAnchorDate = "2026-08-01",
                    weekdaysMask = null,
                    reminderMinutesBefore = null,
                    createdAt = "2026-08-01T10:00:00.000Z",
                    updatedAt = "2026-08-22T10:00:00.000Z"
                ),
                TaskScheduleResponseDto(
                    id = "sched-fut-2",
                    type = "WEEKDAYS",
                    startDate = "2026-08-25",
                    endDate = null,
                    scheduledTime = "09:00:00",
                    intervalDays = null,
                    intervalAnchorDate = null,
                    weekdaysMask = 65,
                    reminderMinutesBefore = null,
                    createdAt = "2026-08-22T10:00:00.000Z",
                    updatedAt = "2026-08-22T10:00:00.000Z"
                )
            )
        )
        val canonicalResponse = UpdateTaskResponseDto(
            task = canonicalTaskDto,
            completions = emptyList()
        )
        val fakeApi = FakePlannerApi(updateScheduleResult = Result.success(canonicalResponse))
        val fakeDb = InMemoryTestDatabase()

        fakeDb.tasksMap["task-recur-1"] = TaskEntity("task-recur-1", "Recurring Task", null, false, "", "")
        // Pre-existing Room state has historical segment and stale future segment
        fakeDb.schedulesMap["sched-hist-1"] = ScheduleEntity(
            id = "sched-hist-1",
            taskId = "task-recur-1",
            scheduleType = "INTERVAL_DAYS",
            startDate = "2026-08-01",
            endDate = null,
            scheduledTime = null,
            intervalDays = 2,
            intervalAnchorDate = "2026-08-01",
            weekdaysMask = null,
            reminderMinutesBefore = null,
            createdAt = "2026-08-01T10:00:00.000Z",
            updatedAt = "2026-08-01T10:00:00.000Z"
        )
        fakeDb.schedulesMap["sched-stale-3"] = ScheduleEntity(
            id = "sched-stale-3",
            taskId = "task-recur-1",
            scheduleType = "INTERVAL_DAYS",
            startDate = "2026-09-01",
            endDate = null,
            scheduledTime = null,
            intervalDays = 3,
            intervalAnchorDate = null,
            weekdaysMask = null,
            reminderMinutesBefore = null,
            createdAt = "2026-08-10T10:00:00.000Z",
            updatedAt = "2026-08-10T10:00:00.000Z"
        )

        val repository = PlannerRepository(fakeDb, tokenProvider = { "token_123" }, fakeApi)

        val result = repository.updateTaskSchedule(
            taskId = "task-recur-1",
            plannerToday = "2026-08-22",
            effectiveDate = "2026-08-25",
            schedule = UpdateTaskScheduleDto(
                type = "WEEKDAYS",
                startDate = "2026-08-25",
                weekdaysMask = 65,
                scheduledTime = "09:00:00"
            )
        )

        assertTrue(result.isSuccess)
        // Stale schedule removed
        assertNull(fakeDb.schedulesMap["sched-stale-3"])

        // Historical segment updated with truncated end_date
        val histSched = fakeDb.schedulesMap["sched-hist-1"]
        assertNotNull(histSched)
        assertEquals("2026-08-24", histSched?.endDate)

        // New future segment created
        val futSched = fakeDb.schedulesMap["sched-fut-2"]
        assertNotNull(futSched)
        assertEquals("WEEKDAYS", futSched?.scheduleType)
        assertEquals(65, futSched?.weekdaysMask)

        assertEquals(2, fakeDb.schedulesMap.size)
    }

    @Test
    fun updateTaskSchedule_removesLocalCompletionsReferencingDeletedSchedulesSafely() = runTest {
        val canonicalTaskDto = TaskResponseDto(
            id = "task-del-sched-comp",
            title = "Task",
            note = null,
            isImportant = false,
            createdAt = "2026-08-22T10:00:00.000Z",
            updatedAt = "2026-08-22T10:00:00.000Z",
            schedules = listOf(
                TaskScheduleResponseDto(
                    id = "sched-surviving-1",
                    type = "ONCE",
                    startDate = "2026-08-22",
                    endDate = "2026-08-22",
                    scheduledTime = null,
                    intervalDays = null,
                    intervalAnchorDate = null,
                    weekdaysMask = null,
                    reminderMinutesBefore = null,
                    createdAt = "2026-08-22T10:00:00.000Z",
                    updatedAt = "2026-08-22T10:00:00.000Z"
                )
            )
        )
        val canonicalResponse = UpdateTaskResponseDto(
            task = canonicalTaskDto,
            completions = emptyList()
        )
        val fakeApi = FakePlannerApi(updateScheduleResult = Result.success(canonicalResponse))
        val fakeDb = InMemoryTestDatabase()

        fakeDb.tasksMap["task-del-sched-comp"] = TaskEntity("task-del-sched-comp", "Task", null, false, "", "")
        fakeDb.schedulesMap["sched-surviving-1"] = ScheduleEntity("sched-surviving-1", "task-del-sched-comp", "ONCE", "2026-08-22", null, null, null, null, null, null, "", "")
        fakeDb.schedulesMap["sched-stale-2"] = ScheduleEntity("sched-stale-2", "task-del-sched-comp", "ONCE", "2026-08-24", null, null, null, null, null, null, "", "")

        // Completion on surviving schedule
        fakeDb.completionsList.add(CompletionEntity("comp-surviving", "task-del-sched-comp", "sched-surviving-1", "2026-08-22", "2026-08-22", "", null, false))
        // Stale completion referencing stale schedule
        fakeDb.completionsList.add(CompletionEntity("comp-stale", "task-del-sched-comp", "sched-stale-2", "2026-08-24", "2026-08-24", "", null, false))

        val repository = PlannerRepository(fakeDb, tokenProvider = { "token_123" }, fakeApi)

        val result = repository.updateTaskSchedule(
            taskId = "task-del-sched-comp",
            plannerToday = "2026-08-22",
            effectiveDate = "2026-08-22",
            schedule = UpdateTaskScheduleDto(type = "ONCE", startDate = "2026-08-22")
        )

        assertTrue(result.isSuccess)
        // Stale schedule and its completion removed
        assertNull(fakeDb.schedulesMap["sched-stale-2"])
        assertTrue(fakeDb.completionsList.none { it.id == "comp-stale" })

        // Surviving schedule and its completion preserved
        assertNotNull(fakeDb.schedulesMap["sched-surviving-1"])
        assertTrue(fakeDb.completionsList.any { it.id == "comp-surviving" })
        assertEquals(1, fakeDb.completionsList.size)
    }

    @Test
    fun updateTaskSchedule_preservesUnrelatedTasksSchedulesAndCompletions() = runTest {
        val canonicalTaskDto = TaskResponseDto(
            id = "task-target",
            title = "Target Task",
            note = null,
            isImportant = false,
            createdAt = "2026-08-22T10:00:00.000Z",
            updatedAt = "2026-08-22T10:00:00.000Z",
            schedules = listOf(
                TaskScheduleResponseDto("sched-target-new", "ONCE", "2026-08-22", "2026-08-22", null, null, null, null, null, "", "")
            )
        )
        val canonicalResponse = UpdateTaskResponseDto(task = canonicalTaskDto, completions = emptyList())
        val fakeApi = FakePlannerApi(updateScheduleResult = Result.success(canonicalResponse))
        val fakeDb = InMemoryTestDatabase()

        // Unrelated task, schedule, and completion
        fakeDb.tasksMap["task-unrelated"] = TaskEntity("task-unrelated", "Unrelated", null, false, "", "")
        fakeDb.schedulesMap["sched-unrelated"] = ScheduleEntity("sched-unrelated", "task-unrelated", "ONCE", "2026-08-22", null, null, null, null, null, null, "", "")
        fakeDb.completionsList.add(CompletionEntity("comp-unrelated", "task-unrelated", "sched-unrelated", "2026-08-22", "2026-08-22", "", null, false))

        fakeDb.tasksMap["task-target"] = TaskEntity("task-target", "Target Task", null, false, "", "")
        fakeDb.schedulesMap["sched-target-old"] = ScheduleEntity("sched-target-old", "task-target", "ONCE", "2026-08-21", null, null, null, null, null, null, "", "")

        val repository = PlannerRepository(fakeDb, tokenProvider = { "token_123" }, fakeApi)

        val result = repository.updateTaskSchedule(
            taskId = "task-target",
            plannerToday = "2026-08-22",
            effectiveDate = "2026-08-22",
            schedule = UpdateTaskScheduleDto(type = "ONCE", startDate = "2026-08-22")
        )

        assertTrue(result.isSuccess)
        // Unrelated data untouched
        assertNotNull(fakeDb.tasksMap["task-unrelated"])
        assertNotNull(fakeDb.schedulesMap["sched-unrelated"])
        assertTrue(fakeDb.completionsList.any { it.id == "comp-unrelated" })
        assertEquals(2, fakeDb.tasksMap.size)
        assertEquals(2, fakeDb.schedulesMap.size)
        assertEquals(1, fakeDb.completionsList.size)
    }

    @Test
    fun updateTaskSchedule_networkFailure_leavesRoomUntouched() = runTest {
        val fakeApi = FakePlannerApi(updateScheduleResult = Result.failure(IOException("No network connection")))
        val fakeDb = InMemoryTestDatabase()

        fakeDb.tasksMap["task-fail"] = TaskEntity("task-fail", "Task", null, false, "", "")
        fakeDb.schedulesMap["sched-fail"] = ScheduleEntity("sched-fail", "task-fail", "ONCE", "2026-08-22", null, null, null, null, null, null, "", "")
        val repository = PlannerRepository(fakeDb, tokenProvider = { "token_123" }, fakeApi)

        val result = repository.updateTaskSchedule(
            taskId = "task-fail",
            plannerToday = "2026-08-22",
            effectiveDate = "2026-08-22",
            schedule = UpdateTaskScheduleDto(type = "ONCE", startDate = "2026-08-23")
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals("2026-08-22", fakeDb.schedulesMap["sched-fail"]?.startDate)
    }

    @Test
    fun updateTaskSchedule_401Unauthorized_propagatesHttpException() = runTest {
        val http401 = HttpException(
            Response.error<UpdateTaskResponseDto>(
                401,
                "{}".toResponseBody("application/json".toMediaType())
            )
        )
        val fakeApi = FakePlannerApi(updateScheduleResult = Result.failure(http401))
        val fakeDb = InMemoryTestDatabase()
        fakeDb.tasksMap["task-401"] = TaskEntity("task-401", "Task", null, false, "", "")

        val repository = PlannerRepository(fakeDb, tokenProvider = { "token_123" }, fakeApi)

        val result = repository.updateTaskSchedule(
            taskId = "task-401",
            plannerToday = "2026-08-22",
            effectiveDate = "2026-08-22",
            schedule = UpdateTaskScheduleDto(type = "ONCE", startDate = "2026-08-22")
        )

        assertTrue(result.isFailure)
        val err = result.exceptionOrNull()
        assertTrue(err is HttpException)
        assertEquals(401, (err as HttpException).code())
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun sharedMutex_serializesRefreshAndUpdateTaskSchedule_staleRefreshCannotOverwriteNewerSchedule() = runTest {
        val sharedMutex = Mutex()
        val refreshDeferred = CompletableDeferred<Unit>()

        val staleSnapshot = PlannerRefreshResponseDto(
            tasks = listOf(
                RefreshTaskDto(
                    id = "task-sched-lock",
                    title = "Task Sched Lock",
                    note = null,
                    isImportant = false,
                    createdAt = "2026-08-18T10:00:00.000Z",
                    updatedAt = "2026-08-18T10:00:00.000Z"
                )
            ),
            schedules = listOf(
                RefreshScheduleDto(
                    id = "sched-stale-lock",
                    taskId = "task-sched-lock",
                    scheduleType = "ONCE",
                    startDate = "2026-08-18",
                    endDate = "2026-08-18",
                    scheduledTime = null,
                    intervalDays = null,
                    intervalAnchorDate = null,
                    weekdaysMask = null,
                    reminderMinutesBefore = null,
                    createdAt = "2026-08-18T10:00:00.000Z",
                    updatedAt = "2026-08-18T10:00:00.000Z"
                )
            ),
            completions = emptyList()
        )

        val canonicalUpdate = UpdateTaskResponseDto(
            task = TaskResponseDto(
                id = "task-sched-lock",
                title = "Task Sched Lock",
                note = null,
                isImportant = false,
                createdAt = "2026-08-18T10:00:00.000Z",
                updatedAt = "2026-08-22T10:00:00.000Z",
                schedules = listOf(
                    TaskScheduleResponseDto(
                        id = "sched-new-lock",
                        type = "ONCE",
                        startDate = "2026-08-25",
                        endDate = "2026-08-25",
                        scheduledTime = null,
                        intervalDays = null,
                        intervalAnchorDate = null,
                        weekdaysMask = null,
                        reminderMinutesBefore = null,
                        createdAt = "2026-08-22T10:00:00.000Z",
                        updatedAt = "2026-08-22T10:00:00.000Z"
                    )
                )
            ),
            completions = emptyList()
        )

        val fakeApi = FakePlannerApi(
            refreshResult = Result.success(staleSnapshot),
            updateScheduleResult = Result.success(canonicalUpdate),
            delayRefreshDeferred = refreshDeferred
        )
        val fakeDb = InMemoryTestDatabase()

        val repo1 = PlannerRepository(fakeDb, { "token" }, fakeApi, sharedMutex)
        val repo2 = PlannerRepository(fakeDb, { "token" }, fakeApi, sharedMutex)

        // 1. Start Refresh first
        val refreshJob = async { repo1.refresh() }
        testScheduler.runCurrent()

        assertEquals(1, fakeApi.getRefreshCallCount)
        assertTrue(sharedMutex.isLocked)

        // 2. Trigger Schedule Update while Refresh is in-flight
        val updateJob = async {
            repo2.updateTaskSchedule(
                taskId = "task-sched-lock",
                plannerToday = "2026-08-22",
                effectiveDate = "2026-08-25",
                schedule = UpdateTaskScheduleDto(type = "ONCE", startDate = "2026-08-25")
            )
        }
        testScheduler.runCurrent()

        assertEquals(0, fakeApi.updateScheduleCallCount)

        // 3. Complete Refresh response -> Refresh finishes and applies stale snapshot
        refreshDeferred.complete(Unit)
        testScheduler.runCurrent()
        refreshJob.await()
        updateJob.await()

        assertEquals(1, fakeApi.updateScheduleCallCount)
        // Stale schedule removed, new schedule present
        assertNull(fakeDb.schedulesMap["sched-stale-lock"])
        assertNotNull(fakeDb.schedulesMap["sched-new-lock"])
        assertEquals("2026-08-25", fakeDb.schedulesMap["sched-new-lock"]?.startDate)
    }

    @Test
    fun updateTaskSchedule_laterToScheduled_removesStaleDirectLaterCompletionAndPreservesUnrelated() = runTest {
        val canonicalTaskDto = TaskResponseDto(
            id = "task-later-stale",
            title = "Task",
            note = null,
            isImportant = false,
            createdAt = "2026-08-22T10:00:00.000Z",
            updatedAt = "2026-08-22T10:05:00.000Z",
            schedules = listOf(
                TaskScheduleResponseDto(
                    id = "sched-once-new",
                    type = "ONCE",
                    startDate = "2026-08-22",
                    endDate = "2026-08-22",
                    scheduledTime = null,
                    intervalDays = null,
                    intervalAnchorDate = null,
                    weekdaysMask = null,
                    reminderMinutesBefore = null,
                    createdAt = "2026-08-22T10:05:00.000Z",
                    updatedAt = "2026-08-22T10:05:00.000Z"
                )
            )
        )
        val canonicalResponse = UpdateTaskResponseDto(task = canonicalTaskDto, completions = emptyList())
        val fakeApi = FakePlannerApi(updateScheduleResult = Result.success(canonicalResponse))
        val fakeDb = InMemoryTestDatabase()

        // 1. Target task had direct-Later completion
        fakeDb.tasksMap["task-later-stale"] = TaskEntity("task-later-stale", "Task", null, false, "", "")
        fakeDb.completionsList.add(
            CompletionEntity(
                id = "comp-direct-later-stale",
                taskId = "task-later-stale",
                scheduleId = null,
                scheduledDate = null,
                completedDate = "2026-08-22",
                completedAt = "2026-08-22T10:00:00.000Z",
                titleSnapshot = "Task",
                isImportantSnapshot = false
            )
        )

        // 2. Unrelated task has direct-Later completion
        fakeDb.tasksMap["task-unrelated-later"] = TaskEntity("task-unrelated-later", "Unrelated", null, false, "", "")
        fakeDb.completionsList.add(
            CompletionEntity(
                id = "comp-unrelated-later",
                taskId = "task-unrelated-later",
                scheduleId = null,
                scheduledDate = null,
                completedDate = "2026-08-22",
                completedAt = "2026-08-22T10:00:00.000Z",
                titleSnapshot = "Unrelated",
                isImportantSnapshot = false
            )
        )

        val repository = PlannerRepository(fakeDb, tokenProvider = { "token_123" }, fakeApi)

        val result = repository.updateTaskSchedule(
            taskId = "task-later-stale",
            plannerToday = "2026-08-22",
            effectiveDate = "2026-08-22",
            schedule = UpdateTaskScheduleDto(type = "ONCE", startDate = "2026-08-22")
        )

        assertTrue(result.isSuccess)
        // Stale direct-Later completion for target task is gone
        assertTrue(fakeDb.completionsList.none { it.id == "comp-direct-later-stale" })

        // Canonical schedule exists
        assertNotNull(fakeDb.schedulesMap["sched-once-new"])
        assertEquals("task-later-stale", fakeDb.schedulesMap["sched-once-new"]?.taskId)

        // Unrelated direct-Later completion remains
        assertTrue(fakeDb.completionsList.any { it.id == "comp-unrelated-later" })
        assertEquals(1, fakeDb.completionsList.size)
    }

    @Test
    fun updateTask_missingToken_returnsFailureWithoutCallingNetwork() = runTest {
        val fakeApi = FakePlannerApi()
        val fakeDb = InMemoryTestDatabase()
        val repository = PlannerRepository(fakeDb, tokenProvider = { null }, fakeApi)

        val result = repository.updateTask(
            taskId = "task-1",
            title = "Title",
            note = "Note",
            isImportant = true,
            plannerToday = "2026-08-22",
            effectiveDate = "2026-08-22",
            schedule = UpdateTaskScheduleDto(type = "ONCE", startDate = "2026-08-22")
        )

        assertTrue(result.isFailure)
        assertEquals("Missing authentication session token", result.exceptionOrNull()?.message)
        assertEquals(0, fakeApi.updateTaskCallCount)
    }

    @Test
    fun updateTask_combinedContentAndSchedule_sendsExactContractAndAtomicallyReconcilesRoom() = runTest {
        val canonicalTaskDto = TaskResponseDto(
            id = "task-combined-1",
            title = "New Canonical Title",
            note = "New Note Content",
            isImportant = true,
            createdAt = "2026-08-22T10:00:00.000Z",
            updatedAt = "2026-08-22T10:20:00.000Z",
            schedules = listOf(
                TaskScheduleResponseDto(
                    id = "sched-combined-1",
                    type = "ONCE",
                    startDate = "2026-08-23",
                    endDate = "2026-08-23",
                    scheduledTime = "11:00:00",
                    intervalDays = null,
                    intervalAnchorDate = null,
                    weekdaysMask = null,
                    reminderMinutesBefore = 15,
                    createdAt = "2026-08-22T10:00:00.000Z",
                    updatedAt = "2026-08-22T10:20:00.000Z"
                )
            )
        )
        val canonicalResponse = UpdateTaskResponseDto(task = canonicalTaskDto, completions = emptyList())
        val fakeApi = FakePlannerApi(updateTaskResult = Result.success(canonicalResponse))
        val fakeDb = InMemoryTestDatabase()

        fakeDb.tasksMap["task-combined-1"] = TaskEntity("task-combined-1", "Old Title", null, false, "", "")
        fakeDb.schedulesMap["sched-old-1"] = ScheduleEntity("sched-old-1", "task-combined-1", "ONCE", "2026-08-22", null, null, null, null, null, null, "", "")

        val repository = PlannerRepository(fakeDb, tokenProvider = { "token_123" }, fakeApi)

        val result = repository.updateTask(
            taskId = "task-combined-1",
            title = "New Canonical Title",
            note = "New Note Content",
            isImportant = true,
            plannerToday = "2026-08-22",
            effectiveDate = "2026-08-22",
            schedule = UpdateTaskScheduleDto(
                type = "ONCE",
                startDate = "2026-08-23",
                scheduledTime = "11:00:00",
                reminderMinutesBefore = 15
            )
        )

        assertTrue(result.isSuccess)
        assertEquals(1, fakeApi.updateTaskCallCount)
        assertEquals(0, fakeApi.updateContentCallCount)
        assertEquals(0, fakeApi.updateScheduleCallCount)
        assertEquals(0, fakeApi.getRefreshCallCount) // Zero follow-up GET

        assertEquals("Bearer token_123", fakeApi.lastAuthorizationHeader)
        assertEquals("task-combined-1", fakeApi.lastUpdateTaskId)
        assertEquals("New Canonical Title", fakeApi.lastUpdateTaskRequest?.title)
        assertEquals("New Note Content", fakeApi.lastUpdateTaskRequest?.note)
        assertEquals(true, fakeApi.lastUpdateTaskRequest?.isImportant)
        assertEquals("2026-08-22", fakeApi.lastUpdateTaskRequest?.plannerToday)
        assertEquals("2026-08-22", fakeApi.lastUpdateTaskRequest?.effectiveDate)
        assertEquals("ONCE", fakeApi.lastUpdateTaskRequest?.schedule?.type)
        assertEquals("2026-08-23", fakeApi.lastUpdateTaskRequest?.schedule?.startDate)

        assertTrue(fakeDb.applyUpdateTaskScheduleCalled)
        // Task updated in Room
        val savedTask = fakeDb.tasksMap["task-combined-1"]
        assertNotNull(savedTask)
        assertEquals("New Canonical Title", savedTask?.title)
        assertEquals("New Note Content", savedTask?.note)
        assertEquals(true, savedTask?.isImportant)

        // Schedule updated in Room (old schedule removed, new schedule added)
        assertNull(fakeDb.schedulesMap["sched-old-1"])
        val savedSched = fakeDb.schedulesMap["sched-combined-1"]
        assertNotNull(savedSched)
        assertEquals("2026-08-23", savedSched?.startDate)
        assertEquals("11:00:00", savedSched?.scheduledTime)
        assertEquals(15, savedSched?.reminderMinutesBefore)
    }

    @Test
    fun updateTask_networkFailure_leavesRoomUntouched() = runTest {
        val fakeApi = FakePlannerApi(updateTaskResult = Result.failure(IOException("No connection")))
        val fakeDb = InMemoryTestDatabase()

        fakeDb.tasksMap["task-fail-combined"] = TaskEntity("task-fail-combined", "Old Title", "Old Note", false, "", "")
        val repository = PlannerRepository(fakeDb, tokenProvider = { "token_123" }, fakeApi)

        val result = repository.updateTask(
            taskId = "task-fail-combined",
            title = "Changed Title",
            note = "Changed Note",
            isImportant = true,
            plannerToday = "2026-08-22",
            effectiveDate = "2026-08-22",
            schedule = UpdateTaskScheduleDto(type = "ONCE", startDate = "2026-08-25")
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals("Old Title", fakeDb.tasksMap["task-fail-combined"]?.title)
    }

    @Test
    fun updateTask_401Unauthorized_propagatesHttpException() = runTest {
        val http401 = HttpException(
            Response.error<UpdateTaskResponseDto>(
                401,
                "{}".toResponseBody("application/json".toMediaType())
            )
        )
        val fakeApi = FakePlannerApi(updateTaskResult = Result.failure(http401))
        val fakeDb = InMemoryTestDatabase()

        val repository = PlannerRepository(fakeDb, tokenProvider = { "token_123" }, fakeApi)

        val result = repository.updateTask(
            taskId = "task-401-comb",
            title = "Title",
            note = null,
            isImportant = false,
            plannerToday = "2026-08-22",
            effectiveDate = "2026-08-22",
            schedule = UpdateTaskScheduleDto(type = "ONCE", startDate = "2026-08-22")
        )

        assertTrue(result.isFailure)
        val err = result.exceptionOrNull()
        assertTrue(err is HttpException)
        assertEquals(401, (err as HttpException).code())
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun sharedMutex_serializesRefreshAndUpdateTask_staleRefreshCannotOverwriteNewerCombinedSave() = runTest {
        val sharedMutex = Mutex()
        val refreshDeferred = CompletableDeferred<Unit>()

        val staleSnapshot = PlannerRefreshResponseDto(
            tasks = listOf(
                RefreshTaskDto(
                    id = "task-comb-lock",
                    title = "Stale Old Title",
                    note = "Stale Old Note",
                    isImportant = false,
                    createdAt = "2026-08-18T10:00:00.000Z",
                    updatedAt = "2026-08-18T10:00:00.000Z"
                )
            ),
            schedules = emptyList(),
            completions = emptyList()
        )

        val canonicalUpdate = UpdateTaskResponseDto(
            task = TaskResponseDto(
                id = "task-comb-lock",
                title = "New Canonical Title",
                note = "New Canonical Note",
                isImportant = true,
                createdAt = "2026-08-18T10:00:00.000Z",
                updatedAt = "2026-08-22T10:00:00.000Z",
                schedules = listOf(
                    TaskScheduleResponseDto(
                        id = "sched-comb-lock",
                        type = "ONCE",
                        startDate = "2026-08-25",
                        endDate = "2026-08-25",
                        scheduledTime = null,
                        intervalDays = null,
                        intervalAnchorDate = null,
                        weekdaysMask = null,
                        reminderMinutesBefore = null,
                        createdAt = "2026-08-22T10:00:00.000Z",
                        updatedAt = "2026-08-22T10:00:00.000Z"
                    )
                )
            ),
            completions = emptyList()
        )

        val fakeApi = FakePlannerApi(
            refreshResult = Result.success(staleSnapshot),
            updateTaskResult = Result.success(canonicalUpdate),
            delayRefreshDeferred = refreshDeferred
        )
        val fakeDb = InMemoryTestDatabase()

        val repo1 = PlannerRepository(fakeDb, { "token" }, fakeApi, sharedMutex)
        val repo2 = PlannerRepository(fakeDb, { "token" }, fakeApi, sharedMutex)

        // 1. Start Refresh first
        val refreshJob = async { repo1.refresh() }
        testScheduler.runCurrent()

        assertEquals(1, fakeApi.getRefreshCallCount)
        assertTrue(sharedMutex.isLocked)

        // 2. Trigger Combined Update while Refresh is in-flight
        val updateJob = async {
            repo2.updateTask(
                taskId = "task-comb-lock",
                title = "New Canonical Title",
                note = "New Canonical Note",
                isImportant = true,
                plannerToday = "2026-08-22",
                effectiveDate = "2026-08-25",
                schedule = UpdateTaskScheduleDto(type = "ONCE", startDate = "2026-08-25")
            )
        }
        testScheduler.runCurrent()

        assertEquals(0, fakeApi.updateTaskCallCount)

        // 3. Complete Refresh response -> Refresh finishes and applies stale snapshot, then combined update executes
        refreshDeferred.complete(Unit)
        testScheduler.runCurrent()
        refreshJob.await()
        updateJob.await()

        assertEquals(1, fakeApi.updateTaskCallCount)
        assertEquals("New Canonical Title", fakeDb.tasksMap["task-comb-lock"]?.title)
        assertEquals("New Canonical Note", fakeDb.tasksMap["task-comb-lock"]?.note)
        assertEquals(true, fakeDb.tasksMap["task-comb-lock"]?.isImportant)
        assertNotNull(fakeDb.schedulesMap["sched-comb-lock"])
    }
}
