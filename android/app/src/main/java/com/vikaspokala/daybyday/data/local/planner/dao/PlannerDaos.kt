package com.vikaspokala.daybyday.data.local.planner.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.vikaspokala.daybyday.data.local.planner.entity.CompletionEntity
import com.vikaspokala.daybyday.data.local.planner.entity.ScheduleEntity
import com.vikaspokala.daybyday.data.local.planner.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

data class HistoryCompletionRow(
    val completionId: String,
    val taskId: String,
    val scheduleId: String?,
    val scheduleType: String?,
    val completedDate: String,
    val completedAt: String,
    val titleSnapshot: String?,
    val isImportantSnapshot: Boolean
)

@Dao
interface TaskDao {

    @Upsert
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Upsert
    suspend fun insert(task: TaskEntity)

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()

    @Query("SELECT * FROM tasks")
    suspend fun getAll(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TaskEntity?
}

@Dao
interface ScheduleDao {

    @Upsert
    suspend fun insertAll(schedules: List<ScheduleEntity>)

    @Upsert
    suspend fun insert(schedule: ScheduleEntity)

    @Query("DELETE FROM schedules")
    suspend fun deleteAll()

    @Query("SELECT * FROM schedules")
    suspend fun getAll(): List<ScheduleEntity>

    @Query("SELECT * FROM schedules WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ScheduleEntity?
}

@Dao
interface CompletionDao {

    @Upsert
    suspend fun insertAll(completions: List<CompletionEntity>)

    @Upsert
    suspend fun insert(completion: CompletionEntity)

    @Query("DELETE FROM completions")
    suspend fun deleteAll()

    @Query("SELECT * FROM completions")
    suspend fun getAll(): List<CompletionEntity>

    @Query("SELECT * FROM completions WHERE taskId = :taskId AND scheduleId IS NULL AND scheduledDate IS NULL LIMIT 1")
    suspend fun findLaterCompletion(taskId: String): CompletionEntity?

    @Query("SELECT * FROM completions WHERE taskId = :taskId AND scheduleId = :scheduleId AND scheduledDate = :scheduledDate LIMIT 1")
    suspend fun findScheduledCompletion(taskId: String, scheduleId: String, scheduledDate: String): CompletionEntity?

    @Query("DELETE FROM completions WHERE taskId = :taskId AND scheduleId IS NULL AND scheduledDate IS NULL")
    suspend fun deleteLaterCompletion(taskId: String)

    @Query("DELETE FROM completions WHERE taskId = :taskId AND scheduleId = :scheduleId AND scheduledDate = :scheduledDate")
    suspend fun deleteScheduledCompletion(taskId: String, scheduleId: String, scheduledDate: String)

    @Query("""
        SELECT 
            c.id AS completionId,
            c.taskId AS taskId,
            c.scheduleId AS scheduleId,
            s.scheduleType AS scheduleType,
            c.completedDate AS completedDate,
            c.completedAt AS completedAt,
            c.titleSnapshot AS titleSnapshot,
            c.isImportantSnapshot AS isImportantSnapshot
        FROM completions c
        LEFT JOIN schedules s ON c.scheduleId = s.id
        WHERE c.isImportantSnapshot = 1
          AND c.completedDate <= :plannerToday
          AND (c.scheduleId IS NULL OR s.scheduleType = 'ONCE')
        ORDER BY c.completedDate DESC, c.completedAt DESC, c.id DESC
    """)
    fun observeHistory(plannerToday: String): Flow<List<HistoryCompletionRow>>
}
