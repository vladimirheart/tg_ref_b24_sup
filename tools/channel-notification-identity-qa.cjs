/* Isolated browser DOM integration: real channel fragment/runtimes, fixture-only fetch; Node 22+. */
const fs = require('node:fs'), path = require('node:path'), cp = require('node:child_process');
const crypto = require('node:crypto'), assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..');
const reportFile = path.resolve(process.argv.find(arg => arg.startsWith('--report='))?.slice(9)
  || path.join(root, 'spring-panel/target/channel-notification-identity-r180/report.json'));
const scriptNames = ['settings-channel-config-runtime.js', 'settings-channel-editor-shell-runtime.js',
  'settings-channel-editor-persistence-runtime.js', 'settings-channel-editor-controls-runtime.js',
  'settings-channels-shell-runtime.js', 'employee-picker.js'];
const inputs = [...scriptNames.map(name => 'spring-panel/src/main/resources/static/js/' + name),
  'spring-panel/src/main/resources/templates/settings/fragments/channel-editor.html'];
const sources = inputs.map(file => ({ file, sha256: crypto.createHash('sha256').update(fs.readFileSync(path.join(root, file))).digest('hex') }));
const payload = { scripts: scriptNames.map((name, index) => ({ name, source: fs.readFileSync(path.join(root, inputs[index]), 'utf8') })),
  fragment: fs.readFileSync(path.join(root, inputs.at(-1)), 'utf8') };

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
  async function scenario(name, field, target, action, extra = {}) {
    const frame = document.createElement('iframe'); document.body.append(frame); const win = frame.contentWindow;
    const routing = { department: ' Support ', targetMode: field === 'employeeUsernames' ? 'employees_only' : 'department_except',
      deliveryMode: 'all', employeeUsernames: [], excludeUsernames: [], [field]: [target.toUpperCase()] };
    const data = { ...input, field, target, action, config: { panelNotifications: { routing } },
      directory: [...new Set(['reader', target])].map(value => ({ value, label: 'Account · ' + value })), ...extra };
    try {
      win.document.open(); win.document.write('<!doctype html><html><head><meta charset="UTF-8"></head><body>' + input.fragment + '</body></html>'); win.document.close();
      await win.eval('(' + exercise.toString() + ')(' + JSON.stringify(data) + ')'); cases.push({ name, ok: true });
    } catch (error) { cases.push({ name, ok: false, kind: error.name === 'AssertionError' ? 'failure' : 'error', message: error.message }); }
    finally { frame.remove(); }
    console.log('identity-case', JSON.stringify(cases.at(-1)));
  }
  for (const field of ['employeeUsernames', 'excludeUsernames']) {
    for (const target of variants) await scenario(field + ' round trip ' + JSON.stringify(target), field, target, 'roundtrip');
    for (const target of variants.slice(1, 6)) await scenario(field + ' missing literal ' + JSON.stringify(target), field, target, 'missing',
      { directory: [{ value: 'reader', label: 'Plain account' }] });
    for (const action of ['clear', 'reset', 'disabled', 'absent', 'directory-failure'])
      await scenario(field + ' ' + action, field, action === 'clear' || action === 'reset' ? 'reader' : ' reader ', action);
    for (const invalid of ['{', '{}', '[1]', '["reader",null]'])
      await scenario(field + ' malformed ' + invalid, field, 'reader', 'invalid', { invalid });
    await scenario(field + ' mixed-case directory', field, 'reader', 'roundtrip', { directory: [{ value: 'READER', label: 'Reader' }] });
  }
  await scenario('typed normalization and compatibility shapes', 'employeeUsernames', 'reader', 'typed');
  await scenario('legacy picker and channel CSV unchanged', 'employeeUsernames', 'reader', 'legacy');
  return cases;

  async function exercise(data) {
    const check = (condition, message) => { if (!condition) { const error = Error(message); error.name = 'AssertionError'; throw error; } };
    const equal = (actual, expected, label) => check(JSON.stringify(actual) === JSON.stringify(expected), label + ': ' + JSON.stringify({ actual, expected }));
    const qa = { writes: [], requests: [], messages: [], mounts: {}, errors: [] }; window.__identityQa = qa;
    window.addEventListener('error', event => qa.errors.push(event.message));
    window.fetch = async (url, options) => {
      qa.requests.push(url);
      if (url === '/api/panel-users/options') return { ok: data.action !== 'directory-failure', status: data.action === 'directory-failure' ? 503 : 200, json: async () => data.directory };
      check(url === '/api/channels/7' && ['POST', 'PUT'].includes(options?.method), 'Unexpected request ' + url);
      const body = JSON.parse(options.body); qa.writes.push(body); qa.channel.questions_cfg = body.questions_cfg;
      return { ok: true, status: 200, json: async () => ({ success: true }) };
    };
    const id = data.field === 'employeeUsernames' ? 'channelEditorPanelNotifEmployees' : 'channelEditorPanelNotifExclude';
    if (data.action === 'absent') document.getElementById(id).remove();
        const noop = () => {}, stub = methods => new Proxy(methods, { get: (object, key) => key in object ? object[key] : noop });
    window.SettingsRuntimeAccess = {
      fetchPageDataSection: async () => ({ assignmentRoutingCatalog: data.catalog }),
      mountRuntime(name, options = {}) {
        qa.mounts[name] = options;
        if (window[name]) {
          const runtime = window[name].mount ? window[name].mount(options) : window[name].createRuntime(options);
          if (name === 'SettingsChannelConfigRuntime') qa.config = runtime;
          return runtime;
        }
        if (name === 'SettingsChannelTemplatesRuntime') return stub({ getQuestionTemplates: () => [], getRatingTemplates: () => [],
          getAutoActionTemplates: () => [], getTemplateSummaryBuilders: () => ({}) });
        if (name === 'SettingsIntegrationNetworkRuntime') return stub({ normalizeChannelWorkingHours: value => value || { start_hour: 9, end_hour: 18 },
          collectRouteFromInputs: () => ({ mode: 'direct' }) });
        if (name === 'SettingsChannelsCatalogRuntime') return stub({ loadChannels: () => {
          qa.channel ||= { id: 7, channel_name: 'Identity fixture', is_active: true, platform: 'web',
            delivery_settings: {}, questions_cfg: qa.config.parseQuestionsCfg(data.config) };
          options.getChannelsRegistry().set('7', qa.channel); options.getChannelEditorState().channelId = 7;
        } });
        return stub({});
      },
    };
    for (const script of data.scripts.filter(script => script.name !== 'employee-picker.js')) (0, eval)(script.source);
    window.SettingsChannelsShellRuntime.mount({ showPopup: (...message) => qa.messages.push(message) }).initChannelsManagement();
    await Promise.resolve(); await Promise.resolve();

    const config = qa.config, editor = window.__settingsChannelEditorShellRuntime;
    const render = () => editor.populateChannelEditorShell({ ...qa.channel, questions_cfg: config.parseQuestionsCfg(qa.channel.questions_cfg) });
    render(); (0, eval)(data.scripts.find(script => script.name === 'employee-picker.js').source); window.EmployeePicker.refresh();
    const source = document.getElementById(id), expected = [data.target.toLowerCase()];
    const selected = () => {
      try { const value = JSON.parse(source.value); if (Array.isArray(value)) return value; } catch (_) {}
      return source.value.split(/[,;\n]+/).map(value => value.trim()).filter(Boolean);
    };
    const tick = () => new Promise(resolve => setTimeout(resolve, 0));
    const save = async () => { document.getElementById('channelEditorSaveBtn').click(); await tick(); };
    const picker = () => source.closest('.employee-picker');
    const open = async () => { picker().querySelector('.employee-picker__trigger').click(); await tick(); };
    const saved = () => qa.writes.at(-1).questions_cfg.panelNotifications.routing[data.field];
    if (data.action === 'typed') {
      const values = [' READER ', ' reader ', 'READER', 'reader', '', ' \t', 'member,one'];
      equal(config.serializeChannelNotificationList(values), [' reader ', 'reader', 'member,one'], 'Typed keys and case dedup');
      for (const raw of [{ panelNotifications: { routing: { employeeUsernames: values } } }, { panelNotifications: { employeeUsernames: values } }, { alertQueue: { employeeUsernames: values } }])
        equal(config.parseQuestionsCfg(raw).panelNotifications.routing.employeeUsernames, [' reader ', ' reader ', 'reader', 'reader', 'member,one'], 'Compatibility shape keeps typed identity');
    } else if (data.action === 'legacy') {
      equal(config.parseChannelNotificationList(' ALICE, Bob; CAROL\tDAVE '), ['alice', 'bob', 'carol', 'dave'], 'Legacy channel CSV grammar');
      const legacy = document.createElement('textarea'); legacy.dataset.employeePicker = 'true'; legacy.dataset.employeeMultiple = 'true';
      legacy.value = 'Alice; Bob\nCarol'; document.body.append(legacy); window.EmployeePicker.refresh();
      const wrapper = legacy.closest('.employee-picker'); wrapper.querySelector('.employee-picker__trigger').click(); await tick();
      Array.from(wrapper.querySelectorAll('[data-employee-value]')).find(button => button.dataset.employeeValue === 'reader').click();
      equal(legacy.value, 'Alice, Bob, Carol, reader', 'Other legacy fields retain CSV value');
    } else if (data.action === 'invalid') {
      source.dataset.employeeValueFormat = 'username-json'; source.value = data.invalid; await save();
      equal(qa.writes, [], 'Invalid typed value must not produce POST'); check(qa.messages.some(message => message[1] === 'error'), 'Invalid state must show an error');
    } else if (data.action === 'absent') {
      await save(); equal(qa.writes.length, 1, 'Absent optional control should still save'); equal(saved(), expected, 'Absent control retains typed configuration');
    } else {
      equal(selected(), expected, 'Restore exact identity');
      if (data.action === 'reset') {
        const wrapper = picker(), form = document.createElement('form'); wrapper.before(form); form.append(wrapper);
        source.value = ''; form.reset(); await tick(); equal(selected(), expected, 'Reset restores configured selection');
      } else if (data.action === 'disabled') {
        const mode = document.getElementById('channelEditorPanelNotifTargetMode'); mode.value = 'all_operators'; mode.dispatchEvent(new Event('change', { bubbles: true }));
        window.EmployeePicker.refresh(); check(source.disabled, 'Inactive target field remains disabled'); await save(); equal(saved(), expected, 'Inactive selection is retained');
      } else {
        await open(); const wrapper = picker(), find = () => Array.from(wrapper.querySelectorAll('[data-employee-value]')).find(button => button.dataset.employeeValue === data.target.toLowerCase());
        if (data.action === 'directory-failure') {
          check(wrapper.querySelector('[role=status]').textContent.includes('Не удалось'), 'Directory error is visible');
          equal(selected(), expected, 'Directory failure must not discard selection');
        } else if (data.action === 'missing') {
          check(!find(), 'Missing literal must not be fabricated in directory'); equal(selected(), expected, 'Plain account must not replace missing literal');
        } else if (data.action === 'clear') {
          Array.from(wrapper.querySelectorAll('button')).find(button => button.textContent === 'Очистить выбор').click(); equal(selected(), [], 'Clear removes selected values');
        } else {
          check(find(), 'Exact option must be offered'); find().click(); equal(selected(), [], 'Toggle removes exact key'); find().click(); equal(selected(), expected, 'Toggle restores exact key');
          const search = wrapper.querySelector('input[type=search]');
          if (data.target === 'reader') {
            search.value = 'no-such-account'; search.dispatchEvent(new Event('input', { bubbles: true }));
            const accepted = search.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
            check(!accepted, 'Search Enter prevents form submission'); equal(qa.writes, [], 'Search does not save'); search.value = ''; search.dispatchEvent(new Event('input', { bubbles: true }));
          }
          search.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }));
          equal(wrapper.querySelector('.employee-picker__trigger').getAttribute('aria-expanded'), 'false', 'Escape closes picker');
          check(document.activeElement === wrapper.querySelector('.employee-picker__trigger'), 'Escape restores focus');
        }
        await save(); equal(qa.writes.length, 1, 'Exactly one save expected'); equal(saved(), data.action === 'clear' ? [] : expected, 'Captured POST preserves selection');
        render(); window.EmployeePicker.refresh(); equal(selected(), data.action === 'clear' ? [] : expected, 'Reload preserves selection');
      }
    }
    equal(qa.errors, [], 'No browser runtime errors'); check(!window.__injected, 'Labels must not execute markup');
    check(qa.requests.every(url => ['/api/channels/7', '/api/panel-users/options'].includes(url)), 'No unrelated endpoint');
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
