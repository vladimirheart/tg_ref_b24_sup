'use strict';
const test = require('node:test'), assert = require('node:assert/strict');
const { describe, transportDetails } = require('../../main/resources/static/js/incident-descriptions.js');
const { incident, overview } = require('./fixtures/incident-concrete-problems-r14.cjs');
const key = 'ui-event-outbox-watch';
const signal = (data = {}) => ({ signal_type: 'integration_transport', signal_key: 'panel-runtime-checkpoints/' + key, events: [{ payload_json: JSON.stringify({ worker_key: key, age_minutes: 2827, stale_threshold_minutes: 10, cursor_lag: 0, unhealthy_streak: 47, ...data }) }] });
test('Пример владельца: точная задача, назначение и возраст позиции вместо времени простоя', () => {
  const input = signal(), original = JSON.stringify(input), value = describe(input);
  assert.match(value.title, /Обновление данных на открытых страницах панели/);
  assert.match(value.summary, /передаёт изменения в открытый интерфейс/);
  assert.match(value.summary, /1 д 23 ч 7 мин/);
  assert.match(value.summary, /более новых записей нет/);
  assert.match(value.impact, /Отставание.*отсутствует/);
  assert(value.facts.some(f => f.value === key)); assert(value.facts.some(f => f.value === 'UiEventOutboxWatcher'));
  assert(value.meaning.some(value => value.includes('47 проверках подряд') && value.includes('не число новых ошибок')));
  assert.equal(JSON.stringify(input), original);
});
test('Неизвестный lag не выдаётся за отсутствие новых записей; gap не становится количеством ошибок', () => {
  const unknown = describe(signal({ cursor_lag: null })); assert(!unknown.summary.includes('более новых записей нет'));
  assert.match(unknown.summary, /Неизвестно/);
  const lag = describe(signal({ cursor_lag: '9007199254740993123' })); assert.match(lag.summary, /есть более новые записи/);
  assert(!JSON.stringify(lag).includes('900719925474099'));
});
test('Снимок другой задачи не приписывается выбранной', () => {
  const value = describe(signal({ worker_key: 'operator-notification-watch.feedbacks', age_minutes: 9999 }));
  assert(!JSON.stringify(value).includes('9999')); assert.match(value.summary, /Возраст.*не указан/);
});
test('Текущий разбор отдельной задачи отбирает ровно её, даже если она теперь healthy', () => {
  const input = structuredClone(overview); input.runtime_checkpoints[2].stale = false; input.runtime_checkpoints[2].health_status = 'healthy';
  const value = transportDetails(input, { workerKey: key }); assert.equal(value.tasks.length, 1); assert.equal(value.tasks[0].workerKey, key);
  assert.match(value.tasks[0].explanation, /более новых нет/);
  assert.equal(transportDetails(input, { workerKey: 'not-found' }).tasks.length, 0);
});
test('Закрытая карточка остаётся исторической, неизвестная задача названа без выдуманного компонента', () => {
  const input = signal(); input.status = 'closed'; assert(describe(input).historical);
  input.signal_key = 'panel-runtime-checkpoints/unfamiliar-worker'; input.events = [];
  const value = describe(input); assert.match(value.title, /unfamiliar-worker/); assert(!JSON.stringify(value).includes('UiEventOutboxWatcher'));
});
test('SQL-причина отдельной записи не попадает в инцидент — правило R17', () => {
  assert(!JSON.stringify(describe(incident)).includes('chat_attachment_metadata'));
  const value = transportDetails(overview); assert(!JSON.stringify(value).includes('chat_attachment_metadata')); assert(!Object.hasOwn(value, 'inbound'));
});
