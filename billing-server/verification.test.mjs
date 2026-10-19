import test from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, verify } from 'node:crypto';
import { entitlement, verifyPurchase, HOUR, hash, validateRequest } from './verification.mjs';

const now = 1_800_000_000_000;
const receipt = (extra = {}) => ({ receiptId: 'receipt', productId: 'dishnest.premium.monthly',
  productType: 'SUBSCRIPTION', purchaseDate: now - HOUR, renewalDate: now + 10 * HOUR,
  cancelDate: null, testTransaction: false, ...extra });

test('LAT test receipts stay isolated from production grants', () => {
  const beta = receipt({ betaProduct:true });
  assert.equal(entitlement(beta,'receipt',now,'lat').active,true);
  assert.throws(()=>entitlement(beta,'receipt',now,'production'));
  assert.throws(()=>entitlement(receipt(),'receipt',now,'lat'));
});

test('active renewal and offline validity are bounded', () => {
  assert.deepEqual(entitlement(receipt(), 'receipt', now, 'production'), {
    sku: 'dishnest.premium.monthly', active: true, expiresAt: now + 10 * HOUR });
  assert.equal(entitlement(receipt({ renewalDate: now + 100 * HOUR }), 'receipt', now, 'production').expiresAt, now + 24 * HOUR);
});
test('turning autorenew off preserves access until future cancel date', () => {
  const result = entitlement(receipt({ autoRenewing: false, renewalDate: null, cancelDate: now + HOUR / 2 }), 'receipt', now, 'production');
  assert.equal(result.active, true); assert.equal(result.expiresAt, now + HOUR / 2);
});
test('expired, refunded and cancelled receipts revoke access', () => {
  for (const extra of [{cancelDate:now}, {cancelDate:now-1}, {renewalDate:now-1}])
    assert.equal(entitlement(receipt(extra), 'receipt', now, 'production').active, false);
});
test('Amazon grace period extends access only to the supplied deadline', () => {
  assert.equal(entitlement(receipt({ renewalDate:now-1, gracePeriodEndDate:now+HOUR }), 'receipt', now, 'production').expiresAt, now+HOUR);
});
test('lifetime is reverified daily and is revocable', () => {
  const value = receipt({ productId:'dishnest.premium.lifetime',productType:'ENTITLED',renewalDate:null });
  assert.equal(entitlement(value,'receipt',now,'production').expiresAt,now+24*HOUR);
  assert.equal(entitlement({...value,cancelDate:now},'receipt',now,'production').active,false);
});
test('unknown SKUs, consumables, wrong receipt and test purchases fail closed', () => {
  for (const extra of [{productId:'another.app'}, {productType:'CONSUMABLE'}, {receiptId:'other'},
    {testTransaction:true}, {betaProduct:true}, {purchaseDate:now+HOUR}, {cancelDate:'bad'}])
    assert.throws(() => entitlement(receipt(extra),'receipt',now,'production'));
});
test('subscription parent SKU maps to allowed term SKU', () => {
  assert.equal(entitlement(receipt({productId:'dishnest.premium',termSku:'dishnest.premium.annual'}),'receipt',now,'production').sku,'dishnest.premium.annual');
});
test('nonce, receipt and account are bound into an authentic signature', async () => {
  const {privateKey,publicKey} = generateKeyPairSync('ec',{namedCurve:'prime256v1'});
  const body = {userId:'account',receiptId:'receipt',nonce:'12345678-1234-1234-1234-123456789abc'};
  const options = {privateKey, environment:'sandbox', secret:'test-only', now,
    fetchReceipt:async (user,id) => { assert.equal(user,'account');assert.equal(id,'receipt');return receipt(); }};
  const result = await verifyPurchase(body,options);
  assert(verify('sha256',Buffer.from(result.payload,'base64'),publicKey,Buffer.from(result.signature,'base64')));
  const claim = JSON.parse(Buffer.from(result.payload,'base64'));
  assert.equal(claim.userHash,hash('account'));assert.equal(claim.nonce,body.nonce);
  const revoked=await verifyPurchase(body,{...options,fetchReceipt:async()=>null});
  assert.equal(JSON.parse(Buffer.from(revoked.payload,'base64')).active,false);
});
test('outages never become a signed denial or a fabricated success', async () => {
  await assert.rejects(verifyPurchase({userId:'u',receiptId:'r',nonce:'12345678-1234-1234-1234-123456789abc'},
    {fetchReceipt:async()=>{throw new Error('offline');}}));
  assert.throws(()=>validateRequest({userId:'',receiptId:'r',nonce:'bad'}));
});

test('a subscription expiring during Amazon verification cannot produce a stale active claim', async () => {
  const {privateKey,publicKey}=generateKeyPairSync('ec',{namedCurve:'prime256v1'});
  let responded=false;
  const signed=await verifyPurchase({userId:'account',receiptId:'receipt',nonce:'12345678-1234-1234-1234-123456789abc'}, {
    privateKey,environment:'sandbox',secret:'test-only',
    fetchReceipt:async()=>{responded=true;return receipt({renewalDate:now+100});},
    clock:()=>{assert(responded);return now+200;},
  });
  assert(verify('sha256',Buffer.from(signed.payload,'base64'),publicKey,Buffer.from(signed.signature,'base64')));
  const claim=JSON.parse(Buffer.from(signed.payload,'base64'));
  assert.equal(claim.verifiedAt,now+200);
  assert.equal(claim.active,false);
  assert.equal(claim.expiresAt,now+200+HOUR);
});
