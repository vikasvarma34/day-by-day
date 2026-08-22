package com.vikaspokala.daybyday.data.local.planner

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.vikaspokala.daybyday.data.local.planner.dao.CompletionDao
import com.vikaspokala.daybyday.data.local.planner.dao.ScheduleDao
import com.vikaspokala.daybyday.data.local.planner.dao.TaskDao
import com.vikaspokala.daybyday.data.local.planner.entity.CompletionEntity
import com.vikaspokala.daybyday.data.local.planner.entity.ScheduleEntity
import com.vikaspokala.daybyday.data.local.planner.entity.TaskEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

@Database(
    entities = [
        TaskEntity::class,
        ScheduleEntity::class,
        CompletionEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PlannerDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun completionDao(): CompletionDao

    open suspend fun replaceSnapshot(
        tasks: List<TaskEntity>,
        schedules: List<ScheduleEntity>,
        completions: List<CompletionEntity>
    ) {
        withTransaction {
            completionDao().deleteAll()
            scheduleDao().deleteAll()
            taskDao().deleteAll()

            taskDao().insertAll(tasks)
            scheduleDao().insertAll(schedules)
            completionDao().insertAll(completions)
        }
    }

    open suspend fun applyCreateTask(task: TaskEntity, schedules: List<ScheduleEntity>) {
        withTransaction {
            taskDao().insert(task)
            if (schedules.isNotEmpty()) {
                scheduleDao().insertAll(schedules)
            }
        }
    }

    open suspend fun applyComplete(completion: CompletionEntity) {
        completionDao().insert(completion)
    }

    open suspend fun applyUndo(
        taskId: String,
        scheduleId: String?,
        scheduledDate: String?
    ) {
        if (scheduleId == null) {
            completionDao().deleteLaterCompletion(taskId)
        } else if (scheduledDate != null) {
            completionDao().deleteScheduledCompletion(taskId, scheduleId, scheduledDate)
        }
    }

    open suspend fun applyDeleteTask(taskId: String) {
        withTransaction {
            completionDao().deleteByTaskId(taskId)
            scheduleDao().deleteByTaskId(taskId)
            taskDao().deleteById(taskId)
        }
    }

    open suspend fun applyUpdateTaskContent(
        task: TaskEntity,
        completions: List<CompletionEntity>
    ) {
        withTransaction {
            taskDao().insert(task)
            if (completions.isNotEmpty()) {
                completionDao().insertAll(completions)
            }
        }
    }

    open suspend fun applyUpdateTaskSchedule(
        task: TaskEntity,
        schedules: List<ScheduleEntity>,
        completions: List<CompletionEntity>
    ) {
        withTransaction {
            taskDao().insert(task)
            val currentSchedules = scheduleDao().getByTaskId(task.id)
            val responseScheduleIds = schedules.map { it.id }.toSet()
            val staleScheduleIds = currentSchedules.map { it.id }.filter { it !in responseScheduleIds }
            if (staleScheduleIds.isNotEmpty()) {
                completionDao().deleteByScheduleIds(staleScheduleIds)
                scheduleDao().deleteByIds(staleScheduleIds)
            }
            if (schedules.isNotEmpty()) {
                completionDao().deleteLaterCompletion(task.id)
                scheduleDao().insertAll(schedules)
            }
            if (completions.isNotEmpty()) {
                completionDao().insertAll(completions)
            }
        }
    }

    open fun observeScheduledOccurrences(date: LocalDate): Flow<List<ScheduledOccurrence>> {
        val dateString = date.toString()
        val candidateSchedulesFlow = scheduleDao().observeCandidateSchedules(dateString)
        val completedScheduleIdsFlow = completionDao().observeCompletedScheduleIdsForDate(dateString)

        return combine(candidateSchedulesFlow, completedScheduleIdsFlow) { candidates, completedIds ->
            val completedSet = completedIds.toSet()
            candidates
                .filter { isScheduleOccurringOnDate(it, date) }
                .map { row ->
                    ScheduledOccurrence(
                        taskId = row.taskId,
                        scheduleId = row.scheduleId,
                        title = row.title,
                        note = row.note,
                        isImportant = row.isImportant,
                        scheduleType = row.scheduleType,
                        startDate = row.startDate,
                        endDate = row.endDate,
                        scheduledTime = row.scheduledTime,
                        intervalDays = row.intervalDays,
                        intervalAnchorDate = row.intervalAnchorDate,
                        weekdaysMask = row.weekdaysMask,
                        reminderMinutesBefore = row.reminderMinutesBefore,
                        scheduledDate = dateString,
                        isCompleted = completedSet.contains(row.scheduleId)
                    )
                }
        }
    }

    companion object {
        private const val DATABASE_NAME = "planner.db"

        @Volatile
        private var instance: PlannerDatabase? = null

        fun getInstance(context: Context): PlannerDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PlannerDatabase::class.java,
                    DATABASE_NAME
                ).build().also { instance = it }
            }
        }
    }

    open fun observeImportantDates(startDate: LocalDate, endDate: LocalDate): Flow<Set<LocalDate>> {
        if (endDate.isBefore(startDate)) return kotlinx.coroutines.flow.flowOf(emptySet())
        return scheduleDao().observeImportantCandidateSchedules(startDate.toString(), endDate.toString()).map { rows ->
            val dates = linkedSetOf<LocalDate>()
            var date = startDate
            while (!date.isAfter(endDate)) {
                if (rows.any { isScheduleOccurringOnDate(it, date) }) dates += date
                date = date.plusDays(1)
            }
            dates
        }
    }

    open fun observeLaterTasks(): Flow<List<LaterTaskProjection>> =
        taskDao().observeLaterTasks().map { rows -> rows.map { it.toProjection() } }
}
