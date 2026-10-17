package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

class UiEventOutboxScopePostgresIntegrationTest {
    private static final AtomicLong IDS = new AtomicLong(925000);
    private static final String PUBLIC_ID = "a".repeat(32);
    private static JdbcTemplate jdbc;
    private static long sushi;
    private static long bliny;
    private MultibusinessFeatureFlags flags;
    private UiEventOutboxAppendService writer;
    private DialogRealtimeEventService events;
    private RuntimeWorkerCheckpointService checkpoint;
    private UiEventOutboxWatcher watcher;
    private long userId;
    private String ticketId;

    @BeforeAll
    static void migrate() {
        jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("ui_outbox_scope");
        Flyway.configure().dataSource(jdbc.getDataSource())
            .locations("filesystem:src/main/resources/db/migration/postgresql").load().migrate();
        sushi = jdbc.queryForObject("SELECT id FROM businesses WHERE code = 'sushi'", Long.class);
        bliny = jdbc.queryForObject("SELECT id FROM businesses WHERE code = 'bliny'", Long.class);
    }

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM ui_event_outbox");
        jdbc.update("DELETE FROM tickets");
        userId = IDS.incrementAndGet();
        ticketId = " event/" + userId + "?% Ю ";
        flags = new MultibusinessFeatureFlags();
        LegacyTicketIdJdbcGuard guard = new LegacyTicketIdJdbcGuard(jdbc);
        writer = new UiEventOutboxAppendService(jdbc, guard);
        events = mock(DialogRealtimeEventService.class);
        checkpoint = new RuntimeWorkerCheckpointService(jdbc);
        checkpoint.saveLongCursor("ui-event-outbox-watch", 0);
        RuntimeCoordinationService coordination = mock(RuntimeCoordinationService.class);
        doAnswer(call -> { call.getArgument(2, Runnable.class).run(); return null; })
            .when(coordination).runWithLease(anyString(), any(Duration.class), any(Runnable.class));
        watcher = new UiEventOutboxWatcher(jdbc, events, coordination, checkpoint, guard,
            new UiEventTicketAccessService(jdbc, flags));
        watcher.initialize();
    }

    @ParameterizedTest
    @ValueSource(strings = {"BUSINESS", "GENERAL", "UNRESOLVED"})
    void writerFreezesCanonicalOwnerAndKindWithoutInferringFromPayload(String kind) {
        owner(userId, ticketId, kind, PUBLIC_ID);
        publish("feedback_created");
        var row = jdbc.queryForMap("SELECT * FROM ui_event_outbox");
        assertThat(row.get("ticket_id")).isEqualTo(ticketId);
        assertThat(row.get("ticket_user_id")).isEqualTo(userId);
        assertThat(row.get("ticket_public_id")).isEqualTo(PUBLIC_ID);
        assertThat(row.get("ticket_scope_kind")).isEqualTo(kind);
        assertThat(row.get("ticket_business_id")).isEqualTo("BUSINESS".equals(kind) ? sushi : null);
        assertThat(row.get("channel_id")).isEqualTo(99L);
        assertThat(row.get("message_text")).isEqualTo("payload");
        assertThat(row.get("message_type")).isEqualTo("text");
        assertThat(row.get("attachment")).isEqualTo("storage/key");

        watcher.watch();
        if ("GENERAL".equals(kind)) verifyNoInteractions(events);
        else verify(events).handleFeedbackCreated(ticketId, 5);
        assertThat(checkpoint.readLongCursor("ui-event-outbox-watch")).isEqualTo(Optional.of((Long) row.get("id")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"business", "general", "missing", "recreated", "tuple", "collision"})
    void staleOwnerStopsBeforeEveryLegacyCallbackAndDoesNotStarveTheNextEvent(String mutation) {
        owner(userId, ticketId, "BUSINESS", PUBLIC_ID);
        for (String type : new String[]{"ticket_created", "client_message_created", "client_message_edited",
                "operator_message_edited", "feedback_created", "ticket_closed_auto", "ticket_closed", "ticket_reopened"}) {
            publish(type);
        }
        var frozen = jdbc.queryForList("SELECT * FROM ui_event_outbox ORDER BY id");
        switch (mutation) {
            case "business" -> jdbc.update("UPDATE tickets SET business_id = ? WHERE user_id = ? AND ticket_id = ?", bliny, userId, ticketId);
            case "general" -> jdbc.update("UPDATE tickets SET business_id = NULL, is_general = TRUE, general_selection_source = 'USER_SELECTION' "
                + "WHERE user_id = ? AND ticket_id = ?", userId, ticketId);
            case "missing", "recreated" -> {
                jdbc.update("DELETE FROM tickets WHERE user_id = ? AND ticket_id = ?", userId, ticketId);
                if ("recreated".equals(mutation)) owner(userId, ticketId, "BUSINESS", "b".repeat(32));
            }
            case "tuple" -> jdbc.update("UPDATE tickets SET user_id = ? WHERE user_id = ? AND ticket_id = ?", IDS.incrementAndGet(), userId, ticketId);
            case "collision" -> owner(IDS.incrementAndGet(), ticketId, "GENERAL", "b".repeat(32));
            default -> throw new IllegalArgumentException(mutation);
        }
        assertThat(jdbc.queryForList("SELECT * FROM ui_event_outbox ORDER BY id")).isEqualTo(frozen);
        String validId = ticketId + "next";
        owner(IDS.incrementAndGet(), validId, "BUSINESS", "c".repeat(32));
        writer.publishFeedbackCreated(validId, 99L, 5);

        watcher.watch();

        verify(events).handleFeedbackCreated(validId, 5);
        verifyNoMoreInteractions(events);
        assertThat(checkpoint.readLongCursor("ui-event-outbox-watch")).isEqualTo(Optional.of(
            jdbc.queryForObject("SELECT MAX(id) FROM ui_event_outbox", Long.class)));
        watcher.watch();
        verifyNoMoreInteractions(events);
    }

    @ParameterizedTest
    @ValueSource(strings = {"BUSINESS", "UNRESOLVED"})
    void nullLocatorRemainsNullAndIsNotUpgradedToTodaysOwnerAtDispatch(String kind) {
        owner(userId, ticketId, kind, null);
        publish("feedback_created");
        jdbc.update("UPDATE tickets SET ticket_public_id = ? WHERE user_id = ? AND ticket_id = ?", PUBLIC_ID, userId, ticketId);
        watcher.watch();
        verifyNoInteractions(events);
        assertThat(jdbc.queryForObject("SELECT ticket_public_id FROM ui_event_outbox", String.class)).isNull();
        assertThat(checkpoint.readLongCursor("ui-event-outbox-watch")).isPresent();
    }

    @Test
    void unclassifiedHistoricalEventIsNeverAssignedTheCurrentOwner() {
        owner(userId, ticketId, "BUSINESS", PUBLIC_ID);
        jdbc.update("INSERT INTO ui_event_outbox(id, event_type, ticket_id, rating, created_at) "
            + "VALUES (12, 'feedback_created', ?, 5, CURRENT_TIMESTAMP)", ticketId);
        watcher.watch();
        verifyNoInteractions(events);
        assertThat(checkpoint.readLongCursor("ui-event-outbox-watch")).isEqualTo(Optional.of(12L));
        assertThat(jdbc.queryForObject("SELECT ticket_user_id FROM ui_event_outbox", Long.class)).isNull();
    }

    @Test
    void strictReadStopsTypedUnresolvedButKeepsBusinessDispatch() {
        owner(userId, ticketId, "UNRESOLVED", PUBLIC_ID);
        publish("feedback_created");
        flags.setStrictReadScope(true);
        watcher.watch();
        verifyNoInteractions(events);
        String businessId = ticketId + "business";
        owner(IDS.incrementAndGet(), businessId, "BUSINESS", "b".repeat(32));
        writer.publishFeedbackCreated(businessId, 99L, 5);
        watcher.watch();
        verify(events).handleFeedbackCreated(businessId, 5);
        verifyNoMoreInteractions(events);
    }

    @Test
    void previousGenerationCreatedEventDoesNotSuppressBellForCurrentGeneration() {
        owner(userId, ticketId, "BUSINESS", PUBLIC_ID);
        publish("ticket_created");
        jdbc.update("DELETE FROM tickets WHERE user_id = ? AND ticket_id = ?", userId, ticketId);
        owner(userId, ticketId, "BUSINESS", "d".repeat(32));
        publish("client_message_created");
        watcher.watch();
        verify(events).handleIncomingClientMessage(ticketId, 99L, "payload", "text", "storage/key", true);
        verifyNoMoreInteractions(events);
    }

    @Test
    void previousGenerationMessageDoesNotPreventSuppressingTheCurrentInitialBell() {
        owner(userId, ticketId, "BUSINESS", PUBLIC_ID);
        publish("client_message_created");
        jdbc.update("DELETE FROM tickets WHERE user_id = ? AND ticket_id = ?", userId, ticketId);
        owner(userId, ticketId, "BUSINESS", "e".repeat(32));
        publish("ticket_created");
        publish("client_message_created");
        publish("client_message_created");
        watcher.watch();
        verify(events).handleTicketCreated(ticketId, 99L, "payload");
        verify(events).handleIncomingClientMessage(ticketId, 99L, "payload", "text", "storage/key", false);
        verify(events).handleIncomingClientMessage(ticketId, 99L, "payload", "text", "storage/key", true);
        verifyNoMoreInteractions(events);
    }

    @Test
    void missingIdentitySchemaFailsClosedBeforeDispatch() {
        UiEventTicketAccessService unavailable = new UiEventTicketAccessService(
            PostgresqlJdbcTestSupport.freshJdbcTemplate("ui_outbox_no_identity"), flags);
        assertThat(unavailable.isCurrentScope(new UiEventTicketScope(1L, "ticket", PUBLIC_ID, "BUSINESS", sushi))).isFalse();
        assertThat(unavailable.isCurrentScope(null)).isFalse();
        assertThat(unavailable.isCurrentScope(new UiEventTicketScope(1L, "ticket", PUBLIC_ID, "GENERAL", null))).isFalse();
    }

    private void publish(String type) {
        writer.append(type, ticketId, 99L, " payload ", " text ", " storage/key ", 5);
    }

    private void owner(long user, String id, String kind, String locator) {
        jdbc.update("INSERT INTO tickets(user_id, ticket_id, ticket_public_id, business_id, is_general, general_selection_source) "
            + "VALUES (?, ?, ?, ?, ?, ?)", user, id, locator, "BUSINESS".equals(kind) ? sushi : null,
            "GENERAL".equals(kind), "GENERAL".equals(kind) ? "USER_SELECTION" : null);
    }
}
