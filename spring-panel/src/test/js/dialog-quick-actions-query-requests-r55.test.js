'use strict';
// R55: реальные функции/callbacks с fetch doubles плюс URL-контракт всех 16 call sites.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const base = path.resolve(__dirname, '../../main/resources/static/js');
const read = name => fs.readFileSync(path.join(base, name), 'utf8').replace(/\r\n/g, '\n');
const exactId = ' T/100?&%#+ Ю ';
const peer = 'agent/+? Ю';

function functionSource(file, name) {
  const text = read(file);
  const marker = 'function ' + name + '(';
  const at = text.indexOf(marker);
  assert.ok(at >= 0, 'missing ' + name);
  const start = text.slice(at - 6, at) === 'async ' ? at - 6 : at;
  const open = text.indexOf('{', at);
  let depth = 0;
  for (let i = open; i < text.length; i++) {
    if (text[i] === '{') depth++;
    if (text[i] === '}' && --depth === 0) return text.slice(start, i + 1);
  }
  throw new Error('unbalanced ' + name);
}
function loadFunction(file, name, context) {
  return vm.runInNewContext(functionSource(file, name) + '\n' + name + ';', context);
}
class Node {
  constructor() { this.dataset = {}; this.value = ''; this.listeners = {}; this.disabled = false; }
  addEventListener(event, handler) { this.listeners[event] = handler; }
  closest() { return this; }
  getAttribute(name) { return name === 'data-remove-participant' ? peer : null; }
}
function capture() {
  const requests = [];
  return {
    requests,
    fetch: async (url, init = {}) => {
      requests.push({ url, ...init });
      return { ok: true, status: 200, json: async () => ({
        success: true, changed: true, responsible: 'operator', participants: [], timestamp: 'now',
      }) };
    },
  };
}
function assertRequest(request, suffix, method = 'POST', ticketId = exactId) {
  const url = new URL(request.url, 'http://localhost');
  assert.equal(url.pathname, '/api/dialog-ticket/' + suffix);
  assert.equal(url.searchParams.get('ticketId'), ticketId);
  assert.equal(request.method, method);
  assert.equal(request.credentials, undefined); // прежний default same-origin
  return url;
}
function everyUrlContract() {
  const expected = {
    'dialogs-actions-runtime.js': ['resolve', 'take', 'snooze', 'spam', 'resolve', 'reopen', 'resolve', 'reopen', 'reply'],
    'dialogs-details-history-runtime.js': ['edit', 'delete'],
    'dialogs-workspace-runtime.js': ['reply'],
    'dialogs-participants-runtime.js': ['participants', 'participants', 'reassign'],
    'dialogs-templates-runtime.js': ['categories'],
  };
  let total = 0;
  for (const [file, suffixes] of Object.entries(expected)) {
    const text = read(file);
    const expressions = [...text.matchAll(/fetch\((\x60[^\x60]+\x60)/g)]
      .filter(match => /\bmethod:\s*'(?:POST|DELETE)'/.test(text.slice(match.index + match[0].length, match.index + match[0].length + 180)))
      .map(match => match[1])
      .filter(expression => /\$\{encodeURIComponent\(/.test(expression) && suffixes.some(
        suffix => expression.includes('/' + suffix + String.fromCharCode(96)) || expression.includes('/' + suffix + '?')));
    assert.equal(expressions.length, suffixes.length, file);
    for (const ticketId of [exactId, 'T-100', ' null ', 'T%2F+']) {
      expressions.forEach((expression, index) => {
        const url = vm.runInNewContext(expression, {
          ticketId, username: peer, activeState: { composerTicketId: ticketId },
          activeWorkspaceState: { ticketId },
        });
        const parsed = assertRequest({ url, method: 'POST' }, suffixes[index], 'POST', ticketId);
        if (expression.includes('encodeURIComponent(username)')) assert.equal(parsed.searchParams.get('username'), peer);
      });
    }
    total += expressions.length;
  }
  assert.equal(total, 16);
}

async function actionsRequests() {
  const captured = capture();
  const context = {
    fetch: captured.fetch, options: {}, getActiveDialogState: () => ({ ticketId: exactId }),
    parseRowCategories: () => ['billing'], updateRowStatus() {}, updateRowQuickActions() {},
    updateRowResponsible() {}, notify() {}, exactLegacyTicketId: value => value || '',
  };
  await loadFunction('dialogs-actions-runtime.js', 'closeDialogQuick', context)(exactId, null);
  await loadFunction('dialogs-actions-runtime.js', 'takeDialog', context)(exactId, null);
  const button = new Node();
  await loadFunction('dialogs-actions-runtime.js', 'snoozeDialog', context)(exactId, 30, button);
  ['resolve', 'take', 'snooze'].forEach((suffix, index) => assertRequest(captured.requests[index], suffix));
  assert.deepEqual(JSON.parse(captured.requests[0].body), { categories: ['billing'] });
  assert.deepEqual(JSON.parse(captured.requests[2].body), { minutes: 30 });
  assert.equal(button.disabled, false);
  await loadFunction('dialogs-actions-runtime.js', 'takeDialog', context)('', null);
  assert.equal(captured.requests.length, 3);
  const failed = loadFunction('dialogs-actions-runtime.js', 'takeDialog', {
    ...context, fetch: async () => ({ ok: false, status: 403, json: async () => ({ error: 'denied' }) }),
  });
  await assert.rejects(() => failed(exactId, null, button), /denied/);
  assert.equal(button.disabled, false);
}

async function historyRequests() {
  const captured = capture();
  let currentId = exactId;
  const context = {
    fetch: captured.fetch, HTMLElement: Node, exactLegacyTicketId: value => value.trim() ? value : '',
    getActiveDialogState: () => ({ ticketId: currentId }), closeHistoryActionMenus() {},
    window: { confirm: () => true, prompt: () => ' edited ' },
    resolveHistoryMessageNode: () => null, refreshHistory: async () => {},
  };
  const handler = loadFunction('dialogs-details-history-runtime.js', 'handleHistoryAction', context);
  for (const action of ['edit', 'delete']) {
    const button = new Node();
    button.dataset = { action, messageId: '456' };
    await handler(button);
  }
  assertRequest(captured.requests[0], 'edit');
  assertRequest(captured.requests[1], 'delete');
  assert.deepEqual(JSON.parse(captured.requests[0].body), { telegramMessageId: 456, message: 'edited' });
  assert.deepEqual(JSON.parse(captured.requests[1].body), { telegramMessageId: 456 });
  currentId = ' ';
  await handler(new Node());
  assert.equal(captured.requests.length, 2);
}

async function participantRequests() {
  const captured = capture();
  let currentId = exactId;
  const elements = {
    participantsAddBtn: new Node(), participantsSelect: { value: ' ' + peer + ' ' },
    participantsCurrentList: new Node(), reassignSubmit: new Node(), reassignSelect: { value: peer },
  };
  const context = {
    fetch: captured.fetch, Element: Node, elements, options: {},
    getActiveDialogTicketId: () => currentId, setParticipantsState() {},
    renderDialogParticipantsState() {}, syncParticipantsSelectOptions() {},
    syncReassignSelectOptions() {}, renderReassignCurrentResponsible() {}, notify() {},
  };
  loadFunction('dialogs-participants-runtime.js', 'bindParticipantEvents', context)();
  await elements.participantsAddBtn.listeners.click();
  await elements.participantsCurrentList.listeners.click({ target: new Node() });
  await elements.reassignSubmit.listeners.click();
  assertRequest(captured.requests[0], 'participants');
  const removed = assertRequest(captured.requests[1], 'participants', 'DELETE');
  assert.equal(removed.searchParams.get('username'), peer);
  assertRequest(captured.requests[2], 'reassign');
  assert.deepEqual(JSON.parse(captured.requests[0].body), { username: peer });
  assert.deepEqual(JSON.parse(captured.requests[2].body), { username: peer });
  currentId = '';
  await elements.participantsAddBtn.listeners.click();
  await elements.participantsCurrentList.listeners.click({ target: new Node() });
  await elements.reassignSubmit.listeners.click();
  assert.equal(captured.requests.length, 3);
}

async function replyAndCategoryRequests() {
  const captured = capture();
  const elements = { detailsReplySend: new Node(), detailsReplyText: new Node() };
  elements.detailsReplyText.value = ' message ';
  const context = {
    fetch: captured.fetch, elements, HTMLElement: Node, getActiveDialogState: () => ({ ticketId: exactId }),
    resolveDetailsTicketId: () => exactId, options: { getActiveReplyToTelegramId: () => 123 },
    setDetailsIconButtonVisual() {}, updateDetailsReplySendLabel() {}, notify() {},
  };
  loadFunction('dialogs-actions-runtime.js', 'bindDetailsReplyActions', context)();
  await elements.detailsReplySend.listeners.click();
  assertRequest(captured.requests[0], 'reply');
  assert.deepEqual(JSON.parse(captured.requests[0].body), { message: 'message', replyToTelegramId: 123 });
  assert.equal(elements.detailsReplySend.disabled, false);

  const workspaceElements = { workspaceComposerText: new Node(), workspaceComposerSend: new Node() };
  workspaceElements.workspaceComposerText.value = ' workspace ';
  await loadFunction('dialogs-workspace-runtime.js', 'sendWorkspaceReply', {
    fetch: captured.fetch, elements: workspaceElements, state: { activeReplyToTelegramId: 456 }, options: {},
    getActiveWorkspaceState: () => ({ composerTicketId: exactId }), saveWorkspaceDraft() {},
    resetWorkspaceReplyTarget() {}, appendWorkspaceMessage() {}, notify() {},
  })();
  assertRequest(captured.requests[1], 'reply');
  assert.deepEqual(JSON.parse(captured.requests[1].body), { message: 'workspace', replyToTelegramId: 456 });
  assert.equal(workspaceElements.workspaceComposerSend.disabled, false);

  await loadFunction('dialogs-templates-runtime.js', 'persistDialogCategories', {
    fetch: captured.fetch, getDialogState: () => ({ activeDialogTicketId: exactId }),
  })([' billing ']);
  assertRequest(captured.requests[2], 'categories');
  assert.deepEqual(JSON.parse(captured.requests[2].body), { categories: [' billing '] });
}

(async () => {
  everyUrlContract();
  await actionsRequests();
  await historyRequests();
  await participantRequests();
  await replyAndCategoryRequests();
  const template = fs.readFileSync(path.resolve(__dirname, '../../main/resources/templates/dialogs/index.html'), 'utf8');
  for (const file of ['actions', 'details-history', 'workspace', 'participants', 'templates']) {
    assert.ok(template.includes('dialogs-' + file + "-runtime.js(v='20261012-01-278-r55')"));
  }
  console.log('DIALOG_QUICK_ACTIONS_QUERY_R55_RESULT=GREEN;URL_CALL_SITES=16;EXECUTED_FETCHES=11');
})().catch(error => { console.error(error); process.exitCode = 1; });
