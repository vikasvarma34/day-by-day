package com.vikaspokala.daybyday.ui.screens.schedule

import java.time.LocalDate
import com.vikaspokala.daybyday.ui.models.Recurrence

data class ScheduleTaskItem(
    val id: String,
    val title: String,
    val note: String? = null,
    val date: LocalDate,
    val time: String? = null,
    val reminder: String? = null,
    val recurrence: Recurrence? = null,
    val isImportant: Boolean = false,
    val isCompleted: Boolean = false
)
