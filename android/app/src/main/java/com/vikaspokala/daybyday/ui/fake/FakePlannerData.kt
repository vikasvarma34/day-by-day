package com.vikaspokala.daybyday.ui.fake

import java.time.LocalDate
import com.vikaspokala.daybyday.ui.screens.history.HistoryTaskItem
import com.vikaspokala.daybyday.ui.screens.later.LaterTaskItem
import com.vikaspokala.daybyday.ui.screens.schedule.ScheduleTaskItem

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

    fun getScheduleDemoTasks(referenceDate: LocalDate = LocalDate.now()): List<ScheduleTaskItem> {
        val today = referenceDate
        return listOf(
            // 2 days before today (minus 2 days)
            ScheduleTaskItem(
                id = "d_m2_1",
                title = "Prep presentation slides",
                note = "Include Q3 metrics",
                date = today.minusDays(2),
                time = "11:00 AM",
                isImportant = true,
                isCompleted = true
            ),
            ScheduleTaskItem(
                id = "d_m2_2",
                title = "Order office supplies",
                note = null,
                date = today.minusDays(2),
                time = null,
                isImportant = false,
                isCompleted = true
            ),

            // Yesterday (minus 1 day)
            ScheduleTaskItem(
                id = "d_m1_1",
                title = "Dentist checkup",
                note = "Dr. Smith clinic",
                date = today.minusDays(1),
                time = "9:30 AM",
                isImportant = false,
                isCompleted = true
            ),
            ScheduleTaskItem(
                id = "d_m1_2",
                title = "Review pull request",
                note = "Check auth flow",
                date = today.minusDays(1),
                time = "4:00 PM",
                isImportant = true,
                isCompleted = false
            ),
            ScheduleTaskItem(
                id = "d_m1_3",
                title = "Water balcony plants",
                note = null,
                date = today.minusDays(1),
                time = null,
                isImportant = false,
                isCompleted = false
            ),

            // Today (referenceDate)
            ScheduleTaskItem(
                id = "s1",
                title = "Morning walk",
                note = null,
                date = today,
                time = "7:30 AM",
                isImportant = false,
                isCompleted = false
            ),
            ScheduleTaskItem(
                id = "s2",
                title = "Plan weekly meals",
                note = "Check pantry first",
                date = today,
                time = "9:00 AM",
                isImportant = true,
                isCompleted = false
            ),
            ScheduleTaskItem(
                id = "s3",
                title = "Pick up parcel",
                note = "Code 4821",
                date = today,
                time = "3:00 PM",
                isImportant = false,
                isCompleted = false
            ),
            ScheduleTaskItem(
                id = "s4",
                title = "Call parents",
                note = null,
                date = today,
                time = "6:30 PM",
                isImportant = false,
                isCompleted = false
            ),
            ScheduleTaskItem(
                id = "s5",
                title = "Review insurance",
                note = "Compare policies",
                date = today,
                time = null,
                isImportant = true,
                isCompleted = false
            ),
            ScheduleTaskItem(
                id = "s6",
                title = "Prepare documents",
                note = null,
                date = today,
                time = null,
                isImportant = false,
                isCompleted = false
            ),

            // Tomorrow (plus 1 day)
            ScheduleTaskItem(
                id = "d_p1_1",
                title = "Team sync meeting",
                note = "Discuss Sprint 4 goals",
                date = today.plusDays(1),
                time = "10:00 AM",
                isImportant = true,
                isCompleted = false
            ),
            ScheduleTaskItem(
                id = "d_p1_2",
                title = "Car wash",
                note = null,
                date = today.plusDays(1),
                time = "2:00 PM",
                isImportant = false,
                isCompleted = false
            ),
            ScheduleTaskItem(
                id = "d_p1_3",
                title = "Renew library books",
                note = null,
                date = today.plusDays(1),
                time = null,
                isImportant = false,
                isCompleted = false
            ),

            // 2 days after today (plus 2 days)
            ScheduleTaskItem(
                id = "d_p2_1",
                title = "Doctor appointment",
                note = "Bring blood test results",
                date = today.plusDays(2),
                time = "11:30 AM",
                isImportant = true,
                isCompleted = false
            ),
            ScheduleTaskItem(
                id = "d_p2_2",
                title = "Grocery shopping",
                note = "Fruits, milk, bread",
                date = today.plusDays(2),
                time = "5:00 PM",
                isImportant = false,
                isCompleted = false
            ),
            ScheduleTaskItem(
                id = "d_p2_3",
                title = "Clean kitchen cabinets",
                note = null,
                date = today.plusDays(2),
                time = null,
                isImportant = false,
                isCompleted = false
            ),

            // Demo tasks on nearby dates for calendar dot testing
            ScheduleTaskItem(
                id = "s7",
                title = "Car service",
                note = null,
                date = today.minusDays(12),
                time = "10:00 AM",
                isImportant = true,
                isCompleted = false
            ),
            ScheduleTaskItem(
                id = "s8",
                title = "Water plants",
                note = null,
                date = today.minusDays(6),
                time = null,
                isImportant = false,
                isCompleted = false
            ),
            ScheduleTaskItem(
                id = "s9",
                title = "Eye exam",
                note = null,
                date = today.plusDays(5),
                time = "11:00 AM",
                isImportant = true,
                isCompleted = false
            ),
            ScheduleTaskItem(
                id = "s10",
                title = "Tax filing",
                note = null,
                date = today.plusDays(12),
                time = null,
                isImportant = true,
                isCompleted = false
            )
        )
    }

    fun getTodayDemoTasks(referenceDate: LocalDate = LocalDate.now()): List<ScheduleTaskItem> {
        return getScheduleDemoTasks(referenceDate)
    }

    fun getLaterDemoTasks(): List<LaterTaskItem> {
        return listOf(
            LaterTaskItem(
                id = "l1",
                title = "Read Atomic Habits",
                note = "A chapter or two when there is time",
                isImportant = true
            ),
            LaterTaskItem(
                id = "l2",
                title = "Compare new headphones",
                note = "Look at comfort and battery life",
                isImportant = false
            ),
            LaterTaskItem(
                id = "l3",
                title = "Watch Interstellar",
                note = "Weekend idea",
                isImportant = false
            ),
            LaterTaskItem(
                id = "l4",
                title = "Call college friend",
                note = "Catch up when free",
                isImportant = true
            ),
            LaterTaskItem(
                id = "l5",
                title = "Research weekend trip",
                note = "Somewhere quiet",
                isImportant = false
            )
        )
    }

    fun getHistoryDemoTasks(referenceDate: LocalDate = LocalDate.now()): List<HistoryTaskItem> {
        return listOf(
            HistoryTaskItem(
                id = "h1",
                title = "Buy groceries",
                completedDate = referenceDate.minusDays(1),
                completedAtTime = "5:15 PM",
                completedAtMinutes = 1035
            ),
            HistoryTaskItem(
                id = "h2",
                title = "Submit tax documents",
                completedDate = referenceDate.minusDays(1),
                completedAtTime = "2:30 PM",
                completedAtMinutes = 870
            ),
            HistoryTaskItem(
                id = "h3",
                title = "Plan weekly meals",
                completedDate = referenceDate.minusDays(3),
                completedAtTime = "11:00 AM",
                completedAtMinutes = 660
            ),
            HistoryTaskItem(
                id = "h4",
                title = "Read Atomic Habits",
                completedDate = referenceDate.minusDays(7),
                completedAtTime = "8:45 PM",
                completedAtMinutes = 1245
            )
        )
    }
}
