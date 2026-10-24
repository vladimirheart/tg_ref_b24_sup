'use strict';
const fs = require('node:fs'), path = require('node:path'), http = require('node:http'), assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..'), resources = path.join(root, 'spring-panel/src/main/resources/static');
const captures = path.join(root, 'spring-panel/target/neutral-ops-rendered'), output = path.join(root, 'spring-panel/target/incidents-r15-qa');
async function main() {
  fs.mkdirSync(output, { recursive: true });
  const server = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://localhost'), screen = url.pathname.startsWith('/screens/');
    const base = screen ? captures : resources, file = path.resolve(base, screen ? url.pathname.slice(9) + '.html' : url.pathname.slice(1));
    if (!file.startsWith(base + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) { res.writeHead(404); res.end('{}'); return; }
    res.setHeader('Content-Type', ({ '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml' })[path.extname(file)] || 'application/octet-stream');
    fs.createReadStream(file).pipe(res);
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
  const browser = await chromium.launch({ headless: true }), origin = `http://127.0.0.1:${server.address().port}`, results = [];
  try {
    for (const theme of ['light', 'dark']) for (const width of [320, 390, 768, 1440, 1920]) {
      const context = await browser.newContext({ viewport: { width, height: 1100 }, colorScheme: theme, reducedMotion: 'reduce' });
      await context.addInitScript(theme => { localStorage.setItem('iguana:theme', theme); localStorage.setItem('iguana:theme-palette', 'neo'); localStorage.setItem('sidebarPinned', '1'); }, theme);
      const incident = { id: 731, incident_key: 'INC-731', title: 'Исходное название', summary: 'Исходная запись', description: '', status: 'open', severity: 'critical', severity_mode: 'automatic', source: 'registry', signal_type: 'credential_rotation', signal_key: 'channel.3.network_route.proxy.password',
        signal_context: { last_status: 'missing_secret', credential_kind: 'proxy_password', display_name: 'Channel network route · тостов proxy password', source_ref: 'channels#3.deliverySettings.network_route.proxy.password' }, relations: [], watchers: [], routes: [], events: [] };
      const writes = [], errors = []; let failNext = false;
      await context.route('**/*', async route => {
        const req = route.request(), url = new URL(req.url()); if (url.origin !== origin) return route.abort();
        if (!url.pathname.startsWith('/api/')) return route.continue();
        if (req.method() === 'PATCH' && url.pathname === '/api/incidents/731') {
          const body = req.postDataJSON(); writes.push(body);
          if (failNext) { failNext = false; return route.fulfill({ status: 503, json: { error: 'Ошибка сохранения' } }); }
          Object.assign(incident, body); if (body.severity) incident.severity_mode = 'manual';
          return route.fulfill({ json: { success: true, incident } });
        }
        assert.equal(req.method(), 'GET', 'Only explicit priority/edit PATCH is allowed');
        if (url.pathname === '/api/incidents') return route.fulfill({ json: { items: [incident] } });
        if (url.pathname === '/api/incidents/731') return route.fulfill({ json: { incident } });
        return route.fulfill({ json: {} });
      });
      const page = await context.newPage(); page.on('pageerror', error => errors.push(error.message));
      await page.goto(origin + '/screens/incidents/index', { waitUntil: 'load' });
      await page.locator('#incidentQuickSeverity').waitFor();
      const explanation = await page.locator('.incident-explanation').textContent();
      assert(explanation.includes('Пароль прокси — канал «тостов»')); assert(explanation.includes('Сетевой маршрут → Прокси'));
      assert(!explanation.includes('Channel network route'));
      await page.locator('#incidentDetailTitle').fill('Черновик не должен исчезнуть');
      await page.locator('#incidentQuickSeverity').selectOption('low');
      await page.waitForFunction(() => document.getElementById('incidentQuickSeverity')?.value === 'low' && document.getElementById('incidentQuickSeverity').parentElement.textContent.includes('Назначен вручную'));
      assert.deepEqual(writes[0], { severity: 'low' });
      assert.equal(await page.locator('#incidentDetailTitle').inputValue(), 'Черновик не должен исчезнуть');
      assert.equal(await page.locator('#incidentDetailSeverity').inputValue(), 'low');
      await page.reload(); await page.locator('#incidentQuickSeverity').waitFor();
      assert.equal(await page.locator('#incidentQuickSeverity').inputValue(), 'low');
      await page.locator('#incidentDetailSeverity').selectOption('medium');
      await page.locator('#incidentWorkbenchSaveIncident').click();
      await page.waitForFunction(() => document.getElementById('incidentQuickSeverity')?.value === 'medium' && !document.getElementById('incidentWorkbenchSaveIncident').disabled);
      assert.equal(writes[1].severity, 'medium', 'Existing full edit form really saves priority');
      await page.locator('#incidentDetailSummary').fill('Уточнённое описание');
      await page.locator('#incidentWorkbenchSaveIncident').click();
      await page.waitForFunction(() => document.getElementById('incidentDetailSummary')?.value === 'Уточнённое описание' && !document.getElementById('incidentWorkbenchSaveIncident').disabled);
      assert(!Object.hasOwn(writes[2], 'severity'), 'Saving text must not pin or change an unchanged priority');
      failNext = true; await page.locator('#incidentQuickSeverity').selectOption('high');
      await page.waitForFunction(() => document.getElementById('incidentQuickSeverity')?.value === 'medium' && !document.getElementById('incidentQuickSeverity').disabled);
      assert.equal(incident.severity, 'medium', 'Failed PATCH never changes persisted fixture');
      await page.reload(); await page.locator('#incidentQuickSeverity').waitFor();
      assert.equal(await page.locator('#incidentQuickSeverity').inputValue(), 'medium');
      assert(!await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1));
      await page.locator('#incidentQuickSeverity').scrollIntoViewIfNeeded();
      await page.screenshot({ path: path.join(output, `priority-${theme}-${width}.png`) });
      assert.deepEqual(errors, []); assert.equal(writes.length, 4);
      results.push({ theme, width, priorityReload: true, draftPreserved: true, fullFormSave: true, failedPatchRollback: true });
      await context.close();
    }
    fs.writeFileSync(path.join(output, 'report.json'), JSON.stringify(results, null, 2));
    console.log(JSON.stringify({ combinations: results.length, output }));
  } finally { await browser.close(); await new Promise(resolve => server.close(resolve)); }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
