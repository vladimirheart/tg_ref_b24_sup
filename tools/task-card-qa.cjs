/* Actual Thymeleaf task page + production JS/CSS; all API writes stay in fixtures. Run TaskCardRenderTest first. */
const fs = require('node:fs'), path = require('node:path'), http = require('node:http'), assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..'), assets = path.join(root, 'spring-panel/src/main/resources/static');
const capture = path.join(root, 'spring-panel/target/task-card-rendered/tasks.html'), out = path.join(root, 'spring-panel/target/task-card-qa');
const employees = {
  anna: { userId: '1', username: 'anna', fullName: 'Анна Смирнова', department: 'Поддержка', photo: '/fixture/avatar.svg', email: 'anna@example.test', phones: [{ label: 'work', value: '+7 495 123-45-67' }], blocked: false, lastPortalActivityAt: new Date().toISOString(), serverTime: new Date().toISOString() },
  victor: { username: 'victor', fullName: 'Виктор Крюков', department: 'Разработка', photo: '/fixture/broken.jpg' },
  old: { username: 'old', fullName: 'Исторический сотрудник', department: '', photo: '' },
};
const original = { id: '41', display_no: 'DL_41', title: 'Подготовить запуск нового проекта и согласовать сроки с командой',
  body_html: '<p>Обновить рабочий план и согласовать результат с командой поддержки.</p><p>Перед запуском проверить настройки и сообщить участникам.</p>',
  creator: 'anna', assignee: 'anna', co: ['victor'], watchers: ['anna'], status: 'В работе', created_at: '2026-11-20T09:00:00Z',
  due_at: '2026-12-01T15:00:00Z', projects: [{ id: '2', project_key: 'CRM', name: 'Развитие поддержки' }], tags: [{ name: 'интерфейс' }],
  comments: [{ author: 'victor', created_at: '2026-11-24T07:00:00Z', html: '<p>План согласован. Можно приступать.</p>' }],
  events: [{ event_type: 'TASK_CREATED', actor: 'anna', occurred_at: '2026-11-20T09:00:00Z' }],
  history: [{ at: '2026-11-20T09:00:00Z', text: 'Старая запись истории' }] };
async function main() {
  assert(fs.existsSync(capture), 'Run TaskCardRenderTest before browser QA'); fs.mkdirSync(out, { recursive: true });
  const server = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://localhost');
    if (url.pathname === '/tasks') { res.setHeader('Content-Type', 'text/html; charset=utf-8'); res.end(fs.readFileSync(capture)); return; }
    if (url.pathname === '/fixture/avatar.svg') { res.setHeader('Content-Type', 'image/svg+xml'); res.end('<svg xmlns="http://www.w3.org/2000/svg" width="80" height="80"><rect width="80" height="80" fill="#416a58"/><circle cx="40" cy="30" r="14" fill="#efd5bd"/><path d="M12 80 Q12 48 40 48 Q68 48 68 80" fill="#d4e0dc"/></svg>'); return; }
    const file = path.resolve(assets, '.' + url.pathname);
    if (!file.startsWith(assets + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) { res.writeHead(404); res.end('{}'); return; }
    res.setHeader('Content-Type', ({ '.css': 'text/css', '.js': 'text/javascript', '.svg': 'image/svg+xml', '.png': 'image/png', '.woff2': 'font/woff2', '.woff': 'font/woff' })[path.extname(file)] || 'application/octet-stream'); fs.createReadStream(file).pipe(res);
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const origin = `http://127.0.0.1:${server.address().port}`, { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
  const browser = await chromium.launch({ headless: true, ...(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {}) }), report = [];
  try {
    for (const theme of (process.env.QA_THEME ? [process.env.QA_THEME] : ['light', 'dark'])) for (const width of (process.env.QA_WIDTH ? [Number(process.env.QA_WIDTH)] : [320, 768, 1440])) {
      const context = await browser.newContext({ viewport: { width, height: 1000 }, colorScheme: theme, reducedMotion: 'reduce' });
      await context.addInitScript(theme => {
        localStorage.setItem('iguana:theme', theme); localStorage.setItem('sidebarPinned', '1');
        window.__profileEvents = [];
        for (const type of ['pointerdown', 'pointerup', 'mousedown', 'mouseup', 'focusin', 'click', 'close', 'cancel', 'keydown']) document.addEventListener(type, event => {
          window.__profileEvents.push({ type, target: event.target.id || event.target.className, key: event.key, open: document.querySelector('#employeeProfileDialog')?.open });
        }, true);
      }, theme);
      let task = structuredClone(original), readonly = false, failSave = false, failProfile = false;
      const writes = [], errors = [], missing = [];
      await context.route('**/*', async route => {
        const req = route.request(), url = new URL(req.url());
        if (url.origin !== origin) return route.abort();
        if (!url.pathname.startsWith('/api/')) return route.continue();
        if (url.pathname === '/api/panel-users/profile') {
          const person = url.searchParams.has('userId') ? Object.values(employees).find(person => person.userId === url.searchParams.get('userId')) : employees[url.searchParams.get('username')];
          return route.fulfill({ status: failProfile ? 503 : person ? 200 : 404, json: person || {} });
        }
        if (url.pathname === '/api/shared-work/context') return route.fulfill({ json: { canCreateTask: true, canCreateProject: true, systemAdmin: true } });
        if (/\/api\/shared-work\/TASK\/41\/access$/.test(url.pathname)) return route.fulfill({ json: { kind: 'TASK', id: '41', canWrite: !readonly, canManage: !readonly, members: [{ userId: '1', username: 'anna', role: 'OWNER' }, { userId: '2', username: 'victor', role: 'EDITOR' }] } });
        if (url.pathname.endsWith('/people')) return route.fulfill({ json: [{ userId: '1', username: 'anna' }, { userId: '2', username: 'victor' }] });
        if (url.pathname === '/api/shared-work/TASK/41/history') return route.fulfill({ json: [{ id: '7', action: 'MEMBER_SET', actorUserId: '1', actorName: 'Старое имя', occurredAt: '2026-11-20T09:00:00Z', beforeState: '{}', afterState: '{}' }] });
        if (url.pathname.endsWith('/history')) return route.fulfill({ json: [] });
        if (url.pathname === '/api/projects') return route.fulfill({ json: { items: original.projects } });
        if (url.pathname === '/api/dialog-ticket') return route.fulfill({ json: { summary: { rawResponsible: 'anna', responsible: 'Анна Смирнова', responsibleAvatarUrl: '/fixture/avatar.svg' }, history: [] } });
        if (url.pathname === '/api/tasks/41') return route.fulfill({ json: task });
        if (url.pathname === '/api/tasks' && req.method() === 'POST') {
          const form = await new Request(req.url(), { method: 'POST', headers: req.headers(), body: req.postDataBuffer() }).formData();
          const values = Object.fromEntries(form); writes.push(values);
          if (failSave) return route.fulfill({ status: 500, json: { error: 'Тестовая ошибка сохранения' } });
          task = { ...task, title: values.title, status: values.status, assignee: values.assignee, co: values.co.split(',').map(s => s.trim()).filter(Boolean), watchers: values.watchers.split(',').map(s => s.trim()).filter(Boolean) };
          return route.fulfill({ json: { ok: true } });
        }
        if (url.pathname === '/api/tasks') return route.fulfill({ json: { items: [task], total: 1, page: 1, page_size: 20 } });
        assert.equal(req.method(), 'GET', 'Unexpected write ' + req.method() + ' ' + url.pathname);
        return route.fulfill({ json: {} });
      });
      const page = await context.newPage(); page.on('pageerror', error => errors.push(error.message));
      const pane = async name => { if (width < 1100) await page.locator(`[data-task-pane-tab="${name}"]`).click(); };
      page.on('response', response => { if (response.status() === 404 && /\.(js|css)(\?|$)/.test(response.url())) missing.push(response.url()); });
      await page.goto(origin + '/tasks#task=41', { waitUntil: 'networkidle' });
      const modal = page.locator('#taskModal'); await modal.waitFor({ state: 'visible' });
      await page.locator('#saveTaskBtn:enabled').waitFor();
      assert.equal(await page.locator('[data-task-disclosure][open]').count(), 0, 'Existing task opens without secondary panels');
      await pane('properties');
      assert.match(await page.locator('#taskPeople').innerText(), /Анна Смирнова/);
      await page.waitForFunction(() => document.querySelectorAll('#taskPeople img').length === 1);
      assert.equal(await page.locator('#taskPeople img').count(), 1, 'One entry per employee; broken avatar falls back to initials');
      await page.locator('#taskPeople [data-employee-identity="anna"]').first().click();
      await page.locator('#employeeProfileTitle').filter({ hasText: 'Анна Смирнова' }).waitFor();
      assert.match(await page.locator('#employeeProfileDialog').innerText(), /Поддержка/);
      assert.match(await page.locator('#employeeProfileDialog').innerText(), /anna@example.test/);
      assert.match(await page.locator('#employeeProfileDialog').innerText(), /Online/);
      assert.match(await page.locator('#employeeProfileDialog').innerText(), /Активен/);
      assert(!(await page.locator('#employeeProfileDialog').innerText()).includes('Логин'));
      await page.keyboard.press('Escape'); assert(await modal.isVisible(), 'Escape closes the employee card and preserves the task');
      await page.locator('.tasks-access-panel > summary').click();
      await page.locator('#taskModal [data-work-manage] > details > summary').click();
      await page.locator('#taskModal [data-work-history] [data-employee-id="1"]').click();
      await page.locator('#employeeProfileTitle').filter({ hasText: 'Анна Смирнова' }).waitFor();
      await page.keyboard.press('Escape'); await page.locator('#taskModal [data-work-manage] > details > summary').click(); await page.locator('.tasks-access-panel > summary').click();
      await page.locator('#taskStatus').selectOption('Ожидание');
      await page.locator('.tasks-participant-editor > summary').click();
      const coPicker = page.locator('#taskCo').locator('..');
      await coPicker.locator('.employee-picker__trigger').click(); await coPicker.locator('input[type="search"]').fill('анна');
      await page.keyboard.press('Enter'); assert.equal(writes.length, 0, 'Employee search Enter does not submit task'); await page.keyboard.press('Escape');
      assert.equal(await page.locator('#taskCo').inputValue(), 'victor, anna');
      await page.locator('.tasks-participant-editor > summary').click();
      await pane('description');
      await page.locator('#editToggleBtn').click(); await page.locator('#taskTitle').fill('Согласовать план запуска с командой');
      await pane('properties');
      await page.locator('#taskPeople [data-employee-identity="anna"]').first().click();
      await page.locator('#employeeProfileTitle').filter({ hasText: 'Анна Смирнова' }).waitFor().catch(async error => {
        await page.screenshot({ path: path.join(out, 'failure.png') });
        console.log(await page.evaluate(() => ({ dialog: document.querySelector('#employeeProfileDialog')?.outerHTML, events: window.__profileEvents.slice(-30) })));
        throw error;
      });
      await page.locator('#employeeProfileDialog .btn-close').click();
      assert.equal(await page.locator('#taskTitle').inputValue(), 'Согласовать план запуска с командой', 'Profile preserves unsaved edits');
      await page.locator('#editToggleBtn').click();
      await pane('chat');
      await page.locator('#taskModal .modal-body').evaluate(el => { el.scrollTop = 0; });
      await page.screenshot({ path: path.join(out, `task-${theme}-${width}.png`) });
      const overflow = await modal.evaluate(el => el.scrollWidth > el.clientWidth + 2 || el.querySelector('.modal-body').scrollWidth > el.querySelector('.modal-body').clientWidth + 2);
      assert.equal(overflow, false, 'Task card fits viewport');
      failSave = true; await page.locator('#saveTaskBtn').click(); await page.locator('#saveTaskBtn:enabled').waitFor();
      assert(await modal.isVisible()); assert.equal(await page.locator('#taskStatus').inputValue(), 'Ожидание');
      // The app error modal can coexist with the task; dismiss it before retrying.
      await page.waitForFunction(() => { const node = document.getElementById('appMessageModal'); return node?.classList.contains('show') && !bootstrap.Modal.getInstance(node)?._isTransitioning; });
      await page.locator('#appMessageModal [data-bs-dismiss="modal"]').click(); await page.locator('#appMessageModal').waitFor({ state: 'hidden' });
      failSave = false; await page.locator('#saveTaskBtn').click(); await modal.waitFor({ state: 'hidden' });
      assert.equal(writes.at(-1).status, 'Ожидание'); assert.equal(writes.at(-1).co, 'victor, anna'); assert.equal(writes.at(-1).project_ids, '2');
      await page.locator('[data-open-task="41"]').first().click(); await modal.waitFor({ state: 'visible' }); await page.locator('#saveTaskBtn:enabled').waitFor();
      assert.equal(await page.locator('#taskStatus').inputValue(), 'Ожидание');
      await pane('chat');
      await page.locator('#commentEditor').fill('Черновик комментария');
      await pane('properties');
      await page.locator('#taskStatus').selectOption('В работе'); await page.locator('#saveTaskBtn').click();
      await page.getByText('Задача сохранена', { exact: true }).waitFor();
      assert(await modal.isVisible()); assert.equal(await page.locator('#commentEditor').innerText(), 'Черновик комментария', 'Saving task preserves the comment draft');
      await pane('chat');
      await page.locator('#commentEditor').fill('');
      await pane('properties');
      readonly = true; await page.evaluate(() => SharedWorkAccess.openObject('TASK', '41')); await page.locator('#taskStatus:disabled').waitFor();
      assert(await page.locator('#editToggleBtn').isDisabled()); assert(await page.locator('#saveTaskBtn').isDisabled());
      await page.locator('#taskPeople [data-employee-identity="anna"]').first().click(); await page.locator('#employeeProfileTitle').filter({ hasText: 'Анна Смирнова' }).waitFor(); await page.keyboard.press('Escape');
      failProfile = true; await page.locator('#taskPeople [data-employee-identity="anna"]').first().click(); await page.locator('#employeeProfileDialog [role="alert"]').waitFor();
      failProfile = false; await page.locator('#employeeProfileDialog').getByText('Повторить', { exact: true }).click(); await page.locator('#employeeProfileTitle').filter({ hasText: 'Анна Смирнова' }).waitFor(); await page.keyboard.press('Escape');
      // Use the production dialog participant renderer inside the existing modal to verify links and scope.
      await pane('description');
      await page.addScriptTag({ url: origin + '/js/dialogs-participants-runtime.js' });
      await page.evaluate(() => {
        const list = document.createElement('div'); list.id = 'dialogPeopleFixture'; document.querySelector('.tasks-task-primary').append(list);
        const runtime = DialogsParticipantsRuntime.createRuntime({ elements: { detailsParticipantsList: list }, normalizeIdentity: x => String(x || '').toLowerCase() });
        runtime.setParticipantsState([{ username: 'victor', displayName: 'Виктор Крюков', department: 'Разработка' }]); runtime.renderDialogParticipantsState();
      });
      await page.locator('#dialogPeopleFixture .employee-identity').click(); await page.locator('#employeeProfileTitle').filter({ hasText: 'Виктор Крюков' }).waitFor();
      assert.match(await page.locator('#employeeProfileDialog').innerText(), /Разработка/);
      await page.screenshot({ path: path.join(out, `employee-${theme}-${width}.png`) }); await page.keyboard.press('Escape');
      await page.addScriptTag({ url: origin + '/js/dialogs-avatar-runtime.js' });
      await page.addScriptTag({ url: origin + '/js/dialogs-details-runtime.js' });
      await page.evaluate(async () => {
        const host = document.createElement('section'); host.id = 'dialogResponsibleFixture'; document.querySelector('.tasks-task-primary').append(host);
        const runtime = DialogsDetailsRuntime.createRuntime({ elements: { detailsModalEl: document.getElementById('taskModal'), detailsSummary: host }, getActiveDialogState: () => ({ ticketId: 'T-41' }) });
        await runtime.openDialogDetails('T-41'); window.__detailsFixture = runtime;
        const cell = document.createElement('div'); cell.id = 'dialogResponsibleCellFixture'; host.after(cell);
        cell.innerHTML = DialogsAvatarRuntime.createRuntime().renderResponsibleCell('Анна Смирнова', '/fixture/avatar.svg', 'anna');
      });
      assert.equal(await page.locator('#dialogResponsibleFixture [data-summary-field="responsible"] a').getAttribute('href'), '/users/anna', 'Initial dialog summary uses username, not display name, for profile');
      await page.locator('#dialogResponsibleCellFixture a').click(); await page.locator('#employeeProfileTitle').filter({ hasText: 'Анна Смирнова' }).waitFor(); await page.keyboard.press('Escape');
      await page.evaluate(() => __detailsFixture.updateDetailsResponsible('Виктор Крюков', { rawResponsible: 'victor' }));
      await page.locator('#dialogResponsibleFixture [data-summary-field="responsible"] a').click(); await page.locator('#employeeProfileTitle').filter({ hasText: 'Виктор Крюков' }).waitFor(); await page.keyboard.press('Escape');
      assert.deepEqual(errors, []); assert.deepEqual(missing, []);
      report.push({ theme, width, writes: writes.length, overflow, errors, missing }); await context.close();
    }
    fs.writeFileSync(path.join(out, 'report.json'), JSON.stringify(report, null, 2)); console.log(`GREEN: ${report.length} viewport/theme scenarios; task edit/save/retry, identities/avatars, read-only profiles and dialog participants.`);
  } finally { await browser.close(); await new Promise(resolve => server.close(resolve)); }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
