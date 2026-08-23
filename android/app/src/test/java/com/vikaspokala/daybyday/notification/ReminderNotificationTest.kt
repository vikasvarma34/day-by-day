package com.vikaspokala.daybyday.notification

import com.vikaspokala.daybyday.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ReminderNotificationTest {

    @Test
    fun reminderBroadcastReceiver_definesCorrectActionConstants() {
        assertEquals("com.vikaspokala.daybyday.action.FIRE_REMINDER", ReminderBroadcastReceiver.ACTION_FIRE_REMINDER)
        assertEquals("com.vikaspokala.daybyday.action.OPEN_TODAY", ReminderBroadcastReceiver.ACTION_OPEN_TODAY)
        assertEquals("extra_task_id", ReminderBroadcastReceiver.EXTRA_TASK_ID)
        assertEquals("extra_task_title", ReminderBroadcastReceiver.EXTRA_TASK_TITLE)
    }
}
