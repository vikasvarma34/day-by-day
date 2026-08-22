package com.vikaspokala.daybyday.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vikaspokala.daybyday.data.local.auth.SessionTokenStore
import com.vikaspokala.daybyday.data.local.planner.PlannerDatabase
import com.vikaspokala.daybyday.data.repository.PlannerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

sealed interface RefreshUiState {
    data object Idle : RefreshUiState
    data object Refreshing : RefreshUiState
    data object Error : RefreshUiState
}

class SettingsViewModel(
    private val plannerRepository: PlannerRepository,
    private val onSessionExpired: () -> Unit = {},
    scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
) : ViewModel() {

    private val coroutineScope = scope

    private val _refreshState = MutableStateFlow<RefreshUiState>(RefreshUiState.Idle)
    val refreshState: StateFlow<RefreshUiState> = _refreshState.asStateFlow()

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
            val sessionTokenStore = SessionTokenStore(context.applicationContext)
            val plannerDatabase = PlannerDatabase.getInstance(context.applicationContext)
            val plannerRepository = PlannerRepository(plannerDatabase, sessionTokenStore)
            return SettingsViewModel(plannerRepository, onSessionExpired) as T
        }
    }
}
