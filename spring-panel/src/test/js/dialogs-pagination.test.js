'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/dialogs-list-runtime.js'), 'utf8');
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));

test('prepaint fallback releases SSR without a working main runtime and supports explicit ownership', () => {
  const prepaint = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/dialogs-prepaint-runtime.js'), 'utf8');
  const classes = new Set(), timers = new Map();
  const context = { document: { documentElement: { classList: {
    add: value => classes.add(value), remove: value => classes.delete(value),
    toggle: (value, enabled) => enabled ? classes.add(value) : classes.delete(value),
  } } }, window: { __dialogsPrimaryReady: true,
    localStorage: { getItem() { throw Error('storage disabled'); } },
    setTimeout: fn => { timers.set(1, fn); return 1; }, clearTimeout: id => timers.delete(id) } };
  vm.runInNewContext(prepaint, context);
  assert(classes.has('dialogs-list-preparing'));
  timers.get(1)(); assert(!classes.has('dialogs-list-preparing'), 'Main flag alone must not leave SSR hidden');
  vm.runInNewContext(prepaint, context);
  context.window.DialogsFirstPaint.claim(); assert.equal(timers.size, 0);
  assert(classes.has('dialogs-list-preparing'), 'Runtime owns the guard while its first query is pending');
  context.window.DialogsFirstPaint.ready(); assert(!classes.has('dialogs-list-preparing'));
});

function harness({ bootstrap, overrides = {}, initialTimeoutMs } = {}) {
  const requests = [], markers = [], overviews = [], errors = [], listeners = new Map();
  let loading = false, timer = null, readyCount = 0;
  const context = {
    window: { DIALOG_LIST_PAGINATION: { page: 1, page_size: 20, total_items: 10000, total_pages: 500 } },
    document: { visibilityState: 'visible', addEventListener: (name, fn) => listeners.set(name, fn),
      removeEventListener: name => listeners.delete(name) },
    localStorage: { getItem: () => 'all', setItem: () => {} },
    setTimeout: (callback, ms) => setTimeout(callback, initialTimeoutMs && [3000, 10000].includes(ms) ? initialTimeoutMs : ms),
    clearTimeout, setInterval, clearInterval, AbortController, console, Set, Map,
    fetch: (url, init) => new Promise((resolve, reject) => {
      requests.push({ url, init, resolve, reject });
      init?.signal?.addEventListener('abort', () => reject(Object.assign(new Error('aborted'), { name: 'AbortError' })));
    }),
  };
  context.window.DIALOG_LIST_BOOTSTRAP = bootstrap;
  context.window.DialogsFirstPaint = { ready: () => { readyCount++; } };
  vm.runInNewContext(source, context);
  const state = { page: 1, pageSize: 20, search: '', status: '', view: 'all', slaWindowMinutes: null, sortMode: 'default' };
  const selection = new Set();
  const options = {
    filterState: state, selectedTicketIds: selection, slaOrchestrationByTicket: new Map(), defaultPageSize: 20,
    listPollInterval: 8000, storage: { pageSize: 'pageSize' },
    rowsList: () => [], updateAllSlaBadges() {}, setLastFilteredRows() {},
    buildDialogsMarker: dialogs => dialogs.map(d => d.ticketId).join(','),
    getLastListMarker: () => markers.at(-1) || null, setLastListMarker: value => markers.push(value),
    normalizeMyDialogsState: value => overviews.push(value), renderMyDialogsPanel() {}, refreshSummaryCounters() {},
    requestSidebarNotificationRefresh() {}, showNotification: value => errors.push(value),
    getListLoading: () => loading, setListLoading: value => { loading = value; },
    getListPollTimer: () => timer, setListPollTimer: value => { timer = value; },
    canRunAction: () => true, emitWorkspaceTelemetry() {},
    getSnoozedTicketIds: () => [' T/1,?& Ю '], getPinnedTicketIds: () => ['pinned'],
    ...overrides,
  };
  const runtime = context.window.DialogsListRuntime.createRuntime(options);
  const answer = (index, ticket = 'fresh', extra = {}) => requests[index].resolve({ ok: true,
    json: async () => ({ success: true, dialogs: ticket ? [{ ticketId: ticket }] : [],
      pagination: { page: 1, page_size: 20, total_items: 10000, total_pages: 500 }, ...extra }) });
  return { context, state, options, runtime, selection, requests, answer, markers, overviews, errors, listeners,
    readyCount: () => readyCount, loading: () => loading };
}

test('startup adopts matching SSR page without reloading rows or saving restored preferences', async () => {
  const query = { page: 1, pageSize: 50, search: '', status: '', view: 'active', slaWindowMinutes: 30,
    sortMode: 'sla_priority', snoozedTicketIds: [], pinnedTicketIds: [] };
  const h = harness({ bootstrap: { query, preferences: { view: 'active', page_size: '50',
    sla_window_minutes: 30, sort_mode: 'sla_priority' }, slaOrchestration: { tickets: { 'T/1': { state: 'breached' } } } },
    overrides: { dialogSlaWindowPresets: [15, 30, 60], getPinnedTicketIds: () => [], getSnoozedTicketIds: () => [] } });
  h.context.window.DIALOG_LIST_PAGINATION.page_size = 50;
  await Promise.all([h.runtime.initializeList(), h.runtime.initializeList()]);
  assert.equal(h.state.view, 'active'); assert.equal(h.state.pageSize, 50); assert.equal(h.state.slaWindowMinutes, 30);
  assert.equal(h.readyCount(), 1);
  assert.equal(h.options.slaOrchestrationByTicket.get('T/1').state, 'breached', 'SSR bootstrap must retain authoritative SLA signals');
  await delay(550);
  assert.equal(h.requests.length, 0, 'Neither preferences nor list should be fetched/saved on matching bootstrap');
  h.runtime.dispose();
});

test('startup waits for fallback preferences and performs one configured query', async () => {
  let removed = 0;
  const h = harness({ overrides: { rowsList: () => [{ remove() { removed++; } }] } });
  const pending = h.runtime.initializeList();
  assert.equal(h.requests.length, 1); assert.equal(h.requests[0].url, '/api/dialogs/triage-preferences');
  assert.equal(h.readyCount(), 0); assert.equal(removed, 0);
  h.requests[0].resolve({ ok: true, json: async () => ({ success: true,
    preferences: { view: 'active', page_size: '50' } }) });
  await delay(0);
  assert.equal(removed, 1); assert.equal(h.requests.length, 2); assert.equal(h.readyCount(), 0);
  const query = JSON.parse(h.requests[1].init.body).query;
  assert.equal(query.view, 'active'); assert.equal(query.pageSize, 50);
  h.answer(1); await pending;
  assert.equal(h.readyCount(), 1);
  await delay(550); assert.equal(h.requests.length, 2, 'Restoring preferences must not autosave them');
  h.runtime.dispose();
});

test('late fallback preferences cannot overwrite a filter chosen by the operator', async () => {
  const h = harness(), pending = h.runtime.initializeList();
  h.runtime.applyQuickSearch('typed');
  await delay(250); assert.equal(h.requests.length, 1, 'No list requests before initialization completes');
  h.requests[0].resolve({ ok: true, json: async () => ({ success: true, preferences: { view: 'active', page_size: '100' } }) });
  await delay(0);
  assert.equal(h.requests.length, 2);
  const query = JSON.parse(h.requests[1].init.body).query;
  assert.equal(query.search, 'typed'); assert.equal(query.view, 'all'); assert.equal(query.pageSize, 20);
  h.answer(1); await pending; h.runtime.dispose();
});

test('preference failure falls back locally and a failed first query releases loading for retry', async () => {
  const h = harness(), pending = h.runtime.initializeList();
  h.requests[0].reject(new Error('offline')); await delay(0);
  assert.equal(h.requests.length, 2);
  h.requests[1].resolve({ ok: false, status: 500, json: async () => ({ success: false }) });
  await pending; assert.equal(h.readyCount(), 1); assert.equal(h.errors.length, 1); assert.equal(h.loading(), false);
  const retry = h.runtime.refreshDialogsList(); h.answer(2); await retry;
  assert.equal(h.markers.at(-1), 'fresh'); assert.equal(h.readyCount(), 1); h.runtime.dispose();
});

test('unexpected initialization failure releases the first-paint guard and shows an error', async () => {
  let broken = true;
  const h = harness({ bootstrap: { preferences: {} }, overrides: {
    getPinnedTicketIds: () => { if (broken) throw Error('bad local state'); return []; },
  } });
  await h.runtime.initializeList(); assert.equal(h.readyCount(), 1); assert.equal(h.errors.length, 1);
  broken = false;
  const retry = h.runtime.refreshDialogsList(); h.answer(0); await retry;
  assert.equal(h.markers.at(-1), 'fresh'); h.runtime.dispose();
});

test('stalled preference and first-list requests time out and release the guard for retry', async () => {
  const h = harness({ initialTimeoutMs: 15 });
  await h.runtime.initializeList();
  assert.equal(h.requests.length, 2);
  assert(h.requests.every(request => request.init.signal.aborted));
  assert.equal(h.readyCount(), 1); assert.equal(h.errors.length, 1); assert.equal(h.loading(), false);
  const retry = h.runtime.refreshDialogsList(); h.answer(2); await retry;
  assert.equal(h.markers.at(-1), 'fresh'); h.runtime.dispose();
});

test('old all preference and oversized values never enable unbounded pages', () => {
  const h = harness(), normalize = h.context.window.DialogsListRuntime.normalizePageSize;
  for (const invalid of ['all', '', null, -1, NaN, Infinity]) assert.equal(normalize(invalid), 20);
  assert.equal(normalize(10000000), 100);
  assert.equal(normalize(10), 10);
  h.runtime.loadPageSize(); assert.equal(h.state.pageSize, 20);
  h.runtime.dispose();
});

test('server counts drive pagination, not the length of loaded rows', () => {
  const h = harness();
  const result = h.runtime.resolveListPagination(20);
  assert.equal(result.totalItems, 10000); assert.equal(result.totalPages, 500);
  assert.equal(h.runtime.applyPageSize(Array(20).fill({})), 20);
  h.runtime.dispose();
});

test('read-only query carries bounded page, full filter and exact opaque snoozed IDs', async () => {
  const h = harness(); Object.assign(h.state, { page: 17, search: 'заказ', view: 'active', status: 'Новый', sortMode: 'sla_priority', slaWindowMinutes: 30 });
  const pending = h.runtime.refreshDialogsList({ includeOverview: false });
  assert.equal(h.requests[0].url, '/api/dialogs/query'); assert.equal(h.requests[0].init.method, 'POST');
  const body = JSON.parse(h.requests[0].init.body);
  assert.deepEqual(body, { query: { page: 17, pageSize: 20, search: 'заказ', status: 'Новый', view: 'active',
    slaWindowMinutes: 30, sortMode: 'sla_priority', snoozedTicketIds: [' T/1,?& Ю '], pinnedTicketIds: ['pinned'] }, includeOverview: false });
  h.answer(0); await pending; assert.equal(h.overviews.length, 0, 'Page switch must not replace global My dialogs with the page');
  h.runtime.dispose();
});

test('filter invalidates old response immediately, including the debounce interval', async () => {
  const h = harness(); h.selection.add('old');
  const old = h.runtime.refreshDialogsList();
  h.runtime.applyQuickSearch('new');
  assert(h.requests[0].init.signal.aborted); assert.equal(h.selection.size, 0);
  h.answer(0, 'stale'); await old;
  assert.equal(h.markers.length, 0, 'Old response cannot apply during debounce');
  await delay(250); assert.equal(h.requests.length, 2);
  h.answer(1, 'new'); await delay(0); assert.equal(h.markers.at(-1), 'new');
  assert.equal(h.loading(), false); h.runtime.dispose();
});

test('newer page wins even if aborted network response still arrives later', async () => {
  const h = harness(); const old = h.runtime.refreshDialogsList();
  h.state.page = 2; h.runtime.applyFilters(); await delay(20);
  h.answer(1, 'page2'); await delay(0); h.answer(0, 'page1'); await old;
  assert.deepEqual(h.markers, ['page2']); assert.equal(h.loading(), false); h.runtime.dispose();
});

test('multiple invalidations while loading coalesce into one follow-up page request', async () => {
  const h = harness(); const first = h.runtime.refreshDialogsList();
  await h.runtime.refreshDialogsList(); await h.runtime.refreshDialogsList();
  assert.equal(h.requests.length, 1);
  h.answer(0); await first; assert.equal(h.requests.length, 2);
  h.answer(1); await delay(0); assert.equal(h.requests.length, 2); h.runtime.dispose();
});

test('overview is optional and authoritative, never inferred from table rows', async () => {
  const h = harness(); const pending = h.runtime.refreshDialogsList();
  const counts = { new: 8000, unanswered: 1000, in_work: 1000 };
  h.answer(0, 'fresh', { my_dialogs: { new: [], unanswered: [], in_work: [], counts } });
  await pending; assert.equal(h.overviews[0].counts.new, 8000); h.runtime.dispose();
});

test('hidden tab skips fetch; becoming visible refreshes and disposal prevents late writes', async () => {
  const h = harness(); h.context.document.visibilityState = 'hidden';
  await h.runtime.refreshDialogsList(); assert.equal(h.requests.length, 0);
  h.context.document.visibilityState = 'visible'; h.listeners.get('visibilitychange')();
  assert.equal(h.requests.length, 1); h.runtime.dispose();
  h.answer(0, 'too-late'); await delay(0); assert.equal(h.markers.length, 0);
  assert(!h.listeners.has('visibilitychange'));
});

test('server errors stay errors and do not turn into a successful empty queue', async () => {
  const h = harness(); const pending = h.runtime.refreshDialogsList();
  h.requests[0].resolve({ ok: false, status: 500, json: async () => ({ success: false }) });
  await pending; assert.equal(h.markers.length, 0); assert.equal(h.errors.length, 1);
  assert.equal(h.loading(), false); h.runtime.dispose();
});
