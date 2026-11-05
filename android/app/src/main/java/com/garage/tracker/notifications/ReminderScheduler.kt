package com.garage.tracker.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.garage.tracker.data.Reminder

const val CHANNEL_ID = "reminders"
const val EXTRA_ID = "reminder_id"
const val EXTRA_TITLE = "title"
const val EXTRA_NOTE = "note"

/**
 * Schedules reminders with AlarmManager. Uses an inexact-but-idle-safe alarm, so no special
 * "exact alarm" permission is needed; delivery may be a few minutes late on some devices.
 */
class ReminderScheduler(private val context: Context) {
    private val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(r: Reminder) {
        if (r.triggerAt <= System.currentTimeMillis()) return
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.triggerAt, pendingIntent(r))
    }

    fun cancel(r: Reminder) {
        alarms.cancel(pendingIntent(r))
    }

    fun scheduleAll(reminders: List<Reminder>) = reminders.forEach(::schedule)

    private fun pendingIntent(r: Reminder): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(EXTRA_ID, r.reminderId)
            .putExtra(EXTRA_TITLE, r.title)
            .putExtra(EXTRA_NOTE, r.note)
        return PendingIntent.getBroadcast(
            context, r.reminderId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        fun createChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Maintenance and service reminders" }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
