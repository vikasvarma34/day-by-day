package com.vikaspokala.daybyday

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vikaspokala.daybyday.notification.DefaultAppNotificationManager
import com.vikaspokala.daybyday.notification.ReminderBroadcastReceiver
import com.vikaspokala.daybyday.ui.AppShell
import com.vikaspokala.daybyday.ui.screens.SplashScreen
import com.vikaspokala.daybyday.ui.screens.auth.AuthUiState
import com.vikaspokala.daybyday.ui.screens.auth.AuthViewModel
import com.vikaspokala.daybyday.ui.screens.auth.ConnectionErrorScreen
import com.vikaspokala.daybyday.ui.screens.auth.SignInScreen
import com.vikaspokala.daybyday.ui.theme.DayByDayTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val openTodayTrigger = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleNotificationIntent(intent)
        DefaultAppNotificationManager(applicationContext).createNotificationChannel()
        setContent {
            DayByDayTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val authViewModel: AuthViewModel = viewModel(
                        factory = AuthViewModel.Factory(applicationContext)
                    )
                    val authState by authViewModel.uiState.collectAsState()
                    val todayTrigger by openTodayTrigger.collectAsState()

                    when (val state = authState) {
                        is AuthUiState.Loading -> SplashScreen()
                        is AuthUiState.SignedOut -> SignInScreen(
                            isLoading = state.isSubmitting,
                            errorMessage = state.error,
                            onSignIn = { email, password ->
                                authViewModel.signIn(email, password)
                            }
                        )
                        is AuthUiState.ConnectionError -> ConnectionErrorScreen(
                            isRetrying = state.isRetrying,
                            onRetry = { authViewModel.retrySessionVerification() }
                        )
                        is AuthUiState.Authenticated -> AppShell(
                            user = state.user,
                            openTodayTrigger = todayTrigger,
                            onSessionExpired = { authViewModel.handleSessionExpired() },
                            onUserUpdated = { authViewModel.updateUser(it) },
                            onPasswordChanged = { authViewModel.handlePasswordChanged() },
                            onLogout = { authViewModel.logout() }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent?.action == ReminderBroadcastReceiver.ACTION_OPEN_TODAY) {
            openTodayTrigger.value += 1
        }
    }
}
