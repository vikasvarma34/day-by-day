package com.vikaspokala.daybyday.ui.screens.today

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vikaspokala.daybyday.ui.components.TaskCard
import com.vikaspokala.daybyday.ui.navigation.Screen
import com.vikaspokala.daybyday.ui.theme.DayByDayAccent
import com.vikaspokala.daybyday.ui.theme.DayByDayBackground
import com.vikaspokala.daybyday.ui.theme.DayByDayNeutralBorder
import com.vikaspokala.daybyday.ui.theme.DayByDayPrimaryText
import com.vikaspokala.daybyday.ui.theme.DayByDaySecondaryText
import com.vikaspokala.daybyday.ui.theme.DayByDaySurface

@Composable
fun TodayScreen(
    onNavigateToSettings: () -> Unit,
    onTaskClick: (Screen.Task) -> Unit = {},
    onAddTask: (Screen.Task) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = viewModel()
) {
    val selectedDate by viewModel.selectedDate.collectAsState()
    val tasks by viewModel.tasks.collectAsState()

    val selectedDateTasks = remember(tasks, selectedDate) {
        viewModel.getTasksForDate(selectedDate, tasks)
    }

    val timedTasks = remember(selectedDateTasks) {
        selectedDateTasks.filter { !it.isCompleted && it.time != null }
    }
    val anytimeTasks = remember(selectedDateTasks) {
        selectedDateTasks.filter { !it.isCompleted && it.time == null }
    }
    val completedTasks = remember(selectedDateTasks) {
        selectedDateTasks.filter { it.isCompleted }
    }

    val actualTodayFormatted = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))
    }
    val greeting = remember {
        TodayViewModel.getGreeting()
    }

    val selectedDateFormatted = remember(selectedDate) {
        selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DayByDayBackground)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            // Header Section
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = greeting,
                                style = MaterialTheme.typography.titleLarge,
                                color = DayByDayPrimaryText
                            )
                            Text(
                                text = actualTodayFormatted,
                                style = MaterialTheme.typography.bodyMedium,
                                color = DayByDaySecondaryText
                            )
                        }

                        // Settings circular button
                        Surface(
                            shape = CircleShape,
                            color = DayByDaySurface,
                            border = BorderStroke(1.dp, DayByDayNeutralBorder)
                        ) {
                            IconButton(onClick = onNavigateToSettings) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = DayByDayPrimaryText
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Date Navigation Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = DayByDaySurface,
                            border = BorderStroke(1.dp, DayByDayNeutralBorder)
                        ) {
                            IconButton(onClick = { viewModel.selectPreviousDay() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "Previous day",
                                    tint = DayByDayPrimaryText
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { viewModel.selectToday() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                tint = DayByDayAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (selectedDate == LocalDate.now()) "Today" else selectedDate.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())),
                                style = MaterialTheme.typography.titleMedium,
                                color = DayByDayPrimaryText
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = DayByDaySurface,
                            border = BorderStroke(1.dp, DayByDayNeutralBorder)
                        ) {
                            IconButton(onClick = { viewModel.selectNextDay() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Next day",
                                    tint = DayByDayPrimaryText
                                )
                            }
                        }
                    }
                }
            }

            // Empty state if no tasks on the selected date
            if (selectedDateTasks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No tasks for this date",
                            style = MaterialTheme.typography.bodyMedium,
                            color = DayByDaySecondaryText
                        )
                    }
                }
            }

            // Timed Tasks Section ("Your day")
            if (timedTasks.isNotEmpty()) {
                item {
                    SectionHeader(title = "Your day")
                }
                items(timedTasks, key = { it.id }) { task ->
                    PaddingWrapper {
                        TaskCard(
                            title = task.title,
                            subtitle = task.time.orEmpty(),
                            isImportant = task.isImportant,
                            isCompleted = task.isCompleted,
                            onToggleCompletion = { viewModel.toggleCompletion(task.id) },
                            onToggleImportant = { viewModel.toggleImportant(task.id) },
                            onCardClick = {
                                onTaskClick(
                                    Screen.Task(
                                        isCreateMode = false,
                                        taskId = task.id,
                                        initialTitle = task.title,
                                        initialNote = task.note,
                                        initialIsImportant = task.isImportant,
                                        dateString = selectedDateFormatted,
                                        timeString = task.time,
                                        isLaterTask = false,
                                        sourceScreen = "TODAY"
                                    )
                                )
                            }
                        )
                    }
                }
            }

            // Anytime Tasks Section
            if (anytimeTasks.isNotEmpty()) {
                item {
                    SectionHeader(title = "Anytime")
                }
                items(anytimeTasks, key = { it.id }) { task ->
                    PaddingWrapper {
                        TaskCard(
                            title = task.title,
                            subtitle = "Anytime",
                            isImportant = task.isImportant,
                            isCompleted = task.isCompleted,
                            onToggleCompletion = { viewModel.toggleCompletion(task.id) },
                            onToggleImportant = { viewModel.toggleImportant(task.id) },
                            onCardClick = {
                                onTaskClick(
                                    Screen.Task(
                                        isCreateMode = false,
                                        taskId = task.id,
                                        initialTitle = task.title,
                                        initialNote = task.note,
                                        initialIsImportant = task.isImportant,
                                        dateString = selectedDateFormatted,
                                        timeString = null,
                                        isLaterTask = false,
                                        sourceScreen = "TODAY"
                                    )
                                )
                            }
                        )
                    }
                }
            }

            // Completed Tasks Section
            if (completedTasks.isNotEmpty()) {
                item {
                    SectionHeader(title = "Completed")
                }
                items(completedTasks, key = { it.id }) { task ->
                    PaddingWrapper {
                        TaskCard(
                            title = task.title,
                            subtitle = task.time ?: "Anytime",
                            isImportant = task.isImportant,
                            isCompleted = task.isCompleted,
                            onToggleCompletion = { viewModel.toggleCompletion(task.id) },
                            onToggleImportant = { viewModel.toggleImportant(task.id) },
                            onCardClick = {
                                onTaskClick(
                                    Screen.Task(
                                        isCreateMode = false,
                                        taskId = task.id,
                                        initialTitle = task.title,
                                        initialNote = task.note,
                                        initialIsImportant = task.isImportant,
                                        dateString = selectedDateFormatted,
                                        timeString = task.time,
                                        isLaterTask = false,
                                        sourceScreen = "TODAY"
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }

        // Floating Action Button
        FloatingActionButton(
            onClick = {
                onAddTask(
                    Screen.Task(
                        isCreateMode = true,
                        dateString = selectedDateFormatted,
                        isLaterTask = false,
                        sourceScreen = "TODAY"
                    )
                )
            },
            containerColor = DayByDayAccent,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp, end = 20.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add task"
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = DayByDayPrimaryText,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
private fun PaddingWrapper(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
    ) {
        content()
    }
}
