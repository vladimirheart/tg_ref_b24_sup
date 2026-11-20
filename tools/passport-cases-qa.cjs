/* Real MVC markup, repository runtimes/CSS, disposable PostgreSQL cases export. */
const fs = require('node:fs'), path = require('node:path'), http = require('node:http'), assert = require('node:assert/strict');
const crypto = require('node:crypto');
const root = path.resolve(__dirname, '..'), assets = path.join(root, 'spring-panel/src/main/resources/static');
const captures = path.join(root, 'spring-panel/target/passport-cases-rendered');
const output = path.join(root, 'spring-panel/target/passport-cases-qa');
const cases = JSON.parse(fs.readFileSync(path.join(captures, 'passport-cases.json')));
const html = fs.readFileSync(path.join(captures, 'passports/detail.html'), 'utf8');
assert(!html.includes('const initialCreateMode = true'), 'Capture the existing passport page');
const equipment = [
  { name: 'Маршрутизатор с очень длинным названием модели оборудования', status: 'Ожидает согласования технического обслуживания и замены оборудования' },
  { name: 'МодельБезПробелов'.repeat(10), status: 'СтатусБезПробелов'.repeat(10) },
  { name: 'Кассовый компьютер', status: 'Работает' },
  { name: 'Архивное оборудование', archived: true }
].map(item => ({ equipment_type: 'Касса', ...item }));
const malformed = { ticket_id: 'internal-secret-id', problem: '<img src=x onerror="window.passportXss=1">', request_number: '20261120-004', status: '<svg onload="window.passportXss=1">', dialog_url: 'javascript:window.passportXss=1' };
const orphan = { ticket_id: 'internal-orphan-id', problem: 'Старое обращение' };
const mime = { '.css': 'text/css', '.js': 'application/javascript', '.svg': 'image/svg+xml', '.woff2': 'font/woff2' };
async function main() {
  const server = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://localhost');
    if (url.pathname === '/object-passports/42') { res.setHeader('Content-Type', 'text/html; charset=utf-8'); res.end(html); return; }
    if (url.pathname === '/dialogs') { res.setHeader('Content-Type', 'text/html; charset=utf-8'); res.end('<p>Целевой диалог</p>'); return; }
    const file = path.resolve(assets, url.pathname.slice(1));
    if (!file.startsWith(assets + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) { res.writeHead(404); res.end('{}'); return; }
    res.setHeader('Content-Type', mime[path.extname(file)] || 'application/octet-stream'); fs.createReadStream(file).pipe(res);
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const origin = 'http://127.0.0.1:' + server.address().port;
  const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
  const browser = await chromium.launch({ headless: true });
  const report = { screens: [], runtimeErrors: [], navigation: [] };
  fs.mkdirSync(output, { recursive: true });
  try {
    const context = await browser.newContext({ reducedMotion: 'reduce' });
    await context.addInitScript(() => localStorage.setItem('sidebarPinned', '1'));
    await context.route('**/*', route => {
      const req = route.request(), url = new URL(req.url());
      if (url.origin !== origin) return route.abort();
      if (url.pathname.startsWith('/api/') || url.pathname === '/profile/ui-preferences') {
        if (url.pathname === '/api/object_passports/42') return route.fulfill({ json: { success: true, passport: { id: 42, business: 'СушиВёсла', city: 'Москва', department: 'Касса', status: 'Работает', equipment, photos: [] } } });
        if (url.pathname === '/api/object_passports/42/cases') return route.fulfill({ json: { ...cases, items: [...cases.items, malformed, orphan] } });
        if (url.pathname === '/api/object_passports/42/tasks') return route.fulfill({ json: { items: [{ id: 'task-1', title: 'Проверить кассу', status: 'Открыта' }] } });
        return route.fulfill({ json: { success: true, items: [], data: [], settings: {}, available: false } });
      }
      assert(['GET', 'HEAD'].includes(req.method()), 'No unexpected writes');
      return route.continue();
    });
    const page = await context.newPage();
    page.on('pageerror', error => report.runtimeErrors.push(String(error)));
    for (const width of [1440, 1024, 390, 320]) for (const theme of ['light', 'dark']) {
      await page.setViewportSize({ width, height: 1100 });
      await page.goto(origin + '/object-passports/42', { waitUntil: 'load' });
      await page.waitForSelector('#passportWorkspace:not(.d-none)');
      await page.evaluate(theme => { window.ThemeRuntime.setPalette('neo'); window.ThemeRuntime.setTheme(theme); }, theme);
      const editorClose = page.locator('button[data-passport-edit-close]').filter({ hasText: 'Закрыть' });
      if (await editorClose.isVisible()) await editorClose.click();
      await page.locator('[data-passport-tab="cases"]').click();
      const links = page.locator('#passportCasesList a');
      assert.equal(await links.count(), 2);
      assert.equal(await links.first().getAttribute('href'), '/dialogs?ticketId=T-2&channelId=1');
      const text = await page.locator('#passportCasesList').innerText();
      assert(text.includes('№ 20260916-002') && text.includes('№ 20260916-001'));
      assert(!text.includes('#T-') && !text.includes('internal-'));
      assert(text.includes('Номер не присвоен'));
      assert.equal(await page.evaluate(() => Boolean(window.passportXss)), false);
      assert.equal(await page.locator('#passportCasesList img, #passportCasesList svg').count(), 0);
      await links.first().focus();
      await page.keyboard.press('Enter');
      await page.waitForURL(origin + '/dialogs?ticketId=T-2&channelId=1');
      report.navigation.push({ width, theme, url: new URL(page.url()).pathname + new URL(page.url()).search });
      await page.goBack({ waitUntil: 'load' });
      await page.waitForSelector('#passportWorkspace:not(.d-none)');
      if (await editorClose.isVisible()) await editorClose.click();
      await page.locator('[data-passport-tab="equipment"]').click();
      await page.locator('[data-equipment-group-toggle]').click();
      await page.screenshot({ path: path.join(output, `equipment-${width}-${theme}.png`), fullPage: true });
      const layout = await page.locator('.passport-asset-card').evaluateAll(cards => cards.map(card => {
        const status = card.querySelector('.passport-asset-status'), title = card.querySelector('h3');
        const bounds = card.getBoundingClientRect(), range = document.createRange(); range.selectNodeContents(status);
        return { status: status.textContent, visible: status.offsetWidth > 0,
          outside: [...range.getClientRects()].some(rect => rect.left < bounds.left - 1 || rect.right > bounds.right + 1),
          statusOverflow: status.scrollWidth > status.clientWidth + 1,
          titleOverflow: title.scrollWidth > title.clientWidth + 1 };
      }));
      assert.equal(layout.length, 4);
      assert(layout.every(item => item.visible && !item.outside && !item.statusOverflow && !item.titleOverflow), JSON.stringify({ width, theme, layout }));
      assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 2), `Page overflow at ${width}`);
      await page.locator('[data-passport-tab="tasks"]').click();
      assert((await page.locator('#passportTasksList').innerText()).includes('#task-1'));
      report.screens.push({ width, theme, equipment: layout.length });
    }
    assert.deepEqual(report.runtimeErrors, []);
    report.assetHashes = Object.fromEntries(['css/app.css', 'js/passport-detail-activity-runtime.js'].map(name =>
      [name, crypto.createHash('sha256').update(fs.readFileSync(path.join(assets, name))).digest('hex')]));
    fs.writeFileSync(path.join(output, 'report.json'), JSON.stringify(report, null, 2));
    console.log(`GREEN: ${report.screens.length} responsive/theme cases; keyboard navigation; number display; XSS; tasks preserved; 0 runtime errors`);
  } finally { await browser.close(); await new Promise(resolve => server.close(resolve)); }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
