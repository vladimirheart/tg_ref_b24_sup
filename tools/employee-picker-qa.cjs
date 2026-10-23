/* Реальные MVC-страницы задач/проектов/настроек; записи перехватываются fixture API. */
const fs = require('node:fs'), path = require('node:path'), http = require('node:http'), assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..'), resources = path.join(root, 'spring-panel/src/main/resources/static');
const captures = path.join(root, 'spring-panel/target/neutral-ops-rendered'), output = path.join(root, 'spring-panel/target/employee-picker-r12-qa');
const users = [{ id: 1, username: 'anna', full_name: 'Анна Смирнова', department: 'Поддержка' }, { id: 2, username: 'victor', full_name: 'Виктор Крюков', department: 'Поддержка' }];
async function main() {
  fs.mkdirSync(output, { recursive: true });
  const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
  const server = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://localhost'), screen = url.pathname.startsWith('/screens/'), base = screen ? captures : resources;
    const file = path.resolve(base, screen ? url.pathname.slice(9) + '.html' : url.pathname.slice(1));
    if (!file.startsWith(base + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) { res.writeHead(404); res.end('{}'); return; }
    res.setHeader('Content-Type', ({ '.html': 'text/html; charset=utf-8', '.css': 'text/css', '.js': 'text/javascript', '.svg': 'image/svg+xml' })[path.extname(file)] || 'application/octet-stream');
    fs.createReadStream(file).pipe(res);
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const origin = `http://127.0.0.1:${server.address().port}`, browser = await chromium.launch({ headless: true }), results = [];
  try {
    for (const theme of ['light', 'dark']) for (const width of [320, 768, 1440]) {
      const context = await browser.newContext({ viewport: { width, height: 1000 }, colorScheme: theme, hasTouch: width === 320, isMobile: width === 320, reducedMotion: 'reduce' });
      await context.addInitScript(theme => { localStorage.setItem('iguana:theme', theme); localStorage.setItem('sidebarPinned', '1'); }, theme);
      const writes = [], errors = [], missing = [];
      await context.route('**/*', async route => {
        const req = route.request(), url = new URL(req.url()); if (url.origin !== origin) return route.abort();
        if (!url.pathname.startsWith('/api/')) return route.continue();
        if (url.pathname === '/api/auth/state') return route.fulfill({ json: { users, roles: [], org_structure: {}, catalog: { pages: [], fields: { edit: [], view: [] } }, capabilities: { fields: { edit: {}, view: {} } } } });
        if (url.pathname === '/api/panel-users/options') return route.fulfill({ json: users.map(user => ({ value: user.username, label: user.full_name + ' · ' + user.username })) });
        if (req.method() === 'POST' && url.pathname === '/api/tasks') {
          const form = await new Request(req.url(), { method: 'POST', headers: req.headers(), body: req.postDataBuffer() }).formData();
          writes.push({ kind: 'task', values: Object.fromEntries(form) }); return route.fulfill({ json: { ok: true } });
        }
        if (req.method() === 'POST' && url.pathname === '/api/projects') { writes.push({ kind: 'project', values: req.postDataJSON() }); return route.fulfill({ json: { success: true } }); }
        assert(['GET', 'HEAD'].includes(req.method()), 'Unexpected write: ' + req.method() + ' ' + url.pathname);
        return route.fulfill({ json: {} });
      });
      const page = await context.newPage(); page.on('pageerror', ex => errors.push(ex.message));
      page.on('response', response => { if (response.status() === 404 && /\.(?:js|css)(?:\?|$)/.test(response.url())) missing.push(response.url()); });
      for (const screen of ['tasks', 'projects']) {
        await page.goto(origin + '/screens/' + screen + '/index', { waitUntil: 'load' });
        await page.evaluate(({ users, screen }) => {
          const list = document.getElementById(screen === 'tasks' ? 'usersList' : 'projectUsersList');
          list.replaceChildren(...users.map(user => { const option = document.createElement('option'); option.value = user.username; option.label = user.full_name + ' · ' + user.username; return option; }));
          if (screen === 'tasks') { const select = document.querySelector('#taskForm [name="assignee"]'); select.replaceChildren(new Option('Не выбран', ''), ...users.map(user => new Option(user.full_name, user.username))); }
          EmployeePicker.refresh();
        }, { users, screen });
        if (screen === 'tasks') {
          await page.locator('#createTaskBtn').click(); await page.locator('#taskModal.show').waitFor();
          await page.locator('#taskTitle').fill('Задача проверки выбора'); await page.locator('#bodyEditor').fill('Описание тестовой задачи');
          for (const [name, query, person] of [['assignee', 'анна', 'Анна'], ['co', 'виктор', 'Виктор'], ['watchers', 'смирнова', 'Анна']]) {
            const picker = page.locator(`#taskForm [name="${name}"]`).locator('..'); await picker.locator('.employee-picker__trigger').click();
            await picker.locator('input[type="search"]').fill(query);
            if (name === 'co') await page.keyboard.press('Enter'); else await picker.locator('.employee-picker__option', { hasText: person }).click();
            assert.equal(writes.length, 0, 'Enter in employee search must not submit the parent form');
            if (name !== 'assignee') await page.keyboard.press('Escape');
          }
          await page.screenshot({ path: path.join(output, `tasks-${theme}-${width}.png`) });
          await page.locator('#saveTaskBtn').click(); await page.locator('#taskModal').waitFor({ state: 'hidden' });
          const values = writes.find(item => item.kind === 'task')?.values; assert(values); assert.equal(values.assignee, 'anna'); assert.equal(values.co, 'victor'); assert.equal(values.watchers, 'anna');
        } else {
          await page.locator('#createProjectBtn').click(); await page.locator('#projectModal.show').waitFor();
          await page.locator('#projectForm [name="name"]').fill('Тестовый проект');
          const picker = page.locator('#projectForm [name="lead_identity"]').locator('..'); await picker.locator('.employee-picker__trigger').click(); await picker.locator('input[type="search"]').fill('виктор');
          await picker.locator('.employee-picker__option', { hasText: 'Виктор' }).click();
          await page.screenshot({ path: path.join(output, `projects-${theme}-${width}.png`) });
          await page.locator('#saveProjectBtn').click(); await page.locator('#projectModal').waitFor({ state: 'hidden' }); assert.equal(writes.find(item => item.kind === 'project').values.lead_identity, 'victor');
        }
      }
      await page.goto(origin + '/screens/settings/index', { waitUntil: 'load' });
      await page.evaluate(async users => {
        const manager = AuthManagement.mount(document.querySelector('[data-auth-management]')); await manager.refresh();
        manager.state.users = users; manager.populateOrgMembersModal({ id: 'test-node', members: [1] });
        bootstrap.Modal.getOrCreateInstance(document.getElementById('orgMembersModal')).show();
      }, users);
      await page.locator('#orgMembersModal.show').waitFor();
      const members = page.locator('[data-org-members-list] .employee-picker');
      await members.locator('.employee-picker__trigger').click(); await members.locator('input[type="search"]').fill('виктор');
      await members.locator('.employee-picker__option', { hasText: 'Виктор' }).click(); await page.keyboard.press('Escape');
      const values = await page.locator('[data-org-members-form]').evaluate(form => new FormData(form).getAll('memberIds'));
      assert.deepEqual(values, ['1', '2'], 'Native multiple select preserves existing org membership FormData contract');
      await page.screenshot({ path: path.join(output, `org-${theme}-${width}.png`) });
      assert.deepEqual(missing, [], 'Changed scripts and styles all load'); assert.deepEqual(errors, []);
      results.push({ theme, width, writes, members: values, missing, errors }); await context.close();
    }
    fs.writeFileSync(path.join(output, 'report.json'), JSON.stringify(results, null, 2)); console.log(JSON.stringify({ combinations: results.length, pageScenarios: results.length * 3, screenshots: results.length * 3, output }));
  } finally { await browser.close(); await new Promise(resolve => server.close(resolve)); }
}
main().catch(ex => { console.error(ex); process.exitCode = 1; });
