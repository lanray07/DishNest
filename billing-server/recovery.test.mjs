import test from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, verify, createHash } from 'node:crypto';
import { readFileSync, writeFileSync } from 'node:fs';
import { createWorker } from './worker.mjs';

// Deterministic overload fixtures: no external requests, real receipts or paid resources.
test('isolate throttling and RVS failures recover without signing an outage as a denial', async t => {
  const start = 1_800_000_000_000;
  t.mock.timers.enable({ apis: ['Date'], now: start });
  const { privateKey, publicKey } = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
  const env = { VERIFICATION_ENABLED: 'true', COST_POLICY: 'free-only', RVS_ENVIRONMENT: 'lat',
    AMAZON_RVS_SECRET: 'local-fixture-only',
    SIGNING_PRIVATE_KEY: privateKey.export({ type: 'pkcs8', format: 'pem' }) };
  let calls = 0, failure = false;
  const worker = createWorker({ fetchReceipt: async () => {
    calls++;
    if (failure) throw new Error('local upstream timeout/quota fixture');
    return { receiptId: 'receipt', productId: 'dishnest.premium.annual', productType: 'SUBSCRIPTION',
      purchaseDate: start - 1000, renewalDate: start + 3_600_000, betaProduct: true };
  } });
  const post = ip => worker.fetch(new Request('https://fixture.test/v1/verify', { method: 'POST',
    headers: { 'content-type': 'application/json', 'cf-connecting-ip': ip },
    body: JSON.stringify({ userId: 'user', receiptId: 'receipt', nonce: '12345678-1234-1234-1234-123456789abc' }) }), env);
  async function granted(response) {
    assert.equal(response.status, 200);
    assert.equal(response.headers.get('cache-control'), 'no-store');
    const signed = await response.json();
    assert(verify('sha256', Buffer.from(signed.payload, 'base64'), publicKey, Buffer.from(signed.signature, 'base64')));
    const claim = JSON.parse(Buffer.from(signed.payload, 'base64'));
    assert.equal(claim.active, true); assert.equal(claim.environment, 'lat');
    assert.equal(claim.verifiedAt, Date.now());
    assert.equal(claim.userHash, createHash('sha256').update('user').digest('hex'));
  }
  async function unavailable(response, status) {
    assert.equal(response.status, status);
    assert.equal(response.headers.get('cache-control'), 'no-store');
    assert.deepEqual(await response.json(), { error: status === 429 ? 'Retry later' : 'Verification unavailable' });
  }
  for (let i = 0; i < 30; i++) await granted(await post('192.0.2.1'));
  const beforeLimit = calls;
  await unavailable(await post('192.0.2.1'), 429);
  t.mock.timers.setTime(start + 59_999);
  await unavailable(await post('192.0.2.1'), 429);
  assert.equal(calls, beforeLimit, 'throttled requests must not consume RVS calls');
  t.mock.timers.setTime(start + 60_000);
  await granted(await post('192.0.2.1'));
  failure = true;
  await unavailable(await post('192.0.2.2'), 503);
  failure = false;
  await granted(await post('192.0.2.2'));
  // Fill the bounded isolate map, then prove expiry frees capacity for a new client.
  for (let i = 0; i < 2046; i++) await granted(await post(`fixture-client-${i}`));
  const beforeCapacity = calls;
  await unavailable(await post('new-fixture-client'), 429);
  assert.equal(calls, beforeCapacity);
  t.mock.timers.setTime(start + 120_000);
  await granted(await post('new-fixture-client'));
  assert.equal(calls, 2080);
  writeFileSync(new URL('../artifacts/billing-recovery-fixtures.json', import.meta.url), JSON.stringify({
    passed: true, fixtureOnly: true, externalRequests: 0, paidServicesEnabled: false,
    checks: ['30-request isolate limit', 'no RVS call while throttled', 'exact 60-second recovery',
      'upstream failure has no signed entitlement', 'same-client recovery after upstream failure',
      '2048-client capacity bound', 'capacity reclaimed after expiry', 'recovered decisions have valid signatures'],
    simulatedRvsCalls: calls, productionCpuBudgetVerified: false, dailyFreeQuotaRecoveryVerified: false,
    deviceEntitlementRecoveryVerified: false, deployed: false,
    sourceSha256: Object.fromEntries(['worker.mjs', 'verification.mjs', 'recovery.test.mjs', 'package-lock.json'].map(name =>
      [name, createHash('sha256').update(readFileSync(new URL(name, import.meta.url))).digest('hex')]))
  }, null, 2) + '\n');
});
