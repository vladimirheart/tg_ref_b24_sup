'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

async function main() {
  const source = fs.readFileSync(path.resolve(__dirname,
    '../../main/resources/static/js/business-administration.js'), 'utf8');
  const start = source.indexOf("  page.querySelector('#channelBindingForm').addEventListener");
  const end = source.indexOf('  refresh().then(', start);
  assert.ok(start >= 0 && end > start, 'binding form handler not found');

  const controls = new Map([
    ['#channelBindingChannel', { value: '10' }],
    ['#channelBindingBusiness', { value: '7' }],
    ['#channelBindingMode', { value: 'EXPLICIT_SELECTION' }]
  ]);
  let submit;
  const writes = [];
  const previews = [];
  const notices = [];
  let operation;
  vm.runInNewContext(source.slice(start, end), {
    page: { querySelector: selector => selector === '#channelBindingForm'
      ? { addEventListener: (_, handler) => { submit = handler; } }
      : controls.get(selector) },
    channelCatalog: [{ id: 10, channelName: 'Общий' }],
    request: async (...args) => { writes.push(args); },
    showChannelPreview: async channel => { previews.push(channel.id); },
    perform: (action, success) => {
      operation = (async () => { await action(); notices.push(success); })();
      return operation;
    },
    announce: message => { notices.push(message); }
  });
  assert.equal(typeof submit, 'function');
  let prevented = false;
  submit({ preventDefault: () => { prevented = true; } });
  await operation;
  assert.equal(prevented, true);
  assert.equal(JSON.stringify(writes), JSON.stringify([['/channels/10/bindings/7', 'PUT',
    { resolutionMode: 'EXPLICIT_SELECTION' }]]));
  assert.deepEqual(previews, [10]);
  assert.match(notices[0], /Маршрутизация остаётся выключенной/);
  console.log('BUSINESS_CHANNEL_BINDING_UI_R111_RESULT=GREEN');
}

main().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
