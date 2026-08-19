package com.vikaspokala.daybyday.ui.screens.today

data class TaskItem(
    val id: String,
    val title: String,
    val time: String? = null,
    val isImportant: Boolean = false,
    val isCompleted: Boolean = false
)
