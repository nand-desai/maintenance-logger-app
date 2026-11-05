package com.garage.tracker.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.garage.tracker.MainActivity
import com.garage.tracker.R
import com.garage.tracker.data.GarageDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Maintenance reminder"
        val note = intent.getStringExtra(EXTRA_NOTE).orEmpty()

        ReminderScheduler.createChannel(context)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pitstop)
            .setContentTitle(title)
            .setContentText(note.ifBlank { "Time for a pitstop 🔧" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(note.ifBlank { "Time for a pitstop 🔧" }))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        val mgr = NotificationManagerCompat.from(context)
        if (mgr.areNotificationsEnabled()) {
            @Suppress("MissingPermission") mgr.notify(id.hashCode(), n)
        }
    }
}

/** Alarms are cleared on reboot, so put the pending ones back. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderScheduler(context).scheduleAll(GarageDatabase.get(context).reminderDao().getAll())
            } finally {
                pending.finish()
            }
        }
    }
}
