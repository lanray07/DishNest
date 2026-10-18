package com.dishnest.app

import com.dishnest.app.platform.EntitlementClock
import org.junit.Assert.*
import org.junit.Test

class EntitlementClockTest {
    @Test
    fun stoppedWallClockCannotFreezeCachedEntitlement() {
        val clock = EntitlementClock(1_000_000, 100, 1_000_000)
        val reading = clock.sample(1_000_000, 3_600_100)
        assertEquals(4_600_000, reading.now)
        assertTrue(reading.rollback)
    }

    @Test
    fun persistedHighWaterPreventsRestartFromRewindingExpiry() {
        val clock = EntitlementClock(1_000_000, 0, 2_000_000)
        assertEquals(2_000_000, clock.sample(1_000_000, 100).now)
        assertTrue(clock.sample(1_000_000, 100).rollback)
    }

    @Test
    fun freshServerVerificationCanRecoverAfterAccidentalFutureClock() {
        val clock = EntitlementClock(1_000_000, 100, 100_000_000)
        assertTrue(clock.sample(1_000_000, 100).rollback)
        clock.serverConfirmed(1_000_000, 100)
        assertFalse(clock.sample(1_001_000, 1100).rollback)
        assertEquals(1_001_000, clock.sample(1_001_000, 1100).now)
    }
}
