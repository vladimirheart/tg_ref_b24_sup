/* R17: failed records stay in read-only diagnostics, not incident cards. */
'use strict';
const fs = require('node:fs'), path = require('node:path'), http = require('node:http'), assert = require('node:assert/strict');
const fixture = require('../spring-panel/src/test/js/fixtures/incident-concrete-problems-r14.cjs');
const root = path.resolve(__dirname, '..'), resources = path.join(root, 'spring-panel/src/main/resources/static');
const captures = path.join(root, 'spring-panel/target/neutral-ops-rendered'), output = path.join(root, 'spring-panel/target/incidents-r17-qa');
async function main() {
  fs.mkdirSync(output, { recursive: true });
  const server = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://localhost'), screen = url.pathname.startsWith('/screens/');
    const base = screen ? captures : resources, file = path.resolve(base, screen ? url.pathname.slice(9) + '.html' : url.pathname.slice(1));
    if (!file.startsWith(base + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) { res.writeHead(404); return res.end('{}'); }
    res.setHeader('Content-Type', ({ '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml' })[path.extname(file)] || 'application/octet-stream');
    fs.createReadStream(file).pipe(res);
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
  const browser = await chromium.launch({ headless: true }), origin = `http://127.0.0.1:${server.address().port}`, results = [];
  try {
    for (const theme of ['light', 'dark']) for (const width of [320, 390, 768, 1440, 1920]) for (const key of ['panel-rabbitmq-bridge', 'panel-transport-sustained-pressure']) {
      const context = await browser.newContext({ viewport: { width, height: 1100 }, colorScheme: theme, reducedMotion: 'reduce' });
      await context.addInitScript(theme => { localStorage.setItem('iguana:theme', theme); localStorage.setItem('iguana:theme-palette', 'neo'); localStorage.setItem('sidebarPinned', '1'); }, theme);
      const incident = structuredClone(fixture.incident), overview = structuredClone(fixture.overview);
      incident.signal_key = key; incident.severity = 'critical';
      incident.events = [{ payload_json: JSON.stringify({ inbound_failed: 4, outbound_failed: 0, latest_summary: 'inbound_failed=4, outbound_failed=0', unhealthy_streak: 47 }) }];
      overview.runtime_checkpoints = []; overview.health_snapshot.stale_checkpoint_count = 0;
      const ids = ['03298e2e-8d69-4586-84d6-447a0897b3f7', 'fed9a9db-c5a9-4dc4-8117-e78157b4ec04', '027d030b-9675-4daa-b287-31b75ae04d6c', 'acb203fa-571d-4a4e-922e-c4d0ac6bd1e8'];
      overview.recent_failed_inbound.forEach((record, i) => { record.event_id = ids[i]; record.ticket_id = 'f5b6bedf-dfac-4683-940c-5617b57314fe'; });
      const writes = [], errors = [], original = JSON.stringify(overview);
      await context.route('**/*', async route => {
        const req = route.request(), url = new URL(req.url()); if (url.origin !== origin) return route.abort();
        if (!url.pathname.startsWith('/api/')) return route.continue();
        if (req.method() !== 'GET') { writes.push(req.method() + ' ' + url.pathname); return route.fulfill({ status: 400, json: {} }); }
        if (url.pathname === '/api/analytics/integration-transport') return route.fulfill({ json: overview });
        if (url.pathname === '/api/incidents') return route.fulfill({ json: { items: [incident] } });
        if (url.pathname === '/api/incidents/731') return route.fulfill({ json: { incident } });
        return route.fulfill({ json: {} });
      });
      const page = await context.newPage(); page.on('pageerror', error => errors.push(error.message));
      await page.goto(origin + '/screens/incidents/index', { waitUntil: 'load' });
      await page.waitForFunction(() => document.getElementById('incidentCurrentProblems')?.textContent.includes('нет системных условий'));
      const pane = page.locator('#incidentWorkbenchIncidentsPane'), current = page.locator('#incidentCurrentProblems');
      const text = await pane.textContent();
      for (const forbidden of [...ids, 'f5b6bedf-dfac-4683-940c-5617b57314fe', 'chat_attachment_metadata', 'Сообщение клиента в существующее обращение — 4']) assert(!text.includes(forbidden), forbidden);
      assert((await page.locator('.incident-explanation').textContent()).includes('не являются основанием автоматического инцидента'));
      assert(!await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1));
      await current.scrollIntoViewIfNeeded(); await page.screenshot({ path: path.join(output, `${key}-${theme}-${width}.png`) });
      await current.locator('[data-incident-diagnostics-target="inbound"]').click();
      await page.locator('#incidentWorkbenchTransportPane').waitFor({ state: 'visible' });
      const diagnostic = await page.locator('#transportWorkbenchInboundList').textContent();
      for (const expected of [...ids, 'f5b6bedf-dfac-4683-940c-5617b57314fe', 'chat_attachment_metadata']) assert(diagnostic.includes(expected), expected);
      assert.equal(JSON.stringify(overview), original); assert.deepEqual(writes, []); assert.deepEqual(errors, []);
      results.push({ theme, width, key, incidentRecordsHidden: true, diagnosticRecordsPreserved: true, noWrites: true });
      await context.close();
    }
    fs.writeFileSync(path.join(output, 'report.json'), JSON.stringify(results, null, 2));
    console.log(JSON.stringify({ combinations: results.length, screenshots: results.length, output }));
  } finally { await browser.close(); await new Promise(resolve => server.close(resolve)); }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
