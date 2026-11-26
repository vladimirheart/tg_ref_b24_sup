/* Actual MVC dialogs page, production Bootstrap/JS/SCSS. Loopback API fixtures; no live requests or messages. */
const fs = require('node:fs'), path = require('node:path'), http = require('node:http'), assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..'), assets = path.join(root, 'spring-panel/src/main/resources/static');
const out = path.join(root, 'spring-panel/target/dialog-layout-qa'), capture = path.join(out, 'dialogs.html');
const raw = '3350bdc8-8a87-3b53-8047-bdc3eb89954a', publicId = 'a'.repeat(32), big = '9007199254740995';
const owner = { userId: '9007199254740993', ticketId: raw, ticketPublicId: publicId, businessId: '11' };
const item = { ticketId: raw, ticketPublicId: publicId, businessId: 11, scopeKind: 'BUSINESS', requestNumber: '20261126-001',
  userId: 1234, clientName: 'Клиент', username: 'client', city: 'Москва', locationName: 'Кафе Чуйкова', business: 'Блинбери',
  channelId: 3, channelName: 'Telegram', status: 'open', statusKey: 'in_work', statusLabel: 'в работе',
  createdAt: '2026-11-26T08:00:00Z', createdDate: '2026-11-26', createdTime: '11:00', rawResponsible: 'operator', responsible: 'Оператор',
  problem: 'Тестовая проблема. ' + 'Длинное описание обращения и сведения клиента. '.repeat(12), categories: 'Технические вопросы, Дополнительные сведения' };
async function main() {
  assert(fs.existsSync(capture), 'Run DialogsControllerWebMvcTest capture first');
  const server = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://localhost'), file = url.pathname === '/dialogs' ? capture : path.resolve(assets, url.pathname.slice(1));
    if ((file !== capture && !file.startsWith(assets + path.sep)) || !fs.existsSync(file) || !fs.statSync(file).isFile()) { res.writeHead(404); return res.end('{}'); }
    const ext = path.extname(file); res.setHeader('Content-Type', ({ '.html': 'text/html; charset=utf-8', '.js': 'application/javascript', '.css': 'text/css', '.woff2': 'font/woff2' })[ext] || 'application/octet-stream');
    if (['.html', '.js', '.css'].includes(ext)) res.end(fs.readFileSync(file, 'utf8').replaceAll('\r\n', '\n')); else fs.createReadStream(file).pipe(res);
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve)); const origin = 'http://127.0.0.1:' + server.address().port;
  const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
  const browser = await chromium.launch({ headless: true, ...(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {}) });
  const report = [];
  try {
    for (const theme of ['light', 'dark']) for (const [width, height] of [[1280, 360], [1280, 540], [1920, 700], [768, 480], [390, 640], [1440, 1000]]) {
      console.log(`Checking ${theme} ${width}x${height}`);
      const context = await browser.newContext({ viewport: { width, height }, colorScheme: theme, reducedMotion: 'reduce' });
      await context.addInitScript(theme => { localStorage.setItem('iguana:theme', theme); localStorage.setItem('iguana:theme-palette', 'north'); }, theme);
      let revision = 7, canManage = true, conflict = false, detailReads = 0, legacyReads = 0;
      let members = Array.from({ length: 12 }, (_, n) => ({ id: String(n + 2), username: 'member' + (n + 2), snapshot: 'Участник ' + (n + 2), eligible: true }));
      const candidates = [{ userId: big, username: 'new-employee' }, ...Array.from({ length: 30 }, (_, n) => ({ userId: String(n + 100), username: 'employee' + (n + 100) }))];
      const writes = [], errors = [], missing = [];
      await context.route('**/*', async route => {
        const req = route.request(), url = new URL(req.url());
        if (url.origin !== origin) return route.abort();
        if (!url.pathname.startsWith('/api/') && !url.pathname.startsWith('/profile/')) return route.continue();
        const rosterPath = `/api/business-scope/tickets/${publicId}/participants`;
        if (url.pathname === rosterPath + '/management') return route.fulfill({ json: { owner, revision: String(revision), canManage, candidates: canManage ? candidates : [] } });
        if (url.pathname === rosterPath && req.method() === 'GET') return route.fulfill({ json: { owner, revision: String(revision), members } });
        if (url.pathname === rosterPath) {
          const body = req.postDataJSON(); writes.push({ method: req.method(), body });
          assert.deepEqual(Object.fromEntries(Object.keys(owner).map(key => [key, body[key]])), owner);
          assert.equal(typeof body.memberId, 'string'); assert.equal(body.revision, String(revision));
          assert(canManage); if (conflict) return route.fulfill({ status: 409, json: {} });
          if (req.method() === 'POST') members.push({ id: body.memberId, username: 'new-employee', snapshot: 'Новый сотрудник', eligible: true });
          else if (req.method() === 'DELETE') members = members.filter(member => member.id !== body.memberId);
          else throw new Error('Unexpected participant mutation');
          revision++; return route.fulfill({ status: 204 });
        }
        if (url.pathname === '/api/dialog-ticket/participants') { legacyReads++; return route.fulfill({ status: 404, json: { success: false, error: 'Диалог не найден' } }); }
        if (url.pathname === '/api/dialog-ticket') {
          detailReads++;
          return route.fulfill({ json: { success: true, summary: item, history: Array.from({ length: 15 }, (_, n) => ({ id: n + 1, sender: 'client', message: 'Сообщение ' + (n + 1), sentAt: '2026-11-26T08:00:00Z' })), categories: [] } });
        }
        if (url.pathname === '/api/dialogs/query') return route.fulfill({ json: { success: true, dialogs: [item], pagination: { page: 1, page_size: 20, total_items: 1, total_pages: 1 }, summary: { totalTickets: 1 }, my_dialogs: { new: [], unanswered: [], in_work: [], counts: {} }, sla_orchestration: { tickets: {} } } });
        if (url.pathname === '/api/panel-users/profile') {
          const userId = url.searchParams.get('userId') || '1', username = url.searchParams.get('username') || (userId === big ? 'new-employee' : 'member' + userId);
          return route.fulfill({ json: { userId, username, fullName: userId === big ? 'Новый сотрудник' : 'Сотрудник ' + userId, department: 'Поддержка', phones: [], blocked: false, serverTime: new Date().toISOString() } });
        }
        if (url.pathname.endsWith('/notes')) return route.fulfill({ json: { success: true, items: [{ id: '1', title: 'Заметка', content: '<p>' + 'Полезная заметка. '.repeat(40) + '</p>' }], total: 1 } });
        assert(req.method() === 'GET' || ['/api/dialogs/triage-preferences', '/api/dialogs/workspace-telemetry'].includes(url.pathname), 'Unrelated mutation ' + url.pathname);
        return route.fulfill({ json: { success: true, items: [], dialogs: [], participants: [], operators: [], settings: {}, available: false, preferences: {} } });
      });
      const page = await context.newPage(); page.setDefaultTimeout(12000);
      page.on('pageerror', error => errors.push(error.message));
      page.on('response', response => { if (response.status() === 404 && /\.(js|css)(\?|$)/.test(response.url())) missing.push(response.url()); });
      await page.goto(origin + '/dialogs', { waitUntil: 'networkidle' });
      const row = page.locator(`#dialogsTable tbody tr[data-ticket-id="${raw}"]`);
      await row.waitFor();
      await row.locator('[data-column-key="client"]').dblclick();
      const details = page.locator('#dialogDetailsModal'); await details.waitFor({ state: 'visible' });
      await page.waitForFunction(() => document.querySelector('#dialogDetailsSummary').textContent.includes('Блинбери')).catch(async error => { console.log({ detailReads, errors, summary: await page.locator('#dialogDetailsSummary').innerText() }); await page.screenshot({path: path.join(out, 'failure.png')}); throw error; });
      assert.equal(detailReads, 1, 'Double click opens once through the same dialog entry');
      const status = details.locator('details').filter({ has: page.locator('#dialogDetailsSummary') });
      await status.locator('summary').click();
      const metrics = await page.locator('#dialogDetailsSummary').evaluate(el => {
        const section = el.closest('details'), next = section.nextElementSibling, bounds = section.getBoundingClientRect();
        return { contentBottom: el.getBoundingClientRect().bottom, bottom: bounds.bottom, nextTop: next.getBoundingClientRect().top, height: bounds.height };
      });
      assert(metrics.contentBottom <= metrics.bottom + 1, 'Status content is inside the expanded section');
      assert(metrics.nextTop >= metrics.bottom - 1, 'The problem section does not overlap status');
      assert(metrics.height > 300, 'Long status content expands instead of being compressed');
      await page.screenshot({ path: path.join(out, `status-${theme}-${width}-${height}.png`) });
      await page.locator('#dialogDetailsParticipantsBtn').click();
      const manager = page.locator('#dialogParticipantsModal'); await manager.waitFor({ state: 'visible' });
      await page.locator('#dialogParticipantsCurrentList .dialog-details-participant-card').nth(11).waitFor({ state: 'attached' });
      const picker = page.locator('#dialogParticipantsSelect').locator('..');
      await picker.locator('.employee-picker__trigger').click();
      const option = picker.locator(`[data-employee-value="${big}"]`); await option.scrollIntoViewIfNeeded();
      await option.click(); assert.equal(await page.locator('#dialogParticipantsSelect').inputValue(), big);
      await page.locator('#dialogParticipantsAddBtn').scrollIntoViewIfNeeded(); await page.locator('#dialogParticipantsAddBtn').click();
      await page.locator(`[data-remove-participant-id="${big}"]`).waitFor({ state: 'attached' });
      await page.locator('#notification-container .ui-notification-close').click();
      assert.equal(writes[0].body.memberId, big); assert.equal(legacyReads, 0, 'BUSINESS never falls back to raw legacy participants');
      await page.screenshot({ path: path.join(out, `participants-${theme}-${width}-${height}.png`) });
      const remove = page.locator(`[data-remove-participant-id="${big}"]`); await remove.scrollIntoViewIfNeeded(); await remove.click();
      await page.waitForFunction(id => !document.querySelector(`[data-remove-participant-id="${id}"]`), big);
      assert.equal(writes[1].method, 'DELETE'); await page.locator('#notification-container .ui-notification-close').click();
      await picker.locator('.employee-picker__trigger').scrollIntoViewIfNeeded(); await picker.locator('.employee-picker__trigger').click();
      await picker.locator(`[data-employee-value="${big}"]`).click(); conflict = true;
      await page.locator('#dialogParticipantsAddBtn').scrollIntoViewIfNeeded(); await page.locator('#dialogParticipantsAddBtn').click();
      await page.waitForFunction(() => document.documentElement.textContent.includes('Список или права изменились.')).catch(async error => { console.log({writes,errors,body: await page.locator('#dialogParticipantsModal').innerText()}); throw error; });
      await page.locator('#notification-container .ui-notification-close').click();
      assert.equal(writes.length, 3, 'Conflict is not retried automatically'); conflict = false; canManage = false;
      await manager.locator('[data-dialog-participants-refresh]').click();
      await page.locator('#dialogParticipantsSelect:disabled').waitFor({state:'attached'}); assert(await page.locator('#dialogParticipantsAddBtn').isDisabled());
      assert.equal(await page.locator('#dialogParticipantsCurrentList [data-remove-participant-id]').count(), 0, 'Read-only roster has no mutation actions');
      const bounds = await manager.locator('.modal-content').boundingBox(); assert(bounds.y >= -1 && bounds.y + bounds.height <= height + 1, 'Manager fits viewport');
      await manager.locator('.modal-footer [data-bs-dismiss]').click(); assert(await details.isVisible());
      await details.locator('.modal-header .btn-close').click(); await details.waitFor({state:'hidden'});
      await row.locator('.dialog-open-btn').click(); await details.waitFor({ state: 'visible' });
      assert.equal(detailReads, 2, 'Original open button remains supported');
      await details.locator('.modal-header .btn-close').click(); await details.waitFor({state:'hidden'});
      await row.locator('[data-column-key="client"]').dispatchEvent('dblclick', { button: 0, ctrlKey: true });
      assert.equal(detailReads, 2, 'Modifier gestures do not open a dialog');
      await row.locator('.dialog-open-btn').dispatchEvent('dblclick', { button: 0 });
      assert.equal(detailReads, 2, 'Interactive child double click is ignored by the row handler');
      assert.deepEqual(errors, []); assert.deepEqual(missing, []);
      report.push({ theme, width, height, metrics, detailReads, legacyReads, writes, errors, missing }); await context.close();
    }
    fs.writeFileSync(path.join(out, 'report.json'), JSON.stringify(report, null, 2)); console.log(`GREEN: ${report.length} viewport/theme scenarios.`);
  } finally { await browser.close(); await new Promise(resolve => server.close(resolve)); }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
