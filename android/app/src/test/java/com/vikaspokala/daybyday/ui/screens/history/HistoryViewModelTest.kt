package com.vikaspokala.daybyday.ui.screens.history

import com.vikaspokala.daybyday.data.local.planner.dao.CompletionDao
import com.vikaspokala.daybyday.data.local.planner.dao.HistoryCompletionRow
import com.vikaspokala.daybyday.data.local.planner.entity.CompletionEntity
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeCompletionDao : CompletionDao {
        val historyFlow = MutableSharedFlow<List<HistoryCompletionRow>>(replay = 1)
        var lastPlannerTodayArg: String? = null

        override suspend fun insertAll(completions: List<CompletionEntity>) = Unit
        override suspend fun insert(completion: CompletionEntity) = Unit
        override suspend fun deleteAll() = Unit
        override suspend fun getAll(): List<CompletionEntity> = emptyList()
        override suspend fun findLaterCompletion(taskId: String): CompletionEntity? = null
        override suspend fun findScheduledCompletion(taskId: String, scheduleId: String, scheduledDate: String): CompletionEntity? = null
        override suspend fun deleteLaterCompletion(taskId: String) = Unit
        override suspend fun deleteScheduledCompletion(taskId: String, scheduleId: String, scheduledDate: String) = Unit
        override suspend fun deleteByTaskId(taskId: String) = Unit
        override suspend fun deleteByScheduleIds(scheduleIds: List<String>) = Unit

        override fun observeHistory(plannerToday: String): Flow<List<HistoryCompletionRow>> {
            lastPlannerTodayArg = plannerToday
            return historyFlow
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun historyViewModel_usesTitleSnapshotAndFormatsItems() = runTest(testDispatcher) {
        val fakeDao = FakeCompletionDao()
        val referenceDate = LocalDate.of(2026, 8, 18)
        val zoneUtc = ZoneOffset.UTC

        val rows = listOf(
            HistoryCompletionRow(
                completionId = "comp-1",
                taskId = "task-1",
                scheduleId = "sched-1",
                scheduleType = "ONCE",
                completedDate = "2026-08-18",
                completedAt = "2026-08-18T14:30:00.000Z",
                titleSnapshot = "Dentist Appointment Snapshot",
                isImportantSnapshot = true
            ),
            HistoryCompletionRow(
                completionId = "comp-2",
                taskId = "task-2",
                scheduleId = null,
                scheduleType = null,
                completedDate = "2026-08-17",
                completedAt = "2026-08-17T09:15:00.000Z",
                titleSnapshot = "File Taxes Snapshot",
                isImportantSnapshot = true
            )
        )
        fakeDao.historyFlow.tryEmit(rows)

        val viewModel = HistoryViewModel(
            completionDao = fakeDao,
            referenceDate = referenceDate,
            zoneId = zoneUtc,
            scope = CoroutineScope(testDispatcher)
        )

        advanceUntilIdle()

        assertEquals("2026-08-18", fakeDao.lastPlannerTodayArg)
        val taskList = viewModel.tasks.value
        assertEquals(2, taskList.size)

        val first = taskList[0]
        assertEquals("task-1", first.id)
        assertEquals("comp-1", first.completionId)
        assertEquals("Dentist Appointment Snapshot", first.title)
        assertTrue(first.isImportant)
        assertEquals(LocalDate.of(2026, 8, 18), first.completedDate)
        assertEquals("2:30 PM", first.completedAtTime)
        assertEquals(14 * 60 + 30, first.completedAtMinutes)
        assertEquals("2026-08-18T14:30:00.000Z", first.completedAt)

        val second = taskList[1]
        assertEquals("task-2", second.id)
        assertEquals("comp-2", second.completionId)
        assertEquals("File Taxes Snapshot", second.title)
        assertTrue(second.isImportant)
        assertEquals(LocalDate.of(2026, 8, 17), second.completedDate)
        assertEquals("9:15 AM", second.completedAtTime)
        assertEquals(9 * 60 + 15, second.completedAtMinutes)
        assertEquals("2026-08-17T09:15:00.000Z", second.completedAt)
    }

    @Test
    fun historyViewModel_preservesSubMinuteOrderingAndTieBreaker() = runTest(testDispatcher) {
        val fakeDao = FakeCompletionDao()
        val referenceDate = LocalDate.of(2026, 8, 18)
        val zoneUtc = ZoneOffset.UTC

        // Two items on the same minute, but different seconds/milliseconds and IDs
        val rows = listOf(
            HistoryCompletionRow(
                completionId = "comp-10",
                taskId = "task-a",
                scheduleId = null,
                scheduleType = null,
                completedDate = "2026-08-18",
                completedAt = "2026-08-18T10:00:10.500Z",
                titleSnapshot = "Task Ten Seconds",
                isImportantSnapshot = true
            ),
            HistoryCompletionRow(
                completionId = "comp-20",
                taskId = "task-b",
                scheduleId = null,
                scheduleType = null,
                completedDate = "2026-08-18",
                completedAt = "2026-08-18T10:00:45.000Z",
                titleSnapshot = "Task Forty-Five Seconds",
                isImportantSnapshot = true
            )
        )
        fakeDao.historyFlow.tryEmit(rows)

        val viewModel = HistoryViewModel(
            completionDao = fakeDao,
            referenceDate = referenceDate,
            zoneId = zoneUtc,
            scope = CoroutineScope(testDispatcher)
        )
        advanceUntilIdle()

        val grouped = viewModel.getFilteredGroupedHistory(query = "")
        val itemsForDay = grouped[LocalDate.of(2026, 8, 18)] ?: emptyList()
        assertEquals(2, itemsForDay.size)
        // Later second should sort first
        assertEquals("comp-20", itemsForDay[0].completionId)
        assertEquals("comp-10", itemsForDay[1].completionId)
    }

    @Test
    fun historyViewModel_searchFilteringMatchesTitleSnapshot() = runTest(testDispatcher) {
        val fakeDao = FakeCompletionDao()
        val referenceDate = LocalDate.of(2026, 8, 18)
        val zoneUtc = ZoneOffset.UTC

        val rows = listOf(
            HistoryCompletionRow(
                completionId = "c1",
                taskId = "t1",
                scheduleId = "s1",
                scheduleType = "ONCE",
                completedDate = "2026-08-18",
                completedAt = "2026-08-18T10:00:00.000Z",
                titleSnapshot = "Morning Run",
                isImportantSnapshot = true
            ),
            HistoryCompletionRow(
                completionId = "c2",
                taskId = "t2",
                scheduleId = null,
                scheduleType = null,
                completedDate = "2026-08-17",
                completedAt = "2026-08-17T11:00:00.000Z",
                titleSnapshot = "Morning Meditation",
                isImportantSnapshot = true
            )
        )
        fakeDao.historyFlow.tryEmit(rows)

        val viewModel = HistoryViewModel(
            completionDao = fakeDao,
            referenceDate = referenceDate,
            zoneId = zoneUtc,
            scope = CoroutineScope(testDispatcher)
        )
        advanceUntilIdle()

        val filtered = viewModel.getFilteredGroupedHistory(query = "Meditation")
        assertEquals(1, filtered.keys.size)
        assertEquals(1, filtered[LocalDate.of(2026, 8, 17)]?.size)
        assertEquals("Morning Meditation", filtered[LocalDate.of(2026, 8, 17)]?.first()?.title)
    }
}
