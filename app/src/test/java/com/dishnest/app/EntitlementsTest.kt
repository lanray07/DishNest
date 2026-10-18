package com.dishnest.app

import com.dishnest.app.platform.*
import com.google.gson.Gson
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class EntitlementsTest {
    @Test
    fun signedReceiptCannotExtendAccessPastStoreCancellation() {
        val verified = verifier.verify(signed(claim()), "user", "receipt", now)
        assertTrue(verified.allowsAccess(now, now + 500))
        assertFalse(verified.allowsAccess(now + 500, now + 500))
        assertFalse(verified.allowsAccess(now, now - 1))
    }

    private val now = 1_800_000_000_000L
    private val pair =
        KeyPairGenerator.getInstance("EC")
            .apply { initialize(ECGenParameterSpec("secp256r1")) }
            .generateKeyPair()
    private val verifier =
        EntitlementVerifier(Base64.getEncoder().encodeToString(pair.public.encoded), "sandbox")

    private fun claim() =
        VerifiedEntitlement(
            1,
            "com.dishnest.app",
            "sandbox",
            EntitlementVerifier.hash("user"),
            EntitlementVerifier.hash("receipt"),
            "nonce",
            now,
            now + 3_600_000,
            "dishnest.premium.annual",
            true,
        )

    private fun signed(value: VerifiedEntitlement): SignedReceipt {
        val bytes = Gson().toJson(value).toByteArray(Charsets.UTF_8)
        val sig =
            Signature.getInstance("SHA256withECDSA")
                .apply {
                    initSign(pair.private)
                    update(bytes)
                }
                .sign()
        return SignedReceipt(
            Base64.getEncoder().encodeToString(bytes),
            Base64.getEncoder().encodeToString(sig),
        )
    }

    @Test
    fun validReceiptGrantsOnlyItsBoundAccountAndReceipt() {
        val token = signed(claim())
        assertTrue(verifier.verify(token, "user", "receipt", now, "nonce").active)
        for ((user, receipt, nonce) in
            listOf(
                Triple("other", "receipt", "nonce"),
                Triple("user", "other", "nonce"),
                Triple("user", "receipt", "other"),
            )) assertThrows(IllegalArgumentException::class.java) {
            verifier.verify(token, user, receipt, now, nonce)
        }
    }

    @Test
    fun alteredOrWrongEnvironmentTokensCannotGrantAccess() {
        val token = signed(claim())
        val altered =
            token.copy(
                payload =
                    Base64.getEncoder()
                        .encodeToString(
                            Gson().toJson(claim().copy(expiresAt = now + 86_400_000)).toByteArray()
                        )
            )
        assertThrows(IllegalArgumentException::class.java) {
            verifier.verify(altered, "user", "receipt", now)
        }
        for (bad in
            listOf(
                claim().copy(environment = "production"),
                claim().copy(audience = "other"),
                claim().copy(sku = "other"),
                claim().copy(version = 2),
            )) assertThrows(IllegalArgumentException::class.java) {
            verifier.verify(signed(bad), "user", "receipt", now)
        }
    }

    @Test
    fun expiryFutureClockAndOversizedGraceAreRejected() {
        for (bad in
            listOf(
                claim().copy(expiresAt = now),
                claim().copy(verifiedAt = now + 300_001),
                claim().copy(expiresAt = now + 86_400_001),
            )) assertThrows(IllegalArgumentException::class.java) {
            verifier.verify(signed(bad), "user", "receipt", now)
        }
    }

    @Test
    fun signedRevocationDoesNotGrantAccess() {
        assertFalse(
            verifier
                .verify(signed(claim().copy(active = false, sku = "")), "user", "receipt", now)
                .active
        )
    }
}
