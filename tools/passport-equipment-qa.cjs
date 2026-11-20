/* Browser regression on MVC markup and production assets; only loopback fixture data. */
const fs = require('node:fs'), path = require('node:path'), http = require('node:http');
const assert = require('node:assert/strict'), crypto = require('node:crypto');
const root = path.resolve(__dirname, '..');
const assets = path.resolve(process.env.PASSPORT_QA_ASSETS || path.join(root, 'spring-panel/src/main/resources/static'));
const output = path.join(root, 'spring-panel/target/passport-equipment-qa');
const catalog = [
  { id: 11, equipment_type: 'Касса', equipment_vendor: 'Производитель', equipment_model: 'Модель', photo_url: JSON.stringify({
    photos: [{ url: '/qa/archive.svg', category: 'archive', caption: 'Разъёмы оборудования' },
      { url: '/qa/title.svg', category: 'title', caption: 'Вид спереди' }], links: ['/qa/manual.pdf'] }) },
  { id: 12, equipment_type: 'Касса', photo_url: '' },
];
const equipment = [
  { catalog_id: 11, equipment_type: 'Касса', name: 'Кассовый компьютер', status: 'Ожидает согласования технического обслуживания',
    serial_number: 'SN-001', ip_address: '192.0.2.11', configuration_profile_id: 71, description: 'Сведения о комплектации\n' + 'Подробное описание. '.repeat(40) },
  { catalog_id: 12, equipment_type: 'Касса', name: 'Без фотографии', status: 'Работает', serial_number: 'SN-002' },
  { catalog_id: 11, equipment_type: 'Касса', name: 'Архивная касса', archived: true },
];
const passport = { id: 42, business: 'СушиВёсла', city: 'Москва', department: 'Проверка оборудования', status: 'Работает', equipment,
  photos: [{ url: '/qa/object.svg', category: 'title', caption: 'Фото самого объекта' }] };
let html = fs.readFileSync(path.join(root, 'spring-panel/target/passport-equipment-rendered/passports/detail.html'), 'utf8');
assert.equal((html.match(/const catalogRaw = \[\];/g) || []).length, 1, 'Expected empty MVC catalogue fixture');
html = html.replace('const catalogRaw = [];', 'const catalogRaw = ' + JSON.stringify(catalog) + ';');
assert(!html.includes('const initialCreateMode = true'));
const mime = { '.css': 'text/css', '.js': 'application/javascript', '.svg': 'image/svg+xml', '.woff2': 'font/woff2' };
const report = { scenarios: [], interactions: [], errors: [], requests: [] };
async function main() {
  const server = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://localhost');
    if (url.pathname === '/object-passports/42') { res.setHeader('Content-Type', 'text/html; charset=utf-8'); res.end(html); return; }
    if (url.pathname.startsWith('/qa/')) {
      res.setHeader('Content-Type', 'image/svg+xml');
      res.end('<svg xmlns="http://www.w3.org/2000/svg" width="1000" height="700"><rect width="1000" height="700" fill="#526987"/><text x="80" y="300" fill="white" font-size="70">' + url.pathname + '</text></svg>'); return;
    }
    const file = path.resolve(assets, url.pathname.slice(1));
    if (!file.startsWith(assets + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) { res.writeHead(404); res.end('{}'); return; }
    res.setHeader('Content-Type', mime[path.extname(file)] || 'application/octet-stream');
    if (/\.(js|css)$/.test(file)) res.end(fs.readFileSync(file, 'utf8').replaceAll('\r\n', '\n'));
    else fs.createReadStream(file).pipe(res);
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const origin = 'http://127.0.0.1:' + server.address().port;
  const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
  const browser = await chromium.launch({ headless: true });
  fs.mkdirSync(output, { recursive: true });
  try {
    const context = await browser.newContext({ reducedMotion: 'reduce' });
    await context.route('**/*', route => {
      const req = route.request(), url = new URL(req.url());
      if (url.origin !== origin) return route.abort();
      assert(['GET', 'HEAD'].includes(req.method()), 'Read-only UI must not write');
      if (url.pathname.startsWith('/api/') || url.pathname === '/profile/ui-preferences') {
        report.requests.push(url.pathname);
        if (url.pathname === '/api/object_passports/42') return route.fulfill({ json: { success: true, passport } });
        if (url.pathname === '/api/settings/it-equipment/profiles') return route.fulfill({ json: { items: [{ id: 71, profile_name: 'Стандартная касса' }] } });
        return route.fulfill({ json: { success: true, items: [], data: [], settings: {}, available: false } });
      }
      return route.continue();
    });
    const page = await context.newPage();
    page.on('pageerror', error => report.errors.push(String(error)));
    const popup = page.locator('.passport-equipment-popover:not([hidden])');
    const viewer = page.locator('#passportPhotoViewer');
    for (const width of [1440, 1024, 390, 320]) for (const theme of ['light', 'dark']) {
      await page.setViewportSize({ width, height: width < 400 ? 660 : 1000 });
      await page.goto(origin + '/object-passports/42', { waitUntil: 'load' });
      await page.waitForSelector('#passportWorkspace:not(.d-none)');
      await page.evaluate(theme => { window.ThemeRuntime.setPalette('north'); window.ThemeRuntime.setTheme(theme); }, theme);
      const editorClose = page.locator('button[data-passport-edit-close]').filter({ hasText: 'Закрыть' });
      if (await editorClose.isVisible()) await editorClose.click();
      await page.locator('[data-passport-tab="equipment"]').click();
      await page.locator('[data-equipment-group-toggle]').click();
      const card = page.locator('[data-equipment-index="0"]');
      const photo = card.locator('[data-equipment-photo-index]');
      assert.equal(await photo.count(), 1, 'Equipment image must be a preview button');
      assert.equal(await page.locator('[data-equipment-index="1"] [data-equipment-photo-index]').count(), 0);
      assert.equal(await page.locator('[data-equipment-index="2"]').evaluate(n => n.classList.contains('is-archived')), true);
      const before = await card.boundingBox();
      const info = card.locator('[data-equipment-details-toggle]');
      await info.click();
      await popup.waitFor();
      await popup.locator('[data-equipment-profile-name]').filter({ hasText: 'Стандартная касса (#71)' }).waitFor();
      const bounds = await popup.boundingBox(), after = await card.boundingBox();
      assert(Math.abs(after.height - before.height) < 1, 'Info must not expand the card');
      assert(await popup.evaluate(n => n.parentElement === document.body), 'Bubble must escape card clipping');
      assert(bounds.x >= 11 && bounds.x + bounds.width <= width - 11 && bounds.y >= 11 && bounds.y + bounds.height <= (width < 400 ? 660 : 1000) - 11, JSON.stringify(bounds));
      assert.equal(await popup.locator('a').getAttribute('href'), '/qa/manual.pdf');
      await page.screenshot({ path: path.join(output, `bubble-${width}-${theme}.png`) });
      assert.equal(await info.getAttribute('aria-expanded'), 'true', 'Bubble remains open before Escape');
      await page.keyboard.press('Escape');
      assert.equal(await popup.count(), 0);
      assert(await info.evaluate(n => n === document.activeElement), JSON.stringify({ check: 'Escape returns focus to info', width, theme, active: await page.evaluate(() => document.activeElement.outerHTML.slice(0, 600)) }));
      await photo.focus();
      await page.keyboard.press('Enter');
      await viewer.waitFor();
      assert((await page.locator('#passportPhotoViewerImage').getAttribute('src')).endsWith('/qa/title.svg'), 'Start with cover, not first archived photo');
      assert.equal(await page.locator('#passportPhotoViewerCounter').innerText(), '2 / 2');
      assert.equal(await page.locator('#passportPhotoViewerCaption').innerText(), 'Вид спереди');
      await page.waitForFunction(() => document.getElementById('passportPhotoViewerImage').naturalWidth === 1000);
      await page.keyboard.press('ArrowRight');
      assert.equal(await page.locator('#passportPhotoViewerCounter').innerText(), '1 / 2');
      assert.equal(await page.locator('#passportPhotoViewerCaption').innerText(), 'Разъёмы оборудования');
      await page.keyboard.press('ArrowLeft');
      assert.equal(await page.locator('#passportPhotoViewerCounter').innerText(), '2 / 2');
      for (let i = 0; i < 5; i++) {
        await page.keyboard.press('Tab');
        assert(await viewer.evaluate(n => n.contains(document.activeElement)), 'Keep keyboard focus in viewer');
      }
      await page.screenshot({ path: path.join(output, `photo-${width}-${theme}.png`) });
      await page.keyboard.press('Escape');
      assert.equal(await viewer.isVisible(), false);
      assert(await photo.evaluate(n => n === document.activeElement), 'Photo preview restores focus');
      await info.click();
      await page.locator('[data-passport-tab="overview"]').focus();
      await page.keyboard.press('Enter');
      assert.equal(await popup.count(), 0, 'Tab change closes bubble');
      await page.locator('[data-passport-tab="photos"]').click();
      await page.locator('#passportPhotoGrid [data-passport-photo-index]').focus();
      await page.keyboard.press('Space');
      await viewer.waitFor();
      assert.equal(await page.locator('#passportPhotoViewerCaption').innerText(), 'Фото самого объекта');
      assert.equal(await page.locator('#passportPhotoViewerCounter').innerText(), '1 / 1');
      assert.equal(await page.locator('[data-passport-photo-next]').isVisible(), false);
      await viewer.locator('.passport-photo-viewer__close').click();
      assert(await page.locator('#passportPhotoGrid [data-passport-photo-index]').evaluate(n => n === document.activeElement));
      assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 2), 'No page overflow');
      report.scenarios.push({ width, theme, cardHeight: after.height, bubble: bounds, gallery: 2 });
    }
    await page.setViewportSize({ width: 1024, height: 520 });
    await page.locator('[data-passport-tab="equipment"]').click();
    const search = page.locator('#passportEquipmentSearch');
    await search.fill('Архивная');
    await page.locator('[data-equipment-index="0"] [data-equipment-photo-index]').click();
    assert.equal(await page.locator('#passportPhotoViewerCategory').innerText(), 'Архивная касса', 'Filtered gallery belongs to correct item');
    await viewer.locator('.passport-photo-viewer__backdrop').click({ position: { x: 1, y: 1 } });
    await page.locator('[data-equipment-details-toggle]').click();
    await page.locator('[data-equipment-details-toggle]').click();
    assert.equal(await popup.count(), 0, 'Second click closes bubble');
    await search.fill('');
    const infos = page.locator('[data-equipment-details-toggle]');
    await infos.nth(0).click();
    await infos.nth(1).focus();
    await page.keyboard.press('Enter');
    assert.equal(await popup.count(), 1, 'Only one bubble at a time');
    assert((await popup.getAttribute('aria-label')).includes('Без фотографии'));
    await page.mouse.click(40, 10);
    assert.equal(await popup.count(), 0, 'Outside click closes bubble');
    await infos.nth(0).focus();
    await page.keyboard.press('Enter');
    await search.fill('несуществующее оборудование');
    assert.equal(await popup.count(), 0, 'Rerender removes bubble');
    assert.equal(await page.locator('body > .passport-equipment-popover').count(), 0, 'No orphaned portals');
    report.interactions.push('filtered-photo', 'backdrop-close', 'toggle-close', 'single-bubble', 'outside-close', 'search-cleanup');
    assert.deepEqual(report.errors, []);
    report.assetHashes = Object.fromEntries(['css/app.css', 'js/passport-detail-equipment-runtime.js', 'js/passport-detail-media-runtime.js', 'js/passport-detail-page-runtime.js'].map(name =>
      [name, crypto.createHash('sha256').update(fs.readFileSync(path.join(assets, name))).digest('hex')]));
    fs.writeFileSync(path.join(output, 'report.json'), JSON.stringify(report, null, 2));
    console.log(`GREEN: ${report.scenarios.length} viewport/theme scenarios, ${report.interactions.length} additional interactions; runtime errors 0.`);
  } finally { await browser.close(); await new Promise(resolve => server.close(resolve)); }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
