package com.vikaspokala.daybyday.ui.fake

import java.time.LocalDate
import com.vikaspokala.daybyday.ui.screens.schedule.ScheduleTaskItem
import com.vikaspokala.daybyday.ui.screens.today.TaskItem

/**
 * ============================================================================
 * TEMPORARY TASK 8 DEMO / FAKE DATA PROVIDER
 * ============================================================================
 * WARNING FOR TASK 9:
 * This file contains hardcoded demo datasets used exclusively for Task 8 Android UI
 * shell development and testing.
 *
 * MUST BE REMOVED OR REPLACED WITH REAL ROOM DATABASE / REPOSITORY IN TASK 9.
 * ============================================================================
 */
object FakePlannerData {

    fun getTodayDemoTasks(): List<TaskItem> {
        return listOf(
            TaskItem(id = "t1", title = "Take medicine", time = "8:00 AM", isImportant = false, isCompleted = false),
            TaskItem(id = "t2", title = "Buy groceries", time = "10:30 AM", isImportant = true, isCompleted = false),
            TaskItem(id = "t3", title = "Lunch", time = "1:00 PM", isImportant = false, isCompleted = false),
            TaskItem(id = "t4", title = "Gym", time = "6:00 PM", isImportant = true, isCompleted = false),
            TaskItem(id = "t5", title = "Call the bank", time = null, isImportant = false, isCompleted = false),
            TaskItem(id = "t6", title = "Morning walk", time = "7:15 AM", isImportant = false, isCompleted = true)
        )
    }

    fun getScheduleDemoTasks(referenceDate: LocalDate = LocalDate.now()): List<ScheduleTaskItem> {
        val today = referenceDate
        return listOf(
            // Demo tasks relative to current reference date (today) - 6 tasks
            ScheduleTaskItem(
                id = "s1",
                title = "Morning walk",
                date = today,
                time = "7:30 AM",
                isImportant = false
            ),
            ScheduleTaskItem(
                id = "s2",
                title = "Plan weekly meals",
                date = today,
                time = "9:00 AM",
                isImportant = true
            ),
            ScheduleTaskItem(
                id = "s3",
                title = "Pick up parcel",
                date = today,
                time = "3:00 PM",
                isImportant = false
            ),
            ScheduleTaskItem(
                id = "s4",
                title = "Call parents",
                date = today,
                time = "6:30 PM",
                isImportant = false
            ),
            ScheduleTaskItem(
                id = "s5",
                title = "Review insurance",
                date = today,
                time = null,
                isImportant = true
            ),
            ScheduleTaskItem(
                id = "s6",
                title = "Prepare documents",
                date = today,
                time = null,
                isImportant = false
            ),
            // Demo tasks on nearby dates relative to today for calendar dot testing
            ScheduleTaskItem(
                id = "s7",
                title = "Car service",
                date = today.minusDays(12),
                time = "10:00 AM",
                isImportant = true
            ),
            ScheduleTaskItem(
                id = "s8",
                title = "Water plants",
                date = today.minusDays(6),
                time = null,
                isImportant = false
            ),
            ScheduleTaskItem(
                id = "s9",
                title = "Doctor appointment",
                date = today.plusDays(5),
                time = "11:00 AM",
                isImportant = true
            ),
            ScheduleTaskItem(
                id = "s10",
                title = "Tax filing",
                date = today.plusDays(12),
                time = null,
                isImportant = true
            )
        )
    }
}
