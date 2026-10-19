package com.example.panel.service;

import com.example.panel.runtime.RuntimeWorkload;
import com.example.panel.runtime.RuntimeRole;
import com.example.panel.runtime.RuntimeReplicaPolicy;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RuntimeWorkload(
    id = "ui-event-outbox-watcher",
    roles = {RuntimeRole.WORKER},
    replicaPolicy = RuntimeReplicaPolicy.LEASED
)@Service
public class UiEventOutboxWatcher {

    private static final String CHECKPOINT_KEY = "ui-event-outbox-watch";
    private static final Logger log = LoggerFactory.getLogger(UiEventOutboxWatcher.class);

    private final JdbcTemplate jdbcTemplate;
    private final DialogRealtimeEventService dialogRealtimeEventService;
    private final RuntimeCoordinationService runtimeCoordinationService;
    private final RuntimeWorkerCheckpointService checkpointService;
    private final LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard;
    private final UiEventTicketAccessService ticketAccess;
    private final GeneralTicketRealtimeService generalRealtime;
    private final AtomicLong lastProcessedId = new AtomicLong(0L);

    public UiEventOutboxWatcher(JdbcTemplate jdbcTemplate,
                                DialogRealtimeEventService dialogRealtimeEventService,
                                RuntimeCoordinationService runtimeCoordinationService,
                                RuntimeWorkerCheckpointService checkpointService,
                                LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard,
                                UiEventTicketAccessService ticketAccess) {
        this(jdbcTemplate, dialogRealtimeEventService, runtimeCoordinationService, checkpointService,
            legacyTicketIdJdbcGuard, ticketAccess, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public UiEventOutboxWatcher(JdbcTemplate jdbcTemplate, DialogRealtimeEventService dialogRealtimeEventService,
                                RuntimeCoordinationService runtimeCoordinationService, RuntimeWorkerCheckpointService checkpointService,
                                LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard, UiEventTicketAccessService ticketAccess,
                                GeneralTicketRealtimeService generalRealtime) {
        this.jdbcTemplate = jdbcTemplate;
        this.dialogRealtimeEventService = dialogRealtimeEventService;
        this.runtimeCoordinationService = runtimeCoordinationService;
        this.checkpointService = checkpointService;
        this.legacyTicketIdJdbcGuard = legacyTicketIdJdbcGuard;
        this.ticketAccess = ticketAccess;
        this.generalRealtime = generalRealtime;
    }

    @PostConstruct
    void initialize() {
        lastProcessedId.set(checkpointService.readLongCursorOrInitialize(CHECKPOINT_KEY, this::readMaxId));
    }

    @Scheduled(fixedDelayString = "${panel.ui-event-outbox.watch-interval-ms:1000}")
    void watch() {
        runtimeCoordinationService.runWithLease("ui-event-outbox-watch", Duration.ofSeconds(30), () -> {
            checkpointService.readLongCursor(CHECKPOINT_KEY).ifPresent(lastProcessedId::set);
            long afterId = lastProcessedId.get();
            jdbcTemplate.query("""
                SELECT id, event_type, ticket_id, channel_id, message_text, message_type, attachment, rating,
                       ticket_user_id, ticket_public_id, ticket_scope_kind, ticket_business_id
                  FROM ui_event_outbox
                 WHERE id > ?
                 ORDER BY id ASC
                """, (ResultSetExtractor<Void>) rs -> {
                long maxSeen = afterId;
                while (rs.next()) {
                    long id = rs.getLong("id");
                    if (id > maxSeen) {
                        maxSeen = id;
                    }
                    try {
                        handleEvent(
                                id,
                                rs.getString("event_type"),
                                rs.getString("ticket_id"),
                                rs.getObject("channel_id") != null ? rs.getLong("channel_id") : null,
                                rs.getString("message_text"),
                                rs.getString("message_type"),
                                rs.getString("attachment"),
                                rs.getObject("rating") != null ? rs.getInt("rating") : null,
                                new UiEventTicketScope(rs.getObject("ticket_user_id", Long.class),
                                    rs.getString("ticket_id"), rs.getString("ticket_public_id"),
                                    rs.getString("ticket_scope_kind"), rs.getObject("ticket_business_id", Long.class))
                        );
                    } catch (RuntimeException ex) {
                        // One best-effort UI notification must not hold the cursor and starve later messages/media.
                        log.error("Unable to process UI outbox event id={} type={}; continuing with later events", id,
                                rs.getString("event_type"), ex);
                    }
                }
                if (maxSeen > afterId) {
                    lastProcessedId.set(maxSeen);
                    checkpointService.saveLongCursor(CHECKPOINT_KEY, maxSeen);
                }
                return null;
            }, afterId);
        });
    }

    @Scheduled(fixedDelayString = "${panel.ui-event-outbox.cleanup-interval-ms:3600000}")
    void cleanup() {
        runtimeCoordinationService.runWithLease("ui-event-outbox-cleanup", Duration.ofMinutes(10), () -> {
            long safeThresholdId = lastProcessedId.get();
            if (safeThresholdId <= 0) {
                return;
            }
            OffsetDateTime thresholdCreatedAt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(3);
            jdbcTemplate.update("""
                DELETE FROM ui_event_outbox
                 WHERE id <= ?
                   AND created_at < ?
                """, safeThresholdId, thresholdCreatedAt);
        });
    }

    private void handleEvent(long eventId,
                             String eventType,
                             String ticketId,
                             Long channelId,
                             String messageText,
                             String messageType,
                             String attachment,
                             Integer rating,
                             UiEventTicketScope scope) {
        if (scope != null && "GENERAL".equals(scope.scopeKind())) {
            if (generalRealtime != null && ticketId != null && ticketId.equals(scope.ticketId())) generalRealtime.publish(scope, eventType);
            return; // GENERAL must never enter legacy bell/AI/assignment/support-chat callbacks.
        }
        if (!legacyTicketIdJdbcGuard.hasUniqueNonGeneralTicket(ticketId)
                || scope == null || !ticketId.equals(scope.ticketId()) || !ticketAccess.isCurrentScope(scope)) {
            return;
        }
        String normalizedEventType = StringUtils.hasText(eventType) ? eventType.trim().toLowerCase(Locale.ROOT) : "";
        switch (normalizedEventType) {
            case "ticket_created" -> dialogRealtimeEventService.handleScopedTicketCreated(scope, channelId, messageText);
            case "client_message_created" -> {
                boolean initialClientMessage = isInitialClientMessageEvent(eventId, ticketId, scope);
                dialogRealtimeEventService.handleScopedIncomingClientMessage(
                        scope,
                        channelId,
                        messageText,
                        messageType,
                        attachment,
                        !initialClientMessage
                );
            }
            case "client_message_edited" -> dialogRealtimeEventService.handleScopedClientMessageEdited(scope, channelId);
            case "operator_message_edited" -> dialogRealtimeEventService.handleScopedOperatorMessageEdited(scope, channelId);
            case "feedback_created" -> dialogRealtimeEventService.handleScopedFeedbackCreated(scope, rating);
            case "ticket_closed_auto" -> dialogRealtimeEventService.handleScopedTicketAutoClosed(scope, channelId, messageText);
            case "ticket_closed" -> dialogRealtimeEventService.handleScopedTicketClosed(scope, channelId);
            case "ticket_reopened" -> dialogRealtimeEventService.handleScopedTicketReopened(scope, channelId);
            default -> {
                // ignore unknown event types
            }
        }
    }

    private boolean isInitialClientMessageEvent(long eventId, String ticketId, UiEventTicketScope scope) {
        if (eventId <= 0L || !StringUtils.hasText(ticketId)) {
            return false;
        }
        try {
            Boolean initial = jdbcTemplate.queryForObject("""
                    SELECT CASE WHEN EXISTS (
                        SELECT 1
                          FROM ui_event_outbox created_event
                         WHERE created_event.ticket_id = ?
                           AND created_event.ticket_user_id = ? AND created_event.ticket_public_id = ?
                           AND created_event.ticket_scope_kind = ?
                           AND created_event.ticket_business_id IS NOT DISTINCT FROM CAST(? AS BIGINT)
                           AND lower(COALESCE(created_event.event_type, '')) = 'ticket_created'
                           AND created_event.id < ?
                    ) AND NOT EXISTS (
                        SELECT 1
                          FROM ui_event_outbox previous_message
                         WHERE previous_message.ticket_id = ?
                           AND previous_message.ticket_user_id = ? AND previous_message.ticket_public_id = ?
                           AND previous_message.ticket_scope_kind = ?
                           AND previous_message.ticket_business_id IS NOT DISTINCT FROM CAST(? AS BIGINT)
                           AND lower(COALESCE(previous_message.event_type, '')) = 'client_message_created'
                           AND previous_message.id < ?
                    ) THEN TRUE ELSE FALSE END
                    """, Boolean.class,
                    ticketId, scope.ticketUserId(), scope.ticketPublicId(), scope.scopeKind(), scope.businessId(), eventId,
                    ticketId, scope.ticketUserId(), scope.ticketPublicId(), scope.scopeKind(), scope.businessId(), eventId);
            return Boolean.TRUE.equals(initial);
        } catch (RuntimeException ex) {
            log.warn("Unable to resolve initial client-message ownership for ticket {} event {}: {}",
                    ticketId, eventId, ex.getMessage());
            return false;
        }
    }

    private long readMaxId() {
        try {
            Long value = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) FROM ui_event_outbox", Long.class);
            return value != null ? value : 0L;
        } catch (RuntimeException ex) {
            return 0L;
        }
    }
}
