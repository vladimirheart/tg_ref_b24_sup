'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

class Element {
  constructor() {
    this.children = [];
    this.textContent = '';
    this.className = '';
    this.hidden = true;
  }

  replaceChildren() { this.children = []; }
  appendChild(child) { this.children.push(child); return child; }
}

async function main() {
  const source = fs.readFileSync(path.resolve(__dirname,
    '../../main/resources/static/js/business-administration.js'), 'utf8');
  const start = source.indexOf('  const classificationLabels = {');
  const end = source.indexOf('  async function loadChannels()', start);
  assert.ok(start >= 0 && end > start, 'channel preview implementation not found');

  const elements = new Map();
  const element = selector => {
    if (!elements.has(selector)) elements.set(selector, new Element());
    return elements.get(selector);
  };
  const pending = new Map();
  const deleted = [];
  const showChannelPreview = vm.runInNewContext(
    `let previewRequestId = 0;\n${source.slice(start, end)}\nshowChannelPreview`, {
      page: { querySelector: element },
      document: { createElement: () => new Element() },
      window: { confirm: () => true },
      revokeButton: onClick => {
        const button = new Element();
        button.onClick = onClick;
        return button;
      },
      perform: action => action(),
      cell: (row, value) => {
        const item = new Element();
        item.textContent = String(value ?? '');
        row.appendChild(item);
        return item;
      },
      request: (url, method) => method === 'DELETE'
        ? (deleted.push(url), Promise.resolve())
        : new Promise((resolve, reject) => pending.set(url, { resolve, reject }))
    });

  const older = showChannelPreview({ id: 1, channelName: 'Старый' });
  const newer = showChannelPreview({ id: 2, channelName: 'Новый' });
  pending.get('/channels/2/binding-preview').resolve({
    classification: 'SINGLE_BUSINESS',
    bindings: [{ businessId: 7, businessCode: 'sushi', businessStatus: 'active',
      resolutionMode: 'SINGLE_BUSINESS', active: true }]
  });
  await newer;
  const title = element('[data-channel-preview-title]');
  const status = element('[data-channel-preview-status]');
  const rows = element('#channelBindingRows');
  assert.match(title.textContent, /Новый/);
  assert.match(status.textContent, /Одна активная привязка/);
  assert.equal(element('#channelBindingPreview').hidden, false);
  assert.equal(rows.children[0].children[0].textContent, 'sushi');
  assert.equal(typeof rows.children[0].children[4].children[0].onClick, 'function');
  const previousPreview = pending.get('/channels/2/binding-preview');
  const deactivate = rows.children[0].children[4].children[0].onClick();
  for (let attempt = 0; attempt < 10
      && pending.get('/channels/2/binding-preview') === previousPreview; attempt += 1) {
    await Promise.resolve();
  }
  assert.notEqual(pending.get('/channels/2/binding-preview'), previousPreview);
  pending.get('/channels/2/binding-preview').resolve({ classification: 'NO_ACTIVE_BINDING', bindings: [] });
  await deactivate;
  assert.deepEqual(deleted, ['/channels/2/bindings/7']);
  assert.equal(rows.children[0].children[0].textContent, 'Привязок нет.');
  pending.get('/channels/1/binding-preview').resolve({ classification: 'NO_ACTIVE_BINDING', bindings: [] });
  await older;
  assert.match(title.textContent, /Новый/);
  assert.equal(rows.children[0].children[0].textContent, 'Привязок нет.');

  const empty = showChannelPreview({ id: 3, channelName: 'Пустой' });
  pending.get('/channels/3/binding-preview').resolve({ classification: 'NO_ACTIVE_BINDING', bindings: [] });
  await empty;
  assert.equal(rows.children.length, 1);
  assert.equal(rows.children[0].children[0].textContent, 'Привязок нет.');
  assert.equal(rows.children[0].children[0].colSpan, 5);

  const staleFailure = showChannelPreview({ id: 5, channelName: 'Устаревшая ошибка' });
  const current = showChannelPreview({ id: 6, channelName: 'Актуальный' });
  pending.get('/channels/6/binding-preview').resolve({ classification: 'NO_ACTIVE_BINDING', bindings: [] });
  await current;
  pending.get('/channels/5/binding-preview').reject(new Error('Старая ошибка'));
  await staleFailure;
  assert.match(title.textContent, /Актуальный/);
  assert.match(status.textContent, /Нет активных привязок/);

  const failed = showChannelPreview({ id: 4, channelName: 'Ошибка' });
  pending.get('/channels/4/binding-preview').reject(new Error('Ошибка загрузки'));
  await failed;
  assert.equal(rows.children.length, 0);
  assert.equal(status.textContent, 'Ошибка загрузки');
  assert.equal(status.className, 'small text-danger');
  console.log('BUSINESS_CHANNEL_PREVIEW_UI_TEST_RESULT=GREEN');
}

main().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
