package com.vikaspokala.daybyday.ui.screens.history

import java.time.LocalDate
import androidx.lifecycle.ViewModel
import com.vikaspokala.daybyday.ui.fake.InMemoryPlannerDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

class HistoryViewModel(
    private val database: InMemoryPlannerDatabase = InMemoryPlannerDatabase.defaultDatabase,
    private val plannerTodayProvider: () -> LocalDate = { LocalDate.now() },
    scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
) : ViewModel() {

    constructor(referenceDate: LocalDate) : this(
        database = InMemoryPlannerDatabase.defaultDatabase,
        plannerTodayProvider = { referenceDate }
    )

    constructor(database: InMemoryPlannerDatabase, referenceDate: LocalDate) : this(
        database = database,
        plannerTodayProvider = { referenceDate }
    )

    val tasks: StateFlow<List<HistoryTaskItem>> = database.observeHistoryTasks(plannerTodayProvider())
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = database.getHistoryTasks(plannerTodayProvider())
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
            val dateTasks = grouped[date].orEmpty().sortedByDescending { it.completedAtMinutes }
            sortedMap[date] = dateTasks
        }
        return sortedMap
    }
}
