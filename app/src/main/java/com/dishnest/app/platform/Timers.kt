package com.dishnest.app.platform

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import com.dishnest.app.DishNestApp
import com.dishnest.app.MainActivity
import com.dishnest.app.R
import com.dishnest.app.domain.KitchenTimer
import com.dishnest.app.data.TimerAlarms
import com.dishnest.app.data.TimerStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class TimerScheduler(private val context: Context) : TimerAlarms {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    private fun intent(id: String) =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, TimerReceiver::class.java).setAction(id).putExtra("id", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    override fun schedule(timer: KitchenTimer) {
        if (!timer.paused && !timer.notified)
            alarms.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                timer.deadline.coerceAtLeast(System.currentTimeMillis() + 500),
                intent(timer.id),
            )
    }

    override fun cancel(id: String) {
        alarms.cancel(intent(id))
        context.getSystemService(NotificationManager::class.java).cancel("timer:$id", 0)
    }
}

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = (context.applicationContext as DishNestApp).database
                val id = intent.getStringExtra("id") ?: return@launch
                TimerStore(db, TimerScheduler(context)).expire(id, { t ->
                    val manager = context.getSystemService(NotificationManager::class.java)
                    manager.createNotificationChannel(
                        NotificationChannel(
                            "cooking",
                            AppLanguages.localized(context).getString(R.string.timer_channel),
                            NotificationManager.IMPORTANCE_HIGH,
                        )
                    )
                    if (
                        manager.areNotificationsEnabled() && (Build.VERSION.SDK_INT < 33 ||
                            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                                PackageManager.PERMISSION_GRANTED)
                    ) {
                        val open =
                            PendingIntent.getActivity(
                                context,
                                0,
                                Intent(context, MainActivity::class.java),
                                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                            )
                        manager.notify(
                            "timer:${t.id}", 0,
                            Notification.Builder(context, "cooking")
                                .setSmallIcon(R.drawable.ic_nest)
                                .setContentTitle(t.label)
                                .setContentText(AppLanguages.localized(context).getString(R.string.timer_ready))
                                .setContentIntent(open)
                                .setAutoCancel(true)
                                .setVisibility(Notification.VISIBILITY_PRIVATE)
                                .build(),
                        )
                        true
                    } else {
                        false
                    }
                })
            } finally {
                pending.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                (context.applicationContext as DishNestApp)
                    .database
                    .kitchen()
                    .timers()
                    .first()
                    .forEach { TimerScheduler(context).schedule(it) }
                PantryReminders(context).reschedule()
            } finally {
                pending.finish()
            }
        }
    }
}
