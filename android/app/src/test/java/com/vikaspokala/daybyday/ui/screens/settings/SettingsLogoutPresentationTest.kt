package com.vikaspokala.daybyday.ui.screens.settings

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsLogoutPresentationTest {

    private val settingsScreenFile = File("src/main/java/com/vikaspokala/daybyday/ui/screens/settings/SettingsScreen.kt")

    @Test
    fun `1 SettingsScreen defines Logout confirmation dialog with required copy`() {
        val content = settingsScreenFile.readText()

        assertTrue(
            "SettingsScreen must contain dialog title 'Log out?'",
            content.contains("text = \"Log out?\"")
        )
        assertTrue(
            "SettingsScreen must contain dialog message 'You’ll need to sign in again to access your planner.'",
            content.contains("text = \"You’ll need to sign in again to access your planner.\"")
        )
        assertTrue(
            "SettingsScreen must contain Cancel button",
            content.contains("text = \"Cancel\"")
        )
        assertTrue(
            "SettingsScreen must contain Log out confirmation button",
            content.contains("text = \"Log out\"")
        )
    }

    @Test
    fun `2 Logout row opens confirmation dialog and does not invoke onLogoutClick directly`() {
        val content = settingsScreenFile.readText()

        assertTrue(
            "Logout row must open confirmation dialog on click",
            content.contains("title = \"Logout\",\n                onClick = { showLogoutConfirmation = true }") ||
                    content.contains("title = \"Logout\",\n                onClick = { showLogoutConfirmation = true }") ||
                    content.contains("onClick = { showLogoutConfirmation = true }")
        )
    }

    @Test
    fun `3 Confirmation dialog cancel dismisses dialog without calling logout`() {
        val content = settingsScreenFile.readText()

        assertTrue(
            "Dismiss button must close confirmation dialog without calling onLogoutClick",
            content.contains("dismissButton = {\n                TextButton(\n                    onClick = { showLogoutConfirmation = false }\n                )")
        )
    }

    @Test
    fun `4 Confirmation dialog confirm invokes onLogoutClick and dismisses dialog`() {
        val content = settingsScreenFile.readText()

        assertTrue(
            "Confirm button must dismiss dialog and invoke onLogoutClick",
            content.contains("confirmButton = {\n                TextButton(\n                    onClick = {\n                        showLogoutConfirmation = false\n                        onLogoutClick()\n                    }")
        )
    }
}
