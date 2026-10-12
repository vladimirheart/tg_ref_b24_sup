'use strict';
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');

const jsRoot = path.resolve(__dirname, '../../main/resources/static/js');
const read = file => fs.readFileSync(path.join(jsRoot, file), 'utf8').replace(/\r\n/g, '\n');
const dialogs = read('dialogs.js');
const workspace = read('dialogs-workspace-runtime.js');
const flow = read('dialogs-flow-runtime.js');
const locationBusiness = read('dialogs-location-business-runtime.js');
const profile = fs.readFileSync(path.resolve(__dirname, '../../main/resources/templates/clients/profile.html'), 'utf8');
const exactId = ' T/100?&%# Ю ';
const target = '/dialogs?ticketId=%20T%2F100%3F%26%25%23%20%D0%AE%20';

function sourceFunction(source, name) {
  const start = source.indexOf('function ' + name + '(');
  assert.ok(start >= 0, 'missing ' + name);
  const begin = source.indexOf('{', start);
  let depth = 0;
  for (let i = begin; i < source.length; i++) {
    if (source[i] === '{') depth++;
    if (source[i] === '}' && --depth === 0) return source.slice(start, i + 1);
  }
  throw new Error('unbalanced ' + name);
}

function evaluate(source, name, context = {}, dependencies = []) {
  const body = dependencies.map(dependency => sourceFunction(source, dependency)).join('\n');
  return vm.runInNewContext(body + '\n' + sourceFunction(source, name) + '\n' + name, {
    URLSearchParams, encodeURIComponent, decodeURIComponent, ...context,
  });
}

const buildListRoute = evaluate(dialogs, 'buildWorkspaceDialogUrl', {
  withChannelParam: (url, channel) => channel ? url + '&channelId=' + encodeURIComponent(channel) : url,
});
assert.equal(buildListRoute(exactId, null), target);
assert.equal(buildListRoute(exactId, 7), target + '&channelId=7');
assert.match(dialogs, /const openHref = buildWorkspaceDialogUrl\(ticketId, channelId\)/);

const getRouteTicketId = evaluate(dialogs, 'getRouteTicketId', {}, ['exactLegacyTicketId']);
assert.equal(getRouteTicketId('/dialogs', '?ticketId=' + encodeURIComponent(exactId)), exactId);
assert.equal(getRouteTicketId('/dialogs/T-OLD', ''), 'T-OLD');
assert.equal(getRouteTicketId('/dialogs', '?view=all'), '');
const isWorkspaceDialogPath = evaluate(dialogs, 'isWorkspaceDialogPath', {}, ['exactLegacyTicketId', 'getRouteTicketId']);
assert.equal(isWorkspaceDialogPath('/dialogs', '?ticketId=' + encodeURIComponent(exactId)), true);
assert.equal(isWorkspaceDialogPath('/dialogs', '?view=all'), false);
const listUrl = evaluate(dialogs, 'buildDialogsListUrl', {
  window: { location: { pathname: '/dialogs', search: '?ticketId=' + encodeURIComponent(exactId), hash: '' } },
}, ['exactLegacyTicketId', 'getRouteTicketId']);
assert.equal(listUrl(), '/dialogs');

const buildWorkspaceRoute = evaluate(workspace, 'buildWorkspaceDialogUrl', {
  options: { withChannelParam: (url, channel) => channel ? url + '&channelId=' + channel : url },
});
assert.equal(buildWorkspaceRoute(exactId, null), target);
assert.equal(buildWorkspaceRoute(exactId, 7), target + '&channelId=7');

const parseFlowRoute = evaluate(flow, 'parseDialogRouteTicketId', {
  window: { location: { pathname: '/dialogs', search: '?ticketId=' + encodeURIComponent(exactId) } },
  isDialogDetailsPath: () => true,
});
assert.equal(parseFlowRoute('/dialogs'), exactId);
assert.equal(parseFlowRoute('/dialogs/T-OLD'), 'T-OLD');

const resolveLocationTicket = evaluate(locationBusiness, 'resolveTicketId', {
  window: { location: { pathname: '/dialogs', search: '?ticketId=' + encodeURIComponent(exactId), origin: 'https://panel.test' } },
  document: { getElementById: () => null },
});
assert.equal(resolveLocationTicket(), exactId);
assert.match(profile, /openDialog\.href = `\/dialogs\?ticketId=\$\{encodeURIComponent\(ticketId\)\}/);
console.log('DIALOG_QUERY_NAVIGATION_RESULT=GREEN');
