package com.vikaspokala.daybyday.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.vikaspokala.daybyday.ui.navigation.ProfileFieldType
import com.vikaspokala.daybyday.ui.navigation.Screen
import com.vikaspokala.daybyday.ui.screens.history.HistoryScreen
import com.vikaspokala.daybyday.ui.screens.later.LaterScreen
import com.vikaspokala.daybyday.ui.screens.later.LaterViewModel
import com.vikaspokala.daybyday.ui.screens.schedule.ScheduleScreen
import com.vikaspokala.daybyday.ui.screens.schedule.ScheduleViewModel
import com.vikaspokala.daybyday.ui.screens.settings.ChangePasswordScreen
import com.vikaspokala.daybyday.ui.screens.settings.EditProfileFieldScreen
import com.vikaspokala.daybyday.ui.screens.settings.SettingsScreen
import com.vikaspokala.daybyday.ui.screens.task.TaskScreen
import com.vikaspokala.daybyday.ui.screens.today.TodayScreen
import com.vikaspokala.daybyday.ui.screens.today.TodayViewModel
import com.vikaspokala.daybyday.ui.theme.DayByDayAccent
import com.vikaspokala.daybyday.ui.theme.DayByDayBackground
import com.vikaspokala.daybyday.ui.theme.DayByDayNeutralBorder
import com.vikaspokala.daybyday.ui.theme.DayByDaySecondaryText
import com.vikaspokala.daybyday.ui.theme.DayByDaySurface

@Composable
fun AppShell(
    todayViewModel: TodayViewModel = viewModel(),
    scheduleViewModel: ScheduleViewModel = viewModel(),
    laterViewModel: LaterViewModel = viewModel()
) {
    val backStack = rememberNavBackStack(Screen.Today as NavKey)

    val currentScreen = backStack.lastOrNull() as? Screen ?: Screen.Today

    var firstName by remember { mutableStateOf("Vikas") }
    var lastName by remember { mutableStateOf("Varma") }
    var nickname by remember { mutableStateOf("Vicky") }

    Scaffold(
        containerColor = DayByDayBackground,
        bottomBar = {
            if (currentScreen != Screen.Settings &&
                currentScreen != Screen.ChangePassword &&
                currentScreen !is Screen.EditProfileField &&
                currentScreen !is Screen.Task) {
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
                        TodayScreen(
                            viewModel = todayViewModel,
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
                    is Screen.History -> NavEntry(key) { HistoryScreen() }
                    is Screen.Settings -> NavEntry(key) {
                        SettingsScreen(
                            firstName = firstName,
                            lastName = lastName,
                            nickname = nickname,
                            onNavigateBack = {
                                if (backStack.size > 1) {
                                    backStack.removeAt(backStack.lastIndex)
                                }
                            },
                            onChangePasswordClick = {
                                backStack.add(Screen.ChangePassword)
                            },
                            onEditFieldClick = { fieldType ->
                                backStack.add(Screen.EditProfileField(fieldType))
                            }
                        )
                    }
                    is Screen.ChangePassword -> NavEntry(key) {
                        ChangePasswordScreen(
                            onNavigateBack = {
                                if (backStack.size > 1) {
                                    backStack.removeAt(backStack.lastIndex)
                                }
                            }
                        )
                    }
                    is Screen.EditProfileField -> NavEntry(key) {
                        val fieldType = key.fieldType
                        val currentValue = when (fieldType) {
                            ProfileFieldType.FIRST_NAME -> firstName
                            ProfileFieldType.LAST_NAME -> lastName
                            ProfileFieldType.NICKNAME -> nickname
                        }
                        EditProfileFieldScreen(
                            fieldType = fieldType,
                            initialValue = currentValue,
                            onNavigateBack = {
                                if (backStack.size > 1) {
                                    backStack.removeAt(backStack.lastIndex)
                                }
                            },
                            onSave = { newValue ->
                                when (fieldType) {
                                    ProfileFieldType.FIRST_NAME -> firstName = newValue
                                    ProfileFieldType.LAST_NAME -> lastName = newValue
                                    ProfileFieldType.NICKNAME -> nickname = newValue
                                }
                                if (backStack.size > 1) {
                                    backStack.removeAt(backStack.lastIndex)
                                }
                            }
                        )
                    }
                    is Screen.Task -> NavEntry(key) {
                        TaskScreen(
                            isCreateMode = key.isCreateMode,
                            initialTitle = key.initialTitle,
                            initialNote = key.initialNote,
                            initialIsImportant = key.initialIsImportant,
                            dateString = key.dateString,
                            timeString = key.timeString,
                            isLaterTask = key.isLaterTask,
                            onNavigateBack = {
                                if (backStack.size > 1) {
                                    backStack.removeAt(backStack.lastIndex)
                                }
                            },
                            onSaveTask = { title, note, newDateStr, newTimeStr, isImp ->
                                if (key.isCreateMode) {
                                    when {
                                        key.isLaterTask || key.sourceScreen == "LATER" -> {
                                            laterViewModel.addTask(title = title, note = note, isImportant = isImp)
                                        }
                                        key.sourceScreen == "SCHEDULE" -> {
                                            val parsedDate = parseDateString(newDateStr) ?: scheduleViewModel.selectedDate.value
                                            scheduleViewModel.addTask(title = title, note = note, date = parsedDate, time = newTimeStr, isImportant = isImp)
                                        }
                                        else -> {
                                            val parsedDate = parseDateString(newDateStr) ?: todayViewModel.selectedDate.value
                                            todayViewModel.addTask(title = title, note = note, date = parsedDate, time = newTimeStr, isImportant = isImp)
                                            scheduleViewModel.addTask(title = title, note = note, date = parsedDate, time = newTimeStr, isImportant = isImp)
                                        }
                                    }
                                } else {
                                    key.taskId?.let { id ->
                                        when {
                                            key.isLaterTask || key.sourceScreen == "LATER" -> {
                                                laterViewModel.updateTask(id = id, title = title, note = note, isImportant = isImp)
                                            }
                                            key.sourceScreen == "SCHEDULE" -> {
                                                val parsedDate = parseDateString(newDateStr) ?: scheduleViewModel.selectedDate.value
                                                scheduleViewModel.updateTask(id = id, title = title, note = note, date = parsedDate, time = newTimeStr, isImportant = isImp)
                                            }
                                            else -> {
                                                val parsedDate = parseDateString(newDateStr) ?: todayViewModel.selectedDate.value
                                                todayViewModel.updateTask(id = id, title = title, note = note, date = parsedDate, time = newTimeStr, isImportant = isImp)
                                                scheduleViewModel.updateTask(id = id, title = title, note = note, date = parsedDate, time = newTimeStr, isImportant = isImp)
                                            }
                                        }
                                    }
                                }
                                if (backStack.size > 1) {
                                    backStack.removeAt(backStack.lastIndex)
                                }
                            },
                            onConfirmDelete = {
                                key.taskId?.let { id ->
                                    todayViewModel.deleteTask(id)
                                    scheduleViewModel.deleteTask(id)
                                    laterViewModel.deleteTask(id)
                                }
                                if (backStack.size > 1) {
                                    backStack.removeAt(backStack.lastIndex)
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

private fun parseDateString(dateStr: String?): LocalDate? {
    if (dateStr.isNullOrEmpty()) return null
    return try {
        val currentYear = LocalDate.now().year
        val fullStr = if (!dateStr.contains(currentYear.toString())) "$dateStr $currentYear" else dateStr
        val formatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())
        LocalDate.parse(fullStr, formatter)
    } catch (e: Exception) {
        null
    }
}
