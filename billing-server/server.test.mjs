import test from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, verify } from 'node:crypto';
import { createReceiptServer } from './server.mjs';

test('local HTTP contract returns a signed decision, rejects invalid input and fails closed during outage',async t=>{
  const {privateKey,publicKey}=generateKeyPairSync('ec',{namedCurve:'prime256v1'});
  let outage=false;
  const server=createReceiptServer({privateKey,environment:'sandbox',secret:'test-only',fetchReceipt:async()=>{
    if(outage)throw new Error('service unavailable');
    return {receiptId:'receipt',productId:'dishnest.premium.lifetime',productType:'ENTITLED',purchaseDate:Date.now()-1000};
  }});
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  t.after(()=>new Promise(resolve=>server.close(resolve)));
  const url=`http://127.0.0.1:${server.address().port}/v1/verify`;
  const post=body=>fetch(url,{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify(body)});
  const body={userId:'user',receiptId:'receipt',nonce:'12345678-1234-1234-1234-123456789abc'};
  const response=await post(body);
  assert.equal(response.status,200);assert.equal(response.headers.get('cache-control'),'no-store');
  const signed=await response.json();
  assert(verify('sha256',Buffer.from(signed.payload,'base64'),publicKey,Buffer.from(signed.signature,'base64')));
  assert.equal(JSON.parse(Buffer.from(signed.payload,'base64')).active,true);
  assert.equal((await post({...body,nonce:'bad'})).status,400);
  assert.equal((await post({...body,userId:'x'.repeat(9000)})).status,413);
  assert.equal((await fetch(url)).status,404);
  outage=true;assert.equal((await post(body)).status,503);
});
