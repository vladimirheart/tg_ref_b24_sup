/* Real browser DOM integration: incident create template + workbench + shared picker; fixture-only fetch, all network blocked. Node 22+. */
const fs = require('node:fs'), path = require('node:path'), cp = require('node:child_process');
const crypto = require('node:crypto'), assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..');
const reportFile = path.resolve(process.argv.find(arg => arg.startsWith('--report='))?.slice(9)
  || path.join(root, 'spring-panel/target/incident-create-identity-r185/report.json'));
const inputs = ['spring-panel/src/main/resources/static/js/incidents-workbench.js',
  'spring-panel/src/main/resources/static/js/employee-picker.js',
  'spring-panel/src/main/resources/templates/incidents/index.html'];
const sources = inputs.map(file => ({ file, sha256: crypto.createHash('sha256').update(fs.readFileSync(path.join(root, file))).digest('hex') }));
const html = fs.readFileSync(path.join(root, inputs.at(-1)), 'utf8');
const start = html.indexOf('<div class="modal fade" id="incidentCreateModal"'), end = html.indexOf('<div class="modal fade" id="incidentExternalObjectsModal"', start);
assert(start > 0 && end > start, 'Real incident create template boundary');
const payload = { scripts: inputs.slice(0, 2).map(file => ({ name: path.basename(file), source: fs.readFileSync(path.join(root, file), 'utf8') })), fragment: html.slice(start, end) };

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
  const cases = [], variants = ['reader', ' reader', 'reader ', ' reader ', '\treader\t', '\nreader\r',
    'member,one', 'member;one', '9007199254740993', '<img src=x onerror="window.__injected=1">'];
  async function scenario(name, action, extra = {}) {
    const frame = document.createElement('iframe'); document.body.append(frame);
    try {
      const win = frame.contentWindow;
      win.document.open(); win.document.write('<!doctype html><html><head><meta charset="UTF-8"><meta name="_csrf" content="fixture-csrf"></head><body>' +
        '<table><tbody id="incidentWorkbenchList"></tbody></table><div id="incidentWorkbenchListMeta"></div>' +
        '<div id="incidentWorkbenchDetail"></div><div id="incidentWorkbenchError" class="d-none"></div><div id="incidentWorkbenchSuccess" hidden></div>' + input.fragment + '</body></html>'); win.document.close();
      await win.eval('(' + exercise.toString() + ')(' + JSON.stringify({ ...input, action, target: ' reader ', ...extra }) + ')');
      cases.push({ name, ok: true });
    } catch (error) { cases.push({ name, ok: false, kind: error.name === 'AssertionError' ? 'failure' : 'error', message: error.message }); }
    finally { frame.remove(); }
    console.log('identity-case', JSON.stringify(cases.at(-1)));
  }
  for (const target of variants) await scenario('create exact owner and watcher ' + JSON.stringify(target), 'roundtrip', { target });
  for (const action of ['distinct', 'clear', 'reset', 'cancel', 'keyboard', 'required', 'retry', 'missing', 'directory-failure', 'disabled', 'empty', 'blank-owner', 'legacy'])
    await scenario(action, action, action === 'keyboard' ? { target: 'member;one' } : {});
  for (const invalid of ['{', '{}', '[1]', '["reader",null]', '[""]', '[" \t"]'])
    await scenario('malformed watchers ' + invalid, 'invalid', { invalid });
  return cases;

  async function exercise(data) {
    const check = (condition, message) => { if (!condition) { const error = Error(message); error.name = 'AssertionError'; throw error; } };
    const equal = (actual, expected, label) => check(JSON.stringify(actual) === JSON.stringify(expected), label + ': ' + JSON.stringify({ actual, expected }));
    const qa = { writes: [], errors: [], requests: [], hidden: 0, failSave: data.action === 'retry' }; window.__identityQa = qa;
    window.addEventListener('error', event => qa.errors.push(event.message));
    window.addEventListener('unhandledrejection', event => qa.errors.push(String(event.reason)));
    const tick = () => new Promise(resolve => setTimeout(resolve, 0));
    const wait = async predicate => { for (let i = 0; i < 60; i++) { if (predicate()) return; await tick(); } throw Error('Fixture did not settle'); };
    const directory = [...new Set(['reader', data.target, 'member,one', 'member;one'])].map(value => ({ value, label: 'Account · ' + value }));
    window.fetch = async (url, options = {}) => {
      qa.requests.push({ url, method: options.method || 'GET' });
      if (url === '/api/panel-users/options') return { ok: data.action !== 'directory-failure', status: 503,
        json: async () => data.action === 'missing' ? [{ value: 'reader', label: 'Plain account' }] : directory };
      if (url === '/api/incidents' && options.method === 'POST') {
        equal(options.credentials, 'same-origin', 'Same origin credentials'); equal(options.headers['X-CSRF-TOKEN'], 'fixture-csrf', 'CSRF retained');
        qa.writes.push(JSON.parse(options.body));
        if (qa.failSave) { qa.failSave = false; return { ok: false, status: 403, json: async () => ({ message: 'Fixture eligibility denied' }) }; }
        return { ok: true, status: 200, json: async () => ({ incident: { id: 7, incident_key: 'INC-7' } }) };
      }
      check(url.startsWith('/api/incidents?') && !options.method, 'Unexpected fixture request ' + url);
      return { ok: true, status: 200, json: async () => ({ items: [] }) };
    };
    window.bootstrap = { Modal: { getOrCreateInstance: () => ({ hide: () => qa.hidden++ }) } };
    for (const script of data.scripts) (0, eval)(script.source);
    window.EmployeePicker.refresh(); await tick();
    const form = document.getElementById('incidentCreateForm'), title = document.getElementById('incidentCreateTitle');
    const owner = document.getElementById('incidentCreateOwner'), watchers = document.getElementById('incidentCreateWatchers');
    // Names are supplied only by the test to check native FormData of the real controls.
    owner.name = 'owner'; watchers.name = 'watchers';
    const key = document.getElementById('incidentCreateRelationKey'), search = document.getElementById('incidentCreateRelationSearch');
    const prepare = () => { title.value = 'Fixture incident'; search.value = 'Ticket'; key.value = ' T/№🙂 '; };
    const wrapper = source => source.closest('.employee-picker');
    const open = async source => { wrapper(source).querySelector('.employee-picker__trigger').click(); await tick(); };
    const option = (source, value) => Array.from(wrapper(source).querySelectorAll('[data-employee-value]')).find(button => button.dataset.employeeValue === value);
    const choose = async (source, value) => { await open(source); check(option(source, value), 'Exact directory option exists'); option(source, value).click(); };
    const setWatchers = values => { watchers.value = watchers.dataset.employeeValueFormat === 'username-json' ? JSON.stringify(values) : values.join(', '); };
    const selectedWatchers = () => {
      try { const values = JSON.parse(watchers.value || '[]'); if (Array.isArray(values)) return values; } catch (_) {}
      return watchers.value.split(/[,;\n]+/).map(value => value.trim()).filter(Boolean);
    };
    const seed = () => { owner.value = data.target; setWatchers([data.target]); window.EmployeePicker.refresh(); };
    const save = async () => { document.getElementById('incidentWorkbenchCreate').click(); await wait(() => document.getElementById('incidentWorkbenchCreate').dataset.actionPending !== 'true'); await tick(); };
    const exactPayload = (body, expectedOwner = data.target, expectedWatchers = [data.target.toLowerCase()]) => {
      equal(body.owner, expectedOwner, 'POST owner exact key'); equal(body.watchers, expectedWatchers, 'POST typed watcher keys');
      equal(body.relations, [{ relation_type: 'ticket', relation_key: ' T/№🙂 ', primary: true }], 'Exact related ticket remains unchanged');
    };
    prepare();
    if (data.action === 'roundtrip' || data.action === 'keyboard') {
      await choose(owner, data.target);
      await open(watchers);
      if (data.action === 'keyboard') {
        const input = wrapper(watchers).querySelector('input[type=search]'); input.value = data.target; input.dispatchEvent(new Event('input', { bubbles: true }));
        input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
      } else { check(option(watchers, data.target), 'Exact watcher option exists'); option(watchers, data.target).click(); }
      equal(owner.value, data.target, 'Native owner source preserves selection'); equal(selectedWatchers(), [data.target.toLowerCase()], 'Native watcher source preserves selection');
      equal(new FormData(form).get('owner'), data.target, 'Native FormData owner');
      const input = wrapper(watchers).querySelector('input[type=search]');
      input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }));
      equal(wrapper(watchers).querySelector('.employee-picker__trigger').getAttribute('aria-expanded'), 'false', 'Escape closes picker');
      check(document.activeElement === wrapper(watchers).querySelector('.employee-picker__trigger'), 'Escape restores focus');
      equal(qa.writes, [], 'Picker search does not submit incident');
      await save(); equal(qa.writes.length, 1, 'Single POST'); exactPayload(qa.writes[0]);
      equal(owner.value, '', 'Successful create resets owner'); equal(selectedWatchers(), [], 'Successful create resets watchers'); equal(qa.hidden, 1, 'Successful create closes modal');
    } else if (data.action === 'invalid') {
      seed(); watchers.value = data.invalid; await save(); equal(qa.writes, [], 'Malformed typed state must block POST'); equal(qa.hidden, 0, 'Invalid draft stays open');
      check(!document.getElementById('incidentWorkbenchError').classList.contains('d-none'), 'Invalid selection error is visible'); equal(owner.value, data.target, 'Invalid draft retains owner'); equal(watchers.value, data.invalid, 'Invalid draft is not silently rewritten');
    } else if (data.action === 'distinct') {
      await choose(owner, data.target); await choose(watchers, 'reader'); option(watchers, data.target).click(); option(watchers, 'member,one').click(); option(watchers, 'member;one').click();
      await save(); exactPayload(qa.writes[0], data.target, ['reader', data.target, 'member,one', 'member;one']);
    } else if (data.action === 'legacy') {
      const legacy = document.createElement('input'); legacy.type = 'text'; legacy.dataset.employeePicker = 'true'; legacy.dataset.employeeMultiple = 'true'; legacy.value = 'Alice; Bob'; form.append(legacy); window.EmployeePicker.refresh();
      await choose(legacy, 'reader'); equal(legacy.value, 'Alice, Bob, reader', 'Other picker fields retain legacy CSV grammar');
      delete watchers.dataset.employeeValueFormat; watchers.value = ' Alice, Bob; Carol '; await save(); exactPayload(qa.writes[0], '', ['Alice', 'Bob', 'Carol']);
    } else {
      if (data.action !== 'empty') seed();
      if (data.action === 'blank-owner') owner.value = ' \t ';
      if (data.action === 'disabled') { owner.disabled = true; watchers.disabled = true; window.EmployeePicker.refresh(); check(wrapper(owner).querySelector('button').disabled, 'Disabled owner picker'); check(wrapper(watchers).querySelector('button').disabled, 'Disabled watchers picker'); }
      if (data.action === 'required') { title.value = ''; await save(); equal(qa.writes, [], 'Required title prevents POST'); equal(owner.value, data.target, 'Required failure keeps draft'); prepare(); }
      if (data.action === 'cancel') {
        document.getElementById('incidentCreateModal').dispatchEvent(new Event('hidden.bs.modal')); document.getElementById('incidentCreateModal').dispatchEvent(new Event('shown.bs.modal'));
        equal(owner.value, data.target, 'Cancel/reopen keeps owner draft'); equal(selectedWatchers(), [data.target], 'Cancel/reopen keeps watcher draft'); equal(qa.writes, [], 'Cancel does not save');
      }
      if (data.action === 'reset') { form.reset(); await tick(); prepare(); equal(owner.value, '', 'Native reset owner'); equal(selectedWatchers(), [], 'Native reset watchers'); }
      if (data.action === 'clear') {
        for (const source of [owner, watchers]) { await open(source); Array.from(wrapper(source).querySelectorAll('button')).find(button => button.textContent === 'Очистить выбор').click(); }
      }
      if (['missing', 'directory-failure'].includes(data.action)) {
        for (const source of [owner, watchers]) {
          await open(source); check(!option(source, data.target), 'Unavailable exact key is not fabricated');
          if (data.action === 'directory-failure') check(wrapper(source).querySelector('[role=status]').textContent.includes('Не удалось'), 'Directory error visible');
        }
        equal(owner.value, data.target, 'Missing directory must not substitute plain owner'); equal(selectedWatchers(), [data.target], 'Missing directory must not substitute plain watcher');
      }
      if (data.action === 'retry') {
        await save(); equal(qa.writes.length, 1, 'Backend rejection reached once'); exactPayload(qa.writes[0]); equal(qa.hidden, 0, 'Backend rejection keeps modal');
        equal(owner.value, data.target, 'Backend rejection retains owner'); equal(selectedWatchers(), [data.target], 'Backend rejection retains watchers');
        check(document.getElementById('incidentWorkbenchError').textContent.includes('eligibility denied'), 'Backend eligibility error is shown');
      }
      await save(); equal(qa.writes.length, data.action === 'retry' ? 2 : 1, 'Expected number of submissions');
      const cleared = ['clear', 'reset', 'empty'].includes(data.action);
      exactPayload(qa.writes.at(-1), cleared || data.action === 'blank-owner' ? '' : data.target, cleared ? [] : [data.target]);
    }
    equal(qa.errors, [], 'No browser runtime errors'); check(!window.__injected, 'Untrusted labels cannot execute markup');
    check(!document.querySelector('.employee-picker img'), 'Untrusted labels remain text');
    check(qa.requests.every(request => request.url === '/api/panel-users/options' || request.url === '/api/incidents' || request.url.startsWith('/api/incidents?')), 'Only fixture endpoints used');
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
