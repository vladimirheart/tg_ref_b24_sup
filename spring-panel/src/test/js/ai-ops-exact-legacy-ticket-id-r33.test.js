'use strict';
// R33: execute the complete AI Ops IIFE with deterministic DOM/fetch doubles.
// No browser, network, timers or database are used.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const source = fs.readFileSync(path.resolve(__dirname,
  '../../main/resources/static/js/ai-ops.js'), 'utf8');
let passed = 0;
const failures = [];

async function test(name, fn) {
  try {
    await fn();
    passed++;
  } catch (error) {
    failures.push(name);
    console.error('AI_OPS_CASE_RED=' + name + ': ' + error.message);
  }
}

function element(value = '') {
  const listeners = new Map();
  return {
    value, textContent: '', innerHTML: '', disabled: false,
    addEventListener(type, handler) { listeners.set(type, handler); },
    emit(type, event = {}) {
      assert(listeners.has(type), 'missing listener ' + type);
      return listeners.get(type)(event);
    },
  };
}

const panelIds = [
  'aiOpsState', 'aiOpsReviewState', 'aiOpsReviewTableBody',
  'aiOpsTestTicket', 'aiOpsTestMessage', 'aiOpsTestRun',
  'aiOpsTestState', 'aiOpsTestResult', 'aiOpsEventType',
  'aiOpsActor', 'aiOpsApplyFilters', 'aiOpsMemoryQuery', 'aiOpsMemorySearch',
];
const tick = () => new Promise(resolve => setImmediate(resolve));

async function boot(options = {}) {
  const elements = new Map((options.ids || panelIds).map(id => [id, element()]));
  const requests = [];
  const intervals = [];
  const location = { href: '/ai-ops' };
  const initialItems = options.items || [];
  let handler = null;
  const document = {
    cookie: options.cookie || '', hidden: false,
    getElementById: id => elements.get(id) || null,
    querySelector: selector => selector === 'meta[name="_csrf"]' && options.meta !== false
      ? { getAttribute: () => 'meta-token' } : null,
    addEventListener() {},
  };
  const window = {
    location, clearTimeout() {}, setTimeout() { return 1; },
    clearInterval() {}, setInterval(fn, delay) { intervals.push({ fn, delay }); return 1; },
  };
  const fetch = async (url, init = {}) => {
    const request = { url, init };
    requests.push(request);
    let response = handler && handler(request);
    if (!response) {
      const allowed = [
        '/api/dialogs/ai-monitoring/summary?',
        '/api/dialogs/ai-monitoring/events?',
        '/api/dialogs/ai-solution-memory?',
        '/api/dialogs/ai-reviews?',
        '/api/dialogs/ai-monitoring/offline-eval',
      ];
      assert(allowed.some(prefix => url.startsWith(prefix)), 'unexpected fetch: ' + url);
      response = { success: true, items: url.startsWith('/api/dialogs/ai-reviews?')
        ? initialItems : [], summary: {}, offline_eval: {} };
    }
    const status = response.httpStatus || 200;
    return { ok: status < 400, status, json: async () => response };
  };
  vm.runInNewContext(source, { document, window, fetch, URLSearchParams, console },
    { filename: 'ai-ops.js', timeout: 1000 });
  await tick();
  return {
    elements, requests, intervals, location,
    respond(fn) { handler = fn; },
    async click(id, target) {
      elements.get(id).emit('click', target ? { target } : {});
      await tick();
    },
  };
}

function decodeHtml(value) {
  return value.replace(/&(amp|lt|gt|quot|#39);/g, (_, entity) => ({
    amp: '&', lt: '<', gt: '>', quot: '"', '#39': "'",
  })[entity]);
}

function rowFor(ticketId, queryKey = ' review-key ') {
  const buttons = {};
  const row = {
    isConnected: true,
    getAttribute(name) {
      if (name === 'data-ai-review-ticket') return ticketId;
      if (name === 'data-ai-review-key') return queryKey;
      return null;
    },
    querySelector(selector) { return buttons[selector] || null; },
    querySelectorAll(selector) {
      return selector === 'button' ? Object.values(buttons)
        : [buttons['[data-ai-review-approve]'], buttons['[data-ai-review-reject]']];
    },
  };
  for (const selector of ['[data-ai-review-open]', '[data-ai-review-approve]', '[data-ai-review-reject]']) {
    buttons[selector] = {
      disabled: selector === '[data-ai-review-open]' && !String(ticketId || '').trim(),
      closest(query) { return query === '[data-ai-review-key]' ? row
        : query === selector ? this : null; },
    };
  }
  return { row, buttons };
}

async function checkTestbench(app, ticketId) {
  app.elements.get('aiOpsTestTicket').value = ticketId;
  app.elements.get('aiOpsTestMessage').value = '  message text  ';
  app.respond(({ url }) => /\/(ai-reclassify|ai-retrieve-debug)$/.test(url)
    ? { success: true, result: {} } : null);
  app.requests.length = 0;
  await app.click('aiOpsTestRun');
}

(async () => {
  const validIds = [
    'T-EXACT', ' T-EXACT ', 'T-EXACT ', ' T-EXACT', '\tT-EXACT\t',
    '\u00a0T-EXACT\u00a0', ' null ', 'null', ' NULL ', '0',
    ' T/percent%20?x=#&"\'<> ', ' Заявка🙂 ', '\u200b', 'T-exact',
  ];
  for (const [index, id] of validIds.entries()) {
    await test('exact_render_navigation_testbench_' + index, async () => {
      const app = await boot({ items: [{ query_key: ' key ', last_ticket_id: id }] });
      const html = app.elements.get('aiOpsReviewTableBody').innerHTML;
      const match = html.match(/data-ai-review-ticket="([^"]*)"/);
      assert(match, 'missing rendered ticket attribute');
      const storedId = decodeHtml(match[1]);
      assert.equal(storedId, id, 'renderer must preserve the opaque ID');
      assert(!/data-ai-review-open disabled/.test(html), 'nonblank ticket stays openable');
      if (id.includes('<')) assert(html.includes('&lt;') && html.includes('&quot;'),
        'ticket must remain HTML escaped');
      const { buttons } = rowFor(storedId);
      await app.click('aiOpsReviewTableBody', buttons['[data-ai-review-open]']);
      assert.equal(app.location.href, '/dialogs?ticketId=' + encodeURIComponent(id));
      await checkTestbench(app, id);
      assert.deepEqual(app.requests.map(r => r.url), [
        '/api/dialogs/' + encodeURIComponent(id) + '/ai-reclassify',
        '/api/dialogs/' + encodeURIComponent(id) + '/ai-retrieve-debug',
      ]);
      for (const request of app.requests) {
        assert.equal(request.init.method, 'POST');
        assert.equal(request.init.credentials, 'same-origin');
        assert.equal(request.init.headers['X-XSRF-TOKEN'], 'meta-token');
        assert.equal(request.init.headers['Content-Type'], 'application/json');
        const body = JSON.parse(request.init.body);
        assert.equal(body.message, 'message text', 'message normalization stays unchanged');
        assert.equal(body.message_type, 'text');
        assert.equal(body.attachment, '');
      }
      assert.equal(JSON.parse(app.requests[1].init.body).limit, 5);
      assert.equal(app.elements.get('aiOpsTestRun').disabled, false);
      assert.match(app.elements.get('aiOpsTestState').textContent, /^Готово\./);
      assert.equal(app.intervals.length, 1);
      assert.equal(app.intervals[0].delay, 20000);
    });
  }
  for (const [index, id] of [null, undefined, '', ' ', '\t\n', '\u00a0'].entries()) {
    await test('blank_id_no_navigation_no_post_' + index, async () => {
      const app = await boot({ items: [{ query_key: 'key', last_ticket_id: id }] });
      const html = app.elements.get('aiOpsReviewTableBody').innerHTML;
      assert(html.includes('data-ai-review-ticket=""'));
      assert(/data-ai-review-open disabled/.test(html));
      const { buttons } = rowFor(id);
      await app.click('aiOpsReviewTableBody', buttons['[data-ai-review-open]']);
      assert.equal(app.location.href, '/ai-ops');
      await checkTestbench(app, id);
      assert.equal(app.requests.length, 0);
      assert.equal(app.elements.get('aiOpsTestState').textContent,
        'Ticket ID и сообщение обязательны.');
      assert.equal(app.elements.get('aiOpsTestRun').disabled, false);
    });
  }
  for (const message of ['', ' \t ']) {
    await test('blank_message_no_post_' + JSON.stringify(message), async () => {
      const app = await boot();
      app.requests.length = 0;
      app.elements.get('aiOpsTestTicket').value = ' T-EXACT ';
      app.elements.get('aiOpsTestMessage').value = message;
      await app.click('aiOpsTestRun');
      assert.equal(app.requests.length, 0);
      assert.equal(app.elements.get('aiOpsTestRun').disabled, false);
    });
  }
  for (const success of [true, false]) {
    for (const ticketId of [' null ', ' \t ']) {
      await test('review_restore_' + success + '_' + JSON.stringify(ticketId), async () => {
        const app = await boot();
        const action = success ? 'approve' : 'reject';
        const { buttons } = rowFor(ticketId);
        app.respond(({ url }) => url.endsWith('/' + action)
          ? { success, httpStatus: success ? 200 : 500, error: 'review_failed' } : null);
        app.requests.length = 0;
        app.elements.get('aiOpsReviewTableBody').emit('click',
          { target: buttons['[data-ai-review-' + action + ']'] });
        assert(Object.values(buttons).every(button => button.disabled),
          'all buttons disabled while review POST is pending');
        await tick();
        assert.equal(app.requests[0].url, '/api/dialogs/ai-reviews/review-key/' + action);
        assert.equal(app.requests[0].init.headers['X-XSRF-TOKEN'], 'meta-token');
        assert.equal(buttons['[data-ai-review-open]'].disabled, !ticketId.trim());
        assert.equal(buttons['[data-ai-review-approve]'].disabled, false);
        assert.equal(buttons['[data-ai-review-reject]'].disabled, false);
        if (!success) assert.match(app.elements.get('aiOpsReviewState').textContent, /review_failed/);
      });
    }
  }
  await test('testbench_failure_restores_button_and_exact_paths', async () => {
    const app = await boot();
    app.elements.get('aiOpsTestTicket').value = ' T-EXACT ';
    app.elements.get('aiOpsTestMessage').value = ' message ';
    app.respond(({ url }) => /\/(ai-reclassify|ai-retrieve-debug)$/.test(url)
      ? { success: false, httpStatus: 500, error: 'preview_failed' } : null);
    app.requests.length = 0;
    await app.click('aiOpsTestRun');
    assert(app.requests.every(r => r.url.includes('/%20T-EXACT%20/')));
    assert.equal(app.requests.length, 2);
    assert.match(app.elements.get('aiOpsTestState').textContent, /preview_failed/);
    assert.equal(app.elements.get('aiOpsTestRun').disabled, false);
    assert.equal(app.elements.get('aiOpsTestResult').innerHTML, '');
  });
  await test('text_filters_and_memory_query_keep_normalization', async () => {
    const app = await boot();
    app.requests.length = 0;
    app.elements.get('aiOpsEventType').value = ' REPLY ';
    app.elements.get('aiOpsActor').value = ' Operator ';
    await app.click('aiOpsApplyFilters');
    const params = new URLSearchParams(app.requests[0].url.split('?')[1]);
    assert.equal(params.get('eventType'), 'reply');
    assert.equal(params.get('actor'), 'operator');
    app.elements.get('aiOpsMemoryQuery').value = ' вопрос ';
    await app.click('aiOpsMemorySearch');
    const memoryParams = new URLSearchParams(app.requests[1].url.split('?')[1]);
    assert.equal(memoryParams.get('query'), 'вопрос');
  });
  await test('csrf_cookie_fallback_preserved', async () => {
    const app = await boot({ meta: false, cookie: 'other=1; XSRF-TOKEN=cookie%20token' });
    await checkTestbench(app, ' T-EXACT ');
    assert(app.requests.every(r => r.init.headers['X-XSRF-TOKEN'] === 'cookie token'));
  });
  await test('optional_panels_may_be_absent', async () => {
    const app = await boot({ ids: ['aiOpsState'] });
    assert.equal(app.requests.length, 5);
    assert.equal(app.intervals.length, 1);
    assert.match(app.elements.get('aiOpsState').textContent, /^Window:/);
  });
  await test('missing_page_state_does_not_boot', async () => {
    const app = await boot({ ids: [] });
    assert.equal(app.requests.length, 0);
    assert.equal(app.intervals.length, 0);
  });
  await test('all_four_ticket_reads_use_exact_boundary', () => {
    assert.equal((source.match(/const ticketId = exactLegacyTicketId\(/g) || []).length, 4);
    assert(!/const ticketId = String\([^\n]*\)\.trim\(\)/.test(source));
  });
  console.log('AI_OPS_EXACT_ID_TESTS_GREEN=' + passed);
  if (failures.length || passed !== 32) {
    console.error('AI_OPS_EXACT_ID_TEST_RESULT=RED; failures=' + failures.length);
    process.exitCode = 1;
  } else {
    console.log('AI_OPS_EXACT_ID_TEST_RESULT=GREEN');
  }
})().catch(error => {
  console.error('AI_OPS_EXACT_ID_TEST_FATAL=' + (error.stack || error));
  process.exitCode = 1;
});
