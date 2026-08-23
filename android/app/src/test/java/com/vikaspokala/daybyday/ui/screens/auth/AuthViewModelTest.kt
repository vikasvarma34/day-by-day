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
        var loginResult: Result<String>? = null,
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

        override suspend fun logout(token: String?) {
            logoutCallCount++
            lastLogoutToken = token
            logoutResult.getOrThrow()
        }

        override suspend fun login(email: String, password: String): String {
            val result = loginResult ?: error("loginResult not configured")
            return result.getOrThrow().also { token ->
                storedToken = token
            }
        }

        override suspend fun verifySession(token: String): AuthUserDto {
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
    fun startup_storedTokenAndVerify200_transitionsToAuthenticated() = runTest(testDispatcher) {
        val repo = FakeAuthRepository(
            storedToken = "valid_token",
            verifyResult = { Result.success(testUser) }
        )
        val viewModel = AuthViewModel(repo)

        advanceUntilIdle()

        assertEquals(AuthUiState.Authenticated(testUser), viewModel.uiState.value)
    }

    @Test
    fun startup_storedTokenAndVerify401_clearsTokenAndTransitionsToSignedOut() = runTest(testDispatcher) {
        val repo = FakeAuthRepository(
            storedToken = "expired_token",
            verifyResult = { Result.failure(createHttpException(401)) }
        )
        val viewModel = AuthViewModel(repo)

        advanceUntilIdle()

        assertNull(repo.storedToken)
        assertEquals(AuthUiState.SignedOut(), viewModel.uiState.value)
    }

    @Test
    fun startup_storedTokenAndNetworkFailure_preservesTokenAndTransitionsToConnectionError() = runTest(testDispatcher) {
        val repo = FakeAuthRepository(
            storedToken = "valid_token",
            verifyResult = { Result.failure(IOException("Network unreachable")) }
        )
        val viewModel = AuthViewModel(repo)

        advanceUntilIdle()

        assertEquals("valid_token", repo.storedToken)
        assertEquals(AuthUiState.ConnectionError(isRetrying = false), viewModel.uiState.value)
    }

    @Test
    fun signIn_success_savesTokenAndTransitionsToAuthenticated() = runTest(testDispatcher) {
        val repo = FakeAuthRepository(
            storedToken = null,
            loginResult = Result.success("new_token"),
            verifyResult = { Result.success(testUser) }
        )
        val viewModel = AuthViewModel(repo)
        advanceUntilIdle()

        viewModel.signIn("test@example.com", "password123")
        advanceUntilIdle()

        assertEquals("new_token", repo.storedToken)
        assertEquals(AuthUiState.Authenticated(testUser), viewModel.uiState.value)
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
    fun startup_storedTokenAndVerify500_preservesTokenAndTransitionsToConnectionError() = runTest(testDispatcher) {
        val repo = FakeAuthRepository(
            storedToken = "valid_token",
            verifyResult = { Result.failure(createHttpException(500)) }
        )
        val viewModel = AuthViewModel(repo)

        advanceUntilIdle()

        assertEquals("valid_token", repo.storedToken)
        assertEquals(AuthUiState.ConnectionError(isRetrying = false), viewModel.uiState.value)
    }

    @Test
    fun startup_storedTokenAndVerify503_preservesTokenAndTransitionsToConnectionError() = runTest(testDispatcher) {
        val repo = FakeAuthRepository(
            storedToken = "valid_token",
            verifyResult = { Result.failure(createHttpException(503)) }
        )
        val viewModel = AuthViewModel(repo)

        advanceUntilIdle()

        assertEquals("valid_token", repo.storedToken)
        assertEquals(AuthUiState.ConnectionError(isRetrying = false), viewModel.uiState.value)
    }

    @Test
    fun signIn_serverError500_doesNotSaveTokenAndShowsConnectionError() = runTest(testDispatcher) {
        val repo = FakeAuthRepository(
            storedToken = null,
            loginResult = Result.failure(createHttpException(500))
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
    fun partialSuccess_loginSucceedsVerify500_preservesTokenAndAllowsRetry() = runTest(testDispatcher) {
        var verifyAttempts = 0
        val repo = FakeAuthRepository(
            storedToken = null,
            loginResult = Result.success("saved_session_token"),
            verifyResult = {
                verifyAttempts++
                if (verifyAttempts == 1) {
                    Result.failure(createHttpException(500))
                } else {
                    Result.success(testUser)
                }
            }
        )
        val viewModel = AuthViewModel(repo)
        advanceUntilIdle()

        // Sign in
        viewModel.signIn("test@example.com", "password123")
        advanceUntilIdle()

        // Token was saved, but verify returned 500 -> ConnectionError with preserved token
        assertEquals("saved_session_token", repo.storedToken)
        assertEquals(AuthUiState.ConnectionError(isRetrying = false), viewModel.uiState.value)

        // Retry session verification
        viewModel.retrySessionVerification()
        advanceUntilIdle()

        assertEquals(AuthUiState.Authenticated(testUser), viewModel.uiState.value)
    }

    @Test
    fun updateUser_updatesAuthenticatedUserState() = runTest(testDispatcher) {
        val repo = FakeAuthRepository(
            storedToken = "valid_token",
            verifyResult = { Result.success(testUser) }
        )
        val viewModel = AuthViewModel(repo)
        advanceUntilIdle()

        assertEquals(AuthUiState.Authenticated(testUser), viewModel.uiState.value)

        val updatedUser = testUser.copy(
            firstName = "Vikram",
            lastName = "Pokala",
            nickname = "Vik"
        )
        viewModel.updateUser(updatedUser)

        assertEquals(AuthUiState.Authenticated(updatedUser), viewModel.uiState.value)
    }

    @Test
    fun handlePasswordChanged_clearsStoredTokenAndTransitionsToSignedOutWithoutCallingVerifySession() = runTest(testDispatcher) {
        var verifyCount = 0
        val repo = FakeAuthRepository(
            storedToken = "valid_token",
            verifyResult = {
                verifyCount++
                Result.success(testUser)
            }
        )
        val viewModel = AuthViewModel(repo)
        advanceUntilIdle()

        assertEquals(AuthUiState.Authenticated(testUser), viewModel.uiState.value)
        assertEquals(1, verifyCount)

        viewModel.handlePasswordChanged()
        advanceUntilIdle()

        assertEquals(null, repo.storedToken)
        assertEquals(AuthUiState.SignedOut(), viewModel.uiState.value)
        assertEquals(1, verifyCount)
    }

    @Test
    fun login_matchingOwner_preservesRoomCache() = runTest(testDispatcher) {
        val repo = FakeAuthRepository(
            storedCacheOwner = testUser.id,
            loginResult = Result.success("valid_token"),
            verifyResult = { Result.success(testUser) }
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
            loginResult = Result.success("user_b_token"),
            verifyResult = { Result.success(userB) }
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
            loginResult = Result.success("new_valid_token"),
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
            loginResult = Result.success("user_b_token"),
            verifyResult = { Result.success(userB) }
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
}
