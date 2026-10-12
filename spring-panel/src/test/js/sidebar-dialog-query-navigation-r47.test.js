'use strict';
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');

const source = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/sidebar.js'), 'utf8');
const start = source.indexOf('function navigateNotification(');
assert.ok(start >= 0);
const begin = source.indexOf('{', start);
let depth = 0;
let end = -1;
for (let i = begin; i < source.length; i++) {
  if (source[i] === '{') depth++;
  if (source[i] === '}' && --depth === 0) {
    end = i + 1;
    break;
  }
}
assert.ok(end > begin);

function navigateOn(pathname, open) {
  const calls = [];
  const location = { pathname, origin: 'https://panel.test', href: '' };
  const window = {
    location,
    history: { pushState: (...args) => calls.push(args) },
    openDialogDetailsByTicketId: open ? id => calls.push(['open', id]) : undefined,
    open: () => { throw new Error('unexpected external navigation'); },
  };
  const fn = vm.runInNewContext(source.slice(start, end) + '\nnavigateNotification', {
    window, URL, decodeURIComponent, encodeURIComponent,
  });
  return { fn, location, calls };
}

const exactId = ' T/100?&%# Ю ';
const target = '/dialogs?ticketId=%20T%2F100%3F%26%25%23%20%D0%AE%20';
const outside = navigateOn('/tasks', false);
outside.fn(target);
assert.equal(outside.location.href, target);

const inside = navigateOn('/dialogs', true);
inside.fn(target);
assert.equal(inside.calls[0][2], target);
assert.equal(inside.calls[1][1], exactId);

const unrelated = navigateOn('/tasks', false);
unrelated.fn('/dialogs?notTicketId=NOT-A-TICKET');
assert.equal(unrelated.location.href, '/dialogs?notTicketId=NOT-A-TICKET');
console.log('SIDEBAR_DIALOG_QUERY_NAVIGATION_GREEN=3');
