package com.vikaspokala.daybyday.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed class Screen : NavKey {
    @Serializable
    data object Today : Screen()
    @Serializable
    data object Schedule : Screen()
    @Serializable
    data object Later : Screen()
    @Serializable
    data object History : Screen()
    @Serializable
    data object Settings : Screen()

    val title: String
        get() = when (this) {
            Today -> "Today"
            Schedule -> "Schedule"
            Later -> "Later"
            History -> "History"
            Settings -> "Settings"
        }

    val icon: ImageVector
        get() = when (this) {
            Today -> Icons.Default.Check
            Schedule -> Icons.Default.DateRange
            Later -> Icons.AutoMirrored.Filled.List
            History -> Icons.Default.Refresh
            Settings -> Icons.Default.Settings
        }

    companion object {
        val bottomNavItems: List<Screen>
            get() = listOf(Today, Schedule, Later, History)
    }
}
