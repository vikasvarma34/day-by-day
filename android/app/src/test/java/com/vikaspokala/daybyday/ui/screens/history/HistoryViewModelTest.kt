package com.vikaspokala.daybyday.ui.screens.history

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HistoryViewModelTest {

    private val referenceDate: LocalDate = LocalDate.of(2026, 8, 19)
    private lateinit var viewModel: HistoryViewModel

    @Before
    fun setUp() {
        viewModel = HistoryViewModel(referenceDate)
    }

    @Test
    fun `demo history items load from FakePlannerData`() {
        val tasks = viewModel.tasks.value
        assertEquals(4, tasks.size)
    }

    @Test
    fun `history entries are grouped by completion date with newest date first`() {
        val grouped = viewModel.getFilteredGroupedHistory()
        val dates = grouped.keys.toList()

        assertEquals(3, dates.size)
        assertEquals(referenceDate.minusDays(1), dates[0]) // 18 August 2026
        assertEquals(referenceDate.minusDays(3), dates[1]) // 16 August 2026
        assertEquals(referenceDate.minusDays(7), dates[2]) // 12 August 2026
    }

    @Test
    fun `items within same date are sorted descending by completion time`() {
        val grouped = viewModel.getFilteredGroupedHistory()
        val yesterdayTasks = grouped[referenceDate.minusDays(1)].orEmpty()

        assertEquals(2, yesterdayTasks.size)
        assertEquals("h1", yesterdayTasks[0].id) // 5:15 PM
        assertEquals("Buy groceries", yesterdayTasks[0].title)
        assertEquals("h2", yesterdayTasks[1].id) // 2:30 PM
        assertEquals("Submit tax documents", yesterdayTasks[1].title)
    }

    @Test
    fun `case-insensitive search filters title correctly`() {
        val filteredLower = viewModel.getFilteredGroupedHistory("groceries")
        assertEquals(1, filteredLower.size)
        assertEquals(1, filteredLower.values.first().size)
        assertEquals("Buy groceries", filteredLower.values.first().first().title)

        val filteredUpper = viewModel.getFilteredGroupedHistory("GROCERIES")
        assertEquals(1, filteredUpper.size)
        assertEquals("Buy groceries", filteredUpper.values.first().first().title)
    }

    @Test
    fun `search trims surrounding whitespace`() {
        val filtered = viewModel.getFilteredGroupedHistory("   groceries   ")
        assertEquals(1, filtered.size)
        assertEquals("Buy groceries", filtered.values.first().first().title)
    }

    @Test
    fun `blank query returns full history`() {
        val filteredSpaces = viewModel.getFilteredGroupedHistory("   ")
        assertEquals(3, filteredSpaces.size)
        assertEquals(4, filteredSpaces.values.sumOf { it.size })
    }

    @Test
    fun `unmatched query returns no history`() {
        val filtered = viewModel.getFilteredGroupedHistory("NonExistentTaskQuery")
        assertTrue(filtered.isEmpty())
    }

    @Test
    fun `filtering preserves date grouping and ordering behavior`() {
        // Search "tax" which matches "Submit tax documents" (date: referenceDate.minusDays(1))
        val filtered = viewModel.getFilteredGroupedHistory("tax")
        assertEquals(1, filtered.size)

        val dateKey = filtered.keys.first()
        assertEquals(referenceDate.minusDays(1), dateKey)
        assertEquals(1, filtered[dateKey]?.size)
        assertEquals("Submit tax documents", filtered[dateKey]?.first()?.title)
    }
}
