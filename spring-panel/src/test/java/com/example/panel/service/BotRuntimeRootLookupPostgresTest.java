package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.panel.entity.Channel;
import com.example.panel.entity.PendingFeedbackRequest;
import com.example.panel.repository.FeedbackRepository;
import com.example.panel.repository.PendingFeedbackRequestRepository;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

/** Scripted statement-boundary changes against PostgreSQL, not a concurrent transaction test. */
class BotRuntimeRootLookupPostgresTest {
    private static final long OWNER = 9_007_199_254_740_993L;
    private static final long CHANNEL = 9_007_199_254_740_995L;
    private static final String RAW = "T-236";

    enum Scope { BUSINESS, GENERAL, UNRESOLVED }
    enum Boundary { UNIQUE, EARLY_DUPLICATE, LATE_DUPLICATE, REMOVED }
    enum Operation { REOPEN, RELAY, CLIENT_EDIT, OPERATOR_EDIT, FEEDBACK }

    static Stream<Arguments> boundaries() {
        return Stream.of(Scope.values()).flatMap(scope -> Stream.of(Operation.values())
                .flatMap(operation -> Stream.of(Boundary.values())
                        .map(boundary -> Arguments.of(scope, operation, boundary))));
    }

    @ParameterizedTest(name = "{0}/{1}/{2}")
    @MethodSource("boundaries")
    void publicWritesResolveOnlyOneCurrentRawRoot(Scope scope, Operation operation, Boundary boundary) {
        Fixture f = new Fixture(RAW, scope);
        AtomicReference<List<List<Map<String, Object>>>> before = new AtomicReference<>();
        if (boundary == Boundary.EARLY_DUPLICATE) {
            f.duplicate();
        }
        if (boundary == Boundary.LATE_DUPLICATE || boundary == Boundary.REMOVED) {
            f.afterPrecheck(() -> {
                if (boundary == Boundary.LATE_DUPLICATE) {
                    f.duplicate();
                } else {
                    f.jdbc.update("DELETE FROM tickets WHERE ticket_id = ?", RAW);
                }
                before.set(f.snapshot());
            });
        }
        before.set(f.snapshot());

        BotRuntimeTicketWriteService.MutationResult result = f.invoke(operation);

        if (boundary == Boundary.UNIQUE) {
            assertThat(result).isEqualTo(new BotRuntimeTicketWriteService.MutationResult(true, true));
            f.assertSuccessfulHandoff(operation);
        } else {
            assertThat(result).isEqualTo(new BotRuntimeTicketWriteService.MutationResult(false, false));
            assertThat(f.snapshot()).isEqualTo(before.get());
            f.assertNoWrites();
        }
    }

    @ParameterizedTest
    @EnumSource(Scope.class)
    void owningLookupRejectsSecondRootRegardlessOfWhichRowPostgresReturnsFirst(Scope scope) {
        Fixture f = new Fixture(RAW, scope);
        f.afterPrecheck(f::duplicate);

        Object snapshot = ReflectionTestUtils.invokeMethod(f.service, "loadTicket", RAW);

        assertThat(snapshot).isNull();
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM tickets WHERE ticket_id = ?", Long.class, RAW))
                .isEqualTo(2L);
        f.assertNoWrites();
    }

    static Stream<Arguments> rawIds() {
        return Stream.of(" T-236 ", "t-236", "Тест-236", "T-'236", "T-236\t")
                .flatMap(raw -> Stream.of(Operation.values()).map(operation -> Arguments.of(raw, operation)));
    }

    @ParameterizedTest(name = "raw={0}/{1}")
    @MethodSource("rawIds")
    void preservesExactRawIdentityAndBigintOwnership(String raw, Operation operation) {
        Fixture f = new Fixture(raw, Scope.BUSINESS);
        if (!raw.equals(raw.trim())) {
            f.jdbc.update("INSERT INTO tickets(user_id, ticket_id, status, channel_id) VALUES (?, ?, 'closed', ?)",
                    OWNER + 1, raw.trim(), CHANNEL + 1);
        }

        assertThat(f.invoke(operation)).isEqualTo(new BotRuntimeTicketWriteService.MutationResult(true, true));
        f.assertSuccessfulHandoff(operation);
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM tickets WHERE ticket_id = ? AND user_id = ?",
                Long.class, raw, OWNER)).isEqualTo(1L);
        if (!raw.equals(raw.trim())) {
            assertThat(f.jdbc.queryForObject("SELECT status FROM tickets WHERE ticket_id = ?",
                    String.class, raw.trim())).isEqualTo("closed");
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    void invalidIdsStillFailClosedWithoutSideEffects(String raw) {
        Fixture f = new Fixture(RAW, Scope.BUSINESS);
        List<List<Map<String, Object>>> before = f.snapshot();
        Object snapshot = ReflectionTestUtils.invokeMethod(f.service, "loadTicket", raw);
        assertThat(snapshot).isNull();
        assertThat(f.snapshot()).isEqualTo(before);
        f.assertNoWrites();
    }

    @Test
    void earlyDatabaseFailureStillReturnsNoRoot() {
        Fixture f = new Fixture(RAW, Scope.BUSINESS);
        f.jdbc.execute("DROP TABLE tickets");
        Object snapshot = ReflectionTestUtils.invokeMethod(f.service, "loadTicket", RAW);
        assertThat(snapshot).isNull();
        f.assertNoWrites();
    }

    @ParameterizedTest
    @ValueSource(strings = {"user_id", "channel_id"})
    void uniqueRootKeepsNullableProjectionCompatibility(String column) {
        Fixture f = new Fixture(RAW, Scope.BUSINESS);
        if (column.equals("user_id")) {
            f.jdbc.execute("ALTER TABLE tickets DROP CONSTRAINT tickets_pkey");
            f.jdbc.execute("ALTER TABLE tickets ALTER COLUMN user_id DROP NOT NULL");
        }
        f.jdbc.execute("UPDATE tickets SET " + column + " = NULL");
        Object snapshot = ReflectionTestUtils.invokeMethod(f.service, "loadTicket", RAW);
        assertThat(snapshot).isNotNull();
        assertThat((Object) ReflectionTestUtils.getField(snapshot, column.equals("user_id") ? "userId" : "channelId"))
                .isNull();
        f.assertNoWrites();
    }

    private static final class Fixture {
        final JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplate("bot_root_r236");
        final DialogReplyTargetService targets = mock(DialogReplyTargetService.class);
        final DialogResponsibilityService responsibility = mock(DialogResponsibilityService.class);
        final DialogParticipantService participants = mock(DialogParticipantService.class);
        final UiEventOutboxAppendService outbox = mock(UiEventOutboxAppendService.class);
        final ProviderDeliveryLedgerService ledger = mock(ProviderDeliveryLedgerService.class);
        final PendingFeedbackRequestRepository requests = mock(PendingFeedbackRequestRepository.class);
        final FeedbackRepository feedback = mock(FeedbackRepository.class);
        final LegacyTicketIdJdbcGuard guard = spy(new LegacyTicketIdJdbcGuard(jdbc));
        final BotRuntimeTicketWriteService service = new BotRuntimeTicketWriteService(
                jdbc, targets, responsibility, participants, outbox, ledger, requests, feedback, guard);
        final PendingFeedbackRequest pending = new PendingFeedbackRequest();
        final OffsetDateTime expiry = OffsetDateTime.now().plusDays(1);
        final String raw;
        final UiEventOutboxAppendService.TicketContext expectedContext;

        Fixture(String raw, Scope scope) {
            this.raw = raw;
            jdbc.execute("""
                    CREATE TABLE tickets (
                      user_id BIGINT, ticket_id TEXT, channel_id BIGINT, status TEXT,
                      business_id BIGINT, is_general BOOLEAN, ticket_public_id TEXT,
                      resolved_at TIMESTAMPTZ, resolved_by TEXT, reopen_count INTEGER DEFAULT 0,
                      last_reopen_at TIMESTAMPTZ, PRIMARY KEY(user_id, ticket_id))
                    """);
            jdbc.execute("""
                    CREATE TABLE chat_history (
                      id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                      ticket_id TEXT, user_id BIGINT, channel_id BIGINT, tg_message_id BIGINT,
                      sender TEXT, message TEXT, original_message TEXT, edited_at TIMESTAMPTZ,
                      timestamp TIMESTAMPTZ, message_type TEXT, ticket_owner_user_id BIGINT)
                    """);
            Boolean general = scope == Scope.GENERAL ? Boolean.TRUE
                    : scope == Scope.BUSINESS ? Boolean.FALSE : null;
            jdbc.update("""
                    INSERT INTO tickets(user_id,ticket_id,channel_id,status,business_id,is_general,ticket_public_id)
                    VALUES (?, ?, ?, 'closed', ?, ?, 'public-r236')
                    """, OWNER, raw, CHANNEL, scope == Scope.BUSINESS ? 31L : null,
                    general);
            jdbc.update("""
                    INSERT INTO chat_history(ticket_id,user_id,channel_id,tg_message_id,sender,message)
                    VALUES (?, ?, ?, 123, 'client', 'original')
                    """, raw, OWNER, CHANNEL);
            Channel channel = new Channel();
            channel.setId(CHANNEL);
            pending.setId(1L);
            pending.setTicketId(raw);
            pending.setUserId(OWNER);
            pending.setChannel(channel);
            pending.setExpiresAt(expiry);
            when(requests.findByIdForUpdate(1L)).thenReturn(Optional.of(pending));
            when(feedback.findFirstByTicketIdOrderByTimestampDesc(raw)).thenReturn(Optional.empty());
            when(targets.loadReplyTarget(raw)).thenReturn(Optional.of(new DialogReplyTarget(OWNER, CHANNEL)));
            when(targets.markOperatorMessageEdited(anyString(), anyLong(), anyString(), any(DialogReplyTarget.class)))
                    .thenReturn(1);
            expectedContext = new UiEventOutboxAppendService.TicketContext(raw, OWNER, CHANNEL, "public-r236",
                    scope == Scope.BUSINESS ? 31L : null, general);
        }

        void duplicate() {
            jdbc.update("""
                    INSERT INTO tickets(user_id,ticket_id,channel_id,status,business_id,is_general,ticket_public_id)
                    VALUES (?, ?, ?, 'closed', 99, false, 'foreign-public-r236')
                    """, OWNER + 1, raw, CHANNEL + 1);
        }

        void afterPrecheck(Runnable mutation) {
            AtomicBoolean armed = new AtomicBoolean(true);
            doAnswer(call -> {
                boolean unique = (boolean) call.callRealMethod();
                if (unique && armed.compareAndSet(true, false)) {
                    mutation.run();
                }
                return unique;
            }).when(guard).hasUniqueTicket(raw);
        }

        BotRuntimeTicketWriteService.MutationResult invoke(Operation operation) {
            return switch (operation) {
                case REOPEN -> service.reopenTicket(raw);
                case RELAY -> service.recordOperatorRelay(raw, "edited", 123L, null, null);
                case CLIENT_EDIT -> service.markClientMessageEdited(CHANNEL, 123L, "edited");
                case OPERATOR_EDIT -> service.markOperatorMessageEdited(raw, 123L, "edited", null);
                case FEEDBACK -> service.storeFeedback(1L, 5);
            };
        }

        List<List<Map<String, Object>>> snapshot() {
            return List.of(jdbc.queryForList("SELECT * FROM tickets ORDER BY user_id,ticket_id"),
                    jdbc.queryForList("SELECT * FROM chat_history ORDER BY id"));
        }

        void assertNoWrites() {
            verifyNoInteractions(targets, responsibility, participants, outbox, ledger, feedback);
            verify(requests, never()).save(any());
            assertThat(pending.getExpiresAt()).isEqualTo(expiry);
        }

        void assertSuccessfulHandoff(Operation operation) {
            switch (operation) {
                case REOPEN -> {
                    assertThat(jdbc.queryForObject("SELECT status FROM tickets WHERE ticket_id = ?",
                            String.class, raw)).isEqualTo("pending");
                    verify(outbox).publishTicketReopened(expectedContext, BotRuntimeTicketWriteService.REOPEN_EVENT_TEXT);
                    verify(targets).touchTicketActivity(raw, Long.toString(OWNER));
                }
                case RELAY -> {
                    verify(targets).logOutgoingMessage(new DialogReplyTarget(OWNER, CHANNEL), raw, "edited",
                            "operator_message", 123L, null, "operator");
                    verify(ledger).recordObservedSuccess(CHANNEL, raw, OWNER, "operator_runtime", "text", 123L, null);
                }
                case CLIENT_EDIT -> {
                    assertThat(jdbc.queryForObject("SELECT message FROM chat_history WHERE tg_message_id = 123",
                            String.class)).isEqualTo("edited");
                    verify(outbox).publishClientMessageEdited(expectedContext, "edited");
                }
                case OPERATOR_EDIT -> {
                    verify(targets).markOperatorMessageEdited(raw, 123L, "edited", new DialogReplyTarget(OWNER, CHANNEL));
                    verify(outbox).publishOperatorMessageEdited(expectedContext, "edited");
                }
                case FEEDBACK -> {
                    verify(feedback).save(org.mockito.ArgumentMatchers.argThat(saved -> raw.equals(saved.getTicketId())
                            && Long.valueOf(OWNER).equals(saved.getUserId()) && Long.valueOf(CHANNEL).equals(saved.getChannelId())
                            && saved.getRating() == 5));
                    verify(outbox).publishFeedbackCreated(expectedContext, 5);
                    verify(requests).save(pending);
                    assertThat(pending.getExpiresAt()).isBefore(expiry);
                }
            }
        }
    }
}
