package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.runtime.RuntimeRoleProperties;
import com.example.panel.runtime.UiEventFanoutProperties;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class UiEventTicketScopePostgresIntegrationTest {

    private static final String PUBLIC_A = "a".repeat(32);
    private JdbcTemplate jdbc;
    private UiEventTicketAccessService access;
    private UiEventFanoutPublisher publisher;
    private UiEventStreamService stream;

    @BeforeEach
    void setUp() {
        jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("ui_event_frozen_scope");
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY, username TEXT UNIQUE, enabled BOOLEAN, is_blocked BOOLEAN)");
        jdbc.execute("CREATE TABLE businesses(id BIGINT PRIMARY KEY, status TEXT)");
        jdbc.execute("CREATE TABLE business_memberships(user_id BIGINT, business_id BIGINT, role_code TEXT, is_active BOOLEAN)");
        jdbc.execute("CREATE TABLE business_access_grants(user_id BIGINT, grant_scope TEXT, role_code TEXT, is_active BOOLEAN)");
        jdbc.execute("CREATE TABLE tickets(user_id BIGINT, ticket_id TEXT, ticket_public_id TEXT, is_general BOOLEAN, business_id BIGINT, PRIMARY KEY(user_id, ticket_id))");
        jdbc.update("INSERT INTO users VALUES (1, 'all-reader', TRUE, FALSE)");
        jdbc.update("INSERT INTO businesses VALUES (1, 'active'), (2, 'active')");
        jdbc.update("INSERT INTO business_access_grants VALUES (1, 'ALL_BUSINESSES', 'VIEWER', TRUE)");
        jdbc.update("INSERT INTO tickets VALUES (10, 'T-A', ?, FALSE, 1), (20, 'T-B', ?, FALSE, 2)", PUBLIC_A, "b".repeat(32));
        access = new UiEventTicketAccessService(jdbc, new MultibusinessFeatureFlags());
        publisher = mock(UiEventFanoutPublisher.class);
        stream = new UiEventStreamService(publisher, new LegacyTicketIdJdbcGuard(jdbc), access);
    }

    @ParameterizedTest
    @CsvSource({"business,broadcast", "business,targeted", "general,broadcast", "general,targeted",
        "unresolved,broadcast", "unresolved,targeted", "deleted,broadcast", "deleted,targeted",
        "recreated,broadcast", "recreated,targeted", "tuple,broadcast", "tuple,targeted",
        "duplicate,broadcast", "duplicate,targeted"})
    void queuedSnapshotCannotFollowChangedOwnerEvenForAllBusinesses(String mutation, String delivery) {
        SseEmitter reader = register();
        UiEventTicketScope scope = access.captureScope("T-A").orElseThrow();
        String target = delivery.equals("targeted") ? " ALL-READER " : null;
        String name = target == null ? "dialogs_changed" : "dialog_history_changed";
        UiEventFanoutEnvelope queued = envelope(target, name, "T-A", scope);

        mutate(mutation);
        stream.deliverFanoutEvent(queued);

        verifyNoInteractions(reader);
        assertThat(access.canReadTicket("all-reader", scope)).isFalse();
        if (List.of("business", "recreated", "tuple").contains(mutation)) {
            UiEventTicketScope current = access.captureScope("T-A").orElseThrow();
            assertThat(current).isNotEqualTo(scope);
            assertThat(access.canReadTicket("all-reader", current)).isTrue();
        }
    }

    @ParameterizedTest
    @CsvSource({"business,dialogs_changed", "business,dialog_history_changed",
        "recreated,dialogs_changed", "recreated,dialog_history_changed",
        "tuple,dialogs_changed", "tuple,dialog_history_changed"})
    void localFallbackUsesTheScopeCapturedBeforePublication(String mutation, String event) {
        SseEmitter reader = register();
        when(publisher.publish(any(), anyString(), any(), any())).thenAnswer(call -> {
            assertThat(call.getArgument(3, UiEventTicketScope.class).ticketPublicId()).isEqualTo(PUBLIC_A);
            mutate(mutation);
            return false;
        });

        if (event.equals("dialogs_changed")) {
            stream.publishDialogsChanged("updated", "T-A");
        } else {
            stream.publishDialogHistoryChanged("T-A", 9L, "updated");
        }

        verify(publisher).publish(any(), anyString(), any(), any());
        verifyNoInteractions(reader);
    }

    @ParameterizedTest
    @CsvSource({"unchanged,broadcast", "unchanged,targeted", "business,broadcast", "business,targeted",
        "recreated,broadcast", "recreated,targeted", "revoked,broadcast", "revoked,targeted"})
    void publisherJsonSubscriberRoundTripKeepsFrozenScopeAndCurrentRead(String mutation, String delivery) throws Exception {
        SseEmitter reader = register();
        ObjectMapper mapper = new ObjectMapper();
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        UiEventFanoutProperties properties = new UiEventFanoutProperties();
        properties.setMode("redis");
        properties.setChannel("test:frozen-scope");
        UiEventFanoutPublisher realPublisher = new UiEventFanoutPublisher(redis, mapper, properties, new RuntimeRoleProperties());
        String target = delivery.equals("targeted") ? " ALL-READER " : null;
        Map<String, Object> payload = Map.of("ticketId", "T-A", "preview", "private old payload");
        UiEventTicketScope scope = access.captureScope("T-A").orElseThrow();
        assertThat(realPublisher.publish(target, "dialogs_changed", payload, scope)).isTrue();
        ArgumentCaptor<String> serialized = ArgumentCaptor.forClass(String.class);
        verify(redis).convertAndSend(anyString(), serialized.capture());
        UiEventFanoutEnvelope decoded = mapper.readValue(serialized.getValue(), UiEventFanoutEnvelope.class);
        assertThat(decoded.ticketScope()).isEqualTo(scope);
        assertThat(decoded.payload()).isEqualTo(payload);
        assertThat(serialized.getValue()).doesNotContain("\"valid\"");

        if (mutation.equals("revoked")) {
            jdbc.update("UPDATE business_access_grants SET is_active = FALSE");
        } else if (!mutation.equals("unchanged")) {
            mutate(mutation);
        }
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(serialized.getValue().getBytes(StandardCharsets.UTF_8));
        new UiEventRedisSubscriber(mapper, stream).onMessage(message, null);

        if (mutation.equals("unchanged")) {
            verify(reader).send(any(SseEmitter.SseEventBuilder.class));
        } else {
            verifyNoInteractions(reader);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"BUSINESS", "UNRESOLVED"})
    void ticketsWithoutPublicLocatorCannotCreateProvableFanout(String scope) {
        if (scope.equals("UNRESOLVED")) {
            jdbc.update("UPDATE tickets SET business_id = NULL WHERE ticket_id = 'T-A'");
        }
        jdbc.update("UPDATE tickets SET ticket_public_id = NULL WHERE ticket_id = 'T-A'");
        SseEmitter reader = register();

        stream.publishDialogsChanged("updated", "T-A");
        stream.publishDialogHistoryChanged("T-A", 9L, "updated");

        assertThat(access.captureScope("T-A")).isEmpty();
        verifyNoInteractions(publisher, reader);
        jdbc.update("UPDATE tickets SET ticket_public_id = 'invalid' WHERE ticket_id = 'T-A'");
        stream.publishDialogsChanged("updated", "T-A");
        assertThat(access.captureScope("T-A")).isEmpty();
        verifyNoInteractions(publisher, reader);
    }

    @Test
    void malformedMissingAndMismatchedScopeNeverBecomesGlobalDelivery() {
        SseEmitter reader = register();
        UiEventTicketScope valid = access.captureScope("T-A").orElseThrow();
        List<UiEventTicketScope> invalid = List.of(
            new UiEventTicketScope(null, "T-A", PUBLIC_A, "BUSINESS", 1L),
            new UiEventTicketScope(10L, "T-A", null, "BUSINESS", 1L),
            new UiEventTicketScope(10L, "T-A", "invalid", "BUSINESS", 1L),
            new UiEventTicketScope(10L, "T-A", PUBLIC_A, "GENERAL", null),
            new UiEventTicketScope(10L, "T-A", PUBLIC_A, "BUSINESS", null),
            new UiEventTicketScope(10L, "T-A", PUBLIC_A, "UNRESOLVED", 1L),
            new UiEventTicketScope(10L, "T-A", PUBLIC_A, "BUSINESS", 0L),
            new UiEventTicketScope(10L, "T-A", PUBLIC_A, "BUSINESS", 2L),
            new UiEventTicketScope(999L, "T-A", PUBLIC_A, "BUSINESS", 1L),
            new UiEventTicketScope(10L, "T-A", "b".repeat(32), "BUSINESS", 1L));
        for (String target : List.of("", "all-reader")) {
            for (String name : List.of("dialogs_changed", "dialog_history_changed", "custom")) {
                stream.deliverFanoutEvent(envelope(target, name, "T-A", null));
                for (UiEventTicketScope scope : invalid) {
                    stream.deliverFanoutEvent(envelope(target, name, "T-A", scope));
                }
                stream.deliverFanoutEvent(envelope(target, name, "T-B", valid));
                stream.deliverFanoutEvent(new UiEventFanoutEnvelope("queued", "worker", target, name, Map.of(), valid));
            }
        }
        verifyNoInteractions(reader);
    }

    @Test
    void absentPublicLocatorSchemaFailsClosedButOldSystemEnvelopesStillWork() throws Exception {
        SseEmitter reader = register();
        UiEventTicketScope scope = access.captureScope("T-A").orElseThrow();
        jdbc.execute("ALTER TABLE tickets DROP COLUMN ticket_public_id");
        stream.publishDialogsChanged("updated", "T-A");
        stream.deliverFanoutEvent(envelope(null, "dialogs_changed", "T-A", scope));
        verifyNoInteractions(publisher, reader);

        ObjectMapper mapper = new ObjectMapper();
        String oldJson = "{\"eventId\":\"old\",\"originInstanceId\":\"old-worker\",\"targetUser\":\"all-reader\",\"eventName\":\"notifications_changed\",\"payload\":{\"reason\":\"refresh\"}}";
        stream.deliverFanoutEvent(mapper.readValue(oldJson, UiEventFanoutEnvelope.class));
        verify(reader).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void validLocalEventPreservesBrowserPayloadWithoutInternalOwnerFields() throws IOException {
        SseEmitter reader = register();
        stream.publishDialogHistoryChanged("T-A", 9L, " edited ");
        ArgumentCaptor<SseEmitter.SseEventBuilder> sent = ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        verify(reader).send(sent.capture());
        var data = sent.getValue().build().stream().map(item -> item.getData())
            .filter(Map.class::isInstance).map(Map.class::cast).findFirst().orElseThrow();
        assertThat(data).containsEntry("ticketId", "T-A").containsEntry("reason", "edited")
            .containsEntry("channelId", 9L).containsKey("emittedAt")
            .doesNotContainKeys("ticketScope", "ticketUserId", "ticketPublicId", "businessId", "scopeKind");
    }

    @Test
    void ownerIsRecheckedBetweenTwoEmittersWithTheSameBroadGrant() throws IOException {
        SseEmitter first = register();
        SseEmitter second = register();
        doAnswer(call -> {
            mutate("business");
            return null;
        }).when(first).send(any(SseEmitter.SseEventBuilder.class));

        stream.deliverFanoutEvent(envelope(null, "dialogs_changed", "T-A", access.captureScope("T-A").orElseThrow()));

        verify(first).send(any(SseEmitter.SseEventBuilder.class));
        verifyNoInteractions(second);
    }

    @Test
    void exactRawIdCannotBeSubstitutedByItsTrimmedNeighbour() throws IOException {
        jdbc.update("INSERT INTO tickets VALUES (30, ' T-A ', ?, FALSE, 1)", "c".repeat(32));
        SseEmitter reader = register();
        UiEventTicketScope scope = access.captureScope(" T-A ").orElseThrow();
        stream.deliverFanoutEvent(envelope(null, "dialogs_changed", "T-A", scope));
        verifyNoInteractions(reader);
        stream.deliverFanoutEvent(envelope(null, "dialogs_changed", " T-A ", scope));
        verify(reader).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void grantMayBeRestoredWithoutRewritingTheQueuedScope() throws IOException {
        SseEmitter reader = register();
        UiEventFanoutEnvelope queued = envelope(null, "dialogs_changed", "T-A", access.captureScope("T-A").orElseThrow());
        jdbc.update("UPDATE business_access_grants SET is_active = FALSE");
        stream.deliverFanoutEvent(queued);
        verifyNoInteractions(reader);
        jdbc.update("UPDATE business_access_grants SET is_active = TRUE");
        stream.deliverFanoutEvent(queued);
        verify(reader).send(any(SseEmitter.SseEventBuilder.class));
        clearInvocations(reader);
        mutate("recreated");
        stream.deliverFanoutEvent(queued);
        verifyNoInteractions(reader);
    }

    private void mutate(String mutation) {
        switch (mutation) {
            case "business" -> jdbc.update("UPDATE tickets SET business_id = 2 WHERE ticket_id = 'T-A'");
            case "general" -> jdbc.update("UPDATE tickets SET is_general = TRUE, business_id = NULL WHERE ticket_id = 'T-A'");
            case "unresolved" -> jdbc.update("UPDATE tickets SET business_id = NULL WHERE ticket_id = 'T-A'");
            case "deleted" -> jdbc.update("DELETE FROM tickets WHERE ticket_id = 'T-A'");
            case "recreated" -> {
                jdbc.update("DELETE FROM tickets WHERE ticket_id = 'T-A'");
                jdbc.update("INSERT INTO tickets VALUES (10, 'T-A', ?, FALSE, 1)", "d".repeat(32));
            }
            case "tuple" -> jdbc.update("UPDATE tickets SET user_id = 999 WHERE ticket_id = 'T-A'");
            case "duplicate" -> jdbc.update("INSERT INTO tickets VALUES (999, 'T-A', ?, FALSE, 2)", "e".repeat(32));
            default -> throw new IllegalArgumentException(mutation);
        }
    }

    private UiEventFanoutEnvelope envelope(String target, String name, String ticketId, UiEventTicketScope scope) {
        return new UiEventFanoutEnvelope("queued", "worker", target, name,
            Map.of("ticketId", ticketId, "preview", "private old payload"), scope);
    }

    @SuppressWarnings("unchecked")
    private SseEmitter register() {
        var emitters = (Map<String, CopyOnWriteArraySet<SseEmitter>>) ReflectionTestUtils.getField(stream, "emittersByUser");
        SseEmitter emitter = mock(SseEmitter.class);
        emitters.computeIfAbsent("all-reader", key -> new CopyOnWriteArraySet<>()).add(emitter);
        return emitter;
    }
}
