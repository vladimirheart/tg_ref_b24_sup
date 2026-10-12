'use strict';
// R56: actual media function and location editor callbacks, without browser/network.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const jsRoot = path.resolve(__dirname, '../../main/resources/static/js');
const read = file => fs.readFileSync(path.join(jsRoot, file), 'utf8').replace(/\r\n/g, '\n');
const exactId = ' T/100?&%#+ Ю ';
const history = read('dialogs-details-history-runtime.js');

function functionSource(text, name) {
  const at = text.indexOf('function ' + name + '(');
  assert.ok(at >= 0, 'missing ' + name);
  const start = text.slice(at - 6, at) === 'async ' ? at - 6 : at;
  let depth = 0;
  let parametersEnd = text.indexOf('(', at);
  for (; parametersEnd < text.length; parametersEnd++) {
    if (text[parametersEnd] === '(') depth++;
    if (text[parametersEnd] === ')' && --depth === 0) break;
  }
  const open = text.indexOf('{', parametersEnd);
  for (let i = open; i < text.length; i++) {
    if (text[i] === '{') depth++;
    if (text[i] === '}' && --depth === 0) return text.slice(start, i + 1);
  }
  throw new Error('unbalanced ' + name);
}
function assertQuery(url, action, ticketId = exactId) {
  const parsed = new URL(url, 'http://localhost');
  assert.equal(parsed.pathname, '/api/dialog-ticket/' + action);
  assert.equal(parsed.searchParams.get('ticketId'), ticketId);
}
class Form {
  constructor() { this.parts = []; }
  append(name, value, filename) { this.parts.push({ name, value, filename }); }
}
async function mediaRequests() {
  const requests = [], rendered = [], errors = [], cleared = [];
  const state = { ticketId: exactId, context: {} };
  const fileA = { name: 'image+ Ю.png', type: 'image/png', bytes: Uint8Array.from([0, 255, 13, 10]) };
  const fileB = { name: 'video.mp4', type: 'video/mp4', bytes: Uint8Array.from([3, 2, 1]) };
  const button = { disabled: false }, input = {};
  let failed = false, completed = 0;
  const context = {
    FormData: Form, elements: {}, getActiveDialogState: () => state,
    getActiveWorkspaceState: () => ({ ticketId: 'TRIMMED-SIBLING' }),
    resolvePendingMediaFileName: file => file.name,
    appendHistoryMessage: message => rendered.push(message),
    clearPendingMediaFiles: value => cleared.push(value),
    notify: message => errors.push(message), options: {},
    fetch: async (url, init) => {
      requests.push({ url, ...init });
      return { ok: !failed, status: failed ? 403 : 200, text: async () => JSON.stringify(failed
        ? { success: false, error: 'denied' }
        : { success: true, attachment: 'unchanged/key', messageType: 'operator_media', message: 'caption' }) };
    },
  };
  const send = vm.runInNewContext(functionSource(history, 'exactLegacyTicketId') + '\n'
    + functionSource(history, 'sendMediaFiles') + '\nsendMediaFiles;', context);
  assert.equal(await send([fileA, fileB], {
    caption: ' caption ', replyToTelegramId: 123, sendButton: button, mediaInput: input,
    afterSuccess: () => { completed++; },
  }), true);
  assert.equal(requests.length, 2);
  const common = read('common.js');
  const attachCsrf = vm.runInNewContext(
    ['getCookieValue', 'getCsrfTokenFromDom', 'attachCsrfToken'].map(name => functionSource(common, name)).join('\n')
      + '\nattachCsrfToken;', { Headers, document: { cookie: '', querySelector: () => ({ content: 'csrf-value' }) } });
  for (const [index, file] of [fileA, fileB].entries()) {
    const request = requests[index];
    assertQuery(request.url, 'media');
    assert.equal(request.method, 'POST');
    assert.equal(request.headers, undefined); // browser creates multipart boundary
    assert.equal(request.credentials, undefined); // existing same-origin default
    const secured = attachCsrf(request);
    assert.equal(secured.headers.get('X-XSRF-TOKEN'), 'csrf-value');
    assert.equal(secured.headers.get('Content-Type'), null);
    assert.equal(secured.credentials, 'same-origin');
    assert.strictEqual(secured.body, request.body);
    assert.strictEqual(request.body.parts[0].value, file);
    assert.equal(request.body.parts[0].filename, file.name);
    assert.deepEqual(request.body.parts.slice(1).map(part => [part.name, part.value]),
      [['message', 'caption'], ['replyToTelegramId', '123']]);
  }
  assert.equal(completed, 1);
  assert.deepEqual(cleared, [input]);
  assert.equal(rendered.length, 2);
  assert.equal(rendered[0].attachment, 'unchanged/key');
  assert.equal(button.disabled, false);
  assert.deepEqual(errors, []);

  failed = true;
  assert.equal(await send([fileA], { sendButton: button, mediaInput: input }), false);
  assert.equal(button.disabled, false);
  assert.deepEqual(errors, ['denied']);
  assert.equal(cleared.length, 1);
  assert.equal(rendered.length, 2);

  state.ticketId = ' ';
  context.getActiveWorkspaceState = () => ({ ticketId: '' });
  await send([fileA]);
  assert.equal(requests.length, 3);
}

function locationEditor({ pathname = '/dialogs', search = '', fallbackId = exactId, denied = false } = {}) {
  class Node {
    constructor(value = '') {
      this.value = value; this.listeners = {}; this.disabled = false; this.textContent = '';
      this.classList = { toggle() {} };
    }
    addEventListener(name, fn) { this.listeners[name] = fn; }
    appendChild() {}
  }
  const nodes = {};
  for (const suffix of ['Business', 'Type', 'City', 'Location', 'Modal', 'Form', 'Error', 'Save']) {
    nodes['dialogLocationBusiness' + suffix] = new Node();
  }
  nodes.dialogDetailsLocationBusinessBtn = new Node();
  nodes.dialogLocationBusinessBusiness.value = ' Business ';
  nodes.dialogLocationBusinessType.value = ' Office ';
  nodes.dialogLocationBusinessCity.value = ' City ';
  nodes.dialogLocationBusinessLocation.value = ' Location ';
  nodes.dialogDetailsCreateTask = { href: '/tasks?ticketId=' + encodeURIComponent(fallbackId) };
  const requests = [];
  let reloads = 0, hides = 0;
  vm.runInNewContext(read('dialogs-location-business-runtime.js'), {
    document: {
      getElementById: id => nodes[id] || null,
      querySelector: selector => ({ getAttribute: () => selector.includes('_csrf_header') ? 'X-CSRF-TOKEN' : 'csrf-value' }),
      createElement: () => new Node(),
    },
    window: { location: { pathname, search, origin: 'http://localhost', reload: () => { reloads++; } } },
    bootstrap: { Modal: { getOrCreateInstance: () => ({ show() {}, hide: () => { hides++; } }) } },
    Headers, URL, URLSearchParams,
    fetch: async (url, init) => {
      requests.push({ url, ...init });
      return { ok: !denied, status: denied ? 403 : 200,
        json: async () => denied ? { success: false, error: 'denied' } : { success: true } };
    },
  });
  return {
    requests, nodes, submit: () => nodes.dialogLocationBusinessForm.listeners.submit({ preventDefault() {} }),
    reloads: () => reloads, hides: () => hides,
  };
}
async function locationRequests() {
  for (const route of [
    { search: '?ticketId=' + encodeURIComponent(exactId), fallbackId: 'T-100' },
    { pathname: '/dialogs/' + encodeURIComponent(exactId), fallbackId: 'T-100' },
    { fallbackId: exactId },
    { fallbackId: ' null ' },
  ]) {
    const editor = locationEditor(route);
    await editor.submit();
    assert.equal(editor.requests.length, 1);
    const request = editor.requests[0];
    assertQuery(request.url, 'location-business', route.fallbackId === ' null ' ? ' null ' : exactId);
    assert.equal(request.method, 'PATCH');
    assert.equal(request.credentials, 'same-origin');
    assert.equal(request.headers.get('X-CSRF-TOKEN'), 'csrf-value');
    assert.deepEqual(JSON.parse(request.body),
      { business: 'Business', locationType: 'Office', city: 'City', locationName: 'Location' });
    assert.equal(editor.nodes.dialogLocationBusinessSave.disabled, false);
    assert.equal(editor.reloads(), 1);
    assert.equal(editor.hides(), 1);
  }
  const blank = locationEditor({ fallbackId: ' ' });
  await blank.submit();
  assert.equal(blank.requests.length, 0);
  assert.equal(blank.reloads(), 0);
  const denied = locationEditor({ denied: true });
  await denied.submit();
  assert.equal(denied.nodes.dialogLocationBusinessError.textContent, 'denied');
  assert.equal(denied.nodes.dialogLocationBusinessSave.disabled, false);
  assert.equal(denied.reloads(), 0);
  assert.equal(denied.hides(), 0);
}

(async () => {
  await mediaRequests();
  await locationRequests();
  const template = fs.readFileSync(path.resolve(__dirname, '../../main/resources/templates/dialogs/index.html'), 'utf8');
  for (const file of ['dialogs-details-history-runtime', 'dialogs-location-business-runtime']) {
    assert.ok(template.includes(file + ".js(v='20261012-01-278-r56')"));
  }
  console.log('DIALOG_MEDIA_LOCATION_QUERY_R56_RESULT=GREEN');
})().catch(error => { console.error(error); process.exitCode = 1; });
