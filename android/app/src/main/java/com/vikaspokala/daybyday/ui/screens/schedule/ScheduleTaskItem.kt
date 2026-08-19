package com.vikaspokala.daybyday.ui.screens.schedule

import java.time.LocalDate

data class ScheduleTaskItem(
    val id: String,
    val title: String,
    val date: LocalDate,
    val time: String? = null,
    val isImportant: Boolean = false,
    val isCompleted: Boolean = false
)
