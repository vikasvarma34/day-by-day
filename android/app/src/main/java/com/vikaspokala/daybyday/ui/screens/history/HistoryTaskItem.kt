package com.vikaspokala.daybyday.ui.screens.history

import java.time.LocalDate

data class HistoryTaskItem(
    val id: String,
    val completionId: String = "",
    val title: String,
    val isImportant: Boolean,
    val completedDate: LocalDate,
    val completedAtTime: String,
    val completedAtMinutes: Int,
    val completedAt: String = ""
)
