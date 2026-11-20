/* Real MVC markup, repository runtimes/CSS, disposable PostgreSQL cases export. */
const fs = require('node:fs'), path = require('node:path'), http = require('node:http'), assert = require('node:assert/strict');
const crypto = require('node:crypto');
const root = path.resolve(__dirname, '..'), assets = path.join(root, 'spring-panel/src/main/resources/static');
const captures = path.join(root, 'spring-panel/target/passport-cases-rendered');
const output = path.join(root, 'spring-panel/target/passport-cases-qa');
const cases = JSON.parse(fs.readFileSync(path.join(captures, 'passport-cases.json')));
const html = fs.readFileSync(path.join(captures, 'passports/detail.html'), 'utf8');
const dialogsHtml = fs.readFileSync(path.join(captures, 'dialogs-base.html'), 'utf8');
const generalHtml = fs.readFileSync(path.join(captures, 'general/work.html'), 'utf8');
const generalId = 'a'.repeat(32);
const displayedCases = cases.items.map((item, index) => ({ ...item,
  problem: index ? 'Коротко' : 'Очень длинное описание проблемы '.repeat(12), status: index ? 'resolved' : 'closed' }));
displayedCases.push({ ticket_id: 'T-3', request_number: '20261120-003', problem: 'Другая проблема', status: 'pending', dialog_url: '/dialogs?ticketId=T-3&channelId=2' },
  { ticket_id: 'general', problem: 'Общее обращение', status: 'open', dialog_url: '/general-dialog-work/' + generalId });
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
    if (url.pathname === '/dialogs' || url.pathname === '/general-dialog-work/' + generalId) {
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'X-Frame-Options': 'SAMEORIGIN', 'Content-Security-Policy': "frame-ancestors 'self'" });
      res.end(url.pathname === '/dialogs' ? dialogsHtml : generalHtml); return;
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
  const report = { screens: [], runtimeErrors: [], navigation: [], interactions: [], detailRequests: [] };
  fs.mkdirSync(output, { recursive: true });
  try {
    const context = await browser.newContext({ reducedMotion: 'reduce' });
    await context.addInitScript(() => localStorage.setItem('sidebarPinned', '1'));
    await context.route('**/*', route => {
      const req = route.request(), url = new URL(req.url());
      if (url.origin !== origin) return route.abort();
      if (url.pathname.startsWith('/api/') || url.pathname === '/profile/ui-preferences') {
        if (url.pathname === '/api/object_passports/42') return route.fulfill({ json: { success: true, passport: { id: 42, business: 'СушиВёсла', city: 'Москва', department: 'Касса', status: 'Работает', equipment, photos: [] } } });
        if (url.pathname === '/api/object_passports/42/cases') return route.fulfill({ json: { ...cases, items: [...displayedCases, malformed, orphan] } });
        if (url.pathname === '/api/dialog-ticket') {
          report.detailRequests.push(url.search);
          return route.fulfill({ json: { success: true, summary: { ticketId: url.searchParams.get('ticketId'), channelId: Number(url.searchParams.get('channelId')), userId: 10,
            requestNumber: '20260916-002', clientName: 'Клиент паспорта', business: 'СушиВёсла', problem: 'Проверка диалога', createdAt: '2026-09-16T12:00:00Z', status: 'closed' },
            categories: [], history: [{ sender: 'user', message: 'Сообщение клиента', timestamp: '2026-09-16T12:00:00Z' }] } });
        }
        if (url.pathname === '/api/auth/permissions') return route.fulfill({ json: { success: true, dialogs: { can_reply: true, can_close: true, can_assign: true }, permissions: {} } });
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
      assert.equal(await links.count(), 4);
      assert.equal(await links.first().getAttribute('href'), '/dialogs?ticketId=T-2&channelId=1');
      const text = await page.locator('#passportCasesList').innerText();
      assert(text.includes('№ 20260916-002') && text.includes('№ 20260916-001'));
      assert(!text.includes('#T-') && !text.includes('internal-'));
      assert(text.includes('Номер не присвоен'));
      assert.equal(await page.evaluate(() => Boolean(window.passportXss)), false);
      assert.equal(await page.locator('#passportCasesList img, #passportCasesList svg').count(), 0);
      const statusPositions = await page.locator('#passportCasesList .passport-record-status').evaluateAll(nodes => nodes.map(node => node.getBoundingClientRect().left));
      assert(statusPositions.every(left => Math.abs(left - statusPositions[0]) < 1), `Statuses must share a column: ${statusPositions}`);
      assert.equal(await page.locator('#passportCasesList .passport-record-status').first().innerText(), 'Закрыт');
      assert.equal(await page.locator('#passportCasesList .passport-record-status').nth(1).innerText(), 'Закрыт');
      await page.screenshot({ path: path.join(output, `cases-${width}-${theme}.png`), fullPage: true });
      const originalUrl = page.url();
      await page.evaluate(() => { window.originalPassportNode = document.getElementById('passportWorkspace'); });
      await links.first().focus();
      await page.keyboard.press('Enter');
      await page.waitForSelector('#passportDialogPreview[open]');
      const preview = page.frameLocator('[data-passport-dialog-frame]');
      await preview.locator('#dialogDetailsModal.show').waitFor();
      await preview.locator('#dialogDetailsClientName').filter({ hasText: 'Клиент паспорта' }).waitFor();
      assert.equal(page.url(), originalUrl);
      assert.equal(await preview.locator('main').isVisible(), false);
      if (width === 1440 && theme === 'light') {
        assert.equal(await preview.locator('body').evaluate(() => {
          const element = document.createElement('div'); let suppress = true, calls = 0;
          const flow = window.DialogsFlowRuntime.createRuntime({ elements: { detailsModalEl: element },
            getCategoryPanelState: () => ({ suppressDetailsReset: suppress }), onDialogModalClosed: () => { calls++; return true; } });
          flow.bindDetailsModalLifecycle(); element.dispatchEvent(new Event('hidden.bs.modal'));
          if (calls !== 0) return false;
          suppress = false; element.dispatchEvent(new Event('hidden.bs.modal')); return calls === 1;
        }), true);
        report.interactions.push('category-modal-guard');
      }
      await page.evaluate(() => window.postMessage({ type: 'iguana-passport-dialog', action: 'close' }, window.location.origin));
      assert.equal(await page.locator('#passportDialogPreview').evaluate(node => node.open), true);
      await page.screenshot({ path: path.join(output, `dialog-${width}-${theme}.png`), fullPage: true });
      await preview.locator('#dialogDetailsModal [data-bs-dismiss="modal"]').first().click();
      await page.waitForSelector('#passportDialogPreview:not([open])', { state: 'attached' });
      assert.equal(page.url(), originalUrl);
      assert(await page.evaluate(() => window.originalPassportNode === document.getElementById('passportWorkspace')));
      assert(await page.locator('[data-passport-panel="cases"]').evaluate(node => node.classList.contains('is-active')));
      report.navigation.push({ width, theme, url: page.url(), passportPreserved: true });
      if (width === 1440 && theme === 'light') {
        for (const action of ['escape', 'back', 'parent-close']) {
          console.log('Checking overlay action: ' + action);
          await page.locator('#passportCasesList a').first().click();
          await preview.locator('#dialogDetailsModal.show').waitFor();
          if (action === 'escape') await preview.locator('#dialogDetailsModal').focus().then(() => page.keyboard.press('Escape'));
          if (action === 'back') await page.goBack();
          if (action === 'parent-close') await page.locator('[data-passport-dialog-close]').click();
          await page.waitForSelector('#passportDialogPreview:not([open])', { state: 'attached' });
          assert.equal(page.url(), originalUrl); report.interactions.push(action);
        }
        await page.locator('#passportCasesList a').last().click();
        await preview.locator('[data-general-work]').waitFor({ state: 'attached' });
        await preview.locator('[data-general-work]').waitFor();
        await preview.locator('[data-general-work]').click();
        await page.keyboard.press('Escape');
        await page.waitForSelector('#passportDialogPreview:not([open])', { state: 'attached' });
        assert.equal(page.url(), originalUrl); report.interactions.push('general-close');
      }
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
    await page.goto(origin + '/dialogs?ticketId=T-2&channelId=1', { waitUntil: 'load' });
    await page.locator('#dialogDetailsModal.show').waitFor();
    assert.equal(await page.evaluate(() => document.documentElement.hasAttribute('data-passport-dialog')), false);
    assert.equal(await page.locator('main').isVisible(), true);
    await page.locator('#dialogDetailsModal [data-bs-dismiss="modal"]').first().click();
    await page.waitForURL(origin + '/dialogs');
    report.interactions.push('normal-dialog-page');
    assert.deepEqual(report.runtimeErrors, []);
    assert(report.detailRequests.every(search => new URLSearchParams(search).get('channelId')));
    report.assetHashes = Object.fromEntries(['css/app.css', 'js/passport-detail-activity-runtime.js', 'js/passport-dialog-preview-runtime.js', 'js/dialog-embedded-runtime.js', 'js/dialogs-flow-runtime.js', 'js/dialogs.js', 'js/dialogs-details-runtime.js', 'js/passport-detail-page-runtime.js'].map(name =>
      [name, crypto.createHash('sha256').update(fs.readFileSync(path.join(assets, name))).digest('hex')]));
    fs.writeFileSync(path.join(output, 'report.json'), JSON.stringify(report, null, 2));
    console.log(`GREEN: ${report.screens.length} responsive/theme cases; aligned/localized statuses; passport overlay; XSS; tasks; ${report.interactions.join(', ')}; 0 runtime errors`);
  } finally { await browser.close(); await new Promise(resolve => server.close(resolve)); }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
