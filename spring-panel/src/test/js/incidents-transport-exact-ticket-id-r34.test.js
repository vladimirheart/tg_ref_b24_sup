'use strict';
// R34: execute the incident transport controls with isolated DOM/fetch doubles.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.resolve(__dirname,
  '../../main/resources/static/js/incidents-workbench.js'), 'utf8');
let passed = 0;
const failures = [];
async function test(name, fn) {
  try { await fn(); passed++; }
  catch (error) {
    failures.push(name);
    console.error('INCIDENT_TRANSPORT_CASE_RED=' + name + ': ' + error.message);
  }
}
const tick = () => new Promise(resolve => setImmediate(resolve));
function deferred() {
  let resolve, reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}
class FakeElement {
  constructor(value = '') {
    this.value = value;
    this.textContent = '';
    this.innerHTML = '';
    this.dataset = {};
    this.attrs = {};
    this.isConnected = true;
    this.listeners = new Map();
    const names = new Set();
    this.classList = {
      add: name => names.add(name),
      remove: name => names.delete(name),
      contains: name => names.has(name),
    };
  }
  addEventListener(type, handler) { this.listeners.set(type, handler); }
  setAttribute(name, value) { this.attrs[name] = String(value); }
  removeAttribute(name) { delete this.attrs[name]; }
  emit(type, event = {}) {
    assert(this.listeners.has(type), 'missing listener ' + type);
    return this.listeners.get(type)(event);
  }
}
class FakeButton extends FakeElement {}
const pageIds = [
  'incidentWorkbenchList', 'incidentWorkbenchDetail',
  'incidentWorkbenchListMeta', 'incidentWorkbenchError',
  'incidentWorkbenchSuccess', 'transportWorkbenchTicketId',
  'transportWorkbenchTicketDebug', 'transportWorkbenchInspectTicket',
  'transportWorkbenchReplayTicket', 'transportWorkbenchRequeueTicket',
  'incidentWorkbenchTransportTab', 'incidentWorkbenchQuery',
];
async function boot(options = {}) {
  const elements = new Map((options.ids || pageIds).map(id => [id,
    /transportWorkbench(ReplayTicket|RequeueTicket)$/.test(id)
      ? new FakeButton() : new FakeElement()]));
  const requests = [];
  let responseFor = null;
  const document = {
    activeElement: null,
    getElementById: id => elements.get(id) || null,
    querySelector: selector => {
      if (selector === 'meta[name="_csrf"]') return options.noCsrf ? null
        : { getAttribute: () => 'csrf-token' };
      if (selector === 'meta[name="_csrf_header"]') return options.noCsrf ? null
        : { getAttribute: () => 'X-CSRF-TOKEN' };
      return null;
    },
  };
  const window = {
    clearTimeout() {}, setTimeout() { return 1; },
    requestAnimationFrame(fn) { fn(); },
  };
  const fetch = async (url, options = {}) => {
    const request = { url, options };
    requests.push(request);
    let payload = responseFor && await responseFor(request);
    if (!payload) {
      if (url.startsWith('/api/incidents?')) payload = { items: [] };
      else if (url.startsWith('/api/analytics/integration-transport/tickets/')
          && url.endsWith('/debug')) {
        payload = { success: true, ticket_id: decodeURIComponent(url.split('/')[5]),
          inbound_events: [], outbound_events: [], related_incidents: [],
          recent_operations: [] };
      } else if (url.startsWith('/api/analytics/integration-transport/tickets/')
          && options.method === 'POST') payload = { success: true };
      else throw Error('unexpected fetch ' + url);
    }
    const status = payload.httpStatus || 200;
    return { ok: status < 400, status, json: async () => payload };
  };
  vm.runInNewContext(source, {
    document, window, fetch, URLSearchParams,
    Element: FakeElement, HTMLElement: FakeElement,
    HTMLButtonElement: FakeButton, HTMLSelectElement: FakeElement,
    console,
  }, { filename: 'incidents-workbench.js', timeout: 1000 });
  await tick();
  return {
    elements, requests,
    respond(fn) { responseFor = fn; },
    input(value) { elements.get('transportWorkbenchTicketId').value = value; },
    click(mode) {
      const id = mode === 'inspect' ? 'transportWorkbenchInspectTicket'
        : mode === 'replay' ? 'transportWorkbenchReplayTicket'
          : 'transportWorkbenchRequeueTicket';
      const button = elements.get(id);
      button.emit('click', { currentTarget: button });
      return button;
    },
    async settle() { await tick(); },
  };
}
const suffix = {
  inspect: '/debug',
  replay: '/replay-inbound?limit=25',
  requeue: '/requeue-outbound?limit=25',
};
const modes = ['inspect', 'replay', 'requeue'];
const exactIds = [
  'T-EXACT', ' T-EXACT ', '\tT-EXACT\t', ' null ',
  'T/percent%20?x=#&"\'<>', ' Заявка🙂 ',
];
(async () => {
  for (const mode of modes) {
    for (const [index, id] of exactIds.entries()) {
      await test('exact_' + mode + '_' + index, async () => {
        const app = await boot();
        app.requests.length = 0;
        app.input(id);
        const button = app.click(mode);
        await app.settle();
        const targeted = app.requests.filter(r => r.url.includes('/tickets/'));
        assert.equal(targeted.length, 1);
        assert.equal(targeted[0].url,
          '/api/analytics/integration-transport/tickets/'
          + encodeURIComponent(id) + suffix[mode]);
        assert.equal(targeted[0].options.credentials, 'same-origin');
        assert.equal(targeted[0].options.headers.Accept, 'application/json');
        assert.equal(targeted[0].options.headers['X-CSRF-TOKEN'], 'csrf-token');
        assert.equal(targeted[0].options.method, mode === 'inspect' ? undefined : 'POST');
        if (mode === 'inspect') {
          assert.equal(app.elements.get('transportWorkbenchTicketDebug')
            .attrs['aria-busy'], 'false');
          assert(app.elements.get('transportWorkbenchTicketDebug').innerHTML
            .includes('Ticket ' + id.replace(/&/g, '&amp;').replace(/</g, '&lt;')
              .replace(/>/g, '&gt;').replace(/"/g, '&quot;')
              .replace(/'/g, '&#39;')));
        } else {
          assert.equal(button.dataset.actionPending, undefined);
          assert.equal(button.attrs['aria-busy'], undefined);
          assert.equal(button.classList.contains('disabled'), false);
          assert(app.requests.some(r => r.url.startsWith('/api/incidents?')),
            'success should refresh incidents');
        }
        assert.equal(app.elements.get('incidentWorkbenchError').textContent, '');
      });
    }
  }
  for (const mode of modes) {
    for (const [index, id] of ['', ' \t '].entries()) {
      await test('blank_' + mode + '_' + index, async () => {
        const app = await boot();
        app.requests.length = 0;
        app.input(id);
        app.click(mode);
        await app.settle();
        assert.equal(app.requests.length, 0);
        assert.match(app.elements.get('incidentWorkbenchError').textContent,
          /Укажите ticket id/);
      });
    }
  }
  for (const mode of ['replay', 'requeue']) {
    await test('pending_button_and_double_click_' + mode, async () => {
      const app = await boot();
      const pending = deferred();
      app.respond(request => request.options.method === 'POST'
        ? pending.promise : null);
      app.requests.length = 0;
      app.input(' T-EXACT ');
      const button = app.click(mode);
      app.click(mode);
      await app.settle();
      assert.equal(app.requests.filter(r => r.options.method === 'POST').length, 1);
      assert.equal(button.dataset.actionPending, 'true');
      assert.equal(button.attrs['aria-busy'], 'true');
      pending.resolve({ success: true });
      await app.settle();
      assert.equal(button.dataset.actionPending, undefined);
      assert.equal(button.attrs['aria-busy'], undefined);
    });
    await test('failed_action_restores_button_' + mode, async () => {
      const app = await boot();
      app.respond(request => request.options.method === 'POST'
        ? { httpStatus: 500, message: 'transport_failed' } : null);
      app.requests.length = 0;
      app.input(' T-EXACT ');
      const button = app.click(mode);
      await app.settle();
      assert.equal(app.requests.filter(r => r.options.method === 'POST').length, 1);
      assert.equal(button.dataset.actionPending, undefined);
      assert.match(app.elements.get('incidentWorkbenchError').textContent,
        /transport_failed/);
    });
  }
  await test('debug_latest_response_wins', async () => {
    const app = await boot();
    const old = deferred();
    app.respond(request => request.url.endsWith('/debug')
      && request.url.includes('%20T-OLD%20') ? old.promise : null);
    app.requests.length = 0;
    app.input(' T-OLD ');
    app.click('inspect');
    app.input(' T-NEW ');
    app.click('inspect');
    await app.settle();
    assert(app.elements.get('transportWorkbenchTicketDebug').innerHTML
      .includes('T-NEW'));
    old.resolve({ success: true, ticket_id: ' T-OLD ',
      inbound_events: [], outbound_events: [], related_incidents: [],
      recent_operations: [] });
    await app.settle();
    assert(!app.elements.get('transportWorkbenchTicketDebug').innerHTML
      .includes('T-OLD'));
    assert.equal(app.elements.get('transportWorkbenchTicketDebug')
      .attrs['aria-busy'], 'false');
  });
  await test('blank_input_invalidates_older_debug', async () => {
    const app = await boot();
    const old = deferred();
    app.respond(request => request.url.endsWith('/debug') ? old.promise : null);
    app.input(' T-OLD ');
    app.click('inspect');
    app.input(' \t ');
    app.click('inspect');
    await app.settle();
    old.resolve({ success: true, ticket_id: ' T-OLD ',
      inbound_events: [], outbound_events: [], related_incidents: [],
      recent_operations: [] });
    await app.settle();
    assert(!app.elements.get('transportWorkbenchTicketDebug').innerHTML
      .includes('T-OLD'));
    assert.equal(app.elements.get('transportWorkbenchTicketDebug')
      .attrs['aria-busy'], 'false');
  });
  await test('tab_hide_invalidates_debug', async () => {
    const app = await boot();
    const old = deferred();
    app.respond(request => request.url.endsWith('/debug') ? old.promise : null);
    app.input(' T-OLD ');
    app.click('inspect');
    app.elements.get('incidentWorkbenchTransportTab').emit('hide.bs.tab');
    old.resolve({ success: true, ticket_id: ' T-OLD ',
      inbound_events: [], outbound_events: [], related_incidents: [],
      recent_operations: [] });
    await app.settle();
    assert(!app.elements.get('transportWorkbenchTicketDebug').innerHTML
      .includes('T-OLD'));
  });
  await test('debug_failure_resets_busy', async () => {
    const app = await boot();
    app.respond(request => request.url.endsWith('/debug')
      ? { httpStatus: 500, error: 'debug_failed' } : null);
    app.input(' T-EXACT ');
    app.click('inspect');
    await app.settle();
    assert.equal(app.elements.get('transportWorkbenchTicketDebug')
      .attrs['aria-busy'], 'false');
    assert.match(app.elements.get('incidentWorkbenchError').textContent,
      /debug_failed/);
  });
  await test('csrf_without_meta_keeps_request_valid', async () => {
    const app = await boot({ noCsrf: true });
    app.requests.length = 0;
    app.input(' T-EXACT ');
    app.click('requeue');
    await app.settle();
    const request = app.requests.find(r => r.options.method === 'POST');
    assert(request);
    assert.equal(request.options.headers['X-CSRF-TOKEN'], undefined);
    assert.equal(request.options.headers.Accept, 'application/json');
  });
  await test('missing_ticket_input_rejects_request', async () => {
    const app = await boot({ ids: pageIds.filter(id => id !== 'transportWorkbenchTicketId') });
    app.requests.length = 0;
    app.click('inspect');
    await app.settle();
    assert.equal(app.requests.length, 0);
    assert.match(app.elements.get('incidentWorkbenchError').textContent,
      /Укажите ticket id/);
  });
  await test('page_absent_does_not_boot', async () => {
    const app = await boot({ ids: [] });
    assert.equal(app.requests.length, 0);
  });
  await test('all_three_ticket_reads_and_event_bindings', () => {
    assert.equal((source.match(/exactLegacyTicketId\(transportNodes\.ticketId\?\.value\)/g) || []).length, 3);
    assert.equal((source.match(/getElementById\('transportWorkbench(Replay|Requeue)Ticket'\)\?\.addEventListener\('click', \(event\) =>/g) || []).length, 2);
  });
  if (failures.length || passed !== 36) {
    console.error('INCIDENT_TRANSPORT_EXACT_ID_RESULT=RED; passed=' + passed
      + '; failed=' + failures.length);
    process.exitCode = 1;
  } else {
    console.log('INCIDENT_TRANSPORT_EXACT_ID_TESTS_GREEN=' + passed);
    console.log('INCIDENT_TRANSPORT_EXACT_ID_RESULT=GREEN');
  }
})().catch(error => {
  console.error('INCIDENT_TRANSPORT_EXACT_ID_FATAL=' + (error.stack || error));
  process.exitCode = 1;
});
