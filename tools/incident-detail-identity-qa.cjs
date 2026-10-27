/* Real incident detail DOM + UI decorator + workbench + picker. Fixture API only, all browser network blocked. Node 22+. */
const fs = require('node:fs'), path = require('node:path'), cp = require('node:child_process');
const crypto = require('node:crypto'), assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..');
const reportFile = path.resolve(process.argv.find(arg => arg.startsWith('--report='))?.slice(9)
  || path.join(root, 'spring-panel/target/incident-detail-identity-r186/report.json'));
const inputs = ['spring-panel/src/main/resources/static/js/employee-picker.js',
  'spring-panel/src/main/resources/static/js/incident-workbench-ui.js',
  'spring-panel/src/main/resources/static/js/incident-descriptions.js',
  'spring-panel/src/main/resources/static/js/incidents-workbench.js',
  'spring-panel/src/main/resources/templates/incidents/index.html'];
const sources = inputs.map(file => ({ file, sha256: crypto.createHash('sha256').update(fs.readFileSync(path.join(root, file))).digest('hex') }));
const html = fs.readFileSync(path.join(root, inputs.at(-1)), 'utf8');
const body = html.match(/<body\b[^>]*>([\s\S]*?)<\/body>/); assert(body, 'Real incident page body');
const payload = { scripts: inputs.slice(0, -1).map(file => ({ name: path.basename(file), source: fs.readFileSync(path.join(root, file), 'utf8') })), fragment: body[1].replace(/<script\b[\s\S]*?<\/script>/g, '') };

class Cdp {
  constructor(ws) {
    this.ws = ws; this.next = 0; this.pending = new Map();
    ws.addEventListener('message', event => {
      const message = JSON.parse(String(event.data));
      if (message.id) {
        const pending = this.pending.get(message.id); if (!pending) return;
        clearTimeout(pending.timer); this.pending.delete(message.id);
        if (message.error) pending.reject(Error(JSON.stringify(message.error))); else pending.resolve(message.result);
      } else if (message.method === 'Runtime.consoleAPICalled' && message.params.args[0]?.value === 'identity-case') {
        console.log(message.params.args[1]?.value);
      }
    });
    ws.addEventListener('close', () => { for (const pending of this.pending.values()) { clearTimeout(pending.timer); pending.reject(Error('Browser connection closed')); } this.pending.clear(); });
  }
  send(method, params = {}, sessionId) {
    return new Promise((resolve, reject) => {
      const id = ++this.next, timer = setTimeout(() => { this.pending.delete(id); reject(Error('CDP timeout: ' + method)); }, 180000);
      this.pending.set(id, { resolve, reject, timer }); this.ws.send(JSON.stringify({ id, method, params, ...(sessionId ? { sessionId } : {}) }));
    });
  }
}

async function browserSuite(input) {
  const cases = [], variants = ['reader', ' reader', 'reader ', ' reader ', '\treader\t', '\nreader\r', 'mem\r\nber',
    'member,one', 'member;one', '9007199254740993', '<img src=x onerror="window.__injected=1">', 'ReAdEr', ' user/%?#&+ Ю '];
  async function scenario(name, action, extra = {}) {
    const frame = document.createElement('iframe'); document.body.append(frame);
    try {
      const win = frame.contentWindow;
      win.document.open(); win.document.write('<!doctype html><html><head><meta charset="UTF-8"><meta name="_csrf" content="fixture-csrf"></head><body>' + input.fragment + '</body></html>'); win.document.close();
      await win.eval('(' + exercise.toString() + ')(' + JSON.stringify({ ...input, action, target: ' reader ', ...extra }) + ')');
      cases.push({ name, ok: true });
    } catch (error) { cases.push({ name, ok: false, kind: error.name === 'AssertionError' ? 'failure' : 'error', message: error.message }); }
    finally { frame.remove(); }
    console.log('identity-case', JSON.stringify(cases.at(-1)));
  }
  for (const action of ['owner-loaded', 'owner-picked', 'watcher-add', 'watcher-remove', 'user-route'])
    for (const target of variants) await scenario(action + ' ' + JSON.stringify(target), action, { target });
  for (const action of ['owner-clear', 'owner-blank', 'owner-missing', 'owner-directory-failure', 'owner-retry', 'owner-draft-watcher', 'owner-draft-route', 'owner-switch', 'owner-focus',
    'watcher-retry', 'watcher-keyboard', 'watcher-blank', 'watcher-remove-focus', 'user-retry', 'user-blank', 'user-switch', 'users-csv', 'webhook', 'department', 'all_operators', 'scoped-catalog'])
    await scenario(action, action, action === 'watcher-keyboard' ? { target: 'member;one' } : {});
  return cases;

  async function exercise(data) {
    const check = (condition, message) => { if (!condition) { const error = Error(message); error.name = 'AssertionError'; throw error; } };
    const equal = (actual, expected, label) => check(JSON.stringify(actual) === JSON.stringify(expected), label + ': ' + JSON.stringify({ actual, expected }));
    const qa = { writes: [], requests: [], errors: [], deny: data.action.endsWith('retry') }; window.__identityQa = qa;
    window.addEventListener('error', event => qa.errors.push(event.message)); window.addEventListener('unhandledrejection', event => qa.errors.push(String(event.reason)));
    const tick = () => new Promise(resolve => setTimeout(resolve, 0));
    const wait = async predicate => { for (let i = 0; i < 120; i++) { if (predicate()) return; await tick(); } throw Error('Fixture did not settle'); };
    const get = id => document.getElementById(id), wrapper = source => source?.closest('.employee-picker');
    const trigger = id => wrapper(get(id))?.querySelector('.employee-picker__trigger');
    const originalOwner = data.action === 'owner-loaded' || ['owner-clear', 'owner-missing', 'owner-directory-failure', 'owner-focus'].includes(data.action) ? data.target : 'reader';
    const incident = { id: 731, incident_key: 'INC-731', title: 'Fixture incident', summary: 'Fixture summary', description: 'Manual description', owner: originalOwner, status: 'open', severity: 'high', events: [], routes: [],
      watchers: [{ watcher_identity: data.target, added_at: '2026-10-27T07:00:00Z' }, ...(data.target !== 'reader' ? [{ watcher_identity: 'reader' }] : [])] };
    const other = { ...incident, id: 732, incident_key: 'INC-732', owner: 'other', watchers: [], routes: [] };
    const directory = [...new Set(['reader', data.target, 'bob', 'member;one'])].map(value => ({ value, label: 'Account · ' + value }));
    const response = (body, status = 200) => ({ ok: status < 400, status, json: async () => JSON.parse(JSON.stringify(body)) });
    window.fetch = async (url, options = {}) => {
      const method = options.method || 'GET'; qa.requests.push({ url, method });
      if (url === '/api/panel-users/options') return data.action === 'owner-directory-failure' ? response({}, 503)
        : response(data.action === 'owner-missing' ? directory.filter(option => option.value !== data.target) : directory);
      if (method === 'GET') {
        if (url === '/api/incidents/ops-summary' || url === '/api/analytics/integration-transport') return response({});
        if (url.startsWith('/api/incidents?')) return response({ items: [incident, other] });
        if (url === '/api/incidents/731') return response({ incident });
        if (url === '/api/incidents/732') return response({ incident: other });
      }
      check(['PATCH', 'POST', 'DELETE'].includes(method) && url.startsWith('/api/incidents/731'), 'Unexpected fixture request ' + method + ' ' + url);
      equal(options.credentials, 'same-origin', 'Same origin credentials'); equal(options.headers['X-CSRF-TOKEN'], 'fixture-csrf', 'CSRF retained');
      const body = options.body ? JSON.parse(options.body) : null; qa.writes.push({ url, method, body });
      if (qa.deny) { qa.deny = false; return response({ message: 'Fixture eligibility denied' }, 403); }
      if (url === '/api/incidents/731' && method === 'PATCH') Object.assign(incident, body, { owner: body.owner?.toLowerCase() || '' });
      else if (url === '/api/incidents/731/watchers' && method === 'POST') {
        const identity = body.watcher_identity.toLowerCase(); if (!incident.watchers.some(item => item.watcher_identity === identity)) incident.watchers.push({ watcher_identity: identity });
      } else if (url.startsWith('/api/incidents/731/watchers/') && method === 'DELETE') {
        const identity = decodeURIComponent(url.slice('/api/incidents/731/watchers/'.length)); incident.watchers = incident.watchers.filter(item => item.watcher_identity !== identity);
      } else if (url === '/api/incidents/731/routes' && method === 'POST') incident.routes.push({ ...body, id: 42, route_status: 'pending' });
      else check(false, 'Unexpected fixture mutation ' + method + ' ' + url);
      return response({ incident });
    };
    window.bootstrap = { Modal: class Modal { constructor(node) { this.node = node; } show() {} hide() {} static getOrCreateInstance(node) { return new this(node); } } };
    for (const script of data.scripts) (0, eval)(script.source);
    const detail = get('incidentWorkbenchDetail');
    const ready = async () => { await wait(() => get('incidentDetailOwner') && detail.getAttribute('aria-busy') === 'false' && trigger('incidentDetailOwner') && get('incidentRouteUserPicker')); await tick(); };
    await ready();
    const open = async id => { trigger(id).click(); await tick(); };
    const option = (id, value) => Array.from(wrapper(get(id)).querySelectorAll('[data-employee-value]')).find(button => button.dataset.employeeValue === value);
    const choose = async (id, value) => { await open(id); check(option(id, value), 'Exact directory option exists'); option(id, value).click(); await tick(); };
    const change = (id, value) => { get(id).value = value; get(id).dispatchEvent(new Event('change', { bubbles: true })); };
    const action = async (idOrNode, reload = true) => {
      const button = typeof idOrNode === 'string' ? get(idOrNode) : idOrNode; button.click();
      await wait(() => !button.isConnected || button.dataset.actionPending !== 'true'); await tick(); if (reload) await ready();
    };
    const save = () => action('incidentWorkbenchSaveIncident');
    const saved = () => qa.writes.at(-1);
    const routeType = value => change('incidentRouteType', value);
    const addWatcher = async () => { await choose('incidentWatcherInput', data.target); await action('incidentAddWatcherButton'); };
    const userRoute = async () => { routeType('user'); await choose('incidentRouteUserPicker', data.target); await action('incidentAddRouteButton'); };
    const exactOwner = value => {
      equal(saved().method, 'PATCH', 'Owner PATCH'); equal(saved().body.owner, value, 'PATCH exact owner key');
      equal(saved().body.title, 'Fixture incident', 'Other fields retained');
    };
    if (data.action === 'owner-loaded') {
      equal(get('incidentDetailOwner').value, data.target, 'Loaded owner exact key'); await save(); exactOwner(data.target);
      equal(get('incidentDetailOwner').value, data.target.toLowerCase(), 'Reloaded owner follows backend case folding only');
    } else if (data.action === 'owner-picked') {
      await choose('incidentDetailOwner', data.target); equal(get('incidentDetailOwner').value, data.target, 'Picker keeps exact scalar'); await save(); exactOwner(data.target);
    } else if (data.action === 'watcher-add') {
      await addWatcher(); equal(saved().body, { watcher_identity: data.target }, 'POST exact watcher');
      check(incident.watchers.some(item => item.watcher_identity === data.target.toLowerCase()), 'Fixture persists exact watcher with existing case folding');
    } else if (data.action === 'watcher-remove' || data.action === 'watcher-remove-focus') {
      const button = detail.querySelector('[data-incident-remove-watcher]'); button.focus(); await action(button);
      equal(saved(), { url: '/api/incidents/731/watchers/' + encodeURIComponent(data.target), method: 'DELETE', body: null }, 'DELETE encodes exact watcher once');
      check(!incident.watchers.some(item => item.watcher_identity === data.target), 'Only exact watcher removed');
      if (data.target !== 'reader') check(incident.watchers.some(item => item.watcher_identity === 'reader'), 'Plain watcher is not removed');
      if (data.action.endsWith('focus')) { await new Promise(resolve => requestAnimationFrame(resolve)); check(document.activeElement === trigger('incidentWatcherInput'), 'Removal returns focus to usable watcher picker'); }
    } else if (data.action === 'user-route') {
      await userRoute(); equal(saved().body, { route_type: 'user', route_target: data.target, note: '' }, 'POST exact singular user route');
      equal(incident.routes[0].route_target, data.target, 'Fixture route persists exact target');
    } else if (data.action === 'owner-clear') {
      await open('incidentDetailOwner'); Array.from(wrapper(get('incidentDetailOwner')).querySelectorAll('button')).find(button => button.textContent === 'Очистить выбор').click();
      await save(); exactOwner(''); equal(get('incidentDetailOwner').value, '', 'Clear never falls back to previous owner');
    } else if (data.action === 'owner-blank') {
      change('incidentDetailOwner', ' \t '); await save(); exactOwner('');
    } else if (['owner-missing', 'owner-directory-failure'].includes(data.action)) {
      await open('incidentDetailOwner'); check(!option('incidentDetailOwner', data.target), 'Missing exact account is not fabricated');
      if (data.action === 'owner-directory-failure') check(wrapper(get('incidentDetailOwner')).querySelector('[role=status]').textContent.includes('Не удалось'), 'Catalog failure visible');
      equal(get('incidentDetailOwner').value, data.target, 'Catalog failure/missing key keeps original owner'); await save(); exactOwner(data.target);
    } else if (data.action === 'owner-retry') {
      await choose('incidentDetailOwner', data.target); await save(); exactOwner(data.target); equal(get('incidentDetailOwner').value, data.target, 'Rejected PATCH keeps exact draft');
      check(get('incidentWorkbenchError').textContent.includes('eligibility denied'), 'Backend error shown'); await save(); exactOwner(data.target); equal(qa.writes.length, 2, 'Owner retry is explicit');
    } else if (data.action.startsWith('owner-draft-') || data.action === 'owner-switch') {
      await choose('incidentDetailOwner', data.target); change('incidentDetailDescription', 'Unsaved owner draft');
      if (data.action === 'owner-draft-watcher') await addWatcher();
      else if (data.action === 'owner-draft-route') await userRoute();
      else {
        document.querySelector('[data-incident-id="732"] .incident-list-row-button').click(); await wait(() => get('incidentDetailOwner')?.value === 'other');
        document.querySelector('[data-incident-id="731"] .incident-list-row-button').click(); await ready();
      }
      equal(get('incidentDetailOwner').value, data.target, 'Refresh/switch preserves exact owner draft'); equal(get('incidentDetailDescription').value, 'Unsaved owner draft', 'Other draft fields retained');
      equal(trigger('incidentDetailOwner').textContent, data.target, 'Picker displays restored owner draft');
      await save(); equal(saved().body.owner, data.target, 'Restored draft submits exact owner'); equal(saved().body.description, 'Unsaved owner draft', 'Restored draft description submits');
    } else if (data.action === 'owner-focus') {
      trigger('incidentDetailOwner').focus(); await action('incidentWorkbenchRefresh'); await new Promise(resolve => requestAnimationFrame(resolve));
      check(document.activeElement === trigger('incidentDetailOwner'), 'Refresh restores focus to owner picker'); equal(get('incidentDetailOwner').value, data.target, 'Focused owner remains exact');
    } else if (data.action === 'watcher-retry') {
      await addWatcher(); equal(saved().body.watcher_identity, data.target, 'Rejected watcher POST remains exact'); equal(get('incidentWatcherInput').value, data.target, 'Rejected watcher keeps selection');
      await action('incidentAddWatcherButton'); equal(saved().body.watcher_identity, data.target, 'Retry exact watcher'); equal(qa.writes.length, 2, 'Watcher retry explicit');
    } else if (data.action === 'watcher-keyboard') {
      await open('incidentWatcherInput'); const search = wrapper(get('incidentWatcherInput')).querySelector('input[type=search]');
      search.value = data.target; search.dispatchEvent(new Event('input', { bubbles: true })); search.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
      equal(qa.writes, [], 'Picker Enter does not POST'); equal(get('incidentWatcherInput').value, data.target, 'Keyboard selection exact'); await action('incidentAddWatcherButton'); equal(saved().body.watcher_identity, data.target, 'Keyboard watcher POST exact');
    } else if (data.action === 'watcher-blank') {
      get('incidentWatcherInput').value = ' \t '; await action('incidentAddWatcherButton'); equal(qa.writes, [], 'Blank watcher does not POST');
    } else if (data.action === 'user-retry') {
      await userRoute(); equal(saved().body.route_target, data.target, 'Rejected user target exact'); equal(get('incidentRouteTarget').value, data.target, 'Route authority retains selection');
      equal(get('incidentRouteUserPicker').value, data.target, 'Visible route picker retains selection'); await action('incidentAddRouteButton'); equal(saved().body.route_target, data.target, 'Retry user target exact'); equal(qa.writes.length, 2, 'Route retry explicit');
    } else if (data.action === 'user-blank') {
      routeType('user'); await choose('incidentRouteUserPicker', data.target); await open('incidentRouteUserPicker');
      Array.from(wrapper(get('incidentRouteUserPicker')).querySelectorAll('button')).find(button => button.textContent === 'Очистить выбор').click();
      await action('incidentAddRouteButton'); equal(qa.writes, [], 'Cleared user target does not fall back to old choice'); check(!get('incidentWorkbenchError').classList.contains('d-none'), 'Missing route target error visible');
    } else if (data.action === 'user-switch') {
      routeType('user'); await choose('incidentRouteUserPicker', data.target); routeType('webhook');
      equal(get('incidentRouteTarget').value, '', 'Existing type switch clears target'); check(!get('incidentRouteTarget').hidden, 'Webhook uses text target');
      get('incidentRouteTarget').value = 'https://example.test/hook'; routeType('user'); equal(get('incidentRouteTarget').value, '', 'Switch back does not restore stale target'); equal(get('incidentRouteUserPicker').value, '', 'Switch clears person picker');
      await action('incidentAddRouteButton'); equal(qa.writes, [], 'Switch does not send stale URL as username');
    } else if (data.action === 'users-csv') {
      routeType('users'); await choose('incidentRouteUsersPicker', 'reader'); option('incidentRouteUsersPicker', 'bob').click(); await action('incidentAddRouteButton');
      equal(saved().body, { route_type: 'users', route_target: 'reader, bob', note: '' }, 'Legacy users route retains CSV shape');
    } else if (['webhook', 'department', 'all_operators'].includes(data.action)) {
      routeType(data.action); const target = data.action === 'webhook' ? ' https://example.test/hook?x=1&y=2 ' : data.action === 'department' ? ' Support ' : '';
      get('incidentRouteTarget').value = target; get('incidentRouteNote').value = ' note '; await action('incidentAddRouteButton');
      equal(saved().body, { route_type: data.action, route_target: target.trim(), note: 'note' }, 'Non-user route keeps old normalization');
      equal(qa.requests.filter(request => request.url === '/api/panel-users/options').length, 0, 'Non-user route does not request employee directory');
    } else if (data.action === 'scoped-catalog') {
      const select = document.createElement('select'); select.id = 'assignmentTarget'; select.innerHTML = '<option value="">Choose</option><option value="9007199254740993">Scoped operator</option>'; detail.append(select); window.EmployeePicker.refresh();
      await choose('assignmentTarget', '9007199254740993'); equal(select.value, '9007199254740993', 'Native scoped ID remains string'); equal(qa.requests.filter(request => request.url === '/api/panel-users/options').length, 0, 'Native scope never expands via global catalog');
    } else check(false, 'Unknown action ' + data.action);
    equal(qa.errors, [], 'No browser runtime errors'); check(!window.__injected && !document.querySelector('.employee-picker img'), 'Account labels remain inert text');
  }
}

async function main() {
  const executable = process.env.BROWSER_BINARY || ['C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    'C:/Program Files/Google/Chrome/Application/chrome.exe', '/usr/bin/chromium', '/usr/bin/google-chrome'].find(file => fs.existsSync(file));
  assert(executable, 'Set BROWSER_BINARY to a Chromium/Edge executable');
  fs.mkdirSync(path.dirname(reportFile), { recursive: true });
  const profile = path.join(path.dirname(reportFile), 'identity-browser-' + crypto.randomUUID()); fs.mkdirSync(profile);
  const log = fs.openSync(profile + '.log', 'wx');
  const child = cp.spawn(executable, ['--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
    '--disable-extensions', '--disable-background-networking', '--remote-debugging-port=0', '--remote-debugging-address=127.0.0.1', '--user-data-dir=' + profile, 'about:blank'],
  { windowsHide: true, stdio: ['ignore', log, log] });
  let peer;
  try {
    const activePort = path.join(profile, 'DevToolsActivePort'), deadline = Date.now() + 20000;
    while (!fs.existsSync(activePort)) { assert(child.exitCode === null, 'Browser exited during startup'); assert(Date.now() < deadline, 'Browser startup timeout'); await new Promise(resolve => setTimeout(resolve, 100)); }
    const [port, endpoint] = fs.readFileSync(activePort, 'utf8').trim().split(/\r?\n/);
    const ws = new WebSocket('ws://127.0.0.1:' + port + endpoint); await new Promise((resolve, reject) => { ws.addEventListener('open', resolve, { once: true }); ws.addEventListener('error', reject, { once: true }); }); peer = new Cdp(ws);
    const browser = await peer.send('Browser.getVersion');
    const { targetId } = await peer.send('Target.createTarget', { url: 'about:blank' });
    const { sessionId } = await peer.send('Target.attachToTarget', { targetId, flatten: true });
    await peer.send('Runtime.enable', {}, sessionId); await peer.send('Network.enable', {}, sessionId);
    await peer.send('Network.setBlockedURLs', { urls: ['*'] }, sessionId);
    const result = await peer.send('Runtime.evaluate', { expression: '(' + browserSuite.toString() + ')(' + JSON.stringify(payload) + ')', awaitPromise: true, returnByValue: true }, sessionId);
    assert(!result.exceptionDetails, JSON.stringify(result.exceptionDetails)); const cases = result.result.value;
    const report = { browser: browser.product, tests: cases.length, failures: cases.filter(test => !test.ok && test.kind === 'failure').length,
      errors: cases.filter(test => !test.ok && test.kind === 'error').length, sources, cases, finished: new Date().toISOString() };
    fs.writeFileSync(reportFile, JSON.stringify(report, null, 2)); console.log(JSON.stringify({ tests: report.tests, failures: report.failures, errors: report.errors, report: reportFile }));
    process.exitCode = report.failures || report.errors ? 1 : 0;
  } finally {
    if (peer) { try { await peer.send('Browser.close'); } catch (_) {} peer.ws.close(); }
    if (child.exitCode === null) child.kill(); fs.closeSync(log);
  }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
