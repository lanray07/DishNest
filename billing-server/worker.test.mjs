import test from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, verify, createHash } from 'node:crypto';
import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { Miniflare, Log, LogLevel } from 'miniflare';

test('Workers runtime: signed Amazon decisions, bounded inputs, outage and activation controls', async t => {
  const config = JSON.parse(readFileSync(new URL('./wrangler.json', import.meta.url)));
  const { privateKey, publicKey } = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
  const bindings = { ...config.vars, VERIFICATION_ENABLED: 'true', AMAZON_RVS_SECRET: 'isolated-test-only',
    SIGNING_PRIVATE_KEY: privateKey.export({ type: 'pkcs8', format: 'pem' }) };
  let status = 200, extra = {}, calls = 0;
  const options = { modules: true, scriptPath: fileURLToPath(new URL('./worker.mjs', import.meta.url)),
    compatibilityDate: config.compatibility_date, compatibilityFlags: config.compatibility_flags,
    bindings, cf: false, log: new Log(LogLevel.NONE), outboundService: async request => {
      calls++;
      const url = new URL(request.url);
      assert.equal(url.origin, 'https://appstore-sdk.amazon.com');
      assert.equal(url.pathname, '/sandbox/version/1.0/verifyReceiptId/developer/isolated-test-only/user/user/receiptId/receipt');
      return new Response(JSON.stringify({ receiptId: 'receipt', productId: 'dishnest.premium.lifetime',
        productType: 'ENTITLED', purchaseDate: Date.now() - 1000, ...extra }), { status });
    } };
  const mf = new Miniflare(options);
  t.after(() => mf.dispose());
  let address = 1;
  const body = { userId: 'user', receiptId: 'receipt', nonce: '12345678-1234-1234-1234-123456789abc' };
  const post = (value = body, headers = {}) => mf.dispatchFetch('https://verifier.test/v1/verify', {
    method: 'POST', headers: { 'content-type': 'application/json', 'cf-connecting-ip': `192.0.2.${address++}`, ...headers },
    body: typeof value === 'string' ? value : JSON.stringify(value) });
  const response = await post();
  assert.equal(response.status, 200); assert.equal(response.headers.get('cache-control'), 'no-store');
  const signed = await response.json();
  assert(verify('sha256', Buffer.from(signed.payload, 'base64'), publicKey, Buffer.from(signed.signature, 'base64')));
  const claim = JSON.parse(Buffer.from(signed.payload, 'base64'));
  assert.equal(claim.active, true); assert.equal(claim.nonce, body.nonce);
  assert.equal(claim.userHash, createHash('sha256').update('user').digest('hex'));
  assert.equal(claim.receiptHash, createHash('sha256').update('receipt').digest('hex'));
  assert.equal(claim.environment, 'sandbox'); assert.equal(claim.audience, 'com.dishnest.app');
  assert(claim.expiresAt <= claim.verifiedAt + 86_400_000);
  extra = { cancelDate: Date.now() - 1 };
  assert.equal(JSON.parse(Buffer.from((await (await post()).json()).payload, 'base64')).active, false);
  extra = {};
  for (status of [400, 410]) {
    const denied = await (await post()).json();
    assert.equal(JSON.parse(Buffer.from(denied.payload, 'base64')).active, false);
  }
  for (status of [302, 429, 496, 500]) {
    const outage = await post(); assert.equal(outage.status, 503);
    assert.deepEqual(await outage.json(), { error: 'Verification unavailable' });
  }
  status = 200; extra = { receiptId: 'wrong' };
  assert.equal((await post()).status, 503); extra = {};
  const before = calls;
  assert.equal((await post(null)).status, 400);
  assert.equal((await post({ ...body, nonce: 'bad' })).status, 400);
  assert.equal((await post('{')).status, 400);
  assert.equal((await post(' '.repeat(8193))).status, 413);
  assert.equal((await post('é'.repeat(5000))).status, 413);
  assert.equal((await post(body, { 'content-type': 'text/plain' })).status, 415);
  assert.equal((await mf.dispatchFetch('https://verifier.test/v1/verify')).status, 404);
  assert.equal((await mf.dispatchFetch('https://verifier.test/v1/verify?user=secret', { method: 'POST' })).status, 404);
  assert.equal(calls, before);
  for (let i = 0; i < 30; i++) assert.equal((await post(body, { 'cf-connecting-ip': '198.51.100.1' })).status, 200);
  assert.equal((await post(body, { 'cf-connecting-ip': '198.51.100.1' })).status, 429);
  for (const change of [{ VERIFICATION_ENABLED: 'false' }, { COST_POLICY: 'paid' },
    { SIGNING_PRIVATE_KEY: '' }, { AMAZON_RVS_SECRET: '' }, { RVS_ENVIRONMENT: 'unknown' }]) {
    await mf.setOptions({ ...options, bindings: { ...bindings, ...change } });
    const count = calls;
    assert.equal((await post()).status, 503); assert.equal(calls, count);
  }
  assert.equal(config.workers_dev, false); assert.equal(config.preview_urls, false);
  assert.equal(config.observability.enabled, false);
  const sources = ['worker.mjs', 'verification.mjs', 'worker.test.mjs', 'wrangler.json', 'package-lock.json'];
  const sourceSha256 = Object.fromEntries(sources.map(name => [name,
    createHash('sha256').update(readFileSync(new URL(name, import.meta.url))).digest('hex')]));
  writeFileSync(new URL('../artifacts/billing-worker.json', import.meta.url), JSON.stringify({
    passed: true, runtime: 'Local workerd through Miniflare 4.20260730.0', sourceSha256,
    checks: ['DER signature and bound claim', 'refund/cancellation', 'RVS outage fails closed',
      'bounded streamed UTF-8 request bytes', 'input validation', 'isolate rate limit',
      'disabled/misconfigured endpoint makes no RVS calls', 'public routes and logs disabled by default'],
    amazon_purchase_tested: false, deployed: false, free_plan_verified: false,
    production_cpu_budget_verified: false, private_key_written: false,
  }, null, 2));
});

test('LAT Worker uses production RVS and isolates subscription lifecycle decisions', async t => {
  const config=JSON.parse(readFileSync(new URL('./wrangler.lat.json',import.meta.url)));
  assert.equal(config.vars.RVS_ENVIRONMENT,'lat');
  assert.equal(config.vars.COST_POLICY,'free-only');
  assert.equal(config.vars.VERIFICATION_ENABLED,'false');
  assert.equal(config.workers_dev,false);assert.equal(config.preview_urls,false);
  assert.equal(config.observability.enabled,false);
  for(const forbidden of ['kv_namespaces','r2_buckets','d1_databases','durable_objects','queues','ai','routes'])
    assert.equal(config[forbidden],undefined);
  const {privateKey,publicKey}=generateKeyPairSync('ec',{namedCurve:'prime256v1'});
  let receipt={},status=200,calls=0;
  const mf=new Miniflare({modules:true,scriptPath:fileURLToPath(new URL('./worker.mjs',import.meta.url)),
    compatibilityDate:config.compatibility_date,compatibilityFlags:config.compatibility_flags,
    bindings:{...config.vars,VERIFICATION_ENABLED:'true',RVS_ENVIRONMENT:'lat',
      AMAZON_RVS_SECRET:'lat-local-fixture',SIGNING_PRIVATE_KEY:privateKey.export({type:'pkcs8',format:'pem'})},
    cf:false,log:new Log(LogLevel.NONE),outboundService:async request=>{
      calls++;
      const url=new URL(request.url);
      assert.equal(url.origin,'https://appstore-sdk.amazon.com');
      assert.equal(url.pathname,'/version/1.0/verifyReceiptId/developer/lat-local-fixture/user/test-user/receiptId/test-receipt');
      return new Response(JSON.stringify(receipt),{status});
    }});
  t.after(()=>mf.dispose());
  const cases=[];
  for(const scenario of [
    {name:'annual-active',extra:{},active:true},
    {name:'monthly-active',extra:{termSku:'dishnest.premium.monthly'},active:true},
    {name:'renewed',extra:{renewalDate:Date.now()+7_200_000},active:true},
    {name:'autorenew-off-paid-period',extra:{autoRenewing:false,renewalDate:null,cancelDate:Date.now()+600_000},active:true},
    {name:'grace',extra:{renewalDate:Date.now()-1000,gracePeriodEndDate:Date.now()+600_000},active:true},
    {name:'expired',extra:{renewalDate:Date.now()-1000},active:false},
    {name:'refunded',extra:{cancelDate:Date.now()-1000},active:false},
    {name:'reject-real-customer',extra:{betaProduct:false,testTransaction:false},http:503},
    {name:'rvs-throttled',extra:{},upstream:429,http:503},
    {name:'rvs-invalidated',extra:{},upstream:410,active:false},
    {name:'rvs-recovered',extra:{},active:true},
  ]) {
    status=scenario.upstream??200;
    receipt={receiptId:'test-receipt',productId:'dishnest.premium',termSku:'dishnest.premium.annual',
      productType:'SUBSCRIPTION',purchaseDate:Date.now()-3_600_000,renewalDate:Date.now()+3_600_000,
      cancelDate:null,betaProduct:true,...scenario.extra};
    const response=await mf.dispatchFetch('https://lat-verifier.test/v1/verify',{method:'POST',
      headers:{'content-type':'application/json','cf-connecting-ip':'192.0.2.10'},
      body:JSON.stringify({userId:'test-user',receiptId:'test-receipt',nonce:'12345678-1234-1234-1234-123456789abc'})});
    assert.equal(response.status,scenario.http??200,scenario.name);
    const result=await response.json();
    if(!scenario.http) {
      assert(verify('sha256',Buffer.from(result.payload,'base64'),publicKey,Buffer.from(result.signature,'base64')));
      const claim=JSON.parse(Buffer.from(result.payload,'base64'));
      assert.equal(claim.environment,'lat');assert.equal(claim.active,scenario.active,scenario.name);
      assert(claim.expiresAt>claim.verifiedAt && claim.expiresAt<=claim.verifiedAt+86_400_000);
    } else assert.deepEqual(result,{error:'Verification unavailable'});
    cases.push({case:scenario.name,passed:true});
  }
  assert.equal(calls,cases.length);
  writeFileSync(new URL('../artifacts/billing-lat-worker.json',import.meta.url),JSON.stringify({passed:true,
    runtime:'Local workerd through Miniflare',checks:cases,productionRvsPathVerified:true,
    fixtureOnly:true,latTestExecuted:false,deployed:false,privateKeyWritten:false,
    sourceSha256:Object.fromEntries(['worker.mjs','verification.mjs','worker.test.mjs','wrangler.lat.json','package-lock.json'].map(name=>
      [name,createHash('sha256').update(readFileSync(new URL(name,import.meta.url))).digest('hex')]))},null,2));
});
