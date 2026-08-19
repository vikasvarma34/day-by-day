package com.vikaspokala.daybyday

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.vikaspokala.daybyday.ui.AppShell
import com.vikaspokala.daybyday.ui.screens.SplashScreen
import com.vikaspokala.daybyday.ui.theme.DayByDayTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DayByDayTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var showSplash by remember { mutableStateOf(true) }

                    LaunchedEffect(Unit) {
                        delay(1200)
                        showSplash = false
                    }

                    if (showSplash) {
                        SplashScreen()
                    } else {
                        AppShell()
                    }
                }
            }
        }
    }
}
