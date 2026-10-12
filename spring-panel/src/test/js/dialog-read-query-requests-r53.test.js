'use strict';
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');

const base = path.resolve(__dirname, '../../main/resources/static/js');
const read = file => fs.readFileSync(path.join(base, file), 'utf8');
const exactId = ' T/100?&%# Ю ';

function sourceFunction(source, name) {
  const at = source.indexOf('function ' + name + '(');
  assert.ok(at >= 0, 'missing ' + name);
  const start = source.slice(at - 6, at) === 'async ' ? at - 6 : at;
  const begin = source.indexOf(') {', at) + 2;
  assert.ok(begin > at, 'missing function body');
  let depth = 0;
  for (let index = begin; index < source.length; index += 1) {
    if (source[index] === '{') depth += 1;
    if (source[index] === '}' && --depth === 0) return source.slice(start, index + 1);
  }
  throw new Error('unbalanced ' + name);
}

function assertRequest(url, endpoint, ticketId) {
  const parsed = new URL(url, 'https://panel.test');
  assert.equal(parsed.pathname, endpoint);
  assert.equal(parsed.searchParams.get('ticketId'), ticketId);
  return parsed.searchParams;
}

async function workspaceRequests() {
  const source = read('dialogs-workspace-runtime.js');
  const urls = [];
  const options = {
    withChannelParam: (url, channelId) => url + (url.includes('?') ? '&' : '?') + 'channelId=' + encodeURIComponent(channelId),
  };
  const code = ['exactLegacyTicketId', 'isValidWorkspaceContract', 'preloadWorkspaceContract']
    .map(name => sourceFunction(source, name)).join('\n') + '\npreloadWorkspaceContract';
  const load = vm.runInNewContext(code, {
    options, URLSearchParams, encodeURIComponent, AbortController,
    window: { setTimeout: () => 1, clearTimeout: () => {} },
    fetch: async url => {
      urls.push(url);
      return { ok: true, json: async () => ({
        contract_version: 'workspace.v1',
        conversation: { ticketId: exactId, status: 'pending' },
        permissions: {}, sla: { state: 'ok' },
      }) };
    },
  });

  await load(exactId, 81, { include: 'messages,sla', limit: 25, cursor: 7 });
  let params = assertRequest(urls[0], '/api/dialog-ticket/workspace', exactId);
  assert.equal(params.get('channelId'), '81');
  assert.equal(params.get('include'), 'messages,sla');
  assert.equal(params.get('limit'), '25');
  assert.equal(params.get('cursor'), '7');

  delete options.withChannelParam;
  options.workspaceContractInclude = 'permissions';
  await load(' null ', null, {});
  params = assertRequest(urls[1], '/api/dialog-ticket/workspace', ' null ');
  assert.equal(params.get('include'), 'permissions');
  assert.equal(params.has('channelId'), false);
  assert.equal(params.has('cursor'), false);
  assert.equal(params.has('limit'), false);
}

function node() {
  const classes = new Set();
  return {
    style: {}, innerHTML: '', textContent: '',
    classList: {
      add: value => classes.add(value),
      remove: value => classes.delete(value),
      contains: value => classes.has(value),
    },
  };
}

async function notesRequests() {
  const window = {};
  const nodes = {
    dialogDetailsNotesSection: node(),
    dialogDetailsNotesState: node(),
    dialogDetailsNotesList: node(),
  };
  const active = { ticketId: '', channelId: null, context: {} };
  const urls = [];
  const errors = [];
  let notesForbidden = false;
  vm.runInNewContext(read('dialogs-details-runtime.js'), {
    window,
    document: { getElementById: id => nodes[id] || null },
    fetch: async url => {
      urls.push(url);
      const isNotes = url.includes('/notes');
      return {
        ok: !(isNotes && notesForbidden),
        status: isNotes && notesForbidden ? 403 : 200,
        json: async () => isNotes ? { notes: [] } : { summary: {}, history: [], categories: [] },
      };
    },
  });
  const runtime = window.DialogsDetailsRuntime.createRuntime({
    elements: { detailsModalEl: node() },
    getActiveDialogState: () => active,
    setActiveDialogTicketId: id => { active.ticketId = id; },
    setActiveDialogChannelId: id => { active.channelId = id; },
    setActiveDialogContext: context => { active.context = context; },
    debugLog: (event, detail) => { if (event === 'openDialogDetails.catch') errors.push(detail.message); },
  });

  await runtime.openDialogDetails(exactId);
  await new Promise(setImmediate);
  assert.deepEqual(errors, []);
  assertRequest(urls.find(url => url.includes('/notes')), '/api/dialog-ticket/notes', exactId);
  assert.equal(nodes.dialogDetailsNotesState.textContent, 'Заметок нет.');
  assert.equal(nodes.dialogDetailsNotesSection.classList.contains('d-none'), false);

  notesForbidden = true;
  urls.length = 0;
  await runtime.openDialogDetails(' null ');
  await new Promise(setImmediate);
  assertRequest(urls.find(url => url.includes('/notes')), '/api/dialog-ticket/notes', ' null ');
  assert.equal(nodes.dialogDetailsNotesSection.classList.contains('d-none'), true);
}

async function participantsRequests() {
  const window = {};
  const urls = [];
  let activeId = exactId;
  vm.runInNewContext(read('dialogs-participants-runtime.js'), {
    window,
    fetch: async url => {
      urls.push(url);
      return { ok: true, json: async () => ({ success: true, participants: [{ username: 'peer' }] }) };
    },
  });
  const runtime = window.DialogsParticipantsRuntime.createRuntime({
    getActiveDialogTicketId: () => activeId,
  });
  const result = await runtime.loadDialogParticipants();
  assertRequest(urls[0], '/api/dialog-ticket/participants', exactId);
  assert.equal(result[0].username, 'peer');

  activeId = '   ';
  const empty = await runtime.loadDialogParticipants();
  assert.equal(empty.length, 0);
  assert.equal(urls.length, 1);
}

(async () => {
  await workspaceRequests();
  await notesRequests();
  await participantsRequests();
  const template = fs.readFileSync(path.resolve(__dirname, '../../main/resources/templates/dialogs/index.html'), 'utf8');
  for (const file of ['dialogs-workspace-runtime', 'dialogs-details-runtime', 'dialogs-participants-runtime']) {
    assert.ok(template.includes(file + ".js(v='20261012-01-278-r53')"), 'missing cache key for ' + file);
  }
  console.log('DIALOG_READ_QUERY_REQUESTS_R53_RESULT=GREEN');
})().catch(error => { console.error(error); process.exitCode = 1; });
