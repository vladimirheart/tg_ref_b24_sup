'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '../../main/resources');
const authSource = fs.readFileSync(path.join(root, 'static/js/dialogs-auth-response-runtime.js'), 'utf8');

function setup() {
  const redirects = [];
  const window = { location: {
    href: 'http://127.0.0.1:8080/dialogs',
    origin: 'http://127.0.0.1:8080',
    assign: url => redirects.push(url),
  } };
  vm.runInNewContext(authSource, { window, URL });
  return { guard: window.DialogsAuthResponseRuntime.ensureApiSession, redirects };
}

test('401 and followed login redirect trigger one re-authentication navigation', () => {
  const h = setup();
  assert.throws(() => h.guard({ status: 401, redirected: false }), error => error.code === 'session_expired');
  assert.throws(() => h.guard({ status: 200, redirected: true,
    url: 'http://127.0.0.1:8080/login?expired=1' }), error => error.code === 'session_expired');
  assert.deepEqual(h.redirects, ['/login']);
});

test('a login HTML response after legacy 302 is detected before JSON parsing', () => {
  const h = setup();
  assert.throws(() => h.guard({ status: 200, redirected: true,
    url: 'http://127.0.0.1:8080/login' }), error => error.code === 'session_expired');
  assert.deepEqual(h.redirects, ['/login']);
});

test('valid JSON, 403, and unrelated redirects are not confused with expiry', () => {
  const h = setup();
  for (const response of [
    { status: 200, redirected: false },
    { status: 403, redirected: false },
    { status: 200, redirected: true, url: 'http://127.0.0.1:8080/other' },
    { status: 200, redirected: true, url: 'https://example.com/login' },
  ]) assert.equal(h.guard(response), response);
  assert.deepEqual(h.redirects, []);
});

test('dialog runtime scripts load the guard before any request client', () => {
  const html = fs.readFileSync(path.join(root, 'templates/dialogs/index.html'), 'utf8');
  assert(html.indexOf('dialogs-auth-response-runtime.js') < html.indexOf('dialogs-list-runtime.js'));
  for (const filename of ['dialogs-list-runtime', 'dialogs-details-runtime',
    'dialogs-details-history-runtime', 'dialogs-workspace-runtime']) {
    const source = fs.readFileSync(path.join(root, 'static/js', filename + '.js'), 'utf8');
    assert.match(source, /DialogsAuthResponseRuntime\?\.ensureApiSession\(/);
  }
});
