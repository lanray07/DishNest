import { createHash, sign } from 'node:crypto';

export const HOUR = 3_600_000;
export const hash = value => createHash('sha256').update(value).digest('hex');
const products = new Map([
  ['dishnest.premium', 'SUBSCRIPTION'],
  ['dishnest.premium.monthly', 'SUBSCRIPTION'],
  ['dishnest.premium.annual', 'SUBSCRIPTION'],
  ['dishnest.premium.lifetime', 'ENTITLED'],
]);

// Amazon cancelDate is loss of access, not the date autorenew was switched off.
export function entitlement(receipt, receiptId, now, environment) {
  const sku = products.has(receipt.termSku) ? receipt.termSku : receipt.productId;
  if (receipt.receiptId !== receiptId || !products.has(sku) || products.get(sku) !== receipt.productType)
    throw new Error('Receipt identity or product mismatch');
  if (!Number.isSafeInteger(receipt.purchaseDate) || receipt.purchaseDate > now + 300_000)
    throw new Error('Invalid purchase date');
  if (environment === 'production' && (receipt.testTransaction || receipt.betaProduct))
    throw new Error('Test receipt in production');
  if (environment === 'lat' && !receipt.testTransaction && !receipt.betaProduct)
    throw new Error('Non-test receipt in LAT profile');
  for (const key of ['cancelDate', 'renewalDate', 'gracePeriodEndDate'])
    if (receipt[key] != null && (!Number.isSafeInteger(receipt[key]) || receipt[key] < 0))
      throw new Error('Invalid receipt dates');
  const canceled = receipt.cancelDate != null && receipt.cancelDate <= now;
  // A validated subscription with no renewal date may be non-renewing. Recheck
  // within an hour rather than inventing a monthly/annual expiry from SKU text.
  let limit = now + (receipt.productType === 'SUBSCRIPTION' ? HOUR : 24 * HOUR);
  if (receipt.productType === 'SUBSCRIPTION') {
    const renewal = Math.max(receipt.renewalDate ?? 0, receipt.gracePeriodEndDate ?? 0);
    if (renewal > 0) limit = Math.min(now + 24 * HOUR, renewal);
  }
  if (receipt.cancelDate != null && receipt.cancelDate > now) limit = Math.min(limit, receipt.cancelDate);
  const active = !canceled && limit > now;
  return { sku, active, expiresAt: active ? limit : now + HOUR };
}

export function validateRequest(body) {
  for (const key of ['userId', 'receiptId', 'nonce'])
    if (typeof body[key] !== 'string' || !body[key].length || body[key].length > 2048)
      throw new Error('Invalid request');
  if (!/^[a-f0-9-]{36}$/.test(body.nonce)) throw new Error('Invalid nonce');
}

export async function verifyPurchase(body, { secret, environment, privateKey, fetchReceipt, now = Date.now() }) {
  validateRequest(body);
  const receipt = await fetchReceipt(body.userId, body.receiptId, secret, environment);
  const result = receipt === null
    ? { sku: '', active: false, expiresAt: now + HOUR }
    : entitlement(receipt, body.receiptId, now, environment);
  const payload = Buffer.from(JSON.stringify({ version: 1, audience: 'com.dishnest.app', environment,
    userHash: hash(body.userId), receiptHash: hash(body.receiptId), nonce: body.nonce,
    verifiedAt: now, ...result })).toString('base64');
  const signature = sign('sha256', Buffer.from(payload, 'base64'), privateKey).toString('base64');
  return { payload, signature };
}

export async function amazonReceipt(userId, receiptId, secret, environment) {
  const segment = value => encodeURIComponent(value);
  const sandbox = environment === 'sandbox' ? '/sandbox' : '';
  const url = `https://appstore-sdk.amazon.com${sandbox}/version/1.0/verifyReceiptId/developer/${segment(secret)}/user/${segment(userId)}/receiptId/${segment(receiptId)}`;
  // No request URLs, credentials or receipt IDs are logged. Redirects cannot leak secrets.
  // Workers supports manual redirects; all 3xx responses fail below without following them.
  const response = await fetch(url, { signal: AbortSignal.timeout(12_000), redirect: 'manual' });
  if ([400, 410].includes(response.status)) return null;
  if (!response.ok) throw new Error('Verification temporarily unavailable');
  const text = await response.text();
  if (text.length > 65536) throw new Error('Oversized RVS response');
  return JSON.parse(text);
}
