package com.vikaspokala.daybyday.notification

import android.content.Context
import com.vikaspokala.daybyday.data.local.auth.SessionTokenStore

object ReminderRestorationHandler {

    suspend fun restoreRemindersIfAuthenticated(
        tokenProvider: suspend () -> String?,
        scheduler: TaskReminderScheduler
    ): Boolean {
        val token = tokenProvider()
        if (token.isNullOrBlank()) {
            scheduler.cancelAllReminders()
            return false
        }

        if (!scheduler.canScheduleExactAlarms()) {
            return false
        }

        scheduler.rescheduleAllFromDatabase()
        return true
    }

    suspend fun handleSystemEvent(
        context: Context,
        sessionTokenStore: SessionTokenStore,
        scheduler: TaskReminderScheduler
    ): Boolean {
        return restoreRemindersIfAuthenticated(
            tokenProvider = { sessionTokenStore.readToken() },
            scheduler = scheduler
        )
    }
}
