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

data class TaskWithScheduleRow(
    val taskId: String,
    val title: String,
    val note: String?,
    val isImportant: Boolean,
    val scheduleId: String,
    val scheduleType: String,
    val startDate: String,
    val endDate: String?,
    val scheduledTime: String?,
    val intervalDays: Int?,
    val intervalAnchorDate: String?,
    val weekdaysMask: Int?,
    val reminderMinutesBefore: Int?
)

data class LaterTaskRow(
    val taskId: String, val title: String, val note: String?, val isImportant: Boolean,
    val createdAt: String, val completionId: String?, val completedDate: String?, val completedAt: String?
)

@Dao
interface TaskDao {

    @Upsert
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Upsert
    suspend fun insert(task: TaskEntity)

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteById(taskId: String)

    @Query("SELECT * FROM tasks")
    suspend fun getAll(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TaskEntity?

    @Query("""SELECT t.id AS taskId, t.title AS title, t.note AS note, t.isImportant AS isImportant,
        t.createdAt AS createdAt, c.id AS completionId, c.completedDate AS completedDate, c.completedAt AS completedAt
        FROM tasks t LEFT JOIN completions c ON c.id = (
            SELECT directCompletion.id FROM completions directCompletion
            WHERE directCompletion.taskId = t.id
              AND directCompletion.scheduleId IS NULL
              AND directCompletion.scheduledDate IS NULL
            ORDER BY directCompletion.completedAt DESC, directCompletion.id DESC LIMIT 1
        )
        WHERE NOT EXISTS (SELECT 1 FROM schedules s WHERE s.taskId = t.id)
        ORDER BY CASE WHEN c.id IS NULL THEN 0 ELSE 1 END,
        CASE WHEN c.id IS NULL THEN t.createdAt ELSE c.completedAt END DESC, t.id DESC""")
    fun observeLaterTasks(): Flow<List<LaterTaskRow>>
}

@Dao
interface ScheduleDao {

    @Upsert
    suspend fun insertAll(schedules: List<ScheduleEntity>)

    @Upsert
    suspend fun insert(schedule: ScheduleEntity)

    @Query("DELETE FROM schedules")
    suspend fun deleteAll()

    @Query("DELETE FROM schedules WHERE taskId = :taskId")
    suspend fun deleteByTaskId(taskId: String)

    @Query("SELECT * FROM schedules WHERE taskId = :taskId")
    suspend fun getByTaskId(taskId: String): List<ScheduleEntity>

    @Query("DELETE FROM schedules WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("SELECT * FROM schedules")
    suspend fun getAll(): List<ScheduleEntity>

    @Query("SELECT * FROM schedules WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ScheduleEntity?

    @Query("""
        SELECT
            t.id AS taskId, t.title AS title, t.note AS note, t.isImportant AS isImportant,
            s.id AS scheduleId, s.scheduleType AS scheduleType, s.startDate AS startDate, s.endDate AS endDate,
            s.scheduledTime AS scheduledTime, s.intervalDays AS intervalDays,
            s.intervalAnchorDate AS intervalAnchorDate, s.weekdaysMask AS weekdaysMask,
            s.reminderMinutesBefore AS reminderMinutesBefore
        FROM schedules s
        INNER JOIN tasks t ON s.taskId = t.id
        WHERE s.startDate <= :dateString AND (s.endDate IS NULL OR s.endDate >= :dateString)
    """)
    fun observeCandidateSchedules(dateString: String): Flow<List<TaskWithScheduleRow>>

    @Query("""SELECT t.id AS taskId, t.title AS title, t.note AS note, t.isImportant AS isImportant,
        s.id AS scheduleId, s.scheduleType AS scheduleType, s.startDate AS startDate, s.endDate AS endDate,
        s.scheduledTime AS scheduledTime, s.intervalDays AS intervalDays, s.intervalAnchorDate AS intervalAnchorDate,
        s.weekdaysMask AS weekdaysMask, s.reminderMinutesBefore AS reminderMinutesBefore
        FROM schedules s INNER JOIN tasks t ON s.taskId = t.id
        WHERE t.isImportant = 1 AND s.startDate <= :endDate
        AND (s.endDate IS NULL OR s.endDate >= :startDate)""")
    fun observeImportantCandidateSchedules(startDate: String, endDate: String): Flow<List<TaskWithScheduleRow>>
}

@Dao
interface CompletionDao {

    @Upsert
    suspend fun insertAll(completions: List<CompletionEntity>)

    @Upsert
    suspend fun insert(completion: CompletionEntity)

    @Query("DELETE FROM completions")
    suspend fun deleteAll()

    @Query("DELETE FROM completions WHERE taskId = :taskId")
    suspend fun deleteByTaskId(taskId: String)

    @Query("DELETE FROM completions WHERE scheduleId IN (:scheduleIds)")
    suspend fun deleteByScheduleIds(scheduleIds: List<String>)

    @Query("SELECT * FROM completions")
    suspend fun getAll(): List<CompletionEntity>

    @Query("SELECT * FROM completions WHERE taskId = :taskId")
    suspend fun getByTaskId(taskId: String): List<CompletionEntity>

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

    @Query("SELECT scheduleId FROM completions WHERE scheduledDate = :dateString AND scheduleId IS NOT NULL")
    fun observeCompletedScheduleIdsForDate(dateString: String): Flow<List<String>>
}
