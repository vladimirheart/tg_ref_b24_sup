'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/settings-channels-bot-runtime.js'), 'utf8');

class Element {
  constructor(label, icon = false) {
    this.innerHTML = label; this.dataset = {}; this.disabled = false; this.hidden = false;
    this.attrs = new Map(); this.classList = { contains: name => icon && name === 'channels-icon-button' };
  }
  set innerHTML(value) { this.content = value; }
  get innerHTML() { return this.content; }
  set textContent(value) { this.content = value; }
  get textContent() { return this.content.replace(/<[^>]*>/g, ''); }
  setAttribute(name, value) { this.attrs.set(name, String(value)); }
  getAttribute(name) { return this.attrs.get(name) || null; }
  removeAttribute(name) { this.attrs.delete(name); }
}

function harness(timeout = 125000) {
  const requests = [], messages = [], selectors = new Map(), ids = new Map();
  const state = { channelId: 1 }, registry = new Map([['1', {}], ['2', {}]]);
  let allowed = true;
  for (const [id, label] of [['channelEditorBotStartBtn', 'Запустить бота'], ['channelEditorBotStopBtn', 'Остановить'],
    ['channelEditorBotRestartBtn', 'Перезапустить']]) ids.set(id, new Element(label));
  for (const selector of ['[data-channel-editor-bot-status]', '[data-channel-editor-bot-status-badge]', '[data-channel-editor-bot-status-details]']) selectors.set(selector, new Element('—'));
  for (const id of ['1', '2']) {
    for (const action of ['start', 'stop', 'restart']) selectors.set(`[data-channel-${action}="${id}"]`, new Element(`<i>${action}</i>`, true));
    selectors.set(`[data-channel-bot-runtime-status="${id}"]`, new Element('—'));
  }
  const context = { window: { SettingsPermissions: { canEdit: () => allowed } },
    document: { getElementById: id => ids.get(id), querySelector: selector => selectors.get(selector) },
    Map, setTimeout, clearTimeout, AbortController, console,
    fetch: (url, init = {}) => new Promise((resolve, reject) => {
      requests.push({ url, init, resolve, reject });
      init.signal?.addEventListener('abort', () => reject(Object.assign(new Error('aborted'), { name: 'AbortError' })));
    }),
  };
  vm.runInNewContext(source, context);
  const runtime = context.window.SettingsChannelsBotRuntime.mount({ getChannelEditorState: () => state,
    getChannelsRegistry: () => registry, showPopup: message => messages.push(message), getCookieValue: () => 'csrf', lifecycleTimeoutMs: timeout });
  const answer = (index, status = 'running', extra = {}, ok = true) => requests[index].resolve({ ok, status: ok ? 200 : 500,
    json: async () => ({ success: ok, status, startedAt: status === 'running' ? '2026-11-12T10:00:00Z' : null, ...extra }) });
  runtime.setChannelRowBotRuntimeStatus(1, 'running'); runtime.setChannelRowBotRuntimeStatus(2, 'stopped');
  return { runtime, state, requests, messages, ids, selectors, answer, forbid: () => { allowed = false; } };
}

test('restart uses the confirmed start command once and keeps both surfaces busy until acknowledgement', async () => {
  const h = harness(), pending = h.runtime.restartBotForChannel(1);
  assert.equal(h.requests.length, 1); assert.equal(h.requests[0].url, '/api/bots/1/start');
  assert.equal(h.requests[0].init.headers['X-XSRF-TOKEN'], 'csrf');
  assert.match(h.ids.get('channelEditorBotRestartBtn').innerHTML, /spinner-border/);
  assert.match(h.selectors.get('[data-channel-bot-runtime-status="1"]').innerHTML, /Перезапускаем/);
  for (const button of h.ids.values()) assert(button.disabled);
  assert.equal(await h.runtime.startBotForChannel('1'), false);
  assert.equal(await h.runtime.stopBotForChannel(1), false);
  assert.equal(await h.runtime.restartBotForChannel(1), false); assert.equal(h.requests.length, 1);
  h.answer(0); assert.equal(await pending, true);
  assert.equal(h.ids.get('channelEditorBotRestartBtn').innerHTML, 'Перезапустить');
  assert.equal(h.ids.get('channelEditorBotRestartBtn').disabled, false);
  assert.equal(h.ids.get('channelEditorBotStartBtn').disabled, true);
  assert.equal(h.ids.get('channelEditorBotStopBtn').disabled, false);
  assert.equal(h.selectors.get('[data-channel-bot-runtime-status="1"]').getAttribute('aria-busy'), 'false');
});

test('another channel stays independent and late completion never paints its editor with the previous channel', async () => {
  const h = harness(), old = h.runtime.stopBotForChannel(1); h.state.channelId = 2;
  const current = h.runtime.startBotForChannel(2);
  h.answer(1); await current;
  assert.match(h.selectors.get('[data-channel-editor-bot-status-badge]').textContent, /запущен/);
  h.answer(0, 'stopped'); await old;
  assert.match(h.selectors.get('[data-channel-editor-bot-status-badge]').textContent, /запущен/);
  assert.equal(h.ids.get('channelEditorBotRestartBtn').disabled, false);
});

test('status GET started before an action cannot overwrite its result after completing late', async () => {
  const h = harness(), old = h.runtime.refreshBotStatus(1), pending = h.runtime.restartBotForChannel(1);
  h.answer(1); await pending; h.answer(0, 'stopped'); await old;
  assert.match(h.selectors.get('[data-channel-editor-bot-status-badge]').textContent, /запущен/);
  assert.equal(h.ids.get('channelEditorBotRestartBtn').disabled, false);
});

test('bulk status refresh keeps the pending spinner and does not query that channel', async () => {
  const h = harness(), pending = h.runtime.restartBotForChannel(1), refresh = h.runtime.refreshAllBotRuntimeStatuses();
  assert.equal(h.requests.length, 2); assert.equal(h.requests[1].url, '/api/bots/2/status');
  assert.match(h.ids.get('channelEditorBotRestartBtn').innerHTML, /spinner-border/);
  h.answer(1, 'stopped'); await refresh; assert(h.ids.get('channelEditorBotRestartBtn').disabled);
  h.answer(0); await pending;
});

test('HTTP errors and malformed acknowledgements release the spinner, show failure and permit recovery', async () => {
  const h = harness(), pending = h.runtime.stopBotForChannel(1);
  h.answer(0, 'failure', { error: 'runner unavailable' }, false);
  assert.equal(await pending, false); assert.match(h.messages[0], /runner unavailable/);
  assert.equal(h.ids.get('channelEditorBotStopBtn').innerHTML, 'Остановить');
  assert.equal(h.ids.get('channelEditorBotStartBtn').disabled, false);
  const bad = h.runtime.startBotForChannel(1); h.answer(1, 'stopped');
  assert.equal(await bad, false);
  const retry = h.runtime.startBotForChannel(1); h.answer(2);
  assert.equal(await retry, true);
});

test('network timeout clears pending state without retrying the lifecycle command', async () => {
  const h = harness(15);
  assert.equal(await h.runtime.restartBotForChannel(1), false); assert.equal(h.requests.length, 1);
  assert(h.requests[0].init.signal.aborted); assert.match(h.messages[0], /Время ожидания/);
  assert.equal(h.ids.get('channelEditorBotRestartBtn').innerHTML, 'Перезапустить');
});

test('view-only access prevents lifecycle commands and is preserved after status updates', async () => {
  const h = harness(); h.forbid();
  assert.equal(await h.runtime.restartBotForChannel(1), false); assert.equal(h.requests.length, 0);
  const refresh = h.runtime.refreshBotStatus(1); h.answer(0); await refresh;
  for (const button of h.ids.values()) assert(button.disabled);
});
