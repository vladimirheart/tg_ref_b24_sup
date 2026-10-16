package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.panel.repository.ChannelRepository;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArraySet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class LegacyGeneralRealtimeBoundaryPostgresIntegrationTest {

    @Test
    void producersAllowOnlyUniqueExplicitNonGeneralOwnersAndKeepExactIds() {
        JdbcTemplate jdbc = tickets();
        seed(jdbc);
        LegacyTicketIdJdbcGuard guard = new LegacyTicketIdJdbcGuard(jdbc);
        UiEventFanoutPublisher publisher = mock(UiEventFanoutPublisher.class);
        when(publisher.publish(any(), anyString(), any(), any())).thenReturn(true);
        UiEventStreamService stream = new UiEventStreamService(publisher, guard, readableRecipient());

        assertThat(guard.hasUniqueTicket("T-GENERAL")).isTrue();
        for (String id : List.of("T-GENERAL", "T-MIXED", "T-UNKNOWN", "T-MISSING", "T-EXACT",
                " T-EXACT ", "T-UNRESOLVED")) {
            stream.publishDialogsChanged("updated", id);
            stream.publishDialogHistoryChanged(id, 9L, "updated");
        }

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloads = ArgumentCaptor.forClass(Map.class);
        verify(publisher, times(4)).publish(any(), anyString(), payloads.capture(), any());
        assertThat(payloads.getAllValues()).extracting(payload -> payload.get("ticketId"))
            .containsExactly(" T-EXACT ", " T-EXACT ", "T-UNRESOLVED", "T-UNRESOLVED");
    }

    @ParameterizedTest
    @CsvSource({"dialogs_changed,broadcast", "dialogs_changed,targeted",
        "dialog_history_changed,broadcast", "dialog_history_changed,targeted",
        "custom_ticket_event,broadcast", "custom_ticket_event,targeted"})
    void queuedFanoutChecksCanonicalOwnerBeforeTargetedOrBroadcastSend(String name, String delivery) throws IOException {
        JdbcTemplate jdbc = tickets();
        seed(jdbc);
        UiEventStreamService stream = new UiEventStreamService(mock(UiEventFanoutPublisher.class),
            new LegacyTicketIdJdbcGuard(jdbc), readableRecipient());
        SseEmitter emitter = register(stream);
        String target = "targeted".equals(delivery) ? " GENERAL-READER " : null;

        for (String id : List.of("T-GENERAL", "T-MIXED", "T-UNKNOWN", "T-MISSING", "T-EXACT")) {
            stream.deliverFanoutEvent(envelope(target, name,
                Map.of("ticketId", id, "scopeKind", "BUSINESS", "preview", "private content")));
        }
        verifyNoInteractions(emitter);

        stream.deliverFanoutEvent(envelope(target, name, Map.of("ticketId", " T-EXACT ")));
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"general", "duplicate", "missing"})
    void localFallbackRechecksOwnerAfterProducerValidation(String mutation) {
        JdbcTemplate jdbc = tickets();
        jdbc.update("INSERT INTO tickets VALUES (1, 'T-LATE', FALSE, 7)");
        UiEventFanoutPublisher publisher = mock(UiEventFanoutPublisher.class);
        when(publisher.publish(any(), anyString(), any(), any())).thenAnswer(call -> {
            switch (mutation) {
                case "general" -> jdbc.update("UPDATE tickets SET is_general = TRUE WHERE ticket_id = 'T-LATE'");
                case "duplicate" -> jdbc.update("INSERT INTO tickets VALUES (2, 'T-LATE', TRUE, NULL)");
                case "missing" -> jdbc.update("DELETE FROM tickets WHERE ticket_id = 'T-LATE'");
                default -> throw new IllegalArgumentException(mutation);
            }
            return false;
        });
        UiEventStreamService stream = new UiEventStreamService(publisher, new LegacyTicketIdJdbcGuard(jdbc),
            readableRecipient());
        SseEmitter emitter = register(stream);

        stream.publishDialogsChanged("updated", "T-LATE");

        verify(publisher).publish(any(), anyString(), any(), any());
        verifyNoInteractions(emitter);
    }

    @Test
    void malformedDialogEnvelopesCannotBecomeGlobalBroadcasts() {
        LegacyTicketIdJdbcGuard guard = mock(LegacyTicketIdJdbcGuard.class);
        UiEventStreamService stream = new UiEventStreamService(mock(UiEventFanoutPublisher.class), guard,
            readableRecipient());
        SseEmitter emitter = register(stream);
        for (String name : List.of("dialogs_changed", "dialog_history_changed")) {
            for (Map<String, Object> payload : List.of(Map.<String, Object>of(),
                    Map.<String, Object>of("ticketId", 42L), Map.<String, Object>of("ticketId", " "))) {
                stream.deliverFanoutEvent(envelope(null, name, payload));
                stream.deliverFanoutEvent(envelope("general-reader", name, payload));
            }
        }
        verifyNoInteractions(guard, emitter);
    }

    @Test
    void missingMarkerSchemaDoesNotDeliverTicketEventsButPreservesSystemSignals() throws IOException {
        JdbcTemplate jdbc = tickets();
        jdbc.execute("ALTER TABLE tickets DROP COLUMN is_general");
        jdbc.update("INSERT INTO tickets VALUES (1, 'T-LEGACY-SCHEMA', NULL)");
        UiEventFanoutPublisher publisher = mock(UiEventFanoutPublisher.class);
        UiEventStreamService stream = new UiEventStreamService(publisher, new LegacyTicketIdJdbcGuard(jdbc),
            readableRecipient());
        SseEmitter emitter = register(stream);

        stream.publishDialogsChanged("updated", "T-LEGACY-SCHEMA");
        stream.deliverFanoutEvent(envelope(null, "dialog_history_changed", Map.of("ticketId", "T-LEGACY-SCHEMA")));
        verifyNoInteractions(emitter, publisher);

        stream.publishNotificationsChanged("general-reader", "notification_created");
        stream.publishSidebarUnblockChanged("updated");
        stream.publishSidebarBotsChanged("updated", 9L);
        stream.sendHeartbeat();
        verify(emitter, times(4)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void directRealtimeHandlersStopGeneralBeforeNotificationsAssignmentsAiOrLegacySse() {
        JdbcTemplate jdbc = tickets();
        jdbc.update("INSERT INTO tickets VALUES (1, 'T-GENERAL', TRUE, NULL)");
        NotificationService notifications = mock(NotificationService.class);
        DialogAiAssistantService ai = mock(DialogAiAssistantService.class);
        AlertQueueService queue = mock(AlertQueueService.class);
        ChannelAssignmentRoutingService routing = mock(ChannelAssignmentRoutingService.class);
        ChannelRepository channels = mock(ChannelRepository.class);
        DialogResponsibilityService responsibility = mock(DialogResponsibilityService.class);
        DialogNotificationService supportChat = mock(DialogNotificationService.class);
        UiEventStreamService stream = mock(UiEventStreamService.class);
        DialogRealtimeEventService events = new DialogRealtimeEventService(notifications, ai, queue, routing,
            channels, responsibility, supportChat, stream, new LegacyTicketIdJdbcGuard(jdbc));

        events.handleTicketCreated("T-GENERAL", 9L, "private preview");
        events.handleIncomingClientMessage("T-GENERAL", 9L, "private text", "photo", "private/key");
        events.handleIncomingClientMessage("T-GENERAL", 9L, "private text", "text", null, false);
        events.handleClientMessageEdited("T-GENERAL", 9L);
        events.handleOperatorMessageEdited("T-GENERAL", 9L);
        events.handleFeedbackCreated("T-GENERAL", 5);
        events.handleTicketAutoClosed("T-GENERAL", 9L, "private text");
        events.handleTicketClosed("T-GENERAL", 9L);
        events.handleTicketReopened("T-GENERAL", 9L);

        verifyNoInteractions(notifications, ai, queue, routing, channels, responsibility, supportChat, stream);
    }

    // These cases isolate the existing marker/identity boundary; business READ has its own real SQL matrix.
    private UiEventTicketAccessService readableRecipient() {
        UiEventTicketAccessService access = mock(UiEventTicketAccessService.class);
        when(access.canReadTicket(anyString(), any(UiEventTicketScope.class))).thenReturn(true);
        when(access.captureScope(anyString())).thenAnswer(call -> Optional.of(scope(call.getArgument(0))));
        return access;
    }

    private JdbcTemplate tickets() {
        JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplate("general_realtime_boundary");
        jdbc.execute("CREATE TABLE tickets(user_id BIGINT, ticket_id TEXT, is_general BOOLEAN, business_id BIGINT, PRIMARY KEY(user_id, ticket_id))");
        return jdbc;
    }

    private void seed(JdbcTemplate jdbc) {
        jdbc.update("""
            INSERT INTO tickets VALUES (1, 'T-GENERAL', TRUE, NULL), (2, 'T-MIXED', TRUE, NULL),
                (3, 'T-MIXED', FALSE, 7), (4, 'T-UNKNOWN', NULL, NULL),
                (5, ' T-EXACT ', FALSE, 7), (6, 'T-EXACT', TRUE, NULL), (7, 'T-UNRESOLVED', FALSE, NULL)
            """);
    }

    private UiEventFanoutEnvelope envelope(String target, String name, Map<String, Object> payload) {
        Object id = payload.get("ticketId");
        return new UiEventFanoutEnvelope("queued-event", "another-worker", target, name, payload,
            id instanceof String rawId ? scope(rawId) : null);
    }

    private UiEventTicketScope scope(String id) {
        return new UiEventTicketScope(1L, id, "a".repeat(32), "BUSINESS", 7L);
    }

    @SuppressWarnings("unchecked")
    private SseEmitter register(UiEventStreamService stream) {
        var emitters = (Map<String, CopyOnWriteArraySet<SseEmitter>>) ReflectionTestUtils.getField(stream, "emittersByUser");
        SseEmitter emitter = mock(SseEmitter.class);
        emitters.put("general-reader", new CopyOnWriteArraySet<>(List.of(emitter)));
        return emitter;
    }
}
