'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { test } = require('node:test');

class Element {
  constructor() {
    this.dataset = {}; this.style = {}; this.listeners = {}; this.disabled = false;
    const classes = new Set();
    this.classList = {
      add: name => classes.add(name), remove: name => classes.delete(name), contains: name => classes.has(name),
      toggle: (name, force) => force ? classes.add(name) : classes.delete(name),
    };
  }
  addEventListener(name, handler) { this.listeners[name] = handler; }
  querySelector() { return null; }
  querySelectorAll() { return []; }
  setAttribute() {}
}

function fixture(initialCategories = [], summaryOverrides = {}) {
  const ticketId = ' R/Ю?#+ ';
  let selected = new Set(), serverCategories = initialCategories, timer;
  let serverStatus = 'open', serverHistory = [], renderedHistory = [];
  let panelOpens = 0;
  const requests = [], notices = [], errors = [];
  const elements = { detailsModalEl: new Element(), detailsResolve: new Element(), workspaceCategoriesList: new Element(), detailsMetrics: new Element() };
  const row = { tagName: 'TR', dataset: { status: 'open', scopeKind: 'BUSINESS' } };
  const state = { ticketId, row };
  const context = vm.createContext({ window: {
    setTimeout: callback => { timer = callback; return 1; }, clearTimeout() {},
  }, Set, HTMLElement: Element, console, encodeURIComponent, document: { addEventListener() {}, querySelector: () => null, getElementById: () => null },
    fetch: async (raw, init = {}) => {
      const url = new URL(raw, 'http://localhost');
      assert.equal(url.searchParams.get('ticketId'), ticketId);
      requests.push({ path: url.pathname, ...init });
      if (url.pathname === '/api/dialog-ticket/categories') {
        serverCategories = JSON.parse(init.body).categories;
        return { ok: true, json: async () => ({ success: true, categories: serverCategories }) };
      }
      if (url.pathname === '/api/dialog-ticket/resolve') {
        assert.deepEqual(JSON.parse(init.body).categories, serverCategories);
        serverStatus = 'resolved';
        serverHistory = [{ sender: 'system', messageType: 'system_notification', message: 'Диалог закрыт. Спасибо за обращение!' }];
        return { ok: true, json: async () => ({ success: true, updated: true }) };
      }
      assert.equal(url.pathname, '/api/dialog-ticket');
      return { ok: true, json: async () => ({ summary: {
        ticketId, scopeKind: 'BUSINESS', status: serverStatus, categories: 'Stale header label',
        ...summaryOverrides,
      }, categories: serverCategories, history: serverHistory }) };
    },
  });
  for (const file of ['dialogs-templates-runtime.js', 'dialogs-details-runtime.js', 'dialogs-actions-runtime.js']) {
    vm.runInContext(fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js', file), 'utf8'), context);
  }
  const common = { elements, getActiveDialogState: () => state, getSelectedCategories: () => selected,
    setSelectedCategories: categories => { selected = categories; }, formatCategoriesLabel: categories => categories.join(', '),
    showNotification: (message, type) => notices.push({ message, type }),
  };
  const templates = context.window.DialogsTemplatesRuntime.createRuntime({ ...common,
    getDialogState: () => ({ activeDialogTicketId: ticketId }),
  });
  const details = context.window.DialogsDetailsRuntime.createRuntime({ ...common,
    syncCategorySelections: templates.syncCategorySelections,
    renderHistory: history => { renderedHistory = history; },
    debugLog: (name, value) => { if (name === 'openDialogDetails.catch') errors.push(value); },
  });
  const actions = context.window.DialogsActionsRuntime.createRuntime({ ...common,
    resolveDetailsTicketId: () => ticketId, canRunAction: () => true,
    openCategoryPanel: () => { panelOpens++; },
    openDialogDetails: details.openDialogDetails,
  });
  templates.bindTemplateEvents(); actions.bindDetailsQuickActions();
  return {
    select(category) {
      elements.workspaceCategoriesList.listeners.click({ target: { closest: () => ({ dataset: { categoryValue: category } }) } });
    },
    save: () => timer(), reopen: () => details.openDialogDetails(ticketId, row),
    close: () => elements.detailsResolve.listeners.click(), requests, notices, errors,
    selected: () => [...selected], panelOpens: () => panelOpens,
    metrics: () => elements.detailsMetrics.innerHTML,
    history: () => renderedHistory,
  };
}

test('saved BUSINESS categories survive reopening and are sent unchanged by the close button', async () => {
  const ui = fixture();
  ui.select('Консультация'); ui.select('Настройка, оборудование');
  await ui.save(); await ui.reopen();
  assert.deepEqual(ui.selected(), ['Консультация', 'Настройка, оборудование']);
  await ui.close();
  assert.equal(ui.requests.filter(request => request.path === '/api/dialog-ticket/resolve').length, 1);
  assert.equal(ui.panelOpens(), 0);
  assert.deepEqual(ui.errors, []);
  assert.equal(ui.notices.some(notice => notice.type === 'error'), false);
  assert.equal(ui.history().length, 1);
  assert.equal(ui.history()[0].message, 'Диалог закрыт. Спасибо за обращение!');
});

for (const [name, dates, expected] of [
  ['PostgreSQL UTC microseconds', { createdAt: '2026-11-18 11:02:00.123456+00', resolvedAt: '2026-11-18 15:33:00.123456+00' }, '4 ч 31 мин'],
  ['PostgreSQL short positive offset', { createdAt: '2026-11-18 11:02:00+03', resolvedAt: '2026-11-18 15:33:00+03' }, '4 ч 31 мин'],
  ['different zones', { createdAt: '2026-11-18 11:02:00+03', resolvedAt: '2026-11-18T12:33:00Z' }, '4 ч 31 мин'],
  ['compact offsets', { createdAt: '2026-11-18T11:02:00+0300', resolvedAt: '2026-11-18T15:33:00+0300' }, '4 ч 31 мин'],
  ['timestamps without zone', { createdAt: '2026-11-18 11:02:00', resolvedAt: '2026-11-18T15:33:00Z' }, '4 ч 31 мин'],
  ['separate date and time', { createdDate: '2026-11-18', createdTime: '11:02', resolvedAt: '2026-11-18T15:33:00Z' }, '4 ч 31 мин'],
  ['localized separate date and time', { createdDate: '18.11.2026', createdTime: '11:02', resolvedAt: '2026-11-18T15:33:00Z' }, '4 ч 31 мин'],
  ['date without time', { createdDate: '2026-11-18', resolvedAt: '2026-11-18T01:30:00Z' }, '1 ч 30 мин'],
  ['snake case fields', { created_at: '2026-11-18 11:02:00+03', resolved_at: '2026-11-18 15:33:00+03' }, '4 ч 31 мин'],
  ['epoch seconds', { createdAt: '1794999720', resolvedAt: '1795015980' }, '4 ч 31 мин'],
  ['epoch zero', { createdAt: '1970-01-01T00:00:00Z', resolvedAt: '1970-01-01T00:02:00Z' }, '2 мин'],
  ['missing creation time', { resolvedAt: '2026-11-18T15:33:00Z' }, '—'],
  ['closed without resolution time', { createdAt: '2026-11-18T11:02:00Z' }, '—'],
]) {
  test(`closed dialog metrics: ${name}`, async () => {
    const ui = fixture(['Консультация'], { status: 'resolved', ...dates });
    await ui.reopen();
    assert.deepEqual(ui.errors, []);
    assert.match(ui.metrics(), new RegExp(`Время обращения</span>\\s*<span[^>]*>${expected}</span>`));
  });
}

test('open dialog duration uses the current time even when a previous resolution time exists', async () => {
  const ui = fixture(['Консультация'], { status: 'open', createdAt: new Date(Date.now() - 90 * 60000).toISOString(), resolvedAt: '2026-01-01T00:00:00Z' });
  await ui.reopen();
  assert.deepEqual(ui.errors, []);
  assert.match(ui.metrics(), /Время обращения<\/span>\s*<span[^>]*>1 ч 30 мин<\/span>/);
});

test('an explicitly empty category array still prevents closing despite a stale header label', async () => {
  const ui = fixture([]);
  await ui.reopen(); await ui.close();
  assert.deepEqual(ui.selected(), []);
  assert.equal(ui.requests.filter(request => request.path === '/api/dialog-ticket/resolve').length, 0);
  assert.equal(ui.panelOpens(), 1);
  assert.equal(ui.notices.some(notice => notice.message.includes('категорию')), true);
  assert.deepEqual(ui.errors, []);
});
