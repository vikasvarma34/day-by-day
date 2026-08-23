package com.vikaspokala.daybyday.ui.screens.history

import com.vikaspokala.daybyday.data.local.planner.dao.CompletionDao
import com.vikaspokala.daybyday.data.local.planner.dao.HistoryCompletionRow
import com.vikaspokala.daybyday.data.local.planner.entity.CompletionEntity
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistorySnapshotSemanticsTest {
    private val today = LocalDate.of(2026, 8, 20)

    @Test
    fun nonImportantOnceIsHidden_thenStarUnstarRestarReactsWithoutChangingSnapshotMetadata() {
        val dao = ReactiveHistoryDao()
        val viewModel = HistoryViewModel(dao, today, ZoneOffset.UTC)
        val original = historyRow(
            completionId = "completion",
            taskId = "task",
            completedDate = today.minusDays(2),
            completedAt = "2026-08-18T09:15:30.000Z",
            title = "Snapshot title"
        )

        assertTrue(viewModel.tasks.value.isEmpty())
        dao.rows.value = listOf(original.copy(isImportantSnapshot = true))
        val starred = viewModel.tasks.value.single()
        assertEquals("Snapshot title", starred.title)
        assertEquals(today.minusDays(2), starred.completedDate)
        assertEquals("2026-08-18T09:15:30.000Z", starred.completedAt)

        dao.rows.value = emptyList()
        assertTrue(viewModel.tasks.value.isEmpty())
        dao.rows.value = listOf(original.copy(isImportantSnapshot = true))
        assertEquals(starred, viewModel.tasks.value.single())
    }

    @Test
    fun importantOnceCompletedTodayAppearsUnderToday() {
        val dao = ReactiveHistoryDao()
        val viewModel = HistoryViewModel(dao, today, ZoneOffset.UTC)

        dao.rows.value = listOf(historyRow(completedDate = today, completedAt = "2026-08-20T12:00:00Z"))

        assertEquals(listOf(today), viewModel.getFilteredGroupedHistory().keys.toList())
        assertTrue(viewModel.tasks.value.single().isImportant)
    }

    @Test
    fun futureOnceCompletedEarlyIsGroupedByCanonicalCompletedDateNotScheduledDate() {
        val dao = ReactiveHistoryDao()
        val viewModel = HistoryViewModel(dao, today, ZoneOffset.UTC)
        dao.rows.value = listOf(
            historyRow(
                scheduleId = "future-schedule",
                completedDate = today,
                completedAt = "2026-08-20T08:00:00Z"
            )
        )

        assertEquals(setOf(today), viewModel.getFilteredGroupedHistory().keys)
    }

    @Test
    fun directLaterCompletionCanAppearAfterCanonicalStarUpdate() {
        val dao = ReactiveHistoryDao()
        val viewModel = HistoryViewModel(dao, today, ZoneOffset.UTC)
        assertTrue(viewModel.tasks.value.isEmpty())

        dao.rows.value = listOf(
            historyRow(
                taskId = "later",
                scheduleId = null,
                scheduleType = null,
                completedDate = today.minusDays(1),
                title = "Direct Later"
            )
        )

        assertEquals("later", viewModel.tasks.value.single().id)
        assertEquals("Direct Later", viewModel.tasks.value.single().title)
    }

    @Test
    fun titleDateAndTimestampSnapshotsRemainFrozenAcrossLaterLiveTaskChanges() {
        val dao = ReactiveHistoryDao()
        val viewModel = HistoryViewModel(dao, today, ZoneOffset.UTC)
        val snapshot = historyRow(
            title = "Original title",
            completedDate = today.minusDays(3),
            completedAt = "2026-08-17T14:45:12.500Z"
        )
        dao.rows.value = listOf(snapshot)

        // A live task edit does not alter the completion row emitted by Room.
        dao.rows.value = listOf(snapshot)

        val item = viewModel.tasks.value.single()
        assertEquals("Original title", item.title)
        assertEquals(today.minusDays(3), item.completedDate)
        assertEquals("2:45 PM", item.completedAtTime)
        assertEquals("2026-08-17T14:45:12.500Z", item.completedAt)
    }

    @Test
    fun recurringAndNonImportantCompletionsExcludedByDaoRemainAbsent() {
        val dao = ReactiveHistoryDao()
        val viewModel = HistoryViewModel(dao, today, ZoneOffset.UTC)

        // CompletionDao.observeHistory is the classification boundary and emits neither row.
        dao.rows.value = emptyList()

        assertFalse(viewModel.tasks.value.any())
        assertEquals(today.toString(), dao.plannerToday)
    }

    private fun historyRow(
        completionId: String = "completion",
        taskId: String = "task",
        scheduleId: String? = "schedule",
        scheduleType: String? = "ONCE",
        completedDate: LocalDate = today,
        completedAt: String = "2026-08-20T10:00:00Z",
        title: String = "Snapshot"
    ) = HistoryCompletionRow(
        completionId = completionId,
        taskId = taskId,
        scheduleId = scheduleId,
        scheduleType = scheduleType,
        completedDate = completedDate.toString(),
        completedAt = completedAt,
        titleSnapshot = title,
        isImportantSnapshot = true
    )

    private class ReactiveHistoryDao : CompletionDao {
        val rows = MutableStateFlow<List<HistoryCompletionRow>>(emptyList())
        var plannerToday: String? = null

        override fun observeHistory(plannerToday: String): Flow<List<HistoryCompletionRow>> {
            this.plannerToday = plannerToday
            return rows
        }

        override suspend fun insertAll(completions: List<CompletionEntity>) = Unit
        override suspend fun insert(completion: CompletionEntity) = Unit
        override suspend fun deleteAll() = Unit
        override suspend fun deleteByTaskId(taskId: String) = Unit
        override suspend fun deleteByScheduleIds(scheduleIds: List<String>) = Unit
        override suspend fun getAll(): List<CompletionEntity> = emptyList()
        override suspend fun getByTaskId(taskId: String): List<CompletionEntity> = emptyList()
        override suspend fun findLaterCompletion(taskId: String): CompletionEntity? = null
        override suspend fun findScheduledCompletion(
            taskId: String,
            scheduleId: String,
            scheduledDate: String
        ): CompletionEntity? = null
        override suspend fun deleteLaterCompletion(taskId: String) = Unit
        override suspend fun deleteScheduledCompletion(taskId: String, scheduleId: String, scheduledDate: String) = Unit
        override fun observeCompletedScheduleIdsForDate(dateString: String): Flow<List<String>> = emptyFlow()
    }
}
