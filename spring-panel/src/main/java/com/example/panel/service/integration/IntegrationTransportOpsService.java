package com.example.panel.service.integration;

import com.example.panel.service.IncidentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class IntegrationTransportOpsService {

    private static final int RECENT_LIMIT = 20;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final InboundClientMessageIngestionService inboundClientMessageIngestionService;
    private final ConversationTicketCreationIngestionService conversationTicketCreationIngestionService;
    private final OutboundFeedbackPromptPublishOutboxService outboundFeedbackPromptPublishOutboxService;
    private final IncidentService incidentService;

    public IntegrationTransportOpsService(JdbcTemplate jdbcTemplate,
                                          ObjectMapper objectMapper,
                                          InboundClientMessageIngestionService inboundClientMessageIngestionService,
                                          ConversationTicketCreationIngestionService conversationTicketCreationIngestionService,
                                          OutboundFeedbackPromptPublishOutboxService outboundFeedbackPromptPublishOutboxService,
                                          IncidentService incidentService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.inboundClientMessageIngestionService = inboundClientMessageIngestionService;
        this.conversationTicketCreationIngestionService = conversationTicketCreationIngestionService;
        this.outboundFeedbackPromptPublishOutboxService = outboundFeedbackPromptPublishOutboxService;
        this.incidentService = incidentService;
    }

    public Map<String, Object> buildOverview() {
        Timestamp inboundStaleThreshold = timestamp(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(15));
        Timestamp outboundStaleThreshold = timestamp(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5));
        Map<String, Object> inboundSummary = new LinkedHashMap<>();
        inboundSummary.put("processing", count("integration_inbound_event_inbox", "status = 'processing'"));
        inboundSummary.put("failed", count("integration_inbound_event_inbox", "status = 'failed'"));
        inboundSummary.put("processed", count("integration_inbound_event_inbox", "status = 'processed'"));
        inboundSummary.put("stale_processing", countWithThreshold(
            "integration_inbound_event_inbox",
            "status = 'processing' AND processing_started_at IS NOT NULL AND processing_started_at < ?",
            inboundStaleThreshold
        ));

        Map<String, Object> outboundSummary = new LinkedHashMap<>();
        outboundSummary.put("queued", count("integration_transport_outbox", "status = 'queued'"));
        outboundSummary.put("processing", count("integration_transport_outbox", "status = 'processing'"));
        outboundSummary.put("failed", count("integration_transport_outbox", "status = 'failed'"));
        outboundSummary.put("published", count("integration_transport_outbox", "status = 'published'"));
        outboundSummary.put("stale_processing", countWithThreshold(
            "integration_transport_outbox",
            "status = 'processing' AND processing_started_at IS NOT NULL AND processing_started_at < ?",
            outboundStaleThreshold
        ));

        return Map.of(
            "success", true,
            "inbound", inboundSummary,
            "outbound", outboundSummary,
            "runtime_checkpoints", loadRuntimeCheckpoints(),
            "recent_failed_inbound", loadRecentInboxItems(inboundStaleThreshold),
            "recent_failed_outbound", loadRecentOutboxItems(outboundStaleThreshold),
            "transport_incidents", incidentService.listIncidentSummariesForSignalType("integration_transport")
        );
    }

    @Transactional
    public Map<String, Object> replayInboundEvent(String eventId, String actor) {
        InboxEntry entry = loadInboxEntry(eventId);
        if (entry == null) {
            throw new IllegalArgumentException("Transport inbox event not found: " + eventId);
        }
        replayInboundEntry(entry);
        incidentService.appendSignalEvent("integration_transport", "panel-rabbitmq-bridge", "manual_replay_inbound",
            "Manual replay requested for inbound event " + entry.eventId(),
            Map.of("event_id", entry.eventId(), "event_kind", entry.eventKind(), "actor", normalize(actor)),
            actor);
        return Map.of("success", true, "event_id", entry.eventId(), "action", "replayed");
    }

    @Transactional
    public Map<String, Object> replayFailedInboundEvents(int limit, String actor) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<InboxEntry> items = loadReplayableInboxEntries(safeLimit);
        int replayed = 0;
        for (InboxEntry entry : items) {
            replayInboundEntry(entry);
            replayed++;
        }
        if (replayed > 0) {
            incidentService.appendSignalEvent("integration_transport", "panel-rabbitmq-bridge", "manual_replay_inbound_batch",
                "Manual replay requested for " + replayed + " inbound event(s)",
                Map.of("count", replayed, "limit", safeLimit, "actor", normalize(actor)),
                actor);
        }
        return Map.of("success", true, "replayed", replayed, "limit", safeLimit);
    }

    @Transactional
    public Map<String, Object> requeueOutboundEvent(String eventId, String actor) {
        int updated = jdbcTemplate.update("""
                UPDATE integration_transport_outbox
                   SET status = 'queued',
                       available_at = CURRENT_TIMESTAMP,
                       processing_started_at = NULL,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE event_id = ?
                   AND status <> 'published'
                """,
            eventId
        );
        if (updated <= 0) {
            throw new IllegalArgumentException("Transport outbox event not found or already published: " + eventId);
        }
        outboundFeedbackPromptPublishOutboxService.dispatchBatch();
        incidentService.appendSignalEvent("integration_transport", "panel-rabbitmq-bridge", "manual_requeue_outbound",
            "Manual requeue requested for outbound event " + eventId,
            Map.of("event_id", eventId, "actor", normalize(actor)),
            actor);
        return Map.of("success", true, "event_id", eventId, "action", "requeued");
    }

    @Transactional
    public Map<String, Object> requeueFailedOutboundEvents(int limit, String actor) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<String> eventIds = jdbcTemplate.query("""
                SELECT event_id
                  FROM integration_transport_outbox
                 WHERE status = 'failed'
                    OR (status = 'processing'
                        AND processing_started_at IS NOT NULL
                        AND processing_started_at < ?)
                 ORDER BY updated_at ASC, event_id ASC
                LIMIT ?
                """,
            (rs, rowNum) -> rs.getString("event_id"),
            timestamp(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5)),
            safeLimit
        );
        int requeued = 0;
        for (String eventId : eventIds) {
            requeued += jdbcTemplate.update("""
                    UPDATE integration_transport_outbox
                       SET status = 'queued',
                           available_at = CURRENT_TIMESTAMP,
                           processing_started_at = NULL,
                           updated_at = CURRENT_TIMESTAMP
                     WHERE event_id = ?
                       AND status <> 'published'
                    """,
                eventId
            );
        }
        if (requeued > 0) {
            outboundFeedbackPromptPublishOutboxService.dispatchBatch();
            incidentService.appendSignalEvent("integration_transport", "panel-rabbitmq-bridge", "manual_requeue_outbound_batch",
                "Manual requeue requested for " + requeued + " outbound event(s)",
                Map.of("count", requeued, "limit", safeLimit, "actor", normalize(actor)),
                actor);
        }
        return Map.of("success", true, "requeued", requeued, "limit", safeLimit);
    }

    public TransportHealthSnapshot buildHealthSnapshot() {
        Timestamp inboundStaleThreshold = timestamp(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(15));
        Timestamp outboundStaleThreshold = timestamp(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5));
        return new TransportHealthSnapshot(
            toLong(count("integration_inbound_event_inbox", "status = 'failed'")),
            toLong(countWithThreshold("integration_inbound_event_inbox", "status = 'processing' AND processing_started_at IS NOT NULL AND processing_started_at < ?", inboundStaleThreshold)),
            toLong(count("integration_transport_outbox", "status = 'failed'")),
            toLong(count("integration_transport_outbox", "status = 'queued' OR status = 'processing'")),
            toLong(countWithThreshold("integration_transport_outbox", "status = 'processing' AND processing_started_at IS NOT NULL AND processing_started_at < ?", outboundStaleThreshold))
        );
    }

    private void replayInboundEntry(InboxEntry entry) {
        try {
            switch (normalize(entry.eventKind())) {
                case "client_message.active_ticket" -> {
                    InboundClientMessageEvent event = objectMapper.readValue(entry.payloadJson(), InboundClientMessageEvent.class);
                    inboundClientMessageIngestionService.ingest(event, entry.routingKey());
                }
                case "ticket.created.initial_contact" -> {
                    ConversationTicketCreatedEvent event = objectMapper.readValue(entry.payloadJson(), ConversationTicketCreatedEvent.class);
                    conversationTicketCreationIngestionService.ingest(event, entry.routingKey());
                }
                default -> throw new IllegalStateException("Unsupported inbound event kind: " + entry.eventKind());
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to replay inbound event " + entry.eventId(), ex);
        }
    }

    private List<Map<String, Object>> loadRuntimeCheckpoints() {
        return jdbcTemplate.query("""
                SELECT worker_key, cursor_text, updated_at
                  FROM runtime_worker_checkpoints
                 ORDER BY worker_key ASC
                """,
            (rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("worker_key", normalize(rs.getString("worker_key")));
                row.put("cursor_text", normalize(rs.getString("cursor_text")));
                row.put("updated_at", normalize(rs.getString("updated_at")));
                return row;
            }
        );
    }

    private List<Map<String, Object>> loadRecentInboxItems(Timestamp staleThreshold) {
        return jdbcTemplate.query("""
                SELECT event_id,
                       event_kind,
                       ticket_id,
                       routing_key,
                       status,
                       attempt_count,
                       processing_started_at,
                       updated_at,
                       last_error
                  FROM integration_inbound_event_inbox
                 WHERE status = 'failed'
                    OR (status = 'processing'
                        AND processing_started_at IS NOT NULL
                        AND processing_started_at < ?)
                 ORDER BY updated_at DESC, event_id DESC
                 LIMIT ?
                """,
            (rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("event_id", rs.getString("event_id"));
                row.put("event_kind", rs.getString("event_kind"));
                row.put("ticket_id", rs.getString("ticket_id"));
                row.put("routing_key", rs.getString("routing_key"));
                row.put("status", rs.getString("status"));
                row.put("attempt_count", rs.getInt("attempt_count"));
                row.put("processing_started_at", normalize(rs.getString("processing_started_at")));
                row.put("updated_at", normalize(rs.getString("updated_at")));
                row.put("last_error", normalize(rs.getString("last_error")));
                return row;
            },
            staleThreshold,
            RECENT_LIMIT
        );
    }

    private List<Map<String, Object>> loadRecentOutboxItems(Timestamp staleThreshold) {
        return jdbcTemplate.query("""
                SELECT event_id,
                       event_kind,
                       ticket_id,
                       routing_key,
                       status,
                       attempt_count,
                       processing_started_at,
                       updated_at,
                       last_error
                  FROM integration_transport_outbox
                 WHERE status = 'failed'
                    OR (status = 'processing'
                        AND processing_started_at IS NOT NULL
                        AND processing_started_at < ?)
                 ORDER BY updated_at DESC, event_id DESC
                 LIMIT ?
                """,
            (rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("event_id", rs.getString("event_id"));
                row.put("event_kind", rs.getString("event_kind"));
                row.put("ticket_id", rs.getString("ticket_id"));
                row.put("routing_key", rs.getString("routing_key"));
                row.put("status", rs.getString("status"));
                row.put("attempt_count", rs.getInt("attempt_count"));
                row.put("processing_started_at", normalize(rs.getString("processing_started_at")));
                row.put("updated_at", normalize(rs.getString("updated_at")));
                row.put("last_error", normalize(rs.getString("last_error")));
                return row;
            },
            staleThreshold,
            RECENT_LIMIT
        );
    }

    private List<InboxEntry> loadReplayableInboxEntries(int limit) {
        return jdbcTemplate.query("""
                SELECT event_id,
                       event_kind,
                       routing_key,
                       payload_json
                  FROM integration_inbound_event_inbox
                 WHERE status = 'failed'
                    OR (status = 'processing'
                        AND processing_started_at IS NOT NULL
                        AND processing_started_at < ?)
                 ORDER BY updated_at ASC, event_id ASC
                 LIMIT ?
                """,
            (rs, rowNum) -> new InboxEntry(
                rs.getString("event_id"),
                rs.getString("event_kind"),
                rs.getString("routing_key"),
                rs.getString("payload_json")
            ),
            timestamp(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(15)),
            limit
        );
    }

    private InboxEntry loadInboxEntry(String eventId) {
        List<InboxEntry> items = jdbcTemplate.query("""
                SELECT event_id, event_kind, routing_key, payload_json
                  FROM integration_inbound_event_inbox
                 WHERE event_id = ?
                """,
            (rs, rowNum) -> new InboxEntry(
                rs.getString("event_id"),
                rs.getString("event_kind"),
                rs.getString("routing_key"),
                rs.getString("payload_json")
            ),
            eventId
        );
        return items.isEmpty() ? null : items.get(0);
    }

    private Object count(String table, String predicate) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + table + " WHERE " + predicate,
            Long.class
        );
    }

    private Object countWithThreshold(String table, String predicate, Timestamp threshold) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + table + " WHERE " + predicate,
            Long.class,
            threshold
        );
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private Timestamp timestamp(OffsetDateTime value) {
        return value == null ? null : Timestamp.from(value.toInstant());
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    public record TransportHealthSnapshot(long inboundFailed,
                                          long inboundStaleProcessing,
                                          long outboundFailed,
                                          long outboundBacklog,
                                          long outboundStaleProcessing) {

        public boolean unhealthy() {
            return inboundFailed > 0
                || inboundStaleProcessing > 0
                || outboundFailed > 0
                || outboundStaleProcessing > 0
                || outboundBacklog >= 100;
        }

        public String severity() {
            if (inboundStaleProcessing > 0 || outboundStaleProcessing > 0 || outboundBacklog >= 250) {
                return "critical";
            }
            if (inboundFailed > 0 || outboundFailed > 0 || outboundBacklog >= 100) {
                return "high";
            }
            return "medium";
        }

        public String summary() {
            return String.format(Locale.ROOT,
                "inbound_failed=%d, inbound_stale=%d, outbound_failed=%d, outbound_backlog=%d, outbound_stale=%d",
                inboundFailed,
                inboundStaleProcessing,
                outboundFailed,
                outboundBacklog,
                outboundStaleProcessing
            );
        }
    }

    private record InboxEntry(String eventId,
                              String eventKind,
                              String routingKey,
                              String payloadJson) {
    }
}
