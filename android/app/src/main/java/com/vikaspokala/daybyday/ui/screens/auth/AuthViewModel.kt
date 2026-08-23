package com.vikaspokala.daybyday.ui.screens.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vikaspokala.daybyday.data.local.auth.SessionTokenStore
import com.vikaspokala.daybyday.data.local.planner.PlannerDatabase
import com.vikaspokala.daybyday.data.remote.dto.AuthUserDto
import com.vikaspokala.daybyday.data.repository.AuthRepository
import com.vikaspokala.daybyday.data.repository.PlannerRepository
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

import com.vikaspokala.daybyday.notification.DefaultTaskReminderScheduler
import com.vikaspokala.daybyday.notification.TaskReminderScheduler

sealed interface AuthUiState {
    data object Loading : AuthUiState
    data class SignedOut(
        val error: String? = null,
        val isSubmitting: Boolean = false
    ) : AuthUiState
    data class ConnectionError(
        val isRetrying: Boolean = false
    ) : AuthUiState
    data class Authenticated(
        val user: AuthUserDto
    ) : AuthUiState
}

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val plannerDatabase: PlannerDatabase? = null,
    private val reminderScheduler: TaskReminderScheduler? = null,
    private val plannerRepository: PlannerRepository? = null,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Loading)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private var lastSyncTimestamp: Long = 0L
    private var isSyncInProgress: Boolean = false

    init {
        checkInitialSession()
    }

    private suspend fun onAuthenticationSuccess(user: AuthUserDto) {
        val storedOwnerId = authRepository.getCacheOwner()
        val db = plannerDatabase
        val isSameOwner = storedOwnerId == user.id

        if (isSameOwner) {
            // Matching owner: keep existing Room cache and restore eligible future ONCE reminders
            reminderScheduler?.rescheduleAllFromDatabase()
            _uiState.value = AuthUiState.Authenticated(user)
            lastSyncTimestamp = clock()

            // Non-blocking background refresh for cold start
            viewModelScope.launch {
                try {
                    plannerRepository?.refresh()
                } catch (_: Exception) {
                    // Non-fatal background refresh failure
                }
            }
        } else if (storedOwnerId != null) {
            // Different owner: cancel previous owner's reminders, clear all account-specific planner Room cache and store new owner
            reminderScheduler?.cancelAllReminders()
            db?.clearPlannerData()
            authRepository.saveCacheOwner(user.id)

            // When Room was cleared / new owner, populate Room first so user doesn't see a flash of empty screens
            try {
                plannerRepository?.refresh()
            } catch (_: Exception) {
                // Auto-refresh failure must NOT invalidate authentication
            }
            lastSyncTimestamp = clock()
            _uiState.value = AuthUiState.Authenticated(user)
        } else {
            // Missing/unknown cache owner
            reminderScheduler?.cancelAllReminders()
            if (db != null && db.hasAnyData()) {
                db.clearPlannerData()
            }
            authRepository.saveCacheOwner(user.id)

            try {
                plannerRepository?.refresh()
            } catch (_: Exception) {
                // Auto-refresh failure must NOT invalidate authentication
            }
            lastSyncTimestamp = clock()
            _uiState.value = AuthUiState.Authenticated(user)
        }
    }

    fun onAppForegrounded() {
        val currentState = _uiState.value
        if (currentState !is AuthUiState.Authenticated) {
            return
        }

        val now = clock()
        if (lastSyncTimestamp > 0L && (now - lastSyncTimestamp) < STALE_SYNC_INTERVAL_MILLIS) {
            return
        }

        if (isSyncInProgress) {
            return
        }
        isSyncInProgress = true

        viewModelScope.launch {
            try {
                val token = authRepository.getStoredToken()
                if (token == null) {
                    handleSessionExpired()
                    return@launch
                }

                // 1. Re-verify session and fetch updated user profile
                try {
                    val freshUser = authRepository.verifySession(token)
                    _uiState.value = AuthUiState.Authenticated(freshUser)
                } catch (e: HttpException) {
                    if (e.code() == 401) {
                        handleSessionExpired()
                        return@launch
                    }
                } catch (_: Exception) {
                    // Non-fatal network error on foreground check
                }

                // 2. Refresh planner snapshot
                try {
                    val refreshResult = plannerRepository?.refresh()
                    if (refreshResult != null && refreshResult.isFailure) {
                        val error = refreshResult.exceptionOrNull()
                        if (error is HttpException && error.code() == 401) {
                            handleSessionExpired()
                            return@launch
                        }
                    }
                } catch (e: HttpException) {
                    if (e.code() == 401) {
                        handleSessionExpired()
                        return@launch
                    }
                } catch (_: Exception) {
                    // Non-fatal
                }

                lastSyncTimestamp = clock()
            } finally {
                isSyncInProgress = false
            }
        }
    }

    fun checkInitialSession() {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val token = authRepository.getStoredToken()
            if (token == null) {
                _uiState.value = AuthUiState.SignedOut()
                return@launch
            }

            try {
                val user = authRepository.verifySession(token)
                onAuthenticationSuccess(user)
            } catch (e: HttpException) {
                if (e.code() == 401) {
                    authRepository.clearSession()
                    _uiState.value = AuthUiState.SignedOut()
                } else {
                    // Other server errors: keep stored token, block planner access
                    _uiState.value = AuthUiState.ConnectionError(isRetrying = false)
                }
            } catch (e: IOException) {
                // Network or server unavailable: keep stored token, block planner access
                _uiState.value = AuthUiState.ConnectionError(isRetrying = false)
            } catch (e: Exception) {
                _uiState.value = AuthUiState.ConnectionError(isRetrying = false)
            }
        }
    }

    fun signIn(email: String, password: String) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty() || password.isEmpty()) {
            _uiState.value = AuthUiState.SignedOut(
                error = "Please enter both email and password.",
                isSubmitting = false
            )
            return
        }

        val currentState = _uiState.value
        if (currentState is AuthUiState.SignedOut && currentState.isSubmitting) {
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.SignedOut(isSubmitting = true)
            try {
                val user = authRepository.login(trimmedEmail, password)
                onAuthenticationSuccess(user)
            } catch (e: HttpException) {
                if (e.code() == 401 || e.code() == 400) {
                    authRepository.clearSession()
                    _uiState.value = AuthUiState.SignedOut(
                        error = "Invalid email or password.",
                        isSubmitting = false
                    )
                } else {
                    _uiState.value = AuthUiState.SignedOut(
                        error = "Unable to connect to server. Check your connection and try again.",
                        isSubmitting = false
                    )
                }
            } catch (e: IOException) {
                _uiState.value = AuthUiState.SignedOut(
                    error = "Unable to connect to server. Check your connection and try again.",
                    isSubmitting = false
                )
            } catch (e: Exception) {
                _uiState.value = AuthUiState.SignedOut(
                    error = "An unexpected error occurred. Please try again.",
                    isSubmitting = false
                )
            }
        }
    }

    fun retrySessionVerification() {
        val currentState = _uiState.value
        if (currentState is AuthUiState.ConnectionError && currentState.isRetrying) {
            return
        }

        viewModelScope.launch {
            val token = authRepository.getStoredToken()
            if (token == null) {
                _uiState.value = AuthUiState.SignedOut()
                return@launch
            }

            _uiState.value = AuthUiState.ConnectionError(isRetrying = true)
            try {
                val user = authRepository.verifySession(token)
                onAuthenticationSuccess(user)
            } catch (e: HttpException) {
                if (e.code() == 401) {
                    authRepository.clearSession()
                    _uiState.value = AuthUiState.SignedOut()
                } else {
                    _uiState.value = AuthUiState.ConnectionError(isRetrying = false)
                }
            } catch (e: IOException) {
                _uiState.value = AuthUiState.ConnectionError(isRetrying = false)
            } catch (e: Exception) {
                _uiState.value = AuthUiState.ConnectionError(isRetrying = false)
            }
        }
    }

    fun updateUser(user: AuthUserDto) {
        _uiState.value = AuthUiState.Authenticated(user)
    }

    fun handleSessionExpired() {
        viewModelScope.launch {
            lastSyncTimestamp = 0L
            isSyncInProgress = false
            reminderScheduler?.cancelAllReminders()
            authRepository.clearSession()
            _uiState.value = AuthUiState.SignedOut()
        }
    }

    fun handlePasswordChanged() {
        viewModelScope.launch {
            lastSyncTimestamp = 0L
            isSyncInProgress = false
            reminderScheduler?.cancelAllReminders()
            authRepository.clearSession()
            _uiState.value = AuthUiState.SignedOut()
        }
    }

    fun logout() {
        viewModelScope.launch {
            val token = authRepository.getStoredToken()
            lastSyncTimestamp = 0L
            isSyncInProgress = false

            // 1. Immediately perform local logout and cancel reminders
            reminderScheduler?.cancelAllReminders()
            authRepository.clearSession()
            try {
                plannerDatabase?.clearPlannerData()
            } catch (_: Exception) {
                // Room clearing errors must not block local logout or UI transition
            }
            try {
                authRepository.clearCacheOwner()
            } catch (_: Exception) {
                // Cache owner errors must not block local logout or UI transition
            }
            _uiState.value = AuthUiState.SignedOut()

            // 2. Best-effort remote POST /auth/logout with captured token
            if (!token.isNullOrBlank()) {
                try {
                    authRepository.logout(token)
                } catch (_: Exception) {
                    // Offline / network failure / server 5xx or 401 ignored as local logout is already complete
                }
            }
        }
    }

    companion object {
        const val STALE_SYNC_INTERVAL_MILLIS = 5 * 60 * 1000L // 5 minutes
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val appContext = context.applicationContext
            val sessionTokenStore = SessionTokenStore(appContext)
            val authRepository = AuthRepository(sessionTokenStore)
            val plannerDatabase = PlannerDatabase.getInstance(appContext)
            val reminderScheduler = DefaultTaskReminderScheduler(appContext, plannerDatabase)
            val plannerRepository = PlannerRepository(
                plannerDatabase = plannerDatabase,
                sessionTokenStore = sessionTokenStore,
                reminderScheduler = reminderScheduler
            )
            return AuthViewModel(authRepository, plannerDatabase, reminderScheduler, plannerRepository) as T
        }
    }
}
