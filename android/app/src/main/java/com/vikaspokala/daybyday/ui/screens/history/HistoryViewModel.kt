package com.vikaspokala.daybyday.ui.screens.history

import java.time.LocalDate
import androidx.lifecycle.ViewModel
import com.vikaspokala.daybyday.ui.fake.FakePlannerData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HistoryViewModel(
    referenceDate: LocalDate = LocalDate.now()
) : ViewModel() {

    private val _tasks = MutableStateFlow(FakePlannerData.getHistoryDemoTasks(referenceDate))
    val tasks: StateFlow<List<HistoryTaskItem>> = _tasks.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun getFilteredGroupedHistory(
        query: String = _searchQuery.value,
        taskList: List<HistoryTaskItem> = _tasks.value
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
