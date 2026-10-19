package com.dishnest.app

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import com.amazon.device.drm.LicensingService
import com.dishnest.app.platform.EntitlementVerifier
import org.json.JSONObject

/** Debug package only: emits configuration/status enums and never purchase identifiers. */
class PurchaseStatusActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(packageName == "com.dishnest.app.purchasetest")
        val billing = (application as DishNestApp).billing
        val key = runCatching { EntitlementVerifier(BuildConfig.BILLING_PUBLIC_KEY, "sandbox") }
        val report = JSONObject().apply {
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
