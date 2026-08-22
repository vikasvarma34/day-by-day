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

@Serializable
data class CreateTaskScheduleRequestDto(
    val type: String,
    val startDate: String,
    val endDate: String? = null,
    val scheduledTime: String? = null,
    val intervalDays: Int? = null,
    val intervalAnchorDate: String? = null,
    val weekdaysMask: Int? = null,
    val reminderMinutesBefore: Int? = null
)

@Serializable
data class CreateTaskRequestDto(
    val id: String,
    val title: String,
    val note: String? = null,
    val isImportant: Boolean = false,
    val plannerToday: String? = null,
    val schedule: CreateTaskScheduleRequestDto? = null
)

@Serializable
data class TaskScheduleResponseDto(
    val id: String,
    val type: String,
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
data class TaskResponseDto(
    val id: String,
    val title: String,
    val note: String? = null,
    val isImportant: Boolean,
    val createdAt: String,
    val updatedAt: String,
    val schedules: List<TaskScheduleResponseDto> = emptyList()
)

@Serializable
data class CreateTaskResponseDto(
    val task: TaskResponseDto
)

@Serializable
data class DeleteTaskResponseDto(
    val deletedTaskId: String
)

@Serializable
data class UpdateTaskContentRequestDto(
    val title: String,
    val note: String? = null,
    val isImportant: Boolean
)

@Serializable
data class UpdateTaskResponseDto(
    val task: TaskResponseDto,
    val completions: List<CompleteTaskResponseDto> = emptyList()
)

@Serializable
data class UpdateTaskScheduleDto(
    val type: String,
    val startDate: String,
    val endDate: String? = null,
    val scheduledTime: String? = null,
    val reminderMinutesBefore: Int? = null,
    val intervalDays: Int? = null,
    val weekdaysMask: Int? = null
)

@Serializable
data class UpdateTaskSchedulePayloadDto(
    val plannerToday: String,
    val effectiveDate: String,
    val schedule: UpdateTaskScheduleDto
)

@Serializable
data class UpdateTaskRequestDto(
    val title: String,
    val note: String? = null,
    val isImportant: Boolean,
    val plannerToday: String,
    val effectiveDate: String,
    val schedule: UpdateTaskScheduleDto
)
