/* MVC HTML + настоящий UI, только локальные read-only fixtures, без production mutations. */
'use strict';
const fs = require('node:fs'), path = require('node:path'), http = require('node:http'), assert = require('node:assert/strict');
const fixtures = require('../spring-panel/src/test/js/fixtures/incident-descriptions-r13.cjs');
const root = path.resolve(__dirname, '..'), resources = path.join(root, 'spring-panel/src/main/resources/static');
const captures = path.join(root, 'spring-panel/target/neutral-ops-rendered'), output = path.join(root, 'spring-panel/target/incidents-r13-qa');
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
      const context = await browser.newContext({ viewport: { width, height: 1000 }, colorScheme: theme, hasTouch: width <= 390, isMobile: width <= 390, reducedMotion: 'reduce' });
      await context.addInitScript(theme => { localStorage.setItem('iguana:theme', theme); localStorage.setItem('iguana:theme-palette', 'neo'); localStorage.setItem('sidebarPinned', '1'); }, theme);
      const incidents = fixtures.map((fixture, index) => ({ id: 731 + index, incident_key: 'INC-' + (731 + index), status: 'open', severity: 'high', ...structuredClone(fixture.incident) }));
      const errors = [], writes = [], missing = [];
      await context.route('**/*', route => {
        const req = route.request(), url = new URL(req.url());
        if (url.origin !== origin) return route.abort();
        if (!url.pathname.startsWith('/api/')) return route.continue();
        if (req.method() !== 'GET') { writes.push({ url: url.pathname, method: req.method() }); return route.fulfill({ status: 400, json: {} }); }
        if (url.pathname === '/api/incidents') return route.fulfill({ json: { items: incidents.map(({ events, ...item }) => item) } });
        const match = url.pathname.match(/^\/api\/incidents\/(\d+)$/);
        if (match) return route.fulfill({ json: { incident: incidents.find(item => String(item.id) === match[1]) } });
        return route.fulfill({ json: {} });
      });
      const page = await context.newPage(); page.on('pageerror', ex => errors.push(ex.message));
      page.on('response', response => { if (response.status() === 404 && /incident-descriptions|incidents-workbench/.test(response.url())) missing.push(response.url()); });
      await page.goto(origin + '/screens/incidents/index', { waitUntil: 'load' });
      await page.locator('.incident-explanation').waitFor();
      for (let index = 0; index < fixtures.length; index++) {
        const fixture = fixtures[index], incident = incidents[index];
        await page.locator(`.incident-list-row[data-incident-id="${incident.id}"] button`).click();
        await page.waitForFunction(({ expected, id }) => document.getElementById('incidentWorkbenchDetail').dataset.incidentId === String(id) && document.querySelector('.incident-explanation-summary')?.textContent.includes(expected), { expected: fixture.expected, id: incident.id });
        const description = page.locator('.incident-explanation');
        if (fixture.name === 'manual') assert((await description.textContent()).includes('сохранённое автором'));
        assert((await page.locator(`.incident-list-row[data-incident-id="${incident.id}"]`).textContent()).includes(await description.locator('.card-header').textContent()));
        assert.equal(await page.locator('#incidentDetailTitle').inputValue(), incident.title);
        assert.equal(await page.locator('#incidentDetailSummary').inputValue(), incident.summary);
        const technical = description.locator('.incident-original-diagnostics'); assert(!await technical.evaluate(el => el.open));
        await technical.locator('summary').click(); assert((await technical.textContent()).includes(incident.summary));
        assert.equal(await technical.locator('img, script').count(), 0, 'Diagnostic strings are text, never HTML');
        await technical.locator('summary').click();
        assert(!await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1), fixture.name + ' overflow');
        if (['chat-history', 'rabbit-metrics', 'manual'].includes(fixture.name)) {
          await description.scrollIntoViewIfNeeded(); await page.screenshot({ path: path.join(output, `${fixture.name}-${theme}-${width}.png`) });
        }
      }
      incidents.at(-1).status = 'closed';
      await page.locator('#incidentWorkbenchRefresh').click();
      await page.waitForFunction(() => document.querySelector('.incident-explanation')?.textContent.includes('не текущее состояние системы'));
      assert.equal(await page.evaluate(() => window.injection), undefined);
      assert.deepEqual(writes, []); assert.deepEqual(errors, []); assert.deepEqual(missing, []);
      results.push({ theme, width, cases: fixtures.length, historicalState: true, noMutations: true, escapedDiagnostics: true, errors, missing });
      await context.close();
    }
    fs.writeFileSync(path.join(output, 'report.json'), JSON.stringify(results, null, 2));
    console.log(JSON.stringify({ combinations: results.length, incidentStates: results.length * fixtures.length, screenshots: results.length * 3, output }));
  } finally { await browser.close(); await new Promise(resolve => server.close(resolve)); }
}
main().catch(ex => { console.error(ex); process.exitCode = 1; });
