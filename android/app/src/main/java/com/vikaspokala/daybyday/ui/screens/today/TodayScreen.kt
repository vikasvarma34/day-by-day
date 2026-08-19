package com.vikaspokala.daybyday.ui.screens.today

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vikaspokala.daybyday.ui.components.TaskCard
import com.vikaspokala.daybyday.ui.theme.DayByDayAccent
import com.vikaspokala.daybyday.ui.theme.DayByDayBackground
import com.vikaspokala.daybyday.ui.theme.DayByDayNeutralBorder
import com.vikaspokala.daybyday.ui.theme.DayByDayPrimaryText
import com.vikaspokala.daybyday.ui.theme.DayByDaySecondaryText
import com.vikaspokala.daybyday.ui.theme.DayByDaySurface

@Composable
fun TodayScreen(
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = viewModel()
) {
    val tasks by viewModel.tasks.collectAsState()

    val timedTasks = tasks.filter { !it.isCompleted && it.time != null }
    val anytimeTasks = tasks.filter { !it.isCompleted && it.time == null }
    val completedTasks = tasks.filter { it.isCompleted }

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
                                text = "Good afternoon, Vicky",
                                style = MaterialTheme.typography.titleLarge,
                                color = DayByDayPrimaryText
                            )
                            Text(
                                text = "Wednesday, Aug 19",
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
                            IconButton(onClick = { /* Previous day callback placeholder */ }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "Previous day",
                                    tint = DayByDayPrimaryText
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                tint = DayByDayAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Today",
                                style = MaterialTheme.typography.titleMedium,
                                color = DayByDayPrimaryText
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = DayByDaySurface,
                            border = BorderStroke(1.dp, DayByDayNeutralBorder)
                        ) {
                            IconButton(onClick = { /* Next day callback placeholder */ }) {
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
                            onToggleImportant = { viewModel.toggleImportant(task.id) }
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
                            onToggleImportant = { viewModel.toggleImportant(task.id) }
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
                            onToggleImportant = { viewModel.toggleImportant(task.id) }
                        )
                    }
                }
            }
        }

        // Floating Action Button
        FloatingActionButton(
            onClick = { /* Add task callback placeholder */ },
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
