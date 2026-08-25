package com.vikaspokala.daybyday.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.widget.Toast
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import com.vikaspokala.daybyday.ui.screens.settings.TestNotificationResult
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.vikaspokala.daybyday.ui.navigation.ProfileFieldType
import com.vikaspokala.daybyday.ui.navigation.Screen
import com.vikaspokala.daybyday.ui.screens.history.HistoryScreen
import com.vikaspokala.daybyday.ui.screens.history.HistoryViewModel
import com.vikaspokala.daybyday.ui.screens.later.LaterScreen
import com.vikaspokala.daybyday.ui.screens.later.LaterViewModel
import com.vikaspokala.daybyday.ui.screens.later.ScheduleItemScreen
import com.vikaspokala.daybyday.ui.screens.schedule.ScheduleScreen
import com.vikaspokala.daybyday.ui.screens.schedule.ScheduleViewModel
import com.vikaspokala.daybyday.ui.screens.settings.ChangePasswordScreen
import com.vikaspokala.daybyday.ui.screens.settings.ChangePasswordUiState
import com.vikaspokala.daybyday.ui.screens.settings.EditProfileFieldScreen
import com.vikaspokala.daybyday.ui.screens.settings.ProfileEditUiState
import com.vikaspokala.daybyday.ui.screens.settings.SettingsScreen
import com.vikaspokala.daybyday.ui.screens.settings.SettingsViewModel
import com.vikaspokala.daybyday.ui.screens.task.TaskScreen
import com.vikaspokala.daybyday.ui.screens.task.PlannerTaskViewModel
import com.vikaspokala.daybyday.ui.screens.today.TodayScreen
import com.vikaspokala.daybyday.ui.screens.today.TodayViewModel
import com.vikaspokala.daybyday.ui.planner.buildCreateSchedule
import com.vikaspokala.daybyday.ui.planner.buildUpdateSchedule
import com.vikaspokala.daybyday.ui.theme.DayByDayAccent
import com.vikaspokala.daybyday.data.remote.dto.AuthUserDto
import com.vikaspokala.daybyday.ui.theme.DayByDayBackground
import com.vikaspokala.daybyday.ui.theme.DayByDayNeutralBorder
import com.vikaspokala.daybyday.ui.theme.DayByDaySecondaryText
import com.vikaspokala.daybyday.ui.theme.DayByDaySurface

@Composable
fun AppShell(
    user: AuthUserDto? = null,
    openTodayTrigger: Int = 0,
    onSessionExpired: () -> Unit = {},
    onUserUpdated: (AuthUserDto) -> Unit = {},
    onPasswordChanged: () -> Unit = onSessionExpired,
    onLogout: () -> Unit = {},
    todayViewModel: TodayViewModel = viewModel(
        factory = TodayViewModel.Factory(LocalContext.current, onSessionExpired)
    ),
    scheduleViewModel: ScheduleViewModel = viewModel(
        factory = ScheduleViewModel.Factory(LocalContext.current, onSessionExpired)
    ),
    laterViewModel: LaterViewModel = viewModel(
        factory = LaterViewModel.Factory(LocalContext.current, onSessionExpired)
    ),
    plannerTaskViewModel: PlannerTaskViewModel = viewModel(
        factory = PlannerTaskViewModel.Factory(LocalContext.current, onSessionExpired)
    ),
    historyViewModel: HistoryViewModel = viewModel(
        factory = HistoryViewModel.Factory(LocalContext.current)
    ),
    settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(LocalContext.current, onSessionExpired)
    )
) {
    val context = LocalContext.current
    val taskActionError by plannerTaskViewModel.actionError.collectAsState()
    LaunchedEffect(taskActionError) {
        taskActionError?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    val reminderNotice by plannerTaskViewModel.reminderNotice.collectAsState()
    LaunchedEffect(reminderNotice) {
        reminderNotice?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            plannerTaskViewModel.dismissReminderNotice()
        }
    }
    val backStack = rememberNavBackStack(Screen.Today as NavKey)

    LaunchedEffect(openTodayTrigger) {
        if (openTodayTrigger > 0) {
            if (backStack.size == 1) {
                backStack[0] = Screen.Today
            } else {
                backStack.clear()
                backStack.add(Screen.Today)
            }
        }
    }

    val currentScreen = backStack.lastOrNull() as? Screen ?: Screen.Today

    val currentUserState by settingsViewModel.currentUser.collectAsState()
    LaunchedEffect(user) {
        settingsViewModel.setCurrentUser(user)
    }

    Scaffold(
        containerColor = DayByDayBackground,
        bottomBar = {
            if (currentScreen != Screen.Settings &&
                currentScreen != Screen.ChangePassword &&
                currentScreen !is Screen.EditProfileField &&
                currentScreen !is Screen.Task &&
                currentScreen !is Screen.ScheduleItem) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Transparent)
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = DayByDaySurface,
                        border = BorderStroke(1.dp, DayByDayNeutralBorder),
                        shadowElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        NavigationBar(
                            containerColor = Color.Transparent,
                            tonalElevation = 0.dp,
                            windowInsets = WindowInsets(0, 0, 0, 0)
                        ) {
                            Screen.bottomNavItems.forEach { screen ->
                                val selected = currentScreen == screen
                                NavigationBarItem(
                                    icon = { Icon(screen.icon, contentDescription = screen.screenTitle) },
                                    label = { Text(screen.screenTitle) },
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
                                            if (screen == Screen.Today) {
                                                todayViewModel.selectToday()
                                            } else if (screen == Screen.Schedule) {
                                                scheduleViewModel.resetToToday()
                                            } else if (screen == Screen.Later) {
                                                laterViewModel.collapseCompleted()
                                            }
                                            if (backStack.size == 1) {
                                                backStack[0] = screen
                                            } else {
                                                backStack.clear()
                                                backStack.add(screen)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.padding(innerPadding),
            transitionSpec = {
                val isInitialTab = initialState.key in Screen.bottomNavItems
                val isTargetTab = targetState.key in Screen.bottomNavItems
                if (isInitialTab && isTargetTab) {
                    fadeIn(animationSpec = tween(durationMillis = 80)) togetherWith
                        fadeOut(animationSpec = tween(durationMillis = 60))
                } else {
                    (fadeIn(animationSpec = tween(durationMillis = 150)) +
                        slideInVertically(animationSpec = tween(durationMillis = 150)) { it / 24 }) togetherWith
                        fadeOut(animationSpec = tween(durationMillis = 100))
                }
            },
            popTransitionSpec = {
                val isInitialTab = initialState.key in Screen.bottomNavItems
                val isTargetTab = targetState.key in Screen.bottomNavItems
                if (isInitialTab && isTargetTab) {
                    fadeIn(animationSpec = tween(durationMillis = 80)) togetherWith
                        fadeOut(animationSpec = tween(durationMillis = 60))
                } else {
                    fadeIn(animationSpec = tween(durationMillis = 100)) togetherWith
                        (fadeOut(animationSpec = tween(durationMillis = 140)) +
                            slideOutVertically(animationSpec = tween(durationMillis = 140)) { it / 24 })
                }
            },
            entryProvider = { key ->
                when (key) {
                    is Screen.Today -> NavEntry(key) {
                        val profileUser by settingsViewModel.currentUser.collectAsState()
                        val activeProfile = profileUser ?: user
                        val todayDisplayName = activeProfile?.nickname?.takeIf { it.isNotBlank() } ?: activeProfile?.firstName.orEmpty()
                        TodayScreen(
                            viewModel = todayViewModel,
                            userName = todayDisplayName,
                            onNavigateToSettings = {
                                backStack.add(Screen.Settings)
                            },
                            onTaskClick = { taskScreen ->
                                backStack.add(taskScreen)
                            },
                            onAddTask = { taskScreen ->
                                backStack.add(taskScreen)
                            }
                        )
                    }
                    is Screen.Schedule -> NavEntry(key) {
                        ScheduleScreen(
                            viewModel = scheduleViewModel,
                            onTaskClick = { taskScreen ->
                                backStack.add(taskScreen)
                            },
                            onAddTask = { taskScreen ->
                                backStack.add(taskScreen)
                            }
                        )
                    }
                    is Screen.Later -> NavEntry(key) {
                        LaterScreen(
                            viewModel = laterViewModel,
                            onTaskClick = { taskScreen ->
                                backStack.add(taskScreen)
                            },
                            onAddTask = { taskScreen ->
                                backStack.add(taskScreen)
                            }
                        )
                    }
                    is Screen.History -> NavEntry(key) { HistoryScreen(viewModel = historyViewModel) }
                    is Screen.Settings -> NavEntry(key) {
                        val refreshState by settingsViewModel.refreshState.collectAsState()
                        val isNotificationAllowed by settingsViewModel.isNotificationAllowed.collectAsState()
                        val profileUser by settingsViewModel.currentUser.collectAsState()
                        val activeProfile = profileUser ?: user
                        val lifecycleOwner = LocalLifecycleOwner.current

                        DisposableEffect(lifecycleOwner) {
                            val observer = LifecycleEventObserver { _, event ->
                                if (event == Lifecycle.Event.ON_RESUME) {
                                    settingsViewModel.refreshNotificationPermission()
                                }
                            }
                            lifecycleOwner.lifecycle.addObserver(observer)
                            onDispose {
                                lifecycleOwner.lifecycle.removeObserver(observer)
                            }
                        }

                        val permissionLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.RequestPermission()
                        ) { _ ->
                            settingsViewModel.refreshNotificationPermission()
                        }

                        SettingsScreen(
                            firstName = activeProfile?.firstName.orEmpty(),
                            lastName = activeProfile?.lastName.orEmpty(),
                            nickname = activeProfile?.nickname,
                            onNavigateBack = {
                                if (backStack.size > 1) {
                                    backStack.removeAt(backStack.lastIndex)
                                }
                            },
                            onChangePasswordClick = {
                                settingsViewModel.resetChangePasswordState()
                                backStack.add(Screen.ChangePassword)
                            },
                            onEditFieldClick = { fieldType ->
                                settingsViewModel.resetProfileEditState()
                                backStack.add(Screen.EditProfileField(fieldType))
                            },
                            onLogoutClick = onLogout,
                            refreshState = refreshState,
                            onRefreshClick = { settingsViewModel.refreshPlannerData(onUserUpdated = onUserUpdated) },
                            isNotificationAllowed = isNotificationAllowed,
                            onNotificationPermissionClick = {
                                if (!isNotificationAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    settingsViewModel.openNotificationSettings(context)
                                }
                            },
                            onTestNotificationClick = {
                                settingsViewModel.sendTestNotification { result ->
                                    when (result) {
                                        is TestNotificationResult.Sent -> {
                                            Toast.makeText(context, "Sample reminder sent", Toast.LENGTH_SHORT).show()
                                        }
                                        is TestNotificationResult.PermissionDenied -> {
                                            Toast.makeText(context, "Notifications are disabled. Allow notifications to test.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        )
                    }
                    is Screen.ChangePassword -> NavEntry(key) {
                        val changePasswordState by settingsViewModel.changePasswordState.collectAsState()
                        ChangePasswordScreen(
                            isLoading = changePasswordState is ChangePasswordUiState.Submitting,
                            errorMessage = (changePasswordState as? ChangePasswordUiState.Error)?.message,
                            onNavigateBack = {
                                settingsViewModel.resetChangePasswordState()
                                if (backStack.size > 1) {
                                    backStack.removeAt(backStack.lastIndex)
                                }
                            },
                            onChangePasswordSubmit = { currentPw, newPw, confirmPw ->
                                settingsViewModel.changePassword(
                                    currentPassword = currentPw,
                                    newPassword = newPw,
                                    confirmPassword = confirmPw,
                                    onSuccess = {
                                        onPasswordChanged()
                                    }
                                )
                            }
                        )
                    }
                    is Screen.EditProfileField -> NavEntry(key) {
                        val fieldType = key.fieldType
                        val profileUser by settingsViewModel.currentUser.collectAsState()
                        val activeProfile = profileUser ?: user
                        val currentValue = when (fieldType) {
                            ProfileFieldType.FIRST_NAME -> activeProfile?.firstName.orEmpty()
                            ProfileFieldType.LAST_NAME -> activeProfile?.lastName.orEmpty()
                            ProfileFieldType.NICKNAME -> activeProfile?.nickname.orEmpty()
                        }
                        val editState by settingsViewModel.profileEditState.collectAsState()
                        EditProfileFieldScreen(
                            fieldType = fieldType,
                            initialValue = currentValue,
                            isLoading = editState is ProfileEditUiState.Saving,
                            errorMessage = (editState as? ProfileEditUiState.Error)?.message,
                            onNavigateBack = {
                                settingsViewModel.resetProfileEditState()
                                if (backStack.size > 1) {
                                    backStack.removeAt(backStack.lastIndex)
                                }
                            },
                            onSave = { newValue ->
                                settingsViewModel.updateProfile(
                                    fieldType = fieldType,
                                    newValue = newValue,
                                    onUserUpdated = onUserUpdated,
                                    onSuccess = {
                                        if (backStack.size > 1) {
                                            backStack.removeAt(backStack.lastIndex)
                                        }
                                    }
                                )
                            }
                        )
                    }
                    is Screen.Task -> NavEntry(key) {
                        TaskScreen(
                            isCreateMode = key.isCreateMode,
                            initialTitle = key.initialTitle,
                            initialNote = key.initialNote,
                            initialIsImportant = key.initialIsImportant,
                            isCompleted = key.isCompleted,
                            dateString = key.dateString,
                            timeString = key.timeString,
                            reminderString = key.reminderString,
                            recurrence = key.recurrence,
                            isLaterTask = key.isLaterTask,
                            plannerToday = todayViewModel.getPlannerToday(),
                            onNavigateBack = {
                                if (backStack.size > 1) {
                                    backStack.removeAt(backStack.lastIndex)
                                }
                            },
                            onScheduleThisClick = {
                                key.taskId?.let { id ->
                                    backStack.add(
                                        Screen.ScheduleItem(
                                            taskId = id,
                                            initialTitle = key.initialTitle,
                                            initialNote = key.initialNote,
                                            initialIsImportant = key.initialIsImportant,
                                            recurrence = key.recurrence
                                        )
                                    )
                                }
                            },
                            onSaveTask = { title, note, newDateStr, newTimeStr, newReminderStr, newRecurrenceStr, isImp ->
                                val onSuccess = {
                                    if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                                }
                                if (key.isCreateMode) {
                                    if (key.isLaterTask || key.sourceScreen == "LATER") {
                                        plannerTaskViewModel.createLater(title, note, isImp, onSuccess)
                                    } else {
                                        val fallback = if (key.sourceScreen == "SCHEDULE") scheduleViewModel.selectedDate.value else todayViewModel.selectedDate.value
                                        val date = parseDateString(newDateStr, todayViewModel.getPlannerToday()) ?: fallback
                                        plannerTaskViewModel.createScheduled(
                                            title, note, isImp,
                                            buildCreateSchedule(date, newTimeStr, newReminderStr, newRecurrenceStr),
                                            onSuccess
                                        )
                                    }
                                } else {
                                    key.taskId?.let { id ->
                                        val contentChanged = title != key.initialTitle || note.orEmpty() != key.initialNote.orEmpty() || isImp != key.initialIsImportant
                                        val scheduleChanged = newDateStr != key.dateString || newTimeStr != key.timeString ||
                                            newReminderStr != key.reminderString || newRecurrenceStr != key.recurrence
                                        val plannerToday = todayViewModel.getPlannerToday()
                                        val date = parseDateString(newDateStr, plannerToday)
                                            ?: parseDateString(key.dateString, plannerToday)
                                            ?: plannerToday
                                        val initialDate = parseDateString(key.dateString, plannerToday)
                                            ?: plannerToday
                                        val effectiveDate = if (initialDate.isBefore(plannerToday)) plannerToday else initialDate
                                        plannerTaskViewModel.saveExisting(
                                            id, title, note, isImp, contentChanged, scheduleChanged,
                                            effectiveDate, buildUpdateSchedule(date, newTimeStr, newReminderStr, newRecurrenceStr), onSuccess
                                        )
                                    }
                                }
                            },
                            onConfirmDelete = {
                                key.taskId?.let { id ->
                                    plannerTaskViewModel.deleteTask(id) {
                                        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                                    }
                                }
                            }
                        )
                    }
                    is Screen.ScheduleItem -> NavEntry(key) {
                        ScheduleItemScreen(
                            initialTitle = key.initialTitle,
                            initialNote = key.initialNote,
                            initialIsImportant = key.initialIsImportant,
                            initialRecurrence = key.recurrence,
                            onNavigateBack = {
                                if (backStack.size > 1) {
                                    backStack.removeAt(backStack.lastIndex)
                                }
                            },
                            onSchedule = { title, note, date, time, reminder, recurrence, isImportant ->
                                val contentChanged = title != key.initialTitle || note.orEmpty() != key.initialNote.orEmpty() || isImportant != key.initialIsImportant
                                plannerTaskViewModel.scheduleLater(
                                    key.taskId, title, note, isImportant, contentChanged,
                                    buildUpdateSchedule(date, time, reminder, recurrence)
                                ) {
                                    while (backStack.isNotEmpty() && (backStack.last() is Screen.ScheduleItem || backStack.last() is Screen.Task)) {
                                        backStack.removeAt(backStack.lastIndex)
                                    }
                                }
                            }
                        )
                    }
                    else -> error("Unknown destination key: $key")
                }
            },
            onBack = {
                if (backStack.size > 1) {
                    backStack.removeAt(backStack.lastIndex)
                }
            }
        )
    }
}

private fun parseDateString(dateStr: String?, referenceDate: LocalDate = LocalDate.now()): LocalDate? {
    if (dateStr.isNullOrEmpty()) return null
    return try {
        val formatter = java.time.format.DateTimeFormatterBuilder()
            .appendPattern("EEEE, d MMMM")
            .parseDefaulting(java.time.temporal.ChronoField.YEAR, referenceDate.year.toLong())
            .toFormatter(Locale.getDefault())
        LocalDate.parse(dateStr, formatter)
    } catch (e: Exception) {
        try {
            val currentYear = referenceDate.year
            val fullStr = if (!dateStr.contains(currentYear.toString())) "$dateStr $currentYear" else dateStr
            val formatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())
            LocalDate.parse(fullStr, formatter)
        } catch (e2: Exception) {
            try {
                LocalDate.parse(dateStr)
            } catch (e3: Exception) {
                null
            }
        }
    }
}
