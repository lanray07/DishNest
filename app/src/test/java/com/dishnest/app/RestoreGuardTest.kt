package com.dishnest.app

import com.dishnest.app.platform.RestoreGuard
import org.junit.Assert.*
import org.junit.Test

class RestoreGuardTest {
    @Test
    fun emptyHistoryCannotSkipRevalidationOfKnownReceipts() {
        val guard = RestoreGuard()
        val session = guard.begin(listOf("known"))
        guard.page(emptyList(), false)
        assertFalse(guard.ready)
        guard.verification("known", true, session)
        assertTrue(guard.ready)
    }

    @Test
    fun laterRestorePageBlocksPurchasesUntilItsReceiptIsVerified() {
        val guard = RestoreGuard()
        val session = guard.begin(emptyList())
        guard.page(listOf("first"), true)
        guard.verification("first", true, session)
        assertFalse(guard.ready)
        guard.page(listOf("last"), false)
        assertFalse(guard.ready)
        guard.verification("last", true, session)
        assertTrue(guard.ready)
    }

    @Test
    fun verificationOutageDoesNotOfferAnotherPurchaseAndRetryCanRecover() {
        val guard = RestoreGuard()
        val session = guard.begin(listOf("owned"))
        guard.page(emptyList(), false)
        guard.verification("owned", false, session)
        assertFalse(guard.ready)
        assertTrue(guard.verificationFailed)
        guard.verification("owned", true, session)
        assertTrue(guard.ready)
        assertFalse(guard.verificationFailed)
    }

    @Test
    fun oldAccountOrRestoreCallbacksCannotCompleteANewSession() {
        val guard = RestoreGuard()
        val old = guard.begin(listOf("old"))
        val current = guard.begin(listOf("new"))
        guard.page(emptyList(), false)
        guard.verification("old", true, old)
        assertFalse(guard.ready)
        guard.verification("new", true, current)
        assertTrue(guard.ready)
        guard.verification("new", false, old)
        assertTrue(guard.ready)
    }

    @Test
    fun failedHistoryRemainsBlockedEvenIfReceiptVerificationFinishes() {
        val guard = RestoreGuard()
        val session = guard.begin(listOf("owned"))
        guard.historyFailure()
        guard.verification("owned", true, session)
        assertFalse(guard.ready)
        assertTrue(guard.historyFailed)
    }
}
