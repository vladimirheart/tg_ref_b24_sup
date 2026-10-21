package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.panel.entity.Channel;
import com.example.panel.repository.NotificationRepository;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

class GeneralNotificationBoundaryPostgresIntegrationTest {

    @Test
    void generalUnknownAndMixedOwnersCannotRevealRecipientsOrCreateLegacyNotifications() {
        Fixture f = new Fixture();
        f.jdbc.update("""
            INSERT INTO tickets VALUES (1, 'T-GENERAL', TRUE), (2, 'T-UNKNOWN', NULL),
                (3, 'T-MIXED', FALSE), (4, 'T-MIXED', TRUE)
            """);
        for (String id : List.of("T-GENERAL", "T-UNKNOWN", "T-MIXED", "T-MISSING")) {
            f.jdbc.update("INSERT INTO ticket_responsibles VALUES (?, 'global_operator')", id);
            assertThat(f.service.findDialogRecipients(id)).isEmpty();
            f.service.notifyDialogParticipants(id, "private text", "/settings", null);
            assertThat(f.service.notifyDialogUsers(id, Set.of("global_operator"), "private text", "/settings")).isEmpty();
            new DialogAiAssistantEscalationService(f.service).notifyOperatorsEscalation(id, "private text", "private reason");
        }
        assertThat(f.notificationCount()).isZero();
        verifyNoInteractions(f.repository, f.stream);
    }

    @Test
    void exactTicketAndNormalizedRecipientsArePersistedWithDefaultJdbcTypes() {
        Fixture f = new Fixture();
        String id = " T/точный?% ";
        f.jdbc.update("INSERT INTO tickets VALUES (1, ?, FALSE), (2, ?, TRUE)", id, id.trim());
        String url = f.service.buildDialogUrl(id);

        Set<String> delivered = f.service.notifyDialogUsers(id, Set.of(" Operator ", "operator", " BACKUP "),
            "private payload", url);

        assertThat(delivered).containsExactlyInAnyOrder("operator", "backup");
        assertThat(f.jdbc.queryForList("SELECT user_identity FROM notifications", String.class))
            .containsExactlyInAnyOrder("operator", "backup");
        assertThat(f.jdbc.queryForList("SELECT url FROM notifications", String.class)).containsOnly(url);
        assertThat(f.jdbc.queryForList("SELECT text FROM notifications", String.class)).containsOnly("private payload");
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE is_read = FALSE AND created_at IS NOT NULL", Long.class))
            .isEqualTo(2L);
        verifyNoInteractions(f.repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"general", "duplicate", "missing"})
    void insertSnapshotRejectsOwnerChangedAfterPreliminaryGuard(String mutation) {
        Fixture f = new Fixture();
        f.jdbc.update("INSERT INTO tickets VALUES (1, 'T-LATE', FALSE)");
        f.beforeInsert = () -> {
            switch (mutation) {
                case "general" -> f.jdbc.update("UPDATE tickets SET is_general = TRUE WHERE ticket_id = 'T-LATE'");
                case "duplicate" -> f.jdbc.update("INSERT INTO tickets VALUES (2, 'T-LATE', TRUE)");
                case "missing" -> f.jdbc.update("DELETE FROM tickets WHERE ticket_id = 'T-LATE'");
                default -> throw new IllegalArgumentException(mutation);
            }
        };

        assertThat(f.service.notifyDialogUsers("T-LATE", Set.of("global_operator"), "private text", "/dialogs"))
            .isEmpty();

        assertThat(f.insertAttempts).isEqualTo(1);
        assertThat(f.notificationCount()).isZero();
        verifyNoInteractions(f.repository, f.stream);
    }

    @Test
    void recipientReadSnapshotRejectsLateGeneralWithoutFallingBackToAllOperators() {
        Fixture f = new Fixture();
        f.jdbc.update("INSERT INTO tickets VALUES (1, 'T-LATE', FALSE)");
        f.jdbc.update("INSERT INTO ticket_responsibles VALUES ('T-LATE', 'owner')");
        f.jdbc.update("INSERT INTO ticket_active VALUES ('T-LATE', 'active')");
        f.jdbc.update("INSERT INTO ticket_participants VALUES ('T-LATE', 'participant')");
        f.beforeRecipientRead = () -> f.jdbc.update("UPDATE tickets SET is_general = TRUE WHERE ticket_id = 'T-LATE'");

        f.service.notifyDialogParticipants("T-LATE", "private text", "/dialogs", null);

        assertThat(f.childRowsRead).isZero();
        assertThat(f.insertAttempts).isZero();
        assertThat(f.notificationCount()).isZero();
        verifyNoInteractions(f.repository, f.stream);
    }

    @Test
    void alertQueueReportsOnlyActuallyInsertedRecipientsAfterRoutingChangesScope() {
        Fixture f = new Fixture();
        f.jdbc.update("INSERT INTO tickets VALUES (1, 'T-LATE', FALSE)");
        ChannelAssignmentRoutingService routing = mock(ChannelAssignmentRoutingService.class);
        when(routing.resolveRecipients(any(), any(), any())).thenAnswer(call -> {
            f.jdbc.update("UPDATE tickets SET is_general = TRUE WHERE ticket_id = 'T-LATE'");
            return new ChannelAssignmentRoutingService.ResolvedAssignmentRouting(true,
                List.of("global_operator"), "global_operator", "operator_pool", "round_robin");
        });
        AlertQueueService queue = new AlertQueueService(f.jdbc, f.service, new ObjectMapper(), routing, f.guard);
        Channel channel = new Channel();
        channel.setId(7L);

        assertThat(queue.notifyQueueForNewPublicAppealRecipients(channel, "T-LATE", "private preview")).isEmpty();
        assertThat(f.notificationCount()).isZero();
        verifyNoInteractions(f.repository, f.stream);
    }

    @Test
    void directAlertQueueEntriesRejectGeneralBeforeRouting() {
        Fixture f = new Fixture();
        f.jdbc.update("INSERT INTO tickets VALUES (1, 'T-GENERAL', TRUE)");
        ChannelAssignmentRoutingService routing = mock(ChannelAssignmentRoutingService.class);
        AlertQueueService queue = new AlertQueueService(f.jdbc, f.service, new ObjectMapper(), routing, f.guard);
        Channel channel = new Channel();
        channel.setId(7L);

        assertThat(queue.notifyQueueForNewPublicAppeal(channel, "T-GENERAL", "private preview")).isFalse();
        assertThat(queue.notifyIncomingClientMessage(channel, "T-GENERAL", "private message")).isFalse();
        assertThat(queue.notifyFirstResponseOverdue(channel, "T-GENERAL", 120)).isFalse();

        assertThat(f.notificationCount()).isZero();
        verifyNoInteractions(routing, f.repository, f.stream);
    }

    @Test
    void missingMarkerSchemaFailsClosedForTicketDelivery() {
        Fixture f = new Fixture();
        f.jdbc.execute("ALTER TABLE tickets DROP COLUMN is_general");
        f.jdbc.update("INSERT INTO tickets VALUES (1, 'T-OLD-SCHEMA')");

        assertThat(f.service.notifyDialogUsers("T-OLD-SCHEMA", Set.of("global_operator"), "private text", "/settings"))
            .isEmpty();
        assertThat(f.notificationCount()).isZero();
        verifyNoInteractions(f.repository, f.stream);
    }

    private static final class Fixture {
        private final JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("general_notification_boundary");
        private final JdbcTemplate observed = spy(jdbc);
        private final NotificationRepository repository = mock(NotificationRepository.class);
        private final UiEventStreamService stream = mock(UiEventStreamService.class);
        private final LegacyTicketIdJdbcGuard guard = new LegacyTicketIdJdbcGuard(jdbc);
        private final NotificationService service = new NotificationService(repository, observed, jdbc, stream, guard);
        private Runnable beforeInsert = () -> { };
        private Runnable beforeRecipientRead = () -> { };
        private int insertAttempts;
        private int childRowsRead;

        private Fixture() {
            jdbc.execute("CREATE TABLE tickets(user_id BIGINT, ticket_id TEXT, is_general BOOLEAN, business_id BIGINT, ticket_public_id TEXT, PRIMARY KEY(user_id, ticket_id))");
            jdbc.execute("CREATE TABLE notifications(id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, user_identity TEXT NOT NULL, text TEXT NOT NULL, url TEXT, is_read BOOLEAN, created_at TIMESTAMPTZ, ticket_user_id BIGINT, ticket_id TEXT, ticket_public_id TEXT, ticket_scope_kind TEXT, ticket_business_id BIGINT)");
            jdbc.execute("CREATE TABLE ticket_responsibles(ticket_id TEXT, responsible TEXT)");
            jdbc.execute("CREATE TABLE ticket_active(ticket_id TEXT, user_identity TEXT)");
            jdbc.execute("CREATE TABLE ticket_participants(ticket_id TEXT, username TEXT)");
            jdbc.execute("CREATE TABLE users(id BIGINT GENERATED ALWAYS AS IDENTITY, username TEXT, enabled BOOLEAN, is_blocked BOOLEAN)");
            jdbc.execute("CREATE TABLE businesses(id BIGINT, status TEXT)");
            jdbc.execute("CREATE TABLE business_memberships(user_id BIGINT, business_id BIGINT, role_code TEXT, is_active BOOLEAN)");
            jdbc.execute("CREATE TABLE business_access_grants(user_id BIGINT, grant_scope TEXT, role_code TEXT, is_active BOOLEAN)");
            jdbc.update("INSERT INTO users(username, enabled, is_blocked) VALUES ('global_operator', TRUE, FALSE)");
            doAnswer(call -> {
                insertAttempts++;
                Runnable mutation = beforeInsert;
                beforeInsert = () -> { };
                mutation.run();
                return jdbc.update(call.getArgument(0, String.class), (Object[]) call.getRawArguments()[1]);
            }).when(observed).update(anyString(), any(Object[].class));
            doAnswer(call -> {
                Runnable mutation = beforeRecipientRead;
                beforeRecipientRead = () -> { };
                mutation.run();
                RowCallbackHandler callback = call.getArgument(1);
                jdbc.query(call.getArgument(0, String.class), (RowCallbackHandler) rs -> {
                    childRowsRead++;
                    callback.processRow(rs);
                }, (Object[]) call.getRawArguments()[2]);
                return null;
            }).when(observed).query(anyString(), any(RowCallbackHandler.class), any(Object[].class));
        }

        private long notificationCount() {
            return jdbc.queryForObject("SELECT COUNT(*) FROM notifications", Long.class);
        }
    }
}
