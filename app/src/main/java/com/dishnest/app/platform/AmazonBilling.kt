package com.dishnest.app.platform

import android.content.Context
import android.os.SystemClock
import com.amazon.device.drm.LicensingService
import com.amazon.device.drm.model.LicenseResponse
import com.amazon.device.iap.PurchasingListener
import com.amazon.device.iap.PurchasingService
import com.amazon.device.iap.model.*
import com.dishnest.app.BuildConfig
import com.dishnest.app.R
import com.google.gson.Gson
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class StoreOffer(val sku: String, val title: String, val price: String)

data class BillingState(
    val offers: List<StoreOffer> = emptyList(),
    val message: Int = R.string.billing_not_configured,
    val premium: Boolean = false,
    val ready: Boolean = false,
    val busy: Boolean = false,
)

private data class CachedReceipt(
    val id: String,
    var signed: SignedReceipt? = null,
    var cancelAt: Long? = null,
)

private data class ReceiptCache(
    val user: String,
    val receipts: MutableList<CachedReceipt> = mutableListOf(),
)

class AmazonBilling(private val context: Context) : PurchasingListener {
    private val state = MutableStateFlow(BillingState())
    val status = state.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private val restoreGuard = RestoreGuard()
    private val prefs = context.getSharedPreferences("verified_billing", Context.MODE_PRIVATE)
    private val clock =
        EntitlementClock(
            System.currentTimeMillis(),
            SystemClock.elapsedRealtime(),
            prefs.getLong("last_seen", 0),
        )
    private val gson = Gson()
    private var account: String? = null
    private var cache: ReceiptCache? = null
    private var started = false
    private var sdkReady = false
    private var accountConfirmed = false
    private var licenseRequest: com.amazon.device.drm.model.RequestId? = null
    private var updatesRequest: RequestId? = null
    private var userRequest: RequestId? = null
    private var requestedSku: String? = null
    private var requestedUser: String? = null
    private var purchaseInFlight = false
    private var purchaseTimeout: Job? = null
    private val verifier =
        runCatching {
                EntitlementVerifier(BuildConfig.BILLING_PUBLIC_KEY, BuildConfig.BILLING_ENVIRONMENT)
            }
            .getOrNull()
    private val configured =
        BuildConfig.BILLING_ENABLED &&
            verifier != null &&
            runCatching {
                    URL(BuildConfig.BILLING_VERIFY_URL).let {
                        it.protocol == "https" && it.host.isNotBlank() && it.userInfo == null
                    }
                }
                .getOrDefault(false)

    fun start() {
        if (!configured || started) return
        started = true
        testDiagnostic("startup", "BEGIN")
        runCatching {
                // Licensing initializes the SDK's sandbox/production routing. It must
                // precede PurchasingService initialization, as in Amazon's SDK sample.
                verifyStore()
                PurchasingService.registerListener(context.applicationContext, this)
                PurchasingService.enablePendingPurchases()
                refresh()
                scope.launch {
                    while (true) {
                        delay(30_000)
                        recompute()
                    }
                }
            }
            .onFailure {
                testDiagnostic("startup", it.javaClass.simpleName)
                started = false
                unavailable()
            }
    }

    /** Reconfirm the current Amazon account before restoring or offering a purchase. */
    fun refresh() {
        if (!configured) return
        if (!started) {
            start()
            return
        }
        accountConfirmed = false
        if (!sdkReady) {
            verifyStore()
            return
        }
        state.value = state.value.copy(ready = false, premium = false, offers = emptyList())
        runCatching {
                userRequest = PurchasingService.getUserData()
                PurchasingService.getProductData(EntitlementVerifier.premiumSkus)
            }
            .onFailure { unavailable() }
    }

    fun restore() {
        if (!configured) {
            message(R.string.billing_not_configured)
            return
        }
        if (!state.value.busy) refresh()
    }

    fun purchase(sku: String) {
        if (
            !configured ||
                !state.value.ready ||
                state.value.busy ||
                state.value.premium ||
                state.value.offers.none { it.sku == sku }
        )
            return
        requestedSku = sku
        accountConfirmed = false
        requestedUser = account
        state.value = state.value.copy(busy = true)
        runCatching { userRequest = PurchasingService.getUserData() }.onFailure { unavailable() }
        purchaseTimeout?.cancel()
        purchaseTimeout =
            scope.launch {
                delay(120_000)
                requestedSku = null
                purchaseInFlight = false
                // Late callbacks still get verified; only the UI busy lock times out.
                state.value = state.value.copy(busy = false, message = R.string.billing_retry)
            }
    }

    override fun onUserDataResponse(response: UserDataResponse) {
        if (response.requestId != userRequest) return
        if (response.requestStatus != UserDataResponse.RequestStatus.SUCCESSFUL) {
            unavailable()
            return
        }
        val user = response.userData.userId
        accountConfirmed = true
        if (user != account) {
            account = user
            updatesRequest = null
            cache =
                runCatching {
                        gson
                            .fromJson(
                                prefs.getString(EntitlementVerifier.hash(user), ""),
                                ReceiptCache::class.java,
                            )
                            ?.takeIf {
                                it.user == user &&
                                    it.receipts.size <= 1000 &&
                                    it.receipts.all { r -> r.id.isNotBlank() }
                            }
                    }
                    .getOrNull() ?: ReceiptCache(user)
            state.value = state.value.copy(premium = false)
        }
        recompute()
        restoreGuard.begin(cache?.receipts?.map { it.id }.orEmpty())
        state.value = state.value.copy(ready = false, message = R.string.billing_ready)
        val sku = requestedSku
        val sameAccount = requestedUser == user
        requestedSku = null
        requestedUser = null
        if (sku != null && sameAccount && !state.value.premium) {
            runCatching {
                    PurchasingService.purchase(sku)
                    purchaseInFlight = true
                }
                .onFailure { unavailable() }
        } else state.value = state.value.copy(busy = purchaseInFlight)
        runCatching { requestUpdates(true) }
            .onFailure {
                restoreGuard.historyFailure()
                message(R.string.billing_retry)
            }
        // Revalidate known receipts even when the store's update page is empty.
        scope.launch { verifyKnown(user) }
    }

    override fun onProductDataResponse(response: ProductDataResponse) {
        if (response.requestStatus != ProductDataResponse.RequestStatus.SUCCESSFUL) {
            state.value = state.value.copy(offers = emptyList(), message = R.string.billing_retry)
            return
        }
        state.value =
            state.value.copy(
                offers =
                    response.productData.values
                        .filter {
                            it.sku in EntitlementVerifier.premiumSkus &&
                                it.productType != ProductType.CONSUMABLE
                        }
                        .sortedBy {
                            when (it.sku) {
                                "dishnest.premium.lifetime" -> 0
                                "dishnest.premium.annual" -> 1
                                else -> 2
                            }
                        }
                        .map { StoreOffer(it.sku, it.title, it.price) }
            )
    }

    override fun onPurchaseResponse(response: PurchaseResponse) {
        purchaseTimeout?.cancel()
        purchaseInFlight = false
        state.value = state.value.copy(busy = false)
        when (response.requestStatus) {
            PurchaseResponse.RequestStatus.SUCCESSFUL ->
                receive(response.userData.userId, listOf(response.receipt))
            PurchaseResponse.RequestStatus.ALREADY_PURCHASED -> {
                message(R.string.billing_already_owned)
                restore()
            }
            PurchaseResponse.RequestStatus.PENDING -> message(R.string.billing_pending)
            PurchaseResponse.RequestStatus.INVALID_SKU ->
                message(R.string.billing_unavailable_product)
            else -> message(R.string.billing_purchase_failed)
        }
    }

    override fun onPurchaseUpdatesResponse(response: PurchaseUpdatesResponse) {
        if (response.requestId != updatesRequest) return
        updatesRequest = null
        if (response.requestStatus != PurchaseUpdatesResponse.RequestStatus.SUCCESSFUL) {
            restoreGuard.historyFailure()
            state.value = state.value.copy(ready = false)
            message(R.string.billing_retry)
            return
        }
        val user = response.userData.userId
        if (user != account) {
            refresh()
            return
        }
        restoreGuard.page(
            response.receipts
                .filter { it.sku in EntitlementVerifier.receiptSkus }
                .map { it.receiptId },
            response.hasMore(),
        )
        receive(user, response.receipts)
        if (response.hasMore())
            runCatching { requestUpdates(false) }
                .onFailure {
                    restoreGuard.historyFailure()
                    state.value = state.value.copy(ready = false)
                    message(R.string.billing_retry)
                }
    }

    private fun receive(user: String, receipts: List<Receipt>) {
        if (user != account) {
            refresh()
            return
        }
        val current = cache ?: return
        state.value = state.value.copy(ready = false)
        restoreGuard.add(
            receipts.filter { it.sku in EntitlementVerifier.receiptSkus }.map { it.receiptId }
        )
        for (receipt in receipts) {
            if (receipt.sku !in EntitlementVerifier.receiptSkus) continue
            var stored = current.receipts.firstOrNull { it.id == receipt.receiptId }
            if (stored == null && current.receipts.size < 1000) {
                stored = CachedReceipt(receipt.receiptId)
                current.receipts.add(stored)
            }
            // Turning off autorenew can supply a future end date. Bound access by
            // that date rather than treating every non-null date as already expired.
            receipt.cancelDate?.time?.let { deadline ->
                stored?.cancelAt = minOf(stored?.cancelAt ?: deadline, deadline)
                if (deadline <= System.currentTimeMillis()) stored?.signed = null
            }
        }
        if (!persist()) {
            message(R.string.billing_retry)
            return
        }
        recompute()
        scope.launch { verifyKnown(user) }
    }

    private suspend fun verifyKnown(user: String, session: Long = restoreGuard.generation) =
        mutex.withLock {
            val current = cache?.takeIf { it.user == user && account == user } ?: return@withLock
            for (receipt in current.receipts.toList()) {
                if (account != user) return@withLock
                val nonce = UUID.randomUUID().toString()
                val result = runCatching {
                    val signed = withContext(Dispatchers.IO) { request(user, receipt.id, nonce) }
                    val verified =
                        verifier!!.verify(
                            signed,
                            user,
                            receipt.id,
                            System.currentTimeMillis(),
                            nonce,
                        )
                    if (account != user || cache !== current) return@withLock
                    clock.serverConfirmed(verified.verifiedAt, SystemClock.elapsedRealtime())
                    prefs.edit().putLong("last_seen", verified.verifiedAt).commit()
                    receipt.signed = signed
                    check(persist())
                    recompute()
                    // Durable, verified access precedes fulfilment. Repeated acknowledgement is
                    // safe.
                    if (
                        verified.allowsAccess(
                            clock
                                .sample(System.currentTimeMillis(), SystemClock.elapsedRealtime())
                                .now,
                            receipt.cancelAt,
                        ) && accountConfirmed && state.value.premium
                    )
                        PurchasingService.notifyFulfillment(receipt.id, FulfillmentResult.FULFILLED)
                }
                result.exceptionOrNull()?.let { error ->
                    testDiagnostic("verification-error", error.javaClass.simpleName)
                    testDiagnostic("verification-stack", error.stackTrace.take(4).joinToString("|") { frame -> frame.className + "." + frame.methodName + ":" + frame.lineNumber })
                }
                restoreGuard.verification(receipt.id, result.isSuccess, session)
            }
            if (account == user) {
                recompute()
                state.value =
                    state.value.copy(
                        ready = restoreGuard.ready && accountConfirmed && !purchaseInFlight
                    )
                message(
                    if (restoreGuard.historyFailed) R.string.billing_retry
                    else if (restoreGuard.verificationFailed) R.string.billing_verification_retry
                    else if (!restoreGuard.historyComplete) R.string.billing_ready
                    else if (state.value.premium) R.string.billing_active
                    else R.string.billing_no_purchases
                )
            }
        }

    private fun request(user: String, receipt: String, nonce: String): SignedReceipt {
        val connection = URL(BuildConfig.BILLING_VERIFY_URL).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use {
                it.write(
                    gson
                        .toJson(mapOf("userId" to user, "receiptId" to receipt, "nonce" to nonce))
                        .toByteArray(Charsets.UTF_8)
                )
            }
            val status = connection.responseCode
            testDiagnostic("verifier-http", status.toString())
            check(status == 200)
            val bytes =
                connection.inputStream.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    while (true) {
                        val size = input.read(buffer)
                        if (size < 0) break
                        check(output.size() + size <= 32768)
                        output.write(buffer, 0, size)
                    }
                    output.toByteArray()
                }
            return gson.fromJson(String(bytes, Charsets.UTF_8), SignedReceipt::class.java)
        } finally {
            connection.disconnect()
        }
    }

    private fun persist(): Boolean {
        val current = cache ?: return false
        return prefs
            .edit()
            .putString(EntitlementVerifier.hash(current.user), gson.toJson(current))
            .commit()
    }

    private fun recompute() {
        val reading = clock.sample(System.currentTimeMillis(), SystemClock.elapsedRealtime())
        val now = reading.now
        val rollback = reading.rollback
        prefs.edit().putLong("last_seen", now).apply()
        val premium =
            !rollback &&
                accountConfirmed &&
                account != null &&
                cache?.user == account &&
                cache?.receipts?.any { receipt ->
                    receipt.signed?.let {
                        runCatching {
                                verifier!!
                                    .verify(it, account!!, receipt.id, now)
                                    .allowsAccess(now, receipt.cancelAt)
                            }
                            .getOrDefault(false)
                    } == true
                } == true
        state.value = state.value.copy(premium = premium)
    }

    private fun message(id: Int) {
        state.value = state.value.copy(message = id)
    }

    fun hasPremium(): Boolean {
        recompute()
        return state.value.premium
    }

    private fun unavailable() {
        accountConfirmed = false
        account = null
        cache = null
        requestedSku = null
        state.value =
            state.value.copy(
                ready = false,
                premium = false,
                busy = false,
                message = R.string.billing_account_unavailable,
            )
    }

    private fun verifyStore() {
        if (licenseRequest != null) return
        state.value = state.value.copy(ready = false, premium = false)
        runCatching {
                licenseRequest =
                    LicensingService.verifyLicense(context.applicationContext) { response ->
                        testDiagnostic("license", response.requestStatus.name + "/" + LicensingService.getAppstoreSDKMode())
                        scope.launch {
                            // Also tolerate an SDK callback delivered before verifyLicense returns.
                            yield()
                            if (response.requestId != licenseRequest) return@launch
                            licenseRequest = null
                            sdkReady =
                                response.requestStatus == LicenseResponse.RequestStatus.LICENSED &&
                                    LicensingService.getAppstoreSDKMode()
                                        .equals(
                                            if (BuildConfig.BILLING_ENVIRONMENT == "lat")
                                                "production"
                                            else BuildConfig.BILLING_ENVIRONMENT,
                                            ignoreCase = true,
                                        )
                            if (sdkReady) refresh() else unavailable()
                        }
                    }
                val request = licenseRequest
                testDiagnostic("sdk-mode", LicensingService.getAppstoreSDKMode())
                scope.launch {
                    delay(30_000)
                    if (licenseRequest == request) {
                        testDiagnostic("license-timeout", "NO_CALLBACK")
                        licenseRequest = null
                        unavailable()
                    }
                }
            }
            .onFailure {
                testDiagnostic("license-start", it.javaClass.simpleName)
                testDiagnostic("license-stack", it.stackTrace.take(6).joinToString("|") { frame -> frame.className + "." + frame.methodName + ":" + frame.lineNumber })
                licenseRequest = null
                unavailable()
            }
    }

    /** Sandbox diagnostics contain enums only, never accounts, receipts, URLs or exceptions. */
    private fun testDiagnostic(event: String, status: String) {
        if (BuildConfig.DEBUG && BuildConfig.BUILD_TYPE == "purchaseTest") {
            android.util.Log.i("DishNestBillingTest", "$event:$status")
            runCatching {
                val file = java.io.File(context.filesDir, "billing-test-diagnostics.txt")
                val lines = if (file.isFile) file.readLines().takeLast(31) else emptyList()
                file.writeText((lines + "$event:$status").joinToString("\n"))
            }
        }
    }

    private fun requestUpdates(reset: Boolean) {
        val request = PurchasingService.getPurchaseUpdates(reset)
        updatesRequest = request
        scope.launch {
            delay(30_000)
            if (updatesRequest == request) {
                updatesRequest = null
                restoreGuard.historyFailure()
                state.value = state.value.copy(ready = false, message = R.string.billing_retry)
            }
        }
    }
}
