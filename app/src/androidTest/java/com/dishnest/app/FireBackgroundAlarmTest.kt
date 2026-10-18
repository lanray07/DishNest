package com.dishnest.app

import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dishnest.app.data.TimerStore
import com.dishnest.app.platform.TimerScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Run in two separate instrumentation processes, with am kill between them. */
@RunWith(AndroidJUnit4::class)
class FireBackgroundAlarmTest {
    @Test fun backgroundAlarmProbe(): Unit = runBlocking {
        check(BuildConfig.APPLICATION_ID == "com.dishnest.app.qa")
        val phase = InstrumentationRegistry.getArguments().getString("phase")
        assumeTrue(phase == "seed" || phase == "verify")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as DishNestApp
        val dao = app.database.kitchen()
        val store = TimerStore(app.database, TimerScheduler(app))
        val label = "QA background alarm fixture"
        if (phase == "seed") {
            dao.timers().first().filter { it.label == label }.forEach { store.cancel(it.id) }
            store.start(label, 15)
            Unit
        } else {
            val timer = dao.timers().first().single { it.label == label }
            try {
                assertTrue("Background receiver did not persist delivery", timer.notified)
                val manager = app.getSystemService(NotificationManager::class.java)
                assertTrue("Own timer notification missing", manager.activeNotifications.any { it.tag == "timer:${timer.id}" })
            } finally { store.cancel(timer.id) }
        }
    }
}
