package com.vikaspokala.daybyday

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vikaspokala.daybyday.ui.AppShell
import com.vikaspokala.daybyday.ui.screens.SplashScreen
import com.vikaspokala.daybyday.ui.screens.auth.AuthUiState
import com.vikaspokala.daybyday.ui.screens.auth.AuthViewModel
import com.vikaspokala.daybyday.ui.screens.auth.ConnectionErrorScreen
import com.vikaspokala.daybyday.ui.screens.auth.SignInScreen
import com.vikaspokala.daybyday.ui.theme.DayByDayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DayByDayTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val authViewModel: AuthViewModel = viewModel(
                        factory = AuthViewModel.Factory(applicationContext)
                    )
                    val authState by authViewModel.uiState.collectAsState()

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
                        is AuthUiState.Authenticated -> AppShell(user = state.user)
                    }
                }
            }
        }
    }
}
