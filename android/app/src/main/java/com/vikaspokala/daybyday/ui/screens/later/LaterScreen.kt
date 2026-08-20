package com.vikaspokala.daybyday.ui.screens.later

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.vikaspokala.daybyday.ui.theme.DayByDayPrimaryText
import com.vikaspokala.daybyday.ui.theme.DayByDaySecondaryText

@Composable
fun LaterScreen(
    onAddTask: (Screen.Task) -> Unit = {},
    onTaskClick: (Screen.Task) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LaterViewModel = viewModel()
) {
    val tasks by viewModel.tasks.collectAsState()
    val isCompletedExpanded by viewModel.isCompletedExpanded.collectAsState()

    val activeTasks = remember(tasks) { viewModel.getActiveTasks(tasks) }
    val completedTasks = remember(tasks) { viewModel.getCompletedTasks(tasks) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DayByDayBackground)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            // Header Section
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = "Later",
                        style = MaterialTheme.typography.displayLarge,
                        color = DayByDayPrimaryText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Things you want to do someday.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DayByDaySecondaryText
                    )
                }
            }

            // Empty state when no Later tasks exist
            if (tasks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 72.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Nothing waiting for later.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = DayByDaySecondaryText,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            // Active Later Task List
            items(activeTasks, key = { it.id }) { task ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                ) {
                    TaskCard(
                        title = task.title,
                        subtitle = null,
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
                                    isCompleted = false,
                                    dateString = null,
                                    timeString = null,
                                    isLaterTask = true,
                                    sourceScreen = "LATER"
                                )
                            )
                        }
                    )
                }
            }

            // Completed Collapsible Section
            if (completedTasks.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleCompletedExpanded() }
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Completed (${completedTasks.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = DayByDayPrimaryText,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (isCompletedExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isCompletedExpanded) "Collapse completed tasks" else "Expand completed tasks",
                            tint = DayByDaySecondaryText
                        )
                    }
                }

                if (isCompletedExpanded) {
                    items(completedTasks, key = { it.id }) { task ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 20.dp, vertical = 4.dp)
                        ) {
                            TaskCard(
                                title = task.title,
                                subtitle = null,
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
                                            isCompleted = true,
                                            dateString = null,
                                            timeString = null,
                                            isLaterTask = true,
                                            sourceScreen = "LATER"
                                        )
                                    )
                                }
                            )
                        }
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
                        dateString = null,
                        isLaterTask = true,
                        sourceScreen = "LATER"
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
