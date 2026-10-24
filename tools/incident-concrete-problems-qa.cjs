/* Read-only fixture: existing analytics GET only; no replay/requeue or production writes. */
'use strict';
const fs = require('node:fs'), path = require('node:path'), http = require('node:http'), assert = require('node:assert/strict');
const fixtures = require('../spring-panel/src/test/js/fixtures/incident-concrete-problems-r14.cjs');
const root = path.resolve(__dirname, '..'), resources = path.join(root, 'spring-panel/src/main/resources/static');
const captures = path.join(root, 'spring-panel/target/neutral-ops-rendered'), output = path.join(root, 'spring-panel/target/incidents-r14-qa');
async function main() {
  fs.mkdirSync(output, { recursive: true });
  const server = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://localhost'), screen = url.pathname.startsWith('/screens/');
    const base = screen ? captures : resources, file = path.resolve(base, screen ? url.pathname.slice(9) + '.html' : url.pathname.slice(1));
    if (!file.startsWith(base + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) { res.writeHead(404); res.end('{}'); return; }
    res.setHeader('Content-Type', ({ '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml' })[path.extname(file)] || 'application/octet-stream');
    if (screen && url.searchParams.has('noanalytics')) {
      let html = fs.readFileSync(file, 'utf8'); const anchor = '<script src="/vendor/bootstrap/'; assert(html.includes(anchor));
      html = html.replace(anchor, '<script>document.getElementById("incidentWorkbenchTransportPane").remove();document.getElementById("incidentWorkbenchTransportTab").remove();</script>' + anchor);
      res.end(html); return;
    }
    fs.createReadStream(file).pipe(res);
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
  const browser = await chromium.launch({ headless: true }), origin = `http://127.0.0.1:${server.address().port}`, results = [];
  try {
    for (const theme of ['light', 'dark']) for (const width of [320, 390, 768, 1440, 1920]) for (const mode of ['active', 'noanalytics', 'closed']) {
      const context = await browser.newContext({ viewport: { width, height: 1100 }, colorScheme: theme, hasTouch: width <= 390, isMobile: width <= 390, reducedMotion: 'reduce' });
      await context.addInitScript(theme => { localStorage.setItem('iguana:theme', theme); localStorage.setItem('iguana:theme-palette', 'neo'); localStorage.setItem('sidebarPinned', '1'); }, theme);
      const incident = structuredClone(fixtures.incident), overview = structuredClone(fixtures.overview);
      if (mode === 'closed') incident.status = 'closed';
      overview.recent_failed_inbound[3].event_id = '<img src=x onerror="window.r14Injection=true">';
      const errors = [], writes = []; let overviewCalls = 0, failRefresh = false, release;
      const gate = new Promise(resolve => { release = resolve; });
      await context.route('**/*', async route => {
        const req = route.request(), url = new URL(req.url()); if (url.origin !== origin) return route.abort();
        if (!url.pathname.startsWith('/api/')) return route.continue();
        if (req.method() !== 'GET') { writes.push(req.method() + ' ' + url.pathname); return route.fulfill({ status: 400, json: {} }); }
        if (url.pathname === '/api/analytics/integration-transport') { overviewCalls++; await gate; return route.fulfill(failRefresh ? { status: 503, json: { message: 'Fixture unavailable' } } : { json: overview }); }
        if (url.pathname === '/api/incidents') return route.fulfill({ json: { items: [incident] } });
        if (url.pathname === '/api/incidents/731') return route.fulfill({ json: { incident } });
        return route.fulfill({ json: {} });
      });
      const page = await context.newPage(); page.on('pageerror', ex => errors.push(ex.message));
      await page.goto(origin + '/screens/incidents/index' + (mode === 'noanalytics' ? '?noanalytics=1' : ''), { waitUntil: 'load' });
      await page.locator('#incidentDetailTitle').waitFor(); await page.locator('#incidentDetailTitle').fill('Несохранённый черновик');
      release();
      if (mode === 'active') {
        await page.waitForFunction(() => document.getElementById('incidentCurrentProblems')?.textContent.includes('сведения о вложении'));
        const current = page.locator('#incidentCurrentProblems'), text = await current.textContent();
        assert(text.includes('Сообщение клиента в существующее обращение — 4')); assert(text.includes('24.08.2026'));
        assert(text.includes('Старые записи с ошибкой не означают новые сбои сегодня'));
        assert(text.includes('Уведомления операторам о новых сообщениях')); assert(text.includes('об оценках клиентов')); assert(text.includes('Обновление данных на открытых страницах'));
        assert.equal((text.match(/более новых нет/g) || []).length, 2);
        assert(text.includes(' T/№9007199254740993123 '));
        assert.equal(await current.locator('img, script').count(), 0); assert.equal(await page.evaluate(() => window.r14Injection), undefined);
        assert.equal(await page.locator('#incidentDetailTitle').inputValue(), 'Несохранённый черновик', 'Async overview never resets editable fields');
        assert((await page.locator('.incident-explanation').textContent()).includes('10 проверках подряд'));
        await current.locator('[data-incident-diagnostics-target="tasks"]').click();
        await page.locator('#incidentWorkbenchTransportPane').waitFor({ state: 'visible' });
        assert.equal(await page.evaluate(() => document.activeElement.id), 'transportWorkbenchCheckpointList');
        failRefresh = true; await page.locator('#transportWorkbenchRefresh').click();
        await page.waitForFunction(() => document.getElementById('incidentCurrentProblems').textContent.includes('Обновить данные не удалось'));
        await page.locator('#incidentWorkbenchIncidentsTab').click(); await current.waitFor({ state: 'visible' });
        assert((await current.textContent()).includes('сведения о вложении'), 'Failed refresh retains explicitly dated previous data');
        assert.equal(await page.locator('#incidentDetailTitle').inputValue(), 'Несохранённый черновик');
        await current.scrollIntoViewIfNeeded(); await page.screenshot({ path: path.join(output, `current-${theme}-${width}.png`) });
      } else {
        await page.waitForTimeout(100);
        assert.equal((await page.locator('#incidentCurrentProblems').textContent()).trim(), '', mode + ' does not show current diagnostics as historical facts');
        if (mode === 'noanalytics') assert.equal(overviewCalls, 0, 'No new analytics access or fetch for denied role');
        if (mode === 'closed') assert((await page.locator('.incident-explanation').textContent()).includes('не текущее состояние'));
      }
      assert(!await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1));
      assert.deepEqual(writes, []); assert.deepEqual(errors, []);
      results.push({ theme, width, mode, overviewCalls, noWrites: true, draftPreserved: true, errors });
      await context.close();
    }
    fs.writeFileSync(path.join(output, 'report.json'), JSON.stringify(results, null, 2));
    console.log(JSON.stringify({ combinations: results.length, screenshots: results.filter(item => item.mode === 'active').length, output }));
  } finally { await browser.close(); await new Promise(resolve => server.close(resolve)); }
}
main().catch(ex => { console.error(ex); process.exitCode = 1; });
