# Premium verification with no paid hosting

Updated 19 October 2026. The publisher approved deployment of the reviewed verifier and protected signing/RVS credentials to the existing Workers Free account. Separate sandbox, production and LAT services are deployed. The LAT profile uses its own key and production Amazon RVS; see [AMAZON_LAT.md](AMAZON_LAT.md). No paid plan, paid binding or domain purchase was enabled. Premium remains customer-paid in scope and disabled in the APK until real purchase lifecycle QA and release checks pass.

Production: https://dishnest-receipt-verifier.lanraybanks.workers.dev/v1/verify

Sandbox: https://dishnest-receipt-verifier-sandbox.lanraybanks.workers.dev/v1/verify

Both services rejected malformed input and returned authentic signed inactive decisions for disposable invalid receipts through live Amazon RVS. The compiled Kotlin verifier accepted those decisions and rejected the wrong account. This does not test a real purchase, renewal, refund or restore. See `artifacts/billing-deployment.json` and `artifacts/billing-live-contract.json`.

## Cost boundary

The candidate is **Cloudflare Workers Free**, not Workers Paid or a time-limited credit offer. Current published limits are 100,000 incoming requests per account per UTC day and 10 ms CPU per invocation. At the daily limit Cloudflare returns Error 1027. There is no database, KV, Durable Object, queue, AI service, paid monitoring, custom-domain purchase or paid fallback in this implementation. Waiting for Amazon is separate from CPU execution; production CPU compliance still needs measurement.

This is a quota-limited free hosting option, not a promise of permanent free pricing or uninterrupted service. Before activation verify the actual account is on Workers Free, that other workers' usage leaves sufficient quota, and current provider terms still fit. Never upgrade or attach a paid service automatically. `COST_POLICY=free-only` is an activation acknowledgement: application code **cannot inspect or lock the account's subscription plan**. An account later moved to Workers Paid could incur usage charges, so this configuration alone is not a billing cap. The authenticated dashboard confirms the current plan is Free ($0), with a shared 100,000/day account quota. Existing unrelated workers were not modified.

The Android app already blocks new purchases while verification is unavailable. Previously signed access remains usable only within its existing bounded validity; outages and exhausted quotas cannot create a new entitlement or extend an expired one. Customer experience at quota exhaustion must pass live QA before launch.

GitHub remains the project, support and privacy URL host. Static GitHub pages cannot safely hold Amazon's shared secret or execute receipt verification. A future verifier needs a separate HTTPS service URL. Cloudflare supplies a workers.dev hostname, but recommends a route/custom domain for production and describes workers.dev as intended for non-business-critical personal/hobby projects. The publisher approved the workers.dev service deployment. Availability, quota recovery and commercial release suitability remain review gates; no domain purchase is authorized.

## Local implementation and repeatable check

```powershell
npm --prefix billing-server ci --ignore-scripts --no-audit --no-fund
node --test billing-server/*.test.mjs
```

Node 22+ is required. The loopback server remains dependency-free at runtime; Miniflare/workerd is a pinned development-only dependency for exercising the actual Workers runtime locally. The test generates an ephemeral P-256 key in memory, intercepts all outbound requests with isolated Amazon fixtures, and writes only source hashes and check results to `artifacts/billing-worker.json`. The local test never calls Amazon, creates a live entitlement or persists a private key. The optional `node tools/verify_billing_contract.mjs --live` command separately tests disposable invalid receipts through the deployed services; it cannot make a purchase. Local runtime tests cannot establish production CPU usage or account quota enforcement.

`billing-server/worker.mjs` reuses the existing receipt policy and DER ECDSA signing contract. It accepts only HTTPS POST `/v1/verify`, JSON bodies up to 8192 streamed bytes, and a Cloudflare-supplied client IP. It applies an isolate-local 30 requests/minute limit with at most 2048 retained client buckets. This reduces abuse but is not a global rate limit or a replacement for provider quota controls. It checks explicit activation, environment, nonempty RVS secret and P-256 private key before contacting Amazon. Redirects are not followed. Amazon outages/invalid credentials produce generic retryable errors, never grants or fabricated revocations. No request, receipt, exception, secret or Amazon URL is logged by this code.

The committed `wrangler.json` template leaves verification disabled, public workers.dev routing disabled, preview URLs disabled and observability disabled. It contains no credentials, account ID, route, deployment trigger or paid bindings. Approved deployment configurations are kept outside the repository under `%LOCALAPPDATA%/DishNest/publisher-private/.local-billing/cloudflare/`, with only workers.dev routing and verification explicitly enabled; previews and application logs remain disabled. Separate P-256 private keys and the RVS secret were uploaded as protected secret bindings. On 19 October, an ACL audit found the project drive is exFAT and cannot enforce Windows permissions. All local billing material was moved with file-hash verification to the NTFS path above. Its parent has inheritance disabled, and child access was verified as limited to the current Windows identity and SYSTEM. Earlier exFAT permission claims were not enforceable. No evidence of disclosure was observed. Private keys must be backed up securely by the publisher; never commit or paste them. `.dev.vars`, environment/key files and `.wrangler` data are excluded from Git and source packages. Avoid enabling live tail, exception logging or third-party telemetry that could capture Amazon's secret-bearing RVS URLs. Cloudflare's own network/security processing is separate and needs privacy review.

## Remaining release inputs

1. UK/US prices are approved and saved in the localized Amazon catalog drafts, with lifetime first in the app. Final catalog validation, other marketplace prices and renewal/territory terms still need review. No trial or introductory pricing is configured.
2. The authenticated existing account is Workers Free. Live negative-receipt checks pass. Representative load/CPU measurements, daily-quota exhaustion/recovery and ongoing plan checks remain required; no paid fallback is permitted.
3. Separate protected test/production keys and the real production RVS secret are provisioned. A separately reviewed `lat` profile and signing key are deployed and verified; do not point beta receipts at the production profile. Actual App Tester/LAT purchase lifecycle testing is pending.
4. The real app-specific Amazon public key is installed in `app/src/main/assets/AppstoreAuthenticationKey.pem`. Public verifier keys are recorded in `artifacts/billing-deployment.json`. Keep the default APK billing-disabled until a matching enabled profile and actual store lifecycle QA pass.
5. Cloudflare provider handling is described in the draft privacy policy. Publisher legal/privacy approval, native-language review, remaining feature/hardware checks and exact publisher-signed release testing are still required. Only the explicitly authorized private LAT app/test catalog has been submitted; no public release or website deployment occurred.

## Primary sources

- [Cloudflare pricing](https://developers.cloudflare.com/workers/platform/pricing/) and [Free daily limits and error behavior](https://developers.cloudflare.com/workers/platform/limits/#daily-requests).
- [workers.dev production guidance](https://developers.cloudflare.com/workers/configuration/routing/workers-dev/).
- [Workers Node crypto support](https://developers.cloudflare.com/workers/runtime-apis/nodejs/crypto/) and [Miniflare runtime testing](https://github.com/cloudflare/workers-sdk/blob/main/packages/miniflare/README.md).
- [Amazon server-side RVS and shared-secret requirement](https://developer.amazon.com/docs/in-app-purchasing/iap-rvs-for-android-apps.html).
