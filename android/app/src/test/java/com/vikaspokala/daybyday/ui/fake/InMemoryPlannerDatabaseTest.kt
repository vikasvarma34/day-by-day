package com.vikaspokala.daybyday.ui.fake

import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class InMemoryPlannerDatabaseTest {

    private val fixedToday = LocalDate.of(2026, 8, 20)
    private lateinit var database: InMemoryPlannerDatabase

    @Before
    fun setUp() {
        database = InMemoryPlannerDatabase(referenceDate = fixedToday)
    }

    @Test
    fun testEverySeededScheduleReferencesRealTask() {
        val taskIds = database.tasks.value.map { it.id }.toSet()
        val schedules = database.schedules.value

        assertTrue("Schedules should not be empty", schedules.isNotEmpty())
        for (schedule in schedules) {
            assertTrue(
                "Schedule ${schedule.id} references non-existent taskId ${schedule.taskId}",
                schedule.taskId in taskIds
            )
        }
    }

    @Test
    fun testEverySeededCompletionReferencesRealTask() {
        val taskIds = database.tasks.value.map { it.id }.toSet()
        val completions = database.completions.value

        assertTrue("Completions should not be empty", completions.isNotEmpty())
        for (completion in completions) {
            assertTrue(
                "Completion ${completion.id} references non-existent taskId ${completion.taskId}",
                completion.taskId in taskIds
            )
        }
    }

    @Test
    fun testScheduledCompletionReferencesCorrectTasksSchedule() {
        val schedules = database.schedules.value
        val completions = database.completions.value

        for (completion in completions) {
            if (completion.scheduleId != null) {
                assertNotNull("Scheduled completion must have scheduledDate", completion.scheduledDate)
                val schedule = schedules.find { it.id == completion.scheduleId }
                assertNotNull(
                    "Completion ${completion.id} references non-existent schedule ${completion.scheduleId}",
                    schedule
                )
                assertEquals(
                    "Completion ${completion.id} schedule ${completion.scheduleId} taskId mismatch",
                    completion.taskId,
                    schedule?.taskId
                )
            } else {
                assertNull("Later completion must have null scheduledDate", completion.scheduledDate)
            }
        }
    }

    @Test
    fun testNoOrphanHistoryOnlyRecords() {
        val taskIds = database.tasks.value.map { it.id }.toSet()
        val completionTaskIds = database.completions.value.map { it.taskId }.toSet()

        // Every completion task ID MUST exist in taskIds
        for (compTaskId in completionTaskIds) {
            assertTrue("Orphan completion found for taskId $compTaskId", compTaskId in taskIds)
        }
    }

    @Test
    fun testLaterSeededTasksHaveNoSchedules() {
        val scheduledTaskIds = database.schedules.value.map { it.taskId }.toSet()
        val laterTaskIds = listOf("l1", "l2", "l3", "l4", "l5")

        for (laterId in laterTaskIds) {
            val task = database.tasks.value.find { it.id == laterId }
            assertNotNull("Later task $laterId should exist", task)
            assertFalse(
                "Later task $laterId must not have an entry in schedules",
                laterId in scheduledTaskIds
            )
        }
    }

    @Test
    fun testPrepPresentationSlidesHasMatchingHistoricalCompletion() {
        val task = database.tasks.value.find { it.id == "d_m2_1" }
        assertNotNull("Task d_m2_1 must exist", task)
        assertEquals("Prep presentation slides", task?.title)
        assertTrue(task?.isImportant == true)

        val completion = database.completions.value.find { it.taskId == "d_m2_1" }
        assertNotNull("Completion for d_m2_1 must exist", completion)
        assertEquals(fixedToday.minusDays(2), completion?.scheduledDate)
        assertEquals(fixedToday.minusDays(2), completion?.completedDate)
        assertEquals("Prep presentation slides", completion?.titleSnapshot)
        assertTrue(completion?.isImportantSnapshot == true)
        assertTrue("Completed epoch millis must be > 0", (completion?.completedAtEpochMillis ?: 0L) > 0L)
    }

    @Test
    fun testOrderOfficeSuppliesHasMatchingNonImportantHistoricalCompletion() {
        val task = database.tasks.value.find { it.id == "d_m2_2" }
        assertNotNull("Task d_m2_2 must exist", task)
        assertEquals("Order office supplies", task?.title)
        assertFalse(task?.isImportant == true)

        val completion = database.completions.value.find { it.taskId == "d_m2_2" }
        assertNotNull("Completion for d_m2_2 must exist", completion)
        assertEquals(fixedToday.minusDays(2), completion?.scheduledDate)
        assertEquals(fixedToday.minusDays(2), completion?.completedDate)
        assertFalse(completion?.isImportantSnapshot == true)
    }

    @Test
    fun testDentistCheckupHasMatchingNonImportantHistoricalCompletion() {
        val task = database.tasks.value.find { it.id == "d_m1_1" }
        assertNotNull("Task d_m1_1 must exist", task)
        assertEquals("Dentist checkup", task?.title)
        assertFalse(task?.isImportant == true)

        val completion = database.completions.value.find { it.taskId == "d_m1_1" }
        assertNotNull("Completion for d_m1_1 must exist", completion)
        assertEquals(fixedToday.minusDays(1), completion?.scheduledDate)
        assertEquals(fixedToday.minusDays(1), completion?.completedDate)
        assertFalse(completion?.isImportantSnapshot == true)
    }

    @Test
    fun testValidLocalTimeScheduleAccepted() {
        val task = TaskEntity(id = "t_time", title = "Timed Task")
        val schedule = ScheduleEntity(
            id = "s_time",
            taskId = "t_time",
            scheduleType = ScheduleType.ONCE,
            startDate = fixedToday,
            endDate = fixedToday,
            scheduledTime = LocalTime.of(14, 30)
        )
        val seed = CanonicalPlannerSeed(tasks = listOf(task), schedules = listOf(schedule), completions = emptyList())
        val db = InMemoryPlannerDatabase(seed = seed)
        assertEquals(LocalTime.of(14, 30), db.schedules.value[0].scheduledTime)
    }

    @Test
    fun testAllowedReminderWithTimeAccepted() {
        val task = TaskEntity(id = "t_rem", title = "Reminder Task")
        val schedule = ScheduleEntity(
            id = "s_rem",
            taskId = "t_rem",
            scheduleType = ScheduleType.ONCE,
            startDate = fixedToday,
            endDate = fixedToday,
            scheduledTime = LocalTime.of(10, 0),
            reminderMinutesBefore = 15
        )
        val seed = CanonicalPlannerSeed(tasks = listOf(task), schedules = listOf(schedule), completions = emptyList())
        val db = InMemoryPlannerDatabase(seed = seed)
        assertEquals(15, db.schedules.value[0].reminderMinutesBefore)
    }

    @Test
    fun testReminderWithoutTimeRejected() {
        val task = TaskEntity(id = "t_rem", title = "Reminder Task")
        val schedule = ScheduleEntity(
            id = "s_rem",
            taskId = "t_rem",
            scheduleType = ScheduleType.ONCE,
            startDate = fixedToday,
            endDate = fixedToday,
            scheduledTime = null, // Missing time
            reminderMinutesBefore = 15
        )
        val seed = CanonicalPlannerSeed(tasks = listOf(task), schedules = listOf(schedule), completions = emptyList())
        try {
            InMemoryPlannerDatabase(seed = seed)
            fail("Expected IllegalArgumentException when reminder has null scheduledTime")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("scheduledTime is null") == true)
        }
    }

    @Test
    fun testUnsupportedReminderValueRejected() {
        val task = TaskEntity(id = "t_rem", title = "Reminder Task")
        val schedule = ScheduleEntity(
            id = "s_rem",
            taskId = "t_rem",
            scheduleType = ScheduleType.ONCE,
            startDate = fixedToday,
            endDate = fixedToday,
            scheduledTime = LocalTime.of(10, 0),
            reminderMinutesBefore = 25 // Not in allowed whitelist
        )
        val seed = CanonicalPlannerSeed(tasks = listOf(task), schedules = listOf(schedule), completions = emptyList())
        try {
            InMemoryPlannerDatabase(seed = seed)
            fail("Expected IllegalArgumentException for unsupported reminderMinutesBefore")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("invalid reminderMinutesBefore") == true)
        }
    }

    @Test
    fun testCompletionHasCanonicalCompletedAtEpochMillis() {
        val task = TaskEntity(id = "t_comp", title = "Comp Task", isImportant = true)
        val completion = CompletionEntity(
            id = "c_comp",
            taskId = "t_comp",
            scheduleId = null,
            scheduledDate = null,
            completedDate = fixedToday,
            completedAtEpochMillis = 1787140000000L,
            titleSnapshot = "Comp Task",
            isImportantSnapshot = true
        )
        val seed = CanonicalPlannerSeed(tasks = listOf(task), schedules = emptyList(), completions = listOf(completion))
        val db = InMemoryPlannerDatabase(seed = seed)
        assertEquals(1787140000000L, db.completions.value[0].completedAtEpochMillis)
    }

    @Test
    fun testCanonicalSeedUsesDeterministicCompletionOrdering() {
        val completions = database.completions.value
        assertTrue("Canonical seed must have seeded completions", completions.size >= 2)
        for (comp in completions) {
            assertTrue("Epoch millis must be deterministic non-zero", comp.completedAtEpochMillis > 0L)
        }
    }

    @Test
    fun testRecurringCompletedOccurrenceCanExistInCompletionRecords() {
        val recurringTask = TaskEntity(id = "rec_1", title = "Daily Standup", isImportant = true)
        val recurringSchedule = ScheduleEntity(
            id = "sched_rec_1",
            taskId = "rec_1",
            scheduleType = ScheduleType.INTERVAL_DAYS,
            startDate = fixedToday.minusDays(5),
            intervalDays = 1,
            intervalAnchorDate = fixedToday.minusDays(5)
        )
        val recurringCompletion = CompletionEntity(
            id = "comp_rec_1_m1",
            taskId = "rec_1",
            scheduleId = "sched_rec_1",
            scheduledDate = fixedToday.minusDays(1),
            completedDate = fixedToday.minusDays(1),
            completedAtEpochMillis = 1787145000000L,
            titleSnapshot = "Daily Standup",
            isImportantSnapshot = true
        )

        val customSeed = CanonicalPlannerSeed(
            tasks = listOf(recurringTask),
            schedules = listOf(recurringSchedule),
            completions = listOf(recurringCompletion)
        )

        val customDb = InMemoryPlannerDatabase(referenceDate = fixedToday, seed = customSeed)
        assertEquals(1, customDb.completions.value.size)
        assertEquals("comp_rec_1_m1", customDb.completions.value[0].id)
    }

    @Test
    fun testRecurringCompletionIsDistinguishableFromOnceLaterForFutureHistoryFiltering() {
        val onceTask = TaskEntity(id = "once_1", title = "Once Important", isImportant = true)
        val onceSched = ScheduleEntity(
            id = "s_once", taskId = "once_1", scheduleType = ScheduleType.ONCE,
            startDate = fixedToday.minusDays(1), endDate = fixedToday.minusDays(1)
        )
        val onceComp = CompletionEntity(
            id = "c_once", taskId = "once_1", scheduleId = "s_once", scheduledDate = fixedToday.minusDays(1),
            completedDate = fixedToday.minusDays(1), completedAtEpochMillis = 1787140000000L,
            titleSnapshot = "Once Important", isImportantSnapshot = true
        )

        val recTask = TaskEntity(id = "rec_1", title = "Daily Exercise", isImportant = true)
        val recSched = ScheduleEntity(
            id = "s_rec", taskId = "rec_1", scheduleType = ScheduleType.INTERVAL_DAYS,
            startDate = fixedToday.minusDays(5), intervalDays = 1, intervalAnchorDate = fixedToday.minusDays(5)
        )
        val recComp = CompletionEntity(
            id = "c_rec", taskId = "rec_1", scheduleId = "s_rec", scheduledDate = fixedToday.minusDays(1),
            completedDate = fixedToday.minusDays(1), completedAtEpochMillis = 1787130000000L,
            titleSnapshot = "Daily Exercise", isImportantSnapshot = true
        )

        val seed = CanonicalPlannerSeed(
            tasks = listOf(onceTask, recTask),
            schedules = listOf(onceSched, recSched),
            completions = listOf(onceComp, recComp)
        )
        val db = InMemoryPlannerDatabase(seed = seed)

        // Project history by filtering: schedule must be ONCE or null (Later), and isImportantSnapshot == true
        val scheduleMap = db.schedules.value.associateBy { it.id }
        val historyEligible = db.completions.value.filter { comp ->
            comp.isImportantSnapshot && (comp.scheduleId == null || scheduleMap[comp.scheduleId]?.scheduleType == ScheduleType.ONCE)
        }

        assertEquals(1, historyEligible.size)
        assertEquals("c_once", historyEligible[0].id)
    }

    @Test
    fun testNoDuplicateTaskIds() {
        val taskIds = database.tasks.value.map { it.id }
        assertEquals("Task IDs must be unique", taskIds.toSet().size, taskIds.size)
    }

    @Test
    fun testNoDuplicateCompletionOccurrenceKeys() {
        val keys = database.completions.value.map { Pair(it.taskId, it.scheduledDate) }
        assertEquals("Completion occurrences (taskId, scheduledDate) must be unique", keys.toSet().size, keys.size)
    }

    @Test
    fun testInvariantValidationRejectsOrphanSchedule() {
        val badSeed = CanonicalPlannerSeed(
            tasks = emptyList(),
            schedules = listOf(
                ScheduleEntity(id = "s1", taskId = "non_existent", scheduleType = ScheduleType.ONCE, startDate = fixedToday, endDate = fixedToday)
            ),
            completions = emptyList()
        )
        try {
            InMemoryPlannerDatabase(seed = badSeed)
            fail("Expected IllegalArgumentException for orphan schedule")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("references non-existent task") == true)
        }
    }

    @Test
    fun testInvariantValidationRejectsOrphanCompletion() {
        val badSeed = CanonicalPlannerSeed(
            tasks = emptyList(),
            schedules = emptyList(),
            completions = listOf(
                CompletionEntity(
                    id = "c1", taskId = "non_existent", completedDate = fixedToday,
                    completedAtEpochMillis = 1787140000000L,
                    titleSnapshot = "Bad", isImportantSnapshot = true
                )
            )
        )
        try {
            InMemoryPlannerDatabase(seed = badSeed)
            fail("Expected IllegalArgumentException for orphan completion")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("references non-existent task") == true)
        }
    }

    @Test
    fun testInvariantValidationRejectsOnceWithIntervalFields() {
        val task = TaskEntity(id = "t1", title = "Task 1")
        val badSchedule = ScheduleEntity(
            id = "s1", taskId = "t1", scheduleType = ScheduleType.ONCE,
            startDate = fixedToday, endDate = fixedToday, intervalDays = 2
        )
        val badSeed = CanonicalPlannerSeed(tasks = listOf(task), schedules = listOf(badSchedule), completions = emptyList())
        try {
            InMemoryPlannerDatabase(seed = badSeed)
            fail("Expected IllegalArgumentException for ONCE with intervalDays")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("must not have intervalDays") == true)
        }
    }

    @Test
    fun testInvariantValidationRejectsIntervalDaysWithoutValidInterval() {
        val task = TaskEntity(id = "t1", title = "Task 1")
        val badSchedule = ScheduleEntity(
            id = "s1", taskId = "t1", scheduleType = ScheduleType.INTERVAL_DAYS,
            startDate = fixedToday, intervalDays = 0, intervalAnchorDate = fixedToday
        )
        val badSeed = CanonicalPlannerSeed(tasks = listOf(task), schedules = listOf(badSchedule), completions = emptyList())
        try {
            InMemoryPlannerDatabase(seed = badSeed)
            fail("Expected IllegalArgumentException for INTERVAL_DAYS with intervalDays < 1")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("intervalDays >= 1") == true)
        }
    }

    @Test
    fun testInvariantValidationRejectsWeekdaysWithEmptyWeekdays() {
        val task = TaskEntity(id = "t1", title = "Task 1")
        val badSchedule = ScheduleEntity(
            id = "s1", taskId = "t1", scheduleType = ScheduleType.WEEKDAYS,
            startDate = fixedToday, weekdays = emptySet()
        )
        val badSeed = CanonicalPlannerSeed(tasks = listOf(task), schedules = listOf(badSchedule), completions = emptyList())
        try {
            InMemoryPlannerDatabase(seed = badSeed)
            fail("Expected IllegalArgumentException for WEEKDAYS with empty weekdays")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("must have non-empty weekdays") == true)
        }
    }

    @Test
    fun testInvariantValidationRejectsDirectLaterCompletionForScheduledTask() {
        val task = TaskEntity(id = "t1", title = "Task 1")
        val schedule = ScheduleEntity(
            id = "s1", taskId = "t1", scheduleType = ScheduleType.ONCE,
            startDate = fixedToday, endDate = fixedToday
        )
        val badCompletion = CompletionEntity(
            id = "c1", taskId = "t1", scheduleId = null, scheduledDate = null,
            completedDate = fixedToday, completedAtEpochMillis = 1787140000000L,
            titleSnapshot = "Task 1", isImportantSnapshot = false
        )
        val badSeed = CanonicalPlannerSeed(tasks = listOf(task), schedules = listOf(schedule), completions = listOf(badCompletion))
        try {
            InMemoryPlannerDatabase(seed = badSeed)
            fail("Expected IllegalArgumentException for direct-Later completion against scheduled task")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("references task t1 which has active schedule") == true)
        }
    }

    @Test
    fun testValidOnceSchedulePreserved() {
        val task = TaskEntity(id = "t_once", title = "Once Task")
        val schedule = ScheduleEntity(
            id = "s_once", taskId = "t_once", scheduleType = ScheduleType.ONCE,
            startDate = fixedToday, endDate = fixedToday
        )
        val seed = CanonicalPlannerSeed(tasks = listOf(task), schedules = listOf(schedule), completions = emptyList())
        val db = InMemoryPlannerDatabase(seed = seed)
        assertEquals(1, db.schedules.value.size)
        assertEquals(ScheduleType.ONCE, db.schedules.value[0].scheduleType)
        assertEquals(fixedToday, db.schedules.value[0].endDate)
    }

    @Test
    fun testValidIntervalDaysSchedulePreserved() {
        val task = TaskEntity(id = "t_int", title = "Interval Task")
        val schedule = ScheduleEntity(
            id = "s_int", taskId = "t_int", scheduleType = ScheduleType.INTERVAL_DAYS,
            startDate = fixedToday, intervalDays = 3, intervalAnchorDate = fixedToday
        )
        val seed = CanonicalPlannerSeed(tasks = listOf(task), schedules = listOf(schedule), completions = emptyList())
        val db = InMemoryPlannerDatabase(seed = seed)
        assertEquals(1, db.schedules.value.size)
        assertEquals(ScheduleType.INTERVAL_DAYS, db.schedules.value[0].scheduleType)
        assertEquals(3, db.schedules.value[0].intervalDays)
        assertEquals(fixedToday, db.schedules.value[0].intervalAnchorDate)
    }

    @Test
    fun testValidWeekdaysSchedulePreserved() {
        val task = TaskEntity(id = "t_wk", title = "Weekdays Task")
        val schedule = ScheduleEntity(
            id = "s_wk", taskId = "t_wk", scheduleType = ScheduleType.WEEKDAYS,
            startDate = fixedToday, weekdays = setOf(1, 3, 5)
        )
        val seed = CanonicalPlannerSeed(tasks = listOf(task), schedules = listOf(schedule), completions = emptyList())
        val db = InMemoryPlannerDatabase(seed = seed)
        assertEquals(1, db.schedules.value.size)
        assertEquals(ScheduleType.WEEKDAYS, db.schedules.value[0].scheduleType)
        assertEquals(setOf(1, 3, 5), db.schedules.value[0].weekdays)
    }

    @Test
    fun testGenuineDirectLaterCompletionPreserved() {
        val laterTask = TaskEntity(id = "t_later", title = "Later Task", isImportant = true)
        val laterCompletion = CompletionEntity(
            id = "c_later", taskId = "t_later", scheduleId = null, scheduledDate = null,
            completedDate = fixedToday, completedAtEpochMillis = 1787140000000L,
            titleSnapshot = "Later Task", isImportantSnapshot = true
        )
        val seed = CanonicalPlannerSeed(tasks = listOf(laterTask), schedules = emptyList(), completions = listOf(laterCompletion))
        val db = InMemoryPlannerDatabase(seed = seed)
        assertEquals(1, db.tasks.value.size)
        assertEquals(0, db.schedules.value.size)
        assertEquals(1, db.completions.value.size)
        assertNull(db.completions.value[0].scheduleId)
        assertNull(db.completions.value[0].scheduledDate)
    }

    // ========================================================================
    // STEP 2A: READ PROJECTION TESTS
    // ========================================================================

    @Test
    fun testOnceTaskAppearsOnlyOnItsDate() {
        val task = TaskEntity(id = "once_t", title = "Doctor Visit")
        val schedule = ScheduleEntity(
            id = "once_s", taskId = "once_t", scheduleType = ScheduleType.ONCE,
            startDate = fixedToday, endDate = fixedToday
        )
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(listOf(task), listOf(schedule), emptyList()))

        val onDate = db.getTasksForDate(fixedToday)
        assertEquals(1, onDate.size)
        assertEquals("once_t", onDate[0].id)
        assertEquals("Doctor Visit", onDate[0].title)

        val beforeDate = db.getTasksForDate(fixedToday.minusDays(1))
        assertEquals(0, beforeDate.size)

        val afterDate = db.getTasksForDate(fixedToday.plusDays(1))
        assertEquals(0, afterDate.size)
    }

    @Test
    fun testIntervalDaysOccurrencesCalculatedCorrectly() {
        val task = TaskEntity(id = "int_t", title = "Take Vitamin")
        val schedule = ScheduleEntity(
            id = "int_s", taskId = "int_t", scheduleType = ScheduleType.INTERVAL_DAYS,
            startDate = fixedToday, intervalDays = 3, intervalAnchorDate = fixedToday
        )
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(listOf(task), listOf(schedule), emptyList()))

        assertTrue(db.getTasksForDate(fixedToday).isNotEmpty()) // Day 0
        assertFalse(db.getTasksForDate(fixedToday.plusDays(1)).isNotEmpty()) // Day 1
        assertFalse(db.getTasksForDate(fixedToday.plusDays(2)).isNotEmpty()) // Day 2
        assertTrue(db.getTasksForDate(fixedToday.plusDays(3)).isNotEmpty()) // Day 3
        assertFalse(db.getTasksForDate(fixedToday.plusDays(4)).isNotEmpty()) // Day 4
        assertTrue(db.getTasksForDate(fixedToday.plusDays(6)).isNotEmpty()) // Day 6
        assertFalse(db.getTasksForDate(fixedToday.minusDays(3)).isNotEmpty()) // Before start date
    }

    @Test
    fun testWeekdaysOccurrencesCalculatedCorrectly() {
        // fixedToday = 2026-08-20 is a Thursday (dayOfWeek.value = 4)
        val task = TaskEntity(id = "wk_t", title = "Gym Workout")
        val schedule = ScheduleEntity(
            id = "wk_s", taskId = "wk_t", scheduleType = ScheduleType.WEEKDAYS,
            startDate = fixedToday, weekdays = setOf(1, 4) // Monday (1) and Thursday (4)
        )
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(listOf(task), listOf(schedule), emptyList()))

        assertTrue(db.getTasksForDate(fixedToday).isNotEmpty()) // Thursday -> matches
        assertFalse(db.getTasksForDate(fixedToday.plusDays(1)).isNotEmpty()) // Friday (5) -> no match
        assertFalse(db.getTasksForDate(fixedToday.plusDays(2)).isNotEmpty()) // Saturday (6) -> no match
        assertFalse(db.getTasksForDate(fixedToday.plusDays(3)).isNotEmpty()) // Sunday (7) -> no match
        assertTrue(db.getTasksForDate(fixedToday.plusDays(4)).isNotEmpty()) // Monday (1) -> matches
    }

    @Test
    fun testEndDateBoundariesRespected() {
        val task = TaskEntity(id = "end_t", title = "Sprint Daily")
        val schedule = ScheduleEntity(
            id = "end_s", taskId = "end_t", scheduleType = ScheduleType.INTERVAL_DAYS,
            startDate = fixedToday, endDate = fixedToday.plusDays(2),
            intervalDays = 1, intervalAnchorDate = fixedToday
        )
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(listOf(task), listOf(schedule), emptyList()))

        assertTrue(db.getTasksForDate(fixedToday).isNotEmpty())
        assertTrue(db.getTasksForDate(fixedToday.plusDays(1)).isNotEmpty())
        assertTrue(db.getTasksForDate(fixedToday.plusDays(2)).isNotEmpty()) // endDate
        assertFalse(db.getTasksForDate(fixedToday.plusDays(3)).isNotEmpty()) // strictly after endDate
    }

    @Test
    fun testOneRecurringOccurrenceCompletionDoesNotCompleteAnotherOccurrence() {
        val task = TaskEntity(id = "rec_comp_t", title = "Water Plants")
        val schedule = ScheduleEntity(
            id = "rec_comp_s", taskId = "rec_comp_t", scheduleType = ScheduleType.INTERVAL_DAYS,
            startDate = fixedToday.minusDays(2), intervalDays = 1, intervalAnchorDate = fixedToday.minusDays(2)
        )
        // Complete only yesterday (today - 1d)
        val completion = CompletionEntity(
            id = "c_yesterday", taskId = "rec_comp_t", scheduleId = "rec_comp_s",
            scheduledDate = fixedToday.minusDays(1), completedDate = fixedToday.minusDays(1),
            completedAtEpochMillis = 1787140000000L, titleSnapshot = "Water Plants", isImportantSnapshot = false
        )
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(listOf(task), listOf(schedule), listOf(completion)))

        val yesterdayTasks = db.getTasksForDate(fixedToday.minusDays(1))
        assertEquals(1, yesterdayTasks.size)
        assertTrue("Yesterday occurrence must be completed", yesterdayTasks[0].isCompleted)

        val todayTasks = db.getTasksForDate(fixedToday)
        assertEquals(1, todayTasks.size)
        assertFalse("Today occurrence must remain incomplete", todayTasks[0].isCompleted)
    }

    @Test
    fun testLaterProjectionContainsOnlyUnscheduledTasks() {
        val scheduledTask = TaskEntity(id = "sched_t", title = "Scheduled")
        val sched = ScheduleEntity(id = "s_1", taskId = "sched_t", scheduleType = ScheduleType.ONCE, startDate = fixedToday, endDate = fixedToday)

        val laterTask1 = TaskEntity(id = "later_1", title = "Later Task 1", createdAtEpochMillis = 1000L)
        val laterTask2 = TaskEntity(id = "later_2", title = "Later Task 2", createdAtEpochMillis = 2000L)

        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(listOf(scheduledTask, laterTask1, laterTask2), listOf(sched), emptyList()))
        val laterList = db.getLaterTasks()

        assertEquals(2, laterList.size)
        assertEquals("later_2", laterList[0].id) // Newer created task first
        assertEquals("later_1", laterList[1].id)
        assertFalse(laterList.any { it.id == "sched_t" })
    }

    @Test
    fun testDirectLaterCompletionSetsLaterIsCompleted() {
        val laterTask = TaskEntity(id = "later_c", title = "Read Book", isImportant = true)
        val laterCompletion = CompletionEntity(
            id = "c_later", taskId = "later_c", scheduleId = null, scheduledDate = null,
            completedDate = fixedToday, completedAtEpochMillis = 1787140000000L,
            titleSnapshot = "Read Book", isImportantSnapshot = true
        )
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(listOf(laterTask), emptyList(), listOf(laterCompletion)))

        val laterList = db.getLaterTasks()
        assertEquals(1, laterList.size)
        assertEquals("later_c", laterList[0].id)
        assertTrue(laterList[0].isCompleted)
    }

    @Test
    fun testHistoryIncludesHistoricalImportantOnceCompletion() {
        val task = TaskEntity(id = "hist_once_t", title = "Tax Filing", isImportant = true)
        val schedule = ScheduleEntity(
            id = "s_hist", taskId = "hist_once_t", scheduleType = ScheduleType.ONCE,
            startDate = fixedToday.minusDays(3), endDate = fixedToday.minusDays(3)
        )
        val completion = CompletionEntity(
            id = "c_hist", taskId = "hist_once_t", scheduleId = "s_hist",
            scheduledDate = fixedToday.minusDays(3), completedDate = fixedToday.minusDays(3),
            completedAtEpochMillis = 1787120000000L, titleSnapshot = "Tax Filing", isImportantSnapshot = true
        )
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(listOf(task), listOf(schedule), listOf(completion)))

        val history = db.getHistoryTasks(plannerToday = fixedToday)
        assertEquals(1, history.size)
        assertEquals("hist_once_t", history[0].id)
        assertEquals("Tax Filing", history[0].title)
        assertTrue(history[0].isImportant)
        assertEquals(fixedToday.minusDays(3), history[0].completedDate)
    }

    @Test
    fun testHistoryIncludesHistoricalImportantDirectLaterCompletion() {
        val laterTask = TaskEntity(id = "hist_later_t", title = "Buy Car", isImportant = true)
        val completion = CompletionEntity(
            id = "c_later_hist", taskId = "hist_later_t", scheduleId = null, scheduledDate = null,
            completedDate = fixedToday.minusDays(2), completedAtEpochMillis = 1787125000000L,
            titleSnapshot = "Buy Car", isImportantSnapshot = true
        )
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(listOf(laterTask), emptyList(), listOf(completion)))

        val history = db.getHistoryTasks(plannerToday = fixedToday)
        assertEquals(1, history.size)
        assertEquals("hist_later_t", history[0].id)
        assertEquals("Buy Car", history[0].title)
    }

    @Test
    fun testHistoryExcludesNonImportantCompletion() {
        val task = TaskEntity(id = "non_imp_t", title = "Buy Milk", isImportant = false)
        val schedule = ScheduleEntity(
            id = "s_non_imp", taskId = "non_imp_t", scheduleType = ScheduleType.ONCE,
            startDate = fixedToday.minusDays(2), endDate = fixedToday.minusDays(2)
        )
        val completion = CompletionEntity(
            id = "c_non_imp", taskId = "non_imp_t", scheduleId = "s_non_imp",
            scheduledDate = fixedToday.minusDays(2), completedDate = fixedToday.minusDays(2),
            completedAtEpochMillis = 1787125000000L, titleSnapshot = "Buy Milk", isImportantSnapshot = false
        )
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(listOf(task), listOf(schedule), listOf(completion)))

        val history = db.getHistoryTasks(plannerToday = fixedToday)
        assertEquals(0, history.size)
    }

    @Test
    fun testHistoryExcludesRecurringCompletion() {
        val recTask = TaskEntity(id = "rec_imp_t", title = "Daily Important Meeting", isImportant = true)
        val recSched = ScheduleEntity(
            id = "s_rec_imp", taskId = "rec_imp_t", scheduleType = ScheduleType.INTERVAL_DAYS,
            startDate = fixedToday.minusDays(5), intervalDays = 1, intervalAnchorDate = fixedToday.minusDays(5)
        )
        val recComp = CompletionEntity(
            id = "c_rec_imp", taskId = "rec_imp_t", scheduleId = "s_rec_imp",
            scheduledDate = fixedToday.minusDays(2), completedDate = fixedToday.minusDays(2),
            completedAtEpochMillis = 1787125000000L, titleSnapshot = "Daily Important Meeting", isImportantSnapshot = true
        )
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(listOf(recTask), listOf(recSched), listOf(recComp)))

        val history = db.getHistoryTasks(plannerToday = fixedToday)
        assertEquals("Recurring completion must NEVER enter History", 0, history.size)
    }

    @Test
    fun testHistoryExcludesCompletionMadeOnPlannerToday() {
        val task = TaskEntity(id = "today_comp_t", title = "Today Task", isImportant = true)
        val schedule = ScheduleEntity(
            id = "s_today_comp", taskId = "today_comp_t", scheduleType = ScheduleType.ONCE,
            startDate = fixedToday, endDate = fixedToday
        )
        // Completed today (completedDate == plannerToday)
        val completion = CompletionEntity(
            id = "c_today_comp", taskId = "today_comp_t", scheduleId = "s_today_comp",
            scheduledDate = fixedToday, completedDate = fixedToday,
            completedAtEpochMillis = 1787150000000L, titleSnapshot = "Today Task", isImportantSnapshot = true
        )
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(listOf(task), listOf(schedule), listOf(completion)))

        val history = db.getHistoryTasks(plannerToday = fixedToday)
        assertEquals("History must exclude completions made on plannerToday", 0, history.size)
    }

    @Test
    fun testHistoryUsesTitleSnapshotNotCurrentLiveTitle() {
        val task = TaskEntity(id = "live_title_t", title = "Mutated Live Title", isImportant = true)
        val schedule = ScheduleEntity(
            id = "s_live", taskId = "live_title_t", scheduleType = ScheduleType.ONCE,
            startDate = fixedToday.minusDays(2), endDate = fixedToday.minusDays(2)
        )
        val completion = CompletionEntity(
            id = "c_live", taskId = "live_title_t", scheduleId = "s_live",
            scheduledDate = fixedToday.minusDays(2), completedDate = fixedToday.minusDays(2),
            completedAtEpochMillis = 1787125000000L, titleSnapshot = "Original Title At Completion", isImportantSnapshot = true
        )
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(listOf(task), listOf(schedule), listOf(completion)))

        val history = db.getHistoryTasks(plannerToday = fixedToday)
        assertEquals(1, history.size)
        assertEquals("Original Title At Completion", history[0].title)
    }

    @Test
    fun testTimeReminderProjectionMatchesCurrentUiFormat() {
        val task = TaskEntity(id = "fmt_t", title = "Formatted Task")
        val schedule = ScheduleEntity(
            id = "s_fmt", taskId = "fmt_t", scheduleType = ScheduleType.ONCE,
            startDate = fixedToday, endDate = fixedToday,
            scheduledTime = LocalTime.of(9, 30),
            reminderMinutesBefore = 15
        )
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(listOf(task), listOf(schedule), emptyList()))

        val tasks = db.getTasksForDate(fixedToday)
        assertEquals(1, tasks.size)
        assertEquals("9:30 AM", tasks[0].time)
        assertEquals("15 minutes before", tasks[0].reminder)
    }

    @Test
    fun testImportantDateProjectionProducesExpectedCalendarDotDates() {
        val impTask1 = TaskEntity(id = "t_imp1", title = "Imp 1", isImportant = true)
        val sched1 = ScheduleEntity(
            id = "s_imp1", taskId = "t_imp1", scheduleType = ScheduleType.ONCE,
            startDate = fixedToday.minusDays(2), endDate = fixedToday.minusDays(2)
        )

        val impTask2 = TaskEntity(id = "t_imp2", title = "Imp 2", isImportant = true)
        val sched2 = ScheduleEntity(
            id = "s_imp2", taskId = "t_imp2", scheduleType = ScheduleType.ONCE,
            startDate = fixedToday.plusDays(3), endDate = fixedToday.plusDays(3)
        )

        val nonImpTask = TaskEntity(id = "t_non_imp", title = "Non Imp", isImportant = false)
        val nonImpSched = ScheduleEntity(
            id = "s_non_imp", taskId = "t_non_imp", scheduleType = ScheduleType.ONCE,
            startDate = fixedToday, endDate = fixedToday
        )

        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(listOf(impTask1, impTask2, nonImpTask), listOf(sched1, sched2, nonImpSched), emptyList()))

        val dotDates = db.getImportantDates(fixedToday.minusDays(5), fixedToday.plusDays(5))
        assertEquals(setOf(fixedToday.minusDays(2), fixedToday.plusDays(3)), dotDates)
        assertTrue(db.hasImportantTask(fixedToday.minusDays(2)))
        assertFalse(db.hasImportantTask(fixedToday)) // Only non-important task today
    }

    // ========================================================================
    // STEP 2B1: TASK + SCHEDULE MUTATION TESTS
    // ========================================================================

    @Test
    fun testCreateOnceDatedTaskCreatesExactlyOneTaskAndOneSchedule() {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))
        val taskId = db.createDatedTask(
            title = "Single Meeting",
            note = "Notes",
            date = fixedToday,
            time = LocalTime.of(15, 0),
            reminderMinutesBefore = 10,
            recurrence = null,
            isImportant = true,
            plannerToday = fixedToday
        )

        assertEquals(1, db.tasks.value.size)
        assertEquals(1, db.schedules.value.size)
        val task = db.tasks.value[0]
        val schedule = db.schedules.value[0]

        assertEquals(taskId, task.id)
        assertEquals("Single Meeting", task.title)
        assertEquals("Notes", task.note)
        assertTrue(task.isImportant)
        assertEquals(taskId, schedule.taskId)
        assertEquals(ScheduleType.ONCE, schedule.scheduleType)
        assertEquals(fixedToday, schedule.startDate)
        assertEquals(fixedToday, schedule.endDate)
        assertEquals(LocalTime.of(15, 0), schedule.scheduledTime)
        assertEquals(10, schedule.reminderMinutesBefore)
    }

    @Test
    fun testCreateRecurringTaskSetsCorrectCanonicalScheduleFields() {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))
        val taskId = db.createDatedTask(
            title = "Biweekly Sync",
            date = fixedToday,
            recurrence = com.vikaspokala.daybyday.ui.models.Recurrence.IntervalDays(2, fixedToday.plusDays(10)),
            plannerToday = fixedToday
        )

        assertEquals(1, db.schedules.value.size)
        val schedule = db.schedules.value[0]
        assertEquals(taskId, schedule.taskId)
        assertEquals(ScheduleType.INTERVAL_DAYS, schedule.scheduleType)
        assertEquals(2, schedule.intervalDays)
        assertEquals(fixedToday, schedule.intervalAnchorDate)
        assertEquals(fixedToday.plusDays(10), schedule.endDate)
    }

    @Test
    fun testCreateLaterCreatesTaskWithZeroSchedules() {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))
        val taskId = db.createLaterTask(
            title = "Someday idea",
            note = "Interesting project",
            isImportant = true
        )

        assertEquals(1, db.tasks.value.size)
        assertEquals(0, db.schedules.value.size)
        assertEquals(0, db.completions.value.size)

        val task = db.tasks.value[0]
        assertEquals(taskId, task.id)
        assertEquals("Someday idea", task.title)
        assertTrue(task.isImportant)

        val laterTasks = db.getLaterTasks()
        assertEquals(1, laterTasks.size)
        assertEquals(taskId, laterTasks[0].id)
    }

    @Test
    fun testHistoricalOnceWithoutReminderSucceeds() {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))
        val taskId = db.createDatedTask(
            title = "Past Log",
            date = fixedToday.minusDays(5),
            time = LocalTime.of(10, 0),
            reminderMinutesBefore = null,
            recurrence = null,
            plannerToday = fixedToday
        )

        assertEquals(1, db.tasks.value.size)
        assertEquals(1, db.schedules.value.size)
        assertEquals(taskId, db.tasks.value[0].id)
    }

    @Test
    fun testHistoricalOnceWithReminderFails() {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))
        try {
            db.createDatedTask(
                title = "Past Log With Reminder",
                date = fixedToday.minusDays(5),
                time = LocalTime.of(10, 0),
                reminderMinutesBefore = 15,
                plannerToday = fixedToday
            )
            fail("Expected IllegalArgumentException for historical task with reminder")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("cannot have a reminder") == true)
        }
    }

    @Test
    fun testHistoricalRecurringCreationFails() {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))
        try {
            db.createDatedTask(
                title = "Past Recurring",
                date = fixedToday.minusDays(5),
                recurrence = com.vikaspokala.daybyday.ui.models.Recurrence.IntervalDays(1),
                plannerToday = fixedToday
            )
            fail("Expected IllegalArgumentException for historical recurring task")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("cannot be recurring") == true)
        }
    }

    @Test
    fun testContentOnlyEditOfHistoricalTaskSucceeds() {
        val task = TaskEntity(id = "hist_edit_t", title = "Old Title", note = "Old note", isImportant = false)
        val schedule = ScheduleEntity(
            id = "hist_edit_s", taskId = "hist_edit_t", scheduleType = ScheduleType.ONCE,
            startDate = fixedToday.minusDays(3), endDate = fixedToday.minusDays(3)
        )
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(listOf(task), listOf(schedule), emptyList()))

        db.updateTask(
            taskId = "hist_edit_t",
            title = "New Updated Title",
            note = "New updated note",
            isImportant = true,
            plannerToday = fixedToday
        )

        val updated = db.tasks.value[0]
        assertEquals("New Updated Title", updated.title)
        assertEquals("New updated note", updated.note)
        assertTrue(updated.isImportant)
        assertEquals(fixedToday.minusDays(3), db.schedules.value[0].startDate) // Schedule unchanged
    }

    @Test
    fun testHistoricalScheduleMutationFails() {
        val task = TaskEntity(id = "hist_mut_t", title = "Old Task")
        val schedule = ScheduleEntity(
            id = "hist_mut_s", taskId = "hist_mut_t", scheduleType = ScheduleType.ONCE,
            startDate = fixedToday.minusDays(3), endDate = fixedToday.minusDays(3)
        )
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(listOf(task), listOf(schedule), emptyList()))

        try {
            db.updateTask(
                taskId = "hist_mut_t",
                title = "Old Task",
                date = fixedToday.minusDays(2), // attempting to reschedule a historical task
                plannerToday = fixedToday
            )
            fail("Expected IllegalArgumentException when modifying schedule date of historical task")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Cannot modify schedule date of a historical task") == true)
        }
    }

    @Test
    fun testLaterToFutureSchedulePreservesSameTaskId() {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))
        val taskId = db.createLaterTask(title = "Buy Desk", note = "Standing desk", isImportant = true)

        assertEquals(1, db.getLaterTasks().size)
        assertEquals(0, db.getTasksForDate(fixedToday.plusDays(2)).size)

        db.scheduleLaterTask(
            taskId = taskId,
            date = fixedToday.plusDays(2),
            time = LocalTime.of(11, 0),
            reminderMinutesBefore = 30,
            plannerToday = fixedToday
        )

        // Preserves exact same task ID
        assertEquals(1, db.tasks.value.size)
        assertEquals(taskId, db.tasks.value[0].id)
        assertEquals(1, db.schedules.value.size)
        assertEquals(taskId, db.schedules.value[0].taskId)

        // Disappears from Later and appears in dated projection
        assertEquals(0, db.getLaterTasks().size)
        val datedTasks = db.getTasksForDate(fixedToday.plusDays(2))
        assertEquals(1, datedTasks.size)
        assertEquals(taskId, datedTasks[0].id)
        assertEquals("Buy Desk", datedTasks[0].title)
        assertEquals("11:00 AM", datedTasks[0].time)
        assertEquals("30 minutes before", datedTasks[0].reminder)
    }

    @Test
    fun testLaterToPastScheduleFails() {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))
        val taskId = db.createLaterTask(title = "Buy Desk")

        try {
            db.scheduleLaterTask(
                taskId = taskId,
                date = fixedToday.minusDays(1),
                plannerToday = fixedToday
            )
            fail("Expected IllegalArgumentException when scheduling Later task to past date")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Cannot schedule Later task to a historical date") == true)
        }
    }

    @Test
    fun testReminderWithoutTimeFails() {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))
        try {
            db.createDatedTask(
                title = "Task without time",
                date = fixedToday,
                time = null,
                reminderMinutesBefore = 15,
                plannerToday = fixedToday
            )
            fail("Expected IllegalArgumentException when reminder is set without scheduledTime")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Reminder requires scheduledTime") == true)
        }
    }

    @Test
    fun testInvalidRecurrenceEndDateFails() {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))
        try {
            db.createDatedTask(
                title = "Invalid recurrence end",
                date = fixedToday,
                recurrence = com.vikaspokala.daybyday.ui.models.Recurrence.IntervalDays(1, fixedToday.minusDays(1)), // endDate < startDate
                plannerToday = fixedToday
            )
            fail("Expected IllegalArgumentException when endDate < startDate")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("endDate cannot be before startDate") == true)
        }
    }

    @Test
    fun testDeleteCascadesTaskSchedulesCompletions() {
        val task = TaskEntity(id = "del_t", title = "To Delete")
        val sched = ScheduleEntity(id = "del_s", taskId = "del_t", scheduleType = ScheduleType.ONCE, startDate = fixedToday, endDate = fixedToday)
        val comp = CompletionEntity(
            id = "del_c", taskId = "del_t", scheduleId = "del_s", scheduledDate = fixedToday,
            completedDate = fixedToday, completedAtEpochMillis = 1787150000000L, titleSnapshot = "To Delete", isImportantSnapshot = false
        )
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(listOf(task), listOf(sched), listOf(comp)))

        assertEquals(1, db.tasks.value.size)
        assertEquals(1, db.schedules.value.size)
        assertEquals(1, db.completions.value.size)

        db.deleteTask("del_t")

        assertEquals(0, db.tasks.value.size)
        assertEquals(0, db.schedules.value.size)
        assertEquals(0, db.completions.value.size)
    }

    @Test
    fun testProjectionsImmediatelyReflectSuccessfulCanonicalMutations() {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))

        // Create dated task
        val t1 = db.createDatedTask(title = "Doctor", date = fixedToday, plannerToday = fixedToday)
        assertEquals(1, db.getTasksForDate(fixedToday).size)

        // Update task title and importance
        db.updateTask(taskId = t1, title = "Doctor Appointment", isImportant = true, plannerToday = fixedToday)
        val updatedItems = db.getTasksForDate(fixedToday)
        assertEquals("Doctor Appointment", updatedItems[0].title)
        assertTrue(updatedItems[0].isImportant)
        assertTrue(db.hasImportantTask(fixedToday))

        // Delete task
        db.deleteTask(t1)
        assertEquals(0, db.getTasksForDate(fixedToday).size)
        assertFalse(db.hasImportantTask(fixedToday))
    }

    @Test
    fun testNoDuplicateTaskScheduleIdsAfterMultipleCreates() {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))
        val taskIds = mutableSetOf<String>()
        val scheduleIds = mutableSetOf<String>()

        for (i in 1..20) {
            val taskId = db.createDatedTask(title = "Task $i", date = fixedToday.plusDays(i.toLong()), plannerToday = fixedToday)
            assertTrue("Task ID must be unique", taskIds.add(taskId))
        }

        for (schedule in db.schedules.value) {
            assertTrue("Schedule ID must be unique", scheduleIds.add(schedule.id))
        }

        assertEquals(20, db.tasks.value.size)
        assertEquals(20, db.schedules.value.size)
    }

    // ========================================================================
    // STEP 2B2: COMPLETION + UNDO + IMPORTANCE SYNCHRONIZATION TESTS
    // ========================================================================

    @Test
    fun testValidOnceOccurrenceCompletionCreatesExactlyOneCompletion() {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))
        val taskId = db.createDatedTask(title = "One-off Task", date = fixedToday, plannerToday = fixedToday)
        val scheduleId = db.schedules.value.first { it.taskId == taskId }.id

        db.completeTask(taskId = taskId, scheduleId = scheduleId, scheduledDate = fixedToday, completedDate = fixedToday, plannerToday = fixedToday)

        assertEquals(1, db.completions.value.size)
        val comp = db.completions.value[0]
        assertEquals(taskId, comp.taskId)
        assertEquals(scheduleId, comp.scheduleId)
        assertEquals(fixedToday, comp.scheduledDate)
        assertEquals(fixedToday, comp.completedDate)
        assertEquals("One-off Task", comp.titleSnapshot)
    }

    @Test
    fun testRecurringOccurrenceCompletionIsStored() {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))
        val taskId = db.createDatedTask(
            title = "Daily Routine",
            date = fixedToday,
            recurrence = com.vikaspokala.daybyday.ui.models.Recurrence.IntervalDays(1),
            plannerToday = fixedToday
        )
        val scheduleId = db.schedules.value.first { it.taskId == taskId }.id

        db.completeTask(taskId = taskId, scheduleId = scheduleId, scheduledDate = fixedToday, completedDate = fixedToday, plannerToday = fixedToday)

        assertEquals(1, db.completions.value.size)
        val comp = db.completions.value[0]
        assertEquals(taskId, comp.taskId)
        assertEquals(scheduleId, comp.scheduleId)
        assertEquals(fixedToday, comp.scheduledDate)
    }

    @Test
    fun testCompletingOneRecurringOccurrenceDoesNotCompleteAnother() {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))
        val taskId = db.createDatedTask(
            title = "Daily Walk",
            date = fixedToday.minusDays(1),
            recurrence = com.vikaspokala.daybyday.ui.models.Recurrence.IntervalDays(1),
            plannerToday = fixedToday.minusDays(1)
        )
        val scheduleId = db.schedules.value.first { it.taskId == taskId }.id

        // Complete yesterday's occurrence
        db.completeTask(taskId = taskId, scheduleId = scheduleId, scheduledDate = fixedToday.minusDays(1), completedDate = fixedToday.minusDays(1), plannerToday = fixedToday)

        val yesterdayTasks = db.getTasksForDate(fixedToday.minusDays(1))
        val todayTasks = db.getTasksForDate(fixedToday)

        assertTrue(yesterdayTasks[0].isCompleted)
        assertFalse(todayTasks[0].isCompleted)
    }

    @Test
    fun testInvalidOccurrenceIsRejected() {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))
        val taskId = db.createDatedTask(
            title = "Every 3 Days",
            date = fixedToday,
            recurrence = com.vikaspokala.daybyday.ui.models.Recurrence.IntervalDays(3),
            plannerToday = fixedToday
        )
        val scheduleId = db.schedules.value.first { it.taskId == taskId }.id

        try {
            // Day + 1 is NOT an occurrence
            db.completeTask(taskId = taskId, scheduleId = scheduleId, scheduledDate = fixedToday.plusDays(1), completedDate = fixedToday, plannerToday = fixedToday)
            fail("Expected IllegalArgumentException for invalid occurrence date")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("not a valid occurrence") == true)
        }
    }

    @Test
    fun testScheduleBelongingToAnotherTaskIsRejected() {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))
        val t1 = db.createDatedTask(title = "Task 1", date = fixedToday, plannerToday = fixedToday)
        val t2 = db.createDatedTask(title = "Task 2", date = fixedToday, plannerToday = fixedToday)
        val s2 = db.schedules.value.first { it.taskId == t2 }.id

        try {
            db.completeTask(taskId = t1, scheduleId = s2, scheduledDate = fixedToday, completedDate = fixedToday, plannerToday = fixedToday)
            fail("Expected IllegalArgumentException when schedule does not belong to task")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("does not belong to task") == true)
        }
    }

    @Test
    fun testHistoricalCompletedDateEqualsScheduledDateSucceeds() {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))
        val taskId = db.createDatedTask(title = "Past Task", date = fixedToday.minusDays(4), plannerToday = fixedToday)
        val scheduleId = db.schedules.value.first { it.taskId == taskId }.id

        db.completeTask(taskId = taskId, scheduleId = scheduleId, scheduledDate = fixedToday.minusDays(4), completedDate = fixedToday.minusDays(4), plannerToday = fixedToday)

        assertEquals(1, db.completions.value.size)
        assertEquals(fixedToday.minusDays(4), db.completions.value[0].completedDate)
    }

    @Test
    fun testPastOccurrenceCompletedTodaySucceeds() {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))
        val taskId = db.createDatedTask(title = "Past Task", date = fixedToday.minusDays(4), plannerToday = fixedToday)
        val scheduleId = db.schedules.value.first { it.taskId == taskId }.id

        // Completed today
        db.completeTask(taskId = taskId, scheduleId = scheduleId, scheduledDate = fixedToday.minusDays(4), completedDate = fixedToday, plannerToday = fixedToday)

        assertEquals(1, db.completions.value.size)
        assertEquals(fixedToday, db.completions.value[0].completedDate)
    }

    @Test
    fun testCompletedDateBeforeScheduledDateFails() {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))
        val taskId = db.createDatedTask(title = "Task", date = fixedToday.minusDays(2), plannerToday = fixedToday)
        val scheduleId = db.schedules.value.first { it.taskId == taskId }.id

        try {
            db.completeTask(taskId = taskId, scheduleId = scheduleId, scheduledDate = fixedToday.minusDays(2), completedDate = fixedToday.minusDays(3), plannerToday = fixedToday)
            fail("Expected IllegalArgumentException when completedDate < scheduledDate")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("cannot be before") == true)
        }
    }

    @Test
    fun testCompletedDateAfterPlannerTodayFails() {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))
        val taskId = db.createDatedTask(title = "Task", date = fixedToday, plannerToday = fixedToday)
        val scheduleId = db.schedules.value.first { it.taskId == taskId }.id

        try {
            db.completeTask(taskId = taskId, scheduleId = scheduleId, scheduledDate = fixedToday, completedDate = fixedToday.plusDays(1), plannerToday = fixedToday)
            fail("Expected IllegalArgumentException when completedDate > plannerToday")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("cannot be after plannerToday") == true)
        }
    }

    @Test
    fun testFutureOccurrenceCompletedTodaySucceeds() {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))
        val taskId = db.createDatedTask(title = "Future Task", date = fixedToday.plusDays(3), plannerToday = fixedToday)
        val scheduleId = db.schedules.value.first { it.taskId == taskId }.id

        // Early completion today (completedDate == plannerToday)
        db.completeTask(taskId = taskId, scheduleId = scheduleId, scheduledDate = fixedToday.plusDays(3), completedDate = fixedToday, plannerToday = fixedToday)

        assertEquals(1, db.completions.value.size)
        assertEquals(fixedToday, db.completions.value[0].completedDate)
        assertEquals(fixedToday.plusDays(3), db.completions.value[0].scheduledDate)
    }

    @Test
    fun testLaterCompletedDateEqualsPlannerTodaySucceeds() {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))
        val taskId = db.createLaterTask(title = "Later Task")

        db.completeTask(taskId = taskId, scheduleId = null, scheduledDate = null, completedDate = fixedToday, plannerToday = fixedToday)

        assertEquals(1, db.completions.value.size)
        val comp = db.completions.value[0]
        assertEquals(taskId, comp.taskId)
        assertNull(comp.scheduleId)
        assertNull(comp.scheduledDate)
        assertEquals(fixedToday, comp.completedDate)
        assertTrue(db.getLaterTasks()[0].isCompleted)
    }

    @Test
    fun testLaterHistoricalOrFutureCompletedDateFails() {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))
        val taskId = db.createLaterTask(title = "Later Task")

        try {
            db.completeTask(taskId = taskId, scheduleId = null, scheduledDate = null, completedDate = fixedToday.minusDays(1), plannerToday = fixedToday)
            fail("Expected IllegalArgumentException for Later completedDate != plannerToday")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Direct Later completedDate must equal plannerToday") == true)
        }
    }

    @Test
    fun testDuplicateCompleteIsIdempotentAndPreservesOriginalSnapshotTimestamp() {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))
        db.nowEpochMillis = { 1000L }

        val taskId = db.createDatedTask(title = "Initial Title", date = fixedToday, plannerToday = fixedToday)
        val scheduleId = db.schedules.value.first { it.taskId == taskId }.id

        // First completion
        db.completeTask(taskId = taskId, scheduleId = scheduleId, scheduledDate = fixedToday, completedDate = fixedToday, plannerToday = fixedToday)
        val originalComp = db.completions.value[0]
        assertEquals(1000L, originalComp.completedAtEpochMillis)
        assertEquals("Initial Title", originalComp.titleSnapshot)

        // Advance time provider and attempt duplicate completion
        db.nowEpochMillis = { 5000L }
        db.completeTask(taskId = taskId, scheduleId = scheduleId, scheduledDate = fixedToday, completedDate = fixedToday, plannerToday = fixedToday)

        // Exactly one completion with unchanged metadata
        assertEquals(1, db.completions.value.size)
        assertEquals(1000L, db.completions.value[0].completedAtEpochMillis)
        assertEquals("Initial Title", db.completions.value[0].titleSnapshot)
    }

    @Test
    fun testScheduledUndoRemovesOnlyExactOccurrence() {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))
        val taskId = db.createDatedTask(
            title = "Habit",
            date = fixedToday.minusDays(2),
            recurrence = com.vikaspokala.daybyday.ui.models.Recurrence.IntervalDays(1),
            plannerToday = fixedToday.minusDays(2)
        )
        val scheduleId = db.schedules.value.first { it.taskId == taskId }.id

        db.completeTask(taskId = taskId, scheduleId = scheduleId, scheduledDate = fixedToday.minusDays(2), completedDate = fixedToday.minusDays(2), plannerToday = fixedToday)
        db.completeTask(taskId = taskId, scheduleId = scheduleId, scheduledDate = fixedToday.minusDays(1), completedDate = fixedToday.minusDays(1), plannerToday = fixedToday)

        assertEquals(2, db.completions.value.size)

        // Undo only day -2
        db.undoTask(taskId = taskId, scheduleId = scheduleId, scheduledDate = fixedToday.minusDays(2))

        assertEquals(1, db.completions.value.size)
        assertEquals(fixedToday.minusDays(1), db.completions.value[0].scheduledDate)
    }

    @Test
    fun testLaterUndoWorksAndIsIdempotent() {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))
        val taskId = db.createLaterTask(title = "Later Task")

        db.completeTask(taskId = taskId, scheduleId = null, scheduledDate = null, completedDate = fixedToday, plannerToday = fixedToday)
        assertTrue(db.getLaterTasks()[0].isCompleted)

        db.undoTask(taskId = taskId)
        assertFalse(db.getLaterTasks()[0].isCompleted)

        // Idempotent second undo
        db.undoTask(taskId = taskId)
        assertEquals(0, db.completions.value.size)
    }

    @Test
    fun testCompletedNonImportantOnceRetainedButHiddenFromHistory() {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))
        val taskId = db.createDatedTask(title = "Unimportant Task", isImportant = false, date = fixedToday.minusDays(2), plannerToday = fixedToday)
        val scheduleId = db.schedules.value.first { it.taskId == taskId }.id

        db.completeTask(taskId = taskId, scheduleId = scheduleId, scheduledDate = fixedToday.minusDays(2), completedDate = fixedToday.minusDays(2), plannerToday = fixedToday)

        assertEquals(1, db.completions.value.size)
        assertEquals(0, db.getHistoryTasks(plannerToday = fixedToday).size)
    }

    @Test
    fun testMarkingItImportantLaterMakesHistoryVisibleWithoutChangingOriginalCompletionMetadata() {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))
        db.nowEpochMillis = { 2000L }

        val taskId = db.createDatedTask(title = "Task", isImportant = false, date = fixedToday.minusDays(2), plannerToday = fixedToday)
        val scheduleId = db.schedules.value.first { it.taskId == taskId }.id

        db.completeTask(taskId = taskId, scheduleId = scheduleId, scheduledDate = fixedToday.minusDays(2), completedDate = fixedToday.minusDays(2), plannerToday = fixedToday)
        assertEquals(0, db.getHistoryTasks(plannerToday = fixedToday).size)

        // Mark task important later
        db.updateTask(taskId = taskId, title = "Task", isImportant = true, plannerToday = fixedToday)

        val history = db.getHistoryTasks(plannerToday = fixedToday)
        assertEquals(1, history.size)
        assertEquals(taskId, history[0].id)
        assertTrue(history[0].isImportant)
        // Original completion timestamp preserved
        assertEquals(2000L, db.completions.value[0].completedAtEpochMillis)
    }

    @Test
    fun testUnmarkingHidesItAgain() {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))
        val taskId = db.createDatedTask(title = "Task", isImportant = true, date = fixedToday.minusDays(2), plannerToday = fixedToday)
        val scheduleId = db.schedules.value.first { it.taskId == taskId }.id

        db.completeTask(taskId = taskId, scheduleId = scheduleId, scheduledDate = fixedToday.minusDays(2), completedDate = fixedToday.minusDays(2), plannerToday = fixedToday)
        assertEquals(1, db.getHistoryTasks(plannerToday = fixedToday).size)

        // Unmark importance
        db.updateTask(taskId = taskId, title = "Task", isImportant = false, plannerToday = fixedToday)
        assertEquals(0, db.getHistoryTasks(plannerToday = fixedToday).size)
    }

    @Test
    fun testDirectLaterFollowsSameImportantBehavior() {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))
        val taskId = db.createLaterTask(title = "Later", isImportant = false)

        // Complete on yesterday (using yesterday as plannerToday)
        db.completeTask(taskId = taskId, scheduleId = null, scheduledDate = null, completedDate = fixedToday.minusDays(1), plannerToday = fixedToday.minusDays(1))
        assertEquals(0, db.getHistoryTasks(plannerToday = fixedToday).size)

        // Mark important
        db.updateTask(taskId = taskId, title = "Later", isImportant = true, plannerToday = fixedToday)
        assertEquals(1, db.getHistoryTasks(plannerToday = fixedToday).size)
    }

    @Test
    fun testRecurringCompletionNeverEntersHistory() {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))
        val taskId = db.createDatedTask(
            title = "Recurring Important",
            isImportant = true,
            date = fixedToday.minusDays(3),
            recurrence = com.vikaspokala.daybyday.ui.models.Recurrence.IntervalDays(1),
            plannerToday = fixedToday.minusDays(3)
        )
        val scheduleId = db.schedules.value.first { it.taskId == taskId }.id

        db.completeTask(taskId = taskId, scheduleId = scheduleId, scheduledDate = fixedToday.minusDays(3), completedDate = fixedToday.minusDays(3), plannerToday = fixedToday)

        // Completion is stored in database
        assertEquals(1, db.completions.value.size)
        // But strictly hidden from History
        assertEquals(0, db.getHistoryTasks(plannerToday = fixedToday).size)
    }

    @Test
    fun testTitleEditAfterCompletionDoesNotChangeTitleSnapshot() {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))
        val taskId = db.createDatedTask(title = "Original Title", date = fixedToday, plannerToday = fixedToday)
        val scheduleId = db.schedules.value.first { it.taskId == taskId }.id

        db.completeTask(taskId = taskId, scheduleId = scheduleId, scheduledDate = fixedToday, completedDate = fixedToday, plannerToday = fixedToday)
        assertEquals("Original Title", db.completions.value[0].titleSnapshot)

        // Edit live title
        db.updateTask(taskId = taskId, title = "Renamed Live Title", plannerToday = fixedToday)

        assertEquals("Renamed Live Title", db.tasks.value[0].title)
        assertEquals("Original Title", db.completions.value[0].titleSnapshot) // titleSnapshot stays immutable
    }

    @Test
    fun testUndoAndReCompleteCapturesFreshTitleImportanceTimestamp() {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))
        db.nowEpochMillis = { 1000L }

        val taskId = db.createDatedTask(title = "Title A", isImportant = false, date = fixedToday, plannerToday = fixedToday)
        val scheduleId = db.schedules.value.first { it.taskId == taskId }.id

        db.completeTask(taskId = taskId, scheduleId = scheduleId, scheduledDate = fixedToday, completedDate = fixedToday, plannerToday = fixedToday)
        assertEquals(1000L, db.completions.value[0].completedAtEpochMillis)
        assertEquals("Title A", db.completions.value[0].titleSnapshot)

        // Undo
        db.undoTask(taskId = taskId, scheduleId = scheduleId, scheduledDate = fixedToday)
        assertEquals(0, db.completions.value.size)

        // Edit task
        db.updateTask(taskId = taskId, title = "Title B", isImportant = true, plannerToday = fixedToday)

        // Re-complete with updated time
        db.nowEpochMillis = { 9000L }
        db.completeTask(taskId = taskId, scheduleId = scheduleId, scheduledDate = fixedToday, completedDate = fixedToday, plannerToday = fixedToday)

        assertEquals(1, db.completions.value.size)
        val newComp = db.completions.value[0]
        assertEquals("Title B", newComp.titleSnapshot)
        assertTrue(newComp.isImportantSnapshot)
        assertEquals(9000L, newComp.completedAtEpochMillis)
    }

    @Test
    fun testHistoricalImportantCompletionImmediatelyAppearsInHistory() {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))
        val taskId = db.createDatedTask(title = "Historical Tax", isImportant = true, date = fixedToday.minusDays(3), plannerToday = fixedToday)
        val scheduleId = db.schedules.value.first { it.taskId == taskId }.id

        db.completeTask(taskId = taskId, scheduleId = scheduleId, scheduledDate = fixedToday.minusDays(3), completedDate = fixedToday.minusDays(3), plannerToday = fixedToday)

        val history = db.getHistoryTasks(plannerToday = fixedToday)
        assertEquals(1, history.size)
        assertEquals("Historical Tax", history[0].title)
    }

    @Test
    fun testCompletionMadeOnPlannerTodayRemainsOutsideHistoryThatDay() {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))
        val taskId = db.createDatedTask(title = "Today Done", isImportant = true, date = fixedToday, plannerToday = fixedToday)
        val scheduleId = db.schedules.value.first { it.taskId == taskId }.id

        db.completeTask(taskId = taskId, scheduleId = scheduleId, scheduledDate = fixedToday, completedDate = fixedToday, plannerToday = fixedToday)

        // Visible in today's dated tasks as completed
        assertTrue(db.getTasksForDate(fixedToday)[0].isCompleted)
        // But NOT in History on the day it was completed
        assertEquals(0, db.getHistoryTasks(plannerToday = fixedToday).size)

        // When plannerToday advances tomorrow, it appears in History!
        val historyTomorrow = db.getHistoryTasks(plannerToday = fixedToday.plusDays(1))
        assertEquals(1, historyTomorrow.size)
        assertEquals("Today Done", historyTomorrow[0].title)
    }

    @Test
    fun testFailedCompletionMutationLeavesCanonicalStateUnchanged() {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))
        val taskId = db.createDatedTask(title = "Task", date = fixedToday, plannerToday = fixedToday)
        val scheduleId = db.schedules.value.first { it.taskId == taskId }.id

        val initialCompletions = db.completions.value.toList()

        try {
            // Illegal completion date (after plannerToday)
            db.completeTask(taskId = taskId, scheduleId = scheduleId, scheduledDate = fixedToday, completedDate = fixedToday.plusDays(2), plannerToday = fixedToday)
            fail("Expected exception")
        } catch (e: Exception) {
            // Verify state is left completely intact
            assertEquals(initialCompletions, db.completions.value)
        }
    }

    // ========================================================================
    // TIMEZONE SEMANTICS TESTS
    // ========================================================================

    @Test
    fun testCompletionEpochInKolkataProjectsAsIntendedLocalTime() {
        val kolkataZone = java.time.ZoneId.of("Asia/Kolkata")
        val compDate = fixedToday.minusDays(2)
        val epoch = compDate.atTime(LocalTime.of(9, 30)).atZone(kolkataZone).toInstant().toEpochMilli()

        val task = TaskEntity(id = "tz_t1", title = "Kolkata Task", isImportant = true)
        val sched = ScheduleEntity(id = "tz_s1", taskId = "tz_t1", scheduleType = ScheduleType.ONCE, startDate = compDate, endDate = compDate)
        val comp = CompletionEntity(
            id = "tz_c1", taskId = "tz_t1", scheduleId = "tz_s1", scheduledDate = compDate,
            completedDate = compDate, completedAtEpochMillis = epoch, titleSnapshot = "Kolkata Task", isImportantSnapshot = true
        )
        val db = InMemoryPlannerDatabase(referenceDate = fixedToday, zoneId = kolkataZone, seed = CanonicalPlannerSeed(listOf(task), listOf(sched), listOf(comp)))

        val history = db.getHistoryTasks(plannerToday = fixedToday)
        assertEquals(1, history.size)
        assertEquals("9:30 AM", history[0].completedAtTime)
        assertEquals(570, history[0].completedAtMinutes)
    }

    @Test
    fun testSameAbsoluteEpochProjectsDifferentlyInDifferentTimezones() {
        val kolkataZone = java.time.ZoneId.of("Asia/Kolkata")
        val utcZone = java.time.ZoneId.of("UTC")
        val compDate = fixedToday.minusDays(2)
        // 9:30 AM IST = 4:00 AM UTC
        val epoch = compDate.atTime(LocalTime.of(9, 30)).atZone(kolkataZone).toInstant().toEpochMilli()

        val task = TaskEntity(id = "tz_t1", title = "Timezone Test", isImportant = true)
        val sched = ScheduleEntity(id = "tz_s1", taskId = "tz_t1", scheduleType = ScheduleType.ONCE, startDate = compDate, endDate = compDate)
        val comp = CompletionEntity(
            id = "tz_c1", taskId = "tz_t1", scheduleId = "tz_s1", scheduledDate = compDate,
            completedDate = compDate, completedAtEpochMillis = epoch, titleSnapshot = "Timezone Test", isImportantSnapshot = true
        )
        val seed = CanonicalPlannerSeed(listOf(task), listOf(sched), listOf(comp))

        val dbKolkata = InMemoryPlannerDatabase(referenceDate = fixedToday, zoneId = kolkataZone, seed = seed)
        assertEquals("9:30 AM", dbKolkata.getHistoryTasks(plannerToday = fixedToday)[0].completedAtTime)

        val dbUtc = InMemoryPlannerDatabase(referenceDate = fixedToday, zoneId = utcZone, seed = seed)
        assertEquals("4:00 AM", dbUtc.getHistoryTasks(plannerToday = fixedToday)[0].completedAtTime)
    }

    @Test
    fun testCanonicalSeedIntendedCompletionTimeProjectsCorrectlyInSuppliedZone() {
        val nyZone = java.time.ZoneId.of("America/New_York")
        val dbNY = InMemoryPlannerDatabase(referenceDate = fixedToday, zoneId = nyZone)

        val history = dbNY.getHistoryTasks(plannerToday = fixedToday)
        val prepSlidesComp = history.find { it.title == "Prep presentation slides" }
        assertNotNull(prepSlidesComp)
        assertEquals("11:00 AM", prepSlidesComp?.completedAtTime)
        assertEquals(660, prepSlidesComp?.completedAtMinutes)
    }

    @Test
    fun testRuntimeEpochProjectionCalculatesCompletedAtMinutesCorrectly() {
        val tokyoZone = java.time.ZoneId.of("Asia/Tokyo")
        val compDate = fixedToday.minusDays(1)
        val epoch = compDate.atTime(LocalTime.of(23, 45)).atZone(tokyoZone).toInstant().toEpochMilli()

        val task = TaskEntity(id = "tz_t3", title = "Tokyo Night Task", isImportant = true)
        val sched = ScheduleEntity(id = "tz_s3", taskId = "tz_t3", scheduleType = ScheduleType.ONCE, startDate = compDate, endDate = compDate)
        val comp = CompletionEntity(
            id = "tz_c3", taskId = "tz_t3", scheduleId = "tz_s3", scheduledDate = compDate,
            completedDate = compDate, completedAtEpochMillis = epoch, titleSnapshot = "Tokyo Night Task", isImportantSnapshot = true
        )
        val db = InMemoryPlannerDatabase(referenceDate = fixedToday, zoneId = tokyoZone, seed = CanonicalPlannerSeed(listOf(task), listOf(sched), listOf(comp)))

        val history = db.getHistoryTasks(plannerToday = fixedToday)
        assertEquals("11:45 PM", history[0].completedAtTime)
        assertEquals(23 * 60 + 45, history[0].completedAtMinutes)
    }

    @Test
    fun testHistoryOrderingRemainsBasedOnAbsoluteEpochMillis() {
        val zone = java.time.ZoneId.of("UTC")
        val compDate = fixedToday.minusDays(2)

        val t1 = TaskEntity(id = "t1", title = "Task 1", isImportant = true)
        val s1 = ScheduleEntity(id = "s1", taskId = "t1", scheduleType = ScheduleType.ONCE, startDate = compDate, endDate = compDate)
        val c1 = CompletionEntity(id = "c1", taskId = "t1", scheduleId = "s1", scheduledDate = compDate, completedDate = compDate, completedAtEpochMillis = 1000L, titleSnapshot = "Task 1", isImportantSnapshot = true)

        val t2 = TaskEntity(id = "t2", title = "Task 2", isImportant = true)
        val s2 = ScheduleEntity(id = "s2", taskId = "t2", scheduleType = ScheduleType.ONCE, startDate = compDate, endDate = compDate)
        val c2 = CompletionEntity(id = "c2", taskId = "t2", scheduleId = "s2", scheduledDate = compDate, completedDate = compDate, completedAtEpochMillis = 2000L, titleSnapshot = "Task 2", isImportantSnapshot = true)

        val db = InMemoryPlannerDatabase(referenceDate = fixedToday, zoneId = zone, seed = CanonicalPlannerSeed(listOf(t1, t2), listOf(s1, s2), listOf(c1, c2)))

        val history = db.getHistoryTasks(plannerToday = fixedToday)
        assertEquals(2, history.size)
        assertEquals("Task 2", history[0].title) // Newer epoch first
        assertEquals("Task 1", history[1].title)
    }

    // ========================================================================
    // REACTIVE CANONICAL STATE / FLOW TESTS
    // ========================================================================

    @Test
    fun testObserveTasksForDateEmitsUpdatedListAfterDatedTaskCreation() = runBlocking {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))
        assertEquals(0, db.observeTasksForDate(fixedToday).first().size)

        db.createDatedTask(title = "New Meeting", date = fixedToday, plannerToday = fixedToday)

        val emitted = db.observeTasksForDate(fixedToday).first()
        assertEquals(1, emitted.size)
        assertEquals("New Meeting", emitted[0].title)
    }

    @Test
    fun testCreatingDatedTaskDoesNotExposeTaskWithoutScheduleIntermediateState() = runBlocking {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))

        // In any state emitted by db.state, task count == schedule count
        db.createDatedTask(title = "Task A", date = fixedToday, plannerToday = fixedToday)
        val currentState = db.state.first()
        assertEquals(1, currentState.tasks.size)
        assertEquals(1, currentState.schedules.size)
        assertEquals(currentState.tasks[0].id, currentState.schedules[0].taskId)
    }

    @Test
    fun testObserveLaterTasksUpdatesAfterLaterCreation() = runBlocking {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))
        assertEquals(0, db.observeLaterTasks().first().size)

        val taskId = db.createLaterTask(title = "Someday Book", isImportant = true)
        val laterList = db.observeLaterTasks().first()

        assertEquals(1, laterList.size)
        assertEquals(taskId, laterList[0].id)
        assertEquals("Someday Book", laterList[0].title)
    }

    @Test
    fun testLaterToScheduleRemovesFromLaterAndExposesSameTaskIdInDatedFlow() = runBlocking {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))
        val taskId = db.createLaterTask(title = "Later to Schedule", isImportant = true)

        assertEquals(1, db.observeLaterTasks().first().size)
        assertEquals(0, db.observeTasksForDate(fixedToday.plusDays(1)).first().size)

        db.scheduleLaterTask(taskId = taskId, date = fixedToday.plusDays(1), plannerToday = fixedToday)

        // Reactively updated
        assertEquals(0, db.observeLaterTasks().first().size)
        val datedList = db.observeTasksForDate(fixedToday.plusDays(1)).first()
        assertEquals(1, datedList.size)
        assertEquals(taskId, datedList[0].id)
    }

    @Test
    fun testCompletionUpdatesDatedProjectionIsCompletedFlow() = runBlocking {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))
        val taskId = db.createDatedTask(title = "Complete Flow Task", date = fixedToday, plannerToday = fixedToday)
        val scheduleId = db.schedules.value.first().id

        assertFalse(db.observeTasksForDate(fixedToday).first()[0].isCompleted)

        db.completeTask(taskId = taskId, scheduleId = scheduleId, scheduledDate = fixedToday, completedDate = fixedToday, plannerToday = fixedToday)

        assertTrue(db.observeTasksForDate(fixedToday).first()[0].isCompleted)
    }

    @Test
    fun testUndoUpdatesDatedProjectionIsCompletedFlow() = runBlocking {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))
        val taskId = db.createDatedTask(title = "Undo Flow Task", date = fixedToday, plannerToday = fixedToday)
        val scheduleId = db.schedules.value.first().id

        db.completeTask(taskId = taskId, scheduleId = scheduleId, scheduledDate = fixedToday, completedDate = fixedToday, plannerToday = fixedToday)
        assertTrue(db.observeTasksForDate(fixedToday).first()[0].isCompleted)

        db.undoTask(taskId = taskId, scheduleId = scheduleId, scheduledDate = fixedToday)
        assertFalse(db.observeTasksForDate(fixedToday).first()[0].isCompleted)
    }

    @Test
    fun testCompletedEligibleImportanceChangeUpdatesHistoryFlow() = runBlocking {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))
        val taskId = db.createDatedTask(title = "History Flow Task", isImportant = false, date = fixedToday.minusDays(2), plannerToday = fixedToday)
        val scheduleId = db.schedules.value.first().id

        db.completeTask(taskId = taskId, scheduleId = scheduleId, scheduledDate = fixedToday.minusDays(2), completedDate = fixedToday.minusDays(2), plannerToday = fixedToday)
        assertEquals(0, db.observeHistoryTasks(plannerToday = fixedToday).first().size)

        // Mark task important
        db.updateTask(taskId = taskId, title = "History Flow Task", isImportant = true, plannerToday = fixedToday)

        val historyList = db.observeHistoryTasks(plannerToday = fixedToday).first()
        assertEquals(1, historyList.size)
        assertEquals(taskId, historyList[0].id)
    }

    @Test
    fun testDeleteRemovesTaskConsistentlyFromRelevantProjections() = runBlocking {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))
        val taskId = db.createDatedTask(title = "To Delete", isImportant = true, date = fixedToday, plannerToday = fixedToday)

        assertEquals(1, db.observeTasksForDate(fixedToday).first().size)
        assertEquals(setOf(fixedToday), db.observeImportantDates(fixedToday.minusDays(1), fixedToday.plusDays(1)).first())

        db.deleteTask(taskId)

        assertEquals(0, db.observeTasksForDate(fixedToday).first().size)
        assertEquals(emptySet<LocalDate>(), db.observeImportantDates(fixedToday.minusDays(1), fixedToday.plusDays(1)).first())
    }

    @Test
    fun testRecurringOccurrenceCompletionOnlyUpdatesThatOccurrenceInFlow() = runBlocking {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))
        val taskId = db.createDatedTask(
            title = "Recurring Habit",
            date = fixedToday.minusDays(1),
            recurrence = com.vikaspokala.daybyday.ui.models.Recurrence.IntervalDays(1),
            plannerToday = fixedToday.minusDays(1)
        )
        val scheduleId = db.schedules.value.first().id

        // Complete yesterday
        db.completeTask(taskId = taskId, scheduleId = scheduleId, scheduledDate = fixedToday.minusDays(1), completedDate = fixedToday.minusDays(1), plannerToday = fixedToday)

        val yesterdayList = db.observeTasksForDate(fixedToday.minusDays(1)).first()
        val todayList = db.observeTasksForDate(fixedToday).first()

        assertTrue(yesterdayList[0].isCompleted)
        assertFalse(todayList[0].isCompleted)
    }

    @Test
    fun testFailedMutationProducesNoNewObservableState() = runBlocking {
        val db = InMemoryPlannerDatabase(seed = CanonicalPlannerSeed(emptyList(), emptyList(), emptyList()))
        val initialPlanState = db.state.first()

        try {
            db.createDatedTask(title = "   ", date = fixedToday, plannerToday = fixedToday) // Blank title
        } catch (e: IllegalArgumentException) {
            // Expected
        }

        val afterFailedState = db.state.first()
        assertEquals(initialPlanState, afterFailedState)
    }
}





