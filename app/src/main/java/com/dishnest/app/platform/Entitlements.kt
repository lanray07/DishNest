package com.dishnest.app.platform

import com.google.gson.Gson
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

data class SignedReceipt(val payload: String, val signature: String)

data class VerifiedEntitlement(
    val version: Int,
    val audience: String,
    val environment: String,
    val userHash: String,
    val receiptHash: String,
    val nonce: String,
    val verifiedAt: Long,
    val expiresAt: Long,
    val sku: String,
    val active: Boolean,
) {
    fun allowsAccess(now: Long, storeCancelAt: Long? = null): Boolean =
        active && expiresAt > now && (storeCancelAt == null || now < storeCancelAt)
}

/** Only signed server decisions grant access. Store callbacks are never payment proof. */
class EntitlementVerifier(publicKey: String, private val environment: String) {
    private val key =
        KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKey)))

    fun verify(
        signed: SignedReceipt,
        user: String,
        receipt: String,
        now: Long,
        nonce: String? = null,
    ): VerifiedEntitlement {
        require(signed.payload.length <= 16384 && signed.signature.length <= 512)
        val bytes = Base64.getDecoder().decode(signed.payload)
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initVerify(key)
        signature.update(bytes)
        require(signature.verify(Base64.getDecoder().decode(signed.signature))) {
            "Invalid signature"
        }
        val claim = Gson().fromJson(String(bytes, Charsets.UTF_8), VerifiedEntitlement::class.java)
        require(
            claim.version == 1 &&
                claim.audience == "com.dishnest.app" &&
                claim.environment == environment
        )
        require(claim.userHash == hash(user) && claim.receiptHash == hash(receipt))
        require(nonce == null || claim.nonce == nonce)
        require(claim.verifiedAt <= now + 300_000 && claim.verifiedAt >= now - 86_400_000)
        require(claim.expiresAt > now && claim.expiresAt <= claim.verifiedAt + 86_400_000)
        require(!claim.active || claim.sku in receiptSkus)
        return claim
    }

    companion object {
        val premiumSkus =
            setOf(
                "dishnest.premium.monthly",
                "dishnest.premium.annual",
                "dishnest.premium.lifetime",
            )
        val receiptSkus = premiumSkus + "dishnest.premium"

        fun hash(value: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
