// Cross-runtime contract check: actual Node signer -> compiled Kotlin verifier.
// Default is offline. --live checks only disposable invalid receipts through approved verifiers.
// Neither mode changes app entitlements or makes a purchase.
import { generateKeyPairSync, randomUUID, createHash } from 'node:crypto';
import { mkdtempSync, writeFileSync, readFileSync, readdirSync, existsSync } from 'node:fs';
import { resolve, join, delimiter } from 'node:path';
import { homedir, tmpdir } from 'node:os';
import { spawnSync } from 'node:child_process';
import { verifyPurchase } from '../billing-server/verification.mjs';

const root = resolve(import.meta.dirname, '..');
const cache = join(process.env.GRADLE_USER_HOME ?? join(homedir(),'.gradle'),'caches/modules-2/files-2.1');
const modules = JSON.parse(readFileSync(join(root,'artifacts/runtime-components.json'),'utf8'));
function jar(group,name) {
  const component=modules.find(c=>c.group===group && c.name===name);
  if(!component)throw new Error('Missing resolved component: '+name);
  const directory=join(cache,group,name,component.version);
  for(const hash of readdirSync(directory)) {
    const path=join(directory,hash,component.file);
    if(existsSync(path))return path;
  }
  throw new Error('Resolved JAR not cached: '+name);
}
const classes=join(root,'app/build/tmp/kotlin-classes/debug');
if(!existsSync(join(classes,'com/dishnest/app/platform/EntitlementVerifier.class')))throw new Error('Compile Android Kotlin first');
const {privateKey,publicKey}=generateKeyPairSync('ec',{namedCurve:'prime256v1'});
const body={userId:'contract-user',receiptId:'contract-receipt',nonce:'12345678-1234-1234-1234-123456789abc'};
const now=1_800_000_000_000;
const signed=await verifyPurchase(body,{privateKey,environment:'sandbox',secret:'test-only',now,
  fetchReceipt:async()=>({receiptId:body.receiptId,productId:'dishnest.premium.lifetime',productType:'ENTITLED',purchaseDate:now-1000})});
const directory=mkdtempSync(join(tmpdir(),'dishnest-billing-contract-'));
writeFileSync(join(directory,'fixture.json'),JSON.stringify({...body,now,...signed,environment:'sandbox',active:true,sku:'dishnest.premium.lifetime',publicKey:publicKey.export({type:'spki',format:'der'}).toString('base64')}));
writeFileSync(join(directory,'VerifyContract.java'),`
import java.nio.file.*;
import com.google.gson.*;
import com.dishnest.app.platform.*;
public class VerifyContract {
  public static void main(String[] args) throws Exception {
    JsonObject f=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
    EntitlementVerifier v=new EntitlementVerifier(f.get("publicKey").getAsString(),f.get("environment").getAsString());
    SignedReceipt s=new SignedReceipt(f.get("payload").getAsString(),f.get("signature").getAsString());
    VerifiedEntitlement result=v.verify(s,f.get("userId").getAsString(),f.get("receiptId").getAsString(),f.get("now").getAsLong(),f.get("nonce").getAsString());
    if(result.getActive()!=f.get("active").getAsBoolean() || !result.getSku().equals(f.get("sku").getAsString()))throw new AssertionError("Contract mismatch");
    boolean rejected=false;
    try { v.verify(s,"different-account",f.get("receiptId").getAsString(),f.get("now").getAsLong(),f.get("nonce").getAsString()); }
    catch(IllegalArgumentException expected) { rejected=true; }
    if(!rejected)throw new AssertionError("Wrong account accepted");
  }
}`);
const classpath=[classes,jar('org.jetbrains.kotlin','kotlin-stdlib'),jar('com.google.code.gson','gson')].join(delimiter);
const binary=name=>process.env.JAVA_HOME ? join(process.env.JAVA_HOME,'bin',name+(process.platform==='win32'?'.exe':'')) : name;
for(const [command,args] of [[binary('javac'),['-cp',classpath,join(directory,'VerifyContract.java')]],
  [binary('java'),['-cp',[directory,classpath].join(delimiter),'VerifyContract',join(directory,'fixture.json')]]]) {
  const result=spawnSync(command,args,{encoding:'utf8',windowsHide:true});
  if(result.status!==0)throw new Error(result.stderr || result.error?.message || 'Contract check failed');
}
// Exercise subscription decisions across the real signer and compiled Android
// verifier, including LAT isolation. These are explicit local fixtures, not store QA.
const lifecycle=[];
const subscription={receiptId:body.receiptId,productId:'dishnest.premium',termSku:'dishnest.premium.annual',
  productType:'SUBSCRIPTION',purchaseDate:now-3_600_000,renewalDate:now+3_600_000,cancelDate:null};
for(const scenario of [
  {name:'annual-active',changes:{},active:true,expiresAt:now+3_600_000},
  {name:'monthly-active',changes:{termSku:'dishnest.premium.monthly'},active:true,expiresAt:now+3_600_000},
  {name:'renewal-rechecked',changes:{renewalDate:now+7_200_000},active:true,expiresAt:now+7_200_000},
  {name:'autorenew-off-paid-period',changes:{autoRenewing:false,renewalDate:null,cancelDate:now+600_000},active:true,expiresAt:now+600_000},
  {name:'grace-period',changes:{renewalDate:now-1,gracePeriodEndDate:now+600_000},active:true,expiresAt:now+600_000},
  {name:'grace-ended',changes:{renewalDate:now-1,gracePeriodEndDate:now-1},active:false},
  {name:'expired',changes:{renewalDate:now-1},active:false},
  {name:'refund-overrides-future-renewal',changes:{cancelDate:now-1},active:false},
  {name:'lat-beta-active',environment:'lat',changes:{betaProduct:true},active:true,expiresAt:now+3_600_000},
  {name:'lat-publishing-test-active',environment:'lat',changes:{testTransaction:true},active:true,expiresAt:now+3_600_000},
  {name:'production-rejects-beta',environment:'production',changes:{betaProduct:true},rejected:true},
  {name:'production-rejects-test',environment:'production',changes:{testTransaction:true},rejected:true},
  {name:'lat-rejects-customer-charge',environment:'lat',changes:{testTransaction:false,betaProduct:false},rejected:true},
]) {
  const environment=scenario.environment??'production';
  let decision;
  try {
    decision=await verifyPurchase(body,{privateKey,environment,secret:'local-fixture-only',now,
      fetchReceipt:async()=>({...subscription,...scenario.changes})});
  } catch(error) {
    if(!scenario.rejected)throw error;
    lifecycle.push({case:scenario.name,environment,rejected:true,passed:true});continue;
  }
  if(scenario.rejected)throw new Error('Forbidden profile accepted: '+scenario.name);
  const claim=JSON.parse(Buffer.from(decision.payload,'base64'));
  if(claim.active!==scenario.active || scenario.expiresAt!==undefined && claim.expiresAt!==scenario.expiresAt)
    throw new Error('Lifecycle decision mismatch: '+scenario.name);
  writeFileSync(join(directory,'fixture.json'),JSON.stringify({...body,...decision,environment,now,
    active:scenario.active,sku:scenario.changes.termSku??subscription.termSku,
    publicKey:publicKey.export({type:'spki',format:'der'}).toString('base64')}));
  const checked=spawnSync(binary('java'),['-cp',[directory,classpath].join(delimiter),'VerifyContract',join(directory,'fixture.json')],{encoding:'utf8',windowsHide:true});
  if(checked.status!==0)throw new Error(checked.stderr||'Lifecycle Kotlin rejection: '+scenario.name);
  lifecycle.push({case:scenario.name,environment,active:scenario.active,passed:true});
}
writeFileSync(join(root,'artifacts/billing-lifecycle-contract.json'),JSON.stringify({passed:true,
  checks:lifecycle,fixtureOnly:true,amazonPurchaseTested:false,latTestExecuted:false,privateKeyWritten:false,
  sourceSha256:Object.fromEntries(['tools/verify_billing_contract.mjs','billing-server/verification.mjs',
    'app/build/tmp/kotlin-classes/debug/com/dishnest/app/platform/EntitlementVerifier.class'].map(p=>[p,createHash('sha256').update(readFileSync(join(root,p))).digest('hex')]))},null,2));
console.log(JSON.stringify({lifecycleContractCases:lifecycle.length,passed:true,fixtureOnly:true,latTestExecuted:false}));
const report={passed:true,signer:'Actual Node receipt-service signing function',verifier:'Compiled Kotlin EntitlementVerifier',
  checks:['ECDSA DER signature','SPKI public key','Base64 UTF-8 payload','account/receipt/nonce binding','wrong-account rejection'],
  amazon_purchase_tested:false,private_key_written:false};
writeFileSync(join(root,'artifacts/billing-contract.json'),JSON.stringify(report,null,2));
console.log(JSON.stringify(report));

if(process.argv.includes('--live')) {
  const deployment=JSON.parse(readFileSync(join(root,'artifacts/billing-deployment.json'),'utf8'));
  if(!deployment.deployment_authorized || !deployment.free_plan_ui_verified)throw new Error('Approved Free deployment evidence required');
  const results=[];
  const profiles=[...deployment.checks];
  const latPath=join(root,'artifacts/billing-lat-deployment.json');
  if(existsSync(latPath)) {
    const lat=JSON.parse(readFileSync(latPath,'utf8'));
    if(lat.deployed===true) {
      if(!lat.deploymentAuthorized||!lat.freePlanVerified||lat.profile!=='lat')throw new Error('Approved Free LAT deployment required');
      profiles.push(lat);
    }
  }
  for(const service of profiles) {
    const expected='https://dishnest-receipt-verifier'+(service.profile==='sandbox'?'-sandbox':service.profile==='lat'?'-lat':'')+'.lanraybanks.workers.dev/v1/verify';
    if(!['sandbox','production','lat'].includes(service.profile)||service.endpoint!==expected)throw new Error('Unexpected verifier destination');
    const request={userId:'dishnest-disposable-invalid-account',receiptId:'dishnest-disposable-invalid-receipt',nonce:randomUUID()};
    const response=await fetch(service.endpoint,{method:'POST',headers:{'content-type':'application/json'},
      body:JSON.stringify(request),redirect:'error',signal:AbortSignal.timeout(20000)});
    if(response.status!==200)throw new Error('Live inactive contract unavailable; purchase QA remains pending');
    const decision=await response.json();
    writeFileSync(join(directory,'fixture.json'),JSON.stringify({...request,...decision,now:Date.now(),
      publicKey:service.public_key,environment:service.profile,active:false,sku:''}));
    const checked=spawnSync(binary('java'),['-cp',[directory,classpath].join(delimiter),'VerifyContract',join(directory,'fixture.json')],{encoding:'utf8',windowsHide:true});
    if(checked.status!==0)throw new Error(checked.stderr||'Live Kotlin verification failed');
    results.push({profile:service.profile,endpoint:service.endpoint,signed_inactive:true,wrong_account_rejected:true});
  }
  const liveReport={passed:true,checked_on:'2026-10-19',signer:'Deployed Cloudflare Worker with live Amazon RVS',
    verifier:'Compiled Kotlin EntitlementVerifier',checks:results,amazon_real_purchase_tested:false,private_key_written:false,
    sourceSha256:Object.fromEntries(['tools/verify_billing_contract.mjs','billing-server/worker.mjs','billing-server/verification.mjs',
      'app/build/tmp/kotlin-classes/debug/com/dishnest/app/platform/EntitlementVerifier.class'].map(p=>[p,createHash('sha256').update(readFileSync(join(root,p))).digest('hex')]))};
  writeFileSync(join(root,'artifacts/billing-live-contract.json'),JSON.stringify(liveReport,null,2));
  console.log(JSON.stringify(liveReport));
}
