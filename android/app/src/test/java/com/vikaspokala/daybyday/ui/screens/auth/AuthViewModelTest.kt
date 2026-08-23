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

    private class FakeAuthRepository(
        var storedToken: String? = null,
        var loginResult: Result<String>? = null,
        var verifyResult: ((String) -> Result<AuthUserDto>)? = null
    ) : AuthRepository() {

        override suspend fun getStoredToken(): String? = storedToken

        override suspend fun clearSession() {
            storedToken = null
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
}
