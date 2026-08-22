package com.vikaspokala.daybyday.ui.screens.history

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vikaspokala.daybyday.data.local.planner.PlannerDatabase
import com.vikaspokala.daybyday.data.local.planner.dao.CompletionDao
import com.vikaspokala.daybyday.data.local.planner.dao.HistoryCompletionRow
import com.vikaspokala.daybyday.data.local.planner.entity.CompletionEntity
import com.vikaspokala.daybyday.ui.fake.InMemoryPlannerDatabase
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class HistoryViewModel(
    completionDao: CompletionDao,
    private val plannerTodayProvider: () -> LocalDate = { LocalDate.now() },
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
) : ViewModel() {

    constructor(
        completionDao: CompletionDao,
        referenceDate: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
        scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
    ) : this(
        completionDao = completionDao,
        plannerTodayProvider = { referenceDate },
        zoneId = zoneId,
        scope = scope
    )

    constructor(
        database: InMemoryPlannerDatabase,
        referenceDate: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
    ) : this(
        completionDao = object : CompletionDao {
            override suspend fun insertAll(completions: List<CompletionEntity>) = Unit
            override suspend fun insert(completion: CompletionEntity) = Unit
            override suspend fun deleteAll() = Unit
            override suspend fun deleteByTaskId(taskId: String) = Unit
            override suspend fun getAll(): List<CompletionEntity> = emptyList()
            override suspend fun findLaterCompletion(taskId: String): CompletionEntity? = null
            override suspend fun findScheduledCompletion(taskId: String, scheduleId: String, scheduledDate: String): CompletionEntity? = null
            override suspend fun deleteLaterCompletion(taskId: String) = Unit
            override suspend fun deleteScheduledCompletion(taskId: String, scheduleId: String, scheduledDate: String) = Unit
            override suspend fun deleteByScheduleIds(scheduleIds: List<String>) = Unit
            override fun observeHistory(plannerToday: String): Flow<List<HistoryCompletionRow>> {
                return database.observeHistoryTasks(referenceDate).map { items ->
                    items.map { item ->
                        val hours = item.completedAtMinutes / 60
                        val mins = item.completedAtMinutes % 60
                        val timeIso = String.format(Locale.US, "%02d:%02d:00.000Z", hours, mins)
                        HistoryCompletionRow(
                            completionId = item.id,
                            taskId = item.id,
                            scheduleId = null,
                            scheduleType = "ONCE",
                            completedDate = item.completedDate.toString(),
                            completedAt = "${item.completedDate}T$timeIso",
                            titleSnapshot = item.title,
                            isImportantSnapshot = item.isImportant
                        )
                    }
                }
            }
        },
        plannerTodayProvider = { referenceDate },
        zoneId = zoneId,
        scope = scope
    )

    val tasks: StateFlow<List<HistoryTaskItem>> = completionDao
        .observeHistory(plannerToday = plannerTodayProvider().toString())
        .map { rows ->
            rows.map { row -> row.toHistoryTaskItem(zoneId) }
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun getFilteredGroupedHistory(
        query: String = _searchQuery.value,
        taskList: List<HistoryTaskItem> = tasks.value,
        plannerToday: LocalDate = plannerTodayProvider()
    ): Map<LocalDate, List<HistoryTaskItem>> {
        val trimmedQuery = query.trim()
        val filteredTasks = if (trimmedQuery.isEmpty()) {
            taskList
        } else {
            taskList.filter { it.title.contains(trimmedQuery, ignoreCase = true) }
        }

        val grouped = filteredTasks.groupBy { it.completedDate }
        val sortedDates = grouped.keys.sortedDescending()

        val sortedMap = LinkedHashMap<LocalDate, List<HistoryTaskItem>>()
        for (date in sortedDates) {
            val dateTasks = grouped[date].orEmpty().sortedWith(
                compareByDescending<HistoryTaskItem> { it.completedAt }
                    .thenByDescending { it.completionId }
            )
            sortedMap[date] = dateTasks
        }
        return sortedMap
    }

    class Factory(
        private val context: Context,
        private val plannerTodayProvider: () -> LocalDate = { LocalDate.now() },
        private val zoneId: ZoneId = ZoneId.systemDefault()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val database = PlannerDatabase.getInstance(context)
            return HistoryViewModel(
                completionDao = database.completionDao(),
                plannerTodayProvider = plannerTodayProvider,
                zoneId = zoneId
            ) as T
        }
    }
}

private fun HistoryCompletionRow.toHistoryTaskItem(zoneId: ZoneId): HistoryTaskItem {
    val instant = try {
        Instant.parse(completedAt)
    } catch (_: Exception) {
        Instant.EPOCH
    }
    val localTime = instant.atZone(zoneId).toLocalTime()
    val timeStr = localTime.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
    val minutes = localTime.hour * 60 + localTime.minute

    return HistoryTaskItem(
        id = taskId,
        completionId = completionId,
        title = titleSnapshot ?: "",
        isImportant = isImportantSnapshot,
        completedDate = try { LocalDate.parse(completedDate) } catch (_: Exception) { LocalDate.MIN },
        completedAtTime = timeStr,
        completedAtMinutes = minutes,
        completedAt = completedAt
    )
}
