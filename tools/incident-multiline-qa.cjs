/* Real MVC HTML, local API doubles. Never contacts the instruction URL or writes working incidents. */
'use strict';
const fs = require('node:fs'), path = require('node:path'), http = require('node:http'), assert = require('node:assert/strict');
const runbook = require('../spring-panel/src/test/js/fixtures/incident-multiline-r20.cjs');
const root = path.resolve(__dirname, '..'), resources = path.join(root, 'spring-panel/src/main/resources/static');
const captures = path.join(root, 'spring-panel/target/neutral-ops-rendered'), output = path.join(root, 'spring-panel/target/incidents-r20-qa');
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
      const context = await browser.newContext({ viewport: { width, height: 1100 }, colorScheme: theme, hasTouch: width <= 390, isMobile: width <= 390, reducedMotion: 'reduce' });
      await context.addInitScript(theme => { localStorage.setItem('iguana:theme', theme); localStorage.setItem('iguana:theme-palette', 'neo'); localStorage.setItem('sidebarPinned', '1'); }, theme);
      const errors = [], requests = [], externalRequests = [];
      const unsafe = 'Сохранённая заметка\n\n  С отступом <img src=x onerror="window.injection=1">';
      const flat = runbook.replace(/\s+/g, ' ');
      const incident = { id: 238, incident_key: 'INC-238', title: 'Проблема на стороне iiko', summary: 'Проблемы на стороне iiko.\nСроки отсутствуют.', description: 'Проблемы на стороне iiko. Сроки отсутствуют.\n\n' + runbook,
        status: 'open', severity: 'high', events: [{ id: 1, event_type: 'comment', event_text: flat }, { id: 2, event_type: 'comment', event_text: unsafe }], watchers: [] };
      let rejectOnce = true;
      await context.route('**/*', route => {
        const req = route.request(), url = new URL(req.url());
        if (url.origin !== origin) { externalRequests.push(url.origin); return route.abort(); }
        if (!url.pathname.startsWith('/api/')) return route.continue();
        if (url.pathname === '/api/incidents/238/events' && req.method() === 'POST') {
          const body = req.postDataJSON(); requests.push(body);
          if (rejectOnce) { rejectOnce = false; return route.fulfill({ status: 400, json: { message: 'Проверка: комментарий не сохранён' } }); }
          incident.events.push({ id: 100 + requests.length, ...body, actor: 'operator' });
          return route.fulfill({ json: { incident } });
        }
        assert.equal(req.method(), 'GET', 'No unrelated mutation');
        if (url.pathname === '/api/incidents') return route.fulfill({ json: { items: [incident] } });
        if (url.pathname === '/api/incidents/238') return route.fulfill({ json: { incident } });
        if (url.pathname === '/api/panel-users/options') return route.fulfill({ json: [] });
        return route.fulfill({ json: { items: [] } });
      });
      const page = await context.newPage(); page.on('pageerror', ex => errors.push(ex.message));
      const noOverflow = async () => assert(!await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1), 'No horizontal overflow: ' + width);
      await page.goto(origin + '/screens/incidents/index'); await page.locator('.incident-explanation').waitFor();
      const technical = page.locator('.incident-original-diagnostics'); assert(!await technical.evaluate(node => node.open));
      await technical.locator('summary').click();
      const description = technical.locator('dd').last();
      assert.equal(await description.textContent(), incident.description);
      assert.equal(await description.innerText(), incident.description, 'Visible paragraphs/indentation are not collapsed');
      assert.equal(await description.evaluate(node => getComputedStyle(node).whiteSpace), 'pre-wrap');
      assert.equal(await technical.locator('add, script, img').count(), 0, 'XML is visible text, never executable markup');
      assert.equal(await page.locator('.incident-explanation-summary').innerText(), incident.summary);
      await noOverflow(); await technical.screenshot({ path: path.join(output, `technical-${theme}-${width}.png`) });

      const timeline = page.locator('.incident-timeline'); await timeline.locator('summary').click();
      assert.equal(await timeline.locator('.incident-event-text').first().innerText(), unsafe);
      assert.equal(await timeline.locator('img, script').count(), 0);
      for (const newline of ['\n', '\r\n']) {
        const editor = page.locator('#incidentEventText'); assert.equal(await editor.evaluate(node => node.tagName), 'TEXTAREA');
        await editor.fill(runbook.replaceAll('\n', newline));
        const entered = await editor.inputValue();
        assert.equal(entered.replaceAll('\r\n', '\n'), runbook, 'LF/CRLF retain the same paragraphs and indentation');
        if (requests.length === 0) {
          await page.locator('#incidentAddEventButton').click();
          await page.waitForFunction(() => document.getElementById('incidentWorkbenchError').textContent.includes('не сохранён'));
          assert.equal(await editor.inputValue(), runbook, 'Server error preserves comment draft');
          await editor.screenshot({ path: path.join(output, `editor-${theme}-${width}.png`) });
        }
        const count = requests.length;
        await page.locator('#incidentAddEventButton').click();
        await page.waitForFunction(count => document.querySelectorAll('.incident-event-text').length === count + 2, count);
        assert.equal(requests.at(-1).event_text, entered, 'POST preserves the actual textarea value'); assert.equal(requests.at(-1).event_type, 'comment');
        if (!await timeline.evaluate(node => node.open)) await timeline.locator('summary').click();
        assert.equal(await timeline.locator('.incident-event-text').first().innerText(), runbook);
        assert.equal(await timeline.locator('add, img, script').count(), 0);
      }
      await page.reload(); await page.locator('.incident-explanation').waitFor();
      await page.locator('.incident-timeline summary').click();
      assert.equal(await page.locator('.incident-event-text').first().innerText(), runbook, 'Reload reads unchanged stored text');
      assert.equal(incident.events[0].event_text, flat, 'Historical flattened comments are not rewritten');
      await page.locator('.incident-timeline').screenshot({ path: path.join(output, `timeline-${theme}-${width}.png`) });
      assert.equal(await page.evaluate(() => window.injection), undefined); assert.deepEqual(errors, []); assert.deepEqual(externalRequests, []); await noOverflow();
      results.push({ theme, width, paragraphs: true, indentation: true, literalXml: true, comments: 2, errorPreservesDraft: true, reload: true, historicalFlatUnchanged: true, errors });
      await context.close();
    }
    fs.writeFileSync(path.join(output, 'report.json'), JSON.stringify(results, null, 2));
    console.log(JSON.stringify({ scenarios: results.length, roundTrips: results.length * 2, screenshots: results.length * 3, output }));
  } finally { await browser.close(); await new Promise(resolve => server.close(resolve)); }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
