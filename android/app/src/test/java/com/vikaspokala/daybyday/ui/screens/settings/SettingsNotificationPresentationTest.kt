package com.vikaspokala.daybyday.ui.screens.settings

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsNotificationPresentationTest {

    private val settingsScreenFile = File("src/main/java/com/vikaspokala/daybyday/ui/screens/settings/SettingsScreen.kt")
    private val manifestFile = File("src/main/AndroidManifest.xml")

    @Test
    fun `1 AndroidManifest declares POST_NOTIFICATIONS permission`() {
        val content = manifestFile.readText()
        assertTrue(
            "AndroidManifest.xml must declare android.permission.POST_NOTIFICATIONS",
            content.contains("android.permission.POST_NOTIFICATIONS")
        )
    }

    @Test
    fun `2 SettingsScreen binds dynamic notification permission status and click handler`() {
        val content = settingsScreenFile.readText()

        assertTrue(
            "SettingsScreen must have isNotificationAllowed parameter",
            content.contains("isNotificationAllowed: Boolean")
        )
        assertTrue(
            "SettingsScreen must have onNotificationPermissionClick parameter",
            content.contains("onNotificationPermissionClick: () -> Unit")
        )
        assertTrue(
            "SettingsScreen must have onTestNotificationClick parameter",
            content.contains("onTestNotificationClick: () -> Unit")
        )
        assertTrue(
            "Notification permission row must dynamically render Allowed vs Not allowed",
            content.contains("statusText = if (isNotificationAllowed) \"Allowed\" else \"Not allowed\"")
        )
        assertTrue(
            "Notification permission row must pass onNotificationPermissionClick",
            content.contains("onClick = onNotificationPermissionClick")
        )
    }

    @Test
    fun `3 SettingsScreen binds test notification click handler`() {
        val content = settingsScreenFile.readText()

        assertTrue(
            "Test notification row must have title Test notification",
            content.contains("title = \"Test notification\"")
        )
        assertTrue(
            "Test notification row must pass onTestNotificationClick",
            content.contains("onClick = onTestNotificationClick")
        )
    }
}
