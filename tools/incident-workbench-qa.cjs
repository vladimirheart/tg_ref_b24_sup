/* Только MVC fixtures и локальные API doubles: никаких записей в рабочую панель. */
const fs = require('node:fs'), path = require('node:path'), http = require('node:http'), assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..'), resources = path.join(root, 'spring-panel/src/main/resources');
const captures = path.join(root, 'spring-panel/target/neutral-ops-rendered');
const output = path.join(root, 'spring-panel/target/incidents-r12-qa');
async function main() {
  fs.mkdirSync(output, { recursive: true });
  const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
  const server = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://localhost');
    const base = url.pathname.startsWith('/screens/') ? captures : path.join(resources, 'static');
    const name = url.pathname.startsWith('/screens/') ? url.pathname.slice(9) + '.html' : url.pathname.slice(1);
    const file = path.resolve(base, name);
    if (!file.startsWith(base + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) { res.writeHead(404); res.end('{}'); return; }
    res.setHeader('Content-Type', ({ '.html': 'text/html; charset=utf-8', '.css': 'text/css', '.js': 'text/javascript', '.svg': 'image/svg+xml' })[path.extname(file)] || 'application/octet-stream');
    fs.createReadStream(file).pipe(res);
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const origin = `http://127.0.0.1:${server.address().port}`, browser = await chromium.launch({ headless: true }), results = [];
  try {
    for (const theme of ['light', 'dark']) for (const width of [320, 390, 768, 1440, 1920]) {
      const context = await browser.newContext({ viewport: { width, height: 1000 }, colorScheme: theme, hasTouch: width <= 390, isMobile: width <= 390, reducedMotion: 'reduce' });
      await context.addInitScript(theme => { localStorage.setItem('iguana:theme', theme); localStorage.setItem('iguana:theme-palette', 'neo'); localStorage.setItem('sidebarPinned', '1'); }, theme);
      const incident = { id: 731, incident_key: 'INC-731', title: 'Нет связи с рестораном', summary: 'Недоступен рабочий канал', severity: 'high', status: 'open', owner: 'anna', source: 'Оператор', description: 'Проверить подключение и восстановить доставку', events: [], watchers: [] };
      const policies = [{ id: 'provider_health', label: 'Внешние подключения', description: 'Останавливает проверки и уведомления', mode: 'enabled', effective_mode: 'enabled', suppressed: false }];
      const patches = [], creates = [], settings = [], errors = []; let directoryCalls = 0;
      await context.route('**/*', route => {
        const req = route.request(), url = new URL(req.url()); if (url.origin !== origin) return route.abort();
        if (!url.pathname.startsWith('/api/')) return route.continue();
        if (url.pathname === '/api/incidents/related-object-options') return route.fulfill({ json: { types: ['ticket', 'task', 'object_passport', 'external_object'], can_create_external: true } });
        if (url.pathname === '/api/incidents/related-objects') return route.fulfill({ json: { items: [{ value: ' T/1?&%# Ю ', label: '20261026-001 · Нет связи', detail: 'Клиент' }] } });
        if (url.pathname === '/api/panel-users/options') { directoryCalls++; return route.fulfill({ json: [{ value: 'anna', label: 'Анна Смирнова · anna' }, { value: 'victor', label: 'Виктор Крюков · victor' }] }); }
        if (url.pathname.startsWith('/api/incidents/monitoring')) {
          if (req.method() === 'PATCH') { const payload = req.postDataJSON(); settings.push(payload); Object.assign(policies[0], payload, { ...(payload.duration_minutes ? { until: new Date(Date.now() + payload.duration_minutes * 60000).toISOString() } : {}), effective_mode: payload.mode, suppressed: payload.mode !== 'enabled' }); }
          return route.fulfill({ json: { items: policies, can_edit: true } });
        }
        if (req.method() === 'PATCH' && url.pathname === '/api/incidents/731') {
          const payload = req.postDataJSON(); patches.push(payload); Object.assign(incident, payload);
          if (payload.resolution_comment) incident.events.push({ event_type: 'resolution', event_text: payload.resolution_comment, actor: 'operator' });
          return route.fulfill({ json: { incident } });
        }
        if (req.method() === 'POST' && url.pathname === '/api/incidents') { creates.push(req.postDataJSON()); return route.fulfill({ json: { incident: { ...incident, id: 732, incident_key: 'INC-732' } } }); }
        if (url.pathname === '/api/incidents') return route.fulfill({ json: { items: [incident] } });
        if (url.pathname === '/api/incidents/731') return route.fulfill({ json: { incident } });
        assert(['GET', 'HEAD'].includes(req.method()), 'Unexpected fixture mutation: ' + req.method() + ' ' + url.pathname);
        return route.fulfill({ json: {} });
      });
      const page = await context.newPage(); page.on('pageerror', ex => errors.push(ex.message));
      await page.goto(origin + '/screens/incidents/index', { waitUntil: 'load' });
      await page.locator('#incidentQuickStatus').waitFor(); await page.waitForTimeout(250);
      assert(await page.locator('main #incidentWorkbenchList').isVisible(), 'Incident queue stays visible outside all modals');
      assert.equal(await page.locator('.modal #incidentWorkbenchList').count(), 0);
      assert(!await page.locator('#incidentFilters').isVisible(), 'Filters collapsed by default');
      assert(!await page.locator('.incident-timeline').evaluate(el => el.open), 'Timeline collapsed by default');
      for (const id of ['incidentDetailTitle', 'incidentDetailSummary', 'incidentDetailSeverity', 'incidentDetailOwner', 'incidentDetailSource', 'incidentDetailDescription']) assert(await page.locator(`label[for="${id}"]`).isVisible(), 'Persistent label ' + id);
      await page.locator('[data-bs-target="#incidentFilters"]').click(); await page.locator('#incidentWorkbenchQuery').waitFor({ state: 'visible' });
      await page.locator('[data-bs-target="#incidentFilters"]').click(); await page.locator('#incidentWorkbenchQuery').waitFor({ state: 'hidden' });
      await page.locator('#incidentQuickStatus').scrollIntoViewIfNeeded();
      const geometry = () => page.evaluate(() => ['main', '#incidentWorkbenchTabs', '#incidentWorkbenchList'].map(selector => document.querySelector(selector).getBoundingClientRect().top + scrollY));
      const before = await geometry();
      await page.locator('#incidentQuickStatus').selectOption('closed');
      await page.locator('#incidentResolutionModal.show').waitFor();
      assert((await page.locator('#incidentResolutionContext').textContent()).includes('INC-731'));
      await page.locator('#incidentResolutionForm button[type="submit"]').click(); assert.equal(patches.length, 0, 'Empty comment must not submit');
      await page.locator('#incidentResolutionForm [data-bs-dismiss="modal"]').last().click(); await page.locator('#incidentResolutionModal').waitFor({ state: 'hidden' });
      await page.waitForFunction(() => document.getElementById('incidentQuickStatus')?.value === 'open');
      assert.equal(await page.locator('#incidentQuickStatus').inputValue(), 'open'); assert.equal(patches.length, 0);
      await page.locator('#incidentQuickStatus').selectOption('closed'); await page.locator('#incidentResolutionModal.show').waitFor();
      await page.locator('#incidentResolutionComment').fill('Заменён кабель. Связь и доставка восстановлены.');
      await page.locator('#incidentResolutionForm button[type="submit"]').click();
      await page.waitForFunction(() => document.getElementById('incidentQuickStatus')?.value === 'closed' && document.getElementById('incidentWorkbenchDetail').getAttribute('aria-busy') === 'false');
      await page.waitForTimeout(200);
      assert.equal(patches.length, 1); assert(patches[0].resolution_comment.includes('кабель'));
      assert((await page.locator('#incidentWorkbenchSuccess').textContent()).includes('INC-731'));
      assert.deepEqual(await geometry(), before, 'Toast does not shift header/tabs/list');
      await page.screenshot({ path: path.join(output, `closed-${theme}-${width}.png`) });
      await page.locator('.incident-timeline summary').click(); assert((await page.locator('.incident-timeline').textContent()).includes('Заменён кабель'));

      await page.locator('[data-bs-target="#incidentCreateModal"]').click(); await page.locator('#incidentCreateModal.show').waitFor();
      const modal = await page.locator('#incidentCreateModal .modal-dialog').boundingBox(); assert(modal.x >= 0 && modal.x + modal.width <= width && modal.width <= 580);
      await page.locator('#incidentCreateTitle').fill('Новая задача связи');
      await page.locator('#incidentCreateRelationSearch').fill('20261026-001');
      await page.locator('#incidentCreateRelationResults button').click();
      assert.equal(await page.locator('#incidentCreateRelationKey').inputValue(), ' T/1?&%# Ю ');
      const owner = page.locator('#incidentCreateOwner').locator('..');
      await owner.locator('.employee-picker__trigger').click(); await owner.locator('input[type="search"]').fill('смирн');
      await owner.locator('.employee-picker__option', { hasText: 'Анна Смирнова' }).click(); assert.equal(await page.locator('#incidentCreateOwner').inputValue(), 'anna');
      const watchers = page.locator('#incidentCreateWatchers').locator('..');
      await watchers.locator('.employee-picker__trigger').click(); await watchers.locator('input[type="search"]').fill('виктор');
      await watchers.locator('.employee-picker__option', { hasText: 'Виктор Крюков' }).click();
      await watchers.locator('input[type="search"]').fill('несуществующий'); assert((await watchers.locator('[role="status"]').textContent()).includes('не найдены'));
      await page.keyboard.press('Escape'); assert(await page.locator('#incidentCreateModal.show').isVisible(), 'Escape closes picker, not parent modal');
      await page.screenshot({ path: path.join(output, `create-${theme}-${width}.png`) });
      await page.locator('#incidentWorkbenchCreate').click(); await page.locator('#incidentCreateModal').waitFor({ state: 'hidden' });
      assert.equal(creates.length, 1); assert.equal(creates[0].owner, 'anna'); assert.deepEqual(creates[0].watchers, ['victor']);
      assert.equal(creates[0].relations[0].relation_key, ' T/1?&%# Ю ');

      await page.locator('[data-bs-target="#incidentMonitoringModal"]').click(); await page.locator('.incident-monitoring-row').waitFor();
      await page.locator('.incident-monitoring-row select').selectOption('week'); await page.locator('.incident-monitoring-row button[type="submit"]').click();
      await page.waitForFunction(() => document.getElementById('incidentMonitoringStatus').textContent.startsWith('Сохранено'));
      assert.equal(settings[0].mode, 'paused'); assert.equal(settings[0].duration_minutes, 10080); assert.equal(settings[0].until, undefined, 'Preset duration uses server time');
      assert(await page.locator('.incident-monitoring-row .form-text').isVisible(), 'Timezone for datetime-local is explicit');
      const unchangedUntil = policies[0].until;
      await page.locator('.incident-monitoring-row button[type="submit"]').click();
      await page.waitForFunction(() => document.getElementById('incidentMonitoringStatus').textContent.startsWith('Сохранено'));
      assert.equal(settings[1].until, unchangedUntil, 'Untouched until preserves exact instant and seconds');
      await page.screenshot({ path: path.join(output, `monitoring-${theme}-${width}.png`) });
      await page.locator('.incident-monitoring-row select').selectOption('disabled'); await page.locator('.incident-monitoring-row button[type="submit"]').click();
      await page.waitForFunction(() => document.querySelector('.incident-monitoring-row .small')?.textContent === 'Отключено бессрочно');
      await page.locator('#incidentMonitoringModal [data-bs-dismiss="modal"]').last().click(); await page.locator('#incidentMonitoringModal').waitFor({ state: 'hidden' });

      // Existing scoped server options remain authoritative; IDs are opaque strings.
      const callsBefore = directoryCalls;
      await page.evaluate(() => {
        const holder = document.createElement('div'); holder.id = 'scoped-picker-fixture';
        holder.innerHTML = '<label for="assignmentTarget">Сотрудник бизнес-очереди</label><select id="assignmentTarget"><option value="">Не выбран</option><option value="9007199254740993123">Анна</option><option value="731">Виктор</option></select>';
        document.querySelector('main').append(holder); EmployeePicker.refresh();
      });
      const scoped = page.locator('#scoped-picker-fixture'); await scoped.locator('.employee-picker__trigger').click();
      await scoped.locator('input[type="search"]').fill('Анна'); await scoped.locator('.employee-picker__option', { hasText: 'Анна' }).click();
      assert.equal(await page.locator('#assignmentTarget').inputValue(), '9007199254740993123'); assert.equal(directoryCalls, callsBefore, 'Scoped selection does not fetch global directory');
      assert(!await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1), 'No horizontal overflow');
      assert.deepEqual(errors, []); results.push({ theme, width, closure: patches[0], creation: creates[0], policy: settings, scopedIdsPreserved: true, errors });
      await context.close();
    }
    fs.writeFileSync(path.join(output, 'report.json'), JSON.stringify(results, null, 2));
    console.log(JSON.stringify({ scenarios: results.length, screenshots: results.length * 3, output }));
  } finally { await browser.close(); await new Promise(resolve => server.close(resolve)); }
}
main().catch(ex => { console.error(ex); process.exitCode = 1; });
