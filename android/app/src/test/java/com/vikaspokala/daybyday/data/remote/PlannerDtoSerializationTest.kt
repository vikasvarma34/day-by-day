package com.vikaspokala.daybyday.data.remote

import com.vikaspokala.daybyday.data.remote.dto.UpdateTaskContentRequestDto
import com.vikaspokala.daybyday.data.remote.dto.UpdateTaskResponseDto
import com.vikaspokala.daybyday.data.remote.dto.UpdateTaskScheduleDto
import com.vikaspokala.daybyday.data.remote.dto.UpdateTaskSchedulePayloadDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerDtoSerializationTest {

    private val json = NetworkClient.json

    @Test
    fun serializeUpdateTaskSchedulePayload_producesExactContractShape() {
        val request = UpdateTaskSchedulePayloadDto(
            plannerToday = "2026-08-22",
            effectiveDate = "2026-08-25",
            schedule = UpdateTaskScheduleDto(
                type = "WEEKDAYS",
                startDate = "2026-08-25",
                endDate = null,
                scheduledTime = "09:00:00",
                reminderMinutesBefore = 15,
                intervalDays = null,
                weekdaysMask = 65
            )
        )
        val serialized = json.encodeToString(UpdateTaskSchedulePayloadDto.serializer(), request)

        assertTrue(serialized.contains(""""plannerToday":"2026-08-22""""))
        assertTrue(serialized.contains(""""effectiveDate":"2026-08-25""""))
        assertTrue(serialized.contains(""""type":"WEEKDAYS""""))
        assertTrue(serialized.contains(""""startDate":"2026-08-25""""))
        assertTrue(serialized.contains(""""scheduledTime":"09:00:00""""))
        assertTrue(serialized.contains(""""reminderMinutesBefore":15"""))
        assertTrue(serialized.contains(""""weekdaysMask":65"""))
        assertFalse(serialized.contains("intervalAnchorDate"))
        assertFalse(serialized.contains("title"))
        assertFalse(serialized.contains("isImportant"))
    }

    @Test
    fun serializeUpdateTaskContentRequest_withNullNote_producesExplicitNullNote() {
        val request = UpdateTaskContentRequestDto(
            title = "Example",
            note = null,
            isImportant = true
        )
        val serialized = json.encodeToString(UpdateTaskContentRequestDto.serializer(), request)

        val expected = """{"title":"Example","note":null,"isImportant":true}"""
        assertEquals(expected, serialized)
        assertTrue(serialized.contains(""""note":null"""))
        assertFalse(serialized.contains("plannerToday"))
        assertFalse(serialized.contains("effectiveDate"))
        assertFalse(serialized.contains("schedule"))
    }

    @Test
    fun serializeUpdateTaskContentRequest_withNonNullNote_producesExactNoteValue() {
        val request = UpdateTaskContentRequestDto(
            title = "Example Task",
            note = "Detailed notes here",
            isImportant = false
        )
        val serialized = json.encodeToString(UpdateTaskContentRequestDto.serializer(), request)

        val expected = """{"title":"Example Task","note":"Detailed notes here","isImportant":false}"""
        assertEquals(expected, serialized)
        assertTrue(serialized.contains(""""note":"Detailed notes here""""))
        assertFalse(serialized.contains("plannerToday"))
        assertFalse(serialized.contains("effectiveDate"))
        assertFalse(serialized.contains("schedule"))
    }

    @Test
    fun deserializeUpdateTaskResponse_withCanonicalCompletions_parsesCorrectly() {
        val jsonString = """
            {
                "task": {
                    "id": "11111111-2222-3333-4444-555555555555",
                    "title": "Renamed Task",
                    "note": null,
                    "isImportant": true,
                    "createdAt": "2026-08-22T10:00:00.000Z",
                    "updatedAt": "2026-08-22T10:05:00.000Z",
                    "schedules": []
                },
                "completions": [
                    {
                        "id": "comp-uuid-1",
                        "taskId": "11111111-2222-3333-4444-555555555555",
                        "scheduleId": null,
                        "scheduledDate": null,
                        "completedDate": "2026-08-22",
                        "completedAt": "2026-08-22T10:00:00.000Z",
                        "titleSnapshot": "Original Task Title",
                        "isImportantSnapshot": true
                    }
                ]
            }
        """.trimIndent()

        val response = json.decodeFromString(UpdateTaskResponseDto.serializer(), jsonString)

        assertEquals("11111111-2222-3333-4444-555555555555", response.task.id)
        assertEquals("Renamed Task", response.task.title)
        assertEquals(null, response.task.note)
        assertEquals(true, response.task.isImportant)
        assertEquals(1, response.completions.size)
        assertEquals("Original Task Title", response.completions[0].titleSnapshot)
        assertEquals(true, response.completions[0].isImportantSnapshot)
    }
}
