const incident = { id: 731, incident_key: 'INC-731', status: 'open', severity: 'high', signal_type: 'integration_transport', signal_key: 'panel-transport-sustained-pressure', title: 'Sustained transport pressure', summary: 'Transport contour остаётся unhealthy несколько последовательных snapshot-циклов подряд.', events: [{ payload_json: JSON.stringify({ latest_summary: 'inbound_failed=4, inbound_stale=0, outbound_failed=0, outbound_backlog=0, outbound_stale=0, stale_checkpoints=3, lagging_checkpoints=1', unhealthy_streak: 10 }) }] };
const overview = {
  inbound: { failed: 4, stale_processing: 0 }, outbound: { failed: 0, queued: 0, processing: 0 }, health_snapshot: { stale_checkpoint_count: 3 },
  recent_failed_inbound: Array.from({ length: 4 }, (_, i) => ({ event_id: 'fixture-event-' + i, ticket_id: i === 0 ? ' T/№9007199254740993123 ' : 'T-' + i, event_kind: 'client_message.active_ticket', status: 'failed', last_error: 'PreparedStatementCallback; bad SQL grammar [INSERT INTO chat_attachment_metadata (...)]', updated_at: '2026-08-24T12:51:22Z' })),
  runtime_checkpoints: [
    { worker_key: 'operator-notification-watch.chat-history', stale: true, lagging: true, cursor_lag: 9000, age_minutes: 40, stale_threshold_minutes: 15, updated_at: '2026-10-24T08:00:00Z' },
    { worker_key: 'operator-notification-watch.feedbacks', stale: true, lagging: false, cursor_lag: 0, age_minutes: 45, stale_threshold_minutes: 20, updated_at: '2026-10-24T07:55:00Z' },
    { worker_key: 'ui-event-outbox-watch', stale: true, lagging: false, cursor_lag: 0, age_minutes: 35, stale_threshold_minutes: 10, updated_at: '2026-10-24T08:05:00Z' }
  ]
};
module.exports = { incident, overview };
