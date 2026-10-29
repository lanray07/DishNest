package com.dishnest.app

import androidx.test.core.app.ApplicationProvider
import com.dishnest.app.platform.LocalMetrics
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@Config(sdk = [28], application = DishNestApp::class)
class LocalMetricsTest {
    private val context get() = ApplicationProvider.getApplicationContext<DishNestApp>()
    private val prefs get() = context.getSharedPreferences("local_usage", 0)
    private fun metrics(day: Int = 1) = LocalMetrics(context, Clock.fixed(Instant.parse(String.format(java.util.Locale.ROOT, "2026-01-%02dT12:00:00Z", day)), ZoneOffset.UTC))

    @Test fun optOutCollectsNothingAndRepeatedConsentDoesNotEraseHistory() {
        val m = metrics(); m.clear()
        m.record("voice_used"); assertTrue(prefs.all.isEmpty())
        m.enabled = true; assertTrue(m.enabledState.value)
        m.record("voice_used"); m.enabled = true
        assertEquals(1, prefs.getInt("voice_used", 0))
        assertEquals("2026-01-01", prefs.getString("cohort", null))
        m.enabled = false
        assertFalse(m.enabledState.value)
        assertEquals(mapOf("enabled" to false), prefs.all)
    }

    @Test fun unknownEventsAndPrivateTextAreNeverStored() {
        val m = metrics(); m.clear(); m.enabled = true
        m.record("private recipe title"); m.record("receipt:private-id")
        assertEquals(setOf("enabled", "cohort"), prefs.all.keys)
    }

    @Test fun onceOnlyEventsSurviveAnotherMetricsInstanceAndRetainExactDayMeaning() {
        val m = metrics(); m.clear(); m.enabled = true
        repeat(2) { m.record("first_recipe_saved"); m.record("premium_conversion") }
        repeat(2) { metrics(8).opened() }
        metrics(9).opened()
        metrics(31).opened(); metrics(31).opened()
        for (event in listOf("first_recipe_saved", "premium_conversion", "retained_day_7", "retained_day_30"))
            assertEquals(event, 1, prefs.getInt(event, 0))
    }

    @Test fun concurrentWritersCannotLoseEventsAndCountersCannotWrap() {
        val m = metrics(); m.clear(); m.enabled = true
        val threads = List(4) { Thread { repeat(50) { metrics().record("voice_used") } } }
        threads.forEach { it.start() }; threads.forEach { it.join() }
        assertEquals(200, prefs.getInt("voice_used", 0))
        prefs.edit().putInt("voice_used", Int.MAX_VALUE).commit()
        m.record("voice_used"); assertEquals(Int.MAX_VALUE, prefs.getInt("voice_used", 0))
        m.clear(); assertFalse(m.enabled); assertTrue(prefs.all.isEmpty())
    }
}
