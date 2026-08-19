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
enum class ProfileFieldType {
    FIRST_NAME,
    LAST_NAME,
    NICKNAME
}

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
    @Serializable
    data object ChangePassword : Screen()
    @Serializable
    data class EditProfileField(val fieldType: ProfileFieldType) : Screen()
    @Serializable
    data class Task(
        val isCreateMode: Boolean = false,
        val taskId: String? = null,
        val initialTitle: String = "",
        val initialNote: String? = null,
        val initialIsImportant: Boolean = false,
        val isCompleted: Boolean = false,
        val dateString: String? = null,
        val timeString: String? = null,
        val reminderString: String? = null,
        val isLaterTask: Boolean = false,
        val sourceScreen: String = "TODAY"
    ) : Screen()
    @Serializable
    data class ScheduleItem(
        val taskId: String,
        val initialTitle: String,
        val initialNote: String? = null,
        val initialIsImportant: Boolean = false
    ) : Screen()

    val screenTitle: String
        get() = when (this) {
            Today -> "Today"
            Schedule -> "Schedule"
            Later -> "Later"
            History -> "History"
            Settings -> "Settings"
            ChangePassword -> "Change Password"
            is EditProfileField -> "Edit Profile"
            is Task -> "Task"
            is ScheduleItem -> "Schedule item"
        }

    val icon: ImageVector
        get() = when (this) {
            Today -> Icons.Default.Check
            Schedule -> Icons.Default.DateRange
            Later -> Icons.AutoMirrored.Filled.List
            History -> Icons.Default.Refresh
            Settings -> Icons.Default.Settings
            ChangePassword -> Icons.Default.Settings
            is EditProfileField -> Icons.Default.Settings
            is Task -> Icons.Default.Check
            is ScheduleItem -> Icons.Default.DateRange
        }

    companion object {
        val bottomNavItems: List<Screen>
            get() = listOf(Today, Schedule, Later, History)
    }
}
