package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.panel.entity.Channel;
import com.example.panel.entity.Feedback;
import com.example.panel.entity.PendingFeedbackRequest;
import com.example.panel.repository.FeedbackRepository;
import com.example.panel.repository.PendingFeedbackRequestRepository;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Real SQL boundaries, plus two-connection row-lock checks; no provider or working datasource. */
class BotRuntimeFrozenOutboxPostgresTest {
    private static final long OWNER = 9_007_199_254_740_993L;
    private static final long CHANNEL = 9_007_199_254_740_995L;
    private static final String PUBLIC = "a".repeat(32);
    enum Scope { BUSINESS, GENERAL, UNRESOLVED }
    enum Operation { REOPEN, CLIENT_EDIT, OPERATOR_EDIT, FEEDBACK }
    enum Change { NONE, OWNER, CHANNEL, PUBLIC_ID, BUSINESS, GENERAL, DUPLICATE, MISSING, RECREATED }

    static Stream<Arguments> events() {
        return Stream.of(Scope.values()).flatMap(scope -> Stream.of(Operation.values())
                .flatMap(operation -> Stream.of(Change.values()).flatMap(change -> Stream.of("T-238", " T-238 ")
                        .map(raw -> Arguments.of(scope, operation, change, raw)))));
    }

    @ParameterizedTest(name = "{0}/{1}/{2}/raw={3}")
    @MethodSource("events")
    void runtimeEventCannotBeReboundAtOwningOutboxInsert(Scope scope, Operation operation, Change change, String raw) {
        Fixture f = new Fixture(scope, raw);
        f.observed.beforeInsert = () -> f.change(change);

        assertThat(f.invoke(operation)).isEqualTo(new BotRuntimeTicketWriteService.MutationResult(true, true));

        assertThat(f.observed.entered).isTrue();
        if (change == Change.NONE) {
            var event = f.jdbc.queryForMap("SELECT * FROM ui_event_outbox");
            assertThat(event.get("ticket_id")).isEqualTo(raw);
            assertThat(event.get("channel_id")).isEqualTo(CHANNEL);
            assertThat(event.get("ticket_user_id")).isEqualTo(OWNER);
            assertThat(event.get("ticket_public_id")).isEqualTo(PUBLIC);
            assertThat(event.get("ticket_scope_kind")).isEqualTo(scope.name());
            assertThat(event.get("ticket_business_id")).isEqualTo(f.business);
            assertThat(event.get("event_type")).isEqualTo(switch (operation) {
                case REOPEN -> "ticket_reopened";
                case CLIENT_EDIT -> "client_message_edited";
                case OPERATOR_EDIT -> "operator_message_edited";
                case FEEDBACK -> "feedback_created";
            });
            assertThat(event.get("message_text")).isEqualTo(operation == Operation.FEEDBACK ? null
                    : operation == Operation.REOPEN ? BotRuntimeTicketWriteService.REOPEN_EVENT_TEXT : "edited");
            assertThat(event.get("rating")).isEqualTo(operation == Operation.FEEDBACK ? 5 : null);
        } else {
            assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM ui_event_outbox", Long.class)).isZero();
        }
    }

    static Stream<Arguments> feedbackChanges() {
        return Stream.of(Scope.values()).flatMap(scope -> Stream.of(Change.values())
                .flatMap(change -> Stream.of("T-238", " T-238 ").flatMap(raw -> Stream.of(false, true)
                        .map(existing -> Arguments.of(scope, change, raw, existing)))));
    }

    @ParameterizedTest(name = "{0}/{1}/raw={2}/existing={3}")
    @MethodSource("feedbackChanges")
    void feedbackRevalidatesFrozenRootAfterRepositoryReadBeforeChangingEntities(
            Scope scope, Change change, String raw, boolean hasExisting) {
        Fixture f = new Fixture(scope, raw);
        Feedback existing = new Feedback();
        existing.setId(77L);
        existing.setTicketId(raw);
        existing.setUserId(OWNER);
        existing.setChannelId(CHANNEL);
        existing.setRating(1);
        AtomicReference<List<List<Map<String, Object>>>> before = new AtomicReference<>();
        doAnswer(call -> {
            f.change(change);
            before.set(f.snapshot());
            return hasExisting ? Optional.of(existing) : Optional.empty();
        }).when(f.feedback).findFirstByTicketIdOrderByTimestampDesc(raw);

        var result = f.invoke(Operation.FEEDBACK);

        if (change == Change.NONE) {
            assertThat(result).isEqualTo(new BotRuntimeTicketWriteService.MutationResult(true, true));
            verify(f.feedback).save(any(Feedback.class));
            verify(f.requests).save(f.pending);
            assertThat(f.pending.getExpiresAt()).isBefore(f.expiry);
            if (hasExisting) {
                assertThat(existing.getRating()).isEqualTo(5);
                assertThat(existing.getId()).isEqualTo(77L);
            }
        } else {
            assertThat(result).isEqualTo(new BotRuntimeTicketWriteService.MutationResult(false, false));
            assertThat(f.snapshot()).isEqualTo(before.get());
            assertThat(f.pending.getExpiresAt()).isEqualTo(f.expiry);
            assertThat(existing.getRating()).isEqualTo(1);
            assertThat(existing.getTimestamp()).isNull();
            verify(f.feedback, never()).save(any());
            verify(f.requests, never()).save(any());
        }
    }

    static Stream<Arguments> locks() {
        return Stream.of(Scope.values()).flatMap(scope -> Stream.of("rating-save", "request-save")
                .map(stage -> Arguments.of(scope, stage)));
    }

    @ParameterizedTest
    @MethodSource("locks")
    void feedbackRootStaysLockedThroughJpaSaveAndRequestExpiryUntilCommit(Scope scope, String stage) {
        Fixture f = new Fixture(scope, "T-238");
        AtomicReference<String> state = new AtomicReference<>();
        org.mockito.stubbing.Answer<Object> competitor = call -> {
            try (var connection = f.jdbc.getDataSource().getConnection(); var statement = connection.createStatement()) {
                statement.execute("SET lock_timeout = '250ms'");
                statement.executeUpdate("UPDATE tickets SET ticket_public_id = '" + "b".repeat(32) + "'");
                state.set("updated");
            } catch (SQLException ex) {
                state.set(ex.getSQLState());
            }
            return call.getArgument(0);
        };
        if (stage.equals("rating-save")) {
            doAnswer(competitor).when(f.feedback).save(any(Feedback.class));
        } else {
            doAnswer(competitor).when(f.requests).save(any(PendingFeedbackRequest.class));
        }

        var result = f.tx().execute(status -> f.invoke(Operation.FEEDBACK));

        assertThat(result).isEqualTo(new BotRuntimeTicketWriteService.MutationResult(true, true));
        assertThat(state.get()).isEqualTo("55P03");
        assertThat(f.jdbc.queryForObject("SELECT ticket_public_id FROM tickets", String.class)).isEqualTo(PUBLIC);
        assertThat(f.jdbc.update("UPDATE tickets SET ticket_public_id = ?", "b".repeat(32))).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(value = Operation.class, names = {"REOPEN", "CLIENT_EDIT"})
    void outboxFailureRollsBackCanonicalSqlMutation(Operation operation) {
        Fixture f = new Fixture(Scope.BUSINESS, " T-238 ");
        var before = f.snapshot();
        f.observed.beforeInsert = () -> { throw new IllegalStateException("test outbox failure"); };

        assertThatThrownBy(() -> f.tx().execute(status -> f.invoke(operation)))
                .isInstanceOf(IllegalStateException.class).hasMessage("test outbox failure");

        assertThat(f.observed.entered).isTrue();
        assertThat(f.snapshot()).isEqualTo(before);
    }

    private static final class ObservedJdbc extends JdbcTemplate {
        Runnable beforeInsert;
        boolean entered;
        ObservedJdbc(JdbcTemplate fixture) { super(fixture.getDataSource()); }
        @Override public int update(String sql, Object... args) {
            if (!entered && beforeInsert != null && sql.stripLeading().startsWith("INSERT INTO ui_event_outbox")) {
                entered = true;
                beforeInsert.run();
            }
            return super.update(sql, args);
        }
    }

    private static final class Fixture {
        final JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("bot_event_r238");
        final ObservedJdbc observed = new ObservedJdbc(jdbc);
        final DialogReplyTargetService targets = mock(DialogReplyTargetService.class);
        final DialogResponsibilityService responsibility = mock(DialogResponsibilityService.class);
        final DialogParticipantService participants = mock(DialogParticipantService.class);
        final ProviderDeliveryLedgerService ledger = mock(ProviderDeliveryLedgerService.class);
        final PendingFeedbackRequestRepository requests = mock(PendingFeedbackRequestRepository.class);
        final FeedbackRepository feedback = mock(FeedbackRepository.class);
        final UiEventOutboxAppendService outbox = new UiEventOutboxAppendService(observed, new LegacyTicketIdJdbcGuard(observed));
        final BotRuntimeTicketWriteService service = new BotRuntimeTicketWriteService(
                jdbc, targets, responsibility, participants, outbox, ledger, requests, feedback, new LegacyTicketIdJdbcGuard(jdbc));
        final PendingFeedbackRequest pending = new PendingFeedbackRequest();
        final OffsetDateTime expiry = OffsetDateTime.now().plusDays(1);
        final String raw;
        final Long business;
        final boolean general;

        Fixture(Scope scope, String raw) {
            this.raw = raw;
            business = scope == Scope.BUSINESS ? 31L : null;
            general = scope == Scope.GENERAL;
            jdbc.execute("""
                    CREATE TABLE tickets (user_id BIGINT, ticket_id TEXT, channel_id BIGINT, status TEXT,
                      ticket_public_id TEXT, business_id BIGINT, is_general BOOLEAN,
                      resolved_at TIMESTAMPTZ, resolved_by TEXT, reopen_count INT DEFAULT 0,
                      last_reopen_at TIMESTAMPTZ, PRIMARY KEY(user_id,ticket_id));
                    CREATE TABLE chat_history (id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                      user_id BIGINT,ticket_id TEXT,channel_id BIGINT,tg_message_id BIGINT,sender TEXT,message TEXT,
                      original_message TEXT,edited_at TIMESTAMPTZ,timestamp TIMESTAMPTZ,message_type TEXT,ticket_owner_user_id BIGINT);
                    CREATE TABLE ui_event_outbox (id BIGINT PRIMARY KEY,event_type TEXT,ticket_id TEXT,channel_id BIGINT,
                      message_text TEXT,message_type TEXT,attachment TEXT,rating INT,created_at TIMESTAMPTZ,
                      ticket_user_id BIGINT,ticket_public_id TEXT,ticket_scope_kind TEXT,ticket_business_id BIGINT)
                    """);
            seedRoot(OWNER, CHANNEL, PUBLIC);
            jdbc.update("""
                    INSERT INTO chat_history(user_id,ticket_id,channel_id,tg_message_id,sender,message)
                    VALUES (?, ?, ?, 123, 'client', 'original')
                    """, OWNER, raw, CHANNEL);
            Channel channel = new Channel();
            channel.setId(CHANNEL);
            pending.setId(1L);
            pending.setUserId(OWNER);
            pending.setChannel(channel);
            pending.setTicketId(raw);
            pending.setExpiresAt(expiry);
            when(requests.findByIdForUpdate(1L)).thenReturn(Optional.of(pending));
            when(feedback.findFirstByTicketIdOrderByTimestampDesc(raw)).thenReturn(Optional.empty());
            when(targets.loadReplyTarget(raw)).thenReturn(Optional.of(new DialogReplyTarget(OWNER, CHANNEL)));
            when(targets.markOperatorMessageEdited(raw, 123L, "edited", new DialogReplyTarget(OWNER, CHANNEL))).thenReturn(1);
        }

        void seedRoot(long owner, long channel, String publicId) {
            jdbc.update("""
                    INSERT INTO tickets(user_id,ticket_id,channel_id,status,ticket_public_id,business_id,is_general)
                    VALUES (?, ?, ?, 'closed', ?, ?, ?)
                    """, owner, raw, channel, publicId, business, general);
        }

        void change(Change change) {
            switch (change) {
                case NONE -> { }
                case OWNER -> jdbc.update("UPDATE tickets SET user_id = ?", OWNER + 1);
                case CHANNEL -> jdbc.update("UPDATE tickets SET channel_id = ?", CHANNEL + 1);
                case PUBLIC_ID -> jdbc.update("UPDATE tickets SET ticket_public_id = ?", "b".repeat(32));
                case BUSINESS -> jdbc.update("UPDATE tickets SET business_id = 99");
                case GENERAL -> jdbc.update("UPDATE tickets SET is_general = NOT is_general");
                case DUPLICATE -> seedRoot(OWNER + 1, CHANNEL + 1, "b".repeat(32));
                case MISSING -> jdbc.update("DELETE FROM tickets");
                case RECREATED -> { jdbc.update("DELETE FROM tickets"); seedRoot(OWNER, CHANNEL, "b".repeat(32)); }
            }
        }

        BotRuntimeTicketWriteService.MutationResult invoke(Operation operation) {
            return switch (operation) {
                case REOPEN -> service.reopenTicket(raw, "operator");
                case CLIENT_EDIT -> service.markClientMessageEdited(CHANNEL, 123L, " edited ");
                case OPERATOR_EDIT -> service.markOperatorMessageEdited(raw, 123L, " edited ", "operator");
                case FEEDBACK -> service.storeFeedback(1L, 5);
            };
        }

        TransactionTemplate tx() { return new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource())); }
        List<List<Map<String, Object>>> snapshot() {
            return List.of(jdbc.queryForList("SELECT * FROM tickets ORDER BY user_id,ticket_id"),
                    jdbc.queryForList("SELECT * FROM chat_history ORDER BY id"), jdbc.queryForList("SELECT * FROM ui_event_outbox ORDER BY id"));
        }
    }
}
