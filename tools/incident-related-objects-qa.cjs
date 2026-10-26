/* Real Thymeleaf/MVC HTML + local API doubles. Does not write to the working panel. */
const fs = require('node:fs'), path = require('node:path'), http = require('node:http'), assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..'), resources = path.join(root, 'spring-panel/src/main/resources');
const captures = path.join(root, 'spring-panel/target/neutral-ops-rendered');
const output = path.join(root, 'spring-panel/target/incidents-r19-qa');
async function main() {
  fs.mkdirSync(output, { recursive: true });
  const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
  const server = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://localhost'), screen = url.pathname.startsWith('/screens/');
    const base = screen ? captures : path.join(resources, 'static');
    const file = path.resolve(base, screen ? url.pathname.slice(9) + '.html' : url.pathname.slice(1));
    if (!file.startsWith(base + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) { res.writeHead(404); res.end('{}'); return; }
    res.setHeader('Content-Type', ({ '.html': 'text/html; charset=utf-8', '.css': 'text/css', '.js': 'text/javascript', '.svg': 'image/svg+xml' })[path.extname(file)] || 'application/octet-stream');
    fs.createReadStream(file).pipe(res);
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const origin = `http://127.0.0.1:${server.address().port}`, browser = await chromium.launch({ headless: true }), report = [];
  try {
    for (const theme of ['light', 'dark']) for (const width of [320, 390, 768, 1440, 1920]) {
      const context = await browser.newContext({ viewport: { width, height: 1000 }, colorScheme: theme, hasTouch: width <= 390, isMobile: width <= 390, reducedMotion: 'reduce' });
      await context.addInitScript(theme => { localStorage.setItem('iguana:theme', theme); localStorage.setItem('iguana:theme-palette', 'neo'); localStorage.setItem('sidebarPinned', '1'); }, theme);
      const exact = ' T/1?&%# Ю ', large = '9007199254740993', creates = [], errors = [], externalPosts = [];
      const incident = { id: 731, incident_key: 'INC-731', title: 'Сбой связи', status: 'open', severity: 'medium', events: [], watchers: [] };
      const external = [{ value: '8', label: '<img src=x onerror=alert(1)> · EXT-8', detail: 'Внешний объект' }];
      let admin = true, failSave = true;
      await context.route('**/*', async route => {
        const req = route.request(), url = new URL(req.url());
        if (url.origin !== origin) return route.abort();
        if (!url.pathname.startsWith('/api/')) return route.continue();
        if (url.pathname === '/api/incidents/related-object-options') return route.fulfill({ json: { types: admin ? ['ticket', 'task', 'object_passport', 'external_object'] : ['task', 'external_object'], can_create_external: admin } });
        if (url.pathname === '/api/incidents/related-objects') {
          const type = url.searchParams.get('type'), query = url.searchParams.get('query');
          if (query === 'old') await new Promise(resolve => setTimeout(resolve, 600));
          const items = query === 'empty' ? [] : type === 'external_object' ? external : [{ value: type === 'ticket' ? exact : large,
            label: query === 'old' ? 'Старый ответ' : type === 'ticket' ? '20261026-001 · Нет связи' : type === 'task' ? 'DL_42 · Заменить кабель' : 'PS-17 · Ресторан', detail: 'Найденная запись' }];
          return route.fulfill({ json: { items } }).catch(() => {});
        }
        if (url.pathname === '/api/incidents/external-objects') {
          externalPosts.push(req.postDataJSON());
          if (failSave) { failSave = false; return route.fulfill({ status: 400, json: { message: 'Проверка: повторите сохранение' } }); }
          const item = { value: large, label: 'Сервер ресторана · EXT-9', detail: 'Сервер за пределами панели' }; external.push(item);
          return route.fulfill({ json: { item } });
        }
        if (url.pathname === '/api/incidents' && req.method() === 'POST') { creates.push(req.postDataJSON()); return route.fulfill({ json: { incident } }); }
        if (url.pathname === '/api/incidents') return route.fulfill({ json: { items: [incident] } });
        if (url.pathname === '/api/incidents/731') return route.fulfill({ json: { incident } });
        if (url.pathname === '/api/panel-users/options') return route.fulfill({ json: [] });
        return route.fulfill({ json: { items: [] } });
      });
      const page = await context.newPage(); page.on('pageerror', error => errors.push(error.message));
      page.on('dialog', () => errors.push('Unexpected script execution'));
      await page.goto(origin + '/screens/incidents/index');
      const openCreate = async () => { await page.locator('[data-bs-target="#incidentCreateModal"]').click(); await page.locator('#incidentCreateModal.show').waitFor(); };
      const query = page.locator('#incidentCreateRelationSearch'), key = page.locator('#incidentCreateRelationKey');
      await openCreate(); await page.locator('#incidentCreateTitle').fill('Проверка поиска');
      await query.fill('old'); await page.waitForTimeout(250); await query.fill('new');
      await page.locator('#incidentCreateRelationResults button', { hasText: '20261026-001' }).waitFor();
      await page.waitForTimeout(650); assert(!(await page.locator('#incidentCreateRelationResults').textContent()).includes('Старый ответ'));
      await query.press('ArrowDown'); await query.press('Enter'); assert.equal(await key.inputValue(), exact);
      await query.fill('edited'); assert.equal(await key.inputValue(), '');
      await page.locator('#incidentWorkbenchCreate').click(); assert.equal(creates.length, 0, 'Free text is not an identity');
      await page.locator('#incidentCreateRelationType').selectOption('task'); assert.equal(await key.inputValue(), '');
      await query.fill('DL_42'); await page.locator('#incidentCreateRelationResults button').click(); assert.equal(await key.inputValue(), large);
      await page.locator('#incidentCreateRelationType').selectOption('object_passport'); await query.fill('PS-17');
      await page.locator('#incidentCreateRelationResults button').click(); assert.equal(await key.inputValue(), large);
      await page.locator('#incidentCreateRelationType').selectOption('ticket'); await query.fill('empty');
      await page.waitForFunction(() => document.getElementById('incidentCreateRelationStatus').textContent.includes('Ничего не найдено'));
      await query.fill('20261026-001'); await page.locator('#incidentCreateRelationResults button').waitFor();
      await page.screenshot({ path: path.join(output, `search-${theme}-${width}.png`) });
      await page.locator('#incidentCreateRelationResults button').click(); await page.locator('#incidentWorkbenchCreate').click();
      await page.locator('#incidentCreateModal').waitFor({ state: 'hidden' }); assert.equal(creates[0].relations[0].relation_key, exact);

      await openCreate(); await page.locator('#incidentCreateTitle').fill('Черновик инцидента сохранён');
      await page.locator('#incidentCreateRelationType').selectOption('external_object');
      await page.locator('#incidentOpenExternalCatalog').click(); await page.locator('#incidentExternalObjectsModal.show').waitFor();
      await page.locator('#incidentExternalList .incident-object-option').waitFor(); assert.equal(await page.locator('#incidentExternalList img').count(), 0, 'Untrusted catalog label is text');
      await page.locator('#incidentExternalAdd').click(); await page.locator('#incidentExternalName').fill('Сервер ресторана');
      await page.locator('#incidentExternalNumber').fill('EXT-9'); await page.locator('#incidentExternalUrl').fill('https://example.test/server/9');
      await page.locator('#incidentExternalDescription').fill('Сервер за пределами панели');
      await page.screenshot({ path: path.join(output, `catalog-${theme}-${width}.png`) });
      await page.locator('#incidentExternalForm [type="submit"]').click();
      await page.waitForFunction(() => document.getElementById('incidentExternalError').textContent.includes('повторите'));
      assert.equal(await page.locator('#incidentExternalName').inputValue(), 'Сервер ресторана');
      await page.locator('#incidentExternalForm [type="submit"]').click();
      await page.locator('#incidentCreateModal.show').waitFor(); assert.equal(await key.inputValue(), large);
      assert.equal(await page.locator('#incidentCreateTitle').inputValue(), 'Черновик инцидента сохранён');
      await page.screenshot({ path: path.join(output, `selected-${theme}-${width}.png`) });
      await page.locator('#incidentWorkbenchCreate').click(); await page.locator('#incidentCreateModal').waitFor({ state: 'hidden' });
      assert.equal(creates[1].relations[0].relation_type, 'external_object'); assert.equal(creates[1].relations[0].relation_key, large);
      await page.locator('[data-bs-target="#incidentExternalObjectsModal"]').first().click(); await page.locator('#incidentExternalObjectsModal.show').waitFor();
      await page.locator('#incidentExternalList', { hasText: 'EXT-9' }).waitFor(); assert.equal(await page.locator('#incidentExternalList button').count(), 0);
      await page.locator('#incidentExternalClose').click(); await page.locator('#incidentExternalObjectsModal').waitFor({ state: 'hidden' });
      assert.equal(await page.locator('#incidentCreateModal.show').count(), 0, 'Standalone catalog does not open incident form');
      assert(!await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1), 'No horizontal overflow');

      // New page/session: an operator can reuse catalog records but cannot see creation controls or inaccessible types.
      admin = false; await page.reload(); await openCreate();
      await page.waitForFunction(() => document.getElementById('incidentCreateRelationType').value === 'task');
      assert.equal(await page.locator('#incidentCreateRelationType option[value="ticket"]').isDisabled(), true);
      await page.locator('#incidentCreateRelationType').selectOption('external_object'); await page.locator('#incidentOpenExternalCatalog').click();
      await page.locator('#incidentExternalObjectsModal.show').waitFor(); await page.locator('#incidentExternalList button', { hasText: 'EXT-9' }).waitFor();
      assert.equal(await page.locator('#incidentExternalAdd').isVisible(), false); assert.equal(await page.locator('#incidentExternalForm').isVisible(), false);
      await page.locator('#incidentExternalList button', { hasText: 'EXT-9' }).click(); await page.locator('#incidentCreateModal.show').waitFor();
      assert.equal(await key.inputValue(), large); assert.equal(externalPosts.length, 2, 'No operator catalog POST');
      assert.deepEqual(errors, []); report.push({ theme, width, exactTicketId: true, opaqueIds: true, staleResponses: true, keyboard: true, adminCreate: true, operatorReadOnly: true, errors });
      await context.close();
    }
    fs.writeFileSync(path.join(output, 'report.json'), JSON.stringify(report, null, 2));
    console.log(JSON.stringify({ scenarios: report.length, screenshots: report.length * 3, output }));
  } finally { await browser.close(); await new Promise(resolve => server.close(resolve)); }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
