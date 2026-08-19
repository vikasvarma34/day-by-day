package com.vikaspokala.daybyday.ui.screens.today

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TodayViewModelTest {

    private lateinit var viewModel: TodayViewModel

    @Before
    fun setUp() {
        viewModel = TodayViewModel()
    }

    @Test
    fun `initial tasks contain correct fake dataset`() {
        val tasks = viewModel.tasks.value
        assertEquals(6, tasks.size)

        val timedActive = tasks.filter { !it.isCompleted && it.time != null }
        assertEquals(4, timedActive.size)

        val anytimeActive = tasks.filter { !it.isCompleted && it.time == null }
        assertEquals(1, anytimeActive.size)

        val completed = tasks.filter { it.isCompleted }
        assertEquals(1, completed.size)
    }

    @Test
    fun `toggleCompletion updates task completion state`() {
        val initialTask = viewModel.tasks.value.first { it.id == "t1" }
        assertFalse(initialTask.isCompleted)

        viewModel.toggleCompletion("t1")
        val updatedTask = viewModel.tasks.value.first { it.id == "t1" }
        assertTrue(updatedTask.isCompleted)

        viewModel.toggleCompletion("t1")
        val restoredTask = viewModel.tasks.value.first { it.id == "t1" }
        assertFalse(restoredTask.isCompleted)
    }

    @Test
    fun `toggleImportant updates task importance state`() {
        val initialTask = viewModel.tasks.value.first { it.id == "t1" }
        assertFalse(initialTask.isImportant)

        viewModel.toggleImportant("t1")
        val updatedTask = viewModel.tasks.value.first { it.id == "t1" }
        assertTrue(updatedTask.isImportant)

        viewModel.toggleImportant("t1")
        val restoredTask = viewModel.tasks.value.first { it.id == "t1" }
        assertFalse(restoredTask.isImportant)
    }
}
