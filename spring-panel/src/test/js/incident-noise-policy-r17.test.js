'use strict';
const test = require('node:test'), assert = require('node:assert/strict');
const { describe, transportDetails } = require('../../main/resources/static/js/incident-descriptions.js');
const event = { event_id: '03298e2e-8d69-4586-84d6-447a0897b3f7', ticket_id: 'f5b6bedf-dfac-4683-940c-5617b57314fe', event_kind: 'client_message.active_ticket', status: 'failed', last_error: 'bad SQL grammar INSERT INTO chat_attachment_metadata', updated_at: '2026-08-24T12:51:22Z' };
for (const key of ['panel-rabbitmq-bridge', 'panel-transport-sustained-pressure']) test('Failed records не являются причиной в описании: ' + key, () => {
  const input = { signal_type: 'integration_transport', signal_key: key, summary: 'inbound_failed=4, outbound_failed=8', events: [{ payload_json: JSON.stringify({ latest_summary: 'inbound_failed=4, outbound_failed=8', inbound_failed: 4, outbound_failed: 8, unhealthy_streak: 47 }) }] };
  const before = JSON.stringify(input), value = describe(input);
  assert(!value.summary.includes('данных: 4')); assert(!value.summary.includes('отправки: 8'));
  assert(!value.facts.some(fact => fact.label.includes('Не завершена обработка')));
  assert(value.meaning.some(text => text.includes('не являются основанием автоматического инцидента')));
  assert.equal(JSON.stringify(input), before);
});
test('Указанные владельцем UUID и SQL не покидают диагностические данные', () => {
  const raw = { inbound: { failed: 4 }, outbound: { failed: 8 }, recent_failed_inbound: [event], recent_failed_outbound: [event] }, before = JSON.stringify(raw);
  const value = JSON.stringify(transportDetails(raw));
  for (const forbidden of [event.event_id, event.ticket_id, event.event_kind, 'chat_attachment_metadata', 'last_error', 'inboundFailed', 'outboundFailed']) assert(!value.includes(forbidden), forbidden);
  assert.equal(JSON.stringify(raw), before); assert.equal(raw.recent_failed_inbound[0].last_error, event.last_error);
});
test('Системная нагрузка остаётся видимой независимо от failed records', () => {
  const value = transportDetails({ inbound: { failed: 4, stale_processing: 2 }, outbound: { failed: 8, queued: 250, stale_processing: 3 } });
  assert.equal(value.inboundStale, 2); assert.equal(value.outboundStale, 3); assert.equal(value.outboundBacklog, 250);
});
