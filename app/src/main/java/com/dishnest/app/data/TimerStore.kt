package com.dishnest.app.data

import androidx.room.withTransaction
import com.dishnest.app.domain.KitchenTimer
import kotlinx.coroutines.flow.first

interface TimerAlarms {
    fun schedule(timer: KitchenTimer)
    fun cancel(id: String)
}

/** Serialize expiry, pause and cancel so stale UI callbacks cannot revive deleted timers. */
class TimerStore(private val database: KitchenDatabase, private val alarms: TimerAlarms) {
    suspend fun start(label: String, seconds: Long, now: Long = System.currentTimeMillis()): KitchenTimer = database.withTransaction {
        require(seconds in 1..86400)
        val timer = KitchenTimer(label = label, deadline = now + seconds * 1000, remainingMillis = seconds * 1000)
        database.kitchen().timer(timer)
        alarms.schedule(timer)
        timer
    }

    suspend fun toggle(id: String, now: Long = System.currentTimeMillis()) = database.withTransaction {
        val dao = database.kitchen()
        val timer = dao.timers().first().find { it.id == id } ?: return@withTransaction
        if (timer.notified) return@withTransaction
        val remaining = timer.remaining(now)
        if (remaining == 0L) return@withTransaction
        val updated = if (timer.paused) timer.copy(paused = false, deadline = now + remaining, notified = false)
            else timer.copy(paused = true, remainingMillis = remaining)
        alarms.cancel(id)
        dao.timer(updated)
        alarms.schedule(updated)
    }

    suspend fun cancel(id: String) = database.withTransaction {
        alarms.cancel(id)
        database.kitchen().deleteTimer(id)
    }

    suspend fun expire(id: String, notify: (KitchenTimer) -> Boolean, now: Long = System.currentTimeMillis()): Boolean = database.withTransaction {
        val dao = database.kitchen()
        val timer = dao.timers().first().find { it.id == id } ?: return@withTransaction false
        if (timer.paused || timer.notified) return@withTransaction false
        if (timer.remaining(now) > 500) { alarms.schedule(timer); return@withTransaction false }
        val posted = notify(timer)
        // Handled even if notifications are denied: don't replay an old alarm on every restart.
        dao.timer(timer.copy(notified = true))
        posted
    }
}
