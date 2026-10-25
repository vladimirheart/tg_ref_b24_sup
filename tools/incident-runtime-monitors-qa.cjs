/* R18: local MVC/API fixtures only. Never writes to a working panel. */
'use strict';
const fs = require('node:fs'), path = require('node:path'), http = require('node:http'), assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..'), staticRoot = path.join(root, 'spring-panel/src/main/resources/static');
const captures = path.join(root, 'spring-panel/target/neutral-ops-rendered'), output = path.join(root, 'spring-panel/target/incidents-r18-qa');
async function main() {
  fs.mkdirSync(output, { recursive: true });
  const server = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://localhost'), screen = url.pathname.startsWith('/screens/');
    const base = screen ? captures : staticRoot, file = path.resolve(base, screen ? url.pathname.slice(9) + '.html' : url.pathname.slice(1));
    if (!file.startsWith(base + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) { res.writeHead(404); return res.end('{}'); }
    res.setHeader('Content-Type', ({ '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml' })[path.extname(file)] || 'application/octet-stream');
    fs.createReadStream(file).pipe(res);
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
  const browser = await chromium.launch({ headless: true }), origin = `http://127.0.0.1:${server.address().port}`, results = [];
  try {
    for (const theme of ['light', 'dark']) for (const width of [320, 390, 768, 1440, 1920]) for (const editable of [true, false]) {
      const context = await browser.newContext({ viewport: { width, height: 1100 }, colorScheme: theme, reducedMotion: 'reduce' });
      await context.addInitScript(theme => { localStorage.setItem('iguana:theme', theme); localStorage.setItem('iguana:theme-palette', 'neo'); localStorage.setItem('sidebarPinned', '1'); }, theme);
      const policies = [
        ['integration_transport', 'Транспорт и фоновые задачи — общий выключатель'],
        ['integration_transport.queue', 'Очередь транспорта интеграций'],
        ['integration_transport.ui_updates', 'Обновление открытых страниц панели'],
        ['integration_transport.operator_notifications', 'Обработка новых сообщений для операторов']
      ].map(([id, label]) => ({ id, label, mode: 'enabled', effective_mode: 'enabled', suppressed: false, description: 'Нет новых данных — нет инцидента. Пауза останавливает проверку, а не рабочий обработчик.' }));
      const writes = [], errors = []; let failNext = false;
      await context.route('**/*', async route => {
        const req = route.request(), url = new URL(req.url()); if (url.origin !== origin) return route.abort();
        if (!url.pathname.startsWith('/api/')) return route.continue();
        if (url.pathname.startsWith('/api/incidents/monitoring')) {
          if (req.method() === 'PATCH') {
            assert(editable); const id = decodeURIComponent(url.pathname.split('/').at(-1)), payload = req.postDataJSON(); writes.push({ id, payload });
            if (failNext) { failNext = false; return route.fulfill({ status: 503, json: { message: 'Тестовая ошибка сохранения' } }); }
            const item = policies.find(row => row.id === id); assert(item);
            Object.assign(item, payload, { effective_mode: payload.mode, suppressed: payload.mode !== 'enabled' });
            if (payload.duration_minutes) item.until = new Date(Date.now() + payload.duration_minutes * 60000).toISOString();
          }
          for (const item of policies) item.parent_suppressed = item.id.includes('.') && policies[0].suppressed;
          return route.fulfill({ json: { items: policies, can_edit: editable } });
        }
        assert.equal(req.method(), 'GET');
        return route.fulfill({ json: url.pathname === '/api/panel-users/options' ? [] : { items: [] } });
      });
      const page = await context.newPage(); page.on('pageerror', error => errors.push(error.message));
      await page.goto(origin + '/screens/incidents/index', { waitUntil: 'load' });
      await page.locator('[data-bs-target="#incidentMonitoringModal"]').click();
      const rows = page.locator('.incident-monitoring-row'); await rows.nth(3).waitFor();
      if (editable) {
        await rows.nth(2).locator('select').selectOption('week'); await rows.nth(2).locator('button').click();
        await page.waitForFunction(() => document.getElementById('incidentMonitoringStatus').textContent.startsWith('Сохранено'));
        assert.deepEqual(writes[0], { id: 'integration_transport.ui_updates', payload: { mode: 'paused', duration_minutes: 10080 } });
        assert.equal(await rows.nth(1).locator('select').inputValue(), 'enabled'); assert.equal(await rows.nth(3).locator('select').inputValue(), 'enabled');
        await rows.nth(3).locator('select').selectOption('disabled'); // Unsaved sibling draft.
        await rows.nth(0).locator('select').selectOption('disabled'); await rows.nth(0).locator('button').click();
        await page.waitForFunction(() => document.querySelectorAll('.incident-monitoring-row')[3].textContent.includes('общим выключателем'));
        assert.equal(await rows.nth(3).locator('select').inputValue(), 'disabled'); assert.equal(policies[3].mode, 'enabled');
        await rows.nth(2).locator('select').selectOption('enabled'); await rows.nth(2).locator('button').click();
        await page.waitForFunction(() => document.getElementById('incidentMonitoringStatus').textContent.endsWith('Обновление открытых страниц панели'));
        assert((await rows.nth(2).textContent()).includes('общим выключателем'));
        await rows.nth(0).locator('select').selectOption('enabled'); await rows.nth(0).locator('button').click();
        await page.waitForFunction(() => !document.querySelectorAll('.incident-monitoring-row')[3].textContent.includes('общим выключателем'));
        failNext = true; await rows.nth(3).locator('select').selectOption('disabled'); await rows.nth(3).locator('button').click();
        await page.waitForFunction(() => document.getElementById('incidentMonitoringStatus').textContent.includes('Тестовая ошибка'));
        assert(!await rows.nth(3).locator('button').isDisabled()); assert.equal(policies[3].mode, 'enabled');
      } else {
        assert.equal(await rows.locator('select:disabled').count(), 4); assert.equal(await rows.locator('button:disabled').count(), 4); assert.deepEqual(writes, []);
      }
      assert(!await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1));
      await page.screenshot({ path: path.join(output, `${theme}-${width}-${editable ? 'admin' : 'reader'}.png`) });
      assert.deepEqual(errors, []); results.push({ theme, width, editable, settingsRequests: writes.length, noBusinessWrites: true }); await context.close();
    }
    fs.writeFileSync(path.join(output, 'report.json'), JSON.stringify(results, null, 2)); console.log(JSON.stringify({ combinations: results.length, screenshots: results.length, output }));
  } finally { await browser.close(); await new Promise(resolve => server.close(resolve)); }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
