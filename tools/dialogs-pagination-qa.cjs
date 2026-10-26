/* Real MVC HTML + real local assets; synthetic read-only API, never contacts the working panel. */
'use strict';
const fs = require('node:fs'), path = require('node:path'), http = require('node:http'), assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..'), resources = path.join(root, 'spring-panel/src/main/resources/static');
const output = path.join(root, 'spring-panel/target/dialogs-pagination-qa');
const items = Array.from({ length: 10000 }, (_, i) => {
  const n = 10000 - i, id = `T-${String(n).padStart(5, '0')}`;
  return { ticketId: id, requestNumber: `20261026-${String(n).padStart(3, '0')}`, userId: 100000 + n,
    username: `client${n}`, clientName: `Клиент ${n}`, business: 'Поддержка', channelId: 1, channelName: 'Telegram',
    city: 'Город', locationName: 'Ресторан', problem: `Проблема ${n}`, createdAt: '2026-10-26T09:00:00Z',
    createdDate: '2026-10-26', createdTime: '09:00:00', status: 'open', statusKey: 'new', statusLabel: 'новый',
    statusClass: 'dialog-status-badge status-new', rawResponsible: '', responsible: '', unreadCount: 1, categories: 'Техническая поддержка' };
});

async function main() {
  assert(fs.existsSync(path.join(output, 'dialogs.html')), 'Run DialogsControllerWebMvcTest with -Diguana.dialogs.capture=... first');
  const server = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://localhost');
    const file = url.pathname === '/dialogs' ? path.join(output, 'dialogs.html') : path.resolve(resources, url.pathname.slice(1));
    if (!(file.startsWith(resources + path.sep) || file === path.join(output, 'dialogs.html')) || !fs.existsSync(file) || !fs.statSync(file).isFile()) {
      res.writeHead(404); res.end('{}'); return;
    }
    res.setHeader('Content-Type', ({ '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml' })[path.extname(file)] || 'application/octet-stream');
    fs.createReadStream(file).pipe(res);
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
  const browser = await chromium.launch({ headless: true }), origin = `http://127.0.0.1:${server.address().port}`;
  const results = [];
  try {
    for (const theme of ['light', 'dark']) for (const width of [390, 1440]) {
      const context = await browser.newContext({ viewport: { width, height: 1000 }, colorScheme: theme, reducedMotion: 'reduce' });
      await context.addInitScript(theme => {
        localStorage.setItem('iguana:theme', theme); localStorage.setItem('iguana:theme-palette', 'neo');
        localStorage.setItem('iguana:dialogs:page-size', 'all');
      }, theme);
      const requests = [], errors = [], external = [];
      let rejectNext = false;
      await context.route('**/*', async route => {
        const req = route.request(), url = new URL(req.url());
        if (url.origin !== origin) { external.push(url.origin); return route.abort(); }
        if (!url.pathname.startsWith('/api/')) return route.continue();
        if (url.pathname === '/api/dialogs/query') {
          assert.equal(req.method(), 'POST');
          const body = req.postDataJSON(), q = body.query; requests.push(body);
          assert(q.pageSize > 0 && q.pageSize <= 100);
          if (rejectNext) { rejectNext = false; return route.fulfill({ status: 500, json: { success: false } }); }
          let filtered = items.filter(item => !q.search || (item.ticketId + ' ' + item.requestNumber + ' ' + item.clientName + ' ' + item.problem)
            .toLowerCase().includes(q.search.toLowerCase()));
          if (q.view !== 'all') filtered = filtered.filter(item => !q.snoozedTicketIds.includes(item.ticketId));
          const total = filtered.length, page = Math.max(1, Math.min(q.page, Math.ceil(total / q.pageSize) || 1));
          const payload = { success: true, dialogs: filtered.slice((page - 1) * q.pageSize, page * q.pageSize),
            pagination: { page, page_size: q.pageSize, total_items: total, total_pages: Math.ceil(total / q.pageSize) },
            sla_orchestration: { tickets: {} } };
          if (body.includeOverview) Object.assign(payload, {
            summary: { totalTickets: 10000, resolvedTickets: 0, pendingTickets: 10000, channelStats: [] },
            my_dialogs: { new: items.slice(0, 20), unanswered: [], in_work: [], counts: { new: 10000, unanswered: 0, in_work: 0 }, limit: 20 },
          });
          if (q.search === 'slow') await new Promise(resolve => setTimeout(resolve, 600));
          try { return await route.fulfill({ json: payload }); } catch { return; } // A superseded request may already be aborted.
        }
        if (url.pathname === '/api/dialogs/workspace-telemetry') return route.fulfill({ json: { success: true } });
        if (req.method() !== 'GET' && url.pathname !== '/api/dialogs/triage-preferences') throw Error('Unrelated mutation: ' + url.pathname);
        if (url.pathname === '/api/dialogs/triage-preferences') return route.fulfill({ json: { success: true, preferences: {} } });
        return route.fulfill({ json: { success: true, items: [], templates: [], notifications: [], dialogs: [], permissions: {} } });
      });
      const page = await context.newPage(); page.on('pageerror', error => errors.push(error.message));
      await page.goto(origin + '/dialogs');
      const rows = page.locator('#dialogsTable tbody tr[data-ticket-id]');
      await page.waitForFunction(() => document.getElementById('dialogPageState').textContent.includes('10000'));
      assert.equal(await rows.count(), 20);
      assert.equal(await page.locator('#dialogPageSize option[value=all]').count(), 0);
      assert((await page.locator('#dialogMyDialogsCount').textContent()).includes('10000'));
      await page.screenshot({ path: path.join(output, `first-${theme}-${width}.png`), fullPage: true });

      await page.locator('#dialogPageNext').click();
      await page.waitForFunction(() => document.getElementById('dialogPageState').textContent.includes('стр. 2/500'));
      assert.equal(await rows.count(), 20); assert.equal(await rows.first().getAttribute('data-ticket-id'), 'T-09980');
      assert.equal(requests.at(-1).includeOverview, false, 'Page switch must not rebuild overview');
      assert((await page.locator('#dialogMyDialogsCount').textContent()).includes('10000'));

      await page.locator('#dialogPageSize').selectOption('100');
      await page.waitForFunction(() => document.querySelectorAll('#dialogsTable tbody tr[data-ticket-id]').length === 100);
      assert.equal(requests.at(-1).query.page, 1);
      assert.equal(await page.locator('#dialogsTable tr[data-ticket-id].d-none').count(), 0);

      await page.locator('#dialogQuickSearch').fill('20261026-001');
      await page.waitForFunction(() => document.querySelectorAll('#dialogsTable tbody tr[data-ticket-id]').length === 1);
      assert.equal(await rows.first().getAttribute('data-ticket-id'), 'T-00001', 'Search must find a record far beyond page 1');
      assert((await page.locator('#dialogMyDialogsCount').textContent()).includes('10000'));
      await page.screenshot({ path: path.join(output, `search-${theme}-${width}.png`), fullPage: true });

      await page.locator('#dialogQuickSearch').fill('slow');
      await page.waitForTimeout(260); // Let the slow response start, then supersede it.
      await page.locator('#dialogQuickSearch').fill('T-00002');
      await page.waitForFunction(() => document.querySelector('#dialogsTable tr[data-ticket-id]')?.dataset.ticketId === 'T-00002');
      await page.waitForTimeout(650);
      assert.equal(await rows.first().getAttribute('data-ticket-id'), 'T-00002', 'Slow response cannot replace a newer result');

      rejectNext = true;
      await page.locator('#dialogQuickSearch').fill('missing');
      await page.waitForFunction(() => document.getElementById('dialogPageState').textContent.includes('Не удалось'));
      assert.equal(await rows.count(), 0, 'Failed query must not leave actionable rows from another filter');
      await page.locator('#dialogQuickSearch').fill('T-00003');
      await page.waitForFunction(() => document.querySelector('#dialogsTable tr[data-ticket-id]')?.dataset.ticketId === 'T-00003');

      const before = requests.length;
      await page.evaluate(() => {
        for (let n = 0; n < 5; n++) window.dispatchEvent(new CustomEvent('panel:sse:dialogs_changed', { detail: { ticketId: 'T-00003' } }));
      });
      await page.waitForTimeout(500);
      assert.equal(requests.length, before + 1, 'SSE burst coalesces into one bounded refresh');
      assert.equal(requests.at(-1).includeOverview, true);
      assert.equal(requests.at(-1).query.search, 'T-00003');
      assert.equal(await rows.count(), 1);
      assert.deepEqual(errors, []); assert.deepEqual(external, []);
      results.push({ theme, width, requests: requests.length, maxRows: 100, errors, external });
      await context.close();
    }
    fs.writeFileSync(path.join(output, 'browser-report.json'), JSON.stringify({ status: 'GREEN', contexts: results }, null, 2));
    console.log('GREEN: 4 browser contexts; server pages, global counts, literal cross-page search, stale-response race, error/retry and SSE; 8 screenshots.');
  } finally { await browser.close(); await new Promise(resolve => server.close(resolve)); }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
