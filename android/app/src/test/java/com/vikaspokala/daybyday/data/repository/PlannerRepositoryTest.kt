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
import com.vikaspokala.daybyday.data.remote.dto.PlannerRefreshResponseDto
import com.vikaspokala.daybyday.data.remote.dto.RefreshCompletionDto
import com.vikaspokala.daybyday.data.remote.dto.RefreshScheduleDto
import com.vikaspokala.daybyday.data.remote.dto.RefreshTaskDto
import com.vikaspokala.daybyday.data.remote.dto.TaskActionResponseDto
import com.vikaspokala.daybyday.data.remote.dto.TaskResponseDto
import com.vikaspokala.daybyday.data.remote.dto.TaskScheduleResponseDto
import com.vikaspokala.daybyday.data.remote.dto.UndoTaskRequestDto
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
        var delayRefreshDeferred: CompletableDeferred<Unit>? = null
    ) : PlannerApi {
        var lastAuthorizationHeader: String? = null
        var lastCreateRequest: CreateTaskRequestDto? = null
        var lastCompleteTaskId: String? = null
        var lastCompleteRequest: CompleteTaskRequestDto? = null
        var lastUndoTaskId: String? = null
        var lastUndoRequest: UndoTaskRequestDto? = null
        var createCallCount = 0
        var getRefreshCallCount = 0
        var completeCallCount = 0
        var undoCallCount = 0

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

        private val fakeTaskDao = object : TaskDao {
            override suspend fun insertAll(tasks: List<TaskEntity>) {
                tasks.forEach { tasksMap[it.id] = it }
            }
            override suspend fun insert(task: TaskEntity) {
                tasksMap[task.id] = task
            }
            override suspend fun deleteAll() { tasksMap.clear() }
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
            override suspend fun getAll(): List<ScheduleEntity> = schedulesMap.values.toList()
            override suspend fun getById(id: String): ScheduleEntity? = schedulesMap[id]
        }

        private val fakeCompletionDao = object : CompletionDao {
            override suspend fun insertAll(completions: List<CompletionEntity>) {
                completionsList.addAll(completions)
            }
            override suspend fun insert(completion: CompletionEntity) {
                completionsList.removeAll { it.id == completion.id }
                completionsList.add(completion)
            }
            override suspend fun deleteAll() { completionsList.clear() }
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
}
