package com.vikaspokala.daybyday.data.remote

import com.vikaspokala.daybyday.data.local.planner.entity.toEntity
import com.vikaspokala.daybyday.data.remote.dto.PlannerRefreshResponseDto
import com.vikaspokala.daybyday.data.remote.dto.RefreshCompletionDto
import com.vikaspokala.daybyday.data.remote.dto.RefreshScheduleDto
import com.vikaspokala.daybyday.data.remote.dto.RefreshTaskDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerRefreshDtoSerializationTest {

    private val json = NetworkClient.json

    @Test
    fun decode_emptySnapshot_success() {
        val jsonString = """
            {
                "tasks": [],
                "schedules": [],
                "completions": []
            }
        """.trimIndent()

        val response = json.decodeFromString(PlannerRefreshResponseDto.serializer(), jsonString)

        assertTrue(response.tasks.isEmpty())
        assertTrue(response.schedules.isEmpty())
        assertTrue(response.completions.isEmpty())
    }

    @Test
    fun decode_populatedSnapshotWithWeekdaysMask_successAndMapsToEntities() {
        val jsonString = """
            {
                "tasks": [
                    {
                        "id": "11111111-1111-1111-1111-111111111111",
                        "title": "Gym Workout",
                        "note": "Leg day routine",
                        "isImportant": true,
                        "createdAt": "2026-08-18T10:00:00.000Z",
                        "updatedAt": "2026-08-18T10:00:00.000Z"
                    },
                    {
                        "id": "22222222-2222-2222-2222-222222222222",
                        "title": "Buy groceries",
                        "note": null,
                        "isImportant": false,
                        "createdAt": "2026-08-18T11:00:00.000Z",
                        "updatedAt": "2026-08-18T11:00:00.000Z"
                    }
                ],
                "schedules": [
                    {
                        "id": "33333333-3333-3333-3333-333333333333",
                        "taskId": "11111111-1111-1111-1111-111111111111",
                        "scheduleType": "WEEKDAYS",
                        "startDate": "2026-08-18",
                        "endDate": "2026-08-31",
                        "scheduledTime": "14:30:00",
                        "intervalDays": null,
                        "intervalAnchorDate": null,
                        "weekdaysMask": 31,
                        "reminderMinutesBefore": 15,
                        "createdAt": "2026-08-18T10:00:00.000Z",
                        "updatedAt": "2026-08-18T10:00:00.000Z"
                    }
                ],
                "completions": [
                    {
                        "id": "44444444-4444-4444-4444-444444444444",
                        "taskId": "11111111-1111-1111-1111-111111111111",
                        "scheduleId": "33333333-3333-3333-3333-333333333333",
                        "scheduledDate": "2026-08-18",
                        "completedDate": "2026-08-18",
                        "completedAt": "2026-08-18T10:30:00.000Z",
                        "titleSnapshot": "Gym Workout",
                        "isImportantSnapshot": true
                    }
                ]
            }
        """.trimIndent()

        val response = json.decodeFromString(PlannerRefreshResponseDto.serializer(), jsonString)

        assertEquals(2, response.tasks.size)
        assertEquals(1, response.schedules.size)
        assertEquals(1, response.completions.size)

        // Check task entity mapping
        val taskEntity = response.tasks[0].toEntity()
        assertEquals("11111111-1111-1111-1111-111111111111", taskEntity.id)
        assertEquals("Gym Workout", taskEntity.title)
        assertEquals("Leg day routine", taskEntity.note)
        assertTrue(taskEntity.isImportant)

        // Check schedule entity mapping and weekdaysMask preservation
        val scheduleDto = response.schedules[0]
        assertEquals(31, scheduleDto.weekdaysMask)
        val scheduleEntity = scheduleDto.toEntity()
        assertEquals(31, scheduleEntity.weekdaysMask)
        assertEquals("WEEKDAYS", scheduleEntity.scheduleType)
        assertEquals("14:30:00", scheduleEntity.scheduledTime)
        assertNull(scheduleEntity.intervalDays)

        // Check completion entity mapping
        val completionEntity = response.completions[0].toEntity()
        assertEquals("44444444-4444-4444-4444-444444444444", completionEntity.id)
        assertEquals("33333333-3333-3333-3333-333333333333", completionEntity.scheduleId)
        assertEquals("2026-08-18", completionEntity.completedDate)
        assertTrue(completionEntity.isImportantSnapshot)
    }

    @Test
    fun decode_ignoreUnknownKeys_success() {
        val jsonString = """
            {
                "tasks": [],
                "schedules": [],
                "completions": [],
                "futureServerField": "ignored_value"
            }
        """.trimIndent()

        val response = json.decodeFromString(PlannerRefreshResponseDto.serializer(), jsonString)
        assertTrue(response.tasks.isEmpty())
    }
}
