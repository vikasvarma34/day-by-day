package com.vikaspokala.daybyday.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.vikaspokala.daybyday.R

interface AppNotificationManager {
    fun createNotificationChannel()
    fun areNotificationsAllowed(): Boolean
    fun postSampleNotification(): Boolean
    fun openNotificationSettings(context: Context)

    companion object {
        const val CHANNEL_REMINDERS_ID = "daybyday_reminders"
        const val CHANNEL_REMINDERS_NAME = "Reminders"
        const val CHANNEL_REMINDERS_DESCRIPTION = "Task and planner reminders"
        const val SAMPLE_NOTIFICATION_ID = 1001
    }
}

open class DefaultAppNotificationManager(
    private val context: Context
) : AppNotificationManager {

    private val notificationManager: NotificationManager?
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    override fun createNotificationChannel() {
        val manager = notificationManager ?: return
        val channel = NotificationChannel(
            AppNotificationManager.CHANNEL_REMINDERS_ID,
            AppNotificationManager.CHANNEL_REMINDERS_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = AppNotificationManager.CHANNEL_REMINDERS_DESCRIPTION
        }
        manager.createNotificationChannel(channel)
    }

    override fun areNotificationsAllowed(): Boolean {
        val manager = notificationManager ?: return false

        if (!manager.areNotificationsEnabled()) {
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionStatus = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }

        val channel = manager.getNotificationChannel(AppNotificationManager.CHANNEL_REMINDERS_ID)
        if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) {
            return false
        }

        return true
    }

    override fun postSampleNotification(): Boolean {
        if (!areNotificationsAllowed()) {
            return false
        }

        createNotificationChannel()

        val manager = notificationManager ?: return false
        val notification = Notification.Builder(context, AppNotificationManager.CHANNEL_REMINDERS_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Day by Day")
            .setContentText("This is a sample reminder.")
            .setAutoCancel(true)
            .build()

        return try {
            manager.notify(AppNotificationManager.SAMPLE_NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    override fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(fallback)
            } catch (_: Exception) {
                // Ignore failure if settings activity cannot be found
            }
        }
    }
}
