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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class TimerScheduler(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    private fun intent(id: String) =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, TimerReceiver::class.java).setAction(id).putExtra("id", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun schedule(timer: KitchenTimer) {
        if (!timer.paused && !timer.notified)
            alarms.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                timer.deadline.coerceAtLeast(System.currentTimeMillis() + 500),
                intent(timer.id),
            )
    }

    fun cancel(id: String) {
        alarms.cancel(intent(id))
        context.getSystemService(NotificationManager::class.java).cancel(id.hashCode())
    }
}

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = (context.applicationContext as DishNestApp).database.kitchen()
                val t =
                    dao.timers().first().find { it.id == intent.getStringExtra("id") }
                        ?: return@launch
                if (t.paused || t.notified) return@launch
                if (t.remaining() > 500) {
                    TimerScheduler(context).schedule(t)
                    return@launch
                }
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(
                    NotificationChannel(
                        "cooking",
                        "Cooking timers",
                        NotificationManager.IMPORTANCE_HIGH,
                    )
                )
                if (
                    Build.VERSION.SDK_INT < 33 ||
                        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                            PackageManager.PERMISSION_GRANTED
                ) {
                    val open =
                        PendingIntent.getActivity(
                            context,
                            0,
                            Intent(context, MainActivity::class.java),
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                        )
                    manager.notify(
                        t.id.hashCode(),
                        Notification.Builder(context, "cooking")
                            .setSmallIcon(R.drawable.ic_nest)
                            .setContentTitle(t.label)
                            .setContentText("Your cooking timer is ready.")
                            .setContentIntent(open)
                            .setAutoCancel(true)
                            .build(),
                    )
                }
                dao.timer(t.copy(notified = true))
            } finally {
                pending.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                (context.applicationContext as DishNestApp)
                    .database
                    .kitchen()
                    .timers()
                    .first()
                    .forEach { TimerScheduler(context).schedule(it) }
            } finally {
                pending.finish()
            }
        }
    }
}
