/* Local editor/catalog assets and delayed API doubles. No working bots or providers are contacted. */
'use strict';
const fs = require('node:fs'), path = require('node:path'), http = require('node:http'), assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..'), assets = path.join(root, 'spring-panel/src/main/resources/static');
const output = path.join(root, 'spring-panel/target/bot-lifecycle-ui-qa'); fs.mkdirSync(output, { recursive: true });
const editor = fs.readFileSync(path.join(root, 'spring-panel/src/main/resources/templates/settings/fragments/channel-editor.html'), 'utf8').replace(/<\/?th:block[^>]*>/g, '');
const html = `<!doctype html><html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<link rel="stylesheet" href="/vendor/bootstrap/5.3.3/bootstrap.min.css"><link rel="stylesheet" href="/vendor/bootstrap-icons/1.10.5/bootstrap-icons.css">
<link rel="stylesheet" href="/css/style.css"><link rel="stylesheet" href="/css/app.css"><link rel="stylesheet" href="/css/settings.css"></head>
<body data-ui-page="settings"><section id="channelsModal" class="container py-3"><h1 class="h4">Каналы</h1><div class="table-responsive"><table class="table"><tbody id="channelsBody"></tbody></table></div></section>
${editor}<script src="/vendor/bootstrap/5.3.3/bootstrap.bundle.min.js"></script>
<script src="/js/settings-permissions.js"></script><script src="/js/settings-channels-bot-runtime.js"></script>
<script src="/js/settings-channels-catalog-runtime.js"></script><script src="/js/settings-channel-editor-controls-runtime.js"></script>
<script>
window.messages=[]; const registry=new Map(), state={channelId:1};
const runtime=SettingsChannelsBotRuntime.mount({getChannelsRegistry:()=>registry,getChannelEditorState:()=>state,showPopup:message=>messages.push(message),getCookieValue:()=> 'fixture-xsrf'});
const rows=[{id:1,name:'Telegram',platform:'telegram',is_active:true},{id:2,name:'MAX',platform:'max',is_active:true}];
const catalog=SettingsChannelsCatalogRuntime.mount({getChannelsRegistry:()=>registry,startBot:id=>runtime.startBot(id),stopBot:id=>runtime.stopBot(id),restartBot:id=>runtime.restartBot(id)});
document.getElementById('channelsBody').addEventListener('click',catalog.handleChannelsBodyClick);
SettingsChannelEditorControlsRuntime.mount({getChannelEditorState:()=>state,channelBotRuntime:runtime}).bindControls();
window.fixture={runtime,state,catalog,rows,open:async id=>{state.channelId=id;await runtime.refreshBotStatus(id);bootstrap.Modal.getOrCreateInstance(document.getElementById('channelEditorModal')).show();}};
async function init(){while(!window.SettingsPermissions)await new Promise(resolve=>setTimeout(resolve,10));catalog.renderChannels(rows);await runtime.refreshAllBotRuntimeStatuses();window.fixtureReady=true;}init();
</script></body></html>`;

async function main() {
  const server = http.createServer((req, res) => {
    const pathname = new URL(req.url, 'http://localhost').pathname;
    if (pathname === '/fixture') { res.setHeader('Content-Type', 'text/html; charset=utf-8'); res.end(html); return; }
    const file = path.resolve(assets, pathname.slice(1));
    if (!file.startsWith(assets + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) { res.writeHead(404); res.end('{}'); return; }
    res.setHeader('Content-Type', ({ '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml' })[path.extname(file)] || 'application/octet-stream');
    if (/\.(js|css)$/.test(file)) res.end(fs.readFileSync(file, 'utf8').replace(/\r\n/g, '\n'));
    else fs.createReadStream(file).pipe(res);
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
  const browser = await chromium.launch({ headless: true, ...(process.env.PLAYWRIGHT_BROWSER_EXECUTABLE ? { executablePath: process.env.PLAYWRIGHT_BROWSER_EXECUTABLE } : {}) });
  const origin = `http://127.0.0.1:${server.address().port}`, results = [];
  try {
    for (const theme of ['light', 'dark']) for (const width of [390, 1440]) {
      const context = await browser.newContext({ viewport: { width, height: 1000 }, colorScheme: theme });
      await context.addInitScript(theme => document.addEventListener('DOMContentLoaded', () => { document.documentElement.dataset.theme = theme; }), theme);
      const actions = [], pending = [], errors = [], statuses = { 1: 'running', 2: 'stopped' };
      let allowed = true;
      await context.route('**/*', async route => {
        const req = route.request(), url = new URL(req.url()); assert.equal(url.origin, origin);
        if (url.pathname === '/api/auth/permissions') return route.fulfill({ json: { settings: { channels: allowed } } });
        const match = url.pathname.match(/^\/api\/bots\/(\d+)\/(start|stop|status)$/);
        if (!match) { assert.equal(req.method(), 'GET'); return route.continue(); }
        const [, id, action] = match;
        if (action === 'status') return route.fulfill({ json: { success: true, status: statuses[id], startedAt: null } });
        assert(allowed, 'View-only user must not send lifecycle commands'); assert.equal(req.method(), 'POST');
        assert.equal(req.headers()['x-xsrf-token'], 'fixture-xsrf'); actions.push({ id, action });
        return new Promise(resolve => pending.push(async (success = true) => {
          if (success) statuses[id] = action === 'stop' ? 'stopped' : 'running';
          await route.fulfill({ status: success ? 200 : 503, json: { success, status: success ? statuses[id] : 'runner unavailable' } }); resolve();
        }));
      });
      const page = await context.newPage(); page.on('pageerror', error => errors.push(error.message));
      await page.goto(origin + '/fixture'); await page.waitForFunction(() => window.fixtureReady);
      const rowRestart = page.locator('[data-channel-restart="1"]');
      assert.equal(await rowRestart.isDisabled(), false); assert.equal(await page.locator('[data-channel-restart="2"]').isDisabled(), true);
      await rowRestart.click(); await page.locator('[data-channel-bot-runtime-status="1"] .spinner-border').waitFor();
      assert.equal(await rowRestart.isDisabled(), true);
      await page.evaluate(() => { window.fixture.runtime.restartBotForChannel(1); window.fixture.runtime.stopBotForChannel(1); });
      assert.equal(actions.length, 1);
      await page.evaluate(() => window.fixture.catalog.renderChannels(window.fixture.rows));
      await page.evaluate(() => window.fixture.runtime.refreshAllBotRuntimeStatuses());
      await page.locator('[data-channel-restart="1"] .spinner-border').waitFor();
      await pending.shift()(); await page.locator('[data-channel-restart="1"] .spinner-border').waitFor({ state: 'detached' });
      assert.equal(await rowRestart.isDisabled(), false);

      await page.evaluate(() => window.fixture.open(1)); await page.locator('#channel-editor-runtime-tab').click();
      const modal = page.locator('#channelEditorModal'), restart = page.locator('#channelEditorBotRestartBtn');
      await restart.click(); await restart.locator('.spinner-border').waitFor();
      assert.equal(await page.locator('#channelEditorBotStartBtn').isDisabled(), true);
      assert.equal(await page.locator('#channelEditorBotStopBtn').isDisabled(), true);
      assert((await modal.locator('[data-channel-editor-bot-status-badge]').textContent()).includes('Перезапускаем'));
      await page.screenshot({ path: path.join(output, `restart-${theme}-${width}.png`), fullPage: true });
      await pending.shift()(); await restart.locator('.spinner-border').waitFor({ state: 'detached' });
      assert.equal(await restart.textContent(), 'Перезапустить'); assert.equal(await restart.isDisabled(), false);

      await page.locator('#channelEditorBotStopBtn').click(); await page.locator('#channelEditorBotStopBtn .spinner-border').waitFor();
      await pending.shift()(); await page.locator('#channelEditorBotStopBtn .spinner-border').waitFor({ state: 'detached' });
      assert.equal(await restart.isDisabled(), true); assert.equal(await page.locator('#channelEditorBotStartBtn').isDisabled(), false);
      await page.locator('#channelEditorBotStartBtn').click(); await page.locator('#channelEditorBotStartBtn .spinner-border').waitFor();
      await pending.shift()(false); await page.locator('#channelEditorBotStartBtn .spinner-border').waitFor({ state: 'detached' });
      assert((await page.evaluate(() => window.messages.at(-1))).includes('runner unavailable'));
      await page.locator('#channelEditorBotStartBtn').click(); await page.locator('#channelEditorBotStartBtn .spinner-border').waitFor();
      await pending.shift()(); await page.locator('#channelEditorBotStartBtn .spinner-border').waitFor({ state: 'detached' });
      assert.equal(await restart.isDisabled(), false);
      allowed = false;
      await page.evaluate(() => window.SettingsPermissions.refresh());
      await page.evaluate(() => window.fixture.runtime.refreshBotStatus(1));
      assert.equal(await restart.isDisabled(), true); assert.equal(await page.locator('#channelEditorBotStopBtn').isDisabled(), true);
      const before = actions.length;
      await page.evaluate(() => window.fixture.runtime.restartBotForChannel(1)); assert.equal(actions.length, before);
      assert.deepEqual(errors, []); results.push({ theme, width, commands: actions.length, errors }); await context.close();
    }
    fs.writeFileSync(path.join(output, 'browser-report.json'), JSON.stringify({ status: 'GREEN', results }, null, 2));
    console.log('GREEN: 4 contexts; real catalog/editor buttons, visible spinners, duplicate suppression, rerender, start/stop/restart, failure/retry, permission revocation.');
  } finally { await browser.close(); await new Promise(resolve => server.close(resolve)); }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
