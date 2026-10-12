'use strict';
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');

const runtime = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/dialogs-details-history-runtime.js'), 'utf8');
const name = 'buildDialogHistoryApiUrl';
const start = runtime.indexOf('function ' + name + '(');
assert.ok(start > 0, 'missing history query URL builder');
const begin = runtime.indexOf('{', start);
let depth = 0;
let end = -1;
for (let index = begin; index < runtime.length; index++) {
  if (runtime[index] === '{') depth++;
  if (runtime[index] === '}' && --depth === 0) { end = index; break; }
}
assert.ok(end > begin, 'unbalanced history query builder');
const buildUrl = vm.runInNewContext(runtime.slice(start, end + 1) + '\n' + name, { encodeURIComponent });
const exactId = ' T/100?&%# Ю ';
const exactUrl = '/api/dialog-ticket/history?ticketId=%20T%2F100%3F%26%25%23%20%D0%AE%20';
const withChannel = (url, channelId) => url + '&channelId=' + encodeURIComponent(channelId);
assert.equal(buildUrl(exactId, null), exactUrl);
assert.equal(buildUrl(exactId, 81, withChannel), exactUrl + '&channelId=81');
assert.equal(buildUrl('null', null), '/api/dialog-ticket/history?ticketId=null');
assert.equal(buildUrl(' null ', null), '/api/dialog-ticket/history?ticketId=%20null%20');
assert.equal(buildUrl(' A+B ', null), '/api/dialog-ticket/history?ticketId=%20A%2BB%20');
assert.match(runtime, /const url = buildDialogHistoryApiUrl\(activeDialogState\.ticketId, activeDialogState\.channelId, withChannelParam\);/);
assert.doesNotMatch(runtime, /withChannelParam\(\x60\/api\/dialogs\/\$\{encodeURIComponent\(activeDialogState\.ticketId\)\}\/history/);
console.log('DIALOG_HISTORY_QUERY_API_R51_RESULT=GREEN');
