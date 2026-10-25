'use strict';
const test = require('node:test'), assert = require('node:assert/strict'), fs = require('node:fs'), path = require('node:path');
const { describe } = require('../../main/resources/static/js/incident-descriptions.js');
const fixtures = require('./fixtures/incident-descriptions-r13.cjs');
for (const fixture of fixtures) test('Понятное описание: ' + fixture.name, () => {
  const original = JSON.stringify(fixture.incident), result = describe(fixture.incident);
  assert(result.summary.includes(fixture.expected));
  assert(/[А-Яа-яЁё]/.test(result.title));
  assert.equal(JSON.stringify(fixture.incident), original, 'Исходные поля не изменяются');
  if (result.known) {
    assert(result.impact); assert(result.action);
    assert(!/stale|TTL|checkpoint|cursor|backlog|unhealthy|burn.rate/i.test([result.title, result.summary, result.impact, result.action].join(' ')));
  }
});
test('Не выдаём отсутствие метрик за остановку RabbitMQ', () => {
  const result = describe(fixtures.find(x => x.name === 'rabbit-metrics').incident);
  assert.match(result.impact, /неизвестно/); assert.match(result.impact, /могла продолжаться/);
  assert(!/RabbitMQ.*остановлен|доставка.*прекращена/i.test(result.summary + result.impact));
});
test('Нет выдуманного количества необработанных записей из cursor lag', () => {
  const result = describe(fixtures.find(x => x.name === 'feedbacks').incident);
  assert(!JSON.stringify(result).includes('10000')); assert(!JSON.stringify(result).includes('10 000'));
});
test('Данные восстановления не заменяют последнюю диагностическую запись', () => {
  const item = structuredClone(fixtures[1].incident);
  item.events.push({ payload_json: '{invalid' }, { payload_json: JSON.stringify({ status: 'healthy' }) });
  item.status = 'closed';
  const result = describe(item); assert(result.historical); assert.equal(result.facts[0].value, '14 мин');
});
test('Все правила из репозитория получают пояснение даже в list без events', () => {
  const rules = fs.readFileSync(path.resolve(__dirname, '../../../../observability/prometheus/rules/iguana-alerts.yml'), 'utf8');
  const names = Array.from(rules.matchAll(/alert:\s*(\w+)/g), m => m[1]); assert.equal(names.length, 16);
  for (const name of names) assert(describe({ signal_type: 'alertmanager', title: 'Alertmanager: ' + name }).known, name);
  assert(!describe({ signal_type: 'alertmanager', title: 'X_IguanaPanel5xxRateHighSuffix' }).known, 'Не переводим похожий чужой alert');
});
test('Статусы ключей доступа и старый payload без signal_context', () => {
  for (const status of ['source_removed', 'missing_secret', 'expired', 'expires_soon', 'rotation_overdue', 'rotation_due_soon']) {
    const result = describe({ signal_type: 'credential_rotation', events: [{ payload_json: JSON.stringify({ last_status: status }) }] });
    assert(!result.title.includes('Нужно проверить'), status);
  }
});
test('Нулевые, повреждённые, отрицательные и неизвестные данные не превращаются в диагноз', () => {
  for (const value of [null, undefined, [], {}, 'invalid']) assert.doesNotThrow(() => describe(value));
  const result = describe({ signal_type: 'integration_transport', signal_key: 'panel-rabbitmq-bridge', summary: 'inbound_failed=0,outbound_backlog=0,unknown_count=500,outbound_stale=-2,inbound_stale=NaN' });
  assert.equal(result.facts.length, 0); assert.match(result.summary, /не сохранены/);
  const unknown = describe({ signal_type: 'future', title: 'Ручное название', summary: 'Исходная причина' });
  assert.equal(unknown.title, 'Ручное название'); assert.equal(unknown.summary, 'Исходная причина'); assert(!unknown.known);
});
test('Сигнал ограничения частоты не приписываем обычной ошибке доставки', () => {
  const result = describe({ signal_type: 'provider_delivery', signal_key: 'channel-7/rate_limit' });
  assert.match(result.title, /частоту/);
});
test('Адрес проверки не раскрывает credentials, path или query из URL', () => {
  const result = describe({ signal_type: 'alertmanager', events: [{ payload_json: JSON.stringify({ alertname: 'IguanaPanelWebReplicaDown', labels: { instance: 'https://fixture-user:fixture-password@panel.test:8443/private?token=fixture-token#secret' } }) }] });
  assert.deepEqual(result.facts, [{ label: 'Адрес проверки', value: 'panel.test:8443' }]);
  assert(!JSON.stringify(result).includes('fixture-password'));
});
test('Подключение каталога перед workbench и версии кеша совпадают', () => {
  const html = fs.readFileSync(path.resolve(__dirname, '../../main/resources/templates/incidents/index.html'), 'utf8');
  assert(html.indexOf('/js/incident-descriptions.js') < html.indexOf('/js/incidents-workbench.js'));
  for (const name of ['incident-descriptions', 'incidents-workbench']) assert(html.includes(`/js/${name}.js(v='20261025-incidents-r18')`));
});
