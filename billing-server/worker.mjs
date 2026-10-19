import { createPrivateKey } from 'node:crypto';
import { amazonReceipt, validateRequest, verifyPurchase } from './verification.mjs';

const reply = (status, error) => Response.json({ error }, { status,
  headers: { 'cache-control': 'no-store', 'x-content-type-options': 'nosniff' } });

// Isolate-local abuse reduction only, never a global quota or billing control.
export function createWorker({ fetchReceipt = amazonReceipt } = {}) {
  const clients = new Map();
  return {
    async fetch(request, env) {
      const url = new URL(request.url);
      if (url.pathname !== '/v1/verify' || url.search || request.method !== 'POST')
        return reply(404, 'Not found');
      if (url.protocol !== 'https:') return reply(400, 'HTTPS required');
      if (env.VERIFICATION_ENABLED !== 'true' || env.COST_POLICY !== 'free-only')
        return reply(503, 'Verification unavailable');
      if (!/^application\/json(?:\s*;|$)/i.test(request.headers.get('content-type') ?? ''))
        return reply(415, 'JSON required');
      // CF supplies this header at the edge. Do not trust arbitrary forwarding headers.
      const client = request.headers.get('cf-connecting-ip');
      if (!client) return reply(503, 'Verification unavailable');
      const now = Date.now();
      for (const [key, value] of clients) if (now - value.start >= 60_000) clients.delete(key);
      if (!clients.has(client)) {
        if (clients.size >= 2048) return reply(429, 'Retry later');
        clients.set(client, { start: now, count: 0 });
      }
      if (++clients.get(client).count > 30) return reply(429, 'Retry later');
      let body;
      try {
        const declared = request.headers.get('content-length');
        if (declared && (!/^\d+$/.test(declared) || Number(declared) > 8192))
          return reply(413, 'Request too large');
        const reader = request.body?.getReader();
        if (!reader) return reply(400, 'Invalid request');
        let size = 0;
        const chunks = [];
        try {
          for (;;) {
            const { done, value } = await reader.read();
            if (done) break;
            size += value.byteLength;
            if (size > 8192) {
              await reader.cancel();
              return reply(413, 'Request too large');
            }
            chunks.push(value);
          }
        } finally { reader.releaseLock(); }
        body = JSON.parse(Buffer.concat(chunks).toString('utf8'));
        validateRequest(body);
      } catch { return reply(400, 'Invalid request'); }
      try {
        if (!['sandbox', 'lat', 'production'].includes(env.RVS_ENVIRONMENT) ||
            typeof env.AMAZON_RVS_SECRET !== 'string' || !env.AMAZON_RVS_SECRET.trim() ||
            typeof env.SIGNING_PRIVATE_KEY !== 'string') throw new Error('Configuration');
        const privateKey = createPrivateKey(env.SIGNING_PRIVATE_KEY);
        if (privateKey.asymmetricKeyType !== 'ec' ||
            privateKey.asymmetricKeyDetails?.namedCurve !== 'prime256v1') throw new Error('Key');
        const signed = await verifyPurchase(body, { privateKey, secret: env.AMAZON_RVS_SECRET,
          environment: env.RVS_ENVIRONMENT, fetchReceipt });
        return Response.json(signed, { headers: { 'cache-control': 'no-store',
          'x-content-type-options': 'nosniff' } });
      } catch {
        // Never log exceptions: Amazon's URL contains private receipt evidence and a secret.
        return reply(503, 'Verification unavailable');
      }
    },
  };
}

export default createWorker();
