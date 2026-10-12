'use strict';
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');

const runtime = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/dialogs-details-history-runtime.js'), 'utf8');
const template = fs.readFileSync(path.resolve(__dirname, '../../main/resources/templates/dialogs/index.html'), 'utf8');
const name = 'buildPreviousDialogHistoryApiUrl';
const start = runtime.indexOf(`function ${name}(`);
assert.ok(start > 0, 'missing previous-history query builder');
const begin = runtime.indexOf('{', start);
let depth = 0;
let end = -1;
for (let index = begin; index < runtime.length; index += 1) {
  if (runtime[index] === '{') depth += 1;
  if (runtime[index] === '}' && --depth === 0) { end = index; break; }
}
assert.ok(end > begin, 'unbalanced previous-history query builder');
const buildUrl = vm.runInNewContext(runtime.slice(start, end + 1) + `\n${name}`, { encodeURIComponent });
const exactId = ' T/100?&%# Ю ';
const exactUrl = '/api/dialog-ticket/history/previous?ticketId=%20T%2F100%3F%26%25%23%20%D0%AE%20&offset=20';

assert.equal(buildUrl(exactId, 20), exactUrl);
assert.equal(buildUrl(' null ', 0), '/api/dialog-ticket/history/previous?ticketId=%20null%20&offset=0');
assert.equal(buildUrl('A+B', 1), '/api/dialog-ticket/history/previous?ticketId=A%2BB&offset=1');
assert.match(runtime, /fetch\(buildPreviousDialogHistoryApiUrl\(activeDialogState\.ticketId, state\.previousNextOffset\)/);
assert.match(template, /dialogs-details-history-runtime\.js\(v='20261012-01-278-r56'\)/);
console.log('DIALOG_PREVIOUS_HISTORY_QUERY_API_R52_RESULT=GREEN');
