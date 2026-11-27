/* Actual Thymeleaf users fragment + production CSS/JS; all writes use loopback fixtures. */
const assert = require('node:assert/strict');
const fs = require('node:fs'), path = require('node:path'), http = require('node:http');
const root = path.resolve(__dirname, '..'), resources = path.join(root, 'spring-panel/src/main/resources');
const out = path.join(root, 'spring-panel/target/permission-editors-qa');
const userId = '9007199254740993';
const pages = ['Главная', 'Диалоги', 'Задачи', 'Проекты', 'Настройки', 'Отчёты', 'Бизнесы', 'Сотрудники']
  .map((label, i) => ({ key: ['home', 'dialogs', 'tasks', 'projects', 'settings', 'reports', 'businesses', 'employees'][i], label }));
const edits = [{ key: 'user.username', label: 'Изменение профилей пользователей' },
  ...Array.from({ length: 38 }, (_, i) => ({ key: 'settings.block' + i, label: `Изменение настроек: раздел ${i + 1}` }))];
const views = [{ key: 'user.password', label: 'Просмотр паролей' }, { key: 'user.contacts', label: 'Контактные данные' }];
function permissionRows(editable) {
  return [
    ...pages.map(p => ({ kind: 'page', ...p, authority: 'PAGE_' + p.key.toUpperCase(), inherited: true, effective: true })),
    { kind: 'settings', key: 'dialogs', label: 'Категории и настройки диалогов', authority: 'EDIT_SETTINGS_DIALOGS', inherited: false, effective: false },
    ...edits.slice(1).map((p, i) => ({ kind: 'settings', ...p, authority: 'EDIT_SETTINGS_BLOCK' + i, inherited: i % 2 === 0, effective: i % 2 === 0 })),
    { kind: 'action', key: 'role.delete', label: 'Удаление ролей', authority: 'EDIT_ROLE_DELETE', inherited: false, effective: false, can_override: false },
  ].map(row => ({ override: 'inherit', can_override: editable, ...row }));
}
function initialRoles() {
  return [{ id: 1, name: 'Системный администратор', description: 'Защищённая роль', is_admin: true,
    permissions: { pages: ['*'], fields: { edit: ['*'], view: ['*'] } } },
  { id: 2, name: 'Оператор поддержки', description: 'Диалоги и рабочие задачи',
    permissions: { pages: ['settings', 'tasks', 'legacy_dashboard'], fields: { edit: ['user.username'], view: [] } } }];
}
function authState(editable, roles) {
  return { current_user_id: 99, users: [{ id: 7, permissions_user_id: userId, username: 'operator', full_name: 'Анна Смирнова', role_id: 2, role: 'Оператор поддержки' }],
    roles, catalog: { pages, legacy_pages: ['legacy_dashboard'], page_access_note: 'Просмотр страницы не даёт права изменять её настройки.', fields: { edit: edits, view: views } },
    capabilities: { fields: { edit: Object.fromEntries(['role.name', 'role.description', 'role.pages', 'role.fields.edit', 'role.fields.view', 'role.delete'].map(key => [key, editable])), view: {} } }, org_structure: { nodes: [] } };
}
async function main() {
  const capture = fs.readFileSync(path.join(out, 'users-workspace.html'), 'utf8');
  const settings = fs.readFileSync(path.join(resources, 'templates/settings/index.html'), 'utf8');
  const css = [...settings.matchAll(/<link th:href="@\{(\/css\/[^()]+)\(v='[^']+'\)\}" rel="stylesheet"/g)].map(m => m[1]);
  assert.deepEqual(css, ['/css/style.css', '/css/app.css', '/css/sidebar.css', '/css/settings.css', '/css/permission-editor.css']);
  const directory = fs.readFileSync(path.join(resources, 'templates/users/index.html'), 'utf8');
  assert(directory.includes('/css/permission-editor.css'));
  assert(directory.indexOf('/js/permission-editor.js') < directory.indexOf('/js/user-permissions.js'));
  assert(settings.indexOf('/js/permission-editor.js') < settings.indexOf('/js/user-permissions.js'));
  assert(settings.indexOf('/js/permission-editor.js') < settings.indexOf('/js/auth-management.js'));
  const html = `<!doctype html><html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><meta name="_csrf" content="permissions-fixture-csrf">
    <link rel="stylesheet" href="/vendor/bootstrap/5.3.0/bootstrap.min.css"><link rel="stylesheet" href="/vendor/bootstrap-icons/1.10.5/bootstrap-icons.css">
    ${css.map(file => `<link rel="stylesheet" href="${file}">`).join('')}
    <script src="/js/ui-preferences.js"></script><script src="/js/theme.js"></script><script src="/js/common.js"></script></head>
    <body data-ui-page="settings"><button id="open" class="btn btn-primary">Пользователи и роли</button>${capture}
    <script src="/vendor/bootstrap/5.3.0/bootstrap.bundle.min.js"></script><script src="/js/modal-helpers.js"></script><script src="/js/modal-resize.js"></script>
    <script src="/js/settings-permissions.js"></script><script src="/js/permission-editor.js"></script><script src="/js/user-permissions.js"></script><script src="/js/auth-management-photo.js"></script><script src="/js/auth-management-user.js"></script><script src="/js/auth-management-roles.js"></script><script src="/js/auth-management-organization.js"></script><script src="/js/auth-management.js"></script>
    <script>AuthManagement.mount(document.querySelector('[data-auth-management]'));document.getElementById('open').onclick=()=>bootstrap.Modal.getOrCreateInstance(document.getElementById('usersModal')).show();</script></body></html>`;
  fs.writeFileSync(path.join(out, 'fixture.html'), html);
  const server = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://local');
    if (url.pathname === '/settings') { res.setHeader('Content-Type', 'text/html; charset=utf-8'); res.end(html); return; }
    const file = path.resolve(resources, 'static', url.pathname.slice(1));
    if (!file.startsWith(path.join(resources, 'static') + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) { res.writeHead(404); res.end(); return; }
    const ext = path.extname(file), mime = { '.css': 'text/css', '.js': 'application/javascript', '.woff2': 'font/woff2', '.woff': 'font/woff', '.svg': 'image/svg+xml' };
    res.setHeader('Content-Type', mime[ext] || 'application/octet-stream');
    res.end(['.css', '.js'].includes(ext) ? fs.readFileSync(file, 'utf8').replace(/\r\n/g, '\n') : fs.readFileSync(file));
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const origin = `http://127.0.0.1:${server.address().port}`;
  const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright'); let browser;
  const results = [];
  try {
    browser = await chromium.launch({ headless: true, executablePath: process.env.PLAYWRIGHT_BROWSER_EXECUTABLE });
    for (const [width, height, scale = 100] of [[1440, 900], [1280, 360], [768, 480], [390, 640], [320, 640], [1024, 480, 150]]) {
      for (const theme of ['light', 'dark']) {
        const editable = !(width === 768 || (width === 320 && theme === 'light'));
        const context = await browser.newContext({ viewport: { width, height }, reducedMotion: 'reduce' });
        const errors = [], writes = [], missing = []; let rows = permissionRows(editable), roles = initialRoles(), failNext = editable;
        await context.route('**/*', async route => {
          const request = route.request(), url = new URL(request.url());
          if (url.origin !== origin) { errors.push('External request: ' + url.href); return route.abort(); }
          if (url.pathname === '/api/auth/permissions') return route.fulfill({ json: { success: true, accounts: true, settings: {} } });
          if (url.pathname === '/api/auth/state') return route.fulfill({ json: authState(editable, roles) });
          if (url.pathname === '/api/users/activity') return route.fulfill({ json: { success: true, users: [] } });
          if (request.method() === 'PUT') {
            assert(editable); assert.equal(request.headers()['x-xsrf-token'], 'permissions-fixture-csrf');
            const payload = request.postDataJSON(); writes.push({ endpoint: url.pathname, payload });
            if (url.pathname === '/api/roles/2') {
              roles[1].permissions = { ...roles[1].permissions, ...payload.permissions };
              return route.fulfill({ json: { success: true } });
            }
            assert.equal(url.pathname, '/api/users/' + userId + '/permissions');
            if (failNext) { failNext = false; return route.fulfill({ status: 422, json: { success: false, error: 'Проверочная ошибка сохранения' } }); }
            rows = rows.map(row => ({ ...row, override: payload[row.authority] ?? row.override,
              effective: payload[row.authority] === undefined ? row.effective : payload[row.authority] === 'inherit' ? row.inherited : payload[row.authority] === 'allow' }));
          }
          if (url.pathname === '/api/users/' + userId + '/permissions') return route.fulfill({ json: { success: true, username: 'Анна Смирнова', can_edit: editable, rows } });
          if (url.pathname.startsWith('/api/')) { errors.push('Unexpected API: ' + url.pathname); return route.fulfill({ status: 404, json: {} }); }
          return route.continue();
        });
        const page = await context.newPage(); page.on('pageerror', error => errors.push(error.message));
        page.on('response', response => { if (response.status() === 404) missing.push(response.url()); });
        await page.goto(origin + '/settings');
        await page.evaluate(theme => { ThemeRuntime.setPalette('neo'); ThemeRuntime.setTheme(theme); }, theme);
        if (scale !== 100) await page.evaluate(scale => document.documentElement.style.setProperty('--iguana-page-root-font-size', `${parseFloat(document.documentElement.dataset.pageFontScaleBasePx) * scale / 100}px`), scale);
        await page.locator('[data-role-id="2"]').waitFor({ state: 'attached' });
        await page.locator('#open').click(); await page.locator('#usersModal.show').waitFor();
        await page.locator('#auth-roles-tab').click();
        const card = page.locator('[data-role-id="2"]');
        await card.locator('.role-card__toggle').click();
        await card.locator('.permissions-editor__search input').waitFor();
        assert.equal(await card.locator('details[open]').count(), 0);
        const pageGroup = card.locator('[data-permission-group="pages"]');
        await card.locator('summary').first().focus(); await page.keyboard.press('Enter');
        assert.equal(await pageGroup.locator('input[value="projects"]').isDisabled(), !editable);
        if (editable) {
          await pageGroup.locator('label').filter({ hasText: 'Проекты' }).click();
          assert.equal(await pageGroup.locator('input[value="projects"]').isChecked(), true);
          const viewGroup = card.locator('[data-permission-group="fields-view"]');
          await card.locator('summary').nth(2).click();
          await viewGroup.locator('input[value="*"]').check();
          assert.equal(await viewGroup.locator('input[value="user.password"]').isDisabled(), true);
          await viewGroup.locator('input[value="*"]').uncheck();
          assert.equal(await viewGroup.locator('input[value="user.password"]').isDisabled(), false);
        }
        const roleOpen = await card.locator('details').evaluateAll(nodes => nodes.map(node => node.open));
        const roleSearch = card.locator('input[type="search"]'); await roleSearch.fill('Проекты');
        assert.equal(await card.locator('.permissions-editor__choice:not([hidden])').count(), 1);
        await page.screenshot({ path: path.join(out, `role-${theme}-${width}.png`) });
        await roleSearch.fill(''); assert.deepEqual(await card.locator('details').evaluateAll(nodes => nodes.map(node => node.open)), roleOpen);
        await roleSearch.fill('Несуществующее разрешение'); assert.equal(await card.locator('.permissions-editor__empty').isVisible(), true);
        if (editable) {
          await card.locator('[data-action="save-role"]').click();
          await page.waitForFunction(() => document.querySelector('[data-role-id="2"] input[value="projects"]').checked && document.querySelector('[data-role-id="2"] .role-card__toggle').getAttribute('aria-expanded') === 'false');
          assert.equal(writes.length, 1);
          assert.deepEqual([...writes[0].payload.permissions.pages].sort(), ['legacy_dashboard', 'projects', 'settings', 'tasks']);
          assert.deepEqual(Object.keys(writes[0].payload), ['permissions']);
        } else assert.equal(await card.locator('[data-action="save-role"]').isDisabled(), true);
        const admin = page.locator('[data-role-id="1"]'); await admin.locator('.role-card__toggle').click();
        assert.equal(await admin.locator('[data-field="name"]').isDisabled(), true);
        assert.equal(await admin.locator('[data-action="delete-role"]').isDisabled(), true);
        assert.equal(await admin.locator('input[data-permission]:not(:disabled)').count(), 0);
        await page.locator('#auth-users-tab').click();
        const row = page.locator('[data-auth-users-body] tr').first(); await row.locator('[data-bs-toggle="dropdown"]').click();
        await row.locator('[data-user-permissions]').click();
        const modal = page.locator('#userPermissionsModal');
        const setting = modal.locator('select[data-authority="EDIT_SETTINGS_DIALOGS"]');
        await setting.waitFor({ state: 'attached' }); assert.equal(await modal.locator('details[open]').count(), 0);
        const search = modal.locator('input[type="search"]'); await search.fill('Категории');
        assert.equal(await setting.isDisabled(), !editable);
        if (editable) {
          await setting.selectOption('allow');
          assert.equal(await setting.locator('xpath=ancestor::div[contains(@class,"permissions-editor__row")]').locator('[data-allowed="true"]').count(), 1);
          await search.fill('Настройки'); await modal.locator('select[data-authority="PAGE_SETTINGS"]').selectOption('deny');
        }
        await search.fill('Удаление ролей'); assert.equal(await modal.locator('select[data-authority="EDIT_ROLE_DELETE"]').isDisabled(), true);
        await search.fill('Несуществующее разрешение');
        if (editable) {
          await modal.locator('[data-permissions-save]').click();
          await modal.locator('[data-permissions-message]').filter({ hasText: 'Проверочная ошибка' }).waitFor();
          assert.equal(await setting.inputValue(), 'allow'); assert.equal(await setting.isDisabled(), false);
          assert.equal(await modal.locator('select[data-authority="EDIT_ROLE_DELETE"]').isDisabled(), true);
          await page.evaluate(() => { const button = document.querySelector('[data-permissions-save]'); button.click(); button.click(); });
          await modal.locator('[data-permissions-message]').filter({ hasText: 'Права сохранены' }).waitFor();
          assert.equal(await search.inputValue(), 'Несуществующее разрешение');
          const userWrites = writes.filter(w => w.endpoint.includes('/users/'));
          assert.equal(userWrites.length, 2);
          userWrites.forEach(write => assert.deepEqual(write.payload, { PAGE_SETTINGS: 'deny', EDIT_SETTINGS_DIALOGS: 'allow' }));
        } else assert.equal(await modal.locator('[data-permissions-save]').isDisabled(), true);
        await search.fill('');
        const closeContrast = await modal.evaluate(node => {
          const button = getComputedStyle(node.querySelector('.modal-header .btn-close'));
          const fill = button.backgroundImage.match(/%23([0-9a-f]{6}|[0-9a-f]{3})(?:['"%]|\b)/i);
          if (!fill) return 0;
          let hex = fill[1]; if (hex.length === 3) hex = [...hex].map(c => c + c).join('');
          let foreground = [0, 2, 4].map(i => parseInt(hex.slice(i, i + 2), 16));
          if (button.filter.includes('invert(1)')) foreground = foreground.map(v => 255 - v);
          const background = getComputedStyle(node.querySelector('.modal-content')).backgroundColor.match(/[\d.]+/g).slice(0, 3).map(Number);
          const luminance = rgb => rgb.map(v => v / 255).map(v => v <= .04045 ? v / 12.92 : ((v + .055) / 1.055) ** 2.4).reduce((sum, v, i) => sum + v * [.2126, .7152, .0722][i], 0);
          const a = luminance(foreground), b = luminance(background);
          return (Math.max(a, b) + .05) / (Math.min(a, b) + .05);
        });
        assert(closeContrast >= 3, `Close icon contrast: ${closeContrast}`);
        const bounds = await modal.evaluate(node => {
          const box = node.querySelector('.modal-content').getBoundingClientRect(), footer = node.querySelector('.modal-footer').getBoundingClientRect();
          return { top: box.top, bottom: box.bottom, footer: footer.bottom, height: innerHeight, overflow: document.documentElement.scrollWidth > innerWidth + 1 };
        });
        assert(bounds.top >= -1 && bounds.bottom <= height + 1 && bounds.footer <= height + 1, JSON.stringify(bounds)); assert.equal(bounds.overflow, false);
        await modal.locator('summary').nth(1).click();
        assert.equal(await modal.locator('.modal-body').evaluate(node => node.scrollHeight > node.clientHeight), true);
        assert((await modal.locator('.modal-footer').boundingBox()).y + (await modal.locator('.modal-footer').boundingBox()).height <= height + 1);
        await page.screenshot({ path: path.join(out, `user-${theme}-${width}.png`) });
        const beforeClose = writes.length; await modal.locator('.modal-header [data-bs-dismiss="modal"]').click();
        await modal.waitFor({ state: 'hidden' }); assert.equal(writes.length, beforeClose);
        assert.deepEqual(errors, []); assert.deepEqual(missing, []);
        results.push({ width, height, theme, scale, editable, writes: writes.length, errors, missing });
        console.log(`PASS ${theme} ${width}x${height} ${scale}% ${editable ? 'editor' : 'readonly'}`);
        await context.close();
      }
    }
    fs.writeFileSync(path.join(out, 'report.json'), JSON.stringify({ passed: true, results }, null, 2));
  } finally { await browser?.close(); await new Promise(resolve => server.close(resolve)); }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
