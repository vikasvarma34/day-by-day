package com.vikaspokala.daybyday.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.vikaspokala.daybyday.ui.navigation.Screen
import com.vikaspokala.daybyday.ui.screens.HistoryScreen
import com.vikaspokala.daybyday.ui.screens.LaterScreen
import com.vikaspokala.daybyday.ui.screens.ScheduleScreen
import com.vikaspokala.daybyday.ui.screens.SettingsScreen
import com.vikaspokala.daybyday.ui.screens.today.TodayScreen
import com.vikaspokala.daybyday.ui.theme.DayByDayAccent
import com.vikaspokala.daybyday.ui.theme.DayByDayNeutralBorder
import com.vikaspokala.daybyday.ui.theme.DayByDaySecondaryText
import com.vikaspokala.daybyday.ui.theme.DayByDaySurface

@Composable
fun AppShell() {
    val backStack = rememberNavBackStack(Screen.Today as NavKey)

    val currentScreen = backStack.lastOrNull() as? Screen ?: Screen.Today

    Scaffold(
        bottomBar = {
            Surface(
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = DayByDaySurface,
                border = BorderStroke(1.dp, DayByDayNeutralBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp
                ) {
                    Screen.bottomNavItems.forEach { screen ->
                        val selected = currentScreen == screen
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = selected,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = DayByDayAccent,
                                selectedTextColor = DayByDayAccent,
                                unselectedIconColor = DayByDaySecondaryText,
                                unselectedTextColor = DayByDaySecondaryText,
                                indicatorColor = Color.Transparent
                            ),
                            onClick = {
                                if (currentScreen != screen) {
                                    backStack.clear()
                                    backStack.add(screen)
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavDisplay(
            backStack = backStack,
            entryProvider = { key ->
                when (key) {
                    is Screen.Today -> NavEntry(key) {
                        TodayScreen(
                            onNavigateToSettings = {
                                backStack.add(Screen.Settings)
                            }
                        )
                    }
                    is Screen.Schedule -> NavEntry(key) { ScheduleScreen() }
                    is Screen.Later -> NavEntry(key) { LaterScreen() }
                    is Screen.History -> NavEntry(key) { HistoryScreen() }
                    is Screen.Settings -> NavEntry(key) { SettingsScreen() }
                    else -> error("Unknown destination key: $key")
                }
            },
            modifier = Modifier.padding(innerPadding),
            onBack = {
                if (backStack.size > 1) {
                    backStack.removeAt(backStack.lastIndex)
                }
            }
        )
    }
}
