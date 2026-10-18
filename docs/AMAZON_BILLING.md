# Amazon billing: local implementation and activation

Nothing in this workflow deploys the service, creates store products or publishes an APK. The default APK has purchases disabled. The publisher confirmed there is no existing catalog or verification service; this repository prepares those integrations locally.

## Catalog contract

| SKU | Type | Role |
| --- | --- | --- |
| `dishnest.premium` | Subscription | Parent; receipts may report this SKU |
| `dishnest.premium.monthly` | Subscription term | Purchasable monthly term |
| `dishnest.premium.annual` | Subscription term | Purchasable annual term |
| `dishnest.premium.lifetime` | Entitlement | Optional one-time unlock |

Prices, territories and subscription terms require publisher approval in Amazon's console. `billing-server/amazon.sdktester.json` contains visibly labelled test prices only. They are never bundled as app prices. The app displays title and price from `getProductData`; Amazon's confirmation screen supplies the final terms. No free trial or recipe-template pack is advertised or implemented. The RVS sandbox may return a synthesized parent `_term` SKU, so verified subscription-family receipts are supported without guessing their duration from the SKU.

## Local service

Requires Node 22 or newer; no npm dependencies. Run from the project root:

```powershell
node --test billing-server/verification.test.mjs
node billing-server/prepare-local.mjs
node --env-file=.local-billing/.env billing-server/server.mjs
```

Preparation generates an isolated P-256 development signing key and refuses to overwrite it. `.local-billing/`, private keys and environment files are excluded from Git and delivery archives. The service listens only on `127.0.0.1:8787`; stop it with Ctrl+C. It does not expose a public endpoint. The sandbox shared-secret value is a non-production placeholder; Amazon permits any non-empty value in its RVS cloud sandbox. No production secret has been provisioned.

`POST /v1/verify` accepts `{userId, receiptId, nonce}`. Those identifiers are bearer purchase evidence: do not log them or put them in URLs to this service. It makes a server-to-server HTTPS request to Amazon with redirects disabled, validates the receipt identity, product family, type, dates and environment, then returns `{payload, signature}`. The payload is base64 UTF-8 JSON and the signature is DER ECDSA/SHA-256 over the decoded payload. There is no verification-success simulation route.

HTTP 400/410 from Amazon creates a signed inactive decision; throttling, invalid credentials, network failures and server failures create a retryable error, never a grant or fabricated revocation. Receipt data is processed in memory and is not persisted by this service. The Android app retains known receipts privately so it can reverify them even if Amazon's update page is empty.

## Android configuration

Amazon also requires its app-specific **Appstore authentication public key**, downloaded from the app's Developer Console as `AppstoreAuthenticationKey.pem`, in `app/src/main/assets/`. This is distinct from the receipt-service EC public key below. It is public material, not the Amazon RVS shared secret or either party's private signing key. An enabled billing build must contain the real console-issued file; no substitute key is generated for Amazon. Creating an app draft/key in the console is still a publisher setup step, not evidence of approval or publication. See [Amazon SDK integration](https://developer.amazon.com/docs/appstore-sdk/integrate-appstore-sdk.html).

The following are **non-secret** Gradle properties, supplied in a local Gradle properties file or `-P` arguments:

```properties
dishnest.billing.enabled=true
dishnest.billing.environment=sandbox
dishnest.billing.url=https://YOUR-APPROVED-VERIFIER/v1/verify
dishnest.billing.publicKey=BASE64_X509_EC_PUBLIC_KEY
```

Use the public key from `.local-billing/public-key.txt` for development only. Android deliberately requires HTTPS; it does not trust arbitrary development certificates or silently use a public tunnel. A Fire device needs an explicitly configured, trusted TLS connection to the locally hosted verifier before end-to-end App Tester QA. No such endpoint is configured in the delivered APK. Production requires a separately protected signing key and a new production public key, a production RVS shared secret stored only on the service, HTTPS termination, per-client rate limits, request timeouts, monitoring and publisher-operated infrastructure. The included loopback service limits request size and rate; its proxy-facing limit is not a substitute for edge controls.

When billing is enabled, debug builds retain the Amazon receiver. The normal billing-disabled debug build excludes it to avoid loading Amazon's Android-specific bytecode in Robolectric. Release builds retain the receiver. Do not use the ordinary host UI suite as evidence that App Tester purchases work.

The app initialises Amazon licensing and permits billing only when the SDK mode matches the configured profile. Both protected IAP/DRM receivers and the specific Amazon Appstore/App Tester package queries are configured. `sandbox` uses App Tester and the RVS sandbox endpoint. `lat` expects the SDK's production mode and uses Amazon's production RVS endpoint with the publisher's real RVS secret, but accepts only Amazon-verified test/beta receipts and issues claims bound to `lat`. `production` rejects test/beta receipts. Keep testing and production verifier keys/configurations separate. LAT still requires console setup and separate permission to distribute a test; none has been performed. See [Amazon's testing modes](https://developer.amazon.com/docs/in-app-purchasing/iap-testing-overview.html).

## Entitlement lifecycle

- SDK account information is refreshed on resume and again before purchase. Account changes clear active access; callbacks for another account cannot grant access to the current account.
- Only catalog-returned purchasable SKUs can open Amazon's purchase UI. Pending parental approval, errors and an already-owned response never grant access by themselves.
- Every known receipt is reverified on account refresh and restore. Update pages are consumed with `reset=false` after the initial `reset=true` page. Receipts are persisted before acknowledgement.
- Purchase offers remain disabled until the entire restore history and every known receipt have been checked. A timeout or verification outage keeps new purchases blocked while any existing valid cached access continues. Restore-session generations prevent older callbacks from enabling a new account/session; paginated requests have bounded timeouts so a manual retry can recover.
- The client pins the service's public signing key and verifies audience, environment, account hash, receipt hash, nonce, SKU and validity window. Edited preferences or an unsigned JSON success do not grant Premium.
- Access is bounded by a known renewal/grace/cancel deadline and at most 24 hours after verification. A valid subscription without a known renewal date is checked again within one hour. Turning off autorenew does not remove paid access before the supplied cancellation date. Refunded/cancelled/expired receipts revoke access.
- Clock rollback beyond five minutes invalidates cached access. Expired cache cannot be prolonged by network errors. A current Amazon account must be confirmed before cached access is used after cold start.
- Monotonic elapsed time prevents a frozen wall clock from freezing expiry. A fresh nonce-bound server verification can recover the local clock high-water mark after an accidental future device time. A known SDK cancellation deadline further bounds cached access; a future cancellation is not treated as an immediate loss of paid access.
- Verified access is committed to private storage before `notifyFulfillment(FULFILLED)`. Failed verification is retried through restore/resume; it is not acknowledged as delivered. Acknowledgement can safely repeat after a restart.
- Recipe creation beyond 25, import and reusable templates check entitlement. Explicit demo data enables feature exploration only inside the labelled demo kitchen of billing-disabled development builds; it grants no Premium bypass when billing is enabled. Reading, editing and deleting existing recipes remain possible after expiry. Additional commercial gates and optional Premium features remain listed in the readiness report.
- The recipe-limit check and write run in one Room transaction. Concurrent saves use persisted records rather than a lagging UI snapshot; editing an existing larger book remains possible after expiry.

## Device evidence still required

Record App Tester and Live App Testing runs for success, pending approval and later approval, already-owned, declined purchase, invalid/unavailable SKU, offline verification and recovery, pagination, restart between verification and fulfilment, account switching, renewal, autorenew-off, expiry, refund and 24-hour cache expiry. Also test both subscription terms and lifetime restoration after reinstall. Local policy tests use isolated fixtures; they do not represent actual store purchases.

Sources checked 18 October 2026: [Amazon implementation guide](https://developer.amazon.com/docs/in-app-purchasing/iap-implement-iap.html), [RVS response and cancellation semantics](https://developer.amazon.com/docs/in-app-purchasing/iap-rvs-for-android-apps.html), [App Tester configuration](https://developer.amazon.com/docs/in-app-purchasing/iap-install-and-configure-app-tester.html), [IAP testing overview](https://developer.amazon.com/docs/in-app-purchasing/iap-testing-overview.html).
