/* Local browser fixtures; no requests reach a working panel or database. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const assets = path.join(root, 'spring-panel/src/main/resources/static');
const output = path.join(root, 'spring-panel/target/settings-permissions-qa');
const rows = [
  { kind: 'page', key: 'settings', label: 'Настройки', authority: 'PAGE_SETTINGS', inherited: true, override: 'inherit', effective: true },
  { kind: 'settings', key: 'dialogs', label: 'Категории и настройки диалогов', authority: 'EDIT_SETTINGS_DIALOGS', inherited: false, override: 'inherit', effective: false },
  { kind: 'settings', key: 'backup', label: 'Резервное копирование', authority: 'EDIT_SETTINGS_BACKUP', inherited: false, override: 'inherit', effective: false },
];
const fixture = `<!doctype html><html lang="ru"><head><meta charset="utf-8"><meta name="_csrf" content="fixture-token"><meta name="viewport" content="width=device-width, initial-scale=1">
<link rel="stylesheet" href="/vendor/bootstrap/5.3.3/bootstrap.min.css"><link rel="stylesheet" href="/css/style.css"></head>
<body><main class="container py-3"><h1>Настройки</h1>
<section id="categoriesModal" class="card p-3"><h2 class="h5">Категории обращений</h2><input id="category" class="form-control" value="Касса">
<select class="form-select"><option>P1</option><option>P2</option></select><button id="saveCategories">Сохранить</button>
<button data-dialog-template-toggle>Посмотреть категории</button></section>
<section id="backupSettingsModal" class="card p-3 my-3"><h2 class="h5">Резервное копирование</h2><input id="backupPath" value="/backups"><button id="saveBackup">Сохранить</button></section>
<section id="channelsModal" class="card p-3"><h2 class="h5">Каналы</h2><button id="createChannel">Создать канал</button>
<section id="channels-templates"><h3 class="h6">Шаблоны</h3><input id="botTemplate" value="Вопросы"><button id="saveTemplate">Сохранить шаблон</button></section></section>
<button class="btn btn-primary mt-3" data-user-permissions="7">Права пользователя</button></main>
<script src="/vendor/bootstrap/5.3.3/bootstrap.bundle.min.js"></script><script src="/js/common.js"></script><script src="/js/settings-permissions.js"></script><script src="/js/user-permissions.js"></script></body></html>`;

async function main() {
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
      const errors = [], writes = [];
      let state = rows.map(row => ({ ...row, can_override: mode === 'editor' }));
      page.on('pageerror', error => errors.push(error.message));
      await page.route('**/api/**', async route => {
        const endpoint = new URL(route.request().url()).pathname;
        if (endpoint === '/api/auth/permissions') return route.fulfill({ json: { success: true,
          settings: { dialogs: mode === 'editor', bot_templates: mode === 'editor' } } });
        assert.equal(endpoint, '/api/users/7/permissions');
        if (route.request().method() === 'PUT') {
          assert.equal(route.request().headers()['x-xsrf-token'], 'fixture-token');
          const payload = route.request().postDataJSON(); writes.push(payload);
          state = state.map(row => ({ ...row, override: payload[row.authority] ?? row.override,
            effective: payload[row.authority] === undefined ? row.effective : payload[row.authority] === 'inherit' ? row.inherited : payload[row.authority] === 'allow' }));
        }
        return route.fulfill({ json: { success: true, username: 'Оператор поддержки', rows: state, can_edit: mode === 'editor' } });
      });
      await page.goto(origin);
      await page.waitForFunction(() => Boolean(window.SettingsPermissions));
      assert.equal(await page.locator('#category').isDisabled(), mode === 'viewer');
      assert.equal(await page.locator('#saveCategories').isDisabled(), mode === 'viewer');
      assert.equal(await page.locator('#backupPath').isDisabled(), true);
      assert.equal(await page.locator('#saveBackup').isDisabled(), true);
      assert.equal(await page.locator('#createChannel').isDisabled(), true);
      assert.equal(await page.locator('#botTemplate').isDisabled(), mode === 'viewer');
      assert.equal(await page.locator('[data-dialog-template-toggle]').isDisabled(), false);
      await page.evaluate(() => { const input = document.createElement('input'); input.id = 'dynamicBackup'; document.getElementById('backupSettingsModal').append(input); });
      await page.waitForFunction(() => document.getElementById('dynamicBackup').disabled);
      await page.locator('[data-user-permissions]').click();
      await page.locator('#userPermissionsModal.show').waitFor();
      await page.locator('#userPermissionsModal tbody tr').first().waitFor();
      const setting = page.locator('select[data-authority="EDIT_SETTINGS_DIALOGS"]');
      assert.equal(await setting.isDisabled(), mode === 'viewer');
      assert.equal(await page.locator('[data-permissions-save]').isDisabled(), mode === 'viewer');
      if (mode === 'editor') {
        await setting.selectOption('allow');
        await page.locator('select[data-authority="PAGE_SETTINGS"]').selectOption('deny');
        await page.locator('[data-permissions-save]').click();
        await page.waitForFunction(() => document.querySelector('[data-permissions-message]').textContent.includes('Права сохранены'));
        assert.deepEqual(writes, [{ PAGE_SETTINGS: 'deny', EDIT_SETTINGS_DIALOGS: 'allow' }]);
        assert.equal(await page.locator('select[data-authority="EDIT_SETTINGS_BACKUP"]').inputValue(), 'inherit');
      }
      await page.screenshot({ path: path.join(output, `${mode}-${width}.png`), fullPage: true });
      assert.deepEqual(errors, []);
      await page.close();
    }
    console.log('Settings permissions QA passed: page viewers, separate block editors, nested sections, dynamic controls, individual allow/deny and unchanged inheritance at 320/1440 px.');
  } finally {
    await browser?.close(); await new Promise(resolve => server.close(resolve));
  }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
