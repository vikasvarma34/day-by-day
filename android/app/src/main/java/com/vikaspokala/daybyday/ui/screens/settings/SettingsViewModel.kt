package com.vikaspokala.daybyday.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vikaspokala.daybyday.data.local.auth.SessionTokenStore
import com.vikaspokala.daybyday.data.local.planner.PlannerDatabase
import com.vikaspokala.daybyday.data.remote.NetworkClient
import com.vikaspokala.daybyday.data.remote.dto.AuthUserDto
import com.vikaspokala.daybyday.data.remote.dto.ChangePasswordRequestDto
import com.vikaspokala.daybyday.data.remote.dto.UpdateProfileRequestDto
import com.vikaspokala.daybyday.data.repository.AuthRepository
import com.vikaspokala.daybyday.data.repository.PlannerRepository
import com.vikaspokala.daybyday.ui.navigation.ProfileFieldType
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.HttpException

import com.vikaspokala.daybyday.notification.AppNotificationManager
import com.vikaspokala.daybyday.notification.DefaultAppNotificationManager
import com.vikaspokala.daybyday.notification.DefaultTaskReminderScheduler

sealed interface RefreshUiState {
    data object Idle : RefreshUiState
    data object Refreshing : RefreshUiState
    data object Error : RefreshUiState
}

sealed interface ProfileEditUiState {
    data object Idle : ProfileEditUiState
    data object Saving : ProfileEditUiState
    data class Error(val message: String) : ProfileEditUiState
}

sealed interface ChangePasswordUiState {
    data object Idle : ChangePasswordUiState
    data object Submitting : ChangePasswordUiState
    data class Error(val message: String) : ChangePasswordUiState
}

sealed interface TestNotificationResult {
    data object Sent : TestNotificationResult
    data object PermissionDenied : TestNotificationResult
}

class SettingsViewModel(
    private val plannerRepository: PlannerRepository,
    private val authRepository: AuthRepository = AuthRepository(),
    private val notificationManager: AppNotificationManager? = null,
    private val onSessionExpired: () -> Unit = {},
    scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
) : ViewModel() {

    private val coroutineScope = scope

    private val _isNotificationAllowed = MutableStateFlow(notificationManager?.areNotificationsAllowed() ?: false)
    val isNotificationAllowed: StateFlow<Boolean> = _isNotificationAllowed.asStateFlow()

    private val _refreshState = MutableStateFlow<RefreshUiState>(RefreshUiState.Idle)
    val refreshState: StateFlow<RefreshUiState> = _refreshState.asStateFlow()

    private val _profileEditState = MutableStateFlow<ProfileEditUiState>(ProfileEditUiState.Idle)
    val profileEditState: StateFlow<ProfileEditUiState> = _profileEditState.asStateFlow()

    private val _changePasswordState = MutableStateFlow<ChangePasswordUiState>(ChangePasswordUiState.Idle)
    val changePasswordState: StateFlow<ChangePasswordUiState> = _changePasswordState.asStateFlow()

    fun refreshNotificationPermission() {
        _isNotificationAllowed.value = notificationManager?.areNotificationsAllowed() ?: false
    }

    fun openNotificationSettings(context: Context) {
        notificationManager?.openNotificationSettings(context)
    }

    fun sendTestNotification(onResult: (TestNotificationResult) -> Unit = {}) {
        val manager = notificationManager
        if (manager != null && manager.areNotificationsAllowed()) {
            val sent = manager.postSampleNotification()
            if (sent) {
                _isNotificationAllowed.value = true
                onResult(TestNotificationResult.Sent)
            } else {
                _isNotificationAllowed.value = false
                onResult(TestNotificationResult.PermissionDenied)
            }
        } else {
            _isNotificationAllowed.value = false
            onResult(TestNotificationResult.PermissionDenied)
        }
    }

    fun resetProfileEditState() {
        _profileEditState.value = ProfileEditUiState.Idle
    }

    fun resetChangePasswordState() {
        _changePasswordState.value = ChangePasswordUiState.Idle
    }

    fun changePassword(
        currentPassword: String,
        newPassword: String,
        confirmPassword: String,
        onSuccess: () -> Unit
    ) {
        if (_changePasswordState.value is ChangePasswordUiState.Submitting) {
            return
        }

        if (currentPassword.isBlank()) {
            _changePasswordState.value = ChangePasswordUiState.Error("Please enter your current password.")
            return
        }
        if (newPassword.isBlank()) {
            _changePasswordState.value = ChangePasswordUiState.Error("Please enter a new password.")
            return
        }
        if (confirmPassword.isBlank()) {
            _changePasswordState.value = ChangePasswordUiState.Error("Please confirm your new password.")
            return
        }
        if (newPassword != confirmPassword) {
            _changePasswordState.value = ChangePasswordUiState.Error("New passwords do not match.")
            return
        }

        val request = ChangePasswordRequestDto(
            currentPassword = currentPassword,
            newPassword = newPassword
        )

        coroutineScope.launch {
            _changePasswordState.value = ChangePasswordUiState.Submitting
            try {
                authRepository.changePassword(request)
                _changePasswordState.value = ChangePasswordUiState.Idle
                onSuccess()
            } catch (e: HttpException) {
                if (e.code() == 401) {
                    val serverMessage = parseErrorMessage(e)
                    if (serverMessage?.contains("current password", ignoreCase = true) == true) {
                        _changePasswordState.value = ChangePasswordUiState.Error("Current password is incorrect.")
                    } else {
                        _changePasswordState.value = ChangePasswordUiState.Idle
                        onSessionExpired()
                    }
                } else if (e.code() == 400) {
                    val serverMessage = parseErrorMessage(e)
                    val displayMessage = when {
                        serverMessage?.contains("between 15 and 128", ignoreCase = true) == true ->
                            "Password must be between 15 and 128 characters."
                        serverMessage?.contains("same as current", ignoreCase = true) == true ->
                            "New password cannot be the same as current password."
                        !serverMessage.isNullOrBlank() -> serverMessage
                        else -> "Unable to change password right now. Please try again."
                    }
                    _changePasswordState.value = ChangePasswordUiState.Error(displayMessage)
                } else {
                    _changePasswordState.value = ChangePasswordUiState.Error("Unable to change password right now. Please try again.")
                }
            } catch (e: IOException) {
                _changePasswordState.value = ChangePasswordUiState.Error("Unable to change password right now. Please try again.")
            } catch (e: Exception) {
                _changePasswordState.value = ChangePasswordUiState.Error("Unable to change password right now. Please try again.")
            }
        }
    }

    fun updateProfile(
        fieldType: ProfileFieldType,
        newValue: String,
        onUserUpdated: (AuthUserDto) -> Unit,
        onSuccess: () -> Unit
    ) {
        if (_profileEditState.value is ProfileEditUiState.Saving) {
            return
        }

        val trimmed = newValue.trim()
        if (fieldType == ProfileFieldType.FIRST_NAME && trimmed.isEmpty()) {
            _profileEditState.value = ProfileEditUiState.Error("Please enter your first name.")
            return
        }
        if (fieldType == ProfileFieldType.LAST_NAME && trimmed.isEmpty()) {
            _profileEditState.value = ProfileEditUiState.Error("Please enter your last name.")
            return
        }

        val request = when (fieldType) {
            ProfileFieldType.FIRST_NAME -> UpdateProfileRequestDto.forFirstName(trimmed)
            ProfileFieldType.LAST_NAME -> UpdateProfileRequestDto.forLastName(trimmed)
            ProfileFieldType.NICKNAME -> {
                val nick = if (trimmed.isEmpty()) null else trimmed
                UpdateProfileRequestDto.forNickname(nick)
            }
        }

        coroutineScope.launch {
            _profileEditState.value = ProfileEditUiState.Saving
            try {
                val updatedUser = authRepository.updateProfile(request)
                onUserUpdated(updatedUser)
                _profileEditState.value = ProfileEditUiState.Idle
                onSuccess()
            } catch (e: HttpException) {
                if (e.code() == 401) {
                    _profileEditState.value = ProfileEditUiState.Idle
                    onSessionExpired()
                } else if (e.code() == 400) {
                    val serverMessage = parseErrorMessage(e)
                    val displayMessage = when {
                        serverMessage?.contains("firstName", ignoreCase = true) == true -> "Please enter your first name."
                        serverMessage?.contains("lastName", ignoreCase = true) == true -> "Please enter your last name."
                        !serverMessage.isNullOrBlank() -> serverMessage
                        fieldType == ProfileFieldType.FIRST_NAME -> "Please enter your first name."
                        fieldType == ProfileFieldType.LAST_NAME -> "Please enter your last name."
                        else -> "Invalid input. Please check and try again."
                    }
                    _profileEditState.value = ProfileEditUiState.Error(displayMessage)
                } else {
                    _profileEditState.value = ProfileEditUiState.Error("Unable to update profile. Please try again.")
                }
            } catch (e: IOException) {
                _profileEditState.value = ProfileEditUiState.Error("Unable to connect to server. Check your connection and try again.")
            } catch (e: Exception) {
                _profileEditState.value = ProfileEditUiState.Error("Unable to update profile. Please try again.")
            }
        }
    }

    private fun parseErrorMessage(exception: HttpException): String? {
        return try {
            val errorBody = exception.response()?.errorBody()?.string() ?: return null
            val element = NetworkClient.json.parseToJsonElement(errorBody)
            val obj = if (element is JsonObject) element else null
            val errorObj = obj?.get("error")
            if (errorObj is JsonObject) {
                val msg = errorObj["message"]
                if (msg is JsonPrimitive) msg.content else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    fun refreshPlannerData() {
        if (_refreshState.value is RefreshUiState.Refreshing) {
            return
        }

        coroutineScope.launch {
            _refreshState.value = RefreshUiState.Refreshing
            val result = plannerRepository.refresh()
            result.fold(
                onSuccess = {
                    _refreshState.value = RefreshUiState.Idle
                },
                onFailure = { exception ->
                    if (exception is HttpException && exception.code() == 401) {
                        _refreshState.value = RefreshUiState.Idle
                        onSessionExpired()
                    } else {
                        _refreshState.value = RefreshUiState.Error
                    }
                }
            )
        }
    }

    class Factory(
        private val context: Context,
        private val onSessionExpired: () -> Unit = {}
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val appContext = context.applicationContext
            val sessionTokenStore = SessionTokenStore(appContext)
            val plannerDatabase = PlannerDatabase.getInstance(appContext)
            val reminderScheduler = DefaultTaskReminderScheduler(appContext, plannerDatabase)
            val plannerRepository = PlannerRepository(plannerDatabase, sessionTokenStore, reminderScheduler = reminderScheduler)
            val authRepository = AuthRepository(sessionTokenStore)
            val notificationManager = DefaultAppNotificationManager(appContext)
            return SettingsViewModel(plannerRepository, authRepository, notificationManager, onSessionExpired) as T
        }
    }
}
