const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const modules = process.env.DISHNEST_NODE_MODULES || 'C:/Users/User/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules';
const { chromium } = require(path.join(modules, 'playwright'));
const evidenceDir = process.env.DISHNEST_QA_DIR || path.join(root, 'artifacts');
fs.mkdirSync(evidenceDir, { recursive: true });
(async () => {
  const browser = await chromium.launch({ headless: true, ...(process.env.DISHNEST_BROWSER_EXECUTABLE ? {executablePath: process.env.DISHNEST_BROWSER_EXECUTABLE} : {}) });
  const results = [];
  try {
    for (const [device, width, height] of [['desktop', 1440, 1000], ['phone', 390, 844]]) {
      const page = await browser.newPage({ viewport: { width, height } });
      const errors = [];
      page.on('pageerror', error => errors.push(error.message));
      page.on('console', message => { if (['error', 'warning'].includes(message.type())) errors.push(message.text()); });
      const response = await page.goto('http://127.0.0.1:8765', { waitUntil: 'networkidle' });
      if (response.status() !== 200) throw new Error('Homepage did not return HTTP 200');
      await page.locator('h1').waitFor();
      const identity = (await page.title()).includes('DishNest') && page.url().startsWith('http://127.0.0.1:8765');
      if (!identity) throw new Error('Unexpected page identity');
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth > innerWidth);
      const brokenImages = await page.locator('img').evaluateAll(images => images.filter(i => !i.complete || !i.naturalWidth).map(i => i.src));
      await page.getByText('Can I access saved recipes offline?', { exact: true }).click();
      const answerVisible = await page.locator('details[open]').count();
      const links = await page.locator('a[href]').evaluateAll(a => a.map(n => n.href));
      for (const url of [...new Set(links)].filter(url => url.startsWith('http://127.0.0.1:8765') && !url.includes('#'))) {
        if ((await page.request.get(url)).status() !== 200) throw new Error(`Broken page link: ${url}`);
      }
      await page.screenshot({ path: path.join(evidenceDir, `website-${device}.png`), fullPage: true });
      const changedPages = [];
      for (const slug of ['pricing', 'privacy']) {
        const changedResponse = await page.goto(`http://127.0.0.1:8765/${slug}.html`, { waitUntil: 'networkidle' });
        const text = await page.locator('main').innerText();
        const pageOverflow = await page.evaluate(() => document.documentElement.scrollWidth > innerWidth);
        if (changedResponse.status() !== 200 || !text.includes(slug === 'privacy' ? 'receipt identifiers' : 'purchases remain disabled') || (slug === 'privacy' && !text.includes('ZIP includes recipe photos with a 64 MB total limit')) || pageOverflow)
          throw new Error(`Changed page failed: ${slug}/${device}`);
        await page.screenshot({ path: path.join(evidenceDir, `${slug}-${device}.png`), fullPage: false });
        changedPages.push({ slug, title: await page.title(), http: changedResponse.status(), overflow: pageOverflow, contentVerified: true });
      }
      const result = { device, identity, http: response.status(), overflow, brokenImages, pageErrors: errors, faqOpened: answerVisible > 0, changedPages, browserPath: 'Playwright: Browser plugin not available', evidenceDir };
      results.push(result);
      if (overflow || brokenImages.length || errors.length || !answerVisible) throw new Error(`Website verification failed: ${JSON.stringify(result)}`);
      await page.close();
    }
  } finally {
    await browser.close();
    fs.writeFileSync(path.join(root, 'artifacts/website-verification.json'), JSON.stringify(results, null, 2));
  }
  console.log(JSON.stringify(results));
})().catch(error => { console.error(error); process.exitCode = 1; });
