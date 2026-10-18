import { createServer } from 'node:http';
import { readFileSync } from 'node:fs';
import { createPrivateKey } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';
import { amazonReceipt, verifyPurchase, validateRequest } from './verification.mjs';

// Loopback only. A production reverse proxy must terminate TLS, limit requests,
// enforce timeouts and supply abuse monitoring. This command never deploys.
export function createReceiptServer(options) {
const buckets = new Map();
const server = createServer(async (req, res) => {
  res.setHeader('Content-Type', 'application/json');
  res.setHeader('Cache-Control', 'no-store');
  const reply = (status, body) => { res.writeHead(status); res.end(JSON.stringify(body)); };
  if (req.method !== 'POST' || req.url !== '/v1/verify') return reply(404, { error: 'Not found' });
  if (!req.headers['content-type']?.startsWith('application/json')) return reply(415, { error: 'JSON required' });
  const now = Date.now();
  const bucket = buckets.get(req.socket.remoteAddress) ?? { start: now, count: 0 };
  if (now - bucket.start > 60_000) { bucket.start = now; bucket.count = 0; }
  buckets.set(req.socket.remoteAddress, bucket);
  if (++bucket.count > 60) return reply(429, { error: 'Retry later' });
  try {
    let raw = '';
    for await (const chunk of req) { raw += chunk; if (Buffer.byteLength(raw) > 8192) { reply(413, { error: 'Too large' }); return; } }
    let body;
    try { body = JSON.parse(raw); validateRequest(body); } catch { return reply(400,{error:'Invalid request'}); }
    const signed = await verifyPurchase(body, options);
    reply(200, signed);
  } catch {
    reply(503, { error: 'Receipt could not be verified; retry later' });
  }
});
server.requestTimeout = 20_000;
server.headersTimeout = 10_000;
return server;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const environment = process.env.DISHNEST_RVS_ENVIRONMENT;
  if (!['sandbox', 'lat', 'production'].includes(environment)) throw new Error('Set DISHNEST_RVS_ENVIRONMENT explicitly');
  const secret = process.env.DISHNEST_AMAZON_SHARED_SECRET;
  if (!secret) throw new Error('Server-only Amazon shared secret required');
  const privateKey = createPrivateKey(readFileSync(process.env.DISHNEST_SIGNING_KEY_FILE));
  if (privateKey.asymmetricKeyType !== 'ec' || privateKey.asymmetricKeyDetails.namedCurve !== 'prime256v1')
    throw new Error('Use a P-256 EC signing key');
  createReceiptServer({secret,environment,privateKey,fetchReceipt:amazonReceipt})
    .listen(Number(process.env.PORT ?? 8787),'127.0.0.1');
}
