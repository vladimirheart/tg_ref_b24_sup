const test = require('node:test'), assert = require('node:assert/strict');
const { describe, transportDetails } = require('../../main/resources/static/js/incident-descriptions.js');
const { incident, overview } = require('./fixtures/incident-concrete-problems-r14.cjs');
test('Десять проверок не превращаются в десять новых ошибок', () => {
  const result = describe(incident);
  assert.match(result.summary, /данных: 4/); assert.match(result.summary, /работы: 3/); assert.match(result.summary, /данных: 1/);
  assert(result.meaning.some(text => text.includes('10 проверках подряд') && text.includes('не число новых сбоев')));
  assert(result.meaning.some(text => text.includes('сообщение клиента')));
});
test('Конкретная операция и причина берутся из записи, не угадываются из счётчика', () => {
  const original = JSON.stringify(overview), result = transportDetails(overview);
  assert.equal(result.inbound.length, 4); assert.match(result.inbound[0].title, /Сообщение клиента/);
  assert.match(result.inbound[0].explanation, /сведения о вложении/); assert.match(result.inbound[0].explanation, /не следует.*потерян/);
  assert.equal(result.inbound[0].ticket, ' T/№9007199254740993123 ');
  assert.equal(result.inbound[0].updatedAt, '2026-08-24T12:51:22Z'); assert.equal(JSON.stringify(overview), original);
});
test('Три конкретные задачи, две без новых записей — не три остановленных процесса', () => {
  const result = transportDetails(overview);
  assert.equal(result.tasks.length, 3);
  assert.match(result.tasks[0].title, /сообщениях/); assert.match(result.tasks[0].explanation, /Есть новые данные/);
  assert.equal(result.tasks.filter(item => item.explanation.includes('более новых нет')).length, 2);
  assert(!JSON.stringify(result.tasks).includes('9000')); assert(!JSON.stringify(result.tasks).includes('остановлен'));
});
test('Неполная выборка не подменяет общий счётчик', () => {
  const result = transportDetails({ inbound: { failed: 80 }, recent_failed_inbound: overview.recent_failed_inbound });
  assert.equal(result.inboundFailed, 80); assert.equal(result.inbound.length, 4);
});
test('Ничего не выводим из повреждённого ответа и неизвестной причины', () => {
  for (const value of [null, [], {}, 'invalid']) assert.doesNotThrow(() => transportDetails(value));
  const result = transportDetails({ recent_failed_inbound: [{ status: 'failed', last_error: 'TOP_SECRET_UNRECOGNIZED <script>alert(1)</script>' }, { status: 'processed' }] });
  assert.equal(result.inbound.length, 1); assert.match(result.inbound[0].explanation, /технический текст/);
  assert(!JSON.stringify(result).includes('TOP_SECRET')); assert(!JSON.stringify(result).includes('<script>'));
});
test('Объяснения известных ошибок связи с обращением не меняют ID и данные', () => {
  for (const [error, expected] of [['Inbound event ticket not found: 5', 'обращение'], ['Inbound event channel not found: 5', 'подключение'], ['Inbound event channel 5 does not match ticket channel', 'не совпало']]) {
    const result = transportDetails({ recent_failed_inbound: [{ status: 'failed', last_error: error }] });
    assert(result.inbound[0].explanation.includes(expected));
  }
});
