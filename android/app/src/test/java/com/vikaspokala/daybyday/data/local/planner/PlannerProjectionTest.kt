package com.vikaspokala.daybyday.data.local.planner

import com.vikaspokala.daybyday.data.local.planner.dao.*
import com.vikaspokala.daybyday.data.local.planner.entity.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlannerProjectionTest {
    private class Schedules : ScheduleDao {
        val rows = MutableStateFlow<List<TaskWithScheduleRow>>(emptyList())
        override fun observeCandidateSchedules(dateString: String) = rows
        override fun observeImportantCandidateSchedules(startDate: String, endDate: String) = rows
        override suspend fun insertAll(schedules: List<ScheduleEntity>) = Unit
        override suspend fun insert(schedule: ScheduleEntity) = Unit
        override suspend fun deleteAll() = Unit
        override suspend fun deleteByTaskId(taskId: String) = Unit
        override suspend fun getByTaskId(taskId: String) = emptyList<ScheduleEntity>()
        override suspend fun deleteByIds(ids: List<String>) = Unit
        override suspend fun getAll() = emptyList<ScheduleEntity>()
        override suspend fun getById(id: String) = null
    }

    private class Tasks : TaskDao {
        val rows = MutableStateFlow<List<LaterTaskRow>>(emptyList())
        override fun observeLaterTasks() = rows
        override suspend fun insertAll(tasks: List<TaskEntity>) = Unit
        override suspend fun insert(task: TaskEntity) = Unit
        override suspend fun deleteAll() = Unit
        override suspend fun deleteById(taskId: String) = Unit
        override suspend fun getAll() = emptyList<TaskEntity>()
        override suspend fun getById(id: String) = null
    }

    private class Db(val s: Schedules, val t: Tasks) : PlannerDatabase() {
        override fun scheduleDao() = s
        override fun taskDao() = t
        override fun completionDao(): CompletionDao = error("unused")
        override fun createInvalidationTracker() = error("unused")
        override fun createOpenHelper(config: androidx.room.DatabaseConfiguration) = error("unused")
        override fun clearAllTables() = Unit
    }

    private fun row(type: String, start: String, end: String? = null, interval: Int? = null,
                    anchor: String? = null, mask: Int? = null, taskId: String = "task", scheduleId: String = "schedule") = TaskWithScheduleRow(
        taskId, "Task", null, true, scheduleId, type, start, end, null, interval, anchor, mask, null
    )

    @Test fun importantDates_match_once_interval_weekdays_and_inclusive_end() = runTest {
        val schedules = Schedules()
        schedules.rows.value = listOf(
            row("ONCE", "2026-08-10", "2026-08-10"),
            row("INTERVAL_DAYS", "2026-08-10", "2026-08-16", 2, "2026-08-10"),
            row("WEEKDAYS", "2026-08-10", "2026-08-16", mask = 1 shl 6)
        )
        val dates = Db(schedules, Tasks()).observeImportantDates(
            LocalDate.parse("2026-08-10"), LocalDate.parse("2026-08-16")
        ).first()
        assertTrue(LocalDate.parse("2026-08-10") in dates)
        assertTrue(LocalDate.parse("2026-08-16") in dates)
        assertEquals(4, dates.size)
    }

    @Test fun importantDates_reacts_to_important_schedule_changes() = runTest {
        val schedules = Schedules()
        val flow = Db(schedules, Tasks()).observeImportantDates(LocalDate.parse("2026-08-10"), LocalDate.parse("2026-08-12"))
        assertTrue(flow.first().isEmpty())
        schedules.rows.value = listOf(row("ONCE", "2026-08-11", "2026-08-11"))
        assertEquals(setOf(LocalDate.parse("2026-08-11")), flow.first())
    }

    @Test fun importantDates_excludesNonImportantCandidatesAndHandlesReversedRange() = runTest {
        val schedules = Schedules()
        val db = Db(schedules, Tasks())
        assertTrue(db.observeImportantDates(LocalDate.parse("2026-08-12"), LocalDate.parse("2026-08-10")).first().isEmpty())
        // The DAO query excludes non-important tasks, so no candidate row means no dot.
        assertTrue(db.observeImportantDates(LocalDate.parse("2026-08-10"), LocalDate.parse("2026-08-12")).first().isEmpty())
    }

    @Test fun importantDates_respectRecurrenceSegmentTransition() = runTest {
        val schedules = Schedules()
        schedules.rows.value = listOf(
            row("INTERVAL_DAYS", "2026-08-10", "2026-08-11", interval = 1, anchor = "2026-08-10", scheduleId = "old"),
            row("WEEKDAYS", "2026-08-12", "2026-08-16", mask = 1 shl 2, scheduleId = "new")
        )
        val dates = Db(schedules, Tasks()).observeImportantDates(LocalDate.parse("2026-08-10"), LocalDate.parse("2026-08-16")).first()
        assertEquals(setOf(LocalDate.parse("2026-08-10"), LocalDate.parse("2026-08-11"), LocalDate.parse("2026-08-12")), dates)
    }

    @Test fun importantDates_reactToImportantToggleAndScheduleChange() = runTest {
        val schedules = Schedules()
        val flow = Db(schedules, Tasks()).observeImportantDates(LocalDate.parse("2026-08-10"), LocalDate.parse("2026-08-12"))
        assertTrue(flow.first().isEmpty()) // Important false: filtered by the DAO query.
        schedules.rows.value = listOf(row("ONCE", "2026-08-10", "2026-08-10"))
        assertEquals(setOf(LocalDate.parse("2026-08-10")), flow.first())
        schedules.rows.value = listOf(row("ONCE", "2026-08-12", "2026-08-12"))
        assertEquals(setOf(LocalDate.parse("2026-08-12")), flow.first())
    }

    @Test fun laterRow_mapsDirectCompletionAndRetainsNote() {
        val projection = LaterTaskRow("t", "Title", "hidden note", true, "2026-08-01T00:00:00Z", "c", "2026-08-02", "2026-08-02T01:00:00Z").toProjection()
        assertEquals("hidden note", projection.note)
        assertEquals("c", projection.directCompletionId)
        assertEquals("2026-08-02", projection.completedDate)
    }

    @Test fun laterProjection_preservesActiveAndCompletedOrderingFromDao() = runTest {
        val tasks = Tasks()
        tasks.rows.value = listOf(
            LaterTaskRow("new", "New", null, false, "2026-08-03T00:00:00Z", null, null, null),
            LaterTaskRow("old", "Old", null, false, "2026-08-01T00:00:00Z", null, null, null),
            LaterTaskRow("recentDone", "Recent", null, false, "2026-08-01T00:00:00Z", "c2", "2026-08-03", "2026-08-03T01:00:00Z"),
            LaterTaskRow("oldDone", "Old done", null, false, "2026-08-01T00:00:00Z", "c1", "2026-08-02", "2026-08-02T01:00:00Z")
        )
        assertEquals(listOf("new", "old", "recentDone", "oldDone"), Db(Schedules(), tasks).observeLaterTasks().first().map { it.taskId })
    }

    @Test fun laterProjection_reactsToContentImportantScheduleAndUndoChanges() = runTest {
        val tasks = Tasks()
        val flow = Db(Schedules(), tasks).observeLaterTasks()
        tasks.rows.value = listOf(LaterTaskRow("t", "Title", "note", false, "2026-08-01T00:00:00Z", null, null, null))
        assertEquals("note", flow.first().single().note)
        tasks.rows.value = listOf(LaterTaskRow("t", "Updated", "updated note", true, "2026-08-01T00:00:00Z", "c", "2026-08-02", "2026-08-02T01:00:00Z"))
        assertEquals(true, flow.first().single().isImportant)
        assertEquals("c", flow.first().single().directCompletionId)
        tasks.rows.value = emptyList() // Later -> scheduled, and scheduled tasks / scheduled completions are absent from this query.
        assertTrue(flow.first().isEmpty())
        tasks.rows.value = listOf(LaterTaskRow("t", "Updated", "updated note", true, "2026-08-01T00:00:00Z", null, null, null))
        assertEquals(null, flow.first().single().directCompletionId) // Undo direct-Later completion returns active Later.
    }
}
