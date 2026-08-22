package com.vikaspokala.daybyday.data.repository

import com.vikaspokala.daybyday.data.local.planner.PlannerDatabase
import com.vikaspokala.daybyday.data.local.planner.dao.CompletionDao
import com.vikaspokala.daybyday.data.local.planner.dao.HistoryCompletionRow
import com.vikaspokala.daybyday.data.local.planner.dao.ScheduleDao
import com.vikaspokala.daybyday.data.local.planner.dao.TaskDao
import com.vikaspokala.daybyday.data.local.planner.dao.TaskWithScheduleRow
import com.vikaspokala.daybyday.data.local.planner.dao.LaterTaskRow
import com.vikaspokala.daybyday.data.local.planner.entity.CompletionEntity
import com.vikaspokala.daybyday.data.local.planner.entity.ScheduleEntity
import com.vikaspokala.daybyday.data.local.planner.entity.TaskEntity
import com.vikaspokala.daybyday.data.remote.api.PlannerApi
import com.vikaspokala.daybyday.data.remote.dto.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PlannerRepositoryOccurrenceTest {

    private class FakeScheduleDao : ScheduleDao {
        val candidates = MutableStateFlow<List<TaskWithScheduleRow>>(emptyList())
        override fun observeCandidateSchedules(dateString: String): Flow<List<TaskWithScheduleRow>> = candidates
        override suspend fun insertAll(schedules: List<ScheduleEntity>) = Unit
        override suspend fun insert(schedule: ScheduleEntity) = Unit
        override suspend fun deleteAll() = Unit
        override suspend fun deleteByTaskId(taskId: String) = Unit
        override suspend fun getByTaskId(taskId: String): List<ScheduleEntity> = emptyList()
        override suspend fun deleteByIds(ids: List<String>) = Unit
        override suspend fun getAll(): List<ScheduleEntity> = emptyList()
        override suspend fun getById(id: String): ScheduleEntity? = null
        override fun observeImportantCandidateSchedules(startDate: String, endDate: String): Flow<List<TaskWithScheduleRow>> = candidates
    }

    private class FakeCompletionDao : CompletionDao {
        val completedIds = MutableStateFlow<List<String>>(emptyList())
        override fun observeCompletedScheduleIdsForDate(dateString: String): Flow<List<String>> = completedIds
        
        override suspend fun insertAll(completions: List<CompletionEntity>) = Unit
        override suspend fun insert(completion: CompletionEntity) = Unit
        override suspend fun deleteAll() = Unit
        override suspend fun deleteByTaskId(taskId: String) = Unit
        override suspend fun deleteByScheduleIds(scheduleIds: List<String>) = Unit
        override suspend fun getAll(): List<CompletionEntity> = emptyList()
        override suspend fun findLaterCompletion(taskId: String): CompletionEntity? = null
        override suspend fun findScheduledCompletion(taskId: String, scheduleId: String, scheduledDate: String): CompletionEntity? = null
        override suspend fun deleteLaterCompletion(taskId: String) = Unit
        override suspend fun deleteScheduledCompletion(taskId: String, scheduleId: String, scheduledDate: String) = Unit
        override fun observeHistory(plannerToday: String): Flow<List<HistoryCompletionRow>> = MutableStateFlow(emptyList())
    }

    private class FakePlannerDatabase(
        val scheduleDao: FakeScheduleDao,
        val completionDao: FakeCompletionDao
    ) : PlannerDatabase() {
        override fun scheduleDao(): ScheduleDao = scheduleDao
        override fun completionDao(): CompletionDao = completionDao
        override fun taskDao(): TaskDao = error("Not needed")
        override fun createInvalidationTracker() = error("Not needed")
        override fun createOpenHelper(config: androidx.room.DatabaseConfiguration) = error("Not needed")
        override fun clearAllTables() = Unit
    }

    private class DummyPlannerApi : PlannerApi {
        override suspend fun createTask(a: String, r: CreateTaskRequestDto) = error("Stub")
        override suspend fun getRefresh(a: String) = error("Stub")
        override suspend fun completeTask(a: String, t: String, r: CompleteTaskRequestDto) = error("Stub")
        override suspend fun undoTask(a: String, t: String, r: UndoTaskRequestDto) = error("Stub")
        override suspend fun deleteTask(a: String, t: String) = error("Stub")
        override suspend fun updateTaskContent(a: String, t: String, r: UpdateTaskContentRequestDto) = error("Stub")
        override suspend fun updateTaskSchedule(a: String, t: String, r: UpdateTaskSchedulePayloadDto) = error("Stub")
        override suspend fun updateTask(a: String, t: String, r: UpdateTaskRequestDto) = error("Stub")
    }

    @Test
    fun observeScheduledOccurrences_emitsCorrectlyBasedOnKotlinMath() = runTest {
        val scheduleDao = FakeScheduleDao()
        val completionDao = FakeCompletionDao()
        val db = FakePlannerDatabase(scheduleDao, completionDao)
        val repository = PlannerRepository(db, { "token" }, DummyPlannerApi())

        // Tuesday, August 25, 2026. dayOfWeek.value = 2. mask = 1 shl 2 = 4.
        val testDate = LocalDate.parse("2026-08-25") 

        scheduleDao.candidates.value = listOf(
            TaskWithScheduleRow(
                taskId = "t1", title = "Task 1", note = null, isImportant = false,
                scheduleId = "s1", scheduleType = "ONCE", startDate = "2026-08-25", endDate = null,
                scheduledTime = null, intervalDays = null, intervalAnchorDate = null, weekdaysMask = null, reminderMinutesBefore = null
            ),
            TaskWithScheduleRow(
                taskId = "t2", title = "Task 2", note = null, isImportant = false,
                scheduleId = "s2", scheduleType = "ONCE", startDate = "2026-08-24", endDate = null,
                scheduledTime = null, intervalDays = null, intervalAnchorDate = null, weekdaysMask = null, reminderMinutesBefore = null
            ),
            TaskWithScheduleRow(
                taskId = "t3", title = "Task 3", note = null, isImportant = false,
                scheduleId = "s3", scheduleType = "INTERVAL_DAYS", startDate = "2026-08-20", endDate = null,
                scheduledTime = null, intervalDays = 2, intervalAnchorDate = "2026-08-23", weekdaysMask = null, reminderMinutesBefore = null
            ),
            TaskWithScheduleRow(
                taskId = "t4", title = "Task 4", note = null, isImportant = false,
                scheduleId = "s4", scheduleType = "WEEKDAYS", startDate = "2026-08-20", endDate = null,
                scheduledTime = null, intervalDays = null, intervalAnchorDate = null, weekdaysMask = 2, reminderMinutesBefore = null // Tuesday (bit 1)
            ),
            TaskWithScheduleRow(
                taskId = "t5", title = "Task 5", note = null, isImportant = false,
                scheduleId = "s5", scheduleType = "WEEKDAYS", startDate = "2026-08-20", endDate = null,
                scheduledTime = null, intervalDays = null, intervalAnchorDate = null, weekdaysMask = 1, reminderMinutesBefore = null // Monday (bit 0)
            ),
            TaskWithScheduleRow(
                taskId = "t6", title = "Task 6", note = null, isImportant = false,
                scheduleId = "s6", scheduleType = "WEEKDAYS", startDate = "2026-08-20", endDate = null,
                scheduledTime = null, intervalDays = null, intervalAnchorDate = null, weekdaysMask = 32, reminderMinutesBefore = null // Saturday (bit 5)
            ),
            TaskWithScheduleRow(
                taskId = "t7", title = "Task 7", note = null, isImportant = false,
                scheduleId = "s7", scheduleType = "WEEKDAYS", startDate = "2026-08-20", endDate = null,
                scheduledTime = null, intervalDays = null, intervalAnchorDate = null, weekdaysMask = 64, reminderMinutesBefore = null // Sunday (bit 6)
            )
        )

        completionDao.completedIds.value = listOf("s3")

        val result = repository.observeScheduledOccurrences(testDate).first()

        assertEquals(3, result.size)
        
        val r1 = result.find { it.scheduleId == "s1" }!!
        assertEquals(false, r1.isCompleted)

        val r3 = result.find { it.scheduleId == "s3" }!!
        assertEquals(true, r3.isCompleted)

        val r4 = result.find { it.scheduleId == "s4" }!!
        assertEquals(false, r4.isCompleted) // Tuesday

        val r5 = result.find { it.scheduleId == "s5" }
        assertEquals(null, r5) // Monday, filtered out
    }

    @Test
    fun observeScheduledOccurrences_futureOnceCompletedEarly_isMarkedCompleted() = runTest {
        val scheduleDao = FakeScheduleDao()
        val completionDao = FakeCompletionDao()
        val db = FakePlannerDatabase(scheduleDao, completionDao)
        val repository = PlannerRepository(db, { "token" }, DummyPlannerApi())

        val testDate = LocalDate.parse("2026-08-25") 

        scheduleDao.candidates.value = listOf(
            TaskWithScheduleRow(
                taskId = "t1", title = "Task 1", note = null, isImportant = false,
                scheduleId = "s1", scheduleType = "ONCE", startDate = "2026-08-25", endDate = null,
                scheduledTime = null, intervalDays = null, intervalAnchorDate = null, weekdaysMask = null, reminderMinutesBefore = null
            )
        )

        // It was completed early on 2026-08-24, but its scheduledDate is 2026-08-25
        completionDao.completedIds.value = listOf("s1")

        val result = repository.observeScheduledOccurrences(testDate).first()

        assertEquals(1, result.size)
        val r1 = result.first()
        assertEquals(true, r1.isCompleted)
    }
}
