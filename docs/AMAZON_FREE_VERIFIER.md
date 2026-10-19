# Premium verification with no paid hosting

Prepared locally on 19 October 2026. No account, paid plan, public service or Amazon product was created. Premium remains customer-paid in scope and disabled in the APK until live verification passes.

## Cost boundary

The candidate is **Cloudflare Workers Free**, not Workers Paid or a time-limited credit offer. Current published limits are 100,000 incoming requests per account per UTC day and 10 ms CPU per invocation. At the daily limit Cloudflare returns Error 1027. There is no database, KV, Durable Object, queue, AI service, paid monitoring, custom-domain purchase or paid fallback in this implementation. Waiting for Amazon is separate from CPU execution; production CPU compliance still needs measurement.

This is a quota-limited free hosting option, not a promise of permanent free pricing or uninterrupted service. Before activation verify the actual account is on Workers Free, that other workers' usage leaves sufficient quota, and current provider terms still fit. Never upgrade or attach a paid service automatically. `COST_POLICY=free-only` is an activation acknowledgement: application code **cannot inspect or lock the account's subscription plan**. An account later moved to Workers Paid could incur usage charges, so this configuration alone is not a billing cap. No such account has been connected or verified.

The Android app already blocks new purchases while verification is unavailable. Previously signed access remains usable only within its existing bounded validity; outages and exhausted quotas cannot create a new entitlement or extend an expired one. Customer experience at quota exhaustion must pass live QA before launch.

GitHub remains the project, support and privacy URL host. Static GitHub pages cannot safely hold Amazon's shared secret or execute receipt verification. A future verifier needs a separate HTTPS service URL. Cloudflare supplies a workers.dev hostname, but recommends a route/custom domain for production and describes workers.dev as intended for non-business-critical personal/hobby projects. Suitability for a commercial entitlement service and any existing no-cost domain must therefore be resolved before activation; no domain purchase is authorized.

## Local implementation and repeatable check

```powershell
npm --prefix billing-server ci --ignore-scripts --no-audit --no-fund
node --test billing-server/*.test.mjs
```

Node 22+ is required. The loopback server remains dependency-free at runtime; Miniflare/workerd is a pinned development-only dependency for exercising the actual Workers runtime locally. The test generates an ephemeral P-256 key in memory, intercepts all outbound requests with isolated Amazon fixtures, and writes only source hashes and check results to `artifacts/billing-worker.json`. It never calls Amazon, creates a live entitlement or persists a private key. Local runtime tests cannot establish production CPU usage or account quota enforcement.

`billing-server/worker.mjs` reuses the existing receipt policy and DER ECDSA signing contract. It accepts only HTTPS POST `/v1/verify`, JSON bodies up to 8192 streamed bytes, and a Cloudflare-supplied client IP. It applies an isolate-local 30 requests/minute limit with at most 2048 retained client buckets. This reduces abuse but is not a global rate limit or a replacement for provider quota controls. It checks explicit activation, environment, nonempty RVS secret and P-256 private key before contacting Amazon. Redirects are not followed. Amazon outages/invalid credentials produce generic retryable errors, never grants or fabricated revocations. No request, receipt, exception, secret or Amazon URL is logged by this code.

`wrangler.json` leaves verification disabled, public workers.dev routing disabled, preview URLs disabled and observability disabled. It contains no credentials, account ID, route, deployment trigger or paid bindings. `.dev.vars`, environment/key files and `.wrangler` data are excluded from Git and source packages. Avoid enabling live tail, exception logging or third-party telemetry that could capture Amazon's secret-bearing RVS URLs. Cloudflare's own network/security processing is separate and needs privacy review.

## Inputs still needed before activation

1. Publisher-approved monthly/annual/lifetime offers and prices, tax/territory/renewal terms and actual Amazon catalog. The SDK test prices remain fixtures only.
2. A publisher-controlled Workers Free account, verified current terms and quota, an approved HTTPS address, and explicit permission for the concrete service deployment. This document does not deploy anything.
3. Separate protected P-256 signing keys for test and production and the real RVS shared secret in Worker secret bindings `SIGNING_PRIVATE_KEY` and `AMAZON_RVS_SECRET`. Never use the generated loopback development key for production. Configure non-secret `RVS_ENVIRONMENT` (`sandbox`, `lat`, `production`) separately and enable `VERIFICATION_ENABLED` only for the reviewed setup. Do not put secrets in Wrangler variables, Git, APKs, chat or command-line arguments.
4. The real Amazon app-specific authentication public key, the verifier's public key pinned in the matching Android profile, and the approved HTTPS verification URL. Keep test and production profiles separate.
5. Updated and approved provider/privacy disclosures, live resource and abuse-control measurements within Free limits, App Tester/LAT purchase lifecycle and quota/outage recovery evidence, and publisher-signed release hardware QA. No production gate is cleared by local fixtures.

## Primary sources

- [Cloudflare pricing](https://developers.cloudflare.com/workers/platform/pricing/) and [Free daily limits and error behavior](https://developers.cloudflare.com/workers/platform/limits/#daily-requests).
- [workers.dev production guidance](https://developers.cloudflare.com/workers/configuration/routing/workers-dev/).
- [Workers Node crypto support](https://developers.cloudflare.com/workers/runtime-apis/nodejs/crypto/) and [Miniflare runtime testing](https://github.com/cloudflare/workers-sdk/blob/main/packages/miniflare/README.md).
- [Amazon server-side RVS and shared-secret requirement](https://developer.amazon.com/docs/in-app-purchasing/iap-rvs-for-android-apps.html).
