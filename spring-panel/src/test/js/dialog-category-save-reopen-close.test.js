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

function fixture(initialCategories = []) {
  const ticketId = ' R/Ю?#+ ';
  let selected = new Set(), serverCategories = initialCategories, timer;
  let panelOpens = 0;
  const requests = [], notices = [], errors = [];
  const elements = { detailsModalEl: new Element(), detailsResolve: new Element(), workspaceCategoriesList: new Element() };
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
        return { ok: true, json: async () => ({ success: true, updated: true }) };
      }
      assert.equal(url.pathname, '/api/dialog-ticket');
      return { ok: true, json: async () => ({ summary: {
        ticketId, scopeKind: 'BUSINESS', status: 'open', categories: 'Stale header label',
      }, categories: serverCategories, history: [] }) };
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
