/* R11: real MVC HTML/workbench; all requests stay on a fixture-only server. */
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..');
const resources = path.join(root, 'spring-panel/src/main/resources');
const captures = path.join(root, 'spring-panel/target/neutral-ops-rendered');
const output = path.join(root, 'spring-panel/target/neutral-ops-r11-notifications');

async function geometry(page) {
  return page.evaluate(() => ({
    tops: ['main', '#incidentWorkbenchTabs', '#incidentWorkbenchList'].map(selector =>
      document.querySelector(selector).getBoundingClientRect().top + scrollY),
    scroll: scrollY,
    overflow: document.documentElement.scrollWidth > innerWidth + 1
  }));
}
function stable(before, after) {
  assert.deepEqual(after, before, 'Notice must not move content or scroll the document');
}
async function main() {
  fs.mkdirSync(output, { recursive: true });
  const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
  const server = http.createServer((req, res) => {
    const pathname = new URL(req.url, 'http://localhost').pathname;
    const base = pathname.startsWith('/screens/') ? captures : path.join(resources, 'static');
    const name = pathname.startsWith('/screens/') ? pathname.slice(9) + '.html' : pathname.slice(1);
    const file = path.resolve(base, name);
    if (!file.startsWith(base + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) {
      res.writeHead(404); res.end('{}'); return;
    }
    const mime = { '.html': 'text/html; charset=utf-8', '.css': 'text/css', '.js': 'text/javascript', '.svg': 'image/svg+xml', '.png': 'image/png', '.woff2': 'font/woff2' };
    res.setHeader('Content-Type', mime[path.extname(file)] || 'application/octet-stream');
    fs.createReadStream(file).pipe(res);
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const origin = `http://127.0.0.1:${server.address().port}`;
  const browser = await chromium.launch({ headless: true });
  const results = [];
  try {
    for (const theme of ['light', 'dark']) for (const width of [320, 390, 768, 1440, 1920]) {
      const touch = width <= 390;
      const motion = width === 1440 ? 'no-preference' : 'reduce';
      const context = await browser.newContext({ viewport: { width, height: 1000 }, colorScheme: theme, hasTouch: touch, isMobile: touch, reducedMotion: motion });
      await context.addInitScript(theme => {
        localStorage.setItem('iguana:theme', theme);
        localStorage.setItem('iguana:theme-palette', 'neo');
        localStorage.setItem('sidebarPinned', '1');
      }, theme);
      const incident = { id: 731, incident_key: 'INC-731', title: 'Fixture incident', summary: 'Notification layout regression', severity: 'high', status: 'open', updated_at: '2026-10-23T10:00:00Z' };
      const patches = [], errors = [];
      let failNextPatch = false;
      await context.route('**/*', route => {
        const request = route.request(), url = new URL(request.url());
        if (url.origin !== origin) return route.abort();
        if (url.pathname.startsWith('/api/')) {
          if (request.method() === 'PATCH' && url.pathname === '/api/incidents/731') {
            patches.push(request.postDataJSON());
            if (failNextPatch) { failNextPatch = false; return route.fulfill({ status: 503, json: { message: 'Fixture status error' } }); }
            incident.status = request.postDataJSON().status;
            return route.fulfill({ json: { incident } });
          }
          assert(['GET', 'HEAD'].includes(request.method()), 'Unexpected fixture mutation: ' + request.method() + ' ' + url.pathname);
          if (url.pathname === '/api/incidents') return route.fulfill({ json: { items: [incident] } });
          if (url.pathname === '/api/incidents/731') return route.fulfill({ json: { incident } });
          return route.fulfill({ json: {} });
        }
        assert(['GET', 'HEAD'].includes(request.method()));
        return route.continue();
      });
      const page = await context.newPage();
      page.on('pageerror', error => errors.push(error.message));
      await page.goto(origin + '/screens/incidents/index', { waitUntil: 'load' });
      await page.locator('#incidentQuickStatus').waitFor();
      await page.waitForTimeout(200); // supplementary detail cards and fonts settle
      const success = page.locator('#incidentWorkbenchSuccess');
      const failure = page.locator('#incidentWorkbenchError');
      assert(!await success.isVisible() && !await failure.isVisible());
      await page.locator('#incidentQuickStatus').scrollIntoViewIfNeeded();
      const beforeStatus = await geometry(page);
      await page.locator('#incidentQuickStatus').selectOption('acknowledged');
      await success.waitFor({ state: 'visible' });
      await page.waitForFunction(() => document.getElementById('incidentQuickStatus')?.value === 'acknowledged' && document.getElementById('incidentWorkbenchDetail').getAttribute('aria-busy') === 'false');
      await page.waitForTimeout(240);
      stable(beforeStatus, await geometry(page));
      assert.deepEqual(patches, [{ status: 'acknowledged' }]);
      const presentation = await success.evaluate(el => {
        const item = el.parentElement, stack = item.parentElement, rect = item.getBoundingClientRect();
        return { fixed: getComputedStyle(stack).position, pointer: getComputedStyle(stack).pointerEvents,
          animation: getComputedStyle(item).animationName, role: el.getAttribute('role'),
          inBounds: rect.left >= 0 && rect.right <= innerWidth && rect.top >= 0 && rect.bottom <= innerHeight,
          closeSize: item.querySelector('button').getBoundingClientRect().height };
      });
      assert.equal(presentation.fixed, 'fixed'); assert.equal(presentation.pointer, 'none');
      assert.equal(presentation.role, 'status'); assert(presentation.inBounds);
      assert.equal(presentation.animation, motion === 'reduce' ? 'none' : 'incident-notification-enter');
      assert.equal(presentation.closeSize, touch ? 44 : 28);
      await page.screenshot({ path: path.join(output, `success-${theme}-${width}.png`) });
      await success.waitFor({ state: 'hidden', timeout: 6000 });
      stable(beforeStatus, await geometry(page));

      failNextPatch = true;
      await page.locator('#incidentQuickStatus').selectOption('investigating');
      await failure.waitFor({ state: 'visible' });
      await page.waitForTimeout(200);
      assert.equal(await failure.textContent(), 'Fixture status error');
      stable(beforeStatus, await geometry(page));
      assert.equal(await page.locator('#incidentQuickStatus').inputValue(), 'acknowledged');
      await page.screenshot({ path: path.join(output, `error-${theme}-${width}.png`) });
      // Keyboard dismissal restores focus without scrolling and keeps the node reusable.
      await page.locator('#incidentWorkbenchRefresh').evaluate(el => el.focus({ preventScroll: true }));
      await page.locator('[data-incident-notice-close="incidentWorkbenchError"]').evaluate(el => el.focus({ preventScroll: true }));
      await page.keyboard.press('Enter');
      assert(!await failure.isVisible());
      assert(await page.locator('#incidentWorkbenchRefresh').evaluate(el => el === document.activeElement));
      stable(beforeStatus, await geometry(page));
      const beforeLong = await geometry(page);
      // Producer-compatible textContent/classList API, long errors and repeated notices.
      await failure.evaluate(el => { el.textContent = 'LongError'.repeat(150); el.classList.remove('d-none'); });
      await page.waitForTimeout(240);
      assert(await page.locator('#incidentWorkbenchRefresh').evaluate(el => el === document.activeElement), 'Appearance must not steal focus');
      const long = await failure.evaluate(el => {
        const rect = el.getBoundingClientRect();
        return { inBounds: rect.left >= 0 && rect.right <= innerWidth && rect.top >= 0 && rect.bottom <= innerHeight, wraps: el.scrollWidth <= el.clientWidth + 1, scrollsInside: el.scrollHeight > el.clientHeight };
      });
      assert(long.inBounds && long.wraps && long.scrollsInside);
      stable(beforeLong, await geometry(page));
      await page.locator('[data-incident-notice-close="incidentWorkbenchError"]').click();
      assert.equal(await failure.count(), 1, 'Dismissal must not remove legacy producer node');
      await failure.evaluate(el => { el.textContent = 'Repeated notice'; el.classList.remove('d-none'); });
      assert(await failure.isVisible());
      assert.equal(await failure.getAttribute('role'), 'alert');
      assert.deepEqual(errors, []);
      results.push({ theme, width, touch, motion, patches, geometryStable: true, presentation, long, errors });
      await context.close();
    }
    fs.writeFileSync(path.join(output, 'report.json'), JSON.stringify(results, null, 2));
    console.log(JSON.stringify({ scenarios: results.length, screenshots: results.length * 2, output }));
  } finally { await browser.close(); await new Promise(resolve => server.close(resolve)); }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
