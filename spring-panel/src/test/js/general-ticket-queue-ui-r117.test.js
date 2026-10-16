'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

class Element {
  constructor(tag = 'div') {
    this.tagName = tag; this.children = []; this.events = {}; this.value = '';
    this.hidden = false; this.disabled = false; this._text = '';
  }
  set textContent(value) { this._text = String(value); this.children = []; }
  get textContent() { return this._text; }
  set innerHTML(_value) { throw new Error('Untrusted HTML must not be rendered'); }
  append(...nodes) { this.children.push(...nodes); }
  appendChild(node) { this.children.push(node); return node; }
  prepend(...nodes) { this.children.unshift(...nodes); }
  replaceChildren(...nodes) { this._text = ''; this.children = [...nodes]; }
  addEventListener(type, handler) { this.events[type] = handler; }
  querySelectorAll(tag) { return this.children.flatMap(child => [...(child.tagName === tag ? [child] : []), ...child.querySelectorAll(tag)]); }
  fire(type) { return this.events[type]?.({ preventDefault() {} }); }
}

function harness(script, pageSelector, csrf = 'test-csrf') {
  const fields = new Map();
  const page = new Element();
  page.querySelector = selector => {
    if (!fields.has(selector)) fields.set(selector, new Element());
    return fields.get(selector);
  };
  const pending = [];
  const calls = [];
  const document = {
    querySelector: selector => selector === pageSelector ? page : selector === 'meta[name="_csrf"]' ? { content: csrf } : null,
    createElement: tag => new Element(tag)
  };
  vm.runInNewContext(fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js', script), 'utf8'), {
    document, URLSearchParams, encodeURIComponent, fetch: (url, options) => {
      calls.push({ url, options });
      return new Promise((resolve, reject) => pending.push({ url, options, resolve, reject }));
    }
  });
  return { page, fields, pending, calls, field: selector => page.querySelector(selector) };
}

const tick = () => new Promise(resolve => setImmediate(resolve));
async function respond(request, body, status = 200) {
  assert.ok(request);
  request.resolve({ ok: status >= 200 && status < 300, status, json: async () => body });
  await tick();
}

async function queue() {
  const h = harness('general-dialogs.js', '[data-general-dialogs]');
  const id = '0123456789abcdef0123456789abcdef';
  const ticket = { ticketPublicId: id, ticketId: '<script>raw</script>', clientName: '<img src=x>', status: 'open' };
  await respond(h.pending.shift(), { tickets: [ticket], hasMore: true });
  const row = h.field('#generalTicketRows').children[0];
  assert.equal(row.children[0].textContent, ticket.ticketId);
  assert.equal(row.children[1].textContent, ticket.clientName);
  const opening = row.children[4].children[0].fire('click');
  await respond(h.pending.shift(), ticket);
  await respond(h.pending.shift(), { ticket, entries: [{ id: 9, message: '<script>message</script>', hasAttachment: true }], hasMore: true, nextBeforeId: 9 });
  await respond(h.pending.shift(), { ticket, relationVerified: true, answers: [{ questionId: 'q', valueText: '<svg onload=evil>' }] });
  await opening;
  assert.equal(h.field('#generalTicketCard').hidden, false);
  assert.equal(h.field('[data-general-answers]').children[1].textContent, '<svg onload=evil>');
  const message = h.field('[data-general-history]').children[0];
  assert.equal(message.children[1].textContent, '<script>message</script>');
  assert.equal(message.children[2].href, `/api/general-tickets/${id}/history/9/attachment`);

  const revoked = h.field('#generalHistoryOlder').fire('click');
  await respond(h.pending.shift(), {}, 403);
  await revoked;
  assert.equal(h.field('#generalTicketCard').hidden, true);
  assert.equal(h.field('[data-general-client]').textContent, '');
  assert.equal(h.field('[data-general-history]').children.length, 0);
  assert.equal(h.field('[data-general-answers]').children.length, 0);
  assert.equal(h.field('#generalTicketRows').children.length, 0);
  assert.equal(h.field('#generalNext').disabled, true);

  h.field('#generalTicketFilters').fire('submit');
  await respond(h.pending.shift(), { tickets: [ticket], hasMore: false });
  const staleOpen = h.field('#generalTicketRows').children[0].children[4].children[0].fire('click');
  const stale = h.pending.splice(0);
  h.field('#generalTicketSearch').value = 'new filter';
  h.field('#generalTicketFilters').fire('submit');
  await respond(h.pending.shift(), { tickets: [], hasMore: false });
  await respond(stale[0], ticket);
  await respond(stale[1], { ticket, entries: [{ id: 8, message: 'stale' }], hasMore: false });
  await respond(stale[2], { ticket, answers: [], relationVerified: true });
  await staleOpen;
  assert.equal(h.field('#generalTicketCard').hidden, true);
  assert.equal(h.field('[data-general-history]').children.length, 0);

  h.field('#generalTicketFilters').fire('submit');
  await respond(h.pending.shift(), { tickets: [ticket], hasMore: false });
  const mismatch = h.field('#generalTicketRows').children[0].children[4].children[0].fire('click');
  await respond(h.pending.shift(), ticket);
  await respond(h.pending.shift(), { ticket: { ...ticket, ticketPublicId: 'f'.repeat(32) }, entries: [], hasMore: false });
  await respond(h.pending.shift(), { ticket, answers: [], relationVerified: true });
  await mismatch;
  assert.equal(h.field('#generalTicketCard').hidden, true);
  assert.equal(h.field('#generalTicketRows').children.length, 0);
  for (const { url, options } of h.calls) {
    assert.ok(url.startsWith('/api/general-tickets/'));
    assert.equal(options.cache, 'no-store');
    assert.equal(options.credentials, 'same-origin');
  }
}

async function grants() {
  const h = harness('general-ticket-access.js', '[data-general-access]');
  const account = { userId: 5, username: '<script>account</script>', canRead: false };
  await respond(h.pending.shift(), [account]);
  assert.equal(h.field('#generalAccessRows').children[0].children[0].textContent, account.username);
  const saving = h.field('#generalAccessRows').children[0].children[2].children[0].fire('click');
  assert.equal(h.pending[0].options.method, 'PUT');
  assert.equal(h.pending[0].options.headers['X-CSRF-TOKEN'], 'test-csrf');
  await respond(h.pending.shift(), null, 204);
  await respond(h.pending.shift(), [{ ...account, canRead: true }]);
  await saving;
  assert.equal(h.field('#generalAccessRows').children[0].children[1].textContent, 'Разрешён');
  const revoking = h.field('#generalAccessRows').children[0].children[2].children[0].fire('click');
  assert.equal(h.pending[0].options.method, 'DELETE');
  await respond(h.pending.shift(), null, 403);
  await revoking;
  assert.equal(h.field('#generalAccessRows').children.length, 0);
  const denied = harness('general-ticket-access.js', '[data-general-access]', '');
  await respond(denied.pending.shift(), [account]);
  denied.field('#generalAccessRows').children[0].children[2].children[0].fire('click');
  assert.equal(denied.pending.length, 0);
}

(async () => {
  await queue();
  await grants();
  console.log('GENERAL_TICKET_QUEUE_UI_R117_RESULT=GREEN');
})().catch(error => { console.error(error); process.exitCode = 1; });
