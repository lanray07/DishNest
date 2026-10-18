package com.dishnest.app.platform

data class BillingTime(val now: Long, val rollback: Boolean)

/** Monotonic time bounds offline access even if the wall clock stops or moves back. */
class EntitlementClock(wall: Long, elapsed: Long, private var highWater: Long) {
    private var anchorWall = wall
    private var anchorElapsed = elapsed

    fun sample(wall: Long, elapsed: Long): BillingTime {
        val monotonic = anchorWall + (elapsed - anchorElapsed).coerceAtLeast(0)
        highWater = maxOf(wall, monotonic, highWater)
        return BillingTime(highWater, wall < highWater - 300_000)
    }

    // Called only after signature, fresh request nonce and identities are verified.
    fun serverConfirmed(verifiedAt: Long, elapsed: Long) {
        anchorWall = verifiedAt
        anchorElapsed = elapsed
        highWater = verifiedAt
    }
}
