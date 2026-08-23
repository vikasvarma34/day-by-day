package com.vikaspokala.daybyday.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vikaspokala.daybyday.data.local.auth.SessionTokenStore
import com.vikaspokala.daybyday.data.local.planner.PlannerDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderRestorationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val db = PlannerDatabase.getInstance(context.applicationContext)
                        val scheduler = DefaultTaskReminderScheduler(context.applicationContext, db)
                        val sessionTokenStore = SessionTokenStore(context.applicationContext)
                        ReminderRestorationHandler.handleSystemEvent(
                            context = context.applicationContext,
                            sessionTokenStore = sessionTokenStore,
                            scheduler = scheduler
                        )
                    } catch (_: Exception) {
                        // Safe fallback
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
    }
}
