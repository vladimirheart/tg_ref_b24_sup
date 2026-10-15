'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

class Element {
  constructor() {
    this.children = [];
    this.textContent = '';
  }
  replaceChildren() { this.children = []; }
  appendChild(child) { this.children.push(child); }
}

async function main() {
  const source = fs.readFileSync(path.resolve(__dirname,
    '../../main/resources/static/js/business-dialogs.js'), 'utf8');
  const start = source.indexOf('  async function loadSpans(');
  const end = source.indexOf('  async function openCard(', start);
  assert.ok(start >= 0 && end > start);

  const ticket = { ticketPublicId: '0123456789abcdef0123456789abcdef',
    businessId: 2, userId: 11, ticketId: 'raw' };
  const pending = [];
  const spans = new Element();
  const spansStatus = new Element();
  const context = {
    spans, spansStatus, document: { createElement: () => new Element() },
    getJson: url => new Promise((resolve, reject) => pending.push({ url, resolve, reject })),
    encodeURIComponent, ticketTime: value => value || '—',
    generation: 1, cardRequest: 7, currentTicketPublicId: ticket.ticketPublicId,
    select: { value: '2' }
  };
  const loadSpans = vm.runInNewContext(`${source.slice(start, end)}\nloadSpans`, context);

  const first = loadSpans(ticket, 1, 7);
  assert.match(pending[0].url, /\/spans$/);
  pending.shift().resolve({ ticket, relationVerified: true, spans: [
    { spanNo: 1, startedAt: '<script>start</script>', endedAt: null, durationSeconds: 0 }
  ] });
  await first;
  assert.equal(spans.children.length, 1);
  assert.equal(spans.children[0].textContent,
    'Интервал 1: <script>start</script> — —; длительность 0 с');

  const stale = loadSpans(ticket, 1, 7);
  context.generation = 2;
  spansStatus.textContent = 'Новый контекст';
  pending.shift().resolve({ ticket, relationVerified: true, spans: [] });
  await stale;
  assert.equal(spansStatus.textContent, 'Новый контекст');

  const ambiguous = loadSpans(ticket, 2, 7);
  pending.shift().resolve({ ticket, relationVerified: false, spans: [] });
  await ambiguous;
  assert.equal(spans.children.length, 0);
  assert.match(spansStatus.textContent, /не имеет однозначной связи/);

  const mismatched = loadSpans(ticket, 2, 7);
  pending.shift().resolve({ ticket: { ...ticket, businessId: 3 }, relationVerified: true,
    spans: [{ spanNo: 9, startedAt: 'foreign', endedAt: null, durationSeconds: 1 }] });
  await mismatched;
  assert.equal(spans.children.length, 0);
  assert.match(spansStatus.textContent, /обращение изменилось/);

  const empty = loadSpans(ticket, 2, 7);
  pending.shift().resolve({ ticket, relationVerified: true, spans: [] });
  await empty;
  assert.match(spansStatus.textContent, /пока нет/);

  const failed = loadSpans(ticket, 2, 7);
  pending.shift().reject(new Error('limit'));
  await failed;
  assert.equal(spans.children.length, 0);
  assert.match(spansStatus.textContent, /недоступны/);
  console.log('BUSINESS_TICKET_SPANS_UI_R107_RESULT=GREEN');
}

main().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
