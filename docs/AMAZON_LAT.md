# DishNest private LAT — 19 October 2026

Amazon accepted **Test 1: DishNest Premium LAT 2026-10-19**. The dashboard shows **Test submitted**. Both the subscription family and lifetime entitlement now show **TEST / Approved**; neither was submitted to the public catalog. The previous subscription dashboard/detail completion mismatch no longer blocks test submission. Amazon processing and actual tester-device purchases remain pending. Do not promote a test item or the app to public release on this evidence.

The publisher confirmed tester consent/age and Amazon's export-compliance declaration at action time. The one-person **DishNest Premium LAT** group is assigned exclusively to this test. Tester email addresses are kept out of the repository and delivery evidence. An extra empty draft created during an asynchronous create request was renamed **Unused draft - do not submit** and remains unsubmitted, with no uploaded binary or test distribution.

The uploaded candidate is `build/lat-candidate/DishNest-0.1.0-LAT.apk`, SHA-256 `ad8b317d8088ee16a2999751d2a300775d5663621fbaf327d2dbf4abd19155bc`. It is a non-debuggable release signed by the existing O. Bankole publisher certificate, with billing enabled specifically for `lat`. Amazon reports DRM applied. Its Android source hashes, certificate and configuration are recorded in `artifacts/lat-candidate.json`; `tools/lat_evidence.py` independently verifies current source/APK identity, signature and verifier binding before packaging. The ordinary build outputs, normal installed app and saved app data were not changed.

The draft retains all available territories and the ten implemented language families (12 resource locales), restricted to the 12 compatible tablet models. TV and automotive devices are deselected because those interfaces have not been implemented or tested. Older incompatible tablets remain disabled by the manifest/API filter. Accelerated subscriptions are enabled: **monthly = 10 minutes, annual = 30 minutes**. Amazon does not permit changing acceleration after a LAT starts.

## Verifier and completed local checks

LAT uses `https://dishnest-receipt-verifier-lat.lanraybanks.workers.dev/v1/verify`, a separate P-256 key and the production Amazon RVS path. Amazon's RVS secret and LAT signing key are protected Cloudflare secrets; local private material stays on ACL-protected NTFS outside Git. Workers Free is retained, with no paid bindings, upgrade, domain, logs, traces or version preview URLs. The compatibility baseline is 2026-06-16 with `nodejs_compat`. The committed LAT template remains disabled and unexposed by default.

The reviewed two-module Worker source was combined for the dashboard and the complete editor text was compared with that bundle before deployment. A disabled-endpoint probe returned 503. After activation, a disposable invalid receipt produced an authentic signed inactive `lat` claim; the compiled Android verifier accepted it and rejected a different account. This is an integration check, not a successful customer purchase. The sandbox and production endpoints also passed their negative contracts.

All 14 service tests pass. Thirteen Node-to-compiled-Kotlin lifecycle/profile cases and eleven local workerd LAT scenarios cover monthly/annual access, renewed deadlines, autorenew-off paid access, grace, expiry, refund, production/test isolation, production RVS routing, throttling and recovery. These use explicit fixtures and do not close the actual LAT lifecycle gate.

## Actual tester-device checks still required

Wait until Amazon makes Test 1 available, then use its invitation on a **separate compatible test device** signed into the authorized tester account. Do not uninstall, clear, replace or attempt to update the ordinary Fire installation: it has a different debug certificate. Do not route LAT receipts through the sandbox or production-claim profiles.

1. Start with no entitlement; verify lifetime is the main offer and prices come from Amazon. Cancel checkout, test pending/declined outcomes, and confirm no grant.
2. Buy monthly in LAT, verify signed access and successful fulfillment, then restart and restore. Observe the accelerated renewal and the new signed deadline. Turn off autorenew and confirm access lasts until the supplied deadline, then expires.
3. With tester-specific entitlement reset only when explicitly authorized, repeat for annual. Test actual grace/refund scenarios that Amazon exposes; unsupported scenarios remain pending rather than simulated as actual results.
4. Test lifetime grant/restore, account isolation, temporary verifier outage and recovery. Never mark unverified content FULFILLED or extend access past a signed expiry.
5. Record sanitized observations against the Amazon-delivered build and profile, with no account/receipt identifiers. Public release additionally requires the remaining hardware, language, privacy/licensing, signing recovery and product-scope gates.

Rebuild the isolated candidate with `tools/prepare_lat_candidate.ps1`; it reuses the publisher key and directs Gradle outputs into a separate build tree. Rebuilding does not upload, submit, install or activate a service. A new APK invalidates earlier upload/device evidence.

Sources: [Amazon LAT setup](https://developer.amazon.com/docs/app-testing/live-app-testing-getting-started.html), [test-only IAP catalog](https://developer.amazon.com/docs/in-app-purchasing/iap-lat.html), [testing modes](https://developer.amazon.com/docs/in-app-purchasing/iap-testing-overview.html), [RVS dates and environments](https://developer.amazon.com/docs/in-app-purchasing/iap-rvs-for-android-apps.html).
