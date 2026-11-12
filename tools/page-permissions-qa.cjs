/* Local fixtures with the catalog captured from the real /api/auth/state MVC test. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const assets = path.join(root, 'spring-panel/src/main/resources/static');
const output = path.join(root, 'spring-panel/target/page-permissions-qa');
const source = JSON.parse(fs.readFileSync(path.join(root, 'spring-panel/target/page-permissions-catalog.json'), 'utf8'));
const fixture = `<!doctype html><html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="_csrf" content="fixture-token"><link rel="stylesheet" href="/vendor/bootstrap/5.3.3/bootstrap.min.css">
<link rel="stylesheet" href="/css/settings.css"></head><body><main class="container py-3"><h1>Роли и права</h1>
<div data-auth-root><p data-auth-message role="status"></p><section data-auth-roles-section>
<div data-auth-roles-empty></div><div data-auth-roles-container></div></section></div>
<button class="btn btn-primary mt-3" data-user-permissions="7">Права пользователя</button></main>
<script src="/vendor/bootstrap/5.3.3/bootstrap.bundle.min.js"></script><script src="/js/common.js"></script>
<script src="/js/auth-management.js"></script><script src="/js/user-permissions.js"></script>
<script>window.AuthManagement.mount(document.querySelector('[data-auth-root]'));</script></body></html>`;

async function main() {
  assert.deepEqual(source.catalog.pages.map(page => page.key), [
    'dialogs', 'ai_ops', 'tasks', 'clients', 'object_passports', 'knowledge_base', 'dashboard', 'analytics', 'settings']);
  fs.mkdirSync(output, { recursive: true });
  const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
  const server = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://local');
    if (url.pathname === '/') { res.setHeader('Content-Type', 'text/html; charset=utf-8'); res.end(fixture); return; }
    const file = path.resolve(assets, url.pathname.slice(1));
    if (!file.startsWith(assets + path.sep) || !fs.existsSync(file)) { res.writeHead(404); res.end(); return; }
    res.setHeader('Content-Type', path.extname(file) === '.js' ? 'text/javascript' : 'text/css');
    fs.createReadStream(file).pipe(res);
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const origin = `http://127.0.0.1:${server.address().port}`;
  let browser;
  try {
    browser = await chromium.launch({ headless: true, ...(process.env.PLAYWRIGHT_BROWSER_EXECUTABLE
      ? { executablePath: process.env.PLAYWRIGHT_BROWSER_EXECUTABLE } : {}) });
    for (const width of [320, 1440]) for (const mode of ['viewer', 'editor']) {
      const page = await browser.newPage({ viewport: { width, height: 1000 } });
      const errors = [], roleWrites = [], userWrites = [];
      page.on('pageerror', error => errors.push(error.message));
      const state = structuredClone(source);
      state.users = [];
      state.roles = [{ id: 21, name: 'Поддержка', description: '', is_admin: false,
        permissions: { pages: ['dialogs', 'channels', 'user_management'], fields: { edit: [], view: [] } } }];
      for (const field of Object.keys(state.capabilities.fields.edit)) state.capabilities.fields.edit[field] = mode === 'editor';
      for (const field of Object.keys(state.capabilities.fields.view)) state.capabilities.fields.view[field] = mode === 'editor';
      const userRows = state.catalog.pages.map(item => ({ kind: 'page', key: item.key, label: item.label,
        authority: 'PAGE_' + item.key.toUpperCase(), inherited: item.key === 'dialogs', override: 'inherit',
        effective: item.key === 'dialogs', can_override: mode === 'editor' }));
      await page.route('**/api/**', async route => {
        const request = route.request(), endpoint = new URL(request.url()).pathname;
        if (endpoint === '/api/auth/state') return route.fulfill({ json: state });
        if (endpoint === '/api/roles/21') {
          assert.equal(request.method(), 'PUT');
          assert.equal(request.headers()['x-xsrf-token'], 'fixture-token');
          const payload = request.postDataJSON(); roleWrites.push(payload);
          if (payload.permissions?.pages) state.roles[0].permissions.pages = payload.permissions.pages;
          if (payload.description !== undefined) state.roles[0].description = payload.description;
          return route.fulfill({ json: { success: true } });
        }
        assert.equal(endpoint, '/api/users/7/permissions');
        if (request.method() === 'PUT') {
          const payload = request.postDataJSON(); userWrites.push(payload);
          for (const row of userRows) if (payload[row.authority]) {
            row.override = payload[row.authority];
            row.effective = row.override === 'inherit' ? row.inherited : row.override === 'allow';
          }
        }
        return route.fulfill({ json: { success: true, username: 'Оператор', rows: userRows, can_edit: mode === 'editor' } });
      });
      await page.goto(origin);
      await page.locator('.role-card').waitFor();
      await page.locator('.role-card__toggle').click();
      await page.locator('.role-card .collapse.show').waitFor();
      const pages = page.locator('[data-permission-group="pages"]');
      assert.deepEqual(await pages.locator('.form-check-label').allTextContents(),
        ['Все страницы', ...source.catalog.pages.map(item => item.label)]);
      assert.equal(await pages.locator('[value="channels"], [value="user_management"]').count(), 0);
      const ai = pages.locator('[value="ai_ops"]');
      assert.equal(await ai.isDisabled(), mode === 'viewer');
      assert.equal(await pages.locator('[value="dialogs"]').isChecked(), true);
      assert.equal(await ai.isChecked(), false);
      assert.equal(await page.getByText(source.catalog.page_access_note, { exact: true }).isVisible(), true);
      if (mode === 'editor') {
        await ai.check();
        await pages.locator('[value="dialogs"]').uncheck();
        await page.locator('[data-action="save-role"]').click();
        await page.waitForFunction(() => JSON.parse(document.querySelector('.role-card')?.dataset.initialPages || '[]').includes('ai_ops'));
        assert.deepEqual(roleWrites[0].permissions.pages.slice().sort(), ['ai_ops', 'channels', 'user_management']);
        assert.equal(roleWrites[0].permissions.fields, undefined);
        // A later description-only save must not emit any page changes or lose hidden legacy grants.
        await page.locator('.role-card__toggle').click();
        await page.locator('[data-field="description"]').fill('Описание роли');
        await page.locator('[data-action="save-role"]').click();
        await page.waitForFunction(() => document.querySelector('.role-card')?.dataset.initialDescription === 'Описание роли');
        assert.deepEqual(roleWrites[1], { description: 'Описание роли' });
        await page.locator('.role-card__toggle').click();
        await pages.locator('[value="*"]').check();
        assert.equal(await pages.locator('[value="ai_ops"]').isDisabled(), true);
        await page.locator('[data-action="save-role"]').click();
        await page.waitForFunction(() => document.querySelector('.role-card')?.dataset.initialPages === '["*"]');
        assert.deepEqual(roleWrites[2], { permissions: { pages: ['*'] } });
      }
      if (!(await page.locator('.role-card .collapse').isVisible())) await page.locator('.role-card__toggle').click();
      await page.locator('.role-card .collapse.show').waitFor();
      await page.screenshot({ path: path.join(output, `roles-${mode}-${width}.png`), fullPage: true });
      await page.locator('[data-user-permissions]').click();
      await page.locator('#userPermissionsModal.show').waitFor();
      const override = page.locator('select[data-authority="PAGE_AI_OPS"]');
      await override.waitFor();
      assert.equal(await override.isDisabled(), mode === 'viewer');
      assert.equal(await page.getByRole('cell', { name: 'AI Ops', exact: true }).count(), 1);
      assert.equal(await page.getByRole('cell', { name: 'Каналы', exact: true }).count(), 0);
      if (mode === 'editor') {
        await override.selectOption('allow');
        await page.locator('select[data-authority="PAGE_DIALOGS"]').selectOption('deny');
        await page.locator('[data-permissions-save]').click();
        await page.waitForFunction(() => document.querySelector('[data-permissions-message]').textContent.includes('Права сохранены'));
        assert.deepEqual(userWrites, [{ PAGE_DIALOGS: 'deny', PAGE_AI_OPS: 'allow' }]);
      }
      await page.screenshot({ path: path.join(output, `users-${mode}-${width}.png`), fullPage: true });
      assert.deepEqual(errors, []);
      await page.close();
    }
    console.log('Page permissions QA passed: current catalog, AI Ops, preserved legacy grants, independent user rights, viewer mode and wildcard at 320/1440 px.');
  } finally {
    await browser?.close(); await new Promise(resolve => server.close(resolve));
  }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
