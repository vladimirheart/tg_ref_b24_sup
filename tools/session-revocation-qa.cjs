/* Real users UI and CSRF fetch wrapper in headless Chromium. Synthetic API, all network blocked. Node 22+. */
const fs = require('node:fs'), path = require('node:path'), cp = require('node:child_process');
const crypto = require('node:crypto'), assert = require('node:assert/strict');
const vm = require('node:vm');
const root = path.resolve(__dirname, '..');
const reportFile = path.resolve(process.argv.find(arg => arg.startsWith('--report='))?.slice(9)
  || path.join(root, 'spring-panel/target/session-revocation-qa/report.json'));
const inputs = ['spring-panel/src/main/resources/static/js/auth-management.js',
  'spring-panel/src/main/resources/templates/settings/fragments/users-access.html', 'spring-panel/src/main/resources/static/js/common.js',
  'spring-panel/src/main/resources/static/js/sidebar.js'];
const contents = inputs.map(file => fs.readFileSync(path.join(root, file), 'utf8'));
const sources = inputs.map((file, i) => ({ file, sha256: crypto.createHash('sha256').update(contents[i]).digest('hex') }));
const csrfEnd = contents[2].indexOf('// Функция для показа уведомлений');
assert(csrfEnd > 0, 'Expected the production CSRF wrapper before notifications');
const controllerFiles = ['photo','user','roles','organization'].map(name => 'spring-panel/src/main/resources/static/js/auth-management-' + name + '.js');
const controllerSources = controllerFiles.map(file => fs.readFileSync(path.join(root, file), 'utf8'));
controllerFiles.forEach((file, i) => sources.push({ file, sha256: crypto.createHash('sha256').update(controllerSources[i]).digest('hex') }));
const payload = { source: controllerSources.join('\n') + '\n' + contents[0], fragment: contents[1], csrf: contents[2].slice(0, csrfEnd) };

async function sseCases() {
  const start = contents[3].indexOf('  function connectPanelEvents() {');
  const end = contents[3].indexOf('\n  refreshSidebarBotMiniIndicators();', start);
  assert(start >= 0 && end > start, 'Expected the production SSE connection function');
  const results = [];
  for (const status of [200, 401, 503, 'offline']) {
    let probes = 0, redirects = 0;
    class Source {
      static CLOSED = 2;
      constructor() { this.listeners = {}; this.readyState = 1; }
      addEventListener(name, listener) { this.listeners[name] = listener; }
      close() { this.readyState = 2; }
    }
    const context = vm.createContext({ window: { EventSource: Source, location: { assign: url => {
      assert.equal(url, '/login?sessionExpired'); redirects++;
    } } }, panelEventsSource: null, panelSessionProbeAttempted: false, PANEL_SSE_URL: '/api/events/stream',
      setPanelEventsConnected() {}, dispatchPanelSseEvent() {}, parseSsePayload() { return {}; }, schedulePanelEventsReconnect() {},
      fetch: async (url, options) => {
        probes++; assert.equal(url, '/api/auth/ping'); assert.equal(options.headers.Accept, 'application/json');
        if (status === 'offline') throw Error('Fixture offline'); return { status };
      } });
    vm.runInContext(contents[3].slice(start, end) + '\nconnectPanelEvents();', context);
    const source = context.panelEventsSource;
    source.onerror(); await new Promise(resolve => setImmediate(resolve));
    source.onerror(); await new Promise(resolve => setImmediate(resolve));
    assert.equal(probes, 1, 'Repeated SSE errors must not create HTTP keep-alive polling');
    assert.equal(redirects, status === 401 ? 1 : 0, 'Only a confirmed 401 should redirect');
    source.listeners.connected({}); source.onerror(); await new Promise(resolve => setImmediate(resolve));
    assert.equal(probes, 2, 'Successful reconnect permits a fresh check');
    source.listeners.session_revoked(); assert.equal(source.readyState, 2); assert(redirects >= 1);
    results.push({ action: 'sse-' + status, ok: true });
  }
  return results;
}

class Cdp {
  constructor(ws) {
    this.ws = ws; this.next = 0; this.pending = new Map();
    ws.addEventListener('message', event => {
      const message = JSON.parse(String(event.data)), pending = this.pending.get(message.id);
      if (!pending) return;
      clearTimeout(pending.timer); this.pending.delete(message.id);
      if (message.error) pending.reject(Error(JSON.stringify(message.error))); else pending.resolve(message.result);
    });
  }
  send(method, params = {}, sessionId) {
    return new Promise((resolve, reject) => {
      const id = ++this.next, timer = setTimeout(() => { this.pending.delete(id); reject(Error('CDP timeout: ' + method)); }, 60000);
      this.pending.set(id, { resolve, reject, timer }); this.ws.send(JSON.stringify({ id, method, params, sessionId }));
    });
  }
}

async function suite(input) {
  const cases = [];
  for (const action of ['single', 'all', 'self', 'cancel', 'denied', 'server-error', 'unauthorized', 'busy']) {
    const frame = document.createElement('iframe'); document.body.append(frame);
    try {
      const win = frame.contentWindow;
      win.document.open(); win.document.write('<html><head><meta charset="UTF-8"><meta name="_csrf" content="fixture-csrf"></head><body>' + input.fragment + '</body></html>'); win.document.close();
      await win.eval('(' + exercise.toString() + ')(' + JSON.stringify({ ...input, action }) + ')');
      cases.push({ action, ok: true });
    } catch (error) { cases.push({ action, ok: false, message: error.message }); }
    finally { frame.remove(); }
  }
  return cases;

  async function exercise(data) {
    const check = (condition, message) => { if (!condition) throw Error(message); };
    const tick = () => new Promise(resolve => setTimeout(resolve, 0));
    const wait = async test => { for (let i = 0; i < 100; i++) { if (test()) return; await tick(); } throw Error('UI did not settle'); };
    const writes = [], confirmations = [], errors = []; let redirects = 0, release;
    window.addEventListener('error', event => errors.push(event.message));
    const user = { id: 99, username: 'target', can_revoke_sessions: data.action !== 'denied', phones: [] };
    window.showConfirmActionModal = async options => { confirmations.push(options); return data.action !== 'cancel'; };
    window.fetch = async (url, options = {}) => {
      if (url === '/api/auth/state') return { ok: true, json: async () => ({ users: [user], roles: [],
        capabilities: { fields: { edit: {}, view: {} }, sessions: { revoke_all: data.action !== 'denied' } },
        catalog: { pages: [], fields: { edit: [], view: [] } }, current_user_id: 7, org_structure: { nodes: [] } }) };
      writes.push({ url, options });
      check(options.method === 'POST', 'Revocation must POST');
      check(options.credentials === 'same-origin', 'Session credentials required');
      check(new Headers(options.headers).get('X-XSRF-TOKEN') === 'fixture-csrf', 'Production CSRF wrapper must attach token');
      check(new Headers(options.headers).get('Accept') === 'application/json', 'Expired login must request JSON');
      if (data.action === 'busy') await new Promise(resolve => { release = resolve; });
      return { ok: data.action !== 'server-error' && data.action !== 'unauthorized',
        status: data.action === 'unauthorized' ? 401 : data.action === 'server-error' ? 503 : 200,
        json: async () => ({ success: data.action !== 'server-error', current_user_revoked: ['all', 'self'].includes(data.action) }) };
    };
    (0, eval)(data.csrf); (0, eval)(data.source);
    const root = document.querySelector('[data-auth-management]'), manager = window.AuthManagement.mount(root);
    manager.redirectToLogin = () => { redirects++; };
    await wait(() => manager.state.users.length === 1 && !root.classList.contains('auth-management--loading'));
    const globalButton = root.querySelector('[data-auth-sessions-revoke-all]');
    const singleButton = root.querySelector('[data-action="revoke-sessions"]');
    if (data.action === 'denied') {
      check(globalButton.disabled && globalButton.classList.contains('d-none') && !singleButton, 'Unauthorized controls must be hidden');
      globalButton.click(); await tick(); check(writes.length === 0 && confirmations.length === 0, 'Hidden action must not write'); return;
    }
    const button = data.action === 'all' ? globalButton : singleButton;
    button.click(); await tick();
    if (data.action === 'busy') {
      await wait(() => Boolean(release)); check(button.disabled, 'Pending button must be disabled'); button.click(); await tick();
      check(writes.length === 1, 'Duplicate click must not revoke twice'); release();
    }
    await wait(() => !manager.revokingSessions);
    check(confirmations.length === 1, 'Confirmation required');
    if (data.action === 'cancel') { check(writes.length === 0, 'Cancel must not write'); return; }
    check(writes.length === 1, 'Exactly one revocation request');
    check(writes[0].url === (data.action === 'all' ? '/api/users/sessions/revoke-all' : '/api/users/99/sessions/revoke'), 'Correct scope and numeric target required');
    if (data.action === 'all') check(confirmations[0].message.includes('включая ваш'), 'Global confirmation must include operator');
    if (['all', 'self', 'unauthorized'].includes(data.action)) check(redirects === 1, 'Own invalidated session must return to login');
    else check(redirects === 0, 'Other-user action must preserve operator');
    if (data.action === 'server-error') check(root.querySelector('[data-auth-message]').textContent.includes('Не удалось'), 'Failure must be visible');
    check(!button.disabled, 'Button must recover after response'); check(errors.length === 0, 'No runtime errors');
  }
}

(async () => {
  const streamResults = await sseCases();
  const executable = process.env.BROWSER_BINARY || ['C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    'C:/Program Files/Google/Chrome/Application/chrome.exe', '/usr/bin/chromium'].find(file => fs.existsSync(file));
  assert(executable, 'Set BROWSER_BINARY'); fs.mkdirSync(path.dirname(reportFile), { recursive: true });
  const profile = path.join(path.dirname(reportFile), 'browser-' + crypto.randomUUID()); fs.mkdirSync(profile);
  const log = fs.openSync(profile + '.log', 'wx');
  const child = cp.spawn(executable, ['--headless=new', '--disable-gpu', '--no-first-run', '--disable-background-networking',
    '--remote-debugging-port=0', '--remote-debugging-address=127.0.0.1', '--user-data-dir=' + profile, 'about:blank'],
    { windowsHide: true, stdio: ['ignore', log, log] });
  let peer;
  try {
    const active = path.join(profile, 'DevToolsActivePort'), deadline = Date.now() + 20000;
    while (!fs.existsSync(active)) { assert(child.exitCode === null && Date.now() < deadline, 'Browser startup failed'); await new Promise(resolve => setTimeout(resolve, 100)); }
    const [port, endpoint] = fs.readFileSync(active, 'utf8').trim().split(/\r?\n/), ws = new WebSocket('ws://127.0.0.1:' + port + endpoint);
    await new Promise((resolve, reject) => { ws.addEventListener('open', resolve, { once: true }); ws.addEventListener('error', reject, { once: true }); });
    peer = new Cdp(ws); const browser = await peer.send('Browser.getVersion');
    const { targetId } = await peer.send('Target.createTarget', { url: 'about:blank' });
    const { sessionId } = await peer.send('Target.attachToTarget', { targetId, flatten: true });
    await peer.send('Network.enable', {}, sessionId); await peer.send('Network.setBlockedURLs', { urls: ['*'] }, sessionId);
    const result = await peer.send('Runtime.evaluate', { expression: '(' + suite.toString() + ')(' + JSON.stringify(payload) + ')', awaitPromise: true, returnByValue: true }, sessionId);
    assert(!result.exceptionDetails, JSON.stringify(result.exceptionDetails)); const cases = [...result.result.value, ...streamResults];
    const report = { browser: browser.product, tests: cases.length, failures: cases.filter(item => !item.ok).length, cases, sources, finished: new Date().toISOString() };
    fs.writeFileSync(reportFile, JSON.stringify(report, null, 2)); console.log(JSON.stringify(report)); process.exitCode = report.failures ? 1 : 0;
  } finally {
    if (peer) { try { await peer.send('Browser.close'); } catch (_) {} peer.ws.close(); }
    if (child.exitCode === null) child.kill(); fs.closeSync(log);
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
