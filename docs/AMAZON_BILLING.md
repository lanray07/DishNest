# Amazon billing: local implementation and activation

The default APK has purchases disabled. Approved UK/US prices and seven localized IAP descriptions have been saved in Amazon catalog drafts. With explicit publisher approval, separate sandbox and production receipt verifiers have been deployed to the existing Cloudflare Workers Free account. No app or website was deployed and no app or IAP was submitted.

The publisher wants customer-paid Premium without ongoing paid processing/hosting. Separate Cloudflare Workers Free verifiers are deployed and tested with signed inactive live-RVS decisions and the compiled Kotlin verifier; see [free verifier setup and limits](AMAZON_FREE_VERIFIER.md). The Free plan and public endpoints are verified; representative production CPU/load and real purchase lifecycle QA remain pending. Do not activate paid infrastructure or treat local fixtures as live Amazon purchase evidence.

## Catalog contract

| SKU | Type | Role |
| --- | --- | --- |
| `dishnest.premium` | Subscription | Parent; receipts may report this SKU |
| `dishnest.premium.monthly` | Subscription term | Purchasable monthly term |
| `dishnest.premium.annual` | Subscription term | Purchasable annual term |
| `dishnest.premium.lifetime` | Entitlement | Main one-time Premium offer |

The publisher approved UK monthly £1.49, annual £9.99 and lifetime £19.99; US monthly $1.99, annual $12.99 and lifetime $24.99 on 19 October 2026. Lifetime is the first and primary offer; annual and monthly remain alternatives. See `marketing/amazon-premium-pricing.json`. Other marketplaces use Amazon-calculated prices pending final market review. `billing-server/amazon.sdktester.json` uses the approved US amounts as visibly labelled isolated test fixtures only. They are never bundled as app prices. The app displays title and price from `getProductData`; Amazon's confirmation screen supplies the final terms. No free trial or recipe-template pack is advertised or implemented. The RVS sandbox may return a synthesized parent `_term` SKU, so verified subscription-family receipts are supported without guessing their duration from the SKU.

## Local service

Requires Node 22 or newer; the loopback server has no runtime npm dependencies. The Workers test harness needs the pinned development dependencies. Run from the project root:

```powershell
npm --prefix billing-server ci --ignore-scripts --no-audit --no-fund
node --test billing-server/*.test.mjs
node billing-server/prepare-local.mjs
node --env-file=.local-billing/.env billing-server/server.mjs
```

Preparation generates an isolated P-256 development signing key and refuses to overwrite it. `.local-billing/`, private keys and environment files are excluded from Git and delivery archives. The service listens only on `127.0.0.1:8787`; stop it with Ctrl+C. It does not expose a public endpoint. The sandbox shared-secret value is a non-production placeholder; Amazon permits any non-empty value in its RVS cloud sandbox. The separate deployed production verifier now has protected service keys and the real RVS secret; these are never used by the loopback development server.

`POST /v1/verify` accepts `{userId, receiptId, nonce}`. Those identifiers are bearer purchase evidence: do not log them or put them in URLs to this service. It makes a server-to-server HTTPS request to Amazon with redirects disabled, validates the receipt identity, product family, type, dates and environment, then returns `{payload, signature}`. The payload is base64 UTF-8 JSON and the signature is DER ECDSA/SHA-256 over the decoded payload. There is no verification-success simulation route.

HTTP 400/410 from Amazon creates a signed inactive decision; throttling, invalid credentials, network failures and server failures create a retryable error, never a grant or fabricated revocation. Receipt data is processed in memory and is not persisted by this service. The Android app retains known receipts privately so it can reverify them even if Amazon's update page is empty.

## Android configuration

Amazon requires its app-specific **Appstore authentication public key**. The real file has now been downloaded from the DishNest Developer Console and installed as `app/src/main/assets/AppstoreAuthenticationKey.pem`; its public-material fingerprint is recorded in `artifacts/amazon-authentication-key.json`. This is distinct from the receipt-service EC public key below. It is public material, not the Amazon RVS shared secret or either party's private signing key. An enabled billing build must contain the real console-issued file; no substitute key is generated for Amazon. Creating an app draft/key in the console is still a publisher setup step, not evidence of approval or publication. See [Amazon SDK integration](https://developer.amazon.com/docs/appstore-sdk/integrate-appstore-sdk.html).

The following are **non-secret** Gradle properties, supplied in a local Gradle properties file or `-P` arguments:

```properties
dishnest.billing.enabled=true
dishnest.billing.environment=sandbox
dishnest.billing.url=https://YOUR-APPROVED-VERIFIER/v1/verify
dishnest.billing.publicKey=BASE64_X509_EC_PUBLIC_KEY
```

Use the public key from `.local-billing/public-key.txt` for development only. Android deliberately requires HTTPS; it does not trust arbitrary development certificates or silently use a public tunnel. A Fire device needs an explicitly configured, trusted TLS connection to the locally hosted verifier before end-to-end App Tester QA. The delivered APK remains billing-disabled and is not wired to either live endpoint. The separate production signing key, public verification key, RVS secret and TLS service are provisioned on Workers Free; real purchase/lifecycle QA and operational review remain required before enabling the app. The included loopback service limits request size and rate; its proxy-facing limit is not a substitute for edge controls.

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
