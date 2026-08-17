package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.panel.config.PanelDatabaseRuntimeMode;
import com.example.panel.entity.Channel;
import com.example.panel.entity.Feedback;
import com.example.panel.entity.PendingFeedbackRequest;
import com.example.panel.repository.FeedbackRepository;
import com.example.panel.repository.PendingFeedbackRequestRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;

class BotRuntimeTicketWriteServiceTest {

    private JdbcTemplate jdbcTemplate;
    private BotRuntimeTicketWriteService service;

    @BeforeEach
    void setUp() throws Exception {
        Path dbFile = Files.createTempFile("bot-runtime-ticket-write-", ".db");
        jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource("jdbc:sqlite:" + dbFile.toAbsolutePath()));
        PanelDatabaseRuntimeMode databaseRuntimeMode = new PanelDatabaseRuntimeMode(new MockEnvironment());
        DialogReplyTargetService dialogReplyTargetService = new DialogReplyTargetService(
            jdbcTemplate,
            new ChatAttachmentMetadataService(jdbcTemplate, databaseRuntimeMode)
        );
        service = new BotRuntimeTicketWriteService(
            jdbcTemplate,
            dialogReplyTargetService,
            new UiEventOutboxAppendService(jdbcTemplate),
            mock(PendingFeedbackRequestRepository.class),
            mock(FeedbackRepository.class)
        );
        createSchema();
    }

    @Test
    void reopenTicketRestoresActivityAndAppendsSystemEvent() {
        jdbcTemplate.update("""
                INSERT INTO tickets(ticket_id, status, user_id, channel_id, reopen_count)
                VALUES (?, ?, ?, ?, ?)
                """,
                "T-500", "closed", 55L, 9L, 0
        );

        BotRuntimeTicketWriteService.MutationResult result = service.reopenTicket("T-500");

        assertThat(result.updated()).isTrue();
        assertThat(result.exists()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM tickets WHERE ticket_id = ?",
                String.class,
                "T-500"
        )).isEqualTo("open");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT user_identity FROM ticket_active WHERE ticket_id = ?",
                String.class,
                "T-500"
        )).isEqualTo("55");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT message FROM chat_history WHERE ticket_id = ? AND message_type = 'system_event'",
                String.class,
                "T-500"
        )).isEqualTo(BotRuntimeTicketWriteService.REOPEN_EVENT_TEXT);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT event_type FROM ui_event_outbox WHERE ticket_id = ?",
                String.class,
                "T-500"
        )).isEqualTo("ticket_reopened");
    }

    @Test
    void recordOperatorRelayStoresHistoryAndTouchesActivity() {
        jdbcTemplate.update("""
                INSERT INTO tickets(ticket_id, status, user_id, channel_id, reopen_count)
                VALUES (?, ?, ?, ?, ?)
                """,
                "T-700", "open", 77L, 12L, 0
        );
        jdbcTemplate.update("""
                INSERT INTO messages(id, user_id, ticket_id, channel_id, created_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                7001L, 77L, "T-700", 12L, "2026-08-16T20:00:00Z"
        );

        BotRuntimeTicketWriteService.MutationResult result = service.recordOperatorRelay(
            "T-700",
            "Operator reply",
            8801L,
            8701L,
            "operator"
        );

        assertThat(result.updated()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT sender FROM chat_history WHERE ticket_id = ? AND tg_message_id = ?",
                String.class,
                "T-700",
                8801L
        )).isEqualTo("operator");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT message FROM chat_history WHERE ticket_id = ? AND tg_message_id = ?",
                String.class,
                "T-700",
                8801L
        )).isEqualTo("Operator reply");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT user_identity FROM ticket_active WHERE ticket_id = ?",
                String.class,
                "T-700"
        )).isEqualTo("operator");
    }

    @Test
    void markClientMessageEditedUpdatesHistoryAndAppendsUiEvent() {
        jdbcTemplate.update("""
                INSERT INTO chat_history(
                    user_id, sender, message, timestamp, ticket_id, message_type, channel_id, tg_message_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                88L, "client", "Initial text", "2026-08-16T19:00:00Z", "T-880", "text", 18L, 8100L
        );

        BotRuntimeTicketWriteService.MutationResult result = service.markClientMessageEdited(18L, 8100L, "Edited text");

        assertThat(result.updated()).isTrue();
        assertThat(result.exists()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT message FROM chat_history WHERE ticket_id = ? AND tg_message_id = ?",
                String.class,
                "T-880",
                8100L
        )).isEqualTo("Edited text");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT original_message FROM chat_history WHERE ticket_id = ? AND tg_message_id = ?",
                String.class,
                "T-880",
                8100L
        )).isEqualTo("Initial text");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT event_type FROM ui_event_outbox WHERE ticket_id = ?",
                String.class,
                "T-880"
        )).isEqualTo("client_message_edited");
    }

    @Test
    void storeFeedbackSavesRatingAndAppendsUiEvent() {
        PendingFeedbackRequestRepository pendingFeedbackRequestRepository = mock(PendingFeedbackRequestRepository.class);
        FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
        PanelDatabaseRuntimeMode databaseRuntimeMode = new PanelDatabaseRuntimeMode(new MockEnvironment());
        DialogReplyTargetService dialogReplyTargetService = new DialogReplyTargetService(
            jdbcTemplate,
            new ChatAttachmentMetadataService(jdbcTemplate, databaseRuntimeMode)
        );
        BotRuntimeTicketWriteService feedbackService = new BotRuntimeTicketWriteService(
            jdbcTemplate,
            dialogReplyTargetService,
            new UiEventOutboxAppendService(jdbcTemplate),
            pendingFeedbackRequestRepository,
            feedbackRepository
        );

        Channel channel = new Channel();
        channel.setId(25L);

        PendingFeedbackRequest request = new PendingFeedbackRequest();
        request.setId(903L);
        request.setUserId(77L);
        request.setTicketId("T-903");
        request.setChannel(channel);
        request.setExpiresAt(java.time.OffsetDateTime.parse("2026-08-18T08:00:00Z"));

        when(pendingFeedbackRequestRepository.findById(903L)).thenReturn(Optional.of(request));
        when(feedbackRepository.findFirstByTicketIdOrderByTimestampDesc("T-903")).thenReturn(Optional.empty());

        BotRuntimeTicketWriteService.MutationResult result = feedbackService.storeFeedback(903L, 4);

        assertThat(result.updated()).isTrue();
        assertThat(result.exists()).isTrue();
        verify(feedbackRepository).save(org.mockito.ArgumentMatchers.argThat((Feedback feedback) ->
            feedback.getUserId().equals(77L)
                && feedback.getTicketId().equals("T-903")
                && feedback.getChannelId().equals(25L)
                && feedback.getRating().equals(4)
                && feedback.getTimestamp() != null
        ));
        verify(pendingFeedbackRequestRepository).save(eq(request));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT event_type FROM ui_event_outbox WHERE ticket_id = ?",
                String.class,
                "T-903"
        )).isEqualTo("feedback_created");
    }

    private void createSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE tickets (
                    ticket_id TEXT PRIMARY KEY,
                    status TEXT,
                    resolved_at TEXT,
                    resolved_by TEXT,
                    user_id INTEGER,
                    channel_id INTEGER,
                    reopen_count INTEGER,
                    last_reopen_at TEXT
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE ticket_active (
                    ticket_id TEXT PRIMARY KEY,
                    user_identity TEXT,
                    last_seen TEXT
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE messages (
                    id INTEGER PRIMARY KEY,
                    user_id INTEGER,
                    ticket_id TEXT,
                    channel_id INTEGER,
                    created_at TEXT
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE chat_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER,
                    sender TEXT,
                    message TEXT,
                    timestamp TEXT,
                    ticket_id TEXT,
                    message_type TEXT,
                    attachment TEXT,
                    channel_id INTEGER,
                    tg_message_id INTEGER,
                    reply_to_tg_id INTEGER,
                    original_message TEXT,
                    forwarded_from TEXT,
                    file_name TEXT,
                    edited_at TEXT,
                    deleted_at TEXT
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE ui_event_outbox (
                    id BIGINT PRIMARY KEY,
                    event_type TEXT NOT NULL,
                    ticket_id TEXT NOT NULL,
                    channel_id BIGINT,
                    message_text TEXT,
                    message_type TEXT,
                    attachment TEXT,
                    rating INTEGER,
                    created_at TEXT NOT NULL
                )
                """);
    }
}
