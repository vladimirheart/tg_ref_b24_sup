'use strict';

// Actual MVC HTML and production assets; all writes stay in loopback fixtures.
const fs = require('node:fs'), path = require('node:path'), http = require('node:http'), assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..'), resources = path.join(root, 'spring-panel/src/main/resources');
const out = path.join(root, 'spring-panel/target/business-administration-qa');
const big = '9007199254740993';
const fixture = () => ({
  businesses: [{ id: big, code: 'first', displayName: 'Первый бизнес', status: 'active' }, { id: '8', code: 'second', displayName: 'Второй бизнес', status: 'active' }, { id: '9', code: 'archive', displayName: 'Архивный бизнес', status: 'archived' }, ...Array.from({ length: 35 }, (_, n) => ({ id: String(n + 20), code: 'business-' + n, displayName: 'Бизнес ' + n, status: 'active' }))],
  users: [{ id: '2', username: 'Анна' }, { id: '3', username: 'Борис' }],
  memberships: [{ businessId: big, userId: '2', roleCode: 'VIEWER' }, { businessId: '8', userId: '3', roleCode: 'OPERATOR' }, { businessId: '9', userId: '2', roleCode: 'MANAGER' }],
  allGrants: [{ userId: '3', roleCode: 'VIEWER' }],
  locations: [{ id: '11', businessId: big, locationName: 'Кафе Первое', locationType: 'Кафе', city: 'Москва', status: 'active' }, { id: '12', businessId: '8', locationName: 'Вторая локация', status: 'active' }, { id: '13', businessId: '9', locationName: 'Архивная локация', status: 'archived' }]
});

async function main() {
  fs.mkdirSync(out, { recursive: true });
  const server = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://localhost');
    const file = url.pathname === '/businesses/manage' ? path.join(out, 'manage.html') : path.resolve(resources, 'static', '.' + url.pathname);
    if (url.pathname !== '/businesses/manage' && !file.startsWith(path.join(resources, 'static') + path.sep)) { res.writeHead(403).end(); return; }
    if (!fs.existsSync(file) || !fs.statSync(file).isFile()) { res.writeHead(404).end(); return; }
    const ext = path.extname(file);
    res.setHeader('Content-Type', ({ '.html': 'text/html; charset=utf-8', '.js': 'application/javascript', '.css': 'text/css', '.woff2': 'font/woff2' })[ext] || 'application/octet-stream');
    if (['.html', '.js', '.css'].includes(ext)) res.end(fs.readFileSync(file, 'utf8').replaceAll('\r\n', '\n')); else fs.createReadStream(file).pipe(res);
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const origin = 'http://127.0.0.1:' + server.address().port;
  const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
  const browser = await chromium.launch({ headless: true, ...(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {}) });
  const report = [];
  try {
    for (const theme of ['light', 'dark']) for (const [width, height] of [[1440, 900], [1280, 360], [768, 480], [390, 640]]) {
      console.log(`Checking ${theme} ${width}x${height}`);
      const context = await browser.newContext({ viewport: { width, height }, colorScheme: theme, reducedMotion: 'reduce' });
      await context.addInitScript(theme => { localStorage.setItem('iguana:theme', theme); localStorage.setItem('iguana:theme-palette', 'north'); }, theme);
      const state = fixture(), writes = [], errors = [], missing = [];
      let denyNext = false, discard = false, version = 'a'.repeat(64), groups = { service: { priority: ['Обычно', 'Срочно'] } };
      await context.route('**/*', async route => {
        const req = route.request(), url = new URL(req.url());
        if (url.origin !== origin) return route.abort();
        if (!url.pathname.startsWith('/api/')) return route.continue();
        const api = '/api/business-admin';
        if (!url.pathname.startsWith(api)) {
          assert.equal(req.method(), 'GET', 'Unrelated write: ' + url.pathname);
          return route.fulfill({ json: { success: true, items: [], notifications: [], settings: {}, preferences: {} } });
        }
        const routePath = url.pathname.slice(api.length), method = req.method();
        if (method === 'GET') {
          if (routePath === '/overview') return route.fulfill({ json: state });
          if (routePath === '/channels') return route.fulfill({ json: [{ id: '10', channelName: 'Основной канал', platform: 'telegram', active: true }] });
          if (routePath === '/channels/10/binding-preview') return route.fulfill({ json: { classification: 'SINGLE_BUSINESS', bindings: [{ businessId: big, businessCode: 'first', businessStatus: 'active', resolutionMode: 'SINGLE_BUSINESS', active: true }] } });
          if (routePath.endsWith('/questionnaire-presets')) return route.fulfill({ json: { businessId: routePath.split('/')[2], sha256: version, groups } });
          throw new Error('Unexpected read ' + routePath);
        }
        const body = req.postData() ? req.postDataJSON() : null;
        assert(req.headers()['x-xsrf-token'], 'Missing CSRF on ' + routePath);
        writes.push({ method, path: routePath, body });
        if (denyNext) { denyNext = false; return route.fulfill({ status: 403, json: {} }); }
        let match;
        if (routePath === '/businesses' && method === 'POST') state.businesses.push({ id: '100', status: 'active', ...body });
        else if ((match = routePath.match(/^\/businesses\/([^/]+)\/memberships\/([^/]+)$/))) {
          state.memberships = state.memberships.filter(g => !(g.businessId === match[1] && g.userId === match[2]));
          if (method === 'PUT') state.memberships.push({ businessId: match[1], userId: match[2], ...body });
          else assert.equal(method, 'DELETE');
        } else if ((match = routePath.match(/^\/all-grants\/([^/]+)$/))) {
          state.allGrants = state.allGrants.filter(g => g.userId !== match[1]);
          if (method === 'PUT') state.allGrants.push({ userId: match[1], ...body }); else assert.equal(method, 'DELETE');
        } else if ((match = routePath.match(/^\/businesses\/([^/]+)\/locations$/))) {
          assert.equal(method, 'POST'); state.locations.push({ id: '14', businessId: match[1], status: 'active', ...body });
        } else if (routePath === `/businesses/${big}/locations/11/status`) state.locations[0].status = body.status;
        else if (routePath === `/businesses/${big}/questionnaire-presets`) {
          assert.equal(body.expectedSha256, version); version = 'b'.repeat(64); groups = body.groups;
          return route.fulfill({ json: { businessId: big, sha256: version, groups } });
        } else if (routePath === `/businesses/${big}/status`) state.businesses[0].status = body.status;
        else if (routePath === `/businesses/${big}`) state.businesses[0].displayName = body.displayName;
        else assert([`/channels/10/bindings/${big}`].includes(routePath), 'Unexpected write ' + routePath);
        return route.fulfill({ status: 204 });
      });
      const page = await context.newPage(); page.setDefaultTimeout(12000);
      page.on('pageerror', error => errors.push(error.message));
      page.on('response', response => { if (response.status() === 404 && /\.(js|css)(\?|$)/.test(response.url())) missing.push(response.url()); });
      page.on('dialog', dialog => dialog.type() === 'prompt' ? dialog.accept('Первый обновлённый') : (discard ? dialog.dismiss() : dialog.accept()));
      const tab = key => page.locator(`#business-${key}-tab`);
      const ready = message => page.locator('[data-business-status]').filter({ hasText: message }).waitFor();
      const openEditor = form => page.locator(`#${form}`).locator('xpath=ancestor::details[1]').locator(':scope > summary').click();
      const submit = form => page.locator(`#${form} [type=submit]`).click();
      const employee = async (id, name) => {
        const picker = page.locator(`#${id}`).locator('xpath=parent::*');
        await picker.locator('.employee-picker__trigger').click();
        await picker.locator('input[type=search]').fill(name);
        await picker.locator('.employee-picker__option').filter({ hasText: name }).click();
      };
      const catalogAction = async name => {
        const row = page.locator('#businessCatalogRows tr:visible').first();
        await row.locator('[data-bs-toggle="dropdown"]').click();
        await row.getByRole('button', { name, exact: false }).filter({ hasText: name }).click();
      };
      await page.goto(origin + '/businesses/manage', { waitUntil: 'networkidle' });
      await ready('Каталог и права загружены');
      assert.equal(await page.locator('.tab-pane:visible').count(), 1);
      assert.equal(await page.locator('[data-business-admin] details[open]').count(), 0);
      assert.equal(await page.locator('[data-business-count="businesses"]').textContent(), '38');
      assert(await page.locator('#businessCatalogRows').evaluate(el => el.closest('.table-responsive').scrollHeight > el.closest('.table-responsive').clientHeight), 'Long list has no bounded scroll');
      assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), 'Page overflows horizontally');
      await page.screenshot({ path: path.join(out, `catalog-${theme}-${width}x${height}.png`), fullPage: true });
      const lastMenu = page.locator('#businessCatalogRows tr').last();
      await lastMenu.locator('[data-bs-toggle="dropdown"]').click();
      assert(await lastMenu.locator('.dropdown-item').first().evaluate(el => { const r = el.getBoundingClientRect(); return el.contains(document.elementFromPoint(r.x + r.width / 2, r.y + r.height / 2)); }), 'Last row menu clipped');
      await lastMenu.locator('[data-bs-toggle="dropdown"]').press('Escape');
      await page.locator('[data-business-search="catalog"]').fill('ПЕРВЫЙ');
      assert.equal(await page.locator('#businessCatalogRows tr:visible').count(), 1);
      await page.locator('[data-business-search="catalog"]').fill('невозможный');
      assert.match(await page.locator('#businessCatalogRows [data-list-empty]').textContent(), /ничего не найдено/);
      await page.locator('[data-business-search="catalog"]').fill('');
      await tab('catalog').focus(); await tab('catalog').press('ArrowRight');
      assert.equal(await tab('access').getAttribute('aria-selected'), 'true', 'Keyboard tabs do not switch');
      assert.match(await page.locator('#businessMembershipRows tr:visible').textContent(), /Анна.*Чтение/);
      assert.equal(await page.locator('.business-admin__group').getAttribute('open'), null);
      await page.screenshot({ path: path.join(out, `access-${theme}-${width}x${height}.png`), fullPage: true });
      await page.locator('#membershipBusiness').selectOption('8');
      assert.match(await page.locator('#businessMembershipRows tr:visible').textContent(), /Борис/);
      await page.locator('#membershipBusiness').selectOption('9');
      assert.match(await page.locator('#businessMembershipRows tr:visible').textContent(), /Анна.*Менеджер/);
      await openEditor('businessMembershipForm');
      assert(await page.locator('#businessMembershipForm [type=submit]').isDisabled());
      await page.locator('#membershipBusiness').selectOption(big);
      await employee('membershipUser', 'Анна'); await page.locator('#membershipRole').selectOption('OPERATOR');
      denyNext = true; await submit('businessMembershipForm'); await ready('Недостаточно прав');
      assert.equal(writes.length, 1, 'Write retried without user action');
      assert.equal(await page.locator('#membershipRole').inputValue(), 'OPERATOR');
      await submit('businessMembershipForm'); await ready('Доступ к бизнесу сохранён');
      assert.deepEqual(writes.at(-1), { method: 'PUT', path: `/businesses/${big}/memberships/2`, body: { roleCode: 'OPERATOR' } });
      await page.locator('[data-business-search="memberships"]').fill('ненайден');
      assert(await page.locator('#businessMembershipRows [data-list-empty]').isVisible());
      await page.locator('[data-business-search="memberships"]').fill('');
      await page.locator('#businessMembershipRows tr:visible button').click(); await ready('Доступ к бизнесу отозван');
      await page.locator('.business-admin__group > summary').click();
      await openEditor('allBusinessGrantForm'); await employee('allGrantUser', 'Анна'); await submit('allBusinessGrantForm'); await ready('Доступ ко всем бизнесам сохранён');
      await page.locator('#allBusinessGrantRows tr').filter({ hasText: 'Анна' }).locator('button').click(); await ready('Доступ ко всем бизнесам отозван');
      await tab('locations').click(); await page.locator('#locationBusiness').selectOption('9');
      assert.match(await page.locator('#businessLocationRows tr:visible').textContent(), /Архивная локация/);
      await openEditor('businessLocationForm'); assert(await page.locator('#businessLocationForm [type=submit]').isDisabled());
      await page.locator('#locationBusiness').selectOption(big);
      await page.locator('#locationName').fill('Несохранённая локация'); await tab('channels').click(); await tab('locations').click();
      assert.equal(await page.locator('#locationName').inputValue(), 'Несохранённая локация');
      await submit('businessLocationForm'); await ready('Локация добавлена');
      assert.equal(writes.at(-1).path, `/businesses/${big}/locations`);
      assert.equal(await page.locator('#locationBusiness').inputValue(), big);
      await page.locator('#businessLocationRows tr').filter({ hasText: 'Кафе Первое' }).getByRole('button', { name: 'Архивировать' }).click(); await ready('Локация архивирована');
      await tab('channels').click(); await page.locator('#businessChannelRows button').click();
      await page.locator('#channelBindingRows button').waitFor();
      await openEditor('channelBindingForm'); await page.locator('#channelBindingBusiness').selectOption(big);
      await submit('channelBindingForm'); await ready('Привязка сохранена');
      await page.locator('#channelBindingRows button').click(); await ready('Привязка выключена');
      await tab('presets').click(); await page.locator('#presetsBusiness').selectOption(big);
      await page.locator('[data-preset-column="values"]').fill('Новый вариант');
      await tab('catalog').click(); await tab('presets').click();
      assert.equal(await page.locator('[data-preset-column="values"]').inputValue(), 'Новый вариант');
      discard = true; await page.locator('#presetsBusiness').selectOption('8'); discard = false;
      assert.equal(await page.locator('#presetsBusiness').inputValue(), big, 'Rejected owner change lost draft');
      await page.locator('[data-presets-save]').click();
      await page.locator('[data-presets-status]').filter({ hasText: 'Справочник выбранного бизнеса сохранён' }).waitFor();
      assert.deepEqual(writes.at(-1).body.groups, { service: { priority: ['Новый вариант'] } });
      await page.screenshot({ path: path.join(out, `presets-${theme}-${width}x${height}.png`), fullPage: true });
      await tab('catalog').click(); await page.locator('[data-business-search="catalog"]').fill('Первый');
      await catalogAction('Переименовать'); await ready('Название бизнеса обновлено');
      await catalogAction('Архивировать'); await ready('Бизнес архивирован');
      await catalogAction('Восстановить'); await ready('Бизнес восстановлен');
      await page.locator('#businessCreateForm').locator('xpath=ancestor::details[1]').locator('summary').focus();
      await page.keyboard.press('Enter'); await page.locator('#businessCode').fill('new-business'); await page.locator('#businessName').fill('Новый бизнес');
      await submit('businessCreateForm'); await ready('Бизнес создан');
      await page.locator('[data-business-search="catalog"]').fill('Новый бизнес');
      assert.equal(await page.locator('#businessCatalogRows tr:visible').count(), 1);
      assert.equal(await page.locator('[data-business-count="businesses"]').textContent(), '39');
      assert.equal(writes.length, 14); assert.deepEqual(errors, []); assert.deepEqual(missing, []);
      if (width === 390) {
        for (const key of ['businesses', 'users', 'memberships', 'allGrants', 'locations']) state[key] = [];
        await page.reload({ waitUntil: 'networkidle' }); await ready('Бизнесов пока нет');
        assert.equal(await page.locator('[data-business-count="businesses"]').textContent(), '0');
        assert.match(await page.locator('#businessCatalogRows').textContent(), /Бизнесов пока нет/);
        await tab('access').click(); await openEditor('businessMembershipForm');
        assert(await page.locator('#businessMembershipForm [type=submit]').isDisabled());
        assert.match(await page.locator('#businessMembershipRows').textContent(), /Сначала создайте бизнес/);
        assert.equal(writes.length, 14, 'Empty state submitted a write');
      }
      report.push({ theme, width, height, writes: writes.length, emptyChecked: width === 390, errors, missing, status: 'GREEN' });
      await context.close();
    }
    fs.writeFileSync(path.join(out, 'report.json'), JSON.stringify(report, null, 2));
    console.log(`BUSINESS_ADMINISTRATION_QA_GREEN=${report.length}`);
  } finally { await browser.close(); server.close(); }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
