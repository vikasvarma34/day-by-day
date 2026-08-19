package com.vikaspokala.daybyday.ui.screens.later

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LaterViewModelTest {

    private lateinit var viewModel: LaterViewModel

    @Before
    fun setUp() {
        viewModel = LaterViewModel()
    }

    @Test
    fun `demo Later items load from FakePlannerData with 5 initial items`() {
        val tasks = viewModel.tasks.value
        assertEquals(5, tasks.size)
        assertEquals(5, viewModel.getActiveTasks().size)
        assertEquals(0, viewModel.getCompletedTasks().size)
    }

    @Test
    fun `LaterTaskItem contains no scheduling requirement fields`() {
        val task = viewModel.tasks.value.first()
        assertEquals("l1", task.id)
        assertEquals("Read Atomic Habits", task.title)
        assertEquals("A chapter or two when there is time", task.note)
    }

    @Test
    fun `toggling importance updates state without reordering items`() {
        val initialOrder = viewModel.getActiveTasks().map { it.id }

        viewModel.toggleImportant("l2")
        val updatedTask = viewModel.tasks.value.first { it.id == "l2" }
        assertTrue(updatedTask.isImportant)

        val newOrder = viewModel.getActiveTasks().map { it.id }
        assertEquals(initialOrder, newOrder)
    }

    @Test
    fun `completing an item moves it from activeTasks to completedTasks`() {
        assertEquals(5, viewModel.getActiveTasks().size)
        assertEquals(0, viewModel.getCompletedTasks().size)

        viewModel.toggleCompletion("l1")

        assertEquals(4, viewModel.getActiveTasks().size)
        assertEquals(1, viewModel.getCompletedTasks().size)
        assertEquals("l1", viewModel.getCompletedTasks().first().id)
        assertTrue(viewModel.getCompletedTasks().first().isCompleted)
    }

    @Test
    fun `undo completion restores item to activeTasks in original position`() {
        val initialOrder = viewModel.getActiveTasks().map { it.id }

        viewModel.toggleCompletion("l1")
        assertEquals(4, viewModel.getActiveTasks().size)

        viewModel.toggleCompletion("l1")
        assertEquals(5, viewModel.getActiveTasks().size)

        val restoredOrder = viewModel.getActiveTasks().map { it.id }
        assertEquals(initialOrder, restoredOrder)
    }

    @Test
    fun `important state survives completion and undo cycle`() {
        val initialTask = viewModel.tasks.value.first { it.id == "l1" }
        assertTrue(initialTask.isImportant)

        viewModel.toggleCompletion("l1")
        val completedTask = viewModel.getCompletedTasks().first { it.id == "l1" }
        assertTrue(completedTask.isImportant)

        viewModel.toggleCompletion("l1")
        val restoredTask = viewModel.getActiveTasks().first { it.id == "l1" }
        assertTrue(restoredTask.isImportant)
    }

    @Test
    fun `isCompletedExpanded toggles and collapses correctly without altering task state`() {
        assertFalse(viewModel.isCompletedExpanded.value)

        viewModel.toggleCompletedExpanded()
        assertTrue(viewModel.isCompletedExpanded.value)

        viewModel.toggleCompletion("l1")
        assertEquals(1, viewModel.getCompletedTasks().size)

        // Bottom nav re-entry invokes collapseCompleted()
        viewModel.collapseCompleted()
        assertFalse(viewModel.isCompletedExpanded.value)

        // Task state remains 100% preserved
        assertEquals(1, viewModel.getCompletedTasks().size)
        assertEquals("l1", viewModel.getCompletedTasks().first().id)
    }
}
