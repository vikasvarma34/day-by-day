package com.vikaspokala.daybyday.ui

import androidx.room.DatabaseConfiguration
import com.vikaspokala.daybyday.data.local.planner.LaterTaskProjection
import com.vikaspokala.daybyday.data.local.planner.PlannerDatabase
import com.vikaspokala.daybyday.data.local.planner.ScheduledOccurrence
import com.vikaspokala.daybyday.data.local.planner.dao.CompletionDao
import com.vikaspokala.daybyday.data.local.planner.dao.ScheduleDao
import com.vikaspokala.daybyday.data.local.planner.dao.TaskDao
import com.vikaspokala.daybyday.data.remote.api.PlannerApi
import com.vikaspokala.daybyday.data.remote.dto.CompleteTaskRequestDto
import com.vikaspokala.daybyday.data.remote.dto.CreateTaskRequestDto
import com.vikaspokala.daybyday.data.remote.dto.CreateTaskScheduleRequestDto
import com.vikaspokala.daybyday.data.remote.dto.UndoTaskRequestDto
import com.vikaspokala.daybyday.data.remote.dto.UpdateTaskContentRequestDto
import com.vikaspokala.daybyday.data.remote.dto.UpdateTaskRequestDto
import com.vikaspokala.daybyday.data.remote.dto.UpdateTaskScheduleDto
import com.vikaspokala.daybyday.data.remote.dto.UpdateTaskSchedulePayloadDto
import com.vikaspokala.daybyday.data.repository.PlannerRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

internal class FakePlannerDatabase : PlannerDatabase() {
    val occurrences = MutableStateFlow<Map<LocalDate, List<ScheduledOccurrence>>>(emptyMap())
    val importantDates = MutableStateFlow<Set<LocalDate>>(emptySet())
    val laterTasks = MutableStateFlow<List<LaterTaskProjection>>(emptyList())
    val importantRanges = mutableListOf<Pair<LocalDate, LocalDate>>()

    override fun observeScheduledOccurrences(date: LocalDate) =
        occurrences.map { rows -> rows[date].orEmpty() }

    override fun observeImportantDates(startDate: LocalDate, endDate: LocalDate) =
        importantDates.map { dates ->
            importantRanges += startDate to endDate
            dates.filterTo(linkedSetOf()) { it in startDate..endDate }
        }

    override fun observeLaterTasks() = laterTasks
    override fun taskDao(): TaskDao = error("unused")
    override fun scheduleDao(): ScheduleDao = error("unused")
    override fun completionDao(): CompletionDao = error("unused")
    override fun createInvalidationTracker() = error("unused")
    override fun createOpenHelper(config: DatabaseConfiguration) = error("unused")
    override fun clearAllTables() = Unit
}

internal sealed interface PlannerUiCall {
    data class Create(
        val taskId: String,
        val title: String,
        val note: String?,
        val isImportant: Boolean,
        val plannerToday: String?,
        val schedule: CreateTaskScheduleRequestDto?
    ) : PlannerUiCall

    data class Complete(
        val taskId: String,
        val plannerToday: String,
        val completedDate: String,
        val scheduleId: String?,
        val scheduledDate: String?
    ) : PlannerUiCall

    data class Undo(val taskId: String, val scheduleId: String?, val scheduledDate: String?) : PlannerUiCall
    data class Content(val taskId: String, val title: String, val note: String?, val isImportant: Boolean) : PlannerUiCall
    data class Schedule(
        val taskId: String,
        val plannerToday: String,
        val effectiveDate: String,
        val schedule: UpdateTaskScheduleDto
    ) : PlannerUiCall

    data class Combined(
        val taskId: String,
        val title: String,
        val note: String?,
        val isImportant: Boolean,
        val plannerToday: String,
        val effectiveDate: String,
        val schedule: UpdateTaskScheduleDto
    ) : PlannerUiCall

    data class Delete(val taskId: String) : PlannerUiCall
}

internal open class RecordingPlannerRepository(
    private val fakeDatabase: FakePlannerDatabase,
    private val result: Result<Unit> = Result.success(Unit)
) : PlannerRepository(fakeDatabase, { "token" }, unusedPlannerApi) {
    val calls = mutableListOf<PlannerUiCall>()

    override suspend fun createTask(
        taskId: String,
        title: String,
        note: String?,
        isImportant: Boolean,
        plannerToday: String?,
        schedule: CreateTaskScheduleRequestDto?
    ): Result<Unit> {
        calls += PlannerUiCall.Create(taskId, title, note, isImportant, plannerToday, schedule)
        return result
    }

    override suspend fun completeTask(
        taskId: String,
        plannerToday: String,
        completedDate: String,
        scheduleId: String?,
        scheduledDate: String?
    ): Result<Unit> {
        calls += PlannerUiCall.Complete(taskId, plannerToday, completedDate, scheduleId, scheduledDate)
        if (result.isSuccess) {
            if (scheduleId == null) {
                fakeDatabase.laterTasks.value = fakeDatabase.laterTasks.value.map { task ->
                    if (task.taskId == taskId) task.copy(
                        directCompletionId = "completion-$taskId",
                        completedDate = completedDate,
                        completedAt = "2026-08-20T12:00:00Z"
                    ) else task
                }
            } else {
                mutateOccurrence(taskId, scheduledDate, isCompleted = true)
            }
        }
        return result
    }

    override suspend fun undoTask(taskId: String, scheduleId: String?, scheduledDate: String?): Result<Unit> {
        calls += PlannerUiCall.Undo(taskId, scheduleId, scheduledDate)
        if (result.isSuccess) {
            if (scheduleId == null) {
                fakeDatabase.laterTasks.value = fakeDatabase.laterTasks.value.map { task ->
                    if (task.taskId == taskId) task.copy(
                        directCompletionId = null,
                        completedDate = null,
                        completedAt = null
                    ) else task
                }
            } else {
                mutateOccurrence(taskId, scheduledDate, isCompleted = false)
            }
        }
        return result
    }

    override suspend fun updateTaskContent(
        taskId: String,
        title: String,
        note: String?,
        isImportant: Boolean
    ): Result<Unit> {
        calls += PlannerUiCall.Content(taskId, title, note, isImportant)
        if (result.isSuccess) {
            fakeDatabase.laterTasks.value = fakeDatabase.laterTasks.value.map { task ->
                if (task.taskId == taskId) task.copy(title = title, note = note, isImportant = isImportant) else task
            }
            fakeDatabase.occurrences.value = fakeDatabase.occurrences.value.mapValues { (_, rows) ->
                rows.map { task ->
                    if (task.taskId == taskId) task.copy(title = title, note = note, isImportant = isImportant) else task
                }
            }
        }
        return result
    }

    override suspend fun updateTaskSchedule(
        taskId: String,
        plannerToday: String,
        effectiveDate: String,
        schedule: UpdateTaskScheduleDto
    ): Result<Unit> {
        calls += PlannerUiCall.Schedule(taskId, plannerToday, effectiveDate, schedule)
        if (result.isSuccess) fakeDatabase.laterTasks.value = fakeDatabase.laterTasks.value.filterNot { it.taskId == taskId }
        return result
    }

    override suspend fun updateTask(
        taskId: String,
        title: String,
        note: String?,
        isImportant: Boolean,
        plannerToday: String,
        effectiveDate: String,
        schedule: UpdateTaskScheduleDto
    ): Result<Unit> {
        calls += PlannerUiCall.Combined(taskId, title, note, isImportant, plannerToday, effectiveDate, schedule)
        if (result.isSuccess) fakeDatabase.laterTasks.value = fakeDatabase.laterTasks.value.filterNot { it.taskId == taskId }
        return result
    }

    override suspend fun deleteTask(taskId: String): Result<Unit> {
        calls += PlannerUiCall.Delete(taskId)
        if (result.isSuccess) {
            fakeDatabase.laterTasks.value = fakeDatabase.laterTasks.value.filterNot { it.taskId == taskId }
            fakeDatabase.occurrences.value = fakeDatabase.occurrences.value.mapValues { (_, rows) ->
                rows.filterNot { it.taskId == taskId }
            }
        }
        return result
    }

    private fun mutateOccurrence(taskId: String, scheduledDate: String?, isCompleted: Boolean) {
        val date = scheduledDate?.let(LocalDate::parse) ?: return
        fakeDatabase.occurrences.value = fakeDatabase.occurrences.value +
            (date to fakeDatabase.occurrences.value[date].orEmpty().map { task ->
                if (task.taskId == taskId) task.copy(isCompleted = isCompleted) else task
            })
    }
}

internal fun scheduledOccurrence(
    taskId: String,
    date: LocalDate,
    scheduleId: String = "schedule-$taskId",
    title: String = taskId,
    note: String? = null,
    time: String? = null,
    reminderMinutes: Int? = null,
    type: String = "ONCE",
    endDate: LocalDate? = date,
    intervalDays: Int? = null,
    weekdaysMask: Int? = null,
    isImportant: Boolean = false,
    isCompleted: Boolean = false
) = ScheduledOccurrence(
    taskId = taskId,
    scheduleId = scheduleId,
    title = title,
    note = note,
    isImportant = isImportant,
    scheduleType = type,
    startDate = date.toString(),
    endDate = endDate?.toString(),
    scheduledTime = time,
    intervalDays = intervalDays,
    intervalAnchorDate = if (type == "INTERVAL_DAYS") date.toString() else null,
    weekdaysMask = weekdaysMask,
    reminderMinutesBefore = reminderMinutes,
    scheduledDate = date.toString(),
    isCompleted = isCompleted
)

internal fun laterProjection(
    taskId: String,
    title: String = taskId,
    note: String? = null,
    isImportant: Boolean = false,
    createdAt: String = "2026-08-20T00:00:00Z",
    completedAt: String? = null
) = LaterTaskProjection(
    taskId = taskId,
    title = title,
    note = note,
    isImportant = isImportant,
    createdAt = createdAt,
    directCompletionId = completedAt?.let { "completion-$taskId" },
    completedDate = completedAt?.substringBefore('T'),
    completedAt = completedAt
)

private val unusedPlannerApi = object : PlannerApi {
    override suspend fun createTask(authorization: String, request: CreateTaskRequestDto) = error("unused")
    override suspend fun getRefresh(authorization: String) = error("unused")
    override suspend fun completeTask(authorization: String, taskId: String, request: CompleteTaskRequestDto) = error("unused")
    override suspend fun undoTask(authorization: String, taskId: String, request: UndoTaskRequestDto) = error("unused")
    override suspend fun deleteTask(authorization: String, taskId: String) = error("unused")
    override suspend fun updateTaskContent(authorization: String, taskId: String, request: UpdateTaskContentRequestDto) = error("unused")
    override suspend fun updateTaskSchedule(authorization: String, taskId: String, request: UpdateTaskSchedulePayloadDto) = error("unused")
    override suspend fun updateTask(authorization: String, taskId: String, request: UpdateTaskRequestDto) = error("unused")
}
