'use strict';
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');

const runtime = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/dialogs-details-runtime.js'), 'utf8');
const template = fs.readFileSync(path.resolve(__dirname, '../../main/resources/templates/dialogs/index.html'), 'utf8');
const name = 'buildDialogDetailsApiUrl';
const start = runtime.indexOf(`function ${name}(`);
assert.ok(start >= 0, 'missing query API URL builder');
const begin = runtime.indexOf('{', start);
let depth = 0;
let end = -1;
for (let index = begin; index < runtime.length; index += 1) {
  if (runtime[index] === '{') depth += 1;
  if (runtime[index] === '}' && --depth === 0) {
    end = index;
    break;
  }
}
assert.ok(end > begin, 'unbalanced URL builder');
const buildUrl = vm.runInNewContext(runtime.slice(start, end + 1) + `\n${name}`, { encodeURIComponent });
const exactId = ' T/100?&%# Ю ';
const exactUrl = '/api/dialog-ticket?ticketId=%20T%2F100%3F%26%25%23%20%D0%AE%20';
const withChannel = (url, channelId) => `${url}&channelId=${encodeURIComponent(channelId)}`;

assert.equal(buildUrl(exactId, null), exactUrl);
assert.equal(buildUrl(exactId, 81, withChannel), exactUrl + '&channelId=81');
assert.equal(buildUrl('T-100', null), '/api/dialog-ticket?ticketId=T-100');
assert.equal(buildUrl(' A+B ', null), '/api/dialog-ticket?ticketId=%20A%2BB%20');
assert.match(runtime, /const url = buildDialogDetailsApiUrl\(ticketId, activeState\.channelId, options\.withChannelParam\)/);
assert.match(template, /dialogs-details-runtime\.js\(v='20261023-neutral-ops-r10'\)/);
console.log('DIALOG_DETAILS_QUERY_API_RESULT=GREEN');
