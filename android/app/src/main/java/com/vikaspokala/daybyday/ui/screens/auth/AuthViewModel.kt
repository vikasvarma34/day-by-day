package com.vikaspokala.daybyday.ui.screens.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vikaspokala.daybyday.data.local.auth.SessionTokenStore
import com.vikaspokala.daybyday.data.remote.dto.AuthUserDto
import com.vikaspokala.daybyday.data.repository.AuthRepository
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

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
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Loading)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        checkInitialSession()
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
                _uiState.value = AuthUiState.Authenticated(user)
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
            var tokenSaved = false
            try {
                val token = authRepository.login(trimmedEmail, password)
                tokenSaved = true
                val user = authRepository.verifySession(token)
                _uiState.value = AuthUiState.Authenticated(user)
            } catch (e: HttpException) {
                if (e.code() == 401 || e.code() == 400) {
                    authRepository.clearSession()
                    _uiState.value = AuthUiState.SignedOut(
                        error = "Invalid email or password.",
                        isSubmitting = false
                    )
                } else if (tokenSaved) {
                    // Login succeeded and token was saved, but /auth/me returned 5xx/server error
                    _uiState.value = AuthUiState.ConnectionError(isRetrying = false)
                } else {
                    _uiState.value = AuthUiState.SignedOut(
                        error = "Unable to connect to server. Check your connection and try again.",
                        isSubmitting = false
                    )
                }
            } catch (e: IOException) {
                if (tokenSaved) {
                    // Login succeeded and token was saved, but /auth/me failed due to network
                    _uiState.value = AuthUiState.ConnectionError(isRetrying = false)
                } else {
                    _uiState.value = AuthUiState.SignedOut(
                        error = "Unable to connect to server. Check your connection and try again.",
                        isSubmitting = false
                    )
                }
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
                _uiState.value = AuthUiState.Authenticated(user)
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

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val sessionTokenStore = SessionTokenStore(context.applicationContext)
            val authRepository = AuthRepository(sessionTokenStore)
            return AuthViewModel(authRepository) as T
        }
    }
}
