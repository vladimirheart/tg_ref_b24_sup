/* Browser QA with local fixtures: settings round trip and real dashboard charts. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const resources = path.join(root, 'spring-panel/src/main/resources');
const output = path.join(root, 'spring-panel/target/category-priorities-qa');
const read = file => fs.readFileSync(path.join(resources, file), 'utf8');
const dashboard = read('templates/dashboard/index.html').replace(
  /th:(href|src)="@\{([^}]+)\}"/g, (_, attr, value) => `${attr}="${value.split('(')[0]}"`);
const settings = `<!doctype html><html lang="ru"><head><meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <link rel="stylesheet" href="/vendor/bootstrap/5.3.3/bootstrap.min.css">
  <link rel="stylesheet" href="/css/style.css"><link rel="stylesheet" href="/css/settings.css">
  </head><body>${read('templates/settings/fragments/dialog-settings.html')}
  <script src="/vendor/bootstrap/5.3.3/bootstrap.bundle.min.js"></script>
  <script src="/js/settings-dialog-templates-runtime.js"></script></body></html>`;

async function main() {
  fs.mkdirSync(output, { recursive: true });
  const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
  const server = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://localhost');
    if (url.pathname === '/settings' || url.pathname === '/dashboard') {
      res.setHeader('Content-Type', 'text/html; charset=utf-8');
      res.end(url.pathname === '/settings' ? settings : dashboard);
      return;
    }
    const base = path.join(resources, 'static');
    const file = path.resolve(base, url.pathname.slice(1));
    if (!file.startsWith(base + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) {
      res.writeHead(404); res.end('{}'); return;
    }
    res.setHeader('Content-Type', ({ '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml' })[path.extname(file)] || 'application/octet-stream');
    fs.createReadStream(file).pipe(res);
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const origin = `http://127.0.0.1:${server.address().port}`;
  let browser;
  try {
    browser = await chromium.launch({ headless: true, ...(process.env.PLAYWRIGHT_BROWSER_EXECUTABLE
      ? { executablePath: process.env.PLAYWRIGHT_BROWSER_EXECUTABLE } : {}) });
    for (const width of [320, 1440]) {
      const page = await browser.newPage({ viewport: { width, height: 1000 } });
      const errors = [];
      page.on('pageerror', error => errors.push(error.message));
      let empty = false;
      await page.route('**/api/**', route => {
        const endpoint = new URL(route.request().url()).pathname;
        if (endpoint === '/api/dashboard/data') return route.fulfill({ json: {
          success: true, stats: { current: { total: empty ? 0 : 4 }, previous: { total: 0 } },
          charts: empty ? {} : {
            category: { 'Касса · P1': 2, 'Очень длинное название категории сетевого подключения · P2': 1, 'Вопрос · P5': 1 },
            priority: { P1: 2, P2: 0, P3: 0, P4: 0, P5: 1, 'Без критичности': 1 },
            direction: { 'Техническая поддержка': 3 }
          }
        } });
        if (endpoint === '/api/dashboard/restaurant-options') return route.fulfill({ json: { success: true, restaurants: [] } });
        return route.fulfill({ json: { success: true } });
      });
      await page.goto(origin + '/settings');
      await page.evaluate(() => {
        window.fixtureConfig = { category_templates: [{ id: 'legacy', name: 'Поддержка', categories: ['Старая'] }] };
        window.runtime = SettingsDialogTemplatesRuntime.mount({ getDialogConfig: () => window.fixtureConfig });
        document.addEventListener('click', event => {
          const trigger = event.target.closest('[data-category-row-add], [data-category-row-remove], [data-dialog-template-toggle]');
          if (!trigger) return;
          if (trigger.matches('[data-category-row-add]')) runtime.addCategoryRow(trigger);
          if (trigger.matches('[data-category-row-remove]')) runtime.removeCategoryRow(trigger);
          if (trigger.matches('[data-dialog-template-toggle]')) runtime.toggleDialogTemplateEditor(trigger);
        });
        bootstrap.Modal.getOrCreateInstance(document.getElementById('categoriesModal')).show();
      });
      await page.locator('#categoriesModal.show').waitFor();
      await page.locator('[data-category-template] [data-dialog-template-toggle]').click();
      assert.equal(await page.locator('.category-item-priority').inputValue(), '');
      await page.locator('.category-item-priority').selectOption('P5');
      for (const priority of ['P1', 'P2', 'P3', 'P4', 'P5']) {
        await page.locator('[data-category-row-add]').click();
        const row = page.locator('[data-category-items] .input-group').last();
        assert.equal(await row.locator('select').inputValue(), 'P3');
        await row.locator('input').fill('Категория ' + priority);
        await row.locator('select').selectOption(priority);
      }
      await page.locator('.category-item-input').nth(1).fill('Касса');
      const payload = await page.evaluate(() => runtime.collectDialogTemplatesPayload());
      assert.deepEqual(payload.fallbackCategories, ['Старая', 'Касса', 'Категория P2', 'Категория P3', 'Категория P4', 'Категория P5']);
      assert.deepEqual(payload.categoryTemplates[0].category_priorities,
        { 'Старая': 'P5', 'Касса': 'P1', 'Категория P2': 'P2', 'Категория P3': 'P3', 'Категория P4': 'P4', 'Категория P5': 'P5' });
      await page.evaluate(payload => { fixtureConfig = { category_templates: payload.categoryTemplates }; runtime.initialize(); }, payload);
      assert.equal(await page.locator('.category-item-priority').nth(1).inputValue(), 'P1');
      await page.locator('[data-category-template] [data-dialog-template-toggle]').click();
      await page.locator('.category-item-input').nth(1).fill('Терминал');
      await page.locator('[data-category-row-remove]').nth(2).click();
      const changed = await page.evaluate(() => runtime.collectDialogTemplatesPayload().categoryTemplates[0]);
      assert.equal(changed.category_priorities['Терминал'], 'P1');
      assert.equal(changed.category_priorities['Касса'], undefined);
      assert.equal(changed.category_priorities['Категория P2'], undefined);
      assert.match(await page.locator('[data-category-template] [data-dialog-template-summary]').textContent(), /Терминал · P1/);
      assert(await page.locator('.category-item-input').first().evaluate(element => element.getBoundingClientRect().width >= 50), 'Category input must remain usable on mobile');
      await page.screenshot({ path: path.join(output, `settings-${width}.png`), fullPage: true });

      await page.goto(origin + '/dashboard');
      await page.waitForFunction(() => Chart.getChart('priorityChart'));
      assert.deepEqual(await page.evaluate(() => Chart.getChart('priorityChart').data.labels), ['P1', 'P2', 'P3', 'P4', 'P5', 'Без критичности']);
      assert.deepEqual(await page.evaluate(() => Chart.getChart('priorityChart').data.datasets[0].data), [2, 0, 0, 0, 1, 1]);
      assert.match((await page.evaluate(() => Chart.getChart('categoryChart').data.labels)).join(','), /Касса · P1/);
      assert.match(await page.evaluate(() => Chart.getChart('categoryChart').scales.y.ticks[1].label), /… · P2$/);
      assert.deepEqual(await page.evaluate(() => Chart.getChart('directionChart').data.labels), ['Техническая поддержка']);
      await page.locator('[data-panel-id="priority-chart"]').screenshot({ path: path.join(output, `dashboard-${width}.png`) });
      empty = true;
      await page.evaluate(() => loadDashboardData());
      assert.equal(await page.locator('#priorityChart').isVisible(), false);
      assert.match(await page.locator('[data-panel-id="priority-chart"] .no-data-message').textContent(), /Нет данных/);
      empty = false;
      await page.evaluate(() => loadDashboardData());
      assert.equal(await page.locator('#priorityChart').isVisible(), true);
      assert.deepEqual(errors, []);
      await page.close();
    }
    console.log('Category priorities QA passed: settings create/edit/reload/rename/delete, dashboard charts and empty states at 320 and 1440 px.');
  } finally {
    await browser?.close();
    await new Promise(resolve => server.close(resolve));
  }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
