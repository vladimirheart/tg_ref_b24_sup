'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/dialogs-list-runtime.js'), 'utf8');
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));

function harness() {
  const requests = [], markers = [], overviews = [], errors = [], listeners = new Map();
  let loading = false, timer = null;
  const context = {
    window: { DIALOG_LIST_PAGINATION: { page: 1, page_size: 20, total_items: 10000, total_pages: 500 } },
    document: { visibilityState: 'visible', addEventListener: (name, fn) => listeners.set(name, fn),
      removeEventListener: name => listeners.delete(name) },
    localStorage: { getItem: () => 'all', setItem: () => {} },
    setTimeout, clearTimeout, setInterval, clearInterval, AbortController, console, Set, Map,
    fetch: (url, init) => new Promise(resolve => requests.push({ url, init, resolve })),
  };
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
  };
  const runtime = context.window.DialogsListRuntime.createRuntime(options);
  const answer = (index, ticket = 'fresh', extra = {}) => requests[index].resolve({ ok: true,
    json: async () => ({ success: true, dialogs: ticket ? [{ ticketId: ticket }] : [],
      pagination: { page: 1, page_size: 20, total_items: 10000, total_pages: 500 }, ...extra }) });
  return { context, state, options, runtime, selection, requests, answer, markers, overviews, errors, listeners, loading: () => loading };
}

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
