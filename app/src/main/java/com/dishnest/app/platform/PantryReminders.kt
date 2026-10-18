package com.dishnest.app.platform

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import com.dishnest.app.DishNestApp
import com.dishnest.app.MainActivity
import com.dishnest.app.R
import com.dishnest.app.domain.PantryItem
import androidx.core.content.edit
import androidx.room.withTransaction
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

object PantryReminderPolicy {
    fun due(items: List<PantryItem>, today: LocalDate): Boolean = items.any {
        it.expiry.isNotBlank() && runCatching { !LocalDate.parse(it.expiry).isAfter(today.plusDays(3)) }.getOrDefault(false)
    }

    fun next(now: ZonedDateTime): Long {
        val today = now.toLocalDate().atTime(9, 0).atZone(now.zone)
        return (if (today.isAfter(now)) today else now.toLocalDate().plusDays(1).atTime(9, 0).atZone(now.zone))
            .toInstant().toEpochMilli()
    }
}

/** Optional, approximate daily check-date reminder; names never appear on the lock screen. */
class PantryReminders(private val context: Context) {
    companion object { private val notificationLock = Any() }
    private val preferences = context.getSharedPreferences("pantry-reminders", Context.MODE_PRIVATE)
    var enabled: Boolean
        get() = preferences.getBoolean("enabled", false)
        set(value) { synchronized(notificationLock) { preferences.edit { putBoolean("enabled", value) }; reschedule() } }
    private val operation get() = PendingIntent.getBroadcast(context, 0,
        Intent(context, PantryReminderReceiver::class.java).setAction("dishnest.pantry.daily"),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun reschedule() {
        val alarms = context.getSystemService(AlarmManager::class.java)
        alarms.cancel(operation)
        if (enabled) alarms.set(AlarmManager.RTC_WAKEUP, PantryReminderPolicy.next(ZonedDateTime.now()), operation)
        else context.getSystemService(NotificationManager::class.java).cancel("pantry", 0)
    }

    suspend fun deliver(today: LocalDate = LocalDate.now()): Boolean {
        val database = (context.applicationContext as DishNestApp).database
        return database.withTransaction {
            val items = database.kitchen().pantry().first()
            if (!PantryReminderPolicy.due(items, today)) return@withTransaction false
            synchronized(notificationLock) {
                if (!enabled || preferences.getString("last-day", "") == today.toString()) return@synchronized false
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(NotificationChannel("pantry", context.getString(R.string.pantry_reminders), NotificationManager.IMPORTANCE_DEFAULT))
                if (manager.areNotificationsEnabled() && (Build.VERSION.SDK_INT < 33 ||
                        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)) {
                    val open = PendingIntent.getActivity(context, 1, Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    manager.notify("pantry", 0, Notification.Builder(context, "pantry")
                        .setSmallIcon(R.drawable.ic_nest).setContentTitle(context.getString(R.string.pantry_reminders))
                        .setContentText(context.getString(R.string.pantry_reminder_message)).setVisibility(Notification.VISIBILITY_PRIVATE)
                        .setContentIntent(open).setAutoCancel(true).build())
                    preferences.edit { putString("last-day", today.toString()) }
                    true
                } else false
            }
        }
    }

    fun clear() { synchronized(notificationLock) { preferences.edit { clear() }; reschedule() } }
}

class PantryReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { PantryReminders(context).deliver() }
            finally { PantryReminders(context).reschedule(); pending.finish() }
        }
    }
}
