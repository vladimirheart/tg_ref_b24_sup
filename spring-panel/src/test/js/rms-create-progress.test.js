'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/rms-monitoring.js'), 'utf8');
const template = fs.readFileSync(path.join(__dirname, '../../main/resources/templates/analytics/rms-control.html'), 'utf8');

class Element {
  constructor({ value = '', checked = false, innerHTML = '' } = {}) {
    this.value = value;
    this.checked = checked;
    this.innerHTML = innerHTML;
    this.disabled = false;
    this.hidden = false;
    this.textContent = '';
    this.dataset = {};
    this.attributes = new Map();
    this.listeners = new Map();
  }

  addEventListener(type, listener) {
    this.listeners.set(type, listener);
  }

  setAttribute(name, value) {
    this.attributes.set(name, String(value));
  }

  getAttribute(name) {
    return this.attributes.get(name) || null;
  }
}

function response(body, status = 200) {
  return { ok: status >= 200 && status < 300, status, json: async () => body };
}

function harness() {
  const requests = [];
  const messages = [];
  const form = new Element();
  const submit = new Element({ innerHTML: 'Добавить' });
  const status = new Element();
  status.hidden = true;
  form.reset = () => { form.resetCount = (form.resetCount || 0) + 1; };

  const elements = new Map([
    ['rmsCreateForm', form],
    ['rmsCreateSubmitBtn', submit],
    ['rmsCreateStatus', status],
    ['rmsAddressInput', new Element({ value: 'https://rms.example.test' })],
    ['rmsLoginInput', new Element({ value: 'operator' })],
    ['rmsPasswordInput', new Element({ value: 'secret' })],
    ['rmsEnabledInput', new Element({ checked: true })],
    ['rmsLicenseMonitoringInput', new Element({ checked: true })],
    ['rmsNetworkMonitoringInput', new Element({ checked: true })],
  ]);

  const context = {
    Headers,
    Map,
    console,
    document: {
      cookie: '',
      getElementById: (id) => elements.get(id),
      querySelector: () => null,
    },
    window: {
      showPopup: (message, type) => messages.push({ message, type }),
      setInterval: () => 1,
      clearInterval: () => {},
    },
    fetch: (url, options = {}) => {
      if (url === '/api/monitoring/rms/sites' && String(options.method || 'GET').toUpperCase() === 'POST') {
        return new Promise((resolve, reject) => requests.push({ url, options, resolve, reject }));
      }
      return Promise.resolve(response({ items: [] }));
    },
  };
  vm.runInNewContext(source, context);
  const submitForm = form.listeners.get('submit');
  return { form, submit, status, requests, messages, submitForm, elements };
}

test('создание RMS показывает spinner и блокирует повторную отправку до ответа', async () => {
  assert.match(template, /id="rmsCreateStatus"[^>]*role="status"[^>]*aria-live="polite"/);
  assert.match(template, /id="rmsCreateSubmitBtn"[^>]*type="submit"/);
  const h = harness();
  const firstSubmit = h.submitForm({ preventDefault() {} });

  assert.equal(h.requests.length, 1);
  assert.equal(h.form.getAttribute('aria-busy'), 'true');
  assert.equal(h.submit.disabled, true);
  assert.match(h.submit.innerHTML, /spinner-border/);
  assert.match(h.submit.innerHTML, /Добавляем RMS/);
  assert.equal(h.status.hidden, false);
  assert.equal(h.status.textContent, 'Добавляем RMS…');

  await h.submitForm({ preventDefault() {} });
  assert.equal(h.requests.length, 1);

  h.requests[0].resolve(response({ success: true }));
  await firstSubmit;

  assert.equal(h.form.getAttribute('aria-busy'), 'false');
  assert.equal(h.submit.disabled, false);
  assert.equal(h.submit.innerHTML, 'Добавить');
  assert.equal(h.status.hidden, true);
  assert.equal(h.status.textContent, '');
  assert.match(h.messages.at(-1).message, /RMS добавлен/);
});

test('ошибка создания RMS снимает индикатор и позволяет повторить запрос', async () => {
  const h = harness();
  const failedSubmit = h.submitForm({ preventDefault() {} });

  h.requests[0].reject(new Error('offline'));
  await failedSubmit;

  assert.equal(h.form.getAttribute('aria-busy'), 'false');
  assert.equal(h.submit.disabled, false);
  assert.equal(h.submit.innerHTML, 'Добавить');
  assert.equal(h.status.hidden, true);
  assert.match(h.messages.at(-1).message, /Не удалось добавить RMS: offline/);

  const retry = h.submitForm({ preventDefault() {} });
  assert.equal(h.requests.length, 2);
  h.requests[1].resolve(response({ success: true }));
  await retry;
});
