package com.vikaspokala.daybyday.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class RefreshTaskDto(
    val id: String,
    val title: String,
    val note: String? = null,
    val isImportant: Boolean,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class RefreshScheduleDto(
    val id: String,
    val taskId: String,
    val scheduleType: String,
    val startDate: String,
    val endDate: String? = null,
    val scheduledTime: String? = null,
    val intervalDays: Int? = null,
    val intervalAnchorDate: String? = null,
    val weekdaysMask: Int? = null,
    val reminderMinutesBefore: Int? = null,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class RefreshCompletionDto(
    val id: String,
    val taskId: String,
    val scheduleId: String? = null,
    val scheduledDate: String? = null,
    val completedDate: String,
    val completedAt: String,
    val titleSnapshot: String? = null,
    val isImportantSnapshot: Boolean
)

@Serializable
data class PlannerRefreshResponseDto(
    val tasks: List<RefreshTaskDto> = emptyList(),
    val schedules: List<RefreshScheduleDto> = emptyList(),
    val completions: List<RefreshCompletionDto> = emptyList()
)

@Serializable
data class CompleteTaskRequestDto(
    val plannerToday: String,
    val completedDate: String,
    val scheduleId: String? = null,
    val scheduledDate: String? = null
)

@Serializable
data class UndoTaskRequestDto(
    val scheduleId: String? = null,
    val scheduledDate: String? = null
)

@Serializable
data class CompleteTaskResponseDto(
    val id: String,
    val taskId: String,
    val scheduleId: String? = null,
    val scheduledDate: String? = null,
    val completedDate: String,
    val completedAt: String,
    val titleSnapshot: String? = null,
    val isImportantSnapshot: Boolean
)

@Serializable
data class TaskActionResponseDto(
    val success: Boolean = true
)
