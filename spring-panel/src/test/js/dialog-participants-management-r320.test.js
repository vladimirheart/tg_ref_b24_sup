'use strict';
const { test } = require('node:test'), assert = require('node:assert/strict');
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm');
const source = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/dialogs-participants-runtime.js'), 'utf8');
const big = '9007199254740995', publicA = 'a'.repeat(32), publicB = 'b'.repeat(32);
class Element {
  constructor() { this.children = []; this.dataset = {}; this.disabled = false; this.value = ''; this.innerHTML = ''; this.textContent = ''; this.listeners = {}; this.isConnected = true; this.classList = { toggle() {}, add() {} }; }
  append(...children) { this.children.push(...children); } appendChild(child) { this.append(child); }
  replaceChildren(...children) { this.children = children; } querySelector() { return null; }
  addEventListener(type, fn) { this.listeners[type] = fn; }
  async fire() { return this.listeners.click?.({ target: this }); }
}
function fixture(config = {}) {
  const elements = Object.fromEntries(['participantsSelect', 'participantsAddBtn', 'participantsSelectHint', 'participantsCurrentList', 'participantsCurrentState', 'participantsModalEl'].map(key => [key, new Element()]));
  let selected = 'a', revision = '7', members = [{ id: '2', username: null, snapshot: 'Удалённый сотрудник', eligible: false }];
  const requests = [], writes = [], notices = [];
  const owner = which => ({ userId: '9007199254740993', ticketId: which === 'a' ? ' raw ' : 'other', ticketPublicId: which === 'a' ? publicA : publicB, businessId: '11' });
  const context = () => ({ scopeKind: 'BUSINESS', ticketPublicId: owner(selected).ticketPublicId, businessId: '11' });
  const response = (data, status = 200) => ({ ok: status >= 200 && status < 300, status, json: async () => data });
  const fetch = async (url, init = {}) => {
    requests.push({ url, init }); assert(url.startsWith('/api/business-scope/'), 'No legacy/global fallback');
    const which = url.includes(publicA) ? 'a' : 'b';
    if (config.delay && which === 'a' && !init.method) await config.delay;
    if (config.readError && which === 'a') return response({}, 404);
    if (init.method) {
      const body = JSON.parse(init.body); writes.push({ method: init.method, body });
      if (config.conflict) return response({}, 409);
      revision = String(BigInt(revision) + 1n); members = [...members, { id: body.memberId, username: 'new-member', eligible: true }];
      return response(null, 204);
    }
    if (url.endsWith('/management')) return response({ owner: owner(which), revision: config.revisionMismatch ? '8' : revision,
      canManage: !config.readonly, candidates: config.readonly ? [] : [{ userId: big, username: '<img onerror=1>' }] });
    return response({ owner: config.wrongOwner ? { ...owner(which), businessId: '12' } : owner(which), revision,
      members: members.map(member => ({ ...member, snapshot: which + ': ' + member.snapshot })) });
  };
  const window = {}, document = { createElement: () => new Element(), querySelector: () => null };
  vm.runInNewContext(source, { window, document, fetch, Element, console });
  const runtime = window.DialogsParticipantsRuntime.createRuntime({ elements, getActiveDialogContext: context,
    getActiveDialogTicketId: () => owner(selected).ticketId, canRunAction: () => false,
    showNotification: (message, type) => notices.push({ message, type }),
    escapeHtml: value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]) });
  runtime.bindParticipantEvents();
  return { runtime, elements, writes, requests, notices, select: which => { selected = which; } };
}
test('BUSINESS adds the exact BIGINT member under full owner and current roster revision', async () => {
  const f = fixture(); await f.runtime.loadDialogParticipants();
  assert.equal(f.elements.participantsSelect.children[1].value, big);
  assert.equal(f.elements.participantsSelect.children[1].textContent, '<img onerror=1>');
  assert.match(f.elements.participantsCurrentList.innerHTML, /data-remove-participant-id="2"/);
  f.elements.participantsSelect.value = big; await f.elements.participantsAddBtn.fire();
  assert.deepEqual(f.writes, [{ method: 'POST', body: { userId: '9007199254740993', ticketId: ' raw ', ticketPublicId: publicA, businessId: '11', revision: '7', memberId: big } }]);
  assert.equal((f.elements.participantsCurrentList.innerHTML.match(/dialog-details-participant-card/g) || []).length, 2);
});
test('read-only BUSINESS keeps unavailable historical members visible and never mutates', async () => {
  const f = fixture({ readonly: true }); await f.runtime.loadDialogParticipants();
  assert.match(f.elements.participantsCurrentList.innerHTML, /Удалённый сотрудник/);
  assert.doesNotMatch(f.elements.participantsCurrentList.innerHTML, /data-remove-participant/);
  assert(f.elements.participantsSelect.disabled); assert(f.elements.participantsAddBtn.disabled);
  f.elements.participantsSelect.value = big; await f.elements.participantsAddBtn.fire(); assert.equal(f.writes.length, 0);
});
for (const config of [{ wrongOwner: true }, { revisionMismatch: true }, { readError: true }]) test('invalid scoped read cannot become a writable legacy roster: ' + JSON.stringify(config), async () => {
  const f = fixture(config); await assert.rejects(f.runtime.loadDialogParticipants());
  assert(f.elements.participantsAddBtn.disabled); assert.equal(f.writes.length, 0);
  assert(f.requests.every(req => req.url.startsWith('/api/business-scope/')));
});
test('late roster and late read error cannot replace another dialog', async () => {
  for (const readError of [false, true]) {
    let release; const delay = new Promise(resolve => { release = resolve; });
    const f = fixture({ delay, readError }); const old = f.runtime.loadDialogParticipants();
    f.select('b'); await f.runtime.loadDialogParticipants(); release(); await old;
    assert.match(f.elements.participantsCurrentList.innerHTML, /b:/);
    assert.equal((f.elements.participantsCurrentList.innerHTML.match(/dialog-details-participant-card/g) || []).length, 1);
  }
});
test('revision conflict sends one write and never retries it or changes the roster', async () => {
  const f = fixture({ conflict: true }); await f.runtime.loadDialogParticipants();
  f.elements.participantsSelect.value = big; await f.elements.participantsAddBtn.fire();
  assert.equal(f.writes.length, 1); assert.equal((f.elements.participantsCurrentList.innerHTML.match(/dialog-details-participant-card/g) || []).length, 1);
  assert.equal(f.notices.at(-1).type, 'error');
});
