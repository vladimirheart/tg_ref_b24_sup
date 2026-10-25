'use strict';
const test = require('node:test'), assert = require('node:assert/strict');
const { describe, transportDetails } = require('../../main/resources/static/js/incident-descriptions.js');
const key = 'ui-event-outbox-watch';
test('Нет новых данных: старый cursor не попадает в текущие проблемы агрегата', () => {
  const raw = { runtime_checkpoints: [{ worker_key: key, cursor_lag: 0, age_minutes: 100000, stale: false, lagging: false, health_status: 'idle', incident_eligible: false }] };
  assert.deepEqual(transportDetails(raw).tasks, []);
});
test('Неиспользуемая позиция оценок не является текущей проблемой', () => {
  const workerKey = 'operator-notification-watch.feedbacks';
  const raw = { runtime_checkpoints: [{ worker_key: workerKey, age_minutes: 100000, health_status: 'not_monitored', stale: false, lagging: false, incident_eligible: false }] };
  assert.deepEqual(transportDetails(raw).tasks, []);
  assert(transportDetails(raw, { workerKey }).tasks[0].explanation.includes('не используется'));
});
test('Подтверждённое отставание объясняется временем наблюдения, не возрастом позиции', () => {
  const data = { worker_key: key, cursor_lag: 100, age_minutes: 100000, stale: true, lagging: true, incident_eligible: true, observed_backlog_minutes: 15 };
  const task = transportDetails({ runtime_checkpoints: [data] }).tasks[0];
  assert(task.explanation.includes('15 мин')); assert(!task.explanation.includes('100000'));
  const result = describe({ signal_type: 'integration_transport', signal_key: 'panel-runtime-checkpoints/' + key, events: [{ payload_json: JSON.stringify(data) }] });
  assert(result.facts.some(fact => fact.label.includes('Новые данные наблюдаются') && fact.value === '15 мин'));
});
test('Пауза мониторинга не объявляется остановкой рабочего обработчика', () => {
  const task = transportDetails({ runtime_checkpoints: [{ worker_key: key, health_status: 'monitoring_paused' }] }, { workerKey: key }).tasks[0];
  assert(task.explanation.includes('проверка') || task.explanation.includes('Проверка'));
  assert(task.explanation.includes('продолжает работать'));
});
