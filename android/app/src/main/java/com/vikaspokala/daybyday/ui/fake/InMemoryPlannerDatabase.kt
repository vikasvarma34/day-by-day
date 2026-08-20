package com.vikaspokala.daybyday.ui.fake

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID
import com.vikaspokala.daybyday.ui.models.Recurrence
import com.vikaspokala.daybyday.ui.screens.history.HistoryTaskItem
import com.vikaspokala.daybyday.ui.screens.later.LaterTaskItem
import com.vikaspokala.daybyday.ui.screens.schedule.ScheduleTaskItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

enum class ScheduleType {
    ONCE,
    INTERVAL_DAYS,
    WEEKDAYS
}

data class TaskEntity(
    val id: String,
    val title: String,
    val note: String? = null,
    val isImportant: Boolean = false,
    val createdAtEpochMillis: Long = 0L
)

data class ScheduleEntity(
    val id: String,
    val taskId: String,
    val scheduleType: ScheduleType,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val scheduledTime: LocalTime? = null,
    val reminderMinutesBefore: Int? = null,
    val intervalDays: Int? = null,
    val intervalAnchorDate: LocalDate? = null,
    val weekdays: Set<Int>? = null
)

data class CompletionEntity(
    val id: String,
    val taskId: String,
    val scheduleId: String? = null,
    val scheduledDate: LocalDate? = null,
    val completedDate: LocalDate,
    val completedAtEpochMillis: Long,
    val titleSnapshot: String,
    val isImportantSnapshot: Boolean
)

data class CanonicalPlannerSeed(
    val tasks: List<TaskEntity>,
    val schedules: List<ScheduleEntity>,
    val completions: List<CompletionEntity>
)

data class PlannerState(
    val tasks: List<TaskEntity> = emptyList(),
    val schedules: List<ScheduleEntity> = emptyList(),
    val completions: List<CompletionEntity> = emptyList()
)

@OptIn(kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi::class)
private class DerivedStateFlow<T>(
    private val parent: StateFlow<PlannerState>,
    private val selector: (PlannerState) -> T
) : StateFlow<T> {
    override val value: T get() = selector(parent.value)
    override val replayCache: List<T> get() = listOf(value)
    override suspend fun collect(collector: FlowCollector<T>): Nothing {
        parent.map { selector(it) }.distinctUntilChanged().collect(collector)
        kotlinx.coroutines.awaitCancellation()
    }
}

class InMemoryPlannerDatabase(
    referenceDate: LocalDate = LocalDate.now(),
    val zoneId: ZoneId = ZoneId.systemDefault(),
    seed: CanonicalPlannerSeed = FakePlannerData.getCanonicalSeed(referenceDate, zoneId)
) {
    var nowEpochMillis: () -> Long = { System.currentTimeMillis() }

    private val _state = MutableStateFlow(PlannerState())
    val state: StateFlow<PlannerState> = _state.asStateFlow()

    val tasks: StateFlow<List<TaskEntity>> = DerivedStateFlow(_state) { it.tasks }
    val schedules: StateFlow<List<ScheduleEntity>> = DerivedStateFlow(_state) { it.schedules }
    val completions: StateFlow<List<CompletionEntity>> = DerivedStateFlow(_state) { it.completions }

    init {
        loadCanonicalSeed(seed)
    }

    @Synchronized
    fun loadCanonicalSeed(seed: CanonicalPlannerSeed) {
        validateInvariants(seed.tasks, seed.schedules, seed.completions)
        _state.value = PlannerState(
            tasks = seed.tasks,
            schedules = seed.schedules,
            completions = seed.completions
        )
    }

    @Synchronized
    fun reset(referenceDate: LocalDate = LocalDate.now()) {
        loadCanonicalSeed(FakePlannerData.getCanonicalSeed(referenceDate, zoneId))
    }

    fun validateInvariants(
        tasks: List<TaskEntity>,
        schedules: List<ScheduleEntity>,
        completions: List<CompletionEntity>
    ) {
        val taskIds = tasks.map { it.id }.toSet()
        require(taskIds.size == tasks.size) { "Duplicate task IDs found" }

        val scheduleIds = schedules.map { it.id }.toSet()
        require(scheduleIds.size == schedules.size) { "Duplicate schedule IDs found" }

        val completionIds = completions.map { it.id }.toSet()
        require(completionIds.size == completions.size) { "Duplicate completion IDs found" }

        // Invariant 1: Every schedule references an existing task and satisfies type and reminder invariants
        schedules.forEach { s ->
            require(s.taskId in taskIds) {
                "Schedule ${s.id} references non-existent task ${s.taskId}"
            }
            require(s.endDate == null || !s.endDate.isBefore(s.startDate)) {
                "Schedule ${s.id} endDate cannot be before startDate"
            }

            if (s.reminderMinutesBefore != null) {
                require(s.reminderMinutesBefore in ALLOWED_REMINDER_MINUTES) {
                    "Schedule ${s.id} has invalid reminderMinutesBefore: ${s.reminderMinutesBefore}"
                }
                require(s.scheduledTime != null) {
                    "Schedule ${s.id} has reminderMinutesBefore but scheduledTime is null"
                }
            }

            when (s.scheduleType) {
                ScheduleType.ONCE -> {
                    require(s.endDate == s.startDate) {
                        "ONCE schedule ${s.id} must have endDate == startDate"
                    }
                    require(s.intervalDays == null) {
                        "ONCE schedule ${s.id} must not have intervalDays"
                    }
                    require(s.intervalAnchorDate == null) {
                        "ONCE schedule ${s.id} must not have intervalAnchorDate"
                    }
                    require(s.weekdays == null) {
                        "ONCE schedule ${s.id} must not have weekdays"
                    }
                }
                ScheduleType.INTERVAL_DAYS -> {
                    require(s.intervalDays != null && s.intervalDays >= 1) {
                        "INTERVAL_DAYS schedule ${s.id} must have intervalDays >= 1"
                    }
                    require(s.intervalAnchorDate != null) {
                        "INTERVAL_DAYS schedule ${s.id} must have intervalAnchorDate"
                    }
                    require(s.weekdays == null) {
                        "INTERVAL_DAYS schedule ${s.id} must not have weekdays"
                    }
                }
                ScheduleType.WEEKDAYS -> {
                    require(!s.weekdays.isNullOrEmpty() && s.weekdays.all { it in 1..7 }) {
                        "WEEKDAYS schedule ${s.id} must have non-empty weekdays in 1..7"
                    }
                    require(s.intervalDays == null) {
                        "WEEKDAYS schedule ${s.id} must not have intervalDays"
                    }
                    require(s.intervalAnchorDate == null) {
                        "WEEKDAYS schedule ${s.id} must not have intervalAnchorDate"
                    }
                }
            }
        }

        val scheduledTaskIds = schedules.map { it.taskId }.toSet()

        // Invariant 2: Every completion references an existing task and valid schedule
        completions.forEach { c ->
            require(c.taskId in taskIds) {
                "Completion ${c.id} references non-existent task ${c.taskId}"
            }

            if (c.scheduleId == null) {
                require(c.scheduledDate == null) {
                    "Direct Later completion ${c.id} must have null scheduledDate"
                }
                require(c.taskId !in scheduledTaskIds) {
                    "Direct Later completion ${c.id} references task ${c.taskId} which has active schedule"
                }
            } else {
                require(c.scheduledDate != null) {
                    "Scheduled completion ${c.id} must have non-null scheduledDate"
                }
                val schedule = schedules.find { it.id == c.scheduleId }
                require(schedule != null) {
                    "Completion ${c.id} references non-existent schedule ${c.scheduleId}"
                }
                require(schedule.taskId == c.taskId) {
                    "Completion ${c.id} schedule ${c.scheduleId} does not belong to task ${c.taskId}"
                }
            }
        }

        // Invariant 3: Unique occurrences (taskId, scheduledDate)
        val occurrenceKeys = mutableSetOf<Pair<String, LocalDate?>>()
        completions.forEach { c ->
            val key = Pair(c.taskId, c.scheduledDate)
            require(occurrenceKeys.add(key)) {
                "Duplicate completion for occurrence (taskId=${c.taskId}, scheduledDate=${c.scheduledDate})"
            }
        }
    }

    // ========================================================================
    // MUTATIONS (Atomic State Updates)
    // ========================================================================

    @Synchronized
    fun createDatedTask(
        title: String,
        note: String? = null,
        date: LocalDate,
        time: LocalTime? = null,
        reminderMinutesBefore: Int? = null,
        recurrence: Recurrence? = null,
        isImportant: Boolean = false,
        plannerToday: LocalDate = LocalDate.now()
    ): String {
        val trimmedTitle = title.trim()
        require(trimmedTitle.isNotEmpty()) { "Title cannot be blank" }
        require(trimmedTitle.length <= 255) { "Invalid title: max 255 characters" }
        if (note != null) {
            require(note.length <= 500) { "Invalid note: max 500 characters" }
        }

        val isHistorical = date.isBefore(plannerToday)
        if (isHistorical) {
            require(recurrence == null || recurrence is Recurrence.Once) {
                "Historical task cannot be recurring"
            }
            require(reminderMinutesBefore == null) {
                "Historical task cannot have a reminder"
            }
        }

        if (reminderMinutesBefore != null) {
            require(reminderMinutesBefore in ALLOWED_REMINDER_MINUTES) {
                "Invalid reminderMinutesBefore: $reminderMinutesBefore"
            }
            require(time != null) {
                "Reminder requires scheduledTime"
            }
        }

        val scheduleType: ScheduleType
        val intervalDays: Int?
        val intervalAnchorDate: LocalDate?
        val weekdays: Set<Int>?
        val endDate: LocalDate?

        when (recurrence) {
            is Recurrence.IntervalDays -> {
                scheduleType = ScheduleType.INTERVAL_DAYS
                intervalDays = recurrence.intervalDays
                intervalAnchorDate = date
                weekdays = null
                endDate = recurrence.endDate
                require(intervalDays >= 1) { "intervalDays must be >= 1" }
                if (endDate != null) {
                    require(!endDate.isBefore(date)) { "endDate cannot be before startDate" }
                }
            }
            is Recurrence.Weekdays -> {
                scheduleType = ScheduleType.WEEKDAYS
                intervalDays = null
                intervalAnchorDate = null
                weekdays = recurrence.days
                endDate = recurrence.endDate
                require(weekdays.isNotEmpty() && weekdays.all { it in 1..7 }) {
                    "weekdays must be non-empty and in 1..7"
                }
                if (endDate != null) {
                    require(!endDate.isBefore(date)) { "endDate cannot be before startDate" }
                }
            }
            else -> {
                scheduleType = ScheduleType.ONCE
                intervalDays = null
                intervalAnchorDate = null
                weekdays = null
                endDate = date
            }
        }

        val taskId = "task_${UUID.randomUUID()}"
        val scheduleId = "sched_${UUID.randomUUID()}"
        val nowEpoch = nowEpochMillis()

        val newTask = TaskEntity(
            id = taskId,
            title = trimmedTitle,
            note = note,
            isImportant = isImportant,
            createdAtEpochMillis = nowEpoch
        )

        val newSchedule = ScheduleEntity(
            id = scheduleId,
            taskId = taskId,
            scheduleType = scheduleType,
            startDate = date,
            endDate = endDate,
            scheduledTime = time,
            reminderMinutesBefore = reminderMinutesBefore,
            intervalDays = intervalDays,
            intervalAnchorDate = intervalAnchorDate,
            weekdays = weekdays
        )

        val current = _state.value
        val newTasks = current.tasks + newTask
        val newSchedules = current.schedules + newSchedule

        validateInvariants(newTasks, newSchedules, current.completions)

        _state.value = PlannerState(
            tasks = newTasks,
            schedules = newSchedules,
            completions = current.completions
        )

        return taskId
    }

    @Synchronized
    fun createLaterTask(
        title: String,
        note: String? = null,
        isImportant: Boolean = false
    ): String {
        val trimmedTitle = title.trim()
        require(trimmedTitle.isNotEmpty()) { "Title cannot be blank" }
        require(trimmedTitle.length <= 255) { "Invalid title: max 255 characters" }
        if (note != null) {
            require(note.length <= 500) { "Invalid note: max 500 characters" }
        }

        val taskId = "task_${UUID.randomUUID()}"
        val newTask = TaskEntity(
            id = taskId,
            title = trimmedTitle,
            note = note,
            isImportant = isImportant,
            createdAtEpochMillis = nowEpochMillis()
        )

        val current = _state.value
        val newTasks = current.tasks + newTask
        validateInvariants(newTasks, current.schedules, current.completions)

        _state.value = PlannerState(
            tasks = newTasks,
            schedules = current.schedules,
            completions = current.completions
        )
        return taskId
    }

    @Synchronized
    fun updateTask(
        taskId: String,
        title: String,
        note: String? = null,
        date: LocalDate? = null,
        time: LocalTime? = null,
        reminderMinutesBefore: Int? = null,
        recurrence: Recurrence? = null,
        isImportant: Boolean? = null,
        plannerToday: LocalDate = LocalDate.now()
    ) {
        val current = _state.value
        val existingTask = current.tasks.find { it.id == taskId }
            ?: throw IllegalArgumentException("Task $taskId not found")

        val trimmedTitle = title.trim()
        require(trimmedTitle.isNotEmpty()) { "Title cannot be blank" }
        require(trimmedTitle.length <= 255) { "Invalid title: max 255 characters" }
        if (note != null) {
            require(note.length <= 500) { "Invalid note: max 500 characters" }
        }

        val existingSchedule = current.schedules.find { it.taskId == taskId }
        val effectiveImportant = isImportant ?: existingTask.isImportant

        val updatedTask = existingTask.copy(
            title = trimmedTitle,
            note = note,
            isImportant = effectiveImportant
        )

        var newSchedules = current.schedules

        if (existingSchedule != null) {
            val isHistorical = existingSchedule.startDate.isBefore(plannerToday)
            val targetDate = date ?: existingSchedule.startDate

            if (isHistorical) {
                if (date != null && date != existingSchedule.startDate) {
                    throw IllegalArgumentException("Cannot modify schedule date of a historical task")
                }
                if (recurrence != null && scheduleEntityToRecurrence(existingSchedule) != recurrence) {
                    throw IllegalArgumentException("Cannot modify recurrence of a historical task")
                }
                if (reminderMinutesBefore != null && reminderMinutesBefore != existingSchedule.reminderMinutesBefore) {
                    throw IllegalArgumentException("Cannot modify reminder of a historical task")
                }
            } else {
                if (targetDate.isBefore(plannerToday)) {
                    throw IllegalArgumentException("Cannot move task to a historical date")
                }

                if (reminderMinutesBefore != null) {
                    require(reminderMinutesBefore in ALLOWED_REMINDER_MINUTES) {
                        "Invalid reminderMinutesBefore: $reminderMinutesBefore"
                    }
                    require(time != null) {
                        "Reminder requires scheduledTime"
                    }
                }

                val scheduleType: ScheduleType
                val intervalDays: Int?
                val intervalAnchorDate: LocalDate?
                val weekdays: Set<Int>?
                val endDate: LocalDate?

                when (recurrence) {
                    is Recurrence.IntervalDays -> {
                        scheduleType = ScheduleType.INTERVAL_DAYS
                        intervalDays = recurrence.intervalDays
                        intervalAnchorDate = targetDate
                        weekdays = null
                        endDate = recurrence.endDate
                        require(intervalDays >= 1) { "intervalDays must be >= 1" }
                        if (endDate != null) {
                            require(!endDate.isBefore(targetDate)) { "endDate cannot be before startDate" }
                        }
                    }
                    is Recurrence.Weekdays -> {
                        scheduleType = ScheduleType.WEEKDAYS
                        intervalDays = null
                        intervalAnchorDate = null
                        weekdays = recurrence.days
                        endDate = recurrence.endDate
                        require(weekdays.isNotEmpty() && weekdays.all { it in 1..7 }) {
                            "weekdays must be non-empty and in 1..7"
                        }
                        if (endDate != null) {
                            require(!endDate.isBefore(targetDate)) { "endDate cannot be before startDate" }
                        }
                    }
                    else -> {
                        scheduleType = ScheduleType.ONCE
                        intervalDays = null
                        intervalAnchorDate = null
                        weekdays = null
                        endDate = targetDate
                    }
                }

                val updatedSchedule = existingSchedule.copy(
                    scheduleType = scheduleType,
                    startDate = targetDate,
                    endDate = endDate,
                    scheduledTime = time,
                    reminderMinutesBefore = reminderMinutesBefore,
                    intervalDays = intervalDays,
                    intervalAnchorDate = intervalAnchorDate,
                    weekdays = weekdays
                )
                newSchedules = current.schedules.map { if (it.id == existingSchedule.id) updatedSchedule else it }
            }
        }

        var newCompletions = current.completions
        if (isImportant != null && isImportant != existingTask.isImportant) {
            if (existingSchedule == null) {
                newCompletions = newCompletions.map {
                    if (it.taskId == taskId && it.scheduleId == null) {
                        it.copy(isImportantSnapshot = effectiveImportant)
                    } else it
                }
            } else if (existingSchedule.scheduleType == ScheduleType.ONCE) {
                newCompletions = newCompletions.map {
                    if (it.taskId == taskId) {
                        it.copy(isImportantSnapshot = effectiveImportant)
                    } else it
                }
            }
        }

        val newTasks = current.tasks.map { if (it.id == taskId) updatedTask else it }
        validateInvariants(newTasks, newSchedules, newCompletions)

        _state.value = PlannerState(
            tasks = newTasks,
            schedules = newSchedules,
            completions = newCompletions
        )
    }

    @Synchronized
    fun scheduleLaterTask(
        taskId: String,
        title: String? = null,
        note: String? = null,
        isImportant: Boolean? = null,
        date: LocalDate,
        time: LocalTime? = null,
        reminderMinutesBefore: Int? = null,
        recurrence: Recurrence? = null,
        plannerToday: LocalDate = LocalDate.now()
    ) {
        val current = _state.value
        val task = current.tasks.find { it.id == taskId }
            ?: throw IllegalArgumentException("Task $taskId not found")

        require(current.schedules.none { it.taskId == taskId }) {
            "Task $taskId already has a schedule"
        }

        require(current.completions.none { it.taskId == taskId }) {
            "Cannot schedule a completed Later task"
        }

        require(!date.isBefore(plannerToday)) {
            "Cannot schedule Later task to a historical date"
        }

        val effectiveTitle = (title ?: task.title).trim()
        require(effectiveTitle.isNotEmpty()) { "Title cannot be blank" }
        require(effectiveTitle.length <= 255) { "Invalid title: max 255 characters" }
        val effectiveNote = if (note != null) {
            require(note.length <= 500) { "Invalid note: max 500 characters" }
            note
        } else task.note
        val effectiveImportant = isImportant ?: task.isImportant

        if (reminderMinutesBefore != null) {
            require(reminderMinutesBefore in ALLOWED_REMINDER_MINUTES) {
                "Invalid reminderMinutesBefore: $reminderMinutesBefore"
            }
            require(time != null) {
                "Reminder requires scheduledTime"
            }
        }

        val scheduleType: ScheduleType
        val intervalDays: Int?
        val intervalAnchorDate: LocalDate?
        val weekdays: Set<Int>?
        val endDate: LocalDate?

        when (recurrence) {
            is Recurrence.IntervalDays -> {
                scheduleType = ScheduleType.INTERVAL_DAYS
                intervalDays = recurrence.intervalDays
                intervalAnchorDate = date
                weekdays = null
                endDate = recurrence.endDate
                require(intervalDays >= 1) { "intervalDays must be >= 1" }
                if (endDate != null) {
                    require(!endDate.isBefore(date)) { "endDate cannot be before startDate" }
                }
            }
            is Recurrence.Weekdays -> {
                scheduleType = ScheduleType.WEEKDAYS
                intervalDays = null
                intervalAnchorDate = null
                weekdays = recurrence.days
                endDate = recurrence.endDate
                require(weekdays.isNotEmpty() && weekdays.all { it in 1..7 }) {
                    "weekdays must be non-empty and in 1..7"
                }
                if (endDate != null) {
                    require(!endDate.isBefore(date)) { "endDate cannot be before startDate" }
                }
            }
            else -> {
                scheduleType = ScheduleType.ONCE
                intervalDays = null
                intervalAnchorDate = null
                weekdays = null
                endDate = date
            }
        }

        val scheduleId = "sched_${UUID.randomUUID()}"
        val newSchedule = ScheduleEntity(
            id = scheduleId,
            taskId = taskId,
            scheduleType = scheduleType,
            startDate = date,
            endDate = endDate,
            scheduledTime = time,
            reminderMinutesBefore = reminderMinutesBefore,
            intervalDays = intervalDays,
            intervalAnchorDate = intervalAnchorDate,
            weekdays = weekdays
        )

        val updatedTask = task.copy(
            title = effectiveTitle,
            note = effectiveNote,
            isImportant = effectiveImportant
        )

        val newTasks = current.tasks.map { if (it.id == taskId) updatedTask else it }
        val newSchedules = current.schedules + newSchedule
        validateInvariants(newTasks, newSchedules, current.completions)

        _state.value = PlannerState(
            tasks = newTasks,
            schedules = newSchedules,
            completions = current.completions
        )
    }

    @Synchronized
    fun deleteTask(taskId: String) {
        val current = _state.value
        val newTasks = current.tasks.filter { it.id != taskId }
        val newSchedules = current.schedules.filter { it.taskId != taskId }
        val newCompletions = current.completions.filter { it.taskId != taskId }

        validateInvariants(newTasks, newSchedules, newCompletions)

        _state.value = PlannerState(
            tasks = newTasks,
            schedules = newSchedules,
            completions = newCompletions
        )
    }

    @Synchronized
    fun completeTask(
        taskId: String,
        scheduleId: String?,
        scheduledDate: LocalDate?,
        completedDate: LocalDate,
        plannerToday: LocalDate
    ) {
        val current = _state.value
        val task = current.tasks.find { it.id == taskId }
            ?: throw IllegalArgumentException("Task $taskId not found")

        val schedule = if (scheduleId != null) {
            current.schedules.find { it.id == scheduleId }
                ?: throw IllegalArgumentException("Schedule $scheduleId not found")
        } else null

        if (schedule != null) {
            require(schedule.taskId == taskId) {
                "Schedule $scheduleId does not belong to task $taskId"
            }
            require(scheduledDate != null) {
                "Scheduled completion requires scheduledDate"
            }
            require(isScheduleOccurringOnDate(schedule, scheduledDate)) {
                "Date $scheduledDate is not a valid occurrence of schedule ${schedule.id}"
            }

            val minAllowed = if (scheduledDate.isBefore(plannerToday)) scheduledDate else plannerToday
            require(!completedDate.isBefore(minAllowed)) {
                "completedDate ($completedDate) cannot be before $minAllowed"
            }
            require(!completedDate.isAfter(plannerToday)) {
                "completedDate ($completedDate) cannot be after plannerToday ($plannerToday)"
            }

            val existing = current.completions.find { it.taskId == taskId && it.scheduledDate == scheduledDate }
            if (existing != null) {
                return
            }

            val newCompletion = CompletionEntity(
                id = "comp_${UUID.randomUUID()}",
                taskId = taskId,
                scheduleId = scheduleId,
                scheduledDate = scheduledDate,
                completedDate = completedDate,
                completedAtEpochMillis = nowEpochMillis(),
                titleSnapshot = task.title,
                isImportantSnapshot = task.isImportant
            )

            val prospectiveCompletions = current.completions + newCompletion
            validateInvariants(current.tasks, current.schedules, prospectiveCompletions)
            _state.value = PlannerState(
                tasks = current.tasks,
                schedules = current.schedules,
                completions = prospectiveCompletions
            )
        } else {
            require(scheduleId == null) { "Direct Later completion requires null scheduleId" }
            require(scheduledDate == null) { "Direct Later completion requires null scheduledDate" }
            require(current.schedules.none { it.taskId == taskId }) {
                "Cannot complete direct Later on a task that has a schedule"
            }
            require(completedDate == plannerToday) {
                "Direct Later completedDate must equal plannerToday ($plannerToday), got $completedDate"
            }

            val existing = current.completions.find { it.taskId == taskId && it.scheduleId == null && it.scheduledDate == null }
            if (existing != null) {
                return
            }

            val newCompletion = CompletionEntity(
                id = "comp_${UUID.randomUUID()}",
                taskId = taskId,
                scheduleId = null,
                scheduledDate = null,
                completedDate = completedDate,
                completedAtEpochMillis = nowEpochMillis(),
                titleSnapshot = task.title,
                isImportantSnapshot = task.isImportant
            )

            val prospectiveCompletions = current.completions + newCompletion
            validateInvariants(current.tasks, current.schedules, prospectiveCompletions)
            _state.value = PlannerState(
                tasks = current.tasks,
                schedules = current.schedules,
                completions = prospectiveCompletions
            )
        }
    }

    @Synchronized
    fun undoTask(
        taskId: String,
        scheduleId: String? = null,
        scheduledDate: LocalDate? = null
    ) {
        val current = _state.value
        val prospectiveCompletions = if (scheduledDate != null) {
            current.completions.filterNot {
                it.taskId == taskId && it.scheduledDate == scheduledDate && (scheduleId == null || it.scheduleId == scheduleId)
            }
        } else {
            current.completions.filterNot {
                it.taskId == taskId && it.scheduleId == null && it.scheduledDate == null
            }
        }

        validateInvariants(current.tasks, current.schedules, prospectiveCompletions)
        _state.value = PlannerState(
            tasks = current.tasks,
            schedules = current.schedules,
            completions = prospectiveCompletions
        )
    }

    @Synchronized
    fun completeScheduledOccurrence(
        taskId: String,
        scheduledDate: LocalDate,
        completedDate: LocalDate = scheduledDate,
        plannerToday: LocalDate = LocalDate.now()
    ) {
        val current = _state.value
        val schedule = current.schedules.find {
            it.taskId == taskId && isScheduleOccurringOnDate(it, scheduledDate)
        } ?: throw IllegalArgumentException("No schedule found for task $taskId occurring on $scheduledDate")

        completeTask(
            taskId = taskId,
            scheduleId = schedule.id,
            scheduledDate = scheduledDate,
            completedDate = completedDate,
            plannerToday = plannerToday
        )
    }

    @Synchronized
    fun undoScheduledOccurrence(
        taskId: String,
        scheduledDate: LocalDate
    ) {
        val current = _state.value
        val schedule = current.schedules.find {
            it.taskId == taskId && isScheduleOccurringOnDate(it, scheduledDate)
        }
        undoTask(
            taskId = taskId,
            scheduleId = schedule?.id,
            scheduledDate = scheduledDate
        )
    }

    // ========================================================================
    // OBSERVABLE PROJECTIONS (Flow APIs)
    // ========================================================================

    fun observeTasksForDate(date: LocalDate): Flow<List<ScheduleTaskItem>> =
        _state.map { state -> getTasksForDate(state, date) }.distinctUntilChanged()

    fun observeLaterTasks(): Flow<List<LaterTaskItem>> =
        _state.map { state -> getLaterTasks(state) }.distinctUntilChanged()

    fun observeHistoryTasks(plannerToday: LocalDate): Flow<List<HistoryTaskItem>> =
        _state.map { state -> getHistoryTasks(state, plannerToday) }.distinctUntilChanged()

    fun observeImportantDates(startDate: LocalDate, endDate: LocalDate): Flow<Set<LocalDate>> =
        _state.map { state -> getImportantDates(state, startDate, endDate) }.distinctUntilChanged()

    // ========================================================================
    // SYNCHRONOUS READ PROJECTIONS
    // ========================================================================

    fun getTasksForDate(date: LocalDate): List<ScheduleTaskItem> =
        getTasksForDate(_state.value, date)

    fun getLaterTasks(): List<LaterTaskItem> =
        getLaterTasks(_state.value)

    fun getHistoryTasks(plannerToday: LocalDate): List<HistoryTaskItem> =
        getHistoryTasks(_state.value, plannerToday)

    fun getImportantDates(startDate: LocalDate, endDate: LocalDate): Set<LocalDate> =
        getImportantDates(_state.value, startDate, endDate)

    fun hasImportantTask(date: LocalDate): Boolean =
        hasImportantTask(_state.value, date)

    // ========================================================================
    // PURE PROJECTION HELPERS
    // ========================================================================

    fun isScheduleOccurringOnDate(schedule: ScheduleEntity, date: LocalDate): Boolean {
        if (date.isBefore(schedule.startDate)) return false
        if (schedule.endDate != null && date.isAfter(schedule.endDate)) return false

        return when (schedule.scheduleType) {
            ScheduleType.ONCE -> date == schedule.startDate
            ScheduleType.INTERVAL_DAYS -> {
                val anchor = schedule.intervalAnchorDate ?: schedule.startDate
                if (date.isBefore(anchor)) return false
                val interval = schedule.intervalDays ?: 1
                val daysDiff = ChronoUnit.DAYS.between(anchor, date)
                (daysDiff % interval) == 0L
            }
            ScheduleType.WEEKDAYS -> {
                val weekday = date.dayOfWeek.value // 1 = Monday .. 7 = Sunday
                schedule.weekdays?.contains(weekday) == true
            }
        }
    }

    fun formatLocalTime(time: LocalTime?): String? {
        if (time == null) return null
        return time.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
    }

    fun reminderMinutesToDisplayString(minutes: Int?): String? {
        return when (minutes) {
            0 -> "At task time"
            5 -> "5 minutes before"
            10 -> "10 minutes before"
            15 -> "15 minutes before"
            30 -> "30 minutes before"
            60 -> "1 hour before"
            1440 -> "1 day before"
            2880 -> "2 days before"
            else -> null
        }
    }

    fun scheduleEntityToRecurrence(schedule: ScheduleEntity): Recurrence? {
        return when (schedule.scheduleType) {
            ScheduleType.ONCE -> Recurrence.Once
            ScheduleType.INTERVAL_DAYS -> Recurrence.IntervalDays(
                intervalDays = schedule.intervalDays ?: 1,
                endDate = schedule.endDate
            )
            ScheduleType.WEEKDAYS -> Recurrence.Weekdays(
                days = schedule.weekdays ?: emptySet(),
                endDate = schedule.endDate
            )
        }
    }

    fun getTasksForDate(state: PlannerState, date: LocalDate): List<ScheduleTaskItem> {
        val currentTasks = state.tasks.associateBy { it.id }
        val currentSchedules = state.schedules
        val currentCompletions = state.completions.associateBy { Triple(it.taskId, it.scheduleId, it.scheduledDate) }

        val items = mutableListOf<ScheduleTaskItem>()
        for (schedule in currentSchedules) {
            if (isScheduleOccurringOnDate(schedule, date)) {
                val task = currentTasks[schedule.taskId] ?: continue
                val isCompleted = currentCompletions.containsKey(Triple(task.id, schedule.id, date))

                items.add(
                    ScheduleTaskItem(
                        id = task.id,
                        title = task.title,
                        note = task.note,
                        date = date,
                        time = formatLocalTime(schedule.scheduledTime),
                        reminder = reminderMinutesToDisplayString(schedule.reminderMinutesBefore),
                        recurrence = scheduleEntityToRecurrence(schedule),
                        isImportant = task.isImportant,
                        isCompleted = isCompleted
                    )
                )
            }
        }
        return items
    }

    fun getLaterTasks(state: PlannerState): List<LaterTaskItem> {
        val scheduledTaskIds = state.schedules.map { it.taskId }.toSet()
        val currentCompletions = state.completions
            .filter { it.scheduleId == null && it.scheduledDate == null }
            .associateBy { it.taskId }

        return state.tasks
            .filter { it.id !in scheduledTaskIds }
            .sortedByDescending { it.createdAtEpochMillis }
            .map { task ->
                val isCompleted = currentCompletions.containsKey(task.id)
                LaterTaskItem(
                    id = task.id,
                    title = task.title,
                    note = task.note,
                    isImportant = task.isImportant,
                    isCompleted = isCompleted
                )
            }
    }

    fun getHistoryTasks(state: PlannerState, plannerToday: LocalDate): List<HistoryTaskItem> {
        val scheduleMap = state.schedules.associateBy { it.id }

        return state.completions
            .filter { comp ->
                comp.completedDate.isBefore(plannerToday) &&
                    comp.isImportantSnapshot &&
                    (comp.scheduleId == null || scheduleMap[comp.scheduleId]?.scheduleType == ScheduleType.ONCE)
            }
            .sortedByDescending { it.completedAtEpochMillis }
            .map { comp ->
                val time = Instant.ofEpochMilli(comp.completedAtEpochMillis)
                    .atZone(zoneId)
                    .toLocalTime()
                val timeStr = time.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
                val minutes = time.hour * 60 + time.minute

                HistoryTaskItem(
                    id = comp.taskId,
                    title = comp.titleSnapshot,
                    isImportant = comp.isImportantSnapshot,
                    completedDate = comp.completedDate,
                    completedAtTime = timeStr,
                    completedAtMinutes = minutes
                )
            }
    }

    fun getImportantDates(state: PlannerState, startDate: LocalDate, endDate: LocalDate): Set<LocalDate> {
        if (endDate.isBefore(startDate)) return emptySet()

        val currentTasks = state.tasks.associateBy { it.id }
        val importantSchedules = state.schedules.filter { currentTasks[it.taskId]?.isImportant == true }

        val importantDates = mutableSetOf<LocalDate>()
        var cur = startDate
        while (!cur.isAfter(endDate)) {
            for (schedule in importantSchedules) {
                if (isScheduleOccurringOnDate(schedule, cur)) {
                    importantDates.add(cur)
                    break
                }
            }
            cur = cur.plusDays(1)
        }
        return importantDates
    }

    fun hasImportantTask(state: PlannerState, date: LocalDate): Boolean {
        val currentTasks = state.tasks.associateBy { it.id }
        return state.schedules.any { schedule ->
            currentTasks[schedule.taskId]?.isImportant == true && isScheduleOccurringOnDate(schedule, date)
        }
    }

    companion object {
        val ALLOWED_REMINDER_MINUTES = setOf(0, 5, 10, 15, 30, 60, 1440, 2880)
        val defaultDatabase by lazy { InMemoryPlannerDatabase() }
    }
}
