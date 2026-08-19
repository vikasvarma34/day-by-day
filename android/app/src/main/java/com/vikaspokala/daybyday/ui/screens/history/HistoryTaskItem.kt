package com.vikaspokala.daybyday.ui.screens.history

import java.time.LocalDate

data class HistoryTaskItem(
    val id: String,
    val title: String,
    val completedDate: LocalDate,
    val completedAtTime: String,
    val completedAtMinutes: Int
)
