package com.vikaspokala.daybyday.ui.screens.auth

import com.vikaspokala.daybyday.data.remote.dto.AuthUserDto
import com.vikaspokala.daybyday.data.repository.AuthRepository
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private open class FakeAuthRepository(
        var storedToken: String? = null,
        var storedCacheOwner: String? = null,
        var loginResult: Result<AuthUserDto>? = null,
        var verifyResult: ((String) -> Result<AuthUserDto>)? = null,
        var logoutResult: Result<Unit> = Result.success(Unit)
    ) : AuthRepository() {

        override suspend fun getStoredToken(): String? = storedToken

        override suspend fun clearSession() {
            storedToken = null
        }

        override suspend fun getCacheOwner(): String? = storedCacheOwner

        override suspend fun saveCacheOwner(userId: String) {
            storedCacheOwner = userId
        }

        override suspend fun clearCacheOwner() {
            storedCacheOwner = null
        }

        var logoutCallCount = 0
        var lastLogoutToken: String? = null
        var loginCallCount = 0
        var verifyCallCount = 0

        override suspend fun logout(token: String?) {
            logoutCallCount++
            lastLogoutToken = token
            logoutResult.getOrThrow()
        }

        override suspend fun login(email: String, password: String): AuthUserDto {
            loginCallCount++
            val result = loginResult ?: error("loginResult not configured")
            return result.getOrThrow().also {
                if (storedToken == null) {
                    storedToken = "saved_login_token"
                }
            }
        }

        override suspend fun verifySession(token: String): AuthUserDto {
            verifyCallCount++
            val verify = verifyResult ?: error("verifyResult not configured")
            return verify(token).getOrThrow()
        }
    }

    private val testUser = AuthUserDto(
        id = "11111111-2222-3333-4444-555555555555",
        email = "test@example.com",
        firstName = "Test",
        lastName = "User",
        nickname = "Tester"
    )

    private fun createHttpException(statusCode: Int): HttpException {
        return HttpException(Response.error<Any>(statusCode, "".toResponseBody(null)))
    }

    @Test
    fun startup_noToken_transitionsToSignedOut() = runTest(testDispatcher) {
        val repo = FakeAuthRepository(storedToken = null)
        val viewModel = AuthViewModel(repo)

        advanceUntilIdle()

        assertEquals(AuthUiState.SignedOut(), viewModel.uiState.value)
    }

    @Test
    fun startup_validToken_transitionsToAuthenticated() = runTest(testDispatcher) {
        val repo = FakeAuthRepository(
            storedToken = "valid_token",
            verifyResult = { Result.success(testUser) }
        )
        val viewModel = AuthViewModel(repo)

        advanceUntilIdle()

        assertEquals(AuthUiState.Authenticated(testUser), viewModel.uiState.value)
    }

    @Test
    fun startup_invalidToken401_clearsTokenAndTransitionsToSignedOut() = runTest(testDispatcher) {
        val repo = FakeAuthRepository(
            storedToken = "invalid_token",
            verifyResult = { Result.failure(createHttpException(401)) }
        )
        val viewModel = AuthViewModel(repo)

        advanceUntilIdle()

        assertNull(repo.storedToken)
        assertEquals(AuthUiState.SignedOut(), viewModel.uiState.value)
    }

    @Test
    fun startup_networkFailure_preservesTokenAndTransitionsToConnectionError() = runTest(testDispatcher) {
        val repo = FakeAuthRepository(
            storedToken = "valid_token",
            verifyResult = { Result.failure(IOException("Server unavailable")) }
        )
        val viewModel = AuthViewModel(repo)

        advanceUntilIdle()

        assertEquals("valid_token", repo.storedToken)
        assertEquals(AuthUiState.ConnectionError(isRetrying = false), viewModel.uiState.value)
    }

    @Test
    fun retry_fromConnectionError_success_transitionsToAuthenticated() = runTest(testDispatcher) {
        var verifyAttempts = 0
        val repo = FakeAuthRepository(
            storedToken = "valid_token",
            verifyResult = {
                verifyAttempts++
                if (verifyAttempts == 1) {
                    Result.failure(IOException("Network error"))
                } else {
                    Result.success(testUser)
                }
            }
        )
        val viewModel = AuthViewModel(repo)
        advanceUntilIdle()

        assertEquals(AuthUiState.ConnectionError(isRetrying = false), viewModel.uiState.value)

        viewModel.retrySessionVerification()
        advanceUntilIdle()

        assertEquals(AuthUiState.Authenticated(testUser), viewModel.uiState.value)
        assertEquals(2, verifyAttempts)
    }

    @Test
    fun signIn_emptyEmailOrPassword_showsValidationError() = runTest(testDispatcher) {
        val repo = FakeAuthRepository()
        val viewModel = AuthViewModel(repo)
        advanceUntilIdle()

        viewModel.signIn("", "password123")
        val state1 = viewModel.uiState.value
        assertTrue(state1 is AuthUiState.SignedOut)
        assertEquals("Please enter both email and password.", (state1 as AuthUiState.SignedOut).error)

        viewModel.signIn("test@example.com", "")
        val state2 = viewModel.uiState.value
        assertTrue(state2 is AuthUiState.SignedOut)
        assertEquals("Please enter both email and password.", (state2 as AuthUiState.SignedOut).error)
    }

    @Test
    fun signIn_success_savesTokenAndTransitionsToAuthenticated() = runTest(testDispatcher) {
        val repo = FakeAuthRepository(
            storedToken = null,
            loginResult = Result.success(testUser)
        )
        val viewModel = AuthViewModel(repo)
        advanceUntilIdle()

        viewModel.signIn("test@example.com", "password123")
        advanceUntilIdle()

        assertEquals("saved_login_token", repo.storedToken)
        assertEquals(AuthUiState.Authenticated(testUser), viewModel.uiState.value)
        assertEquals(1, repo.loginCallCount)
        assertEquals(0, repo.verifyCallCount) // Confirm no redundant /auth/me call after login!
    }

    @Test
    fun signIn_invalidCredentials401_clearsTokenAndShowsErrorMessage() = runTest(testDispatcher) {
        val repo = FakeAuthRepository(
            storedToken = null,
            loginResult = Result.failure(createHttpException(401))
        )
        val viewModel = AuthViewModel(repo)
        advanceUntilIdle()

        viewModel.signIn("test@example.com", "wrong_password")
        advanceUntilIdle()

        assertNull(repo.storedToken)
        val state = viewModel.uiState.value
        assertTrue(state is AuthUiState.SignedOut)
        assertEquals("Invalid email or password.", (state as AuthUiState.SignedOut).error)
    }

    @Test
    fun signIn_networkFailure_doesNotAuthenticateAndShowsConnectionError() = runTest(testDispatcher) {
        val repo = FakeAuthRepository(
            storedToken = null,
            loginResult = Result.failure(IOException("Connection refused"))
        )
        val viewModel = AuthViewModel(repo)
        advanceUntilIdle()

        viewModel.signIn("test@example.com", "password123")
        advanceUntilIdle()

        assertNull(repo.storedToken)
        val state = viewModel.uiState.value
        assertTrue(state is AuthUiState.SignedOut)
        assertEquals("Unable to connect to server. Check your connection and try again.", (state as AuthUiState.SignedOut).error)
    }

    @Test
    fun logout_remoteFails_stillPerformsLocalLogoutAndShowsSignedOut() = runTest(testDispatcher) {
        val repo = FakeAuthRepository(
            storedToken = "valid_token",
            verifyResult = { Result.success(testUser) },
            logoutResult = Result.failure(IOException("Offline"))
        )
        val viewModel = AuthViewModel(repo)
        advanceUntilIdle()

        assertEquals(AuthUiState.Authenticated(testUser), viewModel.uiState.value)

        viewModel.logout()
        advanceUntilIdle()

        assertNull(repo.storedToken)
        assertEquals(AuthUiState.SignedOut(), viewModel.uiState.value)
        assertEquals(1, repo.logoutCallCount)
    }

    @Test
    fun logout_remote401_stillPerformsLocalLogoutAndShowsSignedOut() = runTest(testDispatcher) {
        val repo = FakeAuthRepository(
            storedToken = "valid_token",
            verifyResult = { Result.success(testUser) },
            logoutResult = Result.failure(createHttpException(401))
        )
        val viewModel = AuthViewModel(repo)
        advanceUntilIdle()

        assertEquals(AuthUiState.Authenticated(testUser), viewModel.uiState.value)

        viewModel.logout()
        advanceUntilIdle()

        assertNull(repo.storedToken)
        assertEquals(AuthUiState.SignedOut(), viewModel.uiState.value)
        assertEquals(1, repo.logoutCallCount)
    }

    @Test
    fun handleSessionExpired_clearsTokenAndTransitionsToSignedOut() = runTest(testDispatcher) {
        val repo = FakeAuthRepository(
            storedToken = "valid_token",
            verifyResult = { Result.success(testUser) }
        )
        val viewModel = AuthViewModel(repo)
        advanceUntilIdle()

        assertEquals(AuthUiState.Authenticated(testUser), viewModel.uiState.value)

        viewModel.handleSessionExpired()
        advanceUntilIdle()

        assertNull(repo.storedToken)
        assertEquals(AuthUiState.SignedOut(), viewModel.uiState.value)
    }

    @Test
    fun handlePasswordChanged_clearsTokenAndTransitionsToSignedOut() = runTest(testDispatcher) {
        val repo = FakeAuthRepository(
            storedToken = "valid_token",
            verifyResult = { Result.success(testUser) }
        )
        val viewModel = AuthViewModel(repo)
        advanceUntilIdle()

        assertEquals(AuthUiState.Authenticated(testUser), viewModel.uiState.value)

        viewModel.handlePasswordChanged()
        advanceUntilIdle()

        assertNull(repo.storedToken)
        assertEquals(AuthUiState.SignedOut(), viewModel.uiState.value)
    }

    @Test
    fun updateUser_updatesAuthenticatedUserState() = runTest(testDispatcher) {
        val repo = FakeAuthRepository(
            storedToken = "valid_token",
            verifyResult = { Result.success(testUser) }
        )
        val viewModel = AuthViewModel(repo)
        advanceUntilIdle()

        val updatedUser = testUser.copy(firstName = "NewFirstName")
        viewModel.updateUser(updatedUser)

        assertEquals(AuthUiState.Authenticated(updatedUser), viewModel.uiState.value)
    }

    @Test
    fun retry_invalidToken401_clearsTokenAndTransitionsToSignedOut() = runTest(testDispatcher) {
        var verifyCount = 0
        val repo = FakeAuthRepository(
            storedToken = "token_that_will_expire",
            verifyResult = {
                verifyCount++
                if (verifyCount == 1) Result.failure(IOException("temporary error"))
                else Result.failure(createHttpException(401))
            }
        )
        val viewModel = AuthViewModel(repo)
        advanceUntilIdle()

        assertEquals(AuthUiState.ConnectionError(isRetrying = false), viewModel.uiState.value)

        viewModel.retrySessionVerification()
        advanceUntilIdle()

        assertNull(repo.storedToken)
        assertEquals(AuthUiState.SignedOut(), viewModel.uiState.value)
        assertEquals(2, verifyCount)
    }

    @Test
    fun login_matchingOwner_preservesRoomCache() = runTest(testDispatcher) {
        val repo = FakeAuthRepository(
            storedCacheOwner = testUser.id,
            loginResult = Result.success(testUser)
        )
        val database = com.vikaspokala.daybyday.ui.FakePlannerDatabase().apply {
            hasAnyDataResult = true
        }

        val viewModel = AuthViewModel(repo, database)
        advanceUntilIdle()

        viewModel.signIn("test@example.com", "Password12345!")
        advanceUntilIdle()

        assertEquals(0, database.clearPlannerDataCallCount)
        assertEquals(testUser.id, repo.storedCacheOwner)
        assertEquals(AuthUiState.Authenticated(testUser), viewModel.uiState.value)
    }

    @Test
    fun login_differentOwner_clearsRoomCacheAndStoresNewOwner() = runTest(testDispatcher) {
        val userB = testUser.copy(id = "22222222-3333-4444-5555-666666666666", email = "userb@example.com")
        val repo = FakeAuthRepository(
            storedCacheOwner = "11111111-2222-3333-4444-555555555555", // User A's ID
            loginResult = Result.success(userB)
        )
        val database = com.vikaspokala.daybyday.ui.FakePlannerDatabase().apply {
            hasAnyDataResult = true
        }

        val viewModel = AuthViewModel(repo, database)
        advanceUntilIdle()

        viewModel.signIn("userb@example.com", "Password12345!")
        advanceUntilIdle()

        assertEquals(1, database.clearPlannerDataCallCount)
        assertEquals(userB.id, repo.storedCacheOwner)
        assertEquals(AuthUiState.Authenticated(userB), viewModel.uiState.value)
    }

    @Test
    fun initialSession_unknownOwner_withExistingCachedData_clearsCacheAndStoresNewOwner() = runTest(testDispatcher) {
        val repo = FakeAuthRepository(
            storedToken = "valid_token",
            storedCacheOwner = null,
            verifyResult = { Result.success(testUser) }
        )
        val database = com.vikaspokala.daybyday.ui.FakePlannerDatabase().apply {
            hasAnyDataResult = true
        }

        val viewModel = AuthViewModel(repo, database)
        advanceUntilIdle()

        assertEquals(1, database.clearPlannerDataCallCount)
        assertEquals(testUser.id, repo.storedCacheOwner)
        assertEquals(AuthUiState.Authenticated(testUser), viewModel.uiState.value)
    }

    @Test
    fun initialSession_unknownOwner_withEmptyCache_storesOwnerWithoutClearing() = runTest(testDispatcher) {
        val repo = FakeAuthRepository(
            storedToken = "valid_token",
            storedCacheOwner = null,
            verifyResult = { Result.success(testUser) }
        )
        val database = com.vikaspokala.daybyday.ui.FakePlannerDatabase().apply {
            hasAnyDataResult = false
        }

        val viewModel = AuthViewModel(repo, database)
        advanceUntilIdle()

        assertEquals(0, database.clearPlannerDataCallCount)
        assertEquals(testUser.id, repo.storedCacheOwner)
        assertEquals(AuthUiState.Authenticated(testUser), viewModel.uiState.value)
    }

    @Test
    fun passwordChangeSignout_preservesRoomCacheAndCacheOwner_andSubsequentSignInPreservesCache() = runTest(testDispatcher) {
        val repo = FakeAuthRepository(
            storedToken = "valid_token",
            storedCacheOwner = testUser.id,
            loginResult = Result.success(testUser),
            verifyResult = { Result.success(testUser) }
        )
        val database = com.vikaspokala.daybyday.ui.FakePlannerDatabase().apply {
            hasAnyDataResult = true
        }

        val viewModel = AuthViewModel(repo, database)
        advanceUntilIdle()

        assertEquals(AuthUiState.Authenticated(testUser), viewModel.uiState.value)
        assertEquals(0, database.clearPlannerDataCallCount)

        // Password change triggers sign out
        viewModel.handlePasswordChanged()
        advanceUntilIdle()

        assertEquals(null, repo.storedToken)
        assertEquals(testUser.id, repo.storedCacheOwner) // Cache owner preserved
        assertEquals(AuthUiState.SignedOut(), viewModel.uiState.value)
        assertEquals(0, database.clearPlannerDataCallCount) // Cache not cleared

        // Same user signs in again
        viewModel.signIn("test@example.com", "NewPassword12345!")
        advanceUntilIdle()

        assertEquals(0, database.clearPlannerDataCallCount) // Cache preserved!
        assertEquals(testUser.id, repo.storedCacheOwner)
        assertEquals(AuthUiState.Authenticated(testUser), viewModel.uiState.value)
    }

    @Test
    fun authentication_isNotExposedAsAuthenticatedUntilOwnershipHandlingCompletes() = runTest(testDispatcher) {
        val userB = testUser.copy(id = "22222222-3333-4444-5555-666666666666", email = "userb@example.com")
        val repo = FakeAuthRepository(
            storedCacheOwner = "11111111-2222-3333-4444-555555555555", // User A's ID
            loginResult = Result.success(userB)
        )
        var capturedStateDuringClear: AuthUiState? = null
        val database = object : com.vikaspokala.daybyday.ui.FakePlannerDatabase() {
            var viewModelRef: AuthViewModel? = null
            override suspend fun clearPlannerData() {
                super.clearPlannerData()
                capturedStateDuringClear = viewModelRef?.uiState?.value
            }
        }
        database.hasAnyDataResult = true

        val viewModel = AuthViewModel(repo, database)
        database.viewModelRef = viewModel
        advanceUntilIdle()

        viewModel.signIn("userb@example.com", "Password12345!")
        advanceUntilIdle()

        assertEquals(AuthUiState.SignedOut(isSubmitting = true), capturedStateDuringClear)
        assertEquals(1, database.clearPlannerDataCallCount)
        assertEquals(AuthUiState.Authenticated(userB), viewModel.uiState.value)
    }

    @Test
    fun logout_success_callsBackendLogout_clearsSession_clearsRoom_clearsCacheOwner_andTransitionsToSignedOut() = runTest(testDispatcher) {
        val repo = FakeAuthRepository(
            storedToken = "valid_token_123",
            storedCacheOwner = testUser.id,
            verifyResult = { Result.success(testUser) }
        )
        val database = com.vikaspokala.daybyday.ui.FakePlannerDatabase().apply {
            hasAnyDataResult = true
        }

        val viewModel = AuthViewModel(repo, database)
        advanceUntilIdle()

        assertEquals(AuthUiState.Authenticated(testUser), viewModel.uiState.value)
        assertEquals(0, database.clearPlannerDataCallCount)
        assertEquals(0, repo.logoutCallCount)

        viewModel.logout()
        advanceUntilIdle()

        assertEquals(1, repo.logoutCallCount)
        assertEquals("valid_token_123", repo.lastLogoutToken)
        assertEquals(null, repo.storedToken)
        assertEquals(null, repo.storedCacheOwner)
        assertEquals(1, database.clearPlannerDataCallCount)
        assertEquals(AuthUiState.SignedOut(), viewModel.uiState.value)
    }

    @Test
    fun logout_networkFailure_stillPerformsCompleteLocalLogout() = runTest(testDispatcher) {
        val repo = FakeAuthRepository(
            storedToken = "valid_token_123",
            storedCacheOwner = testUser.id,
            verifyResult = { Result.success(testUser) },
            logoutResult = Result.failure(IOException("No network connectivity"))
        )
        val database = com.vikaspokala.daybyday.ui.FakePlannerDatabase().apply {
            hasAnyDataResult = true
        }

        val viewModel = AuthViewModel(repo, database)
        advanceUntilIdle()

        assertEquals(AuthUiState.Authenticated(testUser), viewModel.uiState.value)

        viewModel.logout()
        advanceUntilIdle()

        assertEquals(1, repo.logoutCallCount)
        assertEquals("valid_token_123", repo.lastLogoutToken)
        assertEquals(null, repo.storedToken)
        assertEquals(null, repo.storedCacheOwner)
        assertEquals(1, database.clearPlannerDataCallCount)
        assertEquals(AuthUiState.SignedOut(), viewModel.uiState.value)
    }

    @Test
    fun logout_http500Failure_stillPerformsCompleteLocalLogout() = runTest(testDispatcher) {
        val http500Exception = createHttpException(500)
        val repo = FakeAuthRepository(
            storedToken = "valid_token_123",
            storedCacheOwner = testUser.id,
            verifyResult = { Result.success(testUser) },
            logoutResult = Result.failure(http500Exception)
        )
        val database = com.vikaspokala.daybyday.ui.FakePlannerDatabase().apply {
            hasAnyDataResult = true
        }

        val viewModel = AuthViewModel(repo, database)
        advanceUntilIdle()

        assertEquals(AuthUiState.Authenticated(testUser), viewModel.uiState.value)

        viewModel.logout()
        advanceUntilIdle()

        assertEquals(1, repo.logoutCallCount)
        assertEquals(null, repo.storedToken)
        assertEquals(null, repo.storedCacheOwner)
        assertEquals(1, database.clearPlannerDataCallCount)
        assertEquals(AuthUiState.SignedOut(), viewModel.uiState.value)
    }

    @Test
    fun logout_http401Failure_stillPerformsCompleteLocalLogout() = runTest(testDispatcher) {
        val http401Exception = createHttpException(401)
        val repo = FakeAuthRepository(
            storedToken = "already_expired_token",
            storedCacheOwner = testUser.id,
            verifyResult = { Result.success(testUser) },
            logoutResult = Result.failure(http401Exception)
        )
        val database = com.vikaspokala.daybyday.ui.FakePlannerDatabase().apply {
            hasAnyDataResult = true
        }

        val viewModel = AuthViewModel(repo, database)
        advanceUntilIdle()

        viewModel.logout()
        advanceUntilIdle()

        assertEquals(1, repo.logoutCallCount)
        assertEquals(null, repo.storedToken)
        assertEquals(null, repo.storedCacheOwner)
        assertEquals(1, database.clearPlannerDataCallCount)
        assertEquals(AuthUiState.SignedOut(), viewModel.uiState.value)
    }

    @Test
    fun logout_performsLocalLogoutBeforeRemoteCallCompletes_usingCapturedToken() = runTest(testDispatcher) {
        var stateDuringRemoteLogout: AuthUiState? = null
        var tokenInRepoDuringRemoteLogout: String? = "uninitialized"

        val repo = object : FakeAuthRepository(
            storedToken = "captured_token_xyz",
            storedCacheOwner = testUser.id,
            verifyResult = { Result.success(testUser) }
        ) {
            var viewModelRef: AuthViewModel? = null
            override suspend fun logout(token: String?) {
                super.logout(token)
                stateDuringRemoteLogout = viewModelRef?.uiState?.value
                tokenInRepoDuringRemoteLogout = storedToken
            }
        }

        val database = com.vikaspokala.daybyday.ui.FakePlannerDatabase().apply {
            hasAnyDataResult = true
        }

        val viewModel = AuthViewModel(repo, database)
        repo.viewModelRef = viewModel
        advanceUntilIdle()

        assertEquals(AuthUiState.Authenticated(testUser), viewModel.uiState.value)

        viewModel.logout()
        advanceUntilIdle()

        assertEquals("captured_token_xyz", repo.lastLogoutToken)
        assertEquals(AuthUiState.SignedOut(), stateDuringRemoteLogout)
        assertEquals(null, tokenInRepoDuringRemoteLogout)
        assertEquals(AuthUiState.SignedOut(), viewModel.uiState.value)
    }

    @Test
    fun logout_whenRoomClearingThrowsException_stillClearsSessionAndSetsSignedOut() = runTest(testDispatcher) {
        val repo = FakeAuthRepository(
            storedToken = "valid_token_123",
            storedCacheOwner = testUser.id,
            verifyResult = { Result.success(testUser) }
        )
        val database = object : com.vikaspokala.daybyday.ui.FakePlannerDatabase() {
            override suspend fun clearPlannerData() {
                throw RuntimeException("Simulated SQLite disk error")
            }
        }
        database.hasAnyDataResult = true

        val viewModel = AuthViewModel(repo, database)
        advanceUntilIdle()

        assertEquals(AuthUiState.Authenticated(testUser), viewModel.uiState.value)

        viewModel.logout()
        advanceUntilIdle()

        assertEquals(null, repo.storedToken)
        assertEquals(null, repo.storedCacheOwner)
        assertEquals(AuthUiState.SignedOut(), viewModel.uiState.value)
    }

    private class RecordingReminderScheduler : com.vikaspokala.daybyday.notification.TaskReminderScheduler {
        var cancelAllCallCount = 0
        var rescheduleAllCallCount = 0

        override fun canScheduleExactAlarms(): Boolean = true
        override fun openExactAlarmSettings(context: android.content.Context) {}
        override fun calculateTriggerEpochMillis(
            startDate: String,
            scheduledTime: String,
            reminderMinutesBefore: Int,
            zoneId: java.time.ZoneId
        ): Long? = null
        override fun scheduleOnceReminder(
            taskId: String,
            title: String,
            startDate: String,
            scheduledTime: String?,
            reminderMinutesBefore: Int?
        ): com.vikaspokala.daybyday.notification.ReminderScheduleResult = com.vikaspokala.daybyday.notification.ReminderScheduleResult.Scheduled
        override suspend fun scheduleTaskReminder(taskId: String): com.vikaspokala.daybyday.notification.ReminderScheduleResult = com.vikaspokala.daybyday.notification.ReminderScheduleResult.Scheduled
        override fun cancelReminder(taskId: String) {}
        override suspend fun cancelAllReminders() {
            cancelAllCallCount++
        }
        override suspend fun rescheduleAllFromDatabase() {
            rescheduleAllCallCount++
        }
    }

    @Test
    fun logout_cancelsAllScheduledReminders() = runTest(testDispatcher) {
        val repo = FakeAuthRepository(
            storedToken = "valid_token",
            storedCacheOwner = testUser.id,
            verifyResult = { Result.success(testUser) }
        )
        val database = com.vikaspokala.daybyday.ui.FakePlannerDatabase()
        val scheduler = RecordingReminderScheduler()
        val viewModel = AuthViewModel(repo, database, scheduler)
        advanceUntilIdle()

        viewModel.logout()
        advanceUntilIdle()

        assertEquals(1, scheduler.cancelAllCallCount)
    }

    @Test
    fun handleSessionExpired_cancelsAllScheduledReminders() = runTest(testDispatcher) {
        val repo = FakeAuthRepository(
            storedToken = "valid_token",
            storedCacheOwner = testUser.id,
            verifyResult = { Result.success(testUser) }
        )
        val database = com.vikaspokala.daybyday.ui.FakePlannerDatabase()
        val scheduler = RecordingReminderScheduler()
        val viewModel = AuthViewModel(repo, database, scheduler)
        advanceUntilIdle()

        viewModel.handleSessionExpired()
        advanceUntilIdle()

        assertEquals(1, scheduler.cancelAllCallCount)
    }

    @Test
    fun accountSwitch_cancelsPreviousOwnerReminders() = runTest(testDispatcher) {
        val differentUser = AuthUserDto(
            id = "different_user_id",
            email = "diff@example.com",
            firstName = "Diff",
            lastName = "User",
            nickname = null
        )
        val repo = FakeAuthRepository(
            storedToken = "valid_token",
            storedCacheOwner = testUser.id,
            verifyResult = { Result.success(differentUser) }
        )
        val database = com.vikaspokala.daybyday.ui.FakePlannerDatabase()
        val scheduler = RecordingReminderScheduler()
        val viewModel = AuthViewModel(repo, database, scheduler)
        advanceUntilIdle()

        assertEquals(1, scheduler.cancelAllCallCount)
        assertEquals(0, scheduler.rescheduleAllCallCount)
    }

    @Test
    fun sameOwnerReauth_reschedulesPreservedReminders() = runTest(testDispatcher) {
        val repo = FakeAuthRepository(
            storedToken = "valid_token",
            storedCacheOwner = testUser.id,
            verifyResult = { Result.success(testUser) }
        )
        val database = com.vikaspokala.daybyday.ui.FakePlannerDatabase()
        val scheduler = RecordingReminderScheduler()
        val viewModel = AuthViewModel(repo, database, scheduler)
        advanceUntilIdle()

        assertEquals(0, scheduler.cancelAllCallCount)
        assertEquals(1, scheduler.rescheduleAllCallCount)
    }

    private class RecordingPlannerRepository(
        var refreshResult: Result<Unit> = Result.success(Unit)
    ) : com.vikaspokala.daybyday.data.repository.PlannerRepository(
        plannerDatabase = com.vikaspokala.daybyday.ui.FakePlannerDatabase(),
        tokenProvider = { "test_token" },
        plannerApi = object : com.vikaspokala.daybyday.data.remote.api.PlannerApi {
            override suspend fun createTask(authorization: String, request: com.vikaspokala.daybyday.data.remote.dto.CreateTaskRequestDto) = error("unused")
            override suspend fun getRefresh(authorization: String) = error("unused")
            override suspend fun completeTask(authorization: String, taskId: String, request: com.vikaspokala.daybyday.data.remote.dto.CompleteTaskRequestDto) = error("unused")
            override suspend fun undoTask(authorization: String, taskId: String, request: com.vikaspokala.daybyday.data.remote.dto.UndoTaskRequestDto) = error("unused")
            override suspend fun updateTask(authorization: String, taskId: String, request: com.vikaspokala.daybyday.data.remote.dto.UpdateTaskRequestDto) = error("unused")
            override suspend fun updateTaskSchedule(authorization: String, taskId: String, request: com.vikaspokala.daybyday.data.remote.dto.UpdateTaskSchedulePayloadDto) = error("unused")
            override suspend fun updateTaskContent(authorization: String, taskId: String, request: com.vikaspokala.daybyday.data.remote.dto.UpdateTaskContentRequestDto) = error("unused")
            override suspend fun deleteTask(authorization: String, taskId: String) = error("unused")
        }
    ) {
        var refreshCallCount = 0
        override suspend fun refresh(): Result<Unit> {
            refreshCallCount++
            return refreshResult
        }
    }

    @Test
    fun login_success_triggersBackgroundPlannerRefresh() = runTest(testDispatcher) {
        val repo = FakeAuthRepository(
            loginResult = Result.success(testUser),
            verifyResult = { Result.success(testUser) }
        )
        val plannerRepo = RecordingPlannerRepository()
        val viewModel = AuthViewModel(
            authRepository = repo,
            plannerRepository = plannerRepo
        )
        advanceUntilIdle()

        viewModel.signIn("test@example.com", "Password12345678")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is AuthUiState.Authenticated)
        assertEquals(1, plannerRepo.refreshCallCount)
        assertEquals(testUser.id, repo.getCacheOwner())
    }

    @Test
    fun coldStart_sameOwner_exposesCacheAndTriggersBackgroundRefresh() = runTest(testDispatcher) {
        val repo = FakeAuthRepository(
            storedToken = "valid_token",
            storedCacheOwner = testUser.id,
            verifyResult = { Result.success(testUser) }
        )
        val plannerRepo = RecordingPlannerRepository()
        val viewModel = AuthViewModel(
            authRepository = repo,
            plannerRepository = plannerRepo
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is AuthUiState.Authenticated)
        assertEquals(1, plannerRepo.refreshCallCount)
    }

    @Test
    fun login_refreshFailure_preservesAuthenticatedState() = runTest(testDispatcher) {
        val repo = FakeAuthRepository(
            loginResult = Result.success(testUser),
            verifyResult = { Result.success(testUser) }
        )
        val plannerRepo = RecordingPlannerRepository(
            refreshResult = Result.failure(IOException("Server unavailable"))
        )
        val viewModel = AuthViewModel(
            authRepository = repo,
            plannerRepository = plannerRepo
        )
        advanceUntilIdle()

        viewModel.signIn("test@example.com", "Password12345678")
        advanceUntilIdle()

        // Successful auth must remain Authenticated despite non-fatal background refresh failure
        val state = viewModel.uiState.value
        assertTrue(state is AuthUiState.Authenticated)
        assertEquals(testUser, (state as AuthUiState.Authenticated).user)
        assertEquals(1, plannerRepo.refreshCallCount)
    }

    @Test
    fun accountSwitch_clearsCacheAndTriggersRefresh() = runTest(testDispatcher) {
        val differentUser = AuthUserDto(
            id = "different_user_id",
            email = "diff@example.com",
            firstName = "Diff",
            lastName = "User",
            nickname = null
        )
        val repo = FakeAuthRepository(
            storedToken = "valid_token",
            storedCacheOwner = testUser.id,
            verifyResult = { Result.success(differentUser) }
        )
        val database = com.vikaspokala.daybyday.ui.FakePlannerDatabase()
        val plannerRepo = RecordingPlannerRepository()
        val viewModel = AuthViewModel(
            authRepository = repo,
            plannerDatabase = database,
            plannerRepository = plannerRepo
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is AuthUiState.Authenticated)
        assertEquals(differentUser.id, repo.getCacheOwner())
        assertEquals(1, plannerRepo.refreshCallCount)
    }

    @Test
    fun onAppForegrounded_within5Minutes_makesNoNetworkCalls() = runTest(testDispatcher) {
        var currentTime = 1_000_000L
        val repo = FakeAuthRepository(
            storedToken = "valid_token",
            storedCacheOwner = testUser.id,
            verifyResult = { Result.success(testUser) }
        )
        val plannerRepo = RecordingPlannerRepository()
        val viewModel = AuthViewModel(
            authRepository = repo,
            plannerRepository = plannerRepo,
            clock = { currentTime }
        )
        advanceUntilIdle()

        assertEquals(1, repo.verifyCallCount)
        assertEquals(1, plannerRepo.refreshCallCount)

        // Advance 4 minutes (< 5 min threshold)
        currentTime += 4 * 60 * 1000L
        viewModel.onAppForegrounded()
        advanceUntilIdle()

        // Zero additional network calls
        assertEquals(1, repo.verifyCallCount)
        assertEquals(1, plannerRepo.refreshCallCount)
    }

    @Test
    fun onAppForegrounded_after5Minutes_triggersSync() = runTest(testDispatcher) {
        var currentTime = 1_000_000L
        val updatedUser = testUser.copy(nickname = "FreshNick")
        val repo = FakeAuthRepository(
            storedToken = "valid_token",
            storedCacheOwner = testUser.id,
            verifyResult = { Result.success(updatedUser) }
        )
        val plannerRepo = RecordingPlannerRepository()
        val viewModel = AuthViewModel(
            authRepository = repo,
            plannerRepository = plannerRepo,
            clock = { currentTime }
        )
        advanceUntilIdle()

        assertEquals(1, repo.verifyCallCount)
        assertEquals(1, plannerRepo.refreshCallCount)

        // Advance 5 minutes (>= 5 min threshold)
        currentTime += 5 * 60 * 1000L
        viewModel.onAppForegrounded()
        advanceUntilIdle()

        // Exactly one additional verify and refresh
        assertEquals(2, repo.verifyCallCount)
        assertEquals(2, plannerRepo.refreshCallCount)
        assertEquals(AuthUiState.Authenticated(updatedUser), viewModel.uiState.value)
    }

    @Test
    fun onAppForegrounded_duplicateCalls_runsOnlyOnce() = runTest(testDispatcher) {
        var currentTime = 1_000_000L
        val repo = FakeAuthRepository(
            storedToken = "valid_token",
            storedCacheOwner = testUser.id,
            verifyResult = { Result.success(testUser) }
        )
        val plannerRepo = RecordingPlannerRepository()
        val viewModel = AuthViewModel(
            authRepository = repo,
            plannerRepository = plannerRepo,
            clock = { currentTime }
        )
        advanceUntilIdle()

        assertEquals(1, plannerRepo.refreshCallCount)

        // Advance 10 minutes and trigger rapid duplicate foreground calls
        currentTime += 10 * 60 * 1000L
        viewModel.onAppForegrounded()
        viewModel.onAppForegrounded()
        advanceUntilIdle()

        // Only 1 additional refresh executed
        assertEquals(2, repo.verifyCallCount)
        assertEquals(2, plannerRepo.refreshCallCount)
    }

    @Test
    fun onAppForegrounded_signedOut_makesNoNetworkCalls() = runTest(testDispatcher) {
        var currentTime = 1_000_000L
        val repo = FakeAuthRepository(
            storedToken = null
        )
        val plannerRepo = RecordingPlannerRepository()
        val viewModel = AuthViewModel(
            authRepository = repo,
            plannerRepository = plannerRepo,
            clock = { currentTime }
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is AuthUiState.SignedOut)

        currentTime += 10 * 60 * 1000L
        viewModel.onAppForegrounded()
        advanceUntilIdle()

        assertEquals(0, repo.verifyCallCount)
        assertEquals(0, plannerRepo.refreshCallCount)
    }
}
