/* R10: реальный MVC HTML / JS, локальные тестовые read/write endpoints; рабочие записи запрещены. */
const fs = require('node:fs'), path = require('node:path'), http = require('node:http'), assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..');
const resources = path.join(root, 'spring-panel/src/main/resources');
const captures = path.join(root, 'spring-panel/target/neutral-ops-rendered');
const output = path.join(root, 'spring-panel/target/neutral-ops-r10-acceptance');
const photo = fs.readFileSync(path.join(resources, 'static/images/iguana-brand.png'));
const stored = new Map();
let noteBody = '', savedBody = '', uploadCount = 0, failPulse = false, denyUpload = false;
const escape = value => String(value).replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
const pulse = { success: true, date: '2026-10-23', zone: 'UTC', generatedAt: '2026-10-23T10:00:00Z',
  dialogs: { available: true, createdToday: 47, active: 12, hourly: Array.from({ length: 24 }, (_, h) => h >= 8 && h <= 17 ? [1, 3, 8, 6, 4, 7, 5, 6, 4, 3][h - 8] : 0), channels: { Telegram: 27, MAX: 19, VK: 1 } },
  incidents: { available: true, active_count: 4, critical_active_count: 1, aged_open_count: 2, failed_route_count: 0, created_24h_count: 6, resolved_24h_count: 2 },
  runtime: { available: true, runningBots: 2, unknownBots: 1, activeChannels: 3, totalChannels: 4 } };

async function main() {
  fs.mkdirSync(output, { recursive: true });
  const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
  const server = http.createServer(async (req, res) => {
    const url = new URL(req.url, 'http://localhost');
    const json = (data, status = 200) => { res.writeHead(status, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(data)); };
    if (url.pathname === '/api/home/pulse') return json(pulse, failPulse ? 503 : 200);
    if (url.pathname === '/api/dialog-ticket') return json({ summary: { ticketId: url.searchParams.get('ticketId') }, history: [] });
    if (url.pathname === '/api/dialog-ticket/notes') {
      const id = url.searchParams.get('ticketId');
      const notes = [{ id: 1, title: 'Роутер', body: 'Инструкция ' + 'А'.repeat(900) + ' перезагрузка\n' + noteBody, locationMatched: true }, { id: 2, title: 'Принтер', body: 'Картридж' }];
      if (id === 'slow-A') return setTimeout(() => json({ notes: [{ id: 3, title: 'Устаревшая заметка', body: 'Старый ответ' }] }), 300);
      return json({ notes });
    }
    if (url.pathname === '/api/attachments/knowledge-base' && req.method === 'POST') {
      if (denyUpload) return json({ error: 'Forbidden' }, 403);
      assert(req.headers['x-csrf-token'] || req.headers['x-xsrf-token'], 'Upload must send CSRF header');
      const chunks = []; for await (const chunk of req) chunks.push(chunk);
      const raw = Buffer.concat(chunks), boundary = req.headers['content-type'].match(/boundary=(.+)$/)[1];
      const start = raw.indexOf('\r\n\r\n') + 4, end = raw.indexOf('\r\n--' + boundary, start);
      const bytes = raw.subarray(start, end); assert.deepEqual(bytes, photo, 'Upload photo bytes must be intact');
      const name = `qa-${++uploadCount}.png`; stored.set(name, bytes);
      return json({ storedName: name, originalName: 'photo.png', size: bytes.length });
    }
    if (url.pathname.startsWith('/api/attachments/knowledge-base/')) {
      const bytes = stored.get(url.pathname.split('/').pop());
      if (!bytes) { res.writeHead(404); return res.end(); }
      res.writeHead(200, { 'Content-Type': 'image/png', 'Content-Disposition': 'attachment; filename="photo.png"' }); return res.end(bytes);
    }
    if (url.pathname === '/knowledge-base/notes' && req.method === 'POST') {
      const chunks = []; for await (const chunk of req) chunks.push(chunk);
      const form = new URLSearchParams(Buffer.concat(chunks).toString());
      assert(form.get('_csrf'), 'Save uses existing CSRF form token'); savedBody = form.get('body'); noteBody = savedBody;
      res.writeHead(303, { Location: '/screens/knowledge/note-editor' }); return res.end();
    }
    if (!['GET', 'HEAD'].includes(req.method)) return json({}, 409);
    const base = url.pathname.startsWith('/screens/') ? captures : path.join(resources, 'static');
    const name = url.pathname.startsWith('/screens/') ? url.pathname.slice(9) + '.html' : url.pathname.slice(1);
    const file = path.resolve(base, name);
    if (!file.startsWith(base + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) return json({}, 404);
    if (url.pathname === '/screens/knowledge/note-editor') {
      const html = fs.readFileSync(file, 'utf8').replace(/(<textarea\b[^>]*id="noteBody"[^>]*>)[\s\S]*?(<\/textarea>)/, (_, a, b) => a + escape(savedBody) + b);
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' }); return res.end(html);
    }
    const mime = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml', '.png': 'image/png', '.woff2': 'font/woff2' };
    res.writeHead(200, { 'Content-Type': mime[path.extname(file)] || 'application/octet-stream' }); fs.createReadStream(file).pipe(res);
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const origin = `http://127.0.0.1:${server.address().port}`;
  const browser = await chromium.launch({ headless: true }); const report = [];
  try {
    for (const theme of ['light', 'dark']) for (const width of [320, 390, 768, 1440, 1920]) {
      const touch = width <= 390; savedBody = ''; noteBody = ''; failPulse = false; denyUpload = false;
      const context = await browser.newContext({ viewport: { width, height: 1100 }, hasTouch: touch, isMobile: touch, colorScheme: theme, reducedMotion: 'reduce' });
      await context.addInitScript(theme => { localStorage.setItem('iguana:theme', theme); localStorage.setItem('iguana:theme-palette', 'neo'); localStorage.setItem('sidebarPinned', '1'); }, theme);
      await context.route('**/*', route => route.request().url().startsWith(origin + '/') ? route.continue() : route.abort());
      const page = await context.newPage();
      async function checkAccountModal() {
        if (await page.locator('#sidebarMobileToggle').isVisible() && await page.locator('#sidebarMobileToggle').getAttribute('aria-expanded') !== 'true') await page.locator('#sidebarMobileToggle').click();
        await page.locator('[data-sidebar-action-trigger]').click();
        await page.locator('[data-sidebar-change-password]').click();
        await page.locator('#changePasswordModal.show').waitFor({ state: 'visible' });
        await page.waitForFunction(() => document.getElementById('changePasswordModal').contains(document.activeElement));
        await page.keyboard.press('Escape');
        await page.locator('#changePasswordModal').waitFor({ state: 'hidden' });
        if (await page.locator('#sidebarMobileToggle').isVisible() && await page.locator('#sidebarMobileToggle').getAttribute('aria-expanded') === 'true') await page.locator('#sidebarMobileToggle').click();
      }
      await page.goto(origin + '/screens/home/index');
      await page.waitForFunction(() => document.querySelector('[data-pulse-value="today"]').textContent === '47');
      assert.equal(await page.locator('[data-pulse-value="bots"]').textContent(), '2');
      assert((await page.locator('[data-pulse-hint="bots"]').textContent()).includes('1'));
      assert.deepEqual(await page.evaluate(() => Chart.getChart('homeHourlyChart').data.datasets[0].data), pulse.dialogs.hourly);
      assert(!await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1));
      assert(await page.locator('.home-section-link[href="/dialogs"]').count());
      await checkAccountModal();
      await page.screenshot({ path: path.join(output, `home-${theme}-${width}.png`), fullPage: true });
      failPulse = true; await page.locator('[data-home-refresh]').click();
      await page.waitForFunction(() => document.querySelector('[data-home-updated]').textContent.includes('Нет свежих данных'));
      assert.equal(await page.locator('[data-pulse-value="today"]').textContent(), '—'); failPulse = false;

      await page.goto(origin + '/screens/knowledge/note-editor');
      await page.locator('[name="title"]').fill('Заметка с фотографией');
      await page.locator('#noteBody').fill('Фото оборудования');
      await page.locator('[data-note-photo-input]').setInputFiles({ name: 'photo.png', mimeType: 'image/png', buffer: photo });
      await page.waitForFunction(() => document.querySelector('[data-note-photo-state]').textContent.includes('Добавлено фото: 1'));
      let body = await page.locator('#noteBody').inputValue(); assert(body.includes('![Фото](/api/attachments/knowledge-base/'));
      await page.waitForFunction(() => document.querySelector('[data-note-body-preview] img')?.naturalWidth > 0);
      await page.locator('#noteBody').evaluate((textarea, base64) => {
        const bytes = Uint8Array.from(atob(base64), char => char.charCodeAt(0));
        const data = new DataTransfer(); data.items.add(new File([bytes], 'pasted.png', { type: 'image/png' }));
        textarea.dispatchEvent(new ClipboardEvent('paste', { clipboardData: data, bubbles: true, cancelable: true }));
      }, photo.toString('base64'));
      await page.waitForFunction(() => document.querySelectorAll('[data-note-body-preview] img').length === 2);
      body = await page.locator('#noteBody').inputValue();
      await page.locator('[data-note-photo-input]').setInputFiles({ name: 'bad.svg', mimeType: 'image/svg+xml', buffer: Buffer.from('<svg/>') });
      await page.waitForFunction(() => document.querySelector('[data-note-photo-state]').textContent.includes('SVG'));
      assert.equal(await page.locator('#noteBody').inputValue(), body, 'Rejected format does not change note');
      denyUpload = true;
      await page.locator('[data-note-photo-input]').setInputFiles({ name: 'photo.png', mimeType: 'image/png', buffer: photo });
      await page.waitForFunction(() => document.querySelector('[data-note-photo-state]').textContent.includes('Не удалось'));
      assert.equal(await page.locator('#noteBody').inputValue(), body, 'Denied upload does not change note'); denyUpload = false;
      await page.locator('[data-knowledge-note-form] [type="submit"]').click();
      await page.waitForURL('**/screens/knowledge/note-editor');
      assert.equal(await page.locator('#noteBody').inputValue(), body, 'Save/reload preserves text and photo reference');
      await page.waitForFunction(() => document.querySelector('[data-note-body-preview] img')?.naturalWidth > 0);
      assert.equal(savedBody.replaceAll('\r\n', '\n'), body); noteBody = body;
      await checkAccountModal();
      await page.screenshot({ path: path.join(output, `note-photo-${theme}-${width}.png`), fullPage: true });

      await page.goto(origin + '/screens/dialogs/index');
      await page.evaluate(async () => {
        const active = { ticketId: null, channelId: null };
        window.notesQaRuntime = DialogsDetailsRuntime.createRuntime({
          elements: { detailsModalEl: document.getElementById('dialogDetailsModal') },
          getActiveDialogState: () => active,
          setActiveDialogTicketId: value => { active.ticketId = value; },
          showModalSafe: el => bootstrap.Modal.getOrCreateInstance(el).show()
        });
        await notesQaRuntime.openDialogDetails('ticket-A');
      });
      await page.waitForFunction(() => document.querySelectorAll('#dialogDetailsNotesList article').length === 2);
      assert.equal(await page.locator('#dialogDetailsNotesSection').getAttribute('open'), null, 'Notes are collapsed by default');
      await page.locator('#dialogDetailsNotesSection summary').click();
      await page.locator('#dialogDetailsNotesSearch').fill('перезагрузка');
      assert.equal(await page.locator('#dialogDetailsNotesList article').count(), 1, 'Search includes text beyond preview limit');
      await page.locator('#dialogDetailsNotesSearch').fill('ПРИНТЕР'); assert.equal(await page.locator('#dialogDetailsNotesList article').count(), 1);
      await page.locator('#dialogDetailsNotesSearch').fill('нет такого текста'); assert.equal(await page.locator('#dialogDetailsNotesList article').count(), 0);
      await page.locator('#dialogDetailsNotesSearch').fill('роутер');
      await page.waitForFunction(() => document.querySelector('#dialogDetailsNotesList img')?.naturalWidth > 0);
      const sidebarGeometry = await page.evaluate(() => {
        const sidebar = document.querySelector('#dialogDetailsSidebar .dialog-details-sidebar-scroll');
        return { width: sidebar.clientWidth, scroll: sidebar.scrollWidth,
          overflow: Array.from(sidebar.querySelectorAll('*')).filter(el => el.getBoundingClientRect().right > sidebar.getBoundingClientRect().right + 1).slice(0, 10).map(el => ({ tag: el.tagName, className: el.className, width: el.getBoundingClientRect().width })) };
      });
      assert(sidebarGeometry.scroll <= sidebarGeometry.width + 1, JSON.stringify(sidebarGeometry));
      await page.screenshot({ path: path.join(output, `dialog-notes-${theme}-${width}.png`) });
      await page.evaluate(async () => { const first = notesQaRuntime.openDialogDetails('slow-A'); await notesQaRuntime.openDialogDetails('ticket-B'); await first; });
      await page.waitForTimeout(450);
      assert.equal(await page.locator('#dialogDetailsNotesSection').getAttribute('open'), null, 'New ticket resets disclosure');
      assert.equal(await page.locator('#dialogDetailsNotesSearch').inputValue(), '', 'New ticket resets search');
      assert(!(await page.locator('#dialogDetailsNotesList').textContent()).includes('Устаревшая заметка'));
      report.push({ theme, width, touch, photoBytes: photo.length, savedPhoto: true, pastedPhoto: true, svgRejected: true, uploadDenialSafe: true, searchFullText: true, collapsed: true, staleResponseRejected: true });
      console.log(`GREEN: ${theme} ${width}px, home + photo upload/save/reload + dialog notes`);
      await context.close();
    }
  } finally { await browser.close(); await new Promise(resolve => server.close(resolve)); }
  fs.writeFileSync(path.join(output, 'report.json'), JSON.stringify(report, null, 2));
}
main().catch(error => { console.error(error); process.exitCode = 1; });
