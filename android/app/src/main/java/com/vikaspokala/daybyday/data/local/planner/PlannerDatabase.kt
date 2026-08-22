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
}
