package com.vikaspokala.daybyday.ui.screens

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmptyStatePresentationTest {

    private val baseScreensPath = "src/main/java/com/vikaspokala/daybyday/ui/screens"

    @Test
    fun `1 Today empty state uses exact new message`() {
        val file = File(baseScreensPath, "today/TodayScreen.kt")
        val content = file.readText()
        assertTrue(
            "TodayScreen must contain exact empty state message",
            content.contains("\"Nothing planned for today. Take it easy.\"")
        )
    }

    @Test
    fun `2 Schedule empty state uses exact new message`() {
        val file = File(baseScreensPath, "schedule/ScheduleScreen.kt")
        val content = file.readText()
        assertTrue(
            "ScheduleScreen must contain exact empty state message",
            content.contains("\"Nothing planned for this day.\"")
        )
    }

    @Test
    fun `3 Later empty state uses exact new message`() {
        val file = File(baseScreensPath, "later/LaterScreen.kt")
        val content = file.readText()
        assertTrue(
            "LaterScreen must contain exact empty state message",
            content.contains("\"Nothing waiting for later.\"")
        )
    }

    @Test
    fun `4 History empty state uses exact new message`() {
        val file = File(baseScreensPath, "history/HistoryScreen.kt")
        val content = file.readText()
        assertTrue(
            "HistoryScreen must contain exact empty state message for empty history",
            content.contains("\"No completed important tasks yet.\"")
        )
        assertTrue(
            "HistoryScreen preserves search-specific empty message",
            content.contains("\"No matching important history.\"")
        )
    }

    @Test
    fun `5 non-empty screens conditionally check empty before rendering empty-state message`() {
        val todayContent = File(baseScreensPath, "today/TodayScreen.kt").readText()
        assertTrue(todayContent.contains("if (selectedDateTasks.isEmpty())"))

        val scheduleContent = File(baseScreensPath, "schedule/ScheduleScreen.kt").readText()
        assertTrue(scheduleContent.contains("if (selectedDateTasks.isEmpty())"))

        val laterContent = File(baseScreensPath, "later/LaterScreen.kt").readText()
        assertTrue(laterContent.contains("if (tasks.isEmpty())"))

        val historyContent = File(baseScreensPath, "history/HistoryScreen.kt").readText()
        assertTrue(historyContent.contains("if (filteredGroupedHistory.isEmpty())"))
    }

    @Test
    fun `6 old No tasks for this date copy is no longer used anywhere`() {
        val screensDir = File(baseScreensPath)
        val allKotlinFiles = screensDir.walkTopDown().filter { it.extension == "kt" }.toList()
        for (f in allKotlinFiles) {
            val content = f.readText()
            assertFalse(
                "File ${f.name} should not contain old 'No tasks for this date' message",
                content.contains("No tasks for this date")
            )
            assertFalse(
                "File ${f.name} should not contain old 'No important history yet.' message",
                content.contains("No important history yet.")
            )
        }
    }
}
