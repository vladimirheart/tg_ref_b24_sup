package com.example.panel.service;

import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class UiEventOutboxWatcherTest {

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {" ", "T-MISSING", "T-DUP", " T-PLAIN "})
    void invalidTicketStopsBeforeInitialMessageChildLookupOrDispatch(String ticketId) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        DialogRealtimeEventService events = mock(DialogRealtimeEventService.class);
        LegacyTicketIdJdbcGuard guard = mock(LegacyTicketIdJdbcGuard.class);
        UiEventOutboxWatcher watcher = new UiEventOutboxWatcher(jdbc, events,
            mock(RuntimeCoordinationService.class), mock(RuntimeWorkerCheckpointService.class), guard);

        ReflectionTestUtils.invokeMethod(watcher, "handleEvent",
            10L, "client_message_created", ticketId, 9L, "message", "text", null, null);

        verify(guard).hasUniqueNonGeneralTicket(ticketId);
        verifyNoInteractions(jdbc, events);
    }

    @Test
    void watcherSkipsInvalidEventsButKeepsCursorProgressAndValidLaterDispatch() {
        WatcherFixture fixture = new WatcherFixture();
        fixture.jdbc.update("INSERT INTO tickets VALUES (1, 'T-VALID'), (2, 'T-DUP'), (3, 'T-DUP')");
        fixture.event(10L, "feedback_created", "T-DUP");
        fixture.event(11L, "feedback_created", "T-MISSING");
        fixture.event(12L, "feedback_created", "T-VALID");

        fixture.watcher.watch();

        verify(fixture.events).handleFeedbackCreated("T-VALID", 5);
        verifyNoMoreInteractions(fixture.events);
        assertThat(fixture.checkpoint.readLongCursor("ui-event-outbox-watch")).isEqualTo(Optional.of(12L));
        fixture.watcher.watch();
        verifyNoMoreInteractions(fixture.events);
    }

    @Test
    void generalEventsDoNotReachLegacyDispatchOrBlockLaterEvents() {
        WatcherFixture fixture = new WatcherFixture();
        fixture.jdbc.update("INSERT INTO tickets VALUES (1, 'T-GENERAL', TRUE), (2, 'T-VALID', FALSE)");
        String[] types = {"ticket_created", "client_message_created", "client_message_edited",
            "operator_message_edited", "feedback_created", "ticket_closed_auto", "ticket_closed", "ticket_reopened"};
        for (int i = 0; i < types.length; i++) {
            fixture.event(i + 1L, types[i], "T-GENERAL");
        }
        fixture.event(20L, "feedback_created", "T-VALID");

        fixture.watcher.watch();

        verify(fixture.events).handleFeedbackCreated("T-VALID", 5);
        verifyNoMoreInteractions(fixture.events);
        assertThat(fixture.checkpoint.readLongCursor("ui-event-outbox-watch")).isEqualTo(Optional.of(20L));
    }

    @ParameterizedTest
    @ValueSource(strings = {"own-created", "plain-created-only"})
    void initialClientMessageDetectionUsesExactIdNotPlainNeighboursEvents(String condition) {
        WatcherFixture fixture = new WatcherFixture();
        fixture.jdbc.update("INSERT INTO tickets VALUES (1, ' T-EXACT '), (2, 'T-EXACT')");
        fixture.event(1L, "ticket_created", "T-EXACT");
        if ("own-created".equals(condition)) {
            fixture.event(2L, "client_message_created", "T-EXACT");
            fixture.event(3L, "ticket_created", " T-EXACT ");
        }
        fixture.event(4L, "client_message_created", " T-EXACT ");

        fixture.watcher.watch();

        verify(fixture.events).handleIncomingClientMessage(
            " T-EXACT ", 9L, "message", "text", null, !"own-created".equals(condition));
        assertThat(fixture.checkpoint.readLongCursor("ui-event-outbox-watch")).isEqualTo(Optional.of(4L));
    }

    @Test
    void watcherProcessesASingleFirstEventAndPersistsItsCursor() {
        WatcherFixture fixture = new WatcherFixture();
        fixture.jdbc.update("INSERT INTO tickets VALUES (1, 'T-VALID')");
        fixture.event(1L, "feedback_created", "T-VALID");

        fixture.watcher.watch();

        verify(fixture.events).handleFeedbackCreated("T-VALID", 5);
        assertThat(fixture.checkpoint.readLongCursor("ui-event-outbox-watch")).isEqualTo(Optional.of(1L));
    }

    @Test
    void failedBestEffortNotificationDoesNotStarveNextEventOrHoldCursor() {
        WatcherFixture fixture = new WatcherFixture();
        fixture.jdbc.update("INSERT INTO tickets VALUES (1, 'T-FAIL'), (2, 'T-VALID')");
        fixture.event(10L, "feedback_created", "T-FAIL");
        fixture.event(11L, "feedback_created", "T-VALID");
        doThrow(new IllegalStateException("test dispatch failure")).when(fixture.events).handleFeedbackCreated("T-FAIL", 5);

        fixture.watcher.watch();

        verify(fixture.events).handleFeedbackCreated("T-FAIL", 5);
        verify(fixture.events).handleFeedbackCreated("T-VALID", 5);
        assertThat(fixture.checkpoint.readLongCursor("ui-event-outbox-watch")).isEqualTo(Optional.of(11L));
    }

    private static final class WatcherFixture {
        private final JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplate("ui_outbox_guard");
        private final DialogRealtimeEventService events = mock(DialogRealtimeEventService.class);
        private final RuntimeWorkerCheckpointService checkpoint;
        private final UiEventOutboxWatcher watcher;

        private WatcherFixture() {
            jdbc.execute("CREATE TABLE tickets(user_id BIGINT, ticket_id TEXT, is_general BOOLEAN NOT NULL DEFAULT FALSE, PRIMARY KEY(user_id, ticket_id))");
            jdbc.execute("""
                CREATE TABLE ui_event_outbox(id BIGINT PRIMARY KEY, event_type TEXT, ticket_id TEXT,
                    channel_id BIGINT, message_text TEXT, message_type TEXT, attachment TEXT, rating INTEGER,
                    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP)
                """);
            checkpoint = new RuntimeWorkerCheckpointService(jdbc);
            RuntimeCoordinationService coordination = mock(RuntimeCoordinationService.class);
            doAnswer(invocation -> {
                invocation.getArgument(2, Runnable.class).run();
                return null;
            }).when(coordination).runWithLease(anyString(), any(Duration.class), any(Runnable.class));
            watcher = new UiEventOutboxWatcher(jdbc, events, coordination, checkpoint, new LegacyTicketIdJdbcGuard(jdbc));
            watcher.initialize();
        }

        private void event(long id, String type, String ticketId) {
            jdbc.update("""
                INSERT INTO ui_event_outbox(id, event_type, ticket_id, channel_id, message_text, message_type, rating)
                VALUES (?, ?, ?, 9, 'message', 'text', 5)
                """, id, type, ticketId);
        }
    }
}
