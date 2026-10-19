package com.dishnest.app

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import com.amazon.device.drm.LicensingService
import com.amazon.device.iap.PurchasingService
import com.amazon.device.iap.model.FulfillmentResult
import com.dishnest.app.platform.EntitlementVerifier
import com.dishnest.app.platform.SignedReceipt
import com.google.gson.Gson
import org.json.JSONObject

/** Debug package only: emits configuration/status enums and never purchase identifiers. */
class PurchaseStatusActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(packageName == "com.dishnest.app.purchasetest")
        val billing = (application as DishNestApp).billing
        val key = runCatching { EntitlementVerifier(BuildConfig.BILLING_PUBLIC_KEY, "sandbox") }
        val cleanup = intent.getBooleanExtra("sandbox_unavailable_fixture", false)
        var unavailable = 0
        if (cleanup) {
            // App Tester blocks all subsequent checkout while an expired synthetic
            // order is unfulfilled. Mark only signed, inactive fixtures UNAVAILABLE;
            // never pretend that content was delivered. This class is absent from release.
            check(BuildConfig.DEBUG && BuildConfig.BILLING_ENVIRONMENT == "sandbox")
            check(BuildConfig.BILLING_VERIFY_URL == "https://dishnest-receipt-verifier-sandbox.lanraybanks.workers.dev/v1/verify")
            check(LicensingService.getAppstoreSDKMode() == "SANDBOX")
            check(billing.status.value.ready && !billing.status.value.premium)
            // Debug-only introspection avoids adding a production billing control API.
            val accountField = billing.javaClass.getDeclaredField("account").apply { isAccessible = true }
            val user = accountField.get(billing) as? String ?: error("No confirmed sandbox account")
            val cached = getSharedPreferences("verified_billing", MODE_PRIVATE)
                .getString(EntitlementVerifier.hash(user), null) ?: error("No sandbox cache")
            val cache = JSONObject(cached)
            check(cache.getString("user") == user)
            val receipts = cache.getJSONArray("receipts")
            check(receipts.length() in 1..16)
            val ids = (0 until receipts.length()).map { index ->
                val receipt = receipts.getJSONObject(index)
                val id = receipt.getString("id")
                val signed = Gson().fromJson(receipt.getJSONObject("signed").toString(), SignedReceipt::class.java)
                val claim = key.getOrThrow().verify(signed, user, id, System.currentTimeMillis())
                check(!claim.active && claim.sku in EntitlementVerifier.receiptSkus)
                id
            }
            // All fixtures are checked before the first acknowledgement is requested.
            ids.forEach { PurchasingService.notifyFulfillment(it, FulfillmentResult.UNAVAILABLE) }
            unavailable = ids.size
        }
        val report = JSONObject().apply {
            put("fixtureCleanupOnly", cleanup)
            put("unavailableFixtureRequests", unavailable)
            put("debug", BuildConfig.DEBUG)
            put("billingEnabled", BuildConfig.BILLING_ENABLED)
            put("environment", BuildConfig.BILLING_ENVIRONMENT)
            put("keyValid", key.isSuccess)
            put("keyError", key.exceptionOrNull()?.javaClass?.simpleName ?: "none")
            put("sandboxEndpoint", BuildConfig.BILLING_VERIFY_URL == "https://dishnest-receipt-verifier-sandbox.lanraybanks.workers.dev/v1/verify")
            put("sdkMode", runCatching { LicensingService.getAppstoreSDKMode() }.getOrDefault("ERROR"))
            put("message", getString(billing.status.value.message))
            put("offers", billing.status.value.offers.size)
            put("ready", billing.status.value.ready)
            put("premium", billing.status.value.premium)
        }.toString()
        java.io.File(filesDir, "purchase-test-status.json").writeText(report)
        setContentView(TextView(this).apply { text = report })
    }
}
