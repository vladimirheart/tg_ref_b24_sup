'use strict';
const test = require('node:test'), assert = require('node:assert/strict');
const { describe } = require('../../main/resources/static/js/incident-descriptions.js');
const input = (extra = {}) => ({ signal_type: 'credential_rotation', signal_context: { last_status: 'missing_secret', credential_kind: 'proxy_password', display_name: 'Channel network route · тостов proxy password', source_ref: 'channels#3.deliverySettings.network_route.proxy.password', ...extra } });
test('Точный пример владельца: пароль прокси конкретного канала и путь настройки', () => {
  const value = input(), original = JSON.stringify(value), info = describe(value);
  assert.match(info.title, /Пароль прокси — канал «тостов»/);
  assert.match(info.summary, /тостов/); assert.match(info.action, /Если используется профиль/);
  assert.match(info.impact, /не доказывает сбой/); assert(!info.impact.includes('истечения'));
  assert(info.facts.some(fact => fact.value.includes('Каналы и запуск ботов → «тостов»')));
  assert(!JSON.stringify(info).includes('Channel network route')); assert.equal(JSON.stringify(value), original);
});
test('VLESS-токен и токен бота не называются паролем прокси', () => {
  assert.match(describe(input({ credential_kind: 'proxy_token', display_name: 'Network profile · Корпоративный proxy token' })).title, /Токен VLESS-прокси — профиль «Корпоративный»/);
  assert.match(describe(input({ credential_kind: 'bot_token', display_name: 'Channel token · Другой канал' })).title, /Токен бота — канал «Другой канал»/);
});
test('Старое событие без signal_context также получает точное пояснение', () => {
  const value = input(), context = value.signal_context; delete value.signal_context;
  value.events = [{ payload_json: JSON.stringify(context) }]; value.status = 'closed';
  const info = describe(value); assert(info.historical); assert.match(info.title, /тостов/);
});
test('Неиспользуемые данные — не отказ; отсутствующие сведения не угадываются', () => {
  assert.match(describe(input({ last_status: 'not_required' })).title, /Не требуется/);
  assert(!describe({ signal_type: 'credential_rotation' }).title.includes('тостов'));
  assert(!describe(input({ display_name: '<img src=x onerror=alert(1)> proxy password' })).title.includes('канал «'));
});
