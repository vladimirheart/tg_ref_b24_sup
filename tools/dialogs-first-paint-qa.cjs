/* Captured MVC HTML and real local assets; delayed, read-only fixture API on loopback. */
'use strict';
const fs = require('node:fs'), path = require('node:path'), http = require('node:http'), assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..'), assets = path.join(root, 'spring-panel/src/main/resources/static');
const output = path.join(root, 'spring-panel/target/dialogs-pagination-qa');
const html = fs.readFileSync(path.join(output, 'dialogs.html'), 'utf8').replace('window.__IGUANA_UI_PREFS_BOOTSTRAP__ = {};',
  'window.__IGUANA_UI_PREFS_BOOTSTRAP__ = { dialogsColumnWidths: { problem: 360 } };');
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

async function main() {
  const server = http.createServer((req, res) => {
    const file = path.resolve(assets, new URL(req.url, 'http://localhost').pathname.slice(1));
    if (!file.startsWith(assets + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) {
      res.writeHead(404); res.end('{}'); return;
    }
    res.setHeader('Content-Type', ({ '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml' })[path.extname(file)] || 'application/octet-stream');
    // Git's Windows CRLF checkout must not invalidate the vendored Bootstrap SRI.
    if (/\.(js|css)$/.test(file)) res.end(fs.readFileSync(file, 'utf8').replace(/\r\n/g, '\n'));
    else fs.createReadStream(file).pipe(res);
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
  const browser = await chromium.launch({ headless: true,
    ...(process.env.PLAYWRIGHT_BROWSER_EXECUTABLE ? { executablePath: process.env.PLAYWRIGHT_BROWSER_EXECUTABLE } : {}) });
  const origin = `http://127.0.0.1:${server.address().port}`, results = [];
  try {
    for (const scenario of ['bootstrap', 'slow-preferences', 'failed-query']) {
      const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, reducedMotion: 'reduce' });
      const requests = [], writes = [], errors = [];
      await context.addInitScript(() => {
        localStorage.setItem('iguana:dialogs:columns', JSON.stringify({ location: false, categories: false }));
        localStorage.setItem('iguana:dialogs:column-order', JSON.stringify(['problem', 'client', 'actions']));
        localStorage.setItem('iguana:dialogs:columns-schema-version', '2');
        localStorage.setItem('iguana:dialogs:column-order-schema-version', '2');
        localStorage.setItem('iguana:dialogs:list-only-mode', 'true');
        localStorage.setItem('iguana:dialogs:column-widths-v2', JSON.stringify({ problem: 360 }));
        window.__firstPaintFrames = [];
        function sample() {
          const table = document.getElementById('dialogsTable');
          if (table?.tHead?.rows[0]?.cells.length === 13 && document.getElementById('dialogPageSize')) {
            const rows = Array.from(table.querySelectorAll('tbody tr[data-ticket-id]'));
            if (!window.__ssrRows && rows.length) window.__ssrRows = rows;
            const cell = table.querySelector('th[data-column-key="location"]');
            window.__firstPaintFrames.push({ visible: getComputedStyle(table).visibility !== 'hidden', rows: rows.length,
              pending: document.documentElement.classList.contains('dialogs-list-preparing'),
              pageSizeVisible: getComputedStyle(document.getElementById('dialogPageSize')).visibility !== 'hidden',
              locationHidden: cell && getComputedStyle(cell).display === 'none', firstColumn: table.tHead.rows[0].cells[0].dataset.columnKey,
              width: table.querySelector('th[data-column-key="problem"]').style.width,
              listOnly: document.body.classList.contains('dialog-list-only-mode') });
          }
          requestAnimationFrame(sample);
        }
        requestAnimationFrame(sample);
      });
      await context.route('**/*', async route => {
        const req = route.request(), url = new URL(req.url());
        assert.equal(url.origin, origin, 'Fixture must never contact an external service');
        if (url.pathname === '/dialogs') {
          const body = scenario === 'bootstrap' ? html : html.replace(
            /window\.DIALOG_LIST_BOOTSTRAP\s*=\s*\{[\s\S]*?\};/,
            'window.DIALOG_LIST_BOOTSTRAP = { query: null, preferences: null };');
          if (scenario !== 'bootstrap') assert.notEqual(body, html, 'Bootstrap replacement must be effective');
          return route.fulfill({ body, contentType: 'text/html; charset=utf-8' });
        }
        if (url.pathname === '/js/dialogs.js') { await sleep(600); return route.continue(); }
        if (!url.pathname.startsWith('/api/')) return route.continue();
        if (url.pathname === '/api/dialogs/query') {
          requests.push(req.postDataJSON()); await sleep(350);
          if (scenario === 'failed-query') return route.fulfill({ status: 500, json: { success: false } });
          return route.fulfill({ json: { success: true, dialogs: [], pagination: {
            page: 1, page_size: 50, total_items: 0, total_pages: 0 }, sla_orchestration: { tickets: {} } } });
        }
        if (url.pathname === '/api/dialogs/triage-preferences') {
          if (req.method() === 'POST') writes.push(req.postDataJSON());
          await sleep(700);
          return route.fulfill({ json: { success: true, preferences: { view: 'active', page_size: '50',
            sort_mode: 'sla_priority', sla_window_minutes: 30 } } });
        }
        if (req.method() !== 'GET' && url.pathname !== '/api/dialogs/workspace-telemetry') throw Error('Unexpected mutation ' + url.pathname);
        return route.fulfill({ json: { success: true, items: [], templates: [], notifications: [], dialogs: [], permissions: {} } });
      });
      const page = await context.newPage(); page.on('pageerror', error => errors.push(error.message));
      for (const navigation of ['open', 'reload']) {
        requests.length = 0; writes.length = 0;
        await (navigation === 'open' ? page.goto(origin + '/dialogs', { waitUntil: 'commit' }) : page.reload({ waitUntil: 'commit' }));
        await page.waitForFunction(() => document.querySelector('#dialogsTable tbody tr[data-ticket-id]'));
        const preparing = await page.evaluate(() => ({ pending: document.documentElement.classList.contains('dialogs-list-preparing'),
          visibility: getComputedStyle(document.getElementById('dialogsTable')).visibility,
          cardVisibility: getComputedStyle(document.querySelector('.dialogs-table-card > .card-body')).visibility,
          primary: window.__dialogsPrimaryReady, errors: window.__firstPaintFrames.length }));
        assert(preparing.pending && preparing.visibility === 'hidden', JSON.stringify(preparing));
        await page.waitForFunction(() => window.__dialogsPrimaryReady && !document.documentElement.classList.contains('dialogs-list-preparing'));
        await page.waitForTimeout(650);
        const frames = await page.evaluate(() => window.__firstPaintFrames);
        fs.writeFileSync(path.join(output, `first-paint-debug-${scenario}-${navigation}.json`), JSON.stringify({ frames,
          errors, state: await page.evaluate(() => ({ columns: localStorage.getItem('iguana:dialogs:columns'),
            order: localStorage.getItem('iguana:dialogs:column-order'), primary: window.__dialogsPrimaryReady })) }, null, 2));
        assert(frames.some(frame => !frame.visible), 'Should observe the preparation state');
        assert(frames.filter(frame => frame.pending).every(frame => !frame.pageSizeVisible), 'Pagination must wait for saved settings too');
        const visible = frames.filter(frame => frame.visible);
        assert(visible.length > 0);
        for (const frame of visible) {
          assert(frame.locationHidden, 'No visible default columns');
          assert.equal(frame.firstColumn, 'problem', 'Saved column order must apply before first paint');
          assert.equal(frame.width, '360px', 'Saved width must apply before first paint');
          assert(frame.listOnly, 'Saved list-only mode must apply before first paint');
        }
        if (scenario === 'bootstrap') {
          assert(visible.every(frame => frame.rows === 20), 'SSR rows must never disappear after being shown');
          assert(await page.evaluate(() => window.__ssrRows.every(row => row.isConnected)), 'Preserve matching SSR rows');
          assert.equal(requests.length, 0, 'No duplicate initial list query');
        } else {
          assert.equal(requests.length, 1, 'Exactly one query after preferences');
          assert.equal(requests[0].query.pageSize, 50); assert.equal(requests[0].query.view, 'active');
          assert.equal(requests[0].query.sortMode, 'sla_priority'); assert.equal(requests[0].query.slaWindowMinutes, 30);
          assert(visible.every(frame => frame.rows === 0), 'Default rows must never appear for another filter');
          if (scenario === 'failed-query') assert((await page.locator('#dialogPageState').textContent()).includes('Не удалось'));
        }
        assert.equal(writes.length, 0, 'Opening/reloading must not save preferences');
        assert.deepEqual(errors, []);
        await page.screenshot({ path: path.join(output, `first-paint-${scenario}-${navigation}.png`), fullPage: true });
        results.push({ scenario, navigation, frames: frames.length, visibleFrames: visible.length, listRequests: requests.length, preferenceWrites: writes.length });
      }
      await context.close();
    }
    fs.writeFileSync(path.join(output, 'first-paint-report.json'), JSON.stringify({ status: 'GREEN', results }, null, 2));
    console.log('GREEN: 6 startup/reload scenarios; delayed bundles, saved layout, matching SSR reuse, delayed preferences, failed first query; no startup preference writes.');
  } finally { await browser.close(); await new Promise(resolve => server.close(resolve)); }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
