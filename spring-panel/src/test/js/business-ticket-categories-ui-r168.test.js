'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

class Element {
  constructor() { this.children = []; this.textContent = ''; }
  replaceChildren() { this.children = []; }
  appendChild(child) { this.children.push(child); }
  set innerHTML(value) { throw new Error(`Unsafe category HTML: ${value}`); }
}
async function main() {
  const source = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/business-dialogs.js'), 'utf8');
  const template = fs.readFileSync(path.resolve(__dirname, '../../main/resources/templates/businesses/dialogs.html'), 'utf8');
  for (const selector of ['businessTicketCategoryFilter', 'businessCategorySummaryRows', 'data-business-category-status', 'data-ticket-categories'])
    assert.ok(template.includes(selector), selector);
  const paramsStart = source.indexOf('  function ticketParams(');
  const paramsEnd = source.indexOf('  async function exportTickets(', paramsStart);
  const filters = { URLSearchParams, categoryFilter: { value: " O'Reilly %_😀 " },
    statusFilter: { value: 'open' }, ticketFilter: { value: ' raw ' }, textFilter: { value: ' query ' } };
  const ticketParams = vm.runInNewContext(`${source.slice(paramsStart, paramsEnd)}\nticketParams`, filters);
  assert.equal(ticketParams('all').get('category'), " O'Reilly %_😀 ");
  assert.equal(ticketParams('all').get('ticket'), ' raw ');
  assert.equal(ticketParams('all').get('q'), 'query');
  filters.categoryFilter.value = '';
  assert.equal(ticketParams('all').has('category'), false);

  const start = source.indexOf('  async function loadCategorySummary(');
  const end = source.indexOf('  async function loadAnswers(', start);
  assert.ok(start > 0 && end > start);
  const pending = [], categoryRows = new Element(), categoryStatus = new Element();
  const context = { categoryRows, categoryStatus, generation: 1, select: { value: '11' }, encodeURIComponent,
    fetch: (url, options) => new Promise(resolve => pending.push({ url, options, resolve })),
    cell(row, value) { const item = new Element(); item.textContent = String(value); row.appendChild(item); return item; },
    document: { createElement: () => new Element() } };
  const load = vm.runInNewContext(`${source.slice(start, end)}\nloadCategorySummary`, context);
  const first = load('11', 1);
  const request = pending.shift();
  assert.equal(request.url, '/api/business-scope/tickets/categories?business_id=11');
  assert.equal(request.options.credentials, 'same-origin');
  assert.equal(request.options.cache, 'no-store');
  request.resolve({ ok: true, json: async () => [{ businessId: 11, businessName: '<script>Business</script>', category: '<img src=x>', total: 2 }] });
  await first;
  assert.equal(categoryRows.children[0].children[0].textContent, '<script>Business</script>');
  assert.equal(categoryRows.children[0].children[1].textContent, '<img src=x>');
  assert.equal(categoryRows.children[0].children[2].textContent, '2');
  assert.match(categoryStatus.textContent, /несколько категорий/);

  const old = load('11', 1), oldRequest = pending.shift();
  context.generation = 2; context.select.value = '22';
  const current = load('22', 2);
  pending.shift().resolve({ ok: true, json: async () => [{ businessName: 'New', category: 'Visible', total: 1 }] });
  await current;
  oldRequest.resolve({ ok: true, json: async () => [{ businessName: 'Old', category: 'Secret', total: 9 }] });
  await old;
  assert.equal(categoryRows.children[0].children[1].textContent, 'Visible');
  const tooLarge = load('22', 2);
  pending.shift().resolve({ ok: false, status: 413 }); await tooLarge;
  assert.equal(categoryRows.children.length, 0);
  assert.match(categoryStatus.textContent, /Выберите отдельный бизнес/);
  const empty = load('22', 2);
  pending.shift().resolve({ ok: true, json: async () => [] }); await empty;
  assert.match(categoryStatus.textContent, /пока не заданы/);
  const denied = load('22', 2);
  pending.shift().resolve({ ok: false, status: 403 }); await denied;
  assert.equal(categoryRows.children.length, 0);
  assert.match(categoryStatus.textContent, /недоступна/);
  console.log('BUSINESS_TICKET_CATEGORIES_UI_R168_RESULT=GREEN');
}
main().catch(error => { console.error(error); process.exitCode = 1; });
