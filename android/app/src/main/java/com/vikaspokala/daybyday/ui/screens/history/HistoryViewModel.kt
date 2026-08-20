package com.vikaspokala.daybyday.ui.screens.history

import java.time.LocalDate
import androidx.lifecycle.ViewModel
import com.vikaspokala.daybyday.ui.fake.InMemoryPlannerDatabase
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
private class DerivedHistoryStateFlow(
    private val database: InMemoryPlannerDatabase,
    private val plannerToday: LocalDate
) : StateFlow<List<HistoryTaskItem>> {
    override val value: List<HistoryTaskItem> get() = database.getHistoryTasks(plannerToday)
    override val replayCache: List<List<HistoryTaskItem>> get() = listOf(value)
    override suspend fun collect(collector: FlowCollector<List<HistoryTaskItem>>): Nothing {
        database.observeHistoryTasks(plannerToday).collect(collector)
        awaitCancellation()
    }
}

class HistoryViewModel(
    private val database: InMemoryPlannerDatabase = InMemoryPlannerDatabase.defaultDatabase,
    private val plannerTodayProvider: () -> LocalDate = { LocalDate.now() }
) : ViewModel() {

    constructor(referenceDate: LocalDate) : this(
        database = InMemoryPlannerDatabase.defaultDatabase,
        plannerTodayProvider = { referenceDate }
    )

    constructor(database: InMemoryPlannerDatabase, referenceDate: LocalDate) : this(
        database = database,
        plannerTodayProvider = { referenceDate }
    )

    val tasks: StateFlow<List<HistoryTaskItem>> = DerivedHistoryStateFlow(database, plannerTodayProvider())

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
            val dateTasks = grouped[date].orEmpty().sortedByDescending { it.completedAtMinutes }
            sortedMap[date] = dateTasks
        }
        return sortedMap
    }
}
