'use strict';
const fs = require('node:fs'), path = require('node:path'), http = require('node:http'), assert = require('node:assert/strict');
const fixture = require('../spring-panel/src/test/js/fixtures/incident-concrete-problems-r14.cjs');
const root = path.resolve(__dirname, '..'), resources = path.join(root, 'spring-panel/src/main/resources/static');
const captures = path.join(root, 'spring-panel/target/neutral-ops-rendered'), output = path.join(root, 'spring-panel/target/incidents-r16-qa');
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
      return res.end(html);
    }
    fs.createReadStream(file).pipe(res);
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
  const browser = await chromium.launch({ headless: true }), origin = `http://127.0.0.1:${server.address().port}`, results = [];
  try {
    for (const theme of ['light', 'dark']) for (const width of [320, 390, 768, 1440, 1920]) for (const mode of ['aggregate', 'worker', 'missing', 'noanalytics', 'closed']) {
      const context = await browser.newContext({ viewport: { width, height: 1100 }, colorScheme: theme, reducedMotion: 'reduce' });
      await context.addInitScript(theme => { localStorage.setItem('iguana:theme', theme); localStorage.setItem('iguana:theme-palette', 'neo'); localStorage.setItem('sidebarPinned', '1'); }, theme);
      const aggregate = structuredClone(fixture.incident), overview = structuredClone(fixture.overview);
      const worker = { ...structuredClone(fixture.incident), id: 732, incident_key: 'INC-732', signal_key: 'panel-runtime-checkpoints/ui-event-outbox-watch', events: [{ payload_json: JSON.stringify({ worker_key: 'ui-event-outbox-watch', age_minutes: 2827, stale_threshold_minutes: 10, cursor_lag: 0, unhealthy_streak: 47 }) }] };
      if (mode === 'closed') worker.status = 'closed';
      if (mode === 'missing') overview.runtime_checkpoints = overview.runtime_checkpoints.slice(0, 2);
      if (mode === 'worker') overview.runtime_checkpoints[2].stale = false; // Include matching task even if now healthy.
      const initial = mode === 'aggregate' ? aggregate : worker;
      let release, calls = 0, failRefresh = false; const gate = new Promise(resolve => { release = resolve; });
      const errors = [], writes = [];
      await context.route('**/*', async route => {
        const req = route.request(), url = new URL(req.url()); if (url.origin !== origin) return route.abort();
        if (!url.pathname.startsWith('/api/')) return route.continue();
        if (req.method() !== 'GET') { writes.push(req.method() + ' ' + url.pathname); return route.fulfill({ status: 400, json: {} }); }
        if (url.pathname === '/api/analytics/integration-transport') { calls++; await gate; return route.fulfill(failRefresh ? { status: 503, json: {} } : { json: overview }); }
        if (url.pathname === '/api/incidents') return route.fulfill({ json: { items: [initial, initial === aggregate ? worker : aggregate] } });
        if (url.pathname === '/api/incidents/731') return route.fulfill({ json: { incident: aggregate } });
        if (url.pathname === '/api/incidents/732') return route.fulfill({ json: { incident: worker } });
        return route.fulfill({ json: {} });
      });
      const page = await context.newPage(); page.on('pageerror', error => errors.push(error.message));
      await page.goto(origin + '/screens/incidents/index' + (mode === 'noanalytics' ? '?noanalytics=1' : ''), { waitUntil: 'load' });
      await page.locator('#incidentDetailTitle').waitFor(); await page.locator('#incidentDetailTitle').fill('Сохранить черновик'); release();
      const current = page.locator('#incidentCurrentProblems'), saved = page.locator('.incident-explanation');
      if (mode === 'aggregate') {
        await page.waitForFunction(() => document.getElementById('incidentCurrentProblems')?.textContent.includes('Состояние очереди и фоновых задач'));
        const content = await current.textContent(); assert(!content.includes('24.08.2026')); assert(!content.includes('chat_attachment_metadata')); assert(!content.includes('Сообщение клиента в существующее обращение'));
        assert(!await saved.locator('.incident-saved-counters').evaluate(node => node.open));
      } else {
        assert((await saved.textContent()).includes('UiEventOutboxWatcher'));
        assert((await saved.textContent()).includes('1 д 23 ч 7 мин'));
        assert((await saved.textContent()).includes('более новых записей нет'));
        if (mode === 'noanalytics' || mode === 'closed') {
          await page.waitForTimeout(60); assert.equal((await current.textContent()).trim(), '');
          if (mode === 'noanalytics') assert.equal(calls, 0);
        } else {
          await page.waitForFunction(() => document.getElementById('incidentCurrentProblems')?.textContent.includes('Данные загружены:'));
          const content = await current.textContent(); assert(content.includes('ui-event-outbox-watch'));
          assert(!content.includes('operator-notification-watch')); assert(!content.includes('client_message')); assert(!content.includes('Сообщение клиента в существующее обращение'));
          if (mode === 'missing') assert(content.includes('другие задачи не подставляются'));
          else {
            assert(content.includes('UiEventOutboxWatcher')); assert(content.includes('более новых нет'));
            await current.locator('[data-incident-diagnostics-target="tasks"]').click();
            await page.locator('#incidentWorkbenchTransportPane').waitFor({ state: 'visible' });
            assert.equal(await page.evaluate(() => document.activeElement.getAttribute('data-transport-checkpoint-input')), 'ui-event-outbox-watch');
            failRefresh = true; await page.locator('#transportWorkbenchRefresh').click();
            await page.waitForFunction(() => document.getElementById('incidentCurrentProblems')?.textContent.includes('Обновить данные не удалось'));
            await page.locator('#incidentWorkbenchIncidentsTab').click(); await current.waitFor({ state: 'visible' });
          }
        }
      }
      if (['aggregate', 'worker', 'missing'].includes(mode)) assert(await page.evaluate(() => document.getElementById('incidentCurrentProblems').compareDocumentPosition(document.querySelector('.incident-explanation')) & Node.DOCUMENT_POSITION_FOLLOWING));
      assert.equal(await page.locator('#incidentDetailTitle').inputValue(), 'Сохранить черновик');
      assert(!await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1), `${theme}/${width}/${mode} overflow`);
      if (mode === 'aggregate' || mode === 'worker') { await current.scrollIntoViewIfNeeded(); await page.screenshot({ path: path.join(output, `${mode}-${theme}-${width}.png`) }); }
      assert.deepEqual(writes, []); assert.deepEqual(errors, []);
      results.push({ theme, width, mode, calls, draftPreserved: true, noWrites: true }); await context.close();
    }
    fs.writeFileSync(path.join(output, 'report.json'), JSON.stringify(results, null, 2));
    console.log(JSON.stringify({ combinations: results.length, screenshots: 20, output }));
  } finally { await browser.close(); await new Promise(resolve => server.close(resolve)); }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
