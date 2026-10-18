const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const root = path.resolve(__dirname, '..');
const modules = process.env.DISHNEST_NODE_MODULES || 'C:/Users/User/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules';
const { chromium } = require(path.join(modules, 'playwright'));
const evidenceDir = process.env.DISHNEST_QA_DIR || path.join(root, 'artifacts');
fs.mkdirSync(evidenceDir, { recursive: true });
(async () => {
  const browser = await chromium.launch({ headless: true, ...(process.env.DISHNEST_BROWSER_EXECUTABLE ? {executablePath: process.env.DISHNEST_BROWSER_EXECUTABLE} : {}) });
  const results = [];
  const snapshot = () => JSON.parse(execFileSync('python', ['tools/website_evidence.py', '--inputs'], { cwd: root, encoding: 'utf8' }));
  const inputs = snapshot();
  let passed = false;
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
      const checkedPages = [];
      for (const file of fs.readdirSync(path.join(root, 'website')).filter(file => file.endsWith('.html'))) {
        const slug = file.replace('.html', '');
        const changedResponse = await page.goto(`http://127.0.0.1:8765/${slug}.html`, { waitUntil: 'networkidle' });
        const text = await page.locator('main').innerText();
        const pageOverflow = await page.evaluate(() => document.documentElement.scrollWidth > innerWidth);
        const images = await page.locator('img').evaluateAll(nodes => nodes.filter(i => !i.complete || !i.naturalWidth).map(i => i.src));
        const meaningful = text.trim().length > 80 && await page.locator('h1').count() === 1 && (await page.title()).includes('DishNest');
        const expected = slug === 'privacy' ? text.includes('receipt identifiers') && text.includes('ZIP includes recipe photos with a 64 MB total limit') : slug === 'pricing' ? text.includes('purchases remain disabled') : true;
        if (['privacy', 'support'].includes(slug)) {
          for (const [label, href] of [['GitHub support', 'https://github.com/lanray07/DishNest/issues'], ['Privacy information on GitHub', 'https://github.com/lanray07/DishNest/blob/main/PRIVACY.txt']]) {
            if (await page.getByRole('link', {name: label, exact: true}).getAttribute('href') !== href) throw new Error(`Wrong GitHub link: ${slug}/${label}`);
          }
        }
        if (changedResponse.status() !== 200 || !meaningful || !expected || pageOverflow || images.length)
          throw new Error(`Changed page failed: ${slug}/${device}`);
        if (['privacy', 'support'].includes(slug)) await page.screenshot({ path: path.join(evidenceDir, `${slug}-${device}.png`), fullPage: false });
        checkedPages.push({ file, title: await page.title(), http: changedResponse.status(), overflow: pageOverflow, contentVerified: true, brokenImages: images });
      }
      const result = { device, identity, http: response.status(), overflow, brokenImages, pageErrors: errors, faqOpened: answerVisible > 0, checkedPages, browserPath: 'Playwright: Browser plugin not available', evidenceDir };
      results.push(result);
      if (overflow || brokenImages.length || errors.length || !answerVisible) throw new Error(`Website verification failed: ${JSON.stringify(result)}`);
      await page.close();
    }
    if (JSON.stringify(inputs) !== JSON.stringify(snapshot())) throw new Error('Website inputs changed during verification');
    passed = true;
  } finally {
    await browser.close();
    fs.writeFileSync(path.join(root, 'artifacts/website-verification.json'), JSON.stringify({passed, verifiedAt: new Date().toISOString(), inputs, results}, null, 2));
  }
  console.log(JSON.stringify(results));
})().catch(error => { console.error(error); process.exitCode = 1; });
