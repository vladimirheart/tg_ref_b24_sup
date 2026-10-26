/* Isolated browser DOM integration: real channel fragment/runtimes, fixture-only fetch; Node 22+. */
const fs = require('node:fs'), path = require('node:path'), cp = require('node:child_process');
const crypto = require('node:crypto'), assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..');
const reportFile = path.resolve(process.argv.find(arg => arg.startsWith('--report='))?.slice(9)
  || path.join(root, 'spring-panel/target/channel-assignment-identity-r178/report.json'));
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
  const cases = [], variants = [' reader', 'reader ', ' reader ', '\treader\t', '\nreader\r'];
  async function scenario(name, target, mode, action, catalogNames = ['reader', target]) {
    const frame = document.createElement('iframe'); document.body.append(frame);
    const win = frame.contentWindow;
    const config = { assignmentRouting: { enabled: true, mode, strategy: 'round_robin', assignResponsibleOnCreate: true,
      operatorUsername: target.toUpperCase(), operatorUsernames: [target.toUpperCase()], department: ' Support ' } };
    const data = { ...input, config, catalog: { operators: [...new Set(catalogNames)].map(username => ({ username,
      display_name: ' Account ', department: ' Support ', role: ' Operator ' })), departments: [' Support '] }, action, target, mode };
    try {
      win.document.open(); win.document.write('<!doctype html><html><head><meta charset="UTF-8"></head><body>' + input.fragment + '</body></html>'); win.document.close();
      const value = await win.eval('(' + exercise.toString() + ')(' + JSON.stringify(data) + ')');
      cases.push({ name, ok: true, value });
    } catch (error) { cases.push({ name, ok: false, kind: error.name === 'AssertionError' ? 'failure' : 'error', message: error.message }); }
    finally { frame.remove(); }
    console.log('identity-case', JSON.stringify({ name, ok: cases.at(-1).ok, message: cases.at(-1).message }));
  }
  for (const target of variants) {
    await scenario('single round trip ' + JSON.stringify(target), target, 'single_operator', 'roundtrip');
    await scenario('pool round trip ' + JSON.stringify(target), target, 'operator_pool', 'roundtrip');
    await scenario('missing single denies alias ' + JSON.stringify(target), target, 'single_operator', 'missing', ['reader']);
    await scenario('missing pool denies alias ' + JSON.stringify(target), target, 'operator_pool', 'missing', ['reader']);
  }
  for (const target of ['reader', 'member,one', 'member;one', '9007199254740993', '<img src=x onerror="window.__injected=1">']) {
    await scenario('picker single ' + JSON.stringify(target), target, 'single_operator', 'picker');
    await scenario('picker pool ' + JSON.stringify(target), target, 'operator_pool', 'picker');
  }
  await scenario('native single reset restores configured selection', 'reader', 'single_operator', 'reset');
  await scenario('native pool reset restores configured selection', 'reader', 'operator_pool', 'reset');
  await scenario('clear single does not restore stale key', 'reader', 'single_operator', 'clear');
  await scenario('typed pool keeps distinct keys and case dedup', 'reader', 'operator_pool', 'typed');
  await scenario('legacy CSV and labels keep their normalization', 'reader', 'single_operator', 'legacy');
  await scenario('disabled mode keeps assignment disabled', 'reader', 'disabled', 'disabled');
  return cases;

  async function exercise(data) {
    const check = (condition, message) => { if (!condition) { const error = Error(message); error.name = 'AssertionError'; throw error; } };
    const equal = (actual, expected, label) => check(JSON.stringify(actual) === JSON.stringify(expected), label + ': ' + JSON.stringify({ actual, expected }));
    const qa = { writes: [], requests: [], messages: [], mounts: {}, errors: [] }; window.__identityQa = qa;
    window.addEventListener('error', event => qa.errors.push(event.message));
    window.fetch = async (url, options) => {
      qa.requests.push(url);
      check(url === '/api/channels/7' && ['POST', 'PUT'].includes(options?.method), 'Unexpected network request: ' + url);
      const body = JSON.parse(options.body); qa.writes.push(body); qa.channel.questions_cfg = body.questions_cfg;
      return { ok: true, status: 200, json: async () => ({ success: true }) };
    };
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
    render();
    (0, eval)(data.scripts.find(script => script.name === 'employee-picker.js').source); window.EmployeePicker.refresh();
    const single = document.getElementById('channelEditorAssignmentSingleOperator');
    const pool = document.getElementById('channelEditorAssignmentPoolOperators');
    const source = data.mode === 'operator_pool' ? pool : single;
    const selected = () => data.mode === 'operator_pool' ? Array.from(pool.selectedOptions, option => option.value) : single.value;
    const expected = () => data.mode === 'operator_pool' ? [data.target.toLowerCase()] : data.target.toLowerCase();
    const save = async () => { document.getElementById('channelEditorSaveBtn').click(); await new Promise(resolve => setTimeout(resolve, 0)); };
    if (data.action === 'missing') {
      equal(selected(), data.mode === 'operator_pool' ? [] : '', 'Missing key cannot select the trimmed account');
      await save(); equal(qa.writes, [], 'Invalid assignment must not be sent'); check(qa.messages.some(message => message[1] === 'error'), 'Missing validation error');
    } else if (data.action === 'reset') {
      const wrapper = source.closest('.employee-picker'), form = document.createElement('form'); wrapper.before(form); form.append(wrapper);
      Array.from(source.options).forEach(option => { option.selected = false; }); source.selectedIndex = -1;
      form.reset(); await new Promise(resolve => setTimeout(resolve, 0)); equal(selected(), expected(), 'Native form reset restores the configured key');
    } else if (data.action === 'clear') {
      const picker = single.closest('.employee-picker'); picker.querySelector('.employee-picker__trigger').click();
      Array.from(picker.querySelectorAll('button')).find(button => button.textContent === 'Очистить выбор').click();
      const payload = qa.mounts.SettingsChannelEditorPersistenceRuntime.buildQuestionsCfgPayload(config.parseQuestionsCfg(data.config));
      equal(payload.assignmentRouting.operatorUsername, '', 'Explicit empty selection is authoritative'); await save(); equal(qa.writes, [], 'Empty required single must not be sent');
    } else if (data.action === 'typed') {
      const parsed = config.parseQuestionsCfg({ assignmentRouting: { ...data.config.assignmentRouting,
        operatorUsernames: [' READER ', ' reader ', 'READER', 'reader', '', ' \t'] } });
      equal(parsed.assignmentRouting.operatorUsernames, [' reader ', 'reader'], 'Typed pool preserves exact identity and deduplicates case');
    } else if (data.action === 'legacy') {
      equal(config.parseChannelNotificationList(' ALICE, Bob; CAROL\tDAVE '), ['alice', 'bob', 'carol', 'dave'], 'Legacy CSV unchanged');
      equal(config.parseQuestionsCfg({ assignmentRouting: { ...data.config.assignmentRouting, operatorUsernames: ' ALICE, Bob; CAROL ' } }).assignmentRouting.operatorUsernames,
        ['alice', 'bob', 'carol'], 'Legacy string pool unchanged');
      equal(qa.mounts.SettingsChannelEditorShellRuntime.getChannelAssignmentRoutingCatalog().departments, ['Support'], 'Department labels still trim');
    } else if (data.action === 'disabled') {
      await save(); check(qa.writes.length === 1, 'Disabled configuration should save');
      equal(qa.writes[0].questions_cfg.assignmentRouting, { enabled: false, mode: 'disabled', assignResponsibleOnCreate: false,
        strategy: 'round_robin', operatorUsername: '', operatorUsernames: [], department: '' }, 'Disabled assignment unchanged');
    } else {
      equal(selected(), expected(), 'Restored selection keeps literal key');
      if (data.action === 'picker' || data.action === 'roundtrip') {
        const picker = source.closest('.employee-picker'); check(picker, 'Picker should mount');
        const trigger = picker.querySelector('.employee-picker__trigger'); trigger.click(); await Promise.resolve();
        const button = Array.from(picker.querySelectorAll('[data-employee-value]')).find(item => item.dataset.employeeValue === data.target.toLowerCase());
        check(button, 'Exact key must be offered by native picker');
        if (source.multiple) {
          button.click(); equal(selected(), [], 'Pool toggle removes only exact key');
          Array.from(picker.querySelectorAll('[data-employee-value]')).find(item => item.dataset.employeeValue === data.target.toLowerCase()).click();
        }
        else button.click();
        equal(selected(), expected(), 'Picker preserves native selected value');
        if (trigger.getAttribute('aria-expanded') !== 'true') trigger.click();
        const search = picker.querySelector('input[type=search]');
        search.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }));
        equal(trigger.getAttribute('aria-expanded'), 'false', 'Escape closes picker');
        check(document.activeElement === trigger, 'Escape restores focus');
        if (data.target === 'reader' && !source.multiple) {
          trigger.click(); search.value = 'reader'; search.dispatchEvent(new Event('input', { bubbles: true }));
          const accepted = search.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
          check(!accepted, 'Enter prevents native form submission'); equal(qa.writes, [], 'Search Enter must not save');
          equal(selected(), expected(), 'Keyboard selection preserves the native key');
        }
      }
      await save(); check(qa.writes.length === 1, 'Exactly one fixture save is expected: ' + JSON.stringify(qa.messages));
      const route = qa.writes[0].questions_cfg.assignmentRouting;
      equal(data.mode === 'operator_pool' ? route.operatorUsernames : route.operatorUsername, expected(), 'Saved JSON keeps exact key');
      render(); window.EmployeePicker.refresh(); equal(selected(), expected(), 'Reloaded selection keeps exact key');
    }
    check(qa.requests.every(url => url === '/api/channels/7'), 'Native picker must not expand its catalog from a global directory');
    equal(qa.errors, [], 'No browser runtime errors'); check(!window.__injected, 'Labels must not execute markup');
    return { writes: qa.writes.length };
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
