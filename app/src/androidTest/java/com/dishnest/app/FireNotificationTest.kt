package com.dishnest.app

import android.Manifest
import android.app.NotificationManager
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dishnest.app.domain.PantryItem
import com.dishnest.app.platform.PantryReminders
import com.dishnest.app.platform.VoiceController
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class FireNotificationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as DishNestApp

    @Test fun pantryReminderIsOptInGenericAndSuppressedAfterFirstDelivery() = runBlocking {
        check(BuildConfig.APPLICATION_ID == "com.dishnest.app.qa")
        val reminders = PantryReminders(app)
        val fixture = PantryItem(name = "QA private pantry fixture", expiry = LocalDate.now().plusDays(2).toString())
        val manager = app.getSystemService(NotificationManager::class.java)
        try {
            reminders.clear()
            app.database.kitchen().pantry(fixture)
            assertFalse(reminders.deliver())
            reminders.enabled = true
            assertTrue(reminders.deliver())
            assertFalse(reminders.deliver())
            val deadline = android.os.SystemClock.elapsedRealtime() + 3000
            while (manager.activeNotifications.none { it.tag == "pantry" } && android.os.SystemClock.elapsedRealtime() < deadline)
                android.os.SystemClock.sleep(50)
            val notification = manager.activeNotifications.single { it.tag == "pantry" }.notification
            assertEquals(app.getString(R.string.pantry_reminder_message), notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString())
            assertEquals(android.app.Notification.VISIBILITY_PRIVATE, notification.visibility)
            reminders.enabled = false
            val cancellationDeadline = android.os.SystemClock.elapsedRealtime() + 3000
            while (manager.activeNotifications.any { it.tag == "pantry" } && android.os.SystemClock.elapsedRealtime() < cancellationDeadline)
                android.os.SystemClock.sleep(50)
            assertFalse(manager.activeNotifications.any { it.tag == "pantry" })
        } finally {
            reminders.clear()
            app.database.kitchen().deletePantry(fixture.id)
        }
    }

    @Test fun deniedMicrophoneReturnsTouchFallbackWithoutStartingRecognition() {
        check(BuildConfig.APPLICATION_ID == "com.dishnest.app.qa")
        assertEquals(PackageManager.PERMISSION_DENIED, app.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
        var status = ""
        instrumentation.runOnMainSync {
            val voice = VoiceController(app, { fail("Denied microphone produced a transcript") }, { status = it })
            try { voice.listen() } finally { voice.close() }
        }
        assertEquals(app.getString(R.string.copy_66bf733db7), status)
    }
}
