package com.vikaspokala.daybyday.data.local.planner.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()

    @Query("SELECT * FROM tasks")
    suspend fun getAll(): List<TaskEntity>
}

@Dao
interface ScheduleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(schedules: List<ScheduleEntity>)

    @Query("DELETE FROM schedules")
    suspend fun deleteAll()

    @Query("SELECT * FROM schedules")
    suspend fun getAll(): List<ScheduleEntity>
}

@Dao
interface CompletionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(completions: List<CompletionEntity>)

    @Query("DELETE FROM completions")
    suspend fun deleteAll()

    @Query("SELECT * FROM completions")
    suspend fun getAll(): List<CompletionEntity>

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
