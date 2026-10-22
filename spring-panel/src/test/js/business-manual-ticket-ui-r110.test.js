'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

async function main() {
  const source = fs.readFileSync(path.resolve(__dirname,
    '../../main/resources/static/js/business-dialogs.js'), 'utf8').replace(/\r\n?/g, '\n');
  const start = source.indexOf('  if (manualForm) {\n    manualForm.addEventListener');
  const end = source.indexOf("  getJson('/api/business-scope/businesses')", start);
  assert.ok(start >= 0 && end > start, 'manual ticket form handler not found');

  const submit = { disabled: false };
  const client = { value: 'Анна' };
  const problem = { value: 'Нужна помощь' };
  let handler;
  let reloads = 0;
  let request;
  const manualForm = {
    reportValidity: () => true,
    addEventListener: (name, callback) => { assert.equal(name, 'submit'); handler = callback; },
    querySelector: selector => ({
      'button[type="submit"]': submit, '#manualTicketClient': client,
      '#manualTicketProblem': problem
    })[selector]
  };
  const manualBusiness = { value: '' };
  const manualStatus = { textContent: '' };
  const select = { value: 'all' };
  const context = {
    manualForm, manualBusiness, manualStatus, select, csrf: 'csrf-test', offset: 50,
    fetch: async (url, options) => {
      request = { url, options };
      return { ok: true, json: async () => ({
        ticketId: '<script>raw-id</script>', clientUserId: -9007199254740991,
        businessId: 2
      }) };
    },
    loadTickets: async () => { reloads++; }
  };
  vm.runInNewContext(source.slice(start, end), context);

  await handler({ preventDefault() {} });
  assert.equal(request, undefined, 'business must be chosen explicitly');
  manualBusiness.value = '2';
  await handler({ preventDefault() {} });
  assert.equal(request.url, '/api/business-scope/tickets/manual');
  assert.equal(request.options.method, 'POST');
  assert.equal(request.options.credentials, 'same-origin');
  assert.equal(request.options.headers['X-XSRF-TOKEN'], 'csrf-test');
  assert.deepEqual(JSON.parse(request.options.body), {
    businessId: 2, clientName: 'Анна', problem: 'Нужна помощь'
  });
  assert.equal(select.value, '2');
  assert.equal(context.offset, 0);
  assert.equal(reloads, 1);
  assert.equal(client.value, '');
  assert.equal(problem.value, '');
  assert.match(manualStatus.textContent, /Локальный ID клиента/);
  assert.equal(submit.disabled, false);
  console.log('BUSINESS_MANUAL_TICKET_UI_R110_RESULT=GREEN');
}

main().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
