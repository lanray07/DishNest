import { generateKeyPairSync } from 'node:crypto';
import { mkdirSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

// Development keys only, isolated from source and delivery archives. Never overwrite.
const directory = resolve(import.meta.dirname, '../.local-billing');
mkdirSync(directory, { recursive: true });
const { publicKey, privateKey } = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
writeFileSync(resolve(directory, 'signing.pem'), privateKey.export({type:'pkcs8',format:'pem'}), {flag:'wx',mode:0o600});
writeFileSync(resolve(directory, 'public-key.txt'), publicKey.export({type:'spki',format:'der'}).toString('base64'), {flag:'wx'});
const env = `DISHNEST_RVS_ENVIRONMENT=sandbox\nDISHNEST_AMAZON_SHARED_SECRET=dishnest-sandbox-only\nDISHNEST_SIGNING_KEY_FILE="${resolve(directory,'signing.pem').replaceAll('\\','/')}"\nPORT=8787\n`;
writeFileSync(resolve(directory,'.env'),env,{flag:'wx',mode:0o600});
console.log('Local sandbox key and configuration created in .local-billing. Nothing was deployed.');
