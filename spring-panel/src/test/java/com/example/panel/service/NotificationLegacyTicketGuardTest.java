package com.example.panel.service;

import com.example.panel.entity.Notification;
import com.example.panel.repository.NotificationRepository;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class NotificationLegacyTicketGuardTest {

    private JdbcTemplate jdbc;
    private NotificationRepository repository;
    private UiEventStreamService stream;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplate("notification_ticket_guard");
        jdbc.execute("CREATE TABLE tickets(user_id BIGINT, ticket_id TEXT, is_general BOOLEAN NOT NULL DEFAULT FALSE, business_id BIGINT, ticket_public_id TEXT, PRIMARY KEY(user_id, ticket_id))");
        jdbc.execute("CREATE TABLE notifications(id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, user_identity TEXT, text TEXT, url TEXT, is_read BOOLEAN, created_at TIMESTAMPTZ, ticket_user_id BIGINT, ticket_id TEXT, ticket_public_id TEXT, ticket_scope_kind TEXT, ticket_business_id BIGINT)");
        jdbc.execute("CREATE TABLE ticket_responsibles(ticket_id TEXT, responsible TEXT)");
        jdbc.execute("CREATE TABLE ticket_active(ticket_id TEXT, user_identity TEXT)");
        jdbc.execute("CREATE TABLE ticket_participants(ticket_id TEXT, username TEXT)");
        jdbc.execute("CREATE TABLE users(id BIGINT GENERATED ALWAYS AS IDENTITY, username TEXT, enabled BOOLEAN, is_blocked BOOLEAN)");
        jdbc.execute("CREATE TABLE businesses(id BIGINT, status TEXT)");
        jdbc.execute("CREATE TABLE business_memberships(user_id BIGINT, business_id BIGINT, role_code TEXT, is_active BOOLEAN)");
        jdbc.execute("CREATE TABLE business_access_grants(user_id BIGINT, grant_scope TEXT, role_code TEXT, is_active BOOLEAN)");
        jdbc.update("INSERT INTO users(username, enabled, is_blocked) VALUES ('global_operator', TRUE, FALSE)");
        jdbc.update("INSERT INTO tickets VALUES (1, 'T-PLAIN'), (2, 'T-DUP'), (3, 'T-DUP')");
        jdbc.update("""
            INSERT INTO ticket_responsibles VALUES
                ('T-DUP', 'foreign'), ('T-MISSING', 'orphan'), ('T-PLAIN', 'plain_operator')
            """);
        repository = mock(NotificationRepository.class);
        stream = mock(UiEventStreamService.class);
        service = new NotificationService(repository, jdbc, jdbc, stream, new LegacyTicketIdJdbcGuard(jdbc));
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {" ", "T-MISSING", "T-DUP", " T-PLAIN "})
    void invalidTicketCannotRevealRecipientsOrBroadcastThroughGlobalFallback(String ticketId) {
        assertThat(service.findDialogRecipients(ticketId)).isEmpty();

        service.notifyDialogParticipants(ticketId, "private detail", "/dialogs/" + ticketId, null);

        verifyNoInteractions(repository, stream);
    }

    @Test
    void exactTicketRecipientsStaySeparateFromPlainNeighbourAndExcludedActor() {
        jdbc.update("INSERT INTO tickets VALUES (4, ' T-PLAIN ')");
        jdbc.update("INSERT INTO ticket_responsibles VALUES (' T-PLAIN ', ' Lead ')");
        jdbc.update("INSERT INTO ticket_active VALUES (' T-PLAIN ', 'Backup')");
        jdbc.update("INSERT INTO ticket_participants VALUES (' T-PLAIN ', 'lead')");

        assertThat(service.findDialogRecipients(" T-PLAIN ")).isEqualTo(Set.of("lead", "backup"));
        assertThat(service.buildDialogUrl(" T-PLAIN ")).isEqualTo("/dialogs?ticketId=%20T-PLAIN%20");
        service.notifyDialogParticipants(" T-PLAIN ", "rating 5/5", service.buildDialogUrl(" T-PLAIN "), "Lead");

        var saved = jdbc.queryForMap("SELECT user_identity, url, text FROM notifications");
        assertThat(saved.get("user_identity")).isEqualTo("backup");
        assertThat(saved.get("url")).isEqualTo("/dialogs?ticketId=%20T-PLAIN%20");
        assertThat(saved.get("text")).isEqualTo("rating 5/5");
        verify(stream).publishNotificationsChanged("backup", "notification_created");
    }

    @Test
    void uniqueTicketWithoutParticipantsRetainsLegacyOperatorFallback() {
        jdbc.update("INSERT INTO tickets VALUES (4, 'T-EMPTY')");

        service.notifyDialogParticipants("T-EMPTY", "rating 5/5", "/dialogs/T-EMPTY", null);

        assertThat(jdbc.queryForObject("SELECT user_identity FROM notifications", String.class)).isEqualTo("global_operator");
    }

    @Test
    void nonTicketNotificationDoesNotRequireTicketGuard() {
        LegacyTicketIdJdbcGuard guard = mock(LegacyTicketIdJdbcGuard.class);
        NotificationService globalService = new NotificationService(repository, jdbc, jdbc, stream, guard);

        globalService.notifyUser("global_operator", "system ready", "/settings");

        verify(repository).save(any(Notification.class));
        verifyNoInteractions(guard);
    }
}
