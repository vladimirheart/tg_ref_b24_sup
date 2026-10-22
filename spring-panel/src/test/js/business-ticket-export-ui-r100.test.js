'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

async function main() {
  const source = fs.readFileSync(path.resolve(__dirname,
    '../../main/resources/static/js/business-dialogs.js'), 'utf8');
  const start = source.indexOf('  function ticketParams(');
  const end = source.indexOf('  function renderSummary(', start);
  assert.ok(start >= 0 && end > start, 'ticket export implementation not found');

  const pending = [];
  const downloads = [];
  const messages = [];
  const exportButton = { disabled: false };
  const select = { value: 'all' };
  const context = {
    URLSearchParams, generation: 1, select, exportButton,
    statusFilter: { value: 'open' }, ticketFilter: { value: 'raw-id' },
    textFilter: { value: '  Иван  ' },
    categoryFilter: { value: 'A%_\'😀' },
    announce: (message, error) => messages.push({ message, error: !!error }),
    fetch: (url, options) => new Promise(resolve => pending.push({ url, options, resolve })),
    URL: {
      createObjectURL: blob => { assert.equal(blob, 'json-blob'); return 'blob:test'; },
      revokeObjectURL: url => assert.equal(url, 'blob:test')
    },
    setTimeout: callback => callback(),
    document: {
      body: { appendChild() {} },
      createElement: () => ({
        click() { downloads.push({ href: this.href, filename: this.download }); },
        remove() {}
      })
    }
  };
  const exportTickets = vm.runInNewContext(
    `let exporting = false;\n${source.slice(start, end)}\nexportTickets`, context);

  const first = exportTickets();
  assert.equal(exportButton.disabled, true);
  const request = pending.shift();
  const params = new URL(request.url, 'http://localhost').searchParams;
  assert.equal(params.get('business_id'), 'all');
  assert.equal(params.get('status'), 'open');
  assert.equal(params.get('ticket'), 'raw-id');
  assert.equal(params.get('q'), 'Иван');
  assert.equal(params.get('category'), 'A%_\'😀');
  assert.equal(params.has('limit'), false);
  assert.equal(request.options.credentials, 'same-origin');
  assert.equal(request.options.cache, 'no-store');
  request.resolve({ ok: true, blob: async () => 'json-blob' });
  await first;
  assert.deepEqual(downloads, [{ href: 'blob:test', filename: 'business-tickets.json' }]);
  assert.equal(exportButton.disabled, false);

  const tooLarge = exportTickets();
  pending.shift().resolve({ ok: false, status: 413 });
  await tooLarge;
  assert.equal(downloads.length, 1);
  assert.match(messages.at(-1).message, /больше 1000/);
  assert.equal(messages.at(-1).error, true);

  const stale = exportTickets();
  context.generation = 2;
  pending.shift().resolve({ ok: true, blob: async () => 'json-blob' });
  await stale;
  assert.equal(downloads.length, 1);
  assert.equal(exportButton.disabled, false);
  console.log('BUSINESS_TICKET_EXPORT_UI_TEST_RESULT=GREEN');
}

main().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
