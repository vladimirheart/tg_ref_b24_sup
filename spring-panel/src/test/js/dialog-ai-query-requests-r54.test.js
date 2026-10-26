'use strict';
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');

const base = path.resolve(__dirname, '../../main/resources/static/js');
const read = file => fs.readFileSync(path.join(base, file), 'utf8');
const exactId = ' T/100?&%# Ю ';

function element(value = '') {
  const classes = new Set();
  const listeners = new Map();
  return {
    value, dataset: {}, innerHTML: '', textContent: '', disabled: false,
    classList: {
      add: name => classes.add(name),
      remove: name => classes.delete(name),
      contains: name => classes.has(name),
    },
    addEventListener: (event, callback) => listeners.set(event, callback),
    click: () => listeners.get('click')(),
  };
}

function assertQuery(url, endpoint) {
  const parsed = new URL(url, 'https://panel.test');
  assert.equal(parsed.pathname, '/api/dialog-ticket/' + endpoint);
  assert.equal(parsed.searchParams.get('ticketId'), exactId);
  return parsed.searchParams;
}

async function runtimeRequests() {
  const window = {};
  const requests = [];
  const elements = Object.fromEntries([
    'workspaceAiSuggestionsSection', 'workspaceAiSuggestionsState', 'workspaceAiSuggestionsList',
    'detailsAiState', 'detailsAiList', 'workspaceAiControlState', 'workspaceAiDisableForDialog',
    'workspaceAiHandoffNoAutoReply', 'workspaceAiReviewBox',
  ].map(name => [name, element()]));
  vm.runInNewContext(read('dialogs-ai-runtime.js'), {
    window,
    fetch: async (url, init = {}) => {
      requests.push({ url, init });
      return { ok: true, json: async () => ({
        success: true, items: [], control: { ai_disabled: false }, review: { pending: false },
      }) };
    },
  });
  const runtime = window.DialogsAiRuntime.createRuntime({ elements });
  await runtime.loadWorkspaceAiSuggestions(exactId);
  await runtime.loadDetailsAiSuggestions(exactId);
  await runtime.loadWorkspaceAiControl(exactId);
  await runtime.loadWorkspaceAiReview(exactId);
  const feedback = { decision: 'accepted', source: 'rag', suggestedReply: ' reply ', memoryKey: ' key ' };
  await runtime.sendAiSuggestionFeedback(exactId, feedback);
  const control = { aiDisabled: true, autoReplyBlocked: false, reason: ' reason ' };
  await runtime.updateWorkspaceAiControl(exactId, control);

  assert.equal(requests.length, 7);
  assert.equal(assertQuery(requests[0].url, 'ai-suggestions').get('limit'), '3');
  assert.equal(assertQuery(requests[1].url, 'ai-suggestions').get('limit'), '3');
  assertQuery(requests[2].url, 'ai-control');
  assertQuery(requests[3].url, 'ai-review');
  assertQuery(requests[4].url, 'ai-suggestions/feedback');
  assertQuery(requests[5].url, 'ai-control');
  assertQuery(requests[6].url, 'ai-control');
  assert.equal(requests[4].init.method, 'POST');
  assert.equal(requests[5].init.method, 'POST');
  assert.deepEqual(JSON.parse(requests[4].init.body), feedback);
  assert.deepEqual(JSON.parse(requests[5].init.body), control);
  for (const request of requests) assert.equal(request.init.credentials, 'same-origin');

  assert.equal(await runtime.sendAiSuggestionFeedback('   ', feedback), false);
  await runtime.updateWorkspaceAiControl('   ', control);
  assert.equal(requests.length, 7);
}

async function reviewActionRequests() {
  const source = read('dialogs.js');
  const start = source.indexOf('  if (workspaceAiReviewApprove) {');
  const end = source.indexOf('  if (workspaceAiSuggestionsList) {', start);
  assert.ok(start >= 0 && end > start, 'missing review action bindings');
  const approve = element();
  const learn = element();
  const reject = element();
  const problemSelect = element('11');
  const solutionSelect = element('22');
  const requests = [];
  vm.runInNewContext(source.slice(start, end), {
    workspaceAiReviewApprove: approve,
    workspaceAiReviewLearn: learn,
    workspaceAiReviewReject: reject,
    workspaceAiReviewProblemSelect: problemSelect,
    workspaceAiReviewSolutionSelect: solutionSelect,
    workspaceAiReviewProblemCandidates: [],
    workspaceAiReviewSolutionCandidates: [],
    activeWorkspaceTicketId: exactId,
    workspaceComposerTicketId: exactId.trim(),
    exactLegacyTicketId: value => String(value || '').trim() ? String(value) : '',
    resolveAiReviewSelectedMessageText: select => select === problemSelect ? 'problem' : 'solution',
    loadWorkspaceAiReview: () => {},
    loadWorkspaceAiSuggestions: () => {},
    fetch: async (url, init) => {
      requests.push({ url, init });
      return { ok: true, json: async () => ({ success: true, updated: true }) };
    },
  });
  await approve.click();
  await learn.click();
  await reject.click();
  assert.equal(requests.length, 3);
  assertQuery(requests[0].url, 'ai-review/approve');
  assertQuery(requests[1].url, 'ai-learning/mapping');
  assertQuery(requests[2].url, 'ai-review/reject');
  assert.deepEqual(JSON.parse(requests[0].init.body), { clientMessageId: 11, operatorMessageId: 22 });
  assert.deepEqual(JSON.parse(requests[1].init.body), {
    clientProblemMessage: 'problem', operatorSolutionMessage: 'solution',
  });
  for (const request of requests) assert.equal(request.init.method, 'POST');
}

(async () => {
  await runtimeRequests();
  await reviewActionRequests();
  const template = fs.readFileSync(path.resolve(__dirname, '../../main/resources/templates/dialogs/index.html'), 'utf8');
  const aiOpsTemplate = fs.readFileSync(path.resolve(__dirname, '../../main/resources/templates/dialogs/ai-ops.html'), 'utf8');
  assert.ok(template.includes("dialogs-ai-runtime.js(v='20261012-01-278-r54')"));
  assert.ok(template.includes("dialogsAssetVersion='20261026-01-282-pagination-v1'"));
  assert.ok(template.includes('dialogs.js(v=${dialogsAssetVersion})'));
  assert.ok(aiOpsTemplate.includes("ai-ops.js(v='20261012-01-278-r54')"));
  console.log('DIALOG_AI_QUERY_REQUESTS_R54_RESULT=GREEN');
})().catch(error => { console.error(error); process.exitCode = 1; });
