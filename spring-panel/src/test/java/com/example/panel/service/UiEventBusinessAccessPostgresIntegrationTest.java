package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class UiEventBusinessAccessPostgresIntegrationTest {

    private JdbcTemplate jdbc;
    private MultibusinessFeatureFlags flags;
    private UiEventTicketAccessService access;
    private UiEventFanoutPublisher publisher;
    private UiEventStreamService stream;

    @BeforeEach
    void setUp() {
        jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("ui_event_business_access");
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY, username TEXT UNIQUE, enabled BOOLEAN, is_blocked BOOLEAN)");
        jdbc.execute("CREATE TABLE businesses(id BIGINT PRIMARY KEY, status TEXT)");
        jdbc.execute("CREATE TABLE business_memberships(user_id BIGINT, business_id BIGINT, role_code TEXT, is_active BOOLEAN)");
        jdbc.execute("CREATE TABLE business_access_grants(user_id BIGINT, grant_scope TEXT, role_code TEXT, is_active BOOLEAN)");
        jdbc.execute("CREATE TABLE user_roles(user_id BIGINT, role TEXT)");
        jdbc.execute("CREATE TABLE general_ticket_read_grants(user_id BIGINT, is_active BOOLEAN)");
        jdbc.execute("CREATE TABLE tickets(user_id BIGINT, ticket_id TEXT, is_general BOOLEAN, business_id BIGINT, ticket_public_id TEXT DEFAULT md5(random()::text), PRIMARY KEY(user_id, ticket_id))");
        jdbc.update("INSERT INTO businesses VALUES (1, 'active'), (2, 'active')");
        jdbc.update("INSERT INTO users VALUES (1, 'Reader@Example.COM', TRUE, FALSE), (2, 'other', TRUE, FALSE), (3, 'admin', TRUE, FALSE)");
        jdbc.update("INSERT INTO business_memberships VALUES (1, 1, 'VIEWER', TRUE), (2, 2, 'OPERATOR', TRUE)");
        jdbc.update("INSERT INTO user_roles VALUES (3, 'ROLE_ADMIN')");
        jdbc.update("INSERT INTO general_ticket_read_grants VALUES (1, TRUE)");
        jdbc.update("INSERT INTO tickets(user_id, ticket_id, is_general, business_id) VALUES (10, 'T-A', FALSE, 1), (20, 'T-B', FALSE, 2), (30, 'T-GENERAL', TRUE, NULL), (40, 'T-UNRESOLVED', FALSE, NULL)");
        flags = new MultibusinessFeatureFlags();
        access = new UiEventTicketAccessService(jdbc, flags);
        publisher = mock(UiEventFanoutPublisher.class);
        stream = new UiEventStreamService(publisher, new LegacyTicketIdJdbcGuard(jdbc), access);
    }

    @ParameterizedTest
    @CsvSource({"dialogs_changed,broadcast", "dialogs_changed,targeted",
        "dialog_history_changed,broadcast", "dialog_history_changed,targeted",
        "custom_ticket_event,broadcast", "custom_ticket_event,targeted"})
    void queuedFanoutFiltersEachRecipientUsingCanonicalBusiness(String name, String delivery) throws IOException {
        SseEmitter reader = register("reader@example.com");
        SseEmitter other = register("other");
        SseEmitter admin = register("admin");
        String target = "targeted".equals(delivery) ? "READER@EXAMPLE.COM" : null;

        deliver(target, name, "T-B");
        verifyNoInteractions(reader, admin);
        deliver(target, name, "T-A");

        verify(reader).send(any(SseEmitter.SseEventBuilder.class));
        if (target == null) {
            verify(other).send(any(SseEmitter.SseEventBuilder.class));
        } else {
            verifyNoInteractions(other);
        }
        verifyNoInteractions(admin);
    }

    @ParameterizedTest
    @ValueSource(strings = {"VIEWER", "OPERATOR", "MANAGER", "ADMIN"})
    void everyCurrentBusinessReadRoleCanReceiveEvents(String role) throws IOException {
        jdbc.update("UPDATE business_memberships SET role_code = ? WHERE user_id = 1", role);
        SseEmitter reader = register("reader@example.com");

        stream.publishDialogsChanged("updated", "T-A");
        stream.publishDialogHistoryChanged("T-A", 9L, "edited");

        verify(reader, times(2)).send(any(SseEmitter.SseEventBuilder.class));
        assertThat(canRead("READER@EXAMPLE.COM", "T-A")).isTrue();
        assertThat(canRead(" READER@EXAMPLE.COM ", "T-A")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"revoke", "delete_membership", "delete_account", "disabled", "blocked", "archived", "unknown_role", "case_collision"})
    void existingConnectionLosesBusinessEventsAfterAccessChanges(String mutation) throws IOException {
        SseEmitter reader = register("reader@example.com");
        deliver(null, "dialogs_changed", "T-A");
        verify(reader).send(any(SseEmitter.SseEventBuilder.class));
        clearInvocations(reader);

        switch (mutation) {
            case "revoke" -> jdbc.update("UPDATE business_memberships SET is_active = FALSE WHERE user_id = 1");
            case "delete_membership" -> jdbc.update("DELETE FROM business_memberships WHERE user_id = 1");
            case "delete_account" -> jdbc.update("DELETE FROM users WHERE id = 1");
            case "disabled" -> jdbc.update("UPDATE users SET enabled = FALSE WHERE id = 1");
            case "blocked" -> jdbc.update("UPDATE users SET is_blocked = TRUE WHERE id = 1");
            case "archived" -> jdbc.update("UPDATE businesses SET status = 'archived' WHERE id = 1");
            case "unknown_role" -> jdbc.update("UPDATE business_memberships SET role_code = 'UNKNOWN' WHERE user_id = 1");
            case "case_collision" -> jdbc.update("INSERT INTO users VALUES (4, 'reader@example.com', FALSE, FALSE)");
            default -> throw new IllegalArgumentException(mutation);
        }
        deliver(null, "dialogs_changed", "T-A");
        deliver("reader@example.com", "dialog_history_changed", "T-A");
        stream.publishDialogsChanged("updated", "T-A");
        verifyNoInteractions(reader);

        // A denial does not disconnect the stream or grant business access via system events.
        stream.publishNotificationsChanged("reader@example.com", "notification_created");
        stream.sendHeartbeat();
        verify(reader, times(2)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void allBusinessesGrantIncludesFutureBusinessButNeverGeneral() throws IOException {
        jdbc.update("INSERT INTO business_access_grants VALUES (1, 'ALL_BUSINESSES', 'VIEWER', TRUE)");
        jdbc.update("INSERT INTO businesses VALUES (3, 'active')");
        jdbc.update("INSERT INTO tickets(user_id, ticket_id, is_general, business_id) VALUES (50, 'T-FUTURE', FALSE, 3)");
        SseEmitter reader = register("reader@example.com");

        deliver(null, "dialogs_changed", "T-B");
        deliver("reader@example.com", "dialog_history_changed", "T-FUTURE");
        deliver(null, "dialogs_changed", "T-GENERAL");
        verify(reader, times(2)).send(any(SseEmitter.SseEventBuilder.class));
        clearInvocations(reader);

        jdbc.update("UPDATE business_access_grants SET is_active = FALSE WHERE user_id = 1");
        deliver(null, "dialogs_changed", "T-FUTURE");
        deliver(null, "dialogs_changed", "T-B");
        verifyNoInteractions(reader);
        deliver(null, "dialogs_changed", "T-A");
        verify(reader).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void eitherIndependentGrantKeepsReadUntilBothAreRevoked() throws IOException {
        jdbc.update("INSERT INTO business_access_grants VALUES (1, 'ALL_BUSINESSES', 'ADMIN', TRUE)");
        SseEmitter reader = register("reader@example.com");
        jdbc.update("UPDATE business_memberships SET is_active = FALSE WHERE user_id = 1");
        deliver(null, "dialogs_changed", "T-A");
        jdbc.update("UPDATE business_memberships SET is_active = TRUE WHERE user_id = 1");
        jdbc.update("UPDATE business_access_grants SET is_active = FALSE WHERE user_id = 1");
        deliver(null, "dialogs_changed", "T-A");
        verify(reader, times(2)).send(any(SseEmitter.SseEventBuilder.class));
        clearInvocations(reader);
        jdbc.update("UPDATE business_memberships SET is_active = FALSE WHERE user_id = 1");
        deliver(null, "dialogs_changed", "T-A");
        verifyNoInteractions(reader);
    }

    @ParameterizedTest
    @ValueSource(strings = {"dialogs_changed", "dialog_history_changed"})
    void lateRevokeBetweenPublicationAndLocalDeliveryDeniesSend(String name) {
        SseEmitter reader = register("reader@example.com");
        when(publisher.publish(any(), anyString(), any(), any())).thenAnswer(call -> {
            jdbc.update("DELETE FROM business_memberships WHERE user_id = 1");
            return false;
        });

        if (name.equals("dialogs_changed")) {
            stream.publishDialogsChanged("updated", "T-A");
        } else {
            stream.publishDialogHistoryChanged("T-A", 9L, "updated");
        }
        verify(publisher).publish(any(), anyString(), any(), any());
        verifyNoInteractions(reader);
    }

    @Test
    void accessIsRecheckedEvenBetweenTwoEmittersOfOneRecipient() throws IOException {
        SseEmitter first = register("reader@example.com");
        SseEmitter second = register("reader@example.com");
        doAnswer(call -> {
            jdbc.update("UPDATE business_memberships SET is_active = FALSE WHERE user_id = 1");
            return null;
        }).when(first).send(any(SseEmitter.SseEventBuilder.class));

        deliver(null, "dialogs_changed", "T-A");

        verify(first).send(any(SseEmitter.SseEventBuilder.class));
        verifyNoInteractions(second);
    }

    @Test
    void unresolvedCompatibilityIsExplicitAndStrictModeDeniesIt() throws IOException {
        SseEmitter reader = register("reader@example.com");
        deliver(null, "dialogs_changed", "T-UNRESOLVED");
        verify(reader).send(any(SseEmitter.SseEventBuilder.class));
        clearInvocations(reader);
        flags.setStrictReadScope(true);
        deliver(null, "dialogs_changed", "T-UNRESOLVED");
        deliver("reader@example.com", "dialogs_changed", "T-UNRESOLVED");
        deliver(null, "dialogs_changed", "T-GENERAL");
        verifyNoInteractions(reader);
        deliver(null, "dialogs_changed", "T-A");
        verify(reader).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void recipientSqlIndependentlyRejectsMissingGeneralUnknownAndAmbiguousOwners() {
        jdbc.update("INSERT INTO tickets(user_id, ticket_id, is_general, business_id) VALUES (60, 'T-A', TRUE, NULL), (70, 'T-UNKNOWN', NULL, 1), (80, ' T-EXACT ', FALSE, 1), (90, 'T-EXACT', TRUE, NULL)");
        for (String id : List.of("T-A", "T-GENERAL", "T-UNKNOWN", "T-MISSING", "T-EXACT", " ")) {
            assertThat(canRead("reader@example.com", id)).as(id).isFalse();
        }
        assertThat(canRead("reader@example.com", " T-EXACT ")).isTrue();
        assertThat(canRead("missing-account", " T-EXACT ")).isFalse();
        assertThat(canRead(null, " T-EXACT ")).isFalse();
    }

    @Test
    void absentAccessSchemaFailsClosedWithoutBlockingSystemSignals() throws IOException {
        SseEmitter reader = register("reader@example.com");
        jdbc.execute("DROP TABLE business_access_grants");
        deliver(null, "dialogs_changed", "T-A");
        deliver("reader@example.com", "dialog_history_changed", "T-A");
        verifyNoInteractions(reader);
        stream.publishNotificationsChanged("reader@example.com", "notification_created");
        stream.publishSidebarBotsChanged("updated", 9L);
        stream.publishSidebarUnblockChanged("updated");
        stream.sendHeartbeat();
        verify(reader, times(4)).send(any(SseEmitter.SseEventBuilder.class));
    }

    private void deliver(String target, String name, String ticketId) {
        // Claimed scope in a queued envelope never grants access.
        stream.deliverFanoutEvent(new UiEventFanoutEnvelope("queued", "other-worker", target, name,
            Map.of("ticketId", ticketId, "scopeKind", "BUSINESS", "businessId", 1L),
            access.captureScope(ticketId).orElse(null)));
    }

    private boolean canRead(String identity, String ticketId) {
        return access.canReadTicket(identity, access.captureScope(ticketId).orElse(null));
    }

    @SuppressWarnings("unchecked")
    private SseEmitter register(String identity) {
        var emitters = (Map<String, CopyOnWriteArraySet<SseEmitter>>) ReflectionTestUtils.getField(stream, "emittersByUser");
        SseEmitter emitter = mock(SseEmitter.class);
        emitters.computeIfAbsent(identity, key -> new CopyOnWriteArraySet<>()).add(emitter);
        return emitter;
    }
}
