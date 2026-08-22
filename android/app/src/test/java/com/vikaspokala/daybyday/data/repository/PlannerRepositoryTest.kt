package com.vikaspokala.daybyday.data.repository

import androidx.room.DatabaseConfiguration
import androidx.room.InvalidationTracker
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.vikaspokala.daybyday.data.local.planner.PlannerDatabase
import com.vikaspokala.daybyday.data.local.planner.dao.CompletionDao
import com.vikaspokala.daybyday.data.local.planner.dao.ScheduleDao
import com.vikaspokala.daybyday.data.local.planner.dao.TaskDao
import com.vikaspokala.daybyday.data.local.planner.entity.CompletionEntity
import com.vikaspokala.daybyday.data.local.planner.entity.ScheduleEntity
import com.vikaspokala.daybyday.data.local.planner.entity.TaskEntity
import com.vikaspokala.daybyday.data.remote.api.PlannerApi
import com.vikaspokala.daybyday.data.remote.dto.PlannerRefreshResponseDto
import com.vikaspokala.daybyday.data.remote.dto.RefreshCompletionDto
import com.vikaspokala.daybyday.data.remote.dto.RefreshScheduleDto
import com.vikaspokala.daybyday.data.remote.dto.RefreshTaskDto
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerRepositoryTest {

    private class FakePlannerApi(
        var refreshResult: Result<PlannerRefreshResponseDto>? = null,
        var lastAuthorizationHeader: String? = null
    ) : PlannerApi {
        override suspend fun getRefresh(authorization: String): PlannerRefreshResponseDto {
            lastAuthorizationHeader = authorization
            return refreshResult?.getOrThrow() ?: error("refreshResult not configured")
        }
    }

    private class FakePlannerDatabase : PlannerDatabase() {
        var replacedTasks: List<TaskEntity>? = null
        var replacedSchedules: List<ScheduleEntity>? = null
        var replacedCompletions: List<CompletionEntity>? = null

        override fun taskDao(): TaskDao = error("Not needed for replaceSnapshot test")
        override fun scheduleDao(): ScheduleDao = error("Not needed for replaceSnapshot test")
        override fun completionDao(): CompletionDao = error("Not needed for replaceSnapshot test")

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
            replacedTasks = tasks
            replacedSchedules = schedules
            replacedCompletions = completions
        }
    }

    @Test
    fun refresh_missingToken_returnsFailureWithoutCallingNetwork() = runTest {
        val fakeApi = FakePlannerApi()
        val fakeDb = FakePlannerDatabase()
        val repository = PlannerRepository(fakeDb, tokenProvider = { null }, fakeApi)

        val result = repository.refresh()

        assertTrue(result.isFailure)
        assertEquals("Missing authentication session token", result.exceptionOrNull()?.message)
        assertTrue(fakeApi.lastAuthorizationHeader == null)
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
        val fakeDb = FakePlannerDatabase()
        val repository = PlannerRepository(fakeDb, tokenProvider = { "session_token_123" }, fakeApi)

        val result = repository.refresh()

        assertTrue(result.isSuccess)
        assertEquals("Bearer session_token_123", fakeApi.lastAuthorizationHeader)

        assertEquals(1, fakeDb.replacedTasks?.size)
        assertEquals("task-1", fakeDb.replacedTasks?.first()?.id)
        assertEquals("Read Room Docs", fakeDb.replacedTasks?.first()?.title)

        assertEquals(1, fakeDb.replacedSchedules?.size)
        assertEquals(31, fakeDb.replacedSchedules?.first()?.weekdaysMask)

        assertEquals(1, fakeDb.replacedCompletions?.size)
        assertEquals("comp-1", fakeDb.replacedCompletions?.first()?.id)
    }

    @Test
    fun refresh_networkFailure_returnsFailure() = runTest {
        val fakeApi = FakePlannerApi(refreshResult = Result.failure(IOException("Server unavailable")))
        val fakeDb = FakePlannerDatabase()
        val repository = PlannerRepository(fakeDb, tokenProvider = { "session_token_123" }, fakeApi)

        val result = repository.refresh()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        assertTrue(fakeDb.replacedTasks == null)
    }
}
