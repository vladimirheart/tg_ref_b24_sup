package com.example.panel.service.integration;

import com.example.panel.converter.LenientOffsetDateTimeConverter;
import com.example.panel.service.IncidentService;
import com.example.panel.service.RuntimeWorkerCheckpointService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
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
    private static final int RECENT_OPERATION_LIMIT = 25;
    private static final int RECENT_WORKER_HISTORY_LIMIT = 24;
    private static final int RECENT_WORKER_OPERATION_LIMIT = 20;
    private static final Duration INBOUND_STALE_TIMEOUT = Duration.ofMinutes(15);
    private static final Duration OUTBOUND_STALE_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration RECENT_MANUAL_OPERATION_WINDOW = Duration.ofHours(6);
    private static final Duration DEFAULT_TREND_WINDOW = Duration.ofHours(24);
    private static final Duration DEFAULT_WORKER_TREND_WINDOW = Duration.ofHours(24);
    private static final long SUSTAINED_UNHEALTHY_STREAK_THRESHOLD = 3L;
    private static final long SUSTAINED_CRITICAL_STREAK_THRESHOLD = 2L;
    private static final LenientOffsetDateTimeConverter DATE_TIME_CONVERTER = new LenientOffsetDateTimeConverter();
    private static final List<WorkerDiagnosticsDefinition> WORKER_DIAGNOSTICS = List.of(
        new WorkerDiagnosticsDefinition("operator-notification-watch.chat-history", "Operator chat-history watcher", "chat_history", "id", Duration.ofMinutes(15), 25L),
        new WorkerDiagnosticsDefinition("operator-notification-watch.feedbacks", "Operator feedback watcher", "feedbacks", "id", Duration.ofMinutes(20), 10L),
        new WorkerDiagnosticsDefinition("ui-event-outbox-watch", "UI event outbox watcher", "ui_event_outbox", "id", Duration.ofMinutes(10), 50L)
    );

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final InboundClientMessageIngestionService inboundClientMessageIngestionService;
    private final ConversationTicketCreationIngestionService conversationTicketCreationIngestionService;
    private final OutboundFeedbackPromptPublishOutboxService outboundFeedbackPromptPublishOutboxService;
    private final IncidentService incidentService;
    private final RuntimeWorkerCheckpointService runtimeWorkerCheckpointService;

    public IntegrationTransportOpsService(JdbcTemplate jdbcTemplate,
                                          ObjectMapper objectMapper,
                                          InboundClientMessageIngestionService inboundClientMessageIngestionService,
                                          ConversationTicketCreationIngestionService conversationTicketCreationIngestionService,
                                          OutboundFeedbackPromptPublishOutboxService outboundFeedbackPromptPublishOutboxService,
                                          IncidentService incidentService,
                                          RuntimeWorkerCheckpointService runtimeWorkerCheckpointService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.inboundClientMessageIngestionService = inboundClientMessageIngestionService;
        this.conversationTicketCreationIngestionService = conversationTicketCreationIngestionService;
        this.outboundFeedbackPromptPublishOutboxService = outboundFeedbackPromptPublishOutboxService;
        this.incidentService = incidentService;
        this.runtimeWorkerCheckpointService = runtimeWorkerCheckpointService;
    }

    public Map<String, Object> buildOverview() {
        Timestamp inboundStaleThreshold = timestamp(OffsetDateTime.now(ZoneOffset.UTC).minus(INBOUND_STALE_TIMEOUT));
        Timestamp outboundStaleThreshold = timestamp(OffsetDateTime.now(ZoneOffset.UTC).minus(OUTBOUND_STALE_TIMEOUT));
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

        TransportHealthSnapshot snapshot = buildHealthSnapshot();
        List<Map<String, Object>> runtimeCheckpoints = loadRuntimeCheckpoints();
        List<Map<String, Object>> recentOperations = loadRecentOperationLogs(RECENT_OPERATION_LIMIT);
        Map<String, Object> trendSummary = buildTrendSummary(DEFAULT_TREND_WINDOW);
        List<Map<String, Object>> transportIncidents = incidentService.listIncidentSummariesForSignalType("integration_transport");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("inbound", inboundSummary);
        payload.put("outbound", outboundSummary);
        payload.put("runtime_checkpoints", runtimeCheckpoints);
        payload.put("recent_failed_inbound", loadRecentInboxItems(inboundStaleThreshold));
        payload.put("recent_failed_outbound", loadRecentOutboxItems(outboundStaleThreshold));
        payload.put("transport_incidents", transportIncidents);
        payload.put("recent_operations", recentOperations);
        payload.put("alerts", buildObservabilityAlerts(snapshot, trendSummary));
        payload.put("health_snapshot", snapshot.toMap());
        payload.put("recent_snapshots", loadRecentHealthSnapshots(24));
        payload.put("trend_summary", trendSummary);
        return payload;
    }

    public Map<String, Object> loadInboundEventDetail(String eventId) {
        return loadEventDetail(
            "integration_inbound_event_inbox",
            List.of("event_id", "event_kind", "ticket_id", "routing_key", "status", "attempt_count", "processing_started_at", "updated_at", "last_error", "payload_json"),
            eventId,
            "inbound_event"
        );
    }

    public Map<String, Object> loadOutboundEventDetail(String eventId) {
        return loadEventDetail(
            "integration_transport_outbox",
            List.of("event_id", "event_kind", "ticket_id", "routing_key", "status", "attempt_count", "processing_started_at", "updated_at", "last_error", "payload_json", "published_at"),
            eventId,
            "outbound_event"
        );
    }

    public Map<String, Object> loadTicketTransportDebug(String ticketId) {
        String normalizedTicketId = normalize(ticketId);
        if (!StringUtils.hasText(normalizedTicketId)) {
            throw new IllegalArgumentException("Ticket id is required for transport debug.");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("ticket_id", normalizedTicketId);
        payload.put("inbound_events", loadInboxItemsForTicket(normalizedTicketId, 25));
        payload.put("outbound_events", loadOutboxItemsForTicket(normalizedTicketId, 25));
        payload.put("related_incidents", incidentService.listIncidentSummariesForTicket(normalizedTicketId));
        payload.put("recent_operations", loadRecentOperationLogsForTicket(normalizedTicketId, 25));
        return payload;
    }

    public Map<String, Object> loadWorkerDiagnostics(String workerKey) {
        String normalizedWorkerKey = normalize(workerKey);
        if (!StringUtils.hasText(normalizedWorkerKey)) {
            throw new IllegalArgumentException("Worker key is required for transport diagnostics.");
        }
        WorkerCheckpointSnapshot snapshot = loadCurrentWorkerCheckpointSnapshot(normalizedWorkerKey);
        if (snapshot == null) {
            throw new IllegalArgumentException("Runtime worker checkpoint not found: " + normalizedWorkerKey);
        }
        Map<String, Object> trendSummary = buildWorkerTrendSummary(normalizedWorkerKey, DEFAULT_WORKER_TREND_WINDOW);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("worker", snapshot.toMap());
        payload.put("trend_summary", trendSummary);
        payload.put("recent_history", loadRecentWorkerHealthSnapshots(normalizedWorkerKey, RECENT_WORKER_HISTORY_LIMIT));
        payload.put("recent_operations", loadRecentOperationLogsForWorker(normalizedWorkerKey, RECENT_WORKER_OPERATION_LIMIT));
        payload.put("related_incidents", incidentService.listIncidentSummariesForSignal("integration_transport",
            workerSignalKey(normalizedWorkerKey)));
        payload.put("recommendations", buildWorkerRecommendations(snapshot, trendSummary));
        return payload;
    }

    public void recordHealthSnapshot(TransportHealthSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO integration_transport_health_snapshots(
                    inbound_failed,
                    inbound_stale_processing,
                    outbound_failed,
                    outbound_backlog,
                    outbound_stale_processing,
                    stale_checkpoint_count,
                    lagging_checkpoint_count,
                    recent_manual_operations,
                    severity,
                    summary_text,
                    created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
            snapshot.inboundFailed(),
            snapshot.inboundStaleProcessing(),
            snapshot.outboundFailed(),
            snapshot.outboundBacklog(),
            snapshot.outboundStaleProcessing(),
            snapshot.staleCheckpointCount(),
            snapshot.laggingCheckpointCount(),
            snapshot.recentManualOperations(),
            snapshot.severity(),
            snapshot.summary()
        );
    }

    public void recordWorkerHealthSnapshots() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (WorkerCheckpointSnapshot snapshot : loadWorkerCheckpointSnapshots(now)) {
            recordWorkerHealthSnapshot(snapshot);
        }
    }

    public void deleteHealthSnapshotsOlderThan(Duration retention) {
        Duration safeRetention = retention == null || retention.isZero() || retention.isNegative()
            ? Duration.ofDays(30)
            : retention;
        jdbcTemplate.update("""
                DELETE FROM integration_transport_health_snapshots
                 WHERE created_at < ?
                """,
            timestamp(OffsetDateTime.now(ZoneOffset.UTC).minus(safeRetention))
        );
    }

    public void deleteWorkerHealthSnapshotsOlderThan(Duration retention) {
        Duration safeRetention = retention == null || retention.isZero() || retention.isNegative()
            ? Duration.ofDays(30)
            : retention;
        jdbcTemplate.update("""
                DELETE FROM integration_transport_worker_health_snapshots
                 WHERE created_at < ?
                """,
            timestamp(OffsetDateTime.now(ZoneOffset.UTC).minus(safeRetention))
        );
    }

    public Map<String, Object> buildTrendSummary(Duration window) {
        Duration safeWindow = window == null || window.isZero() || window.isNegative() ? DEFAULT_TREND_WINDOW : window;
        List<TransportHealthSnapshotRow> snapshots = loadHealthSnapshotRows(safeWindow, 288);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("window_hours", safeWindow.toHours());
        summary.put("snapshot_count", snapshots.size());
        if (snapshots.isEmpty()) {
            summary.put("status", "no_data");
            summary.put("sustained_pressure", false);
            summary.put("unhealthy_snapshot_count", 0);
            summary.put("critical_snapshot_count", 0);
            summary.put("unhealthy_streak", 0);
            summary.put("critical_streak", 0);
            summary.put("peak_outbound_backlog", 0);
            summary.put("peak_stale_checkpoints", 0);
            summary.put("peak_recent_manual_operations", 0);
            summary.put("latest_created_at", null);
            summary.put("latest_severity", null);
            summary.put("latest_summary", null);
            return summary;
        }
        long unhealthyCount = snapshots.stream().filter(TransportHealthSnapshotRow::unhealthy).count();
        long criticalCount = snapshots.stream().filter(snapshot -> "critical".equalsIgnoreCase(snapshot.severity())).count();
        long unhealthyStreak = consecutiveCount(snapshots, TransportHealthSnapshotRow::unhealthy);
        long criticalStreak = consecutiveCount(snapshots, snapshot -> "critical".equalsIgnoreCase(snapshot.severity()));
        long peakOutboundBacklog = snapshots.stream().map(TransportHealthSnapshotRow::outboundBacklog).max(Comparator.naturalOrder()).orElse(0L);
        long peakStaleCheckpoints = snapshots.stream().map(TransportHealthSnapshotRow::staleCheckpointCount).max(Comparator.naturalOrder()).orElse(0L);
        long peakRecentManualOperations = snapshots.stream().map(TransportHealthSnapshotRow::recentManualOperations).max(Comparator.naturalOrder()).orElse(0L);
        boolean sustainedPressure = unhealthyStreak >= SUSTAINED_UNHEALTHY_STREAK_THRESHOLD
            || criticalStreak >= SUSTAINED_CRITICAL_STREAK_THRESHOLD;
        TransportHealthSnapshotRow latest = snapshots.get(0);
        summary.put("status", sustainedPressure ? "pressure" : (latest.unhealthy() ? "monitor" : "healthy"));
        summary.put("sustained_pressure", sustainedPressure);
        summary.put("unhealthy_snapshot_count", unhealthyCount);
        summary.put("critical_snapshot_count", criticalCount);
        summary.put("unhealthy_streak", unhealthyStreak);
        summary.put("critical_streak", criticalStreak);
        summary.put("peak_outbound_backlog", peakOutboundBacklog);
        summary.put("peak_stale_checkpoints", peakStaleCheckpoints);
        summary.put("peak_recent_manual_operations", peakRecentManualOperations);
        summary.put("latest_created_at", latest.createdAt());
        summary.put("latest_severity", latest.severity());
        summary.put("latest_summary", latest.summaryText());
        return summary;
    }

    public Map<String, Object> buildWorkerTrendSummary(String workerKey, Duration window) {
        String normalizedWorkerKey = normalize(workerKey);
        if (!StringUtils.hasText(normalizedWorkerKey)) {
            throw new IllegalArgumentException("Worker key is required for worker trend summary.");
        }
        Duration safeWindow = window == null || window.isZero() || window.isNegative()
            ? DEFAULT_WORKER_TREND_WINDOW
            : window;
        List<WorkerHealthSnapshotRow> snapshots = loadWorkerHealthSnapshotRows(normalizedWorkerKey, safeWindow, 288);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("worker_key", normalizedWorkerKey);
        summary.put("window_hours", safeWindow.toHours());
        summary.put("snapshot_count", snapshots.size());
        if (snapshots.isEmpty()) {
            summary.put("status", "no_data");
            summary.put("sustained_pressure", false);
            summary.put("unhealthy_snapshot_count", 0);
            summary.put("critical_snapshot_count", 0);
            summary.put("unhealthy_streak", 0);
            summary.put("critical_streak", 0);
            summary.put("peak_cursor_lag", 0);
            summary.put("peak_age_minutes", 0);
            summary.put("latest_created_at", null);
            summary.put("latest_summary", null);
            return summary;
        }
        long unhealthyCount = snapshots.stream().filter(WorkerHealthSnapshotRow::unhealthy).count();
        long criticalCount = snapshots.stream().filter(WorkerHealthSnapshotRow::critical).count();
        long unhealthyStreak = consecutiveCount(snapshots, WorkerHealthSnapshotRow::unhealthy);
        long criticalStreak = consecutiveCount(snapshots, WorkerHealthSnapshotRow::critical);
        long peakCursorLag = snapshots.stream().map(WorkerHealthSnapshotRow::cursorLag).filter(java.util.Objects::nonNull)
            .max(Comparator.naturalOrder()).orElse(0L);
        long peakAgeMinutes = snapshots.stream().map(WorkerHealthSnapshotRow::ageMinutes).filter(java.util.Objects::nonNull)
            .max(Comparator.naturalOrder()).orElse(0L);
        boolean sustainedPressure = unhealthyStreak >= SUSTAINED_UNHEALTHY_STREAK_THRESHOLD
            || criticalStreak >= SUSTAINED_CRITICAL_STREAK_THRESHOLD;
        WorkerHealthSnapshotRow latest = snapshots.get(0);
        summary.put("status", sustainedPressure ? "pressure" : (latest.unhealthy() ? "monitor" : "healthy"));
        summary.put("sustained_pressure", sustainedPressure);
        summary.put("unhealthy_snapshot_count", unhealthyCount);
        summary.put("critical_snapshot_count", criticalCount);
        summary.put("unhealthy_streak", unhealthyStreak);
        summary.put("critical_streak", criticalStreak);
        summary.put("peak_cursor_lag", peakCursorLag);
        summary.put("peak_age_minutes", peakAgeMinutes);
        summary.put("latest_created_at", latest.createdAt());
        summary.put("latest_summary", latest.summaryText());
        summary.put("latest_health_status", latest.healthStatus());
        return summary;
    }

    @Transactional
    public Map<String, Object> replayInboundEvent(String eventId, String actor) {
        InboxEntry entry = loadInboxEntry(eventId);
        if (entry == null) {
            throw new IllegalArgumentException("Transport inbox event not found: " + eventId);
        }
        replayInboundEntry(entry);
        recordOperation(
            "manual_replay_inbound",
            "inbound_event",
            entry.eventId(),
            entry.ticketId(),
            null,
            "success",
            actor,
            "Manual replay for inbound event " + entry.eventId(),
            Map.of("event_kind", normalize(entry.eventKind()), "routing_key", normalize(entry.routingKey()))
        );
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
            recordOperation(
                "manual_replay_inbound_batch",
                "inbound_batch",
                "failed-or-stale",
                null,
                null,
                "success",
                actor,
                "Manual replay for " + replayed + " inbound event(s)",
                Map.of("count", replayed, "limit", safeLimit)
            );
            incidentService.appendSignalEvent("integration_transport", "panel-rabbitmq-bridge", "manual_replay_inbound_batch",
                "Manual replay requested for " + replayed + " inbound event(s)",
                Map.of("count", replayed, "limit", safeLimit, "actor", normalize(actor)),
                actor);
        }
        return Map.of("success", true, "replayed", replayed, "limit", safeLimit);
    }

    @Transactional
    public Map<String, Object> replayFailedInboundEventsForTicket(String ticketId, int limit, String actor) {
        String normalizedTicketId = normalize(ticketId);
        if (!StringUtils.hasText(normalizedTicketId)) {
            throw new IllegalArgumentException("Transport inbox replay requires ticket id.");
        }
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<InboxEntry> items = loadReplayableInboxEntriesForTicket(normalizedTicketId, safeLimit);
        int replayed = 0;
        for (InboxEntry entry : items) {
            replayInboundEntry(entry);
            replayed++;
        }
        if (replayed > 0) {
            recordOperation(
                "manual_replay_inbound_ticket",
                "ticket",
                normalizedTicketId,
                normalizedTicketId,
                null,
                "success",
                actor,
                "Manual inbound replay for ticket " + normalizedTicketId + " (" + replayed + " event(s))",
                Map.of("count", replayed, "limit", safeLimit)
            );
            incidentService.appendSignalEvent("integration_transport", "panel-rabbitmq-bridge", "manual_replay_inbound_ticket",
                "Manual replay requested for " + replayed + " inbound event(s) of ticket " + normalizedTicketId,
                Map.of("count", replayed, "limit", safeLimit, "ticket_id", normalizedTicketId, "actor", normalize(actor)),
                actor);
        }
        return Map.of("success", true, "ticket_id", normalizedTicketId, "replayed", replayed, "limit", safeLimit);
    }

    @Transactional
    public Map<String, Object> requeueOutboundEvent(String eventId, String actor) {
        OutboxRef entry = loadOutboxRef(eventId);
        if (entry == null) {
            throw new IllegalArgumentException("Transport outbox event not found: " + eventId);
        }
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
        recordOperation(
            "manual_requeue_outbound",
            "outbound_event",
            eventId,
            entry.ticketId(),
            null,
            "success",
            actor,
            "Manual requeue for outbound event " + eventId,
            Map.of("event_kind", normalize(entry.eventKind()), "routing_key", normalize(entry.routingKey()))
        );
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
            recordOperation(
                "manual_requeue_outbound_batch",
                "outbound_batch",
                "failed-or-stale",
                null,
                null,
                "success",
                actor,
                "Manual requeue for " + requeued + " outbound event(s)",
                Map.of("count", requeued, "limit", safeLimit)
            );
            incidentService.appendSignalEvent("integration_transport", "panel-rabbitmq-bridge", "manual_requeue_outbound_batch",
                "Manual requeue requested for " + requeued + " outbound event(s)",
                Map.of("count", requeued, "limit", safeLimit, "actor", normalize(actor)),
                actor);
        }
        return Map.of("success", true, "requeued", requeued, "limit", safeLimit);
    }

    @Transactional
    public Map<String, Object> requeueFailedOutboundEventsForTicket(String ticketId, int limit, String actor) {
        String normalizedTicketId = normalize(ticketId);
        if (!StringUtils.hasText(normalizedTicketId)) {
            throw new IllegalArgumentException("Transport outbox requeue requires ticket id.");
        }
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<String> eventIds = loadReplayableOutboxEventIdsForTicket(normalizedTicketId, safeLimit);
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
            recordOperation(
                "manual_requeue_outbound_ticket",
                "ticket",
                normalizedTicketId,
                normalizedTicketId,
                null,
                "success",
                actor,
                "Manual outbound requeue for ticket " + normalizedTicketId + " (" + requeued + " event(s))",
                Map.of("count", requeued, "limit", safeLimit)
            );
            incidentService.appendSignalEvent("integration_transport", "panel-rabbitmq-bridge", "manual_requeue_outbound_ticket",
                "Manual requeue requested for " + requeued + " outbound event(s) of ticket " + normalizedTicketId,
                Map.of("count", requeued, "limit", safeLimit, "ticket_id", normalizedTicketId, "actor", normalize(actor)),
                actor);
        }
        return Map.of("success", true, "ticket_id", normalizedTicketId, "requeued", requeued, "limit", safeLimit);
    }

    public Map<String, Object> updateCheckpoint(String workerKey, String cursorText, String actor) {
        String normalizedWorkerKey = normalize(workerKey);
        if (!StringUtils.hasText(normalizedWorkerKey)) {
            throw new IllegalArgumentException("Runtime checkpoint worker key is required.");
        }
        String normalizedCursorText = cursorText == null ? null : cursorText.trim();
        runtimeWorkerCheckpointService.saveCursor(normalizedWorkerKey, normalizedCursorText);
        recordOperation(
            "manual_checkpoint_update",
            "checkpoint",
            normalizedWorkerKey,
            null,
            normalizedWorkerKey,
            "success",
            actor,
            "Manual checkpoint update for " + normalizedWorkerKey,
            Map.of("cursor_text", normalizedCursorText)
        );
        incidentService.appendSignalEvent("integration_transport", "panel-rabbitmq-bridge", "manual_checkpoint_update",
            "Manual checkpoint update for " + normalizedWorkerKey,
            Map.of("worker_key", normalizedWorkerKey, "cursor_text", normalizedCursorText, "actor", normalize(actor)),
            actor);
        return Map.of(
            "success", true,
            "worker_key", normalizedWorkerKey,
            "cursor_text", runtimeWorkerCheckpointService.readCursorText(normalizedWorkerKey).orElse(normalizedCursorText)
        );
    }

    public TransportHealthSnapshot buildHealthSnapshot() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Timestamp inboundStaleThreshold = timestamp(OffsetDateTime.now(ZoneOffset.UTC).minus(INBOUND_STALE_TIMEOUT));
        Timestamp outboundStaleThreshold = timestamp(OffsetDateTime.now(ZoneOffset.UTC).minus(OUTBOUND_STALE_TIMEOUT));
        List<WorkerCheckpointSnapshot> checkpointSnapshots = loadWorkerCheckpointSnapshots(now);
        return new TransportHealthSnapshot(
            toLong(count("integration_inbound_event_inbox", "status = 'failed'")),
            toLong(countWithThreshold("integration_inbound_event_inbox", "status = 'processing' AND processing_started_at IS NOT NULL AND processing_started_at < ?", inboundStaleThreshold)),
            toLong(count("integration_transport_outbox", "status = 'failed'")),
            toLong(count("integration_transport_outbox", "status = 'queued' OR status = 'processing'")),
            toLong(countWithThreshold("integration_transport_outbox", "status = 'processing' AND processing_started_at IS NOT NULL AND processing_started_at < ?", outboundStaleThreshold)),
            checkpointSnapshots.stream().filter(WorkerCheckpointSnapshot::stale).count(),
            checkpointSnapshots.stream().filter(snapshot -> snapshot.cursorLag() != null && snapshot.cursorLag() > 0L).count(),
            countRecentManualOperations(RECENT_MANUAL_OPERATION_WINDOW)
        );
    }

    public List<Map<String, Object>> loadRuntimeCheckpointDiagnostics() {
        return loadRuntimeCheckpoints();
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
        return loadWorkerCheckpointSnapshots(OffsetDateTime.now(ZoneOffset.UTC)).stream()
            .map(WorkerCheckpointSnapshot::toMap)
            .toList();
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

    private List<Map<String, Object>> loadInboxItemsForTicket(String ticketId, int limit) {
        return jdbcTemplate.query("""
                SELECT event_id,
                       event_kind,
                       ticket_id,
                       routing_key,
                       status,
                       attempt_count,
                       processing_started_at,
                       updated_at,
                       received_at,
                       last_error
                  FROM integration_inbound_event_inbox
                 WHERE ticket_id = ?
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
                row.put("received_at", normalize(rs.getString("received_at")));
                row.put("last_error", normalize(rs.getString("last_error")));
                return row;
            },
            ticketId,
            limit
        );
    }

    private List<Map<String, Object>> loadOutboxItemsForTicket(String ticketId, int limit) {
        return jdbcTemplate.query("""
                SELECT event_id,
                       event_kind,
                       ticket_id,
                       routing_key,
                       status,
                       attempt_count,
                       processing_started_at,
                       updated_at,
                       available_at,
                       published_at,
                       last_error
                  FROM integration_transport_outbox
                 WHERE ticket_id = ?
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
                row.put("available_at", normalize(rs.getString("available_at")));
                row.put("published_at", normalize(rs.getString("published_at")));
                row.put("last_error", normalize(rs.getString("last_error")));
                return row;
            },
            ticketId,
            limit
        );
    }

    private List<InboxEntry> loadReplayableInboxEntries(int limit) {
        return jdbcTemplate.query("""
                SELECT event_id,
                       event_kind,
                       ticket_id,
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
                rs.getString("ticket_id"),
                rs.getString("routing_key"),
                rs.getString("payload_json")
            ),
            timestamp(OffsetDateTime.now(ZoneOffset.UTC).minus(INBOUND_STALE_TIMEOUT)),
            limit
        );
    }

    private List<InboxEntry> loadReplayableInboxEntriesForTicket(String ticketId, int limit) {
        return jdbcTemplate.query("""
                SELECT event_id,
                       event_kind,
                       ticket_id,
                       routing_key,
                       payload_json
                  FROM integration_inbound_event_inbox
                 WHERE ticket_id = ?
                   AND (
                        status = 'failed'
                        OR (status = 'processing'
                            AND processing_started_at IS NOT NULL
                            AND processing_started_at < ?)
                   )
                 ORDER BY updated_at ASC, event_id ASC
                 LIMIT ?
                """,
            (rs, rowNum) -> new InboxEntry(
                rs.getString("event_id"),
                rs.getString("event_kind"),
                rs.getString("ticket_id"),
                rs.getString("routing_key"),
                rs.getString("payload_json")
            ),
            ticketId,
            timestamp(OffsetDateTime.now(ZoneOffset.UTC).minus(INBOUND_STALE_TIMEOUT)),
            limit
        );
    }

    private List<String> loadReplayableOutboxEventIdsForTicket(String ticketId, int limit) {
        return jdbcTemplate.query("""
                SELECT event_id
                  FROM integration_transport_outbox
                 WHERE ticket_id = ?
                   AND (
                        status = 'failed'
                        OR (status = 'processing'
                            AND processing_started_at IS NOT NULL
                            AND processing_started_at < ?)
                   )
                 ORDER BY updated_at ASC, event_id ASC
                 LIMIT ?
                """,
            (rs, rowNum) -> rs.getString("event_id"),
            ticketId,
            timestamp(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5)),
            limit
        );
    }

    private Map<String, Object> loadEventDetail(String table, List<String> columns, String eventId, String targetType) {
        String normalizedEventId = normalize(eventId);
        if (!StringUtils.hasText(normalizedEventId)) {
            throw new IllegalArgumentException("Transport event id is required.");
        }
        String columnProjection = String.join(", ", columns);
        List<Map<String, Object>> rows = jdbcTemplate.query(
            "SELECT " + columnProjection + " FROM " + table + " WHERE event_id = ?",
            (rs, rowNum) -> readEventRow(rs, columns),
            normalizedEventId
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Transport event not found: " + normalizedEventId);
        }
        Map<String, Object> item = rows.get(0);
        String payloadJson = normalize((String) item.get("payload_json"));
        if (StringUtils.hasText(payloadJson)) {
            item.put("payload", parseJson(payloadJson));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("item", item);
        payload.put("recent_operations", loadRecentOperationLogsForTarget(targetType, normalizedEventId, 15));
        return payload;
    }

    private Map<String, Object> readEventRow(java.sql.ResultSet rs, List<String> columns) throws java.sql.SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        for (String column : columns) {
            row.put(column, normalize(rs.getString(column)));
        }
        return row;
    }

    private InboxEntry loadInboxEntry(String eventId) {
        List<InboxEntry> items = jdbcTemplate.query("""
                SELECT event_id, event_kind, ticket_id, routing_key, payload_json
                  FROM integration_inbound_event_inbox
                 WHERE event_id = ?
                """,
            (rs, rowNum) -> new InboxEntry(
                rs.getString("event_id"),
                rs.getString("event_kind"),
                rs.getString("ticket_id"),
                rs.getString("routing_key"),
                rs.getString("payload_json")
            ),
            eventId
        );
        return items.isEmpty() ? null : items.get(0);
    }

    private OutboxRef loadOutboxRef(String eventId) {
        List<OutboxRef> items = jdbcTemplate.query("""
                SELECT event_id, event_kind, ticket_id, routing_key
                  FROM integration_transport_outbox
                 WHERE event_id = ?
                """,
            (rs, rowNum) -> new OutboxRef(
                rs.getString("event_id"),
                rs.getString("event_kind"),
                rs.getString("ticket_id"),
                rs.getString("routing_key")
            ),
            eventId
        );
        return items.isEmpty() ? null : items.get(0);
    }

    private List<WorkerCheckpointSnapshot> loadWorkerCheckpointSnapshots(OffsetDateTime now) {
        return jdbcTemplate.query("""
                SELECT worker_key, cursor_text, updated_at
                  FROM runtime_worker_checkpoints
                 ORDER BY worker_key ASC
                """,
            (rs, rowNum) -> toCheckpointSnapshot(
                normalize(rs.getString("worker_key")),
                normalize(rs.getString("cursor_text")),
                normalize(rs.getString("updated_at")),
                now
            )
        );
    }

    private WorkerCheckpointSnapshot loadCurrentWorkerCheckpointSnapshot(String workerKey) {
        List<WorkerCheckpointSnapshot> items = jdbcTemplate.query("""
                SELECT worker_key, cursor_text, updated_at
                  FROM runtime_worker_checkpoints
                 WHERE worker_key = ?
                """,
            (rs, rowNum) -> toCheckpointSnapshot(
                normalize(rs.getString("worker_key")),
                normalize(rs.getString("cursor_text")),
                normalize(rs.getString("updated_at")),
                OffsetDateTime.now(ZoneOffset.UTC)
            ),
            workerKey
        );
        return items.isEmpty() ? null : items.get(0);
    }

    private WorkerCheckpointSnapshot toCheckpointSnapshot(String workerKey,
                                                          String cursorText,
                                                          String updatedAtRaw,
                                                          OffsetDateTime now) {
        WorkerDiagnosticsDefinition definition = workerDefinition(workerKey);
        String label = definition != null ? definition.label() : workerKey;
        OffsetDateTime updatedAt = parseOffsetDateTime(updatedAtRaw);
        long staleThresholdMinutes = definition != null ? definition.staleThreshold().toMinutes() : 60L;
        long lagAlertThreshold = definition != null ? definition.lagAlertThreshold() : 0L;
        Long sourceMaxCursor = definition != null ? loadMaxCursor(definition.sourceTable(), definition.cursorColumn()) : null;
        Long cursorValue = parseLong(cursorText);
        Long cursorLag = cursorValue != null && sourceMaxCursor != null ? Math.max(0L, sourceMaxCursor - cursorValue) : null;
        long ageMinutes = updatedAt == null ? Long.MAX_VALUE : Math.max(0L, Duration.between(updatedAt, now).toMinutes());
        boolean stale = ageMinutes > staleThresholdMinutes;
        boolean lagging = cursorLag != null && cursorLag > lagAlertThreshold;
        String healthStatus = stale ? "stale" : (lagging ? "lagging" : "healthy");
        return new WorkerCheckpointSnapshot(
            workerKey,
            label,
            cursorText,
            updatedAtRaw,
            ageMinutes == Long.MAX_VALUE ? null : ageMinutes,
            staleThresholdMinutes,
            lagAlertThreshold,
            healthStatus,
            definition != null ? definition.sourceTable() : null,
            sourceMaxCursor,
            cursorLag,
            stale,
            lagging
        );
    }

    private List<Map<String, Object>> loadRecentOperationLogs(int limit) {
        return jdbcTemplate.query("""
                SELECT id,
                       action_type,
                       summary_text,
                       target_type,
                       target_id,
                       ticket_id,
                       worker_key,
                       result_status,
                       actor,
                       details_json,
                       created_at
                  FROM integration_transport_operation_log
                 ORDER BY created_at DESC, id DESC
                 LIMIT ?
                """,
            (rs, rowNum) -> readOperationLogRow(rs),
            Math.max(1, limit)
        );
    }

    private List<Map<String, Object>> loadRecentOperationLogsForTarget(String targetType, String targetId, int limit) {
        return jdbcTemplate.query("""
                SELECT id,
                       action_type,
                       summary_text,
                       target_type,
                       target_id,
                       ticket_id,
                       worker_key,
                       result_status,
                       actor,
                       details_json,
                       created_at
                  FROM integration_transport_operation_log
                 WHERE target_type = ?
                   AND target_id = ?
                 ORDER BY created_at DESC, id DESC
                 LIMIT ?
                """,
            (rs, rowNum) -> readOperationLogRow(rs),
            targetType,
            targetId,
            Math.max(1, limit)
        );
    }

    private List<Map<String, Object>> loadRecentOperationLogsForWorker(String workerKey, int limit) {
        return jdbcTemplate.query("""
                SELECT id,
                       action_type,
                       summary_text,
                       target_type,
                       target_id,
                       ticket_id,
                       worker_key,
                       result_status,
                       actor,
                       details_json,
                       created_at
                  FROM integration_transport_operation_log
                 WHERE worker_key = ?
                    OR (target_type = 'checkpoint' AND target_id = ?)
                 ORDER BY created_at DESC, id DESC
                 LIMIT ?
                """,
            (rs, rowNum) -> readOperationLogRow(rs),
            workerKey,
            workerKey,
            Math.max(1, limit)
        );
    }

    private List<Map<String, Object>> loadRecentHealthSnapshots(int limit) {
        return jdbcTemplate.query("""
                SELECT id,
                       inbound_failed,
                       inbound_stale_processing,
                       outbound_failed,
                       outbound_backlog,
                       outbound_stale_processing,
                       stale_checkpoint_count,
                       lagging_checkpoint_count,
                       recent_manual_operations,
                       severity,
                       summary_text,
                       created_at
                  FROM integration_transport_health_snapshots
                 ORDER BY created_at DESC, id DESC
                 LIMIT ?
                """,
            (rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("inbound_failed", rs.getLong("inbound_failed"));
                row.put("inbound_stale_processing", rs.getLong("inbound_stale_processing"));
                row.put("outbound_failed", rs.getLong("outbound_failed"));
                row.put("outbound_backlog", rs.getLong("outbound_backlog"));
                row.put("outbound_stale_processing", rs.getLong("outbound_stale_processing"));
                row.put("stale_checkpoint_count", rs.getLong("stale_checkpoint_count"));
                row.put("lagging_checkpoint_count", rs.getLong("lagging_checkpoint_count"));
                row.put("recent_manual_operations", rs.getLong("recent_manual_operations"));
                row.put("severity", normalize(rs.getString("severity")));
                row.put("summary_text", normalize(rs.getString("summary_text")));
                row.put("created_at", normalize(rs.getString("created_at")));
                return row;
            },
            Math.max(1, limit)
        );
    }

    private List<Map<String, Object>> loadRecentWorkerHealthSnapshots(String workerKey, int limit) {
        return jdbcTemplate.query("""
                SELECT id,
                       worker_key,
                       worker_label,
                       source_table,
                       checkpoint_updated_at,
                       cursor_text,
                       source_max_cursor,
                       cursor_lag,
                       age_minutes,
                       stale_threshold_minutes,
                       lag_alert_threshold,
                       health_status,
                       stale,
                       unhealthy,
                       summary_text,
                       created_at
                  FROM integration_transport_worker_health_snapshots
                 WHERE worker_key = ?
                 ORDER BY created_at DESC, id DESC
                 LIMIT ?
                """,
            (rs, rowNum) -> readWorkerHealthSnapshotRow(rs),
            workerKey,
            Math.max(1, limit)
        );
    }

    private List<TransportHealthSnapshotRow> loadHealthSnapshotRows(Duration window, int limit) {
        Duration safeWindow = window == null || window.isZero() || window.isNegative() ? DEFAULT_TREND_WINDOW : window;
        return jdbcTemplate.query("""
                SELECT id,
                       inbound_failed,
                       inbound_stale_processing,
                       outbound_failed,
                       outbound_backlog,
                       outbound_stale_processing,
                       stale_checkpoint_count,
                       lagging_checkpoint_count,
                       recent_manual_operations,
                       severity,
                       summary_text,
                       created_at
                  FROM integration_transport_health_snapshots
                 WHERE created_at >= ?
                 ORDER BY created_at DESC, id DESC
                 LIMIT ?
                """,
            (rs, rowNum) -> new TransportHealthSnapshotRow(
                rs.getLong("id"),
                rs.getLong("inbound_failed"),
                rs.getLong("inbound_stale_processing"),
                rs.getLong("outbound_failed"),
                rs.getLong("outbound_backlog"),
                rs.getLong("outbound_stale_processing"),
                rs.getLong("stale_checkpoint_count"),
                rs.getLong("lagging_checkpoint_count"),
                rs.getLong("recent_manual_operations"),
                normalize(rs.getString("severity")),
                normalize(rs.getString("summary_text")),
                normalize(rs.getString("created_at"))
            ),
            timestamp(OffsetDateTime.now(ZoneOffset.UTC).minus(safeWindow)),
            Math.max(1, limit)
        );
    }

    private List<WorkerHealthSnapshotRow> loadWorkerHealthSnapshotRows(String workerKey, Duration window, int limit) {
        Duration safeWindow = window == null || window.isZero() || window.isNegative() ? DEFAULT_WORKER_TREND_WINDOW : window;
        return jdbcTemplate.query("""
                SELECT id,
                       worker_key,
                       worker_label,
                       cursor_lag,
                       age_minutes,
                       health_status,
                       stale,
                       unhealthy,
                       summary_text,
                       created_at
                  FROM integration_transport_worker_health_snapshots
                 WHERE worker_key = ?
                   AND created_at >= ?
                 ORDER BY created_at DESC, id DESC
                 LIMIT ?
                """,
            (rs, rowNum) -> new WorkerHealthSnapshotRow(
                rs.getLong("id"),
                normalize(rs.getString("worker_key")),
                normalize(rs.getString("worker_label")),
                readNullableLong(rs, "cursor_lag"),
                readNullableLong(rs, "age_minutes"),
                normalize(rs.getString("health_status")),
                rs.getBoolean("stale"),
                rs.getBoolean("unhealthy"),
                normalize(rs.getString("summary_text")),
                normalize(rs.getString("created_at"))
            ),
            workerKey,
            timestamp(OffsetDateTime.now(ZoneOffset.UTC).minus(safeWindow)),
            Math.max(1, limit)
        );
    }

    private List<Map<String, Object>> loadRecentOperationLogsForTicket(String ticketId, int limit) {
        return jdbcTemplate.query("""
                SELECT id,
                       action_type,
                       summary_text,
                       target_type,
                       target_id,
                       ticket_id,
                       worker_key,
                       result_status,
                       actor,
                       details_json,
                       created_at
                  FROM integration_transport_operation_log
                 WHERE ticket_id = ?
                 ORDER BY created_at DESC, id DESC
                 LIMIT ?
                """,
            (rs, rowNum) -> readOperationLogRow(rs),
            ticketId,
            Math.max(1, limit)
        );
    }

    private Map<String, Object> readOperationLogRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getLong("id"));
        row.put("action_type", normalize(rs.getString("action_type")));
        row.put("summary_text", normalize(rs.getString("summary_text")));
        row.put("target_type", normalize(rs.getString("target_type")));
        row.put("target_id", normalize(rs.getString("target_id")));
        row.put("ticket_id", normalize(rs.getString("ticket_id")));
        row.put("worker_key", normalize(rs.getString("worker_key")));
        row.put("result_status", normalize(rs.getString("result_status")));
        row.put("actor", normalize(rs.getString("actor")));
        String detailsJson = normalize(rs.getString("details_json"));
        row.put("details_json", detailsJson);
        row.put("details", StringUtils.hasText(detailsJson) ? parseJson(detailsJson) : null);
        row.put("created_at", normalize(rs.getString("created_at")));
        return row;
    }

    private Map<String, Object> readWorkerHealthSnapshotRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getLong("id"));
        row.put("worker_key", normalize(rs.getString("worker_key")));
        row.put("worker_label", normalize(rs.getString("worker_label")));
        row.put("source_table", normalize(rs.getString("source_table")));
        row.put("checkpoint_updated_at", normalize(rs.getString("checkpoint_updated_at")));
        row.put("cursor_text", normalize(rs.getString("cursor_text")));
        row.put("source_max_cursor", readNullableLong(rs, "source_max_cursor"));
        row.put("cursor_lag", readNullableLong(rs, "cursor_lag"));
        row.put("age_minutes", readNullableLong(rs, "age_minutes"));
        row.put("stale_threshold_minutes", rs.getLong("stale_threshold_minutes"));
        row.put("lag_alert_threshold", rs.getLong("lag_alert_threshold"));
        row.put("health_status", normalize(rs.getString("health_status")));
        row.put("stale", rs.getBoolean("stale"));
        row.put("unhealthy", rs.getBoolean("unhealthy"));
        row.put("summary_text", normalize(rs.getString("summary_text")));
        row.put("created_at", normalize(rs.getString("created_at")));
        return row;
    }

    private List<Map<String, Object>> buildObservabilityAlerts(TransportHealthSnapshot snapshot,
                                                               Map<String, Object> trendSummary) {
        List<Map<String, Object>> alerts = new ArrayList<>();
        if (snapshot.inboundFailed() > 0) {
            alerts.add(alert("inbound_failed", "high", "Есть failed inbound события, требуется replay/debug.", snapshot.inboundFailed(), 0));
        }
        if (snapshot.inboundStaleProcessing() > 0) {
            alerts.add(alert("inbound_stale_processing", "critical", "Inbound processing завис и требует ручного разбора.", snapshot.inboundStaleProcessing(), 0));
        }
        if (snapshot.outboundFailed() > 0) {
            alerts.add(alert("outbound_failed", "high", "Есть failed outbound publish события, требуется requeue/debug.", snapshot.outboundFailed(), 0));
        }
        if (snapshot.outboundStaleProcessing() > 0) {
            alerts.add(alert("outbound_stale_processing", "critical", "Outbound publish завис в processing.", snapshot.outboundStaleProcessing(), 0));
        }
        if (snapshot.outboundBacklog() >= 100) {
            alerts.add(alert("outbound_backlog", snapshot.outboundBacklog() >= 250 ? "critical" : "warning",
                "Накопился заметный outbound backlog.", snapshot.outboundBacklog(), 100));
        }
        if (snapshot.staleCheckpointCount() > 0) {
            alerts.add(alert("stale_checkpoints", "warning", "Есть stale runtime checkpoints: проверьте worker loops и leases.",
                snapshot.staleCheckpointCount(), 0));
        }
        if (snapshot.laggingCheckpointCount() > 0) {
            alerts.add(alert("lagging_checkpoints", "warning", "Worker checkpoints отстают от source cursors и требуют проверки pressure/backlog.",
                snapshot.laggingCheckpointCount(), 0));
        }
        if (snapshot.recentManualOperations() >= 5) {
            alerts.add(alert("manual_compensation_pressure", "warning", "За последние часы выросло число ручных recovery действий.",
                snapshot.recentManualOperations(), 5));
        }
        if (Boolean.TRUE.equals(trendSummary.get("sustained_pressure"))) {
            alerts.add(alert("sustained_transport_pressure", "critical",
                "Транспортный контур остаётся unhealthy несколько последовательных snapshot-циклов подряд.",
                toLong(trendSummary.get("unhealthy_streak")), SUSTAINED_UNHEALTHY_STREAK_THRESHOLD));
        }
        if (alerts.isEmpty()) {
            alerts.add(alert("transport_healthy", "ok", "Transport contour и worker checkpoints выглядят стабильно.", 0, 0));
        }
        return alerts;
    }

    private Map<String, Object> alert(String key, String severity, String message, long value, long threshold) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", key);
        row.put("severity", severity);
        row.put("message", message);
        row.put("value", value);
        row.put("threshold", threshold);
        return row;
    }

    private long countRecentManualOperations(Duration window) {
        Duration safeWindow = window == null || window.isZero() || window.isNegative() ? RECENT_MANUAL_OPERATION_WINDOW : window;
        OffsetDateTime since = OffsetDateTime.now(ZoneOffset.UTC).minus(safeWindow);
        Long value = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM integration_transport_operation_log
                 WHERE created_at >= ?
                   AND action_type LIKE 'manual_%'
                """,
            Long.class,
            timestamp(since)
        );
        return value != null ? value : 0L;
    }

    private <T> long consecutiveCount(List<T> rows,
                                      java.util.function.Predicate<T> predicate) {
        long count = 0L;
        for (T row : rows) {
            if (!predicate.test(row)) {
                break;
            }
            count++;
        }
        return count;
    }

    private void recordOperation(String actionType,
                                 String targetType,
                                 String targetId,
                                 String ticketId,
                                 String workerKey,
                                 String resultStatus,
                                 String actor,
                                 String summaryText,
                                 Object details) {
        jdbcTemplate.update("""
                INSERT INTO integration_transport_operation_log(
                    action_type,
                    summary_text,
                    target_type,
                    target_id,
                    ticket_id,
                    worker_key,
                    result_status,
                    actor,
                    details_json,
                    created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
            normalize(actionType),
            requiredSummary(summaryText),
            normalize(targetType),
            normalize(targetId),
            normalize(ticketId),
            normalize(workerKey),
            normalize(resultStatus) == null ? "success" : normalize(resultStatus),
            normalize(actor),
            serializeJson(details)
        );
    }

    private void recordWorkerHealthSnapshot(WorkerCheckpointSnapshot snapshot) {
        if (snapshot == null || !StringUtils.hasText(snapshot.workerKey())) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO integration_transport_worker_health_snapshots(
                    worker_key,
                    worker_label,
                    source_table,
                    checkpoint_updated_at,
                    cursor_text,
                    source_max_cursor,
                    cursor_lag,
                    age_minutes,
                    stale_threshold_minutes,
                    lag_alert_threshold,
                    health_status,
                    stale,
                    unhealthy,
                    summary_text,
                    created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
            snapshot.workerKey(),
            snapshot.workerLabel(),
            snapshot.sourceTable(),
            timestamp(parseOffsetDateTime(snapshot.updatedAt())),
            snapshot.cursorText(),
            snapshot.sourceMaxCursor(),
            snapshot.cursorLag(),
            snapshot.ageMinutes(),
            snapshot.staleThresholdMinutes(),
            snapshot.lagAlertThreshold(),
            snapshot.healthStatus(),
            snapshot.stale(),
            snapshot.unhealthy(),
            snapshot.summary()
        );
    }

    private String requiredSummary(String summaryText) {
        String normalized = normalize(summaryText);
        return StringUtils.hasText(normalized) ? normalized : "transport-operation";
    }

    private String serializeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize transport operation payload.", ex);
        }
    }

    private Object parseJson(String payloadJson) {
        try {
            JsonNode node = objectMapper.readTree(payloadJson);
            return objectMapper.convertValue(node, Object.class);
        } catch (Exception ex) {
            return payloadJson;
        }
    }

    private OffsetDateTime parseOffsetDateTime(String rawValue) {
        return DATE_TIME_CONVERTER.convertToEntityAttribute(rawValue);
    }

    private Long parseLong(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        try {
            return Long.parseLong(rawValue.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long loadMaxCursor(String tableName, String cursorColumn) {
        if (!StringUtils.hasText(tableName) || !StringUtils.hasText(cursorColumn)) {
            return null;
        }
        try {
            return jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(" + cursorColumn + "), 0) FROM " + tableName,
                Long.class
            );
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private List<Map<String, Object>> buildWorkerRecommendations(WorkerCheckpointSnapshot snapshot,
                                                                 Map<String, Object> trendSummary) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (snapshot == null) {
            return items;
        }
        if (snapshot.stale()) {
            items.add(recommendation("critical", "Проверьте lease owner и логи worker-а: checkpoint устарел сверх TTL."));
        }
        if (snapshot.lagging()) {
            items.add(recommendation("warning", "Source cursor ушёл вперёд: проверьте backlog в source table и скорость consumer loop."));
        }
        if (Boolean.TRUE.equals(trendSummary.get("sustained_pressure"))) {
            items.add(recommendation("high", "Проблема не разовая: посмотрите историю worker snapshot-ов и зафиксируйте recovery note в incident."));
        }
        if (snapshot.stale() || snapshot.lagging()) {
            items.add(recommendation("info", "Ручной checkpoint update делайте только после проверки source cursor и безопасного replay диапазона."));
        }
        if (items.isEmpty()) {
            items.add(recommendation("ok", "Worker выглядит стабильно: sustained pressure и заметный lag не наблюдаются."));
        }
        return items;
    }

    private Map<String, Object> recommendation(String severity, String message) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("severity", severity);
        row.put("message", message);
        return row;
    }

    public String workerSignalKey(String workerKey) {
        String normalizedWorkerKey = normalize(workerKey);
        return StringUtils.hasText(normalizedWorkerKey)
            ? "panel-runtime-checkpoints/" + normalizedWorkerKey
            : "panel-runtime-checkpoints";
    }

    private WorkerDiagnosticsDefinition workerDefinition(String workerKey) {
        if (!StringUtils.hasText(workerKey)) {
            return null;
        }
        for (WorkerDiagnosticsDefinition definition : WORKER_DIAGNOSTICS) {
            if (workerKey.equalsIgnoreCase(definition.workerKey())) {
                return definition;
            }
        }
        return null;
    }

    private Long readNullableLong(java.sql.ResultSet rs, String columnName) throws java.sql.SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
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
                                          long outboundStaleProcessing,
                                          long staleCheckpointCount,
                                          long laggingCheckpointCount,
                                          long recentManualOperations) {

        public boolean unhealthy() {
            return inboundFailed > 0
                || inboundStaleProcessing > 0
                || outboundFailed > 0
                || outboundStaleProcessing > 0
                || outboundBacklog >= 100
                || staleCheckpointCount > 0;
        }

        public String severity() {
            if (inboundStaleProcessing > 0 || outboundStaleProcessing > 0 || outboundBacklog >= 250 || staleCheckpointCount > 0) {
                return "critical";
            }
            if (inboundFailed > 0 || outboundFailed > 0 || outboundBacklog >= 100) {
                return "high";
            }
            if (laggingCheckpointCount > 0 || recentManualOperations >= 5) {
                return "warning";
            }
            return "ok";
        }

        public String summary() {
            return String.format(Locale.ROOT,
                "inbound_failed=%d, inbound_stale=%d, outbound_failed=%d, outbound_backlog=%d, outbound_stale=%d, stale_checkpoints=%d, lagging_checkpoints=%d, recent_manual_ops=%d",
                inboundFailed,
                inboundStaleProcessing,
                outboundFailed,
                outboundBacklog,
                outboundStaleProcessing,
                staleCheckpointCount,
                laggingCheckpointCount,
                recentManualOperations
            );
        }

        public Map<String, Object> toMap() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("inbound_failed", inboundFailed);
            payload.put("inbound_stale_processing", inboundStaleProcessing);
            payload.put("outbound_failed", outboundFailed);
            payload.put("outbound_backlog", outboundBacklog);
            payload.put("outbound_stale_processing", outboundStaleProcessing);
            payload.put("stale_checkpoint_count", staleCheckpointCount);
            payload.put("lagging_checkpoint_count", laggingCheckpointCount);
            payload.put("recent_manual_operations", recentManualOperations);
            payload.put("severity", severity());
            payload.put("summary", summary());
            return payload;
        }
    }

    private record InboxEntry(String eventId,
                              String eventKind,
                              String ticketId,
                              String routingKey,
                              String payloadJson) {
    }

    private record OutboxRef(String eventId,
                             String eventKind,
                             String ticketId,
                             String routingKey) {
    }

    private record WorkerDiagnosticsDefinition(String workerKey,
                                               String label,
                                               String sourceTable,
                                               String cursorColumn,
                                               Duration staleThreshold,
                                               long lagAlertThreshold) {
    }

    private record WorkerCheckpointSnapshot(String workerKey,
                                            String workerLabel,
                                            String cursorText,
                                            String updatedAt,
                                            Long ageMinutes,
                                            long staleThresholdMinutes,
                                            long lagAlertThreshold,
                                            String healthStatus,
                                            String sourceTable,
                                            Long sourceMaxCursor,
                                            Long cursorLag,
                                            boolean stale,
                                            boolean lagging) {
        boolean unhealthy() {
            return stale || lagging;
        }

        String summary() {
            return String.format(Locale.ROOT,
                "worker=%s, health=%s, cursor=%s, lag=%s, age_minutes=%s, source=%s",
                workerKey,
                healthStatus,
                cursorText == null ? "-" : cursorText,
                cursorLag == null ? "-" : String.valueOf(cursorLag),
                ageMinutes == null ? "-" : String.valueOf(ageMinutes),
                sourceTable == null ? "-" : sourceTable
            );
        }

        Map<String, Object> toMap() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("worker_key", workerKey);
            row.put("worker_label", workerLabel);
            row.put("cursor_text", cursorText);
            row.put("updated_at", updatedAt);
            row.put("age_minutes", ageMinutes);
            row.put("stale_threshold_minutes", staleThresholdMinutes);
            row.put("lag_alert_threshold", lagAlertThreshold);
            row.put("health_status", healthStatus);
            row.put("source_table", sourceTable);
            row.put("source_max_cursor", sourceMaxCursor);
            row.put("cursor_lag", cursorLag);
            row.put("stale", stale);
            row.put("lagging", lagging);
            row.put("unhealthy", unhealthy());
            row.put("summary_text", summary());
            return row;
        }
    }

    private record TransportHealthSnapshotRow(long id,
                                              long inboundFailed,
                                              long inboundStaleProcessing,
                                              long outboundFailed,
                                              long outboundBacklog,
                                              long outboundStaleProcessing,
                                              long staleCheckpointCount,
                                              long laggingCheckpointCount,
                                              long recentManualOperations,
                                              String severity,
                                              String summaryText,
                                              String createdAt) {
        boolean unhealthy() {
            return inboundFailed > 0
                || inboundStaleProcessing > 0
                || outboundFailed > 0
                || outboundStaleProcessing > 0
                || outboundBacklog >= 100
                || staleCheckpointCount > 0;
        }
    }

    private record WorkerHealthSnapshotRow(long id,
                                           String workerKey,
                                           String workerLabel,
                                           Long cursorLag,
                                           Long ageMinutes,
                                           String healthStatus,
                                           boolean stale,
                                           boolean unhealthy,
                                           String summaryText,
                                           String createdAt) {
        boolean critical() {
            return stale || "stale".equalsIgnoreCase(healthStatus);
        }
    }
}
