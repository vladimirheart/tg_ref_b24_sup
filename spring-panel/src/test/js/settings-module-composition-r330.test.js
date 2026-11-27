'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const resources = path.resolve(__dirname, '../../main/resources');
const modules = {
  AuthUserPhoto: 'auth-management-photo', AuthUserEditor: 'auth-management-user',
  AuthRoleEditor: 'auth-management-roles', AuthOrganization: 'auth-management-organization',
  BotQuestionModel: 'bot-settings-model', BotRatingEditor: 'bot-settings-rating',
  BotQuestionEditor: 'bot-settings-editor',
};
function load() {
  const sandbox = vm.createContext({ window: {} });
  for (const file of Object.values(modules)) {
    vm.runInContext(fs.readFileSync(path.join(resources, 'static/js', file + '.js'), 'utf8'), sandbox);
  }
  return sandbox.window;
}
test('bounded modules load and compose without touching DOM or eagerly reading host state', () => {
  const exports = load();
  const lazyHost = new Proxy({}, { get() { throw new Error('Premature state read'); } });
  for (const name of Object.keys(modules)) {
    assert.equal(typeof exports[name].create(lazyHost, {}), 'object', name);
  }
});
test('settings loads each dependency once, before its host, with the same cache revision', () => {
  const html = fs.readFileSync(path.join(resources, 'templates/settings/index.html'), 'utf8');
  for (const [name, file] of Object.entries(modules)) {
    const marker = '/js/' + file + '.js';
    assert.equal(html.split(marker).length, 2, file);
    assert(html.indexOf(marker) < html.indexOf('/js/' + (name.startsWith('Auth') ? 'auth-management' : 'bot-settings') + '.js'));
    assert(html.includes(marker + '?v=20261127-modules-330'));
  }
});
test('question model reads the live host catalog instead of retaining a previous business catalog', () => {
  const host = { DASHBOARD_ATTRIBUTE_KEY_SET: new Set(['direction']) };
  const model = load().BotQuestionModel.create(host);
  assert.equal(model.isDashboardBindingKeyAllowed('direction'), true);
  host.DASHBOARD_ATTRIBUTE_KEY_SET = new Set(['city']);
  assert.equal(model.isDashboardBindingKeyAllowed('direction'), false);
  assert.equal(model.isDashboardBindingKeyAllowed('city'), true);
});
