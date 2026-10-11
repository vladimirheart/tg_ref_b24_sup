package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.panel.entity.Channel;
import com.example.panel.entity.Feedback;
import com.example.panel.entity.PendingFeedbackRequest;
import com.example.panel.repository.FeedbackRepository;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.repository.MonitoringCheckHistoryRepository;
import com.example.panel.repository.PendingFeedbackRequestRepository;
import com.example.panel.repository.ProviderDeliveryLedgerRepository;
import com.example.panel.storage.AttachmentObjectStorageService;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.util.Optional;
import java.util.List;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

class BotRuntimeTicketWriteServiceTest {

    private JdbcTemplate jdbcTemplate;
    private BotRuntimeTicketWriteService service;
    private DialogResponsibilityService dialogResponsibilityService;
    private DialogParticipantService dialogParticipantService;
    private ChannelRepository channelRepository;
    private ProviderDeliveryLedgerService providerDeliveryLedgerService;
    private PendingFeedbackRequestRepository pendingRepository;
    private FeedbackRepository feedbackRepository;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate = PostgresqlJdbcTestSupport.freshJdbcTemplate("bot_runtime_ticket_write");
        LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard = new LegacyTicketIdJdbcGuard(jdbcTemplate);
        DialogReplyTargetService dialogReplyTargetService = new DialogReplyTargetService(
            jdbcTemplate,
            new ChatAttachmentMetadataService(jdbcTemplate, mock(AttachmentObjectStorageService.class)),
            legacyTicketIdJdbcGuard
        );
        dialogResponsibilityService = new DialogResponsibilityService(jdbcTemplate, legacyTicketIdJdbcGuard);
        dialogParticipantService = new DialogParticipantService(
            jdbcTemplate,
            jdbcTemplate,
            legacyTicketIdJdbcGuard
        );
        channelRepository = mock(ChannelRepository.class);
        providerDeliveryLedgerService = new ProviderDeliveryLedgerService(
            new ProviderDeliveryLedgerRepository(jdbcTemplate),
            new MonitoringCheckHistoryRepository(jdbcTemplate),
            channelRepository
        );
        pendingRepository = mock(PendingFeedbackRequestRepository.class);
        feedbackRepository = mock(FeedbackRepository.class);
        service = new BotRuntimeTicketWriteService(
            jdbcTemplate,
            dialogReplyTargetService,
            dialogResponsibilityService,
            dialogParticipantService,
            new UiEventOutboxAppendService(jdbcTemplate, legacyTicketIdJdbcGuard),
            providerDeliveryLedgerService,
            pendingRepository,
            feedbackRepository,
            legacyTicketIdJdbcGuard
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

        BotRuntimeTicketWriteService.MutationResult result = service.reopenTicket("T-500", "operator");

        assertThat(result.updated()).isTrue();
        assertThat(result.exists()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM tickets WHERE ticket_id = ?",
                String.class,
                "T-500"
        )).isEqualTo("pending");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT user_identity FROM ticket_active WHERE ticket_id = ?",
                String.class,
                "T-500"
        )).isEqualTo("55");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT responsible FROM ticket_responsibles WHERE ticket_id = ?",
                String.class,
                "T-500"
        )).isEqualTo("operator");
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
    void doesNotReopenAmbiguousLegacyTicketId() {
        jdbcTemplate.update("""
                INSERT INTO tickets(ticket_id, status, user_id, channel_id, reopen_count)
                VALUES (?, ?, ?, ?, ?), (?, ?, ?, ?, ?)
                """,
                "T-AMB", "closed", 55L, 9L, 0,
                "T-AMB", "closed", 56L, 10L, 0
        );

        BotRuntimeTicketWriteService.MutationResult result = service.reopenTicket("T-AMB", "operator");

        assertThat(result.updated()).isFalse();
        assertThat(result.exists()).isFalse();
        assertThat(jdbcTemplate.queryForList(
                "SELECT status FROM tickets WHERE ticket_id = ? ORDER BY user_id",
                String.class,
                "T-AMB"
        )).containsExactly("closed", "closed");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ticket_active WHERE ticket_id = ?",
                Integer.class,
                "T-AMB"
        )).isZero();
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
        stubChannel(12L, "telegram", "token12");

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
        assertThat(jdbcTemplate.queryForObject(
                "SELECT responsible FROM ticket_responsibles WHERE ticket_id = ?",
                String.class,
                "T-700"
        )).isEqualTo("operator");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT sender_kind FROM provider_delivery_ledger WHERE ticket_id = ? ORDER BY id DESC LIMIT 1",
                String.class,
                "T-700"
        )).isEqualTo("operator_runtime");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT message_kind FROM provider_delivery_ledger WHERE ticket_id = ? ORDER BY id DESC LIMIT 1",
                String.class,
                "T-700"
        )).isEqualTo("text");
    }

    @Test
    void recordOperatorRelayAddsParticipantWhenResponsibleAlreadyAssignedToAnotherOperator() {
        jdbcTemplate.update("""
                INSERT INTO tickets(ticket_id, status, user_id, channel_id, reopen_count)
                VALUES (?, ?, ?, ?, ?)
                """,
                "T-701", "open", 78L, 13L, 0
        );
        jdbcTemplate.update("""
                INSERT INTO messages(id, user_id, ticket_id, channel_id, created_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                7011L, 78L, "T-701", 13L, "2026-08-16T20:00:00Z"
        );
        jdbcTemplate.update("""
                INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by)
                VALUES (?, ?, ?)
                """,
                "T-701", "lead_operator", "lead_operator"
        );

        BotRuntimeTicketWriteService.MutationResult result = service.recordOperatorRelay(
            "T-701",
            "Secondary operator reply",
            8802L,
            8702L,
            "backup_operator"
        );

        assertThat(result.updated()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT responsible FROM ticket_responsibles WHERE ticket_id = ?",
                String.class,
                "T-701"
        )).isEqualTo("lead_operator");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT username FROM ticket_participants WHERE ticket_id = ?",
                String.class,
                "T-701"
        )).isEqualTo("backup_operator");
    }

    @Test
    void markClientMessageEditedUpdatesHistoryAndAppendsUiEvent() {
        jdbcTemplate.update("""
                INSERT INTO tickets(ticket_id, status, user_id, channel_id, reopen_count)
                VALUES (?, ?, ?, ?, ?)
                """,
                "T-880", "pending", 88L, 18L, 0
        );
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
    void markOperatorMessageEditedUpdatesHistoryAndAppendsUiEvent() {
        jdbcTemplate.update("""
                INSERT INTO tickets(ticket_id, status, user_id, channel_id, reopen_count)
                VALUES (?, ?, ?, ?, ?)
                """,
                "T-881", "pending", 88L, 18L, 0
        );
        jdbcTemplate.update("""
                INSERT INTO chat_history(
                    user_id, sender, message, timestamp, ticket_id, message_type, channel_id, tg_message_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                88L, "operator", "Initial operator text", "2026-08-16T19:00:00Z", "T-881", "operator_message", 18L, 8101L
        );
        jdbcTemplate.update("""
                INSERT INTO messages(id, user_id, ticket_id, channel_id, created_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                8101L, 88L, "T-881", 18L, "2026-08-16T19:00:00Z"
        );
        stubChannel(18L, "telegram", "token18");

        BotRuntimeTicketWriteService.MutationResult result = service.markOperatorMessageEdited(
            "T-881",
            8101L,
            "Edited by operator",
            "operator"
        );

        assertThat(result.updated()).isTrue();
        assertThat(result.exists()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT message FROM chat_history WHERE ticket_id = ? AND tg_message_id = ?",
                String.class,
                "T-881",
                8101L
        )).isEqualTo("Edited by operator");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT original_message FROM chat_history WHERE ticket_id = ? AND tg_message_id = ?",
                String.class,
                "T-881",
                8101L
        )).isEqualTo("Initial operator text");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT event_type FROM ui_event_outbox WHERE ticket_id = ? ORDER BY id DESC LIMIT 1",
                String.class,
                "T-881"
        )).isEqualTo("operator_message_edited");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT responsible FROM ticket_responsibles WHERE ticket_id = ?",
                String.class,
                "T-881"
        )).isEqualTo("operator");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT sender_kind FROM provider_delivery_ledger WHERE ticket_id = ? ORDER BY id DESC LIMIT 1",
                String.class,
                "T-881"
        )).isEqualTo("operator_runtime");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT message_kind FROM provider_delivery_ledger WHERE ticket_id = ? ORDER BY id DESC LIMIT 1",
                String.class,
                "T-881"
        )).isEqualTo("text_edit");
    }

    @Test
    void storeFeedbackSavesRatingAndAppendsUiEvent() {
        PendingFeedbackRequestRepository pendingFeedbackRequestRepository = mock(PendingFeedbackRequestRepository.class);
        FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
        DialogReplyTargetService dialogReplyTargetService = new DialogReplyTargetService(
            jdbcTemplate,
            new ChatAttachmentMetadataService(jdbcTemplate, mock(AttachmentObjectStorageService.class)),
            new LegacyTicketIdJdbcGuard(jdbcTemplate)
        );
        BotRuntimeTicketWriteService feedbackService = new BotRuntimeTicketWriteService(
            jdbcTemplate,
            dialogReplyTargetService,
            dialogResponsibilityService,
            dialogParticipantService,
            new UiEventOutboxAppendService(jdbcTemplate, new LegacyTicketIdJdbcGuard(jdbcTemplate)),
            providerDeliveryLedgerService,
            pendingFeedbackRequestRepository,
            feedbackRepository,
            new LegacyTicketIdJdbcGuard(jdbcTemplate)
        );

        Channel channel = new Channel();
        channel.setId(25L);

        PendingFeedbackRequest request = new PendingFeedbackRequest();
        request.setId(903L);
        request.setUserId(77L);
        request.setTicketId("T-903");
        request.setChannel(channel);
        request.setExpiresAt(java.time.OffsetDateTime.now().plusDays(1));

        when(pendingFeedbackRequestRepository.findById(903L)).thenReturn(Optional.of(request));
        when(feedbackRepository.findFirstByTicketIdOrderByTimestampDesc("T-903")).thenReturn(Optional.empty());
        jdbcTemplate.update("""
                INSERT INTO tickets(ticket_id, status, user_id, channel_id, reopen_count)
                VALUES (?, ?, ?, ?, ?)
                """,
                "T-903", "closed", 77L, 25L, 0
        );

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

    @ParameterizedTest
    @ValueSource(strings = {"missing", "duplicate", "wrong-owner", "wrong-channel",
        "duplicate-provider-same-ticket", "duplicate-provider-other-ticket", "missing-provider"})
    void clientEditDeniesInvalidRootOrAmbiguousProviderLocatorWithoutSideEffects(String condition) {
        if (!"missing".equals(condition)) {
            seedTicket("T-EDIT", 88L, 18L);
        }
        if ("duplicate".equals(condition)) {
            seedTicket("T-EDIT", 99L, 19L);
        }
        seedHistory("T-EDIT", "wrong-owner".equals(condition) ? 99L : 88L,
            "wrong-channel".equals(condition) ? 19L : 18L, 8100L, "client");
        if (condition.startsWith("duplicate-provider")) {
            String secondId = "duplicate-provider-other-ticket".equals(condition) ? "T-OTHER" : "T-EDIT";
            if ("T-OTHER".equals(secondId)) {
                seedTicket(secondId, 99L, 18L);
            }
            seedHistory(secondId, 88L, 18L, 8100L, "client");
        }

        BotRuntimeTicketWriteService.MutationResult result = service.markClientMessageEdited(
            "wrong-channel".equals(condition) ? 19L : 18L,
            "missing-provider".equals(condition) ? 9999L : 8100L, "edited");

        assertThat(result).isEqualTo(new BotRuntimeTicketWriteService.MutationResult(false, false));
        assertThat(jdbcTemplate.queryForList("SELECT message FROM chat_history", String.class))
            .allMatch("initial"::equals);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM chat_history WHERE original_message IS NOT NULL OR edited_at IS NOT NULL",
            Integer.class)).isZero();
        assertNoRuntimeSideEffects();
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "duplicate", "wrong-owner", "wrong-channel"})
    void relayDoesNotUseUnresolvedOrMismatchedRootMessage(String condition) {
        if (!"missing".equals(condition)) {
            seedTicket("T-RELAY", 88L, 18L);
        }
        if ("duplicate".equals(condition)) {
            seedTicket("T-RELAY", 99L, 19L);
        }
        seedMessage("T-RELAY", "wrong-owner".equals(condition) ? 99L : 88L,
            "wrong-channel".equals(condition) ? 19L : 18L, 1L);

        assertThat(service.recordOperatorRelay("T-RELAY", "reply", 8100L, null, "operator").updated()).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chat_history", Integer.class)).isZero();
        assertNoRuntimeSideEffects();
        verifyNoInteractions(channelRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrong-owner", "wrong-channel"})
    void operatorEditDoesNotChangeHistoryWithMismatchedCanonicalContext(String condition) {
        seedTicket("T-OPERATOR", 88L, 18L);
        seedHistory("T-OPERATOR", "wrong-owner".equals(condition) ? 99L : 88L,
            "wrong-channel".equals(condition) ? 19L : 18L, 8100L, "operator");

        assertThat(service.markOperatorMessageEdited("T-OPERATOR", 8100L, "edited", "operator").updated()).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT message FROM chat_history", String.class)).isEqualTo("initial");
        assertNoRuntimeSideEffects();
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "duplicate", "wrong-owner", "wrong-channel", "null-channel", "null-owner"})
    void feedbackDeniesInvalidCanonicalContextWithoutSavingOrExpiringRequest(String condition) {
        if (!"missing".equals(condition)) {
            seedTicket("T-FEEDBACK", 88L, 18L);
        }
        if ("duplicate".equals(condition)) {
            seedTicket("T-FEEDBACK", 99L, 19L);
        }
        PendingFeedbackRequest request = pendingRequest("T-FEEDBACK",
            "null-owner".equals(condition) ? null : "wrong-owner".equals(condition) ? 99L : 88L,
            "null-channel".equals(condition) ? null : "wrong-channel".equals(condition) ? 19L : 18L);
        OffsetDateTime expiresAt = request.getExpiresAt();
        when(pendingRepository.findById(1L)).thenReturn(Optional.of(request));

        assertThat(service.storeFeedback(1L, 4)).isEqualTo(new BotRuntimeTicketWriteService.MutationResult(false, false));
        assertThat(request.getExpiresAt()).isEqualTo(expiresAt);
        verifyNoInteractions(feedbackRepository);
        verify(pendingRepository, never()).save(any());
        assertNoRuntimeSideEffects();
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrong-owner", "wrong-channel"})
    void feedbackDoesNotOverwriteForeignExistingRating(String condition) {
        seedTicket("T-FEEDBACK", 88L, 18L);
        PendingFeedbackRequest request = pendingRequest("T-FEEDBACK", 88L, 18L);
        OffsetDateTime expiresAt = request.getExpiresAt();
        when(pendingRepository.findById(1L)).thenReturn(Optional.of(request));
        Feedback existing = new Feedback();
        existing.setTicketId("T-FEEDBACK");
        existing.setUserId("wrong-owner".equals(condition) ? 99L : 88L);
        existing.setChannelId("wrong-channel".equals(condition) ? 19L : 18L);
        existing.setRating(1);
        when(feedbackRepository.findFirstByTicketIdOrderByTimestampDesc("T-FEEDBACK"))
            .thenReturn(Optional.of(existing));

        assertThat(service.storeFeedback(1L, 4).updated()).isFalse();
        assertThat(existing.getRating()).isEqualTo(1);
        assertThat(request.getExpiresAt()).isEqualTo(expiresAt);
        verify(feedbackRepository, never()).save(any());
        verify(pendingRepository, never()).save(any());
        assertNoRuntimeSideEffects();
    }

    @ParameterizedTest
    @ValueSource(strings = {"register", "clear", "reopen", "relay", "operator-edit", "client-edit", "feedback"})
    void runtimeMutationsPreserveExactIdWithoutChangingPlainNeighbour(String operation) {
        String exactId = " T-EXACT ";
        seedTicket(exactId, 88L, 18L);
        seedTicket("T-EXACT", 99L, 19L);
        seedMessage(exactId, 88L, 18L, 1L);
        seedMessage("T-EXACT", 99L, 19L, 2L);
        seedHistory(exactId, 88L, 18L, 8100L, "client-edit".equals(operation) ? "client" : "operator");
        seedHistory("T-EXACT", 99L, 19L, 8100L, "operator");
        jdbcTemplate.update("""
            INSERT INTO ticket_active(ticket_id, user_identity, last_seen) VALUES
                (?, '88', '2026-08-16T19:00:00Z'), (?, '99', '2026-08-16T19:00:00Z')
            """, exactId, "T-EXACT");
        if ("relay".equals(operation)) {
            jdbcTemplate.update("INSERT INTO ticket_responsibles(ticket_id, responsible) VALUES (?, 'lead')", exactId);
        }
        stubChannel(18L, "telegram", "token18");
        if ("feedback".equals(operation)) {
            PendingFeedbackRequest request = pendingRequest(exactId, 88L, 18L);
            when(pendingRepository.findById(1L)).thenReturn(Optional.of(request));
        }
        BotRuntimeTicketWriteService.MutationResult result = switch (operation) {
            case "register" -> service.registerActivity(exactId, "operator");
            case "clear" -> service.clearActivity(exactId);
            case "reopen" -> service.reopenTicket(exactId, "operator");
            case "relay" -> service.recordOperatorRelay(exactId, "reply", 8200L, null, "backup");
            case "operator-edit" -> service.markOperatorMessageEdited(exactId, 8100L, "edited", "operator");
            case "client-edit" -> service.markClientMessageEdited(18L, 8100L, "edited");
            case "feedback" -> service.storeFeedback(1L, 4);
            default -> throw new AssertionError(operation);
        };

        assertThat(result).isEqualTo(new BotRuntimeTicketWriteService.MutationResult(true, true));
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM tickets WHERE ticket_id = 'T-EXACT'", String.class)).isEqualTo("closed");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT message FROM chat_history WHERE ticket_id = 'T-EXACT'", String.class)).isEqualTo("initial");
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM ticket_active
             WHERE ticket_id = 'T-EXACT' AND user_identity = '99' AND last_seen = '2026-08-16T19:00:00Z'
            """, Integer.class)).isEqualTo(1);
        for (String table : List.of("ticket_responsibles", "ticket_participants", "ui_event_outbox", "provider_delivery_ledger")) {
            assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE ticket_id = 'T-EXACT'", Integer.class)).isZero();
        }
        if ("relay".equals(operation)) {
            assertThat(jdbcTemplate.queryForObject("SELECT ticket_id FROM provider_delivery_ledger", String.class))
                .isEqualTo(exactId);
            assertThat(jdbcTemplate.queryForObject("SELECT ticket_id FROM ticket_participants", String.class))
                .isEqualTo(exactId);
        }
        if (List.of("reopen", "operator-edit", "client-edit", "feedback").contains(operation)) {
            assertThat(jdbcTemplate.queryForObject("SELECT ticket_id FROM ui_event_outbox", String.class))
                .isEqualTo(exactId);
        }
        if ("feedback".equals(operation)) {
            verify(feedbackRepository).save(org.mockito.ArgumentMatchers.argThat(feedback ->
                exactId.equals(feedback.getTicketId()) && Long.valueOf(88L).equals(feedback.getUserId())));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"valid", "missing", "duplicate", "wrong-owner", "wrong-channel"})
    void directOperatorDeleteRequiresUniqueCanonicalHistoryContext(String condition) {
        if (!"missing".equals(condition)) {
            seedTicket("T-DELETE", 88L, 18L);
        }
        if ("duplicate".equals(condition)) {
            seedTicket("T-DELETE", 99L, 19L);
        }
        seedHistory("T-DELETE", "wrong-owner".equals(condition) ? 99L : 88L,
            "wrong-channel".equals(condition) ? 19L : 18L, 8100L, "operator");
        DialogReplyTargetService reply = new DialogReplyTargetService(
            jdbcTemplate, mock(ChatAttachmentMetadataService.class), new LegacyTicketIdJdbcGuard(jdbcTemplate));

        assertThat(reply.markOperatorMessageDeleted("T-DELETE", 8100L)).isEqualTo("valid".equals(condition) ? 1 : 0);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT deleted_at IS NOT NULL FROM chat_history", Boolean.class)).isEqualTo("valid".equals(condition));
    }

    @Test
    void outgoingMediaPersistsExactTicketIdAndPassesItToAttachmentMetadata() {
        seedTicket(" T-MEDIA ", 88L, 18L);
        ChatAttachmentMetadataService metadata = mock(ChatAttachmentMetadataService.class);
        DialogReplyTargetService reply = new DialogReplyTargetService(
            jdbcTemplate, metadata, new LegacyTicketIdJdbcGuard(jdbcTemplate));

        String timestamp = reply.logOutgoingMediaMessage(new DialogReplyTarget(88L, 18L), " T-MEDIA ",
            "caption", "stored.png", "original.png", "image/png", 10L, "image", 8100L, null);

        assertThat(OffsetDateTime.parse(timestamp)).isNotNull();
        assertThat(jdbcTemplate.queryForObject("SELECT ticket_id FROM chat_history", String.class)).isEqualTo(" T-MEDIA ");
        verify(metadata).upsertForChatHistory(org.mockito.ArgumentMatchers.anyLong(), eq(" T-MEDIA "), eq(18L),
            eq("stored.png"), eq("original.png"), eq("image/png"), eq(10L), eq("image"));
    }

    @Test
    void validExistingFeedbackCanBeUpdatedWithoutReplacingItsIdentity() {
        seedTicket("T-FEEDBACK", 88L, 18L);
        PendingFeedbackRequest request = pendingRequest("T-FEEDBACK", 88L, 18L);
        when(pendingRepository.findById(1L)).thenReturn(Optional.of(request));
        Feedback existing = new Feedback();
        existing.setTicketId("T-FEEDBACK");
        existing.setUserId(88L);
        existing.setChannelId(18L);
        existing.setRating(1);
        when(feedbackRepository.findFirstByTicketIdOrderByTimestampDesc("T-FEEDBACK"))
            .thenReturn(Optional.of(existing));

        assertThat(service.storeFeedback(1L, 5)).isEqualTo(new BotRuntimeTicketWriteService.MutationResult(true, true));
        assertThat(existing.getRating()).isEqualTo(5);
        verify(feedbackRepository).save(existing);
        verify(pendingRepository).save(request);
        assertThat(request.getExpiresAt()).isBefore(OffsetDateTime.now());
    }

    @Test
    void expiredCanonicalFeedbackRequestDoesNotReadOrWriteRating() {
        seedTicket("T-FEEDBACK", 88L, 18L);
        PendingFeedbackRequest request = pendingRequest("T-FEEDBACK", 88L, 18L);
        OffsetDateTime expiry = OffsetDateTime.now().minusDays(1);
        request.setExpiresAt(expiry);
        when(pendingRepository.findById(1L)).thenReturn(Optional.of(request));

        assertThat(service.storeFeedback(1L, 5)).isEqualTo(new BotRuntimeTicketWriteService.MutationResult(false, true));
        assertThat(request.getExpiresAt()).isEqualTo(expiry);
        verifyNoInteractions(feedbackRepository);
        verify(pendingRepository, never()).save(any());
        assertNoRuntimeSideEffects();
    }

    private void seedTicket(String ticketId, Long userId, Long channelId) {
        jdbcTemplate.update("""
            INSERT INTO tickets(ticket_id, status, user_id, channel_id, reopen_count)
            VALUES (?, 'closed', ?, ?, 0)
            """, ticketId, userId, channelId);
    }

    private void seedMessage(String ticketId, Long userId, Long channelId, Long id) {
        jdbcTemplate.update("""
            INSERT INTO messages(id, user_id, ticket_id, channel_id, created_at)
            VALUES (?, ?, ?, ?, '2026-08-16T19:00:00Z')
            """, id, userId, ticketId, channelId);
    }

    private void seedHistory(String ticketId, Long userId, Long channelId, Long providerId, String sender) {
        jdbcTemplate.update("""
            INSERT INTO chat_history(user_id, sender, message, timestamp, ticket_id, message_type, channel_id, tg_message_id)
            VALUES (?, ?, 'initial', '2026-08-16T19:00:00Z', ?, 'text', ?, ?)
            """, userId, sender, ticketId, channelId, providerId);
    }

    private PendingFeedbackRequest pendingRequest(String ticketId, Long userId, Long channelId) {
        PendingFeedbackRequest request = new PendingFeedbackRequest();
        request.setId(1L);
        request.setTicketId(ticketId);
        request.setUserId(userId);
        if (channelId != null) {
            Channel channel = new Channel();
            channel.setId(channelId);
            request.setChannel(channel);
        }
        request.setExpiresAt(OffsetDateTime.now().plusDays(1));
        return request;
    }

    private void assertNoRuntimeSideEffects() {
        for (String table : List.of("ticket_active", "ticket_responsibles", "ticket_participants",
                "ui_event_outbox", "provider_delivery_ledger")) {
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class))
                .as(table).isZero();
        }
    }

    private void createSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE channels (
                    id BIGINT PRIMARY KEY,
                    token TEXT,
                    channel_name TEXT,
                    platform TEXT,
                    is_active BOOLEAN,
                    created_at TIMESTAMP WITH TIME ZONE
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE tickets (
                    ticket_id TEXT,
                    status TEXT,
                    resolved_at TIMESTAMP WITH TIME ZONE,
                    resolved_by TEXT,
                    user_id BIGINT,
                    channel_id BIGINT,
                    reopen_count INTEGER,
                    last_reopen_at TIMESTAMP WITH TIME ZONE,
                    PRIMARY KEY (user_id, ticket_id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE ticket_active (
                    ticket_id TEXT PRIMARY KEY,
                    user_identity TEXT,
                    last_seen TIMESTAMP WITH TIME ZONE
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE messages (
                    id BIGINT PRIMARY KEY,
                    user_id BIGINT,
                    ticket_id TEXT,
                    channel_id BIGINT,
                    created_at TIMESTAMP WITH TIME ZONE
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE chat_history (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    user_id BIGINT,
                    sender TEXT,
                    message TEXT,
                    timestamp TIMESTAMP WITH TIME ZONE,
                    ticket_id TEXT,
                    message_type TEXT,
                    attachment TEXT,
                    channel_id BIGINT,
                    tg_message_id BIGINT,
                    reply_to_tg_id BIGINT,
                    original_message TEXT,
                    forwarded_from TEXT,
                    file_name TEXT,
                    edited_at TIMESTAMP WITH TIME ZONE,
                    deleted_at TIMESTAMP WITH TIME ZONE
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
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE ticket_responsibles (
                    ticket_id TEXT PRIMARY KEY,
                    responsible TEXT,
                    assigned_by TEXT,
                    last_read_at TIMESTAMP WITH TIME ZONE
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ticket_participants (
                    ticket_id TEXT NOT NULL,
                    username TEXT NOT NULL,
                    added_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    added_by TEXT,
                    PRIMARY KEY (ticket_id, username)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE users (
                    username TEXT PRIMARY KEY,
                    full_name TEXT,
                    photo TEXT,
                    department TEXT,
                    role TEXT,
                    enabled BOOLEAN,
                    is_blocked BOOLEAN
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE provider_delivery_ledger (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    channel_id BIGINT NOT NULL,
                    ticket_id TEXT,
                    platform TEXT NOT NULL,
                    provider TEXT NOT NULL,
                    user_id BIGINT,
                    sender_kind TEXT NOT NULL,
                    message_kind TEXT NOT NULL,
                    delivery_status TEXT NOT NULL,
                    classification TEXT NOT NULL,
                    severity_level TEXT NOT NULL,
                    retry_state TEXT NOT NULL,
                    http_status INTEGER,
                    provider_error_code TEXT,
                    provider_message TEXT,
                    response_excerpt TEXT,
                    provider_message_id BIGINT,
                    reply_to_message_id BIGINT,
                    duration_ms BIGINT,
                    attempted_at TIMESTAMP WITH TIME ZONE NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE monitoring_check_history (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    monitor_kind TEXT NOT NULL,
                    monitor_id BIGINT NOT NULL,
                    check_kind TEXT NOT NULL,
                    status TEXT NOT NULL,
                    summary TEXT,
                    details_excerpt TEXT,
                    http_status INTEGER,
                    duration_ms BIGINT,
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL
                )
                """);
    }
    private void stubChannel(Long channelId, String platform, String token) {
        Channel channel = new Channel();
        channel.setId(channelId);
        channel.setPlatform(platform);
        channel.setToken(token);
        when(channelRepository.findById(channelId)).thenReturn(Optional.of(channel));
    }
}
