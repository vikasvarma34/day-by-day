package com.vikaspokala.daybyday.ui.screens.settings

import androidx.room.DatabaseConfiguration
import androidx.room.InvalidationTracker
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.vikaspokala.daybyday.data.local.planner.PlannerDatabase
import com.vikaspokala.daybyday.data.local.planner.dao.CompletionDao
import com.vikaspokala.daybyday.data.local.planner.dao.ScheduleDao
import com.vikaspokala.daybyday.data.local.planner.dao.TaskDao
import com.vikaspokala.daybyday.data.remote.api.PlannerApi
import com.vikaspokala.daybyday.data.remote.dto.PlannerRefreshResponseDto
import com.vikaspokala.daybyday.data.repository.PlannerRepository
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class DummyPlannerDatabase : PlannerDatabase() {
        override fun taskDao(): TaskDao = error("Not needed")
        override fun scheduleDao(): ScheduleDao = error("Not needed")
        override fun completionDao(): CompletionDao = error("Not needed")
        override fun createInvalidationTracker(): InvalidationTracker = error("Not needed")
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun createOpenHelper(config: DatabaseConfiguration): SupportSQLiteOpenHelper = error("Not needed")
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun clearAllTables() {}
    }

    private class DummyPlannerApi : PlannerApi {
        override suspend fun createTask(
            authorization: String,
            request: com.vikaspokala.daybyday.data.remote.dto.CreateTaskRequestDto
        ): com.vikaspokala.daybyday.data.remote.dto.CreateTaskResponseDto = error("Not needed")
        override suspend fun getRefresh(authorization: String): PlannerRefreshResponseDto = error("Not needed")
        override suspend fun completeTask(
            authorization: String,
            taskId: String,
            request: com.vikaspokala.daybyday.data.remote.dto.CompleteTaskRequestDto
        ): com.vikaspokala.daybyday.data.remote.dto.CompleteTaskResponseDto = error("Not needed")
        override suspend fun undoTask(
            authorization: String,
            taskId: String,
            request: com.vikaspokala.daybyday.data.remote.dto.UndoTaskRequestDto
        ): com.vikaspokala.daybyday.data.remote.dto.TaskActionResponseDto = error("Not needed")
        override suspend fun deleteTask(
            authorization: String,
            taskId: String
        ): com.vikaspokala.daybyday.data.remote.dto.DeleteTaskResponseDto = error("Not needed")
    }

    private class FakePlannerRepository(
        var refreshResult: Result<Unit> = Result.success(Unit),
        var delayDeferred: CompletableDeferred<Unit>? = null
    ) : PlannerRepository(
        plannerDatabase = DummyPlannerDatabase(),
        tokenProvider = { "test_token" },
        plannerApi = DummyPlannerApi()
    ) {
        var refreshCallCount = 0

        override suspend fun refresh(): Result<Unit> {
            refreshCallCount++
            delayDeferred?.await()
            return refreshResult
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_isIdle_andDoesNotTriggerAutomaticRefresh() = runTest(testDispatcher) {
        val fakeRepo = FakePlannerRepository()
        var sessionExpiredCalled = false
        val viewModel = SettingsViewModel(
            plannerRepository = fakeRepo,
            onSessionExpired = { sessionExpiredCalled = true },
            scope = CoroutineScope(testDispatcher)
        )

        advanceUntilIdle()

        assertEquals(RefreshUiState.Idle, viewModel.refreshState.value)
        assertEquals(0, fakeRepo.refreshCallCount)
        assertFalse(sessionExpiredCalled)
    }

    @Test
    fun refreshPlannerData_success_transitionsToIdle() = runTest(testDispatcher) {
        val fakeRepo = FakePlannerRepository(refreshResult = Result.success(Unit))
        val viewModel = SettingsViewModel(
            plannerRepository = fakeRepo,
            scope = CoroutineScope(testDispatcher)
        )

        viewModel.refreshPlannerData()
        advanceUntilIdle()

        assertEquals(1, fakeRepo.refreshCallCount)
        assertEquals(RefreshUiState.Idle, viewModel.refreshState.value)
    }

    @Test
    fun refreshPlannerData_inFlight_preventsDuplicateExecution() = runTest(testDispatcher) {
        val deferred = CompletableDeferred<Unit>()
        val fakeRepo = FakePlannerRepository(
            refreshResult = Result.success(Unit),
            delayDeferred = deferred
        )
        val viewModel = SettingsViewModel(
            plannerRepository = fakeRepo,
            scope = CoroutineScope(testDispatcher)
        )

        // Launch first refresh (will hang on deferred)
        viewModel.refreshPlannerData()
        testDispatcher.scheduler.runCurrent()

        assertEquals(RefreshUiState.Refreshing, viewModel.refreshState.value)
        assertEquals(1, fakeRepo.refreshCallCount)

        // Tap again while in flight
        viewModel.refreshPlannerData()
        testDispatcher.scheduler.runCurrent()

        // Call count should still be 1
        assertEquals(1, fakeRepo.refreshCallCount)

        // Complete first refresh
        deferred.complete(Unit)
        advanceUntilIdle()

        assertEquals(RefreshUiState.Idle, viewModel.refreshState.value)
        assertEquals(1, fakeRepo.refreshCallCount)
    }

    @Test
    fun refreshPlannerData_networkOrServerError_transitionsToError_andAllowsRetry() = runTest(testDispatcher) {
        val fakeRepo = FakePlannerRepository(
            refreshResult = Result.failure(IOException("Server unavailable"))
        )
        var sessionExpiredCalled = false
        val viewModel = SettingsViewModel(
            plannerRepository = fakeRepo,
            onSessionExpired = { sessionExpiredCalled = true },
            scope = CoroutineScope(testDispatcher)
        )

        viewModel.refreshPlannerData()
        advanceUntilIdle()

        assertEquals(1, fakeRepo.refreshCallCount)
        assertEquals(RefreshUiState.Error, viewModel.refreshState.value)
        assertFalse("Session must not be expired on network/server error", sessionExpiredCalled)

        // Retry
        fakeRepo.refreshResult = Result.success(Unit)
        viewModel.refreshPlannerData()
        advanceUntilIdle()

        assertEquals(2, fakeRepo.refreshCallCount)
        assertEquals(RefreshUiState.Idle, viewModel.refreshState.value)
    }

    @Test
    fun refreshPlannerData_401Unauthorized_invokesOnSessionExpired_andResetsToIdle() = runTest(testDispatcher) {
        val http401Exception = HttpException(
            Response.error<PlannerRefreshResponseDto>(
                401,
                "{}".toResponseBody("application/json".toMediaType())
            )
        )
        val fakeRepo = FakePlannerRepository(
            refreshResult = Result.failure(http401Exception)
        )
        var sessionExpiredCalled = false
        val viewModel = SettingsViewModel(
            plannerRepository = fakeRepo,
            onSessionExpired = { sessionExpiredCalled = true },
            scope = CoroutineScope(testDispatcher)
        )

        viewModel.refreshPlannerData()
        advanceUntilIdle()

        assertEquals(1, fakeRepo.refreshCallCount)
        assertTrue("Session expiration callback must be triggered on 401", sessionExpiredCalled)
        assertEquals(RefreshUiState.Idle, viewModel.refreshState.value)
    }
}
