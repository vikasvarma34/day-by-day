package com.vikaspokala.daybyday.ui.screens.later

data class LaterTaskItem(
    val id: String,
    val title: String,
    val note: String? = null,
    val isImportant: Boolean = false,
    val isCompleted: Boolean = false,
    val directCompletionId: String? = null,
    val createdAt: String? = null,
    val completedAt: String? = null
)
