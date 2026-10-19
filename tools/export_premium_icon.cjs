// Technical size/format exports only. The artwork is the imagegen-authored source.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const root = path.resolve(__dirname, '..');
const modules = process.env.DISHNEST_NODE_MODULES || 'C:/Users/User/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules';
const sharp = require(path.join(modules, 'sharp'));
const assets = path.join(root, 'marketing/assets');
const source = path.join(assets, 'icon-premium-source.png');

(async () => {
  const metadata = await sharp(source).metadata();
  if (metadata.width !== metadata.height || metadata.width < 1024) throw new Error('High-resolution square source required');
  const outputs = [
    [2048, 'marketing/assets/icon-master.png'],
    [512, 'marketing/assets/icon-512.png'],
    [114, 'marketing/assets/icon-114.png'],
    [1024, 'marketing/assets/adaptive-foreground.png'],
    [1024, 'app/src/main/res/drawable-nodpi/ic_nest_color.png'],
  ];
  for (const [size, relative] of outputs) {
    const target = path.join(root, relative);
    fs.mkdirSync(path.dirname(target), {recursive: true});
    await sharp(source).resize(size, size, {kernel: 'lanczos3'}).toColourspace('srgb').png().toFile(target);
  }
  await sharp({create: {width: 1024, height: 1024, channels: 3, background: '#00484D'}})
    .png().toFile(path.join(assets, 'adaptive-background.png'));
  const promotionIcon = await sharp(source).resize(230, 230).png().toBuffer();
  const promotionText = Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="500">
    <text x="350" y="220" font-family="Georgia,serif" font-size="78" fill="#29312d">DishNest</text>
    <g font-family="Segoe UI,sans-serif" font-size="30" fill="#456b59">
      <text x="353" y="285">Save recipes. Plan meals.</text>
      <text x="353" y="335">Cook hands-free.</text>
    </g></svg>`);
  await sharp({create: {width: 1024, height: 500, channels: 3, background: '#faf7f0'}})
    .composite([{input: promotionIcon, left: 70, top: 135}, {input: promotionText}])
    .png().toFile(path.join(assets, 'promotional-1024x500.png'));
  // Keep existing SVG consumers working with the identical authored raster art.
  // These compatibility wrappers are not advertised as editable vector masters.
  const data = fs.readFileSync(path.join(assets, 'icon-512.png')).toString('base64');
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" viewBox="0 0 512 512"><image width="512" height="512" xlink:href="data:image/png;base64,${data}"/></svg>\n`;
  for (const relative of ['marketing/assets/icon-master.svg', 'website/assets/icon.svg']) {
    fs.writeFileSync(path.join(root, relative), svg);
  }
  const manifestPath = path.join(assets, 'asset-manifest.json');
  const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
  manifest.icon = 'Premium emerald/teal, coral and champagne-gold nest-and-leaf artwork; imagegen-authored raster master with size exports and SVG compatibility wrappers. Original monochrome vector retained for themed icons and notifications.';
  manifest.icon_generation = {tool: 'built-in image_gen', date: '2026-10-19', source: 'marketing/assets/icon-premium-source.png',
    prompt: 'marketing/icon-premium-prompt.txt', sourceSha256: crypto.createHash('sha256').update(fs.readFileSync(source)).digest('hex')};
  fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2) + '\n');
  console.log(JSON.stringify({iconExports: outputs.length, promotionalExport: true, svgConsumersUpdated: 2, sourcePixels: metadata.width, published: false}));
})().catch(error => { console.error(error.message); process.exitCode = 1; });
