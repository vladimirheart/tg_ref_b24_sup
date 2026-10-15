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
  appendChild(child) { this.children.push(child); }
  append(...children) { this.children.push(...children); }
  addEventListener() {}
}

function cell(row, value) {
  const item = new Element();
  item.textContent = String(value ?? '');
  row.appendChild(item);
  return item;
}

async function main() {
  const source = fs.readFileSync(path.resolve(__dirname,
    '../../main/resources/static/js/business-dialogs.js'), 'utf8');
  const listStart = source.indexOf('  async function loadTickets() {');
  const listEnd = source.indexOf("  select.addEventListener('change'", listStart);
  const cardStart = source.indexOf('  async function openCard(');
  const cardEnd = source.indexOf('  function historyItem(', cardStart);
  assert.ok(listStart >= 0 && listEnd > listStart && cardStart >= 0 && cardEnd > cardStart);

  const publicId = '0123456789abcdef0123456789abcdef';
  const ticket = { userId: 11, ticketId: 'raw', ticketPublicId: publicId, businessId: 2,
    businessName: 'Суши', clientName: '<script>Иван</script>',
    problemPreview: 'П'.repeat(121), status: 'open' };
  const rows = new Element();
  const listContext = {
    generation: 0, cardRequest: 0, offset: 0, limit: 50, select: { value: 'all' },
    card: new Element(), older: new Element(), previous: new Element(), next: new Element(),
    rows, summaryRows: new Element(), summaryStatus: new Element(), pageNumber: new Element(),
    ticketParams: businessId => new URLSearchParams({ business_id: businessId }),
    getJson: async url => url.includes('/summary?') ? { total: 2, businesses: [] }
      : { tickets: [ticket, { ...ticket, ticketId: 'empty', ticketPublicId: null,
        clientName: null, problemPreview: null }], hasMore: false },
    renderSummary() {}, announce() {}, statusLabel: value => value,
    document: { createElement: () => new Element() }, cell, openCard() {}
  };
  const loadTickets = vm.runInNewContext(`${source.slice(listStart, listEnd)}\nloadTickets`, listContext);
  await loadTickets();
  assert.equal(rows.children.length, 2);
  assert.equal(rows.children[0].children[2].textContent, '<script>Иван</script>');
  assert.equal(rows.children[0].children[3].textContent, `${'П'.repeat(120)}…`);
  assert.equal(rows.children[1].children[2].textContent, 'ID 11');
  assert.equal(rows.children[1].children[3].textContent, '—');

  const fields = new Map();
  const card = new Element();
  card.querySelector = selector => {
    if (!fields.has(selector)) fields.set(selector, new Element());
    return fields.get(selector);
  };
  let snapshot = { ticket, entries: [], hasMore: false, nextBeforeId: null };
  let answersLoaded = 0;
  const cardContext = {
    generation: 4, cardRequest: 0, card, older: new Element(), history: new Element(),
    answers: new Element(), answersStatus: new Element(), select: { value: '2' },
    getJson: async () => snapshot, loadAnswers: () => { answersLoaded++; },
    statusLabel: value => value, announce() {},
    document: { createElement: () => new Element() }, encodeURIComponent
  };
  const openCard = vm.runInNewContext(`${source.slice(cardStart, cardEnd)}\nopenCard`, cardContext);
  await openCard(ticket, 4);
  assert.equal(card.hidden, false);
  assert.equal(fields.get('[data-ticket-client]').textContent, '<script>Иван</script>');
  assert.equal(fields.get('[data-ticket-problem]').textContent, 'П'.repeat(121));
  assert.equal(answersLoaded, 1);

  snapshot = { ...snapshot, ticket: { ...ticket, userId: 99 } };
  await openCard(ticket, 4);
  assert.equal(card.hidden, true);
  assert.equal(answersLoaded, 1);
  console.log('BUSINESS_TICKET_ROOT_PREVIEW_UI_TEST_RESULT=GREEN');
}

main().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
