'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const read = name => fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js', name), 'utf8');
const escape = value => String(value ?? '').replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&#39;');
const business = { window: {} };
vm.runInNewContext(read('dialogs-details-history-runtime.js'), business);
const render = business.window.DialogsDetailsHistoryRuntime.createRuntime({ escapeHtml: escape, escapeAttribute: escape }).messageToHtml;
let cases = 0;
for (const type of ['forward', 'reply']) {
  const sourceLink = { type, providerMessageId: 'mid.<svg/onload=evil>', authorUserId: '9007199254740993', authorName: '<img src=x onerror=evil>' };
  const html = render({ message: 'Caption', messageType: 'document', sourceLink });
  assert.ok(html.includes(escape(sourceLink.providerMessageId)));
  assert.ok(html.includes(escape(sourceLink.authorName)));
  assert.ok(!html.includes('<svg'));
  assert.ok(!html.includes('<img src=x'));
  assert.ok(!html.includes('href="mid.'));
  assert.ok(html.includes(type === 'forward' ? 'Переслано от' : 'Ответ на сообщение'));
  cases++;
  const deleted = render({ message: '', messageType: 'document', sourceLink, deletedAt: '2026-11-06T06:00:00Z' });
  assert.ok(!deleted.includes('mid.'));
  cases++;
}
assert.ok(render({ message: 'old', forwardedFrom: '<legacy>' }).includes('&lt;legacy&gt;'));
cases++;
class Element {
  constructor() { this.children = []; this.events = {}; this.value = ''; this.text = ''; }
  set textContent(value) { this.text = String(value); this.children = []; }
  get textContent() { return this.text; }
  set innerHTML(_) { throw new Error('Source context must not enter an HTML sink'); }
  append(...children) { this.children.push(...children); }
  appendChild(child) { this.children.push(child); }
  addEventListener(name, action) { this.events[name] = action; }
  replaceChildren(...children) { this.children = children; }
}
const fields = new Map(), root = new Element();
root.querySelector = selector => { if (!fields.has(selector)) fields.set(selector, new Element()); return fields.get(selector); };
const source = read('general-dialogs.js'), anchor = '  async function openTicket(publicId) {';
assert.equal(source.split(anchor).length, 2);
const general = {
  document: { querySelector: selector => selector === '[data-general-dialogs]' ? root : null, createElement: () => new Element() },
  fetch: () => new Promise(() => {}), URLSearchParams, encodeURIComponent
};
vm.runInNewContext(source.replace(anchor, '  globalThis.r292HistoryNodes = historyNodes;\n' + anchor), general);
for (const type of ['forward', 'reply']) {
  const sourceLink = { type, providerMessageId: 'mid.<svg/onload=evil>', authorUserId: '9007199254740993', authorName: '<img src=x onerror=evil>' };
  const nodes = general.r292HistoryNodes([{ id: 1, sender: 'client', message: 'Caption', sourceLink, hasAttachment: false }], 'a'.repeat(32));
  assert.ok(nodes[0].children[2].textContent.includes(sourceLink.providerMessageId));
  assert.ok(nodes[0].children[2].textContent.includes(sourceLink.authorName));
  assert.equal(nodes[0].children[2].children.length, 0);
  cases++;
}
console.log(`GREEN: ${cases} source-link UI cases; actual BUSINESS/GENERAL renderers, no provider/network calls`);
