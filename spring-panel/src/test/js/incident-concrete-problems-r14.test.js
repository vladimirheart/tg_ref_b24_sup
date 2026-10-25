const test = require('node:test'), assert = require('node:assert/strict');
const { describe, transportDetails } = require('../../main/resources/static/js/incident-descriptions.js');
const { incident, overview } = require('./fixtures/incident-concrete-problems-r14.cjs');
test('Десять проверок не превращаются в десять новых ошибок', () => {
  const result = describe(incident);
  assert(!result.summary.includes('данных: 4')); assert.match(result.summary, /работы: 3/); assert.match(result.summary, /данных: 1/);
  assert(result.meaning.some(text => text.includes('10 проверках подряд') && text.includes('не число новых сбоев')));
  assert(result.meaning.some(text => text.includes('не являются основанием автоматического инцидента')));
});
test('Ошибки отдельных записей не переносятся из диагностики в данные карточки', () => {
  const original = JSON.stringify(overview), result = transportDetails(overview);
  assert(!Object.hasOwn(result, 'inbound')); assert(!Object.hasOwn(result, 'outbound'));
  assert(!JSON.stringify(result).includes('fixture-event')); assert(!JSON.stringify(result).includes('chat_attachment_metadata'));
  assert(!JSON.stringify(result).includes('9007199254740993123')); assert.equal(JSON.stringify(overview), original);
});
test('Три конкретные задачи, две без новых записей — не три остановленных процесса', () => {
  const result = transportDetails(overview);
  assert.equal(result.tasks.length, 3);
  assert.match(result.tasks[0].title, /сообщениях/); assert.match(result.tasks[0].explanation, /Есть новые данные/);
  assert.equal(result.tasks.filter(item => item.explanation.includes('более новых нет')).length, 2);
  assert(!JSON.stringify(result.tasks).includes('9000')); assert(!JSON.stringify(result.tasks).includes('остановлен'));
});
test('Количество failed не попадает в карточку, системные счётчики сохраняются', () => {
  const result = transportDetails({ inbound: { failed: 80 }, recent_failed_inbound: overview.recent_failed_inbound });
  assert(!Object.hasOwn(result, 'inboundFailed')); assert(!JSON.stringify(result).includes('80'));
  assert.equal(transportDetails({ outbound: { queued: 250 } }).outboundBacklog, 250);
});
test('Ничего не выводим из повреждённого ответа и неизвестной причины', () => {
  for (const value of [null, [], {}, 'invalid']) assert.doesNotThrow(() => transportDetails(value));
  const result = transportDetails({ recent_failed_inbound: [{ status: 'failed', last_error: 'TOP_SECRET_UNRECOGNIZED <script>alert(1)</script>' }, { status: 'processed' }] });
  assert.equal(result.tasks.length, 0);
  assert(!JSON.stringify(result).includes('TOP_SECRET')); assert(!JSON.stringify(result).includes('<script>'));
});
test('Любые per-record ошибки и ID остаются только в исходных диагностических данных', () => {
  for (const [error, expected] of [['Inbound event ticket not found: 5', 'обращение'], ['Inbound event channel not found: 5', 'подключение'], ['Inbound event channel 5 does not match ticket channel', 'не совпало']]) {
    const result = transportDetails({ recent_failed_inbound: [{ status: 'failed', last_error: error }] });
    assert(!JSON.stringify(result).includes(error)); assert(!Object.hasOwn(result, 'inbound'));
  }
});
