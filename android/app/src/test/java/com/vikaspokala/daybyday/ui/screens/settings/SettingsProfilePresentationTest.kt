package com.vikaspokala.daybyday.ui.screens.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsProfilePresentationTest {

    private val baseScreensPath = "src/main/java/com/vikaspokala/daybyday/ui/screens"
    private val appMainPath = "src/main/java/com/vikaspokala/daybyday"

    @Test
    fun `1 Settings profile row displays Nickname (Optional)`() {
        val settingsFile = File(baseScreensPath, "settings/SettingsScreen.kt")
        val content = settingsFile.readText()
        assertTrue(
            "SettingsScreen must contain 'title = \"Nickname (Optional)\"'",
            content.contains("title = \"Nickname (Optional)\"")
        )
    }

    @Test
    fun `2 EditProfileFieldScreen displays Nickname (Optional) for heading and label`() {
        val editFile = File(baseScreensPath, "settings/EditProfileFieldScreen.kt")
        val content = editFile.readText()
        assertTrue(
            "EditProfileFieldScreen must configure Nickname (Optional) title and NICKNAME (Optional) label",
            content.contains("Triple(\"Nickname (Optional)\", \"NICKNAME (Optional)\", \"Enter nickname\")")
        )
    }

    @Test
    fun `3 No fake identity names hardcoded in production source`() {
        val mainDir = File(appMainPath)
        val allKotlinFiles = mainDir.walkTopDown().filter { it.extension == "kt" }.toList()

        val forbiddenStrings = listOf(
            "\"Vicky\"",
            "\"Wiki\"",
            "\"Varma\"",
            "\"Vikas\""
        )

        for (file in allKotlinFiles) {
            val content = file.readText()
            for (forbidden in forbiddenStrings) {
                assertFalse(
                    "Production file ${file.name} must not contain hardcoded fake identity $forbidden",
                    content.contains(forbidden)
                )
            }
        }
    }
}
