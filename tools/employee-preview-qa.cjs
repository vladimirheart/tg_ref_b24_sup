/* Production shared preview/renderers and Bootstrap; loopback fixtures only, no project server or DB. */
const fs = require('node:fs'), path = require('node:path'), http = require('node:http'), assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..'), assets = path.join(root, 'spring-panel/src/main/resources/static');
const out = path.join(root, 'spring-panel/target/employee-preview-qa');
const fixed = '2026-11-25T10:00:00Z', largeId = '9007199254740993';
const anna = { userId: largeId, username: 'private-login', fullName: 'Анна Смирнова', department: 'Поддержка',
  email: 'anna@example.test', phones: [{ label: 'work', value: '+7 (495) 123-45-67' }],
  lastPortalActivityAt: '2026-11-25T09:45:00Z', serverTime: fixed, blocked: false, photo: '' };
const html = `<!doctype html><html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<link rel="stylesheet" href="/vendor/bootstrap/5.3.3/bootstrap.min.css"><link rel="stylesheet" href="/css/style.css">
<link rel="stylesheet" href="/vendor/bootstrap-icons/1.10.5/bootstrap-icons.css"><link rel="stylesheet" href="/css/employee-profiles.css">
</head><body class="settings-page"><main class="container p-3 settings-surface page-shell--settings"><h1>Сотрудники</h1><div id="history"></div><div id="messages"></div><div id="dynamic"></div></main>
<div id="usersModal" class="modal" tabindex="-1"><div class="modal-dialog"><div class="modal-content"><div class="modal-body">Пользователи</div></div></div></div>
<div id="userEditorModal" class="modal" tabindex="-1"><div class="modal-dialog"><div class="modal-content"><div class="modal-body"><input id="unsaved" value="Несохранённое имя"><span data-employee-id="${largeId}" data-employee-compact="true">Сотрудник</span></div></div></div></div>
<script src="/vendor/bootstrap/5.3.3/bootstrap.bundle.min.js"></script><script src="/js/employee-profiles.js"></script>
<script src="/js/dialogs-avatar-runtime.js"></script><script src="/js/dialogs-presentation-runtime.js"></script><script src="/js/settings-page-shell.js"></script></body></html>`;
async function main() {
  fs.mkdirSync(out, { recursive: true });
  const server = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://localhost');
    if (url.pathname === '/') { res.setHeader('Content-Type', 'text/html; charset=utf-8'); return res.end(html); }
    const file = path.resolve(assets, '.' + url.pathname);
    if (!file.startsWith(assets + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) { res.writeHead(404); return res.end('{}'); }
    res.setHeader('Content-Type', ({ '.css': 'text/css', '.js': 'text/javascript', '.woff2': 'font/woff2', '.woff': 'font/woff' })[path.extname(file)] || 'application/octet-stream');
    fs.createReadStream(file).pipe(res);
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const origin = `http://127.0.0.1:${server.address().port}`, { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
  const browser = await chromium.launch({ headless: true, ...(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {}) });
  const report = [];
  try {
    for (const theme of ['light', 'dark']) for (const width of [320, 768, 1440]) {
      console.log(`Checking ${theme}/${width}`);
      const context = await browser.newContext({ viewport: { width, height: 900 }, colorScheme: theme, reducedMotion: 'reduce' });
      let profile = structuredClone(anna), fail = false, delayed = false;
      const requests = [], errors = [], missing = [], delayedRoutes = [];
      await context.route('**/*', async route => {
        const req = route.request(), url = new URL(req.url());
        if (url.origin !== origin) return route.abort();
        if (url.pathname !== '/api/panel-users/profile') return route.continue();
        assert.equal(req.method(), 'GET'); requests.push(Object.fromEntries(url.searchParams));
        const id = url.searchParams.get('userId'), username = url.searchParams.get('username');
        if (username === 'slow' && delayed) { delayedRoutes.push(route); return; }
        if (username === 'SYSTEM') return route.fulfill({ status: 404, json: {} });
        const person = username === 'slow' ? { ...anna, username: 'slow', fullName: 'Медленный ответ' }
          : username === 'empty' ? { ...anna, userId: '3', username: 'empty', fullName: '', department: '', email: '', phones: [], lastPortalActivityAt: null, blocked: true }
            : id === largeId || username === 'private-login' ? profile : null;
        return route.fulfill({ status: fail ? 503 : person ? 200 : 404, json: person || {} });
      });
      const page = await context.newPage(); page.on('pageerror', error => errors.push(error.message));
      page.setDefaultTimeout(10000);
      page.on('response', response => { if (response.status() === 404 && /\.(js|css)(\?|$)/.test(response.url())) missing.push(response.url()); });
      await page.goto(origin, { waitUntil: 'networkidle' });
      await page.clock.install();
      await page.evaluate(theme => {
        document.documentElement.dataset.bsTheme = theme; document.documentElement.dataset.theme = theme;
        const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
        const runtime = DialogsPresentationRuntime.createRuntime({ escapeHtml, renderMessageAvatar: () => '', renderMessageText: escapeHtml });
        document.querySelector('#messages').innerHTML = runtime.renderWorkspaceMessageItem({ senderRole: 'operator', senderUsername: 'private-login', senderName: 'Анна Смирнова', messageText: 'Сообщение оператора' })
          + runtime.renderWorkspaceMessageItem({ senderRole: 'client', senderUsername: 'private-login', senderName: 'Внешний клиент', messageText: 'Внешнее сообщение' });
        const avatars = DialogsAvatarRuntime.createRuntime({ escapeHtml, avatarInitial: () => 'АС' });
        document.querySelector('#messages').insertAdjacentHTML('beforeend', avatars.renderMessageAvatar(avatars.resolveWorkspaceMessageAvatarSpec({ senderRole: 'support', senderUsername: 'private-login', senderName: 'Анна Смирнова' })));
        document.querySelector('#history').innerHTML = 'Действие: ' + EmployeeProfiles.actor({ userId: '9007199254740993', fullName: 'Старое имя' })
          + '<div id="system">' + EmployeeProfiles.actor('SYSTEM') + '</div>';
      }, theme);
      await page.locator('#history [data-employee-verified="true"]').first().waitFor();
      await page.waitForFunction(() => !document.querySelector('#system [data-employee-identity]'));
      assert.equal(await page.locator('#system [role], #system a, #system [tabindex]').count(), 0, 'System/unresolved authors stay plain text');
      assert.equal(await page.locator('#messages article').nth(1).locator('[data-employee-identity]').count(), 0, 'External client identity is never treated as employee');
      await page.locator('#history [data-employee-id]').focus(); await page.keyboard.press('Enter');
      const dialog = page.locator('#employeeProfileDialog');
      await page.locator('#employeeProfileTitle').filter({ hasText: anna.fullName }).waitFor();
      const text = await dialog.innerText();
      assert(!text.includes('private-login') && !text.includes('Логин'), 'Preview contains no login');
      assert.match(text, /Поддержка/); assert.match(text, /Online/); assert.match(text, /Активен/); assert.match(text, /Последняя активность/);
      assert.equal(await dialog.locator('a[href^="mailto:"]').textContent(), anna.email);
      assert.equal(await dialog.locator('a[href^="tel:"]').textContent(), anna.phones[0].value);
      assert.equal(await dialog.locator('time').getAttribute('datetime'), anna.lastPortalActivityAt.replace('Z', '.000Z'));
      assert(requests.some(request => request.userId === largeId && !request.username), 'Large ID remains exact and never becomes username lookup');
      assert(await dialog.evaluate(el => el.scrollWidth <= el.clientWidth + 1), 'Preview has no horizontal overflow');
      await page.screenshot({ path: path.join(out, `${theme}-${width}.png`) });
      profile = { ...anna, blocked: true };
      await page.clock.fastForward(61000);
      await dialog.locator('[data-profile-access]').filter({ hasText: 'Заблокирован' }).waitFor();
      assert.equal(await dialog.locator('[data-profile-presence]').textContent(), 'Online', 'Blocked and online are independent');
      await page.keyboard.press('Escape'); assert.equal(await dialog.evaluate(el => el.open), false);
      assert.equal(await page.evaluate(() => document.activeElement.dataset.employeeId), largeId, 'Focus returns to author');

      // Opening again must refetch instead of displaying the cached access/presence status.
      profile = { ...anna, blocked: true, lastPortalActivityAt: '2026-11-25T09:29:59Z' };
      await page.locator('#history [data-employee-id]').click();
      await dialog.locator('[data-profile-access]').filter({ hasText: 'Заблокирован' }).waitFor();
      assert.equal(await dialog.locator('[data-profile-presence]').textContent(), 'Offline');
      await page.keyboard.press('Escape');
      await page.locator('#messages [data-employee-preserve-content]').click();
      await page.locator('#employeeProfileTitle').filter({ hasText: anna.fullName }).waitFor();
      await page.keyboard.press('Escape');
      await page.locator('#messages article').first().locator('[data-employee-verified="true"]').click();
      await page.locator('#employeeProfileTitle').filter({ hasText: anna.fullName }).waitFor();
      await page.keyboard.press('Escape');

      // Observer binds later-inserted authors; missing names/contacts never reveal the login.
      await page.evaluate(() => document.querySelector('#dynamic').innerHTML = EmployeeProfiles.actor('empty'));
      await page.locator('#dynamic [data-employee-verified="true"]').waitFor();
      await page.locator('#dynamic [data-employee-identity]').focus(); await page.keyboard.press('Space');
      await page.locator('#employeeProfileTitle').filter({ hasText: /^Сотрудник$/ }).waitFor();
      assert.match(await dialog.innerText(), /Контактные данные не указаны/); assert.match(await dialog.innerText(), /Не зафиксирована/);
      assert(!(await dialog.innerText()).includes('empty')); await page.keyboard.press('Escape');

      // Retry a transient failure, and ensure a late response cannot overwrite another employee.
      fail = true; await page.evaluate(() => void EmployeeProfiles.open({ userId: '9007199254740993' }));
      await dialog.locator('[role="alert"]').waitFor().catch(async error => { console.log(await page.evaluate(() => ({ dialog: document.querySelector('#employeeProfileDialog')?.outerHTML }))); console.log({ requests, errors }); throw error; }); fail = false;
      await dialog.getByRole('button', { name: 'Повторить' }).click();
      await page.locator('#employeeProfileTitle').filter({ hasText: anna.fullName }).waitFor();
      await page.evaluate(() => { document.querySelector('#employeeProfileDialog').close(); void EmployeeProfiles.open('empty'); });
      await page.locator('#employeeProfileTitle').filter({ hasText: /^Сотрудник$/ }).waitFor();
      delayed = true; await page.evaluate(() => void EmployeeProfiles.open('slow'));
      await page.waitForResponse(() => false, { timeout: 50 }).catch(() => {});
      await page.evaluate(() => void EmployeeProfiles.open('empty'));
      await page.locator('#employeeProfileTitle').filter({ hasText: /^Сотрудник$/ }).waitFor();
      assert.equal(delayedRoutes.length, 1);
      await delayedRoutes.pop().fulfill({ json: { ...anna, username: 'slow', fullName: 'Медленный ответ' } });
      await page.waitForFunction(() => EmployeeProfiles.get('slow').resolved === true);
      assert.equal(await page.locator('#employeeProfileTitle').textContent(), 'Сотрудник'); await page.keyboard.press('Escape');

      // Contact values and labels are text, even with malicious stored content.
      profile = { ...anna, fullName: '<img src=x onerror="window.__xss=1">', email: '<script>@example.test', phones: [{ label: '<img onerror=1>', value: '<script>+123</script>' }] };
      await page.evaluate(() => void EmployeeProfiles.open({ userId: '9007199254740993' }));
      await page.waitForFunction(() => document.querySelector('#employeeProfileTitle')?.textContent.startsWith('<img'));
      assert.equal(await dialog.locator('img, script').count(), 0); assert.equal(await page.evaluate(() => window.__xss), undefined);
      await page.keyboard.press('Escape'); profile = structuredClone(anna);

      // Native preview on top of two actual Bootstrap settings modals preserves both and the editor value.
      await page.evaluate(() => { bootstrap.Modal.getOrCreateInstance(document.querySelector('#usersModal')).show(); bootstrap.Modal.getOrCreateInstance(document.querySelector('#userEditorModal')).show(); });
      await page.locator('#userEditorModal.show').waitFor(); await page.locator('#unsaved').fill('Имя ещё не сохранено');
      await page.locator('#userEditorModal [data-employee-id]').click();
      await page.locator('#employeeProfileTitle').filter({ hasText: anna.fullName }).waitFor(); await page.keyboard.press('Escape');
      assert.equal(await page.locator('.modal.show').count(), 2); assert.equal(await page.locator('#unsaved').inputValue(), 'Имя ещё не сохранено');
      assert.equal(await dialog.evaluate(el => el.open), false);
      assert.deepEqual(errors, []); assert.deepEqual(missing, []);
      report.push({ theme, width, requests: requests.length, errors, missing, cases: ['contacts/no-login', 'presence/access/periodic-refresh', 'large-id/rename', 'dynamic/keyboard', 'unknown-author', 'message-author/avatar', 'fresh/retry/stale-response/reopen', 'escaping', 'stacked-modal-escape'] });
      await context.close();
    }
    fs.writeFileSync(path.join(out, 'report.json'), JSON.stringify(report, null, 2));
    console.log(`GREEN: ${report.length} viewport/theme scenarios; shared employee preview and actor navigation.`);
  } finally { await browser.close(); await new Promise(resolve => server.close(resolve)); }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
