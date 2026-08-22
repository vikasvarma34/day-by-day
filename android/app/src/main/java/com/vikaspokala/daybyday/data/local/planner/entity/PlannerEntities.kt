package com.vikaspokala.daybyday.data.local.planner.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.vikaspokala.daybyday.data.remote.dto.RefreshCompletionDto
import com.vikaspokala.daybyday.data.remote.dto.RefreshScheduleDto
import com.vikaspokala.daybyday.data.remote.dto.RefreshTaskDto

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val note: String?,
    val isImportant: Boolean,
    val createdAt: String,
    val updatedAt: String
)

@Entity(
    tableName = "schedules",
    indices = [Index(value = ["taskId"])]
)
data class ScheduleEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val scheduleType: String,
    val startDate: String,
    val endDate: String?,
    val scheduledTime: String?,
    val intervalDays: Int?,
    val intervalAnchorDate: String?,
    val weekdaysMask: Int?,
    val reminderMinutesBefore: Int?,
    val createdAt: String,
    val updatedAt: String
)

@Entity(
    tableName = "completions",
    indices = [Index(value = ["taskId"])]
)
data class CompletionEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val scheduleId: String?,
    val scheduledDate: String?,
    val completedDate: String,
    val completedAt: String,
    val titleSnapshot: String?,
    val isImportantSnapshot: Boolean
)

fun RefreshTaskDto.toEntity(): TaskEntity = TaskEntity(
    id = id,
    title = title,
    note = note,
    isImportant = isImportant,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun RefreshScheduleDto.toEntity(): ScheduleEntity = ScheduleEntity(
    id = id,
    taskId = taskId,
    scheduleType = scheduleType,
    startDate = startDate,
    endDate = endDate,
    scheduledTime = scheduledTime,
    intervalDays = intervalDays,
    intervalAnchorDate = intervalAnchorDate,
    weekdaysMask = weekdaysMask,
    reminderMinutesBefore = reminderMinutesBefore,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun RefreshCompletionDto.toEntity(): CompletionEntity = CompletionEntity(
    id = id,
    taskId = taskId,
    scheduleId = scheduleId,
    scheduledDate = scheduledDate,
    completedDate = completedDate,
    completedAt = completedAt,
    titleSnapshot = titleSnapshot,
    isImportantSnapshot = isImportantSnapshot
)
