package com.vikaspokala.daybyday.ui.screens.schedule

import java.time.format.DateTimeFormatter
import java.util.Locale
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vikaspokala.daybyday.ui.components.MonthCalendar
import com.vikaspokala.daybyday.ui.components.TaskCard
import com.vikaspokala.daybyday.ui.navigation.Screen
import com.vikaspokala.daybyday.ui.theme.DayByDayAccent
import com.vikaspokala.daybyday.ui.theme.DayByDayBackground
import com.vikaspokala.daybyday.ui.theme.DayByDayNeutralBorder
import com.vikaspokala.daybyday.ui.theme.DayByDayPrimaryText
import com.vikaspokala.daybyday.ui.theme.DayByDaySecondaryText
import com.vikaspokala.daybyday.ui.theme.DayByDaySurface

@Composable
fun ScheduleScreen(
    onAddTask: (Screen.Task) -> Unit = {},
    onTaskClick: (Screen.Task) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ScheduleViewModel
) {
    val currentMonth by viewModel.currentMonth.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val importantDates by viewModel.importantDates.collectAsState()
    val actionError by viewModel.actionError.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(actionError) {
        actionError?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    val selectedDateTasks = remember(tasks, selectedDate) {
        viewModel.getTasksForDate(selectedDate, tasks)
    }

    val todayDate = remember { java.time.LocalDate.now() }
    val monthTitleFormatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()) }
    val selectedDateFormatter = remember { DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()) }
    val fullDateFormatter = remember { DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()) }

    val formattedSelectedDate = remember(selectedDate) {
        selectedDate.format(fullDateFormatter)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DayByDayBackground)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            // Top Area: Schedule Heading & Today Control
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
                        Text(
                            text = "Schedule",
                            style = MaterialTheme.typography.displayLarge,
                            color = DayByDayPrimaryText
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Surface(
                            shape = CircleShape,
                            color = DayByDaySurface,
                            border = BorderStroke(1.dp, DayByDayNeutralBorder)
                        ) {
                            TextButton(
                                onClick = { viewModel.selectToday() },
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Today",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = DayByDayPrimaryText
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Month Navigation Bar
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
                            IconButton(onClick = { viewModel.previousMonth() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "Previous month",
                                    tint = DayByDayPrimaryText
                                )
                            }
                        }

                        Text(
                            text = currentMonth.format(monthTitleFormatter),
                            style = MaterialTheme.typography.titleMedium,
                            color = DayByDayPrimaryText
                        )

                        Surface(
                            shape = CircleShape,
                            color = DayByDaySurface,
                            border = BorderStroke(1.dp, DayByDayNeutralBorder)
                        ) {
                            IconButton(onClick = { viewModel.nextMonth() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Next month",
                                    tint = DayByDayPrimaryText
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Month Calendar Grid
                    MonthCalendar(
                        month = currentMonth,
                        selectedDate = selectedDate,
                        todayDate = todayDate,
                        hasImportantTask = { date -> date in importantDates },
                        onDateSelected = { date -> viewModel.selectDate(date) }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Selected Date Header (Date & Task Count)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedDate.format(selectedDateFormatter),
                            style = MaterialTheme.typography.titleMedium,
                            color = DayByDayPrimaryText,
                            modifier = Modifier.weight(1f)
                        )
                        val count = selectedDateTasks.size
                        val countText = if (count == 1) "1 task" else "$count tasks"
                        Text(
                            text = countText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = DayByDaySecondaryText
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Selected Date Task Cards
            if (selectedDateTasks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Nothing planned for this day.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = DayByDaySecondaryText,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                items(selectedDateTasks, key = { it.id }) { task ->
                    Box(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    ) {
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
                                        dateString = formattedSelectedDate,
                                        timeString = task.time,
                                        reminderString = task.reminder,
                                        recurrence = task.recurrence,
                                        isLaterTask = false,
                                        sourceScreen = "SCHEDULE"
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }

        // Floating Action Button carrying currently selected date
        FloatingActionButton(
            onClick = {
                onAddTask(
                    Screen.Task(
                        isCreateMode = true,
                        dateString = formattedSelectedDate,
                        isLaterTask = false,
                        sourceScreen = "SCHEDULE"
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
