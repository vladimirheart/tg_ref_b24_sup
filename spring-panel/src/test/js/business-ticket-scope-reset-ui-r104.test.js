'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

class Element {
  constructor() {
    this.children = [];
    this.textContent = '';
    this.hidden = false;
    this.disabled = false;
  }
  replaceChildren() { this.children = []; }
  prepend(...items) { this.children.unshift(...items); }
  removeAttribute(name) { delete this[name]; }
}

async function main() {
  const source = fs.readFileSync(path.resolve(__dirname,
    '../../main/resources/static/js/business-dialogs.js'), 'utf8');
  const clearStart = source.indexOf('  function clearCard() {');
  const clearEnd = source.indexOf('  function announce(', clearStart);
  const olderStart = source.indexOf('  async function loadOlderHistory() {');
  const olderEnd = source.indexOf('  async function loadTickets() {', olderStart);
  const jsonStart = source.indexOf('  async function getJson(');
  const jsonEnd = source.indexOf('  function cell(', jsonStart);
  assert.ok(clearStart >= 0 && clearEnd > clearStart && olderStart >= 0 && olderEnd > olderStart
    && jsonStart >= 0 && jsonEnd > jsonStart);

  let fetchOptions;
  const getJson = vm.runInNewContext(`${source.slice(jsonStart, jsonEnd)}\ngetJson`, {
    fetch: async (_url, options) => {
      fetchOptions = options;
      return { ok: true, json: async () => ({ tickets: [] }) };
    }
  });
  await getJson('/api/business-scope/tickets/page?business_id=2');
  assert.equal(fetchOptions.cache, 'no-store');

  const publicId = '0123456789abcdef0123456789abcdef';
  const ticket = { ticketPublicId: publicId, businessId: 2, userId: 11, ticketId: 'raw' };
  const fields = new Map();
  const card = new Element();
  card.querySelector = selector => {
    if (!fields.has(selector)) fields.set(selector, new Element());
    return fields.get(selector);
  };
  const history = new Element();
  const answers = new Element();
  const workspace = new Element();
  const spans = new Element();
  const older = new Element();
  const messages = [];
  const pending = [];
  const context = {
    card, history, answers, workspace, spans, older, answersStatus: new Element(),
    workspaceStatus: new Element(), spansStatus: new Element(),
    currentTicket: ticket, currentTicketPublicId: publicId,
    historyBefore: 9, olderLoading: false, generation: 3, cardRequest: 4,
    select: { value: 'all' }, encodeURIComponent,
    getJson: () => new Promise(resolve => pending.push(resolve)),
    historyItem: entry => ({ id: entry.id }),
    announce: message => messages.push(message)
  };
  const { clearCard, loadOlderHistory } = vm.runInNewContext(
    `${source.slice(clearStart, clearEnd)}\n${source.slice(olderStart, olderEnd)}\n({ clearCard, loadOlderHistory })`,
    context);

  const success = loadOlderHistory();
  pending.shift()({ ticket, entries: [{ id: 5 }], nextBeforeId: 5, hasMore: true });
  await success;
  assert.equal(history.children[0].id, 5);
  assert.equal(context.historyBefore, 5);
  assert.equal(older.hidden, false);

  answers.children.push({ textContent: 'старый ответ' });
  workspace.children.push({ textContent: 'старые рабочие данные' });
  context.workspaceStatus.textContent = 'старый статус';
  spans.children.push({ textContent: 'старый интервал' });
  context.spansStatus.textContent = 'старый статус интервала';
  card.querySelector('[data-ticket-client]').textContent = 'старый клиент';
  card.querySelector('[data-ticket-resolved-by]').textContent = 'старый оператор';
  card.querySelector('[data-ticket-resolved-at]').textContent = 'старое время';
  for (const selector of ['[data-ticket-ai]', '[data-ticket-assignment]']) {
    const link = card.querySelector(selector);
    link.href = '/stale-target';
    link.hidden = false;
  }
  const mismatch = loadOlderHistory();
  pending.shift()({ ticket: { ...ticket, userId: 22 }, entries: [{ id: 4 }],
    nextBeforeId: 4, hasMore: false });
  await mismatch;
  assert.equal(card.hidden, true);
  assert.equal(history.children.length, 0);
  assert.equal(answers.children.length, 0);
  assert.equal(workspace.children.length, 0);
  assert.equal(context.workspaceStatus.textContent, '');
  assert.equal(spans.children.length, 0);
  assert.equal(context.spansStatus.textContent, '');
  assert.equal(fields.get('[data-ticket-client]').textContent, '');
  assert.equal(fields.get('[data-ticket-resolved-by]').textContent, '');
  assert.equal(fields.get('[data-ticket-resolved-at]').textContent, '');
  for (const selector of ['[data-ticket-ai]', '[data-ticket-assignment]']) {
    assert.equal(fields.get(selector).hidden, true);
    assert.equal(fields.get(selector).href, undefined);
  }
  assert.equal(context.currentTicket, null);
  assert.equal(context.currentTicketPublicId, null);
  assert.match(messages.at(-1), /История недоступна/);

  context.currentTicket = ticket;
  context.currentTicketPublicId = publicId;
  context.historyBefore = 5;
  card.hidden = false;
  const stale = loadOlderHistory();
  context.generation++;
  clearCard();
  pending.shift()({ ticket, entries: [{ id: 3 }], nextBeforeId: null, hasMore: false });
  await stale;
  assert.equal(card.hidden, true);
  assert.equal(history.children.length, 0);
  assert.equal(context.currentTicket, null);
  assert.equal(messages.length, 1);
  console.log('BUSINESS_TICKET_SCOPE_RESET_UI_R104_RESULT=GREEN');
}

main().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
