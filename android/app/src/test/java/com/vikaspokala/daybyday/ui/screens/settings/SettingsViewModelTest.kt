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
import org.junit.Assert.assertNull
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
        override suspend fun updateTask(
            authorization: String,
            taskId: String,
            request: com.vikaspokala.daybyday.data.remote.dto.UpdateTaskRequestDto
        ): com.vikaspokala.daybyday.data.remote.dto.UpdateTaskResponseDto = error("Not needed")
        override suspend fun updateTaskSchedule(
            authorization: String,
            taskId: String,
            request: com.vikaspokala.daybyday.data.remote.dto.UpdateTaskSchedulePayloadDto
        ): com.vikaspokala.daybyday.data.remote.dto.UpdateTaskResponseDto = error("Not needed")
        override suspend fun updateTaskContent(
            authorization: String,
            taskId: String,
            request: com.vikaspokala.daybyday.data.remote.dto.UpdateTaskContentRequestDto
        ): com.vikaspokala.daybyday.data.remote.dto.UpdateTaskResponseDto = error("Not needed")
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

    private class FakeAuthRepository(
        var updateProfileResult: Result<com.vikaspokala.daybyday.data.remote.dto.AuthUserDto> = Result.success(
            com.vikaspokala.daybyday.data.remote.dto.AuthUserDto(
                id = "11111111-2222-3333-4444-555555555555",
                email = "test@example.com",
                firstName = "Vikas",
                lastName = "Varma",
                nickname = "Vicky"
            )
        ),
        var changePasswordResult: Result<Unit> = Result.success(Unit),
        var verifySessionResult: Result<com.vikaspokala.daybyday.data.remote.dto.AuthUserDto>? = null,
        var storedToken: String? = "test_token",
        var delayDeferred: CompletableDeferred<Unit>? = null
    ) : com.vikaspokala.daybyday.data.repository.AuthRepository() {
        var updateProfileCallCount = 0
        var lastRequest: com.vikaspokala.daybyday.data.remote.dto.UpdateProfileRequestDto? = null

        var changePasswordCallCount = 0
        var lastChangePasswordRequest: com.vikaspokala.daybyday.data.remote.dto.ChangePasswordRequestDto? = null

        var verifySessionCallCount = 0

        override suspend fun getStoredToken(): String? = storedToken

        override suspend fun verifySession(token: String): com.vikaspokala.daybyday.data.remote.dto.AuthUserDto {
            verifySessionCallCount++
            return (verifySessionResult ?: updateProfileResult).getOrThrow()
        }

        override suspend fun updateProfile(request: com.vikaspokala.daybyday.data.remote.dto.UpdateProfileRequestDto): com.vikaspokala.daybyday.data.remote.dto.AuthUserDto {
            updateProfileCallCount++
            lastRequest = request
            delayDeferred?.await()
            return updateProfileResult.getOrThrow()
        }

        override suspend fun changePassword(request: com.vikaspokala.daybyday.data.remote.dto.ChangePasswordRequestDto) {
            changePasswordCallCount++
            lastChangePasswordRequest = request
            delayDeferred?.await()
            changePasswordResult.getOrThrow()
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
        assertEquals(ProfileEditUiState.Idle, viewModel.profileEditState.value)
        assertEquals(0, fakeRepo.refreshCallCount)
        assertFalse(sessionExpiredCalled)
    }

    @Test
    fun refreshPlannerData_success_transitionsToIdle() = runTest(testDispatcher) {
        val fakeRepo = FakePlannerRepository(refreshResult = Result.success(Unit))
        val viewModel = SettingsViewModel(
            plannerRepository = fakeRepo,
            authRepository = FakeAuthRepository(),
            scope = CoroutineScope(testDispatcher)
        )

        viewModel.refreshPlannerData()
        advanceUntilIdle()

        assertEquals(1, fakeRepo.refreshCallCount)
        assertEquals(RefreshUiState.Idle, viewModel.refreshState.value)
    }

    @Test
    fun refreshPlannerData_success_updatesCurrentUserProfile() = runTest(testDispatcher) {
        val updatedUser = com.vikaspokala.daybyday.data.remote.dto.AuthUserDto(
            id = "11111111-2222-3333-4444-555555555555",
            email = "test@example.com",
            firstName = "UpdatedFirst",
            lastName = "UpdatedLast",
            nickname = "UpdatedNick"
        )
        val fakePlannerRepo = FakePlannerRepository(refreshResult = Result.success(Unit))
        val fakeAuthRepo = FakeAuthRepository(
            verifySessionResult = Result.success(updatedUser)
        )
        val viewModel = SettingsViewModel(
            plannerRepository = fakePlannerRepo,
            authRepository = fakeAuthRepo,
            scope = CoroutineScope(testDispatcher)
        )

        var passedUser: com.vikaspokala.daybyday.data.remote.dto.AuthUserDto? = null
        viewModel.refreshPlannerData(onUserUpdated = { passedUser = it })
        advanceUntilIdle()

        assertEquals(1, fakePlannerRepo.refreshCallCount)
        assertEquals(1, fakeAuthRepo.verifySessionCallCount)
        assertEquals(RefreshUiState.Idle, viewModel.refreshState.value)
        assertEquals(updatedUser, passedUser)
        assertEquals(updatedUser, viewModel.currentUser.value)
    }

    @Test
    fun refreshPlannerData_profileVerificationFailsWith500_transitionsToError_andDoesNotSilentlySucceed() = runTest(testDispatcher) {
        val http500Exception = HttpException(
            Response.error<Any>(
                500,
                "{}".toResponseBody("application/json".toMediaType())
            )
        )
        val fakeAuthRepo = FakeAuthRepository(
            verifySessionResult = Result.failure(http500Exception)
        )
        val fakePlannerRepo = FakePlannerRepository(refreshResult = Result.success(Unit))
        val viewModel = SettingsViewModel(
            plannerRepository = fakePlannerRepo,
            authRepository = fakeAuthRepo,
            scope = CoroutineScope(testDispatcher)
        )

        var passedUser: com.vikaspokala.daybyday.data.remote.dto.AuthUserDto? = null
        viewModel.refreshPlannerData(onUserUpdated = { passedUser = it })
        advanceUntilIdle()

        assertEquals(RefreshUiState.Error, viewModel.refreshState.value)
        assertNull(passedUser)
        assertEquals(0, fakePlannerRepo.refreshCallCount)
    }

    @Test
    fun refreshPlannerData_profileVerification401_invokesOnSessionExpired() = runTest(testDispatcher) {
        val http401Exception = HttpException(
            Response.error<Any>(
                401,
                "{}".toResponseBody("application/json".toMediaType())
            )
        )
        val fakeAuthRepo = FakeAuthRepository(
            verifySessionResult = Result.failure(http401Exception)
        )
        val fakePlannerRepo = FakePlannerRepository(refreshResult = Result.success(Unit))
        var sessionExpiredCalled = false
        val viewModel = SettingsViewModel(
            plannerRepository = fakePlannerRepo,
            authRepository = fakeAuthRepo,
            onSessionExpired = { sessionExpiredCalled = true },
            scope = CoroutineScope(testDispatcher)
        )

        viewModel.refreshPlannerData()
        advanceUntilIdle()

        assertTrue(sessionExpiredCalled)
        assertEquals(RefreshUiState.Idle, viewModel.refreshState.value)
        assertEquals(0, fakePlannerRepo.refreshCallCount)
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
            authRepository = FakeAuthRepository(),
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
            authRepository = FakeAuthRepository(),
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
            authRepository = FakeAuthRepository(),
            onSessionExpired = { sessionExpiredCalled = true },
            scope = CoroutineScope(testDispatcher)
        )

        viewModel.refreshPlannerData()
        advanceUntilIdle()

        assertEquals(1, fakeRepo.refreshCallCount)
        assertTrue("Session expiration callback must be triggered on 401", sessionExpiredCalled)
        assertEquals(RefreshUiState.Idle, viewModel.refreshState.value)
    }

    @Test
    fun updateProfile_firstName_success_updatesCanonicalUserAndNavigates() = runTest(testDispatcher) {
        val updatedUser = com.vikaspokala.daybyday.data.remote.dto.AuthUserDto(
            id = "11111111-2222-3333-4444-555555555555",
            email = "dev.user@example.com",
            firstName = "Vikram",
            lastName = "Varma",
            nickname = "Vicky"
        )
        val fakeAuthRepo = FakeAuthRepository(updateProfileResult = Result.success(updatedUser))
        val viewModel = SettingsViewModel(
            plannerRepository = FakePlannerRepository(),
            authRepository = fakeAuthRepo,
            scope = CoroutineScope(testDispatcher)
        )

        var userUpdated: com.vikaspokala.daybyday.data.remote.dto.AuthUserDto? = null
        var successNavigated = false

        viewModel.updateProfile(
            fieldType = com.vikaspokala.daybyday.ui.navigation.ProfileFieldType.FIRST_NAME,
            newValue = "  Vikram  ",
            onUserUpdated = { userUpdated = it },
            onSuccess = { successNavigated = true }
        )
        advanceUntilIdle()

        assertEquals(1, fakeAuthRepo.updateProfileCallCount)
        assertEquals("Vikram", fakeAuthRepo.lastRequest?.firstName)
        assertEquals(null, fakeAuthRepo.lastRequest?.lastName)
        assertEquals(null, fakeAuthRepo.lastRequest?.nickname)
        assertFalse(fakeAuthRepo.lastRequest?.includeNickname ?: true)

        assertEquals(updatedUser, userUpdated)
        assertEquals(updatedUser, viewModel.currentUser.value)
        assertTrue(successNavigated)
        assertEquals(ProfileEditUiState.Idle, viewModel.profileEditState.value)
    }

    @Test
    fun updateProfile_nickname_success_updatesCanonicalUser() = runTest(testDispatcher) {
        val updatedUser = com.vikaspokala.daybyday.data.remote.dto.AuthUserDto(
            id = "11111111-2222-3333-4444-555555555555",
            email = "dev.user@example.com",
            firstName = "Vikas",
            lastName = "Varma",
            nickname = "Vik"
        )
        val fakeAuthRepo = FakeAuthRepository(updateProfileResult = Result.success(updatedUser))
        val viewModel = SettingsViewModel(
            plannerRepository = FakePlannerRepository(),
            authRepository = fakeAuthRepo,
            scope = CoroutineScope(testDispatcher)
        )

        var userUpdated: com.vikaspokala.daybyday.data.remote.dto.AuthUserDto? = null
        var successNavigated = false

        viewModel.updateProfile(
            fieldType = com.vikaspokala.daybyday.ui.navigation.ProfileFieldType.NICKNAME,
            newValue = "Vik",
            onUserUpdated = { userUpdated = it },
            onSuccess = { successNavigated = true }
        )
        advanceUntilIdle()

        assertEquals(1, fakeAuthRepo.updateProfileCallCount)
        assertEquals("Vik", fakeAuthRepo.lastRequest?.nickname)
        assertTrue(fakeAuthRepo.lastRequest?.includeNickname == true)
        assertEquals(updatedUser, userUpdated)
        assertTrue(successNavigated)
        assertEquals(ProfileEditUiState.Idle, viewModel.profileEditState.value)
    }

    @Test
    fun updateProfile_clearingNickname_sendsNullAndUpdatesCanonicalUser() = runTest(testDispatcher) {
        val updatedUser = com.vikaspokala.daybyday.data.remote.dto.AuthUserDto(
            id = "11111111-2222-3333-4444-555555555555",
            email = "dev.user@example.com",
            firstName = "Vikas",
            lastName = "Varma",
            nickname = null
        )
        val fakeAuthRepo = FakeAuthRepository(updateProfileResult = Result.success(updatedUser))
        val viewModel = SettingsViewModel(
            plannerRepository = FakePlannerRepository(),
            authRepository = fakeAuthRepo,
            scope = CoroutineScope(testDispatcher)
        )

        var userUpdated: com.vikaspokala.daybyday.data.remote.dto.AuthUserDto? = null
        var successNavigated = false

        viewModel.updateProfile(
            fieldType = com.vikaspokala.daybyday.ui.navigation.ProfileFieldType.NICKNAME,
            newValue = "   ",
            onUserUpdated = { userUpdated = it },
            onSuccess = { successNavigated = true }
        )
        advanceUntilIdle()

        assertEquals(1, fakeAuthRepo.updateProfileCallCount)
        assertEquals(null, fakeAuthRepo.lastRequest?.nickname)
        assertTrue(fakeAuthRepo.lastRequest?.includeNickname == true)
        assertEquals(updatedUser, userUpdated)
        assertTrue(successNavigated)
        assertEquals(ProfileEditUiState.Idle, viewModel.profileEditState.value)
    }

    @Test
    fun updateProfile_blankFirstName_showsFriendlyErrorAndDoesNotCallRepository() = runTest(testDispatcher) {
        val fakeAuthRepo = FakeAuthRepository()
        val viewModel = SettingsViewModel(
            plannerRepository = FakePlannerRepository(),
            authRepository = fakeAuthRepo,
            scope = CoroutineScope(testDispatcher)
        )

        var userUpdated: com.vikaspokala.daybyday.data.remote.dto.AuthUserDto? = null
        var successNavigated = false

        viewModel.updateProfile(
            fieldType = com.vikaspokala.daybyday.ui.navigation.ProfileFieldType.FIRST_NAME,
            newValue = "   ",
            onUserUpdated = { userUpdated = it },
            onSuccess = { successNavigated = true }
        )
        advanceUntilIdle()

        assertEquals(0, fakeAuthRepo.updateProfileCallCount)
        assertEquals(null, userUpdated)
        assertFalse(successNavigated)
        assertTrue(viewModel.profileEditState.value is ProfileEditUiState.Error)
        assertEquals(
            "Please enter your first name.",
            (viewModel.profileEditState.value as ProfileEditUiState.Error).message
        )
    }

    @Test
    fun updateProfile_blankLastName_showsFriendlyErrorAndDoesNotCallRepository() = runTest(testDispatcher) {
        val fakeAuthRepo = FakeAuthRepository()
        val viewModel = SettingsViewModel(
            plannerRepository = FakePlannerRepository(),
            authRepository = fakeAuthRepo,
            scope = CoroutineScope(testDispatcher)
        )

        var userUpdated: com.vikaspokala.daybyday.data.remote.dto.AuthUserDto? = null
        var successNavigated = false

        viewModel.updateProfile(
            fieldType = com.vikaspokala.daybyday.ui.navigation.ProfileFieldType.LAST_NAME,
            newValue = "",
            onUserUpdated = { userUpdated = it },
            onSuccess = { successNavigated = true }
        )
        advanceUntilIdle()

        assertEquals(0, fakeAuthRepo.updateProfileCallCount)
        assertEquals(null, userUpdated)
        assertFalse(successNavigated)
        assertTrue(viewModel.profileEditState.value is ProfileEditUiState.Error)
        assertEquals(
            "Please enter your last name.",
            (viewModel.profileEditState.value as ProfileEditUiState.Error).message
        )
    }

    @Test
    fun updateProfile_backendValidationFailure_mapsToFriendlyMessage() = runTest(testDispatcher) {
        val http400Exception = HttpException(
            Response.error<com.vikaspokala.daybyday.data.remote.dto.UpdateProfileResponseDto>(
                400,
                """{"error":{"code":"BAD_REQUEST","message":"Invalid firstName: must not exceed length"}}""".toResponseBody("application/json".toMediaType())
            )
        )
        val fakeAuthRepo = FakeAuthRepository(updateProfileResult = Result.failure(http400Exception))
        val viewModel = SettingsViewModel(
            plannerRepository = FakePlannerRepository(),
            authRepository = fakeAuthRepo,
            scope = CoroutineScope(testDispatcher)
        )

        var userUpdated: com.vikaspokala.daybyday.data.remote.dto.AuthUserDto? = null
        var successNavigated = false

        viewModel.updateProfile(
            fieldType = com.vikaspokala.daybyday.ui.navigation.ProfileFieldType.FIRST_NAME,
            newValue = "TooLongName",
            onUserUpdated = { userUpdated = it },
            onSuccess = { successNavigated = true }
        )
        advanceUntilIdle()

        assertEquals(1, fakeAuthRepo.updateProfileCallCount)
        assertEquals(null, userUpdated)
        assertFalse(successNavigated)
        assertTrue(viewModel.profileEditState.value is ProfileEditUiState.Error)
        assertEquals(
            "Please enter your first name.",
            (viewModel.profileEditState.value as ProfileEditUiState.Error).message
        )
    }

    @Test
    fun updateProfile_networkError_setsCalmErrorAndPreservesUser() = runTest(testDispatcher) {
        val fakeAuthRepo = FakeAuthRepository(updateProfileResult = Result.failure(IOException("No connection")))
        val viewModel = SettingsViewModel(
            plannerRepository = FakePlannerRepository(),
            authRepository = fakeAuthRepo,
            scope = CoroutineScope(testDispatcher)
        )

        var userUpdated: com.vikaspokala.daybyday.data.remote.dto.AuthUserDto? = null
        var successNavigated = false

        viewModel.updateProfile(
            fieldType = com.vikaspokala.daybyday.ui.navigation.ProfileFieldType.FIRST_NAME,
            newValue = "Vikram",
            onUserUpdated = { userUpdated = it },
            onSuccess = { successNavigated = true }
        )
        advanceUntilIdle()

        assertEquals(1, fakeAuthRepo.updateProfileCallCount)
        assertEquals(null, userUpdated)
        assertFalse(successNavigated)
        assertTrue(viewModel.profileEditState.value is ProfileEditUiState.Error)
        assertEquals(
            "Unable to connect to server. Check your connection and try again.",
            (viewModel.profileEditState.value as ProfileEditUiState.Error).message
        )
    }

    @Test
    fun updateProfile_server500Error_setsCalmErrorAndPreservesUser() = runTest(testDispatcher) {
        val http500Exception = HttpException(
            Response.error<com.vikaspokala.daybyday.data.remote.dto.UpdateProfileResponseDto>(
                500,
                """{"error":{"code":"INTERNAL_ERROR","message":"Internal server error"}}""".toResponseBody("application/json".toMediaType())
            )
        )
        val fakeAuthRepo = FakeAuthRepository(updateProfileResult = Result.failure(http500Exception))
        val viewModel = SettingsViewModel(
            plannerRepository = FakePlannerRepository(),
            authRepository = fakeAuthRepo,
            scope = CoroutineScope(testDispatcher)
        )

        var userUpdated: com.vikaspokala.daybyday.data.remote.dto.AuthUserDto? = null
        var successNavigated = false

        viewModel.updateProfile(
            fieldType = com.vikaspokala.daybyday.ui.navigation.ProfileFieldType.LAST_NAME,
            newValue = "Pokala",
            onUserUpdated = { userUpdated = it },
            onSuccess = { successNavigated = true }
        )
        advanceUntilIdle()

        assertEquals(1, fakeAuthRepo.updateProfileCallCount)
        assertEquals(null, userUpdated)
        assertFalse(successNavigated)
        assertTrue(viewModel.profileEditState.value is ProfileEditUiState.Error)
        assertEquals(
            "Unable to update profile. Please try again.",
            (viewModel.profileEditState.value as ProfileEditUiState.Error).message
        )
    }

    @Test
    fun updateProfile_401Unauthorized_triggersSessionExpiredAndResetsState() = runTest(testDispatcher) {
        val http401Exception = HttpException(
            Response.error<com.vikaspokala.daybyday.data.remote.dto.UpdateProfileResponseDto>(
                401,
                "{}".toResponseBody("application/json".toMediaType())
            )
        )
        val fakeAuthRepo = FakeAuthRepository(updateProfileResult = Result.failure(http401Exception))
        var sessionExpiredCalled = false
        val viewModel = SettingsViewModel(
            plannerRepository = FakePlannerRepository(),
            authRepository = fakeAuthRepo,
            onSessionExpired = { sessionExpiredCalled = true },
            scope = CoroutineScope(testDispatcher)
        )

        viewModel.updateProfile(
            fieldType = com.vikaspokala.daybyday.ui.navigation.ProfileFieldType.FIRST_NAME,
            newValue = "Vikram",
            onUserUpdated = {},
            onSuccess = {}
        )
        advanceUntilIdle()

        assertEquals(1, fakeAuthRepo.updateProfileCallCount)
        assertTrue(sessionExpiredCalled)
        assertEquals(ProfileEditUiState.Idle, viewModel.profileEditState.value)
    }

    @Test
    fun changePassword_blankCurrentPassword_showsFriendlyErrorAndDoesNotCallRepository() = runTest(testDispatcher) {
        val fakeAuthRepo = FakeAuthRepository()
        val viewModel = SettingsViewModel(
            plannerRepository = FakePlannerRepository(),
            authRepository = fakeAuthRepo,
            scope = CoroutineScope(testDispatcher)
        )

        var successCalled = false
        viewModel.changePassword(
            currentPassword = "   ",
            newPassword = "NewValidPassword12345!",
            confirmPassword = "NewValidPassword12345!",
            onSuccess = { successCalled = true }
        )
        advanceUntilIdle()

        assertEquals(0, fakeAuthRepo.changePasswordCallCount)
        assertFalse(successCalled)
        assertTrue(viewModel.changePasswordState.value is ChangePasswordUiState.Error)
        assertEquals(
            "Please enter your current password.",
            (viewModel.changePasswordState.value as ChangePasswordUiState.Error).message
        )
    }

    @Test
    fun changePassword_blankNewPassword_showsFriendlyErrorAndDoesNotCallRepository() = runTest(testDispatcher) {
        val fakeAuthRepo = FakeAuthRepository()
        val viewModel = SettingsViewModel(
            plannerRepository = FakePlannerRepository(),
            authRepository = fakeAuthRepo,
            scope = CoroutineScope(testDispatcher)
        )

        var successCalled = false
        viewModel.changePassword(
            currentPassword = "CurrentPassword12345!",
            newPassword = "",
            confirmPassword = "",
            onSuccess = { successCalled = true }
        )
        advanceUntilIdle()

        assertEquals(0, fakeAuthRepo.changePasswordCallCount)
        assertFalse(successCalled)
        assertTrue(viewModel.changePasswordState.value is ChangePasswordUiState.Error)
        assertEquals(
            "Please enter a new password.",
            (viewModel.changePasswordState.value as ChangePasswordUiState.Error).message
        )
    }

    @Test
    fun changePassword_blankConfirmPassword_showsFriendlyErrorAndDoesNotCallRepository() = runTest(testDispatcher) {
        val fakeAuthRepo = FakeAuthRepository()
        val viewModel = SettingsViewModel(
            plannerRepository = FakePlannerRepository(),
            authRepository = fakeAuthRepo,
            scope = CoroutineScope(testDispatcher)
        )

        var successCalled = false
        viewModel.changePassword(
            currentPassword = "CurrentPassword12345!",
            newPassword = "NewValidPassword12345!",
            confirmPassword = "   ",
            onSuccess = { successCalled = true }
        )
        advanceUntilIdle()

        assertEquals(0, fakeAuthRepo.changePasswordCallCount)
        assertFalse(successCalled)
        assertTrue(viewModel.changePasswordState.value is ChangePasswordUiState.Error)
        assertEquals(
            "Please confirm your new password.",
            (viewModel.changePasswordState.value as ChangePasswordUiState.Error).message
        )
    }

    @Test
    fun changePassword_mismatchedPasswords_showsFriendlyErrorAndDoesNotCallRepository() = runTest(testDispatcher) {
        val fakeAuthRepo = FakeAuthRepository()
        val viewModel = SettingsViewModel(
            plannerRepository = FakePlannerRepository(),
            authRepository = fakeAuthRepo,
            scope = CoroutineScope(testDispatcher)
        )

        var successCalled = false
        viewModel.changePassword(
            currentPassword = "CurrentPassword12345!",
            newPassword = "NewValidPassword12345!",
            confirmPassword = "DifferentPassword12345!",
            onSuccess = { successCalled = true }
        )
        advanceUntilIdle()

        assertEquals(0, fakeAuthRepo.changePasswordCallCount)
        assertFalse(successCalled)
        assertTrue(viewModel.changePasswordState.value is ChangePasswordUiState.Error)
        assertEquals(
            "New passwords do not match.",
            (viewModel.changePasswordState.value as ChangePasswordUiState.Error).message
        )
    }

    @Test
    fun changePassword_success_callsRepositoryWithOnlyCurrentAndNewPassword_andInvokesSuccessCallback() = runTest(testDispatcher) {
        val fakeAuthRepo = FakeAuthRepository()
        val viewModel = SettingsViewModel(
            plannerRepository = FakePlannerRepository(),
            authRepository = fakeAuthRepo,
            scope = CoroutineScope(testDispatcher)
        )

        var successCalled = false
        viewModel.changePassword(
            currentPassword = "OldValidPassword12345!",
            newPassword = "BrandNewValidPassword12345!",
            confirmPassword = "BrandNewValidPassword12345!",
            onSuccess = { successCalled = true }
        )
        advanceUntilIdle()

        assertEquals(1, fakeAuthRepo.changePasswordCallCount)
        assertEquals("OldValidPassword12345!", fakeAuthRepo.lastChangePasswordRequest?.currentPassword)
        assertEquals("BrandNewValidPassword12345!", fakeAuthRepo.lastChangePasswordRequest?.newPassword)
        assertTrue(successCalled)
        assertEquals(ChangePasswordUiState.Idle, viewModel.changePasswordState.value)
    }

    @Test
    fun changePassword_wrongCurrentPassword_showsFriendlyErrorAndPreservesState() = runTest(testDispatcher) {
        val http401Exception = HttpException(
            Response.error<com.vikaspokala.daybyday.data.remote.dto.ChangePasswordResponseDto>(
                401,
                """{"error":{"code":"UNAUTHORIZED","message":"Invalid current password"}}""".toResponseBody("application/json".toMediaType())
            )
        )
        val fakeAuthRepo = FakeAuthRepository(changePasswordResult = Result.failure(http401Exception))
        var sessionExpiredCalled = false
        var successCalled = false
        val viewModel = SettingsViewModel(
            plannerRepository = FakePlannerRepository(),
            authRepository = fakeAuthRepo,
            onSessionExpired = { sessionExpiredCalled = true },
            scope = CoroutineScope(testDispatcher)
        )

        viewModel.changePassword(
            currentPassword = "WrongPassword12345!",
            newPassword = "BrandNewValidPassword12345!",
            confirmPassword = "BrandNewValidPassword12345!",
            onSuccess = { successCalled = true }
        )
        advanceUntilIdle()

        assertEquals(1, fakeAuthRepo.changePasswordCallCount)
        assertFalse(successCalled)
        assertFalse("Wrong current password must not trigger onSessionExpired", sessionExpiredCalled)
        assertTrue(viewModel.changePasswordState.value is ChangePasswordUiState.Error)
        assertEquals(
            "Current password is incorrect.",
            (viewModel.changePasswordState.value as ChangePasswordUiState.Error).message
        )
    }

    @Test
    fun changePassword_networkError_showsFriendlyErrorAndPreservesState() = runTest(testDispatcher) {
        val fakeAuthRepo = FakeAuthRepository(changePasswordResult = Result.failure(IOException("No connection")))
        var sessionExpiredCalled = false
        var successCalled = false
        val viewModel = SettingsViewModel(
            plannerRepository = FakePlannerRepository(),
            authRepository = fakeAuthRepo,
            onSessionExpired = { sessionExpiredCalled = true },
            scope = CoroutineScope(testDispatcher)
        )

        viewModel.changePassword(
            currentPassword = "CurrentPassword12345!",
            newPassword = "BrandNewValidPassword12345!",
            confirmPassword = "BrandNewValidPassword12345!",
            onSuccess = { successCalled = true }
        )
        advanceUntilIdle()

        assertEquals(1, fakeAuthRepo.changePasswordCallCount)
        assertFalse(successCalled)
        assertFalse(sessionExpiredCalled)
        assertTrue(viewModel.changePasswordState.value is ChangePasswordUiState.Error)
        assertEquals(
            "Unable to change password right now. Please try again.",
            (viewModel.changePasswordState.value as ChangePasswordUiState.Error).message
        )
    }

    @Test
    fun changePassword_server500Error_showsFriendlyErrorAndPreservesState() = runTest(testDispatcher) {
        val http500Exception = HttpException(
            Response.error<com.vikaspokala.daybyday.data.remote.dto.ChangePasswordResponseDto>(
                500,
                """{"error":{"code":"INTERNAL_ERROR","message":"Internal server error"}}""".toResponseBody("application/json".toMediaType())
            )
        )
        val fakeAuthRepo = FakeAuthRepository(changePasswordResult = Result.failure(http500Exception))
        var sessionExpiredCalled = false
        var successCalled = false
        val viewModel = SettingsViewModel(
            plannerRepository = FakePlannerRepository(),
            authRepository = fakeAuthRepo,
            onSessionExpired = { sessionExpiredCalled = true },
            scope = CoroutineScope(testDispatcher)
        )

        viewModel.changePassword(
            currentPassword = "CurrentPassword12345!",
            newPassword = "BrandNewValidPassword12345!",
            confirmPassword = "BrandNewValidPassword12345!",
            onSuccess = { successCalled = true }
        )
        advanceUntilIdle()

        assertEquals(1, fakeAuthRepo.changePasswordCallCount)
        assertFalse(successCalled)
        assertFalse(sessionExpiredCalled)
        assertTrue(viewModel.changePasswordState.value is ChangePasswordUiState.Error)
        assertEquals(
            "Unable to change password right now. Please try again.",
            (viewModel.changePasswordState.value as ChangePasswordUiState.Error).message
        )
    }

    @Test
    fun changePassword_401SessionExpired_triggersOnSessionExpired() = runTest(testDispatcher) {
        val http401Exception = HttpException(
            Response.error<com.vikaspokala.daybyday.data.remote.dto.ChangePasswordResponseDto>(
                401,
                """{"error":{"code":"UNAUTHORIZED","message":"Authentication required"}}""".toResponseBody("application/json".toMediaType())
            )
        )
        val fakeAuthRepo = FakeAuthRepository(changePasswordResult = Result.failure(http401Exception))
        var sessionExpiredCalled = false
        var successCalled = false
        val viewModel = SettingsViewModel(
            plannerRepository = FakePlannerRepository(),
            authRepository = fakeAuthRepo,
            onSessionExpired = { sessionExpiredCalled = true },
            scope = CoroutineScope(testDispatcher)
        )

        viewModel.changePassword(
            currentPassword = "CurrentPassword12345!",
            newPassword = "BrandNewValidPassword12345!",
            confirmPassword = "BrandNewValidPassword12345!",
            onSuccess = { successCalled = true }
        )
        advanceUntilIdle()

        assertEquals(1, fakeAuthRepo.changePasswordCallCount)
        assertFalse(successCalled)
        assertTrue(sessionExpiredCalled)
        assertEquals(ChangePasswordUiState.Idle, viewModel.changePasswordState.value)
    }

    private class FakeAppNotificationManager(
        var notificationsAllowed: Boolean = true,
        var postSampleResult: Boolean = true
    ) : com.vikaspokala.daybyday.notification.AppNotificationManager {
        var createChannelCallCount = 0
        var postSampleCallCount = 0
        var openSettingsCallCount = 0

        override fun createNotificationChannel() {
            createChannelCallCount++
        }

        override fun areNotificationsAllowed(): Boolean = notificationsAllowed

        override fun postSampleNotification(): Boolean {
            postSampleCallCount++
            return postSampleResult
        }

        override fun openNotificationSettings(context: android.content.Context) {
            openSettingsCallCount++
        }
    }

    @Test
    fun initialNotificationPermission_reflectsNotificationManager() {
        val fakeManager = FakeAppNotificationManager(notificationsAllowed = true)
        val viewModel = SettingsViewModel(
            plannerRepository = FakePlannerRepository(),
            authRepository = FakeAuthRepository(),
            notificationManager = fakeManager,
            scope = CoroutineScope(testDispatcher)
        )
        assertTrue(viewModel.isNotificationAllowed.value)

        val deniedManager = FakeAppNotificationManager(notificationsAllowed = false)
        val deniedViewModel = SettingsViewModel(
            plannerRepository = FakePlannerRepository(),
            authRepository = FakeAuthRepository(),
            notificationManager = deniedManager,
            scope = CoroutineScope(testDispatcher)
        )
        assertFalse(deniedViewModel.isNotificationAllowed.value)
    }

    @Test
    fun refreshNotificationPermission_updatesStateFlow() {
        val fakeManager = FakeAppNotificationManager(notificationsAllowed = false)
        val viewModel = SettingsViewModel(
            plannerRepository = FakePlannerRepository(),
            authRepository = FakeAuthRepository(),
            notificationManager = fakeManager,
            scope = CoroutineScope(testDispatcher)
        )
        assertFalse(viewModel.isNotificationAllowed.value)

        fakeManager.notificationsAllowed = true
        viewModel.refreshNotificationPermission()
        assertTrue(viewModel.isNotificationAllowed.value)
    }

    @Test
    fun sendTestNotification_whenAllowedAndSuccess_emitsSent() {
        val fakeManager = FakeAppNotificationManager(notificationsAllowed = true, postSampleResult = true)
        val viewModel = SettingsViewModel(
            plannerRepository = FakePlannerRepository(),
            authRepository = FakeAuthRepository(),
            notificationManager = fakeManager,
            scope = CoroutineScope(testDispatcher)
        )

        var result: TestNotificationResult? = null
        viewModel.sendTestNotification { result = it }

        assertEquals(1, fakeManager.postSampleCallCount)
        assertEquals(TestNotificationResult.Sent, result)
        assertTrue(viewModel.isNotificationAllowed.value)
    }

    @Test
    fun sendTestNotification_whenAllowedAndPostingFails_emitsPermissionDenied() {
        val fakeManager = FakeAppNotificationManager(notificationsAllowed = true, postSampleResult = false)
        val viewModel = SettingsViewModel(
            plannerRepository = FakePlannerRepository(),
            authRepository = FakeAuthRepository(),
            notificationManager = fakeManager,
            scope = CoroutineScope(testDispatcher)
        )

        var result: TestNotificationResult? = null
        viewModel.sendTestNotification { result = it }

        assertEquals(1, fakeManager.postSampleCallCount)
        assertEquals(TestNotificationResult.PermissionDenied, result)
        assertFalse(viewModel.isNotificationAllowed.value)
    }

    @Test
    fun sendTestNotification_whenDisallowed_emitsPermissionDeniedWithoutPosting() {
        val fakeManager = FakeAppNotificationManager(notificationsAllowed = false)
        val viewModel = SettingsViewModel(
            plannerRepository = FakePlannerRepository(),
            authRepository = FakeAuthRepository(),
            notificationManager = fakeManager,
            scope = CoroutineScope(testDispatcher)
        )

        var result: TestNotificationResult? = null
        viewModel.sendTestNotification { result = it }

        assertEquals(0, fakeManager.postSampleCallCount)
        assertEquals(TestNotificationResult.PermissionDenied, result)
        assertFalse(viewModel.isNotificationAllowed.value)
    }
}
