package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.panel.repository.FeedbackRepository;
import com.example.panel.repository.PendingFeedbackRequestRepository;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.util.List;
import java.util.Map;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Changes occur after real SELECTs, immediately before real owning DML; not an MVCC concurrency proof. */
class BotRuntimeOwningWritePostgresTest {
    private static final long OWNER = 9_007_199_254_740_993L;
    private static final long CHANNEL = 9_007_199_254_740_995L;
    enum Scope { BUSINESS, GENERAL, UNRESOLVED }
    enum Operation { REOPEN, CLIENT_EDIT }
    enum Change { NONE, MISSING, OWNER, CHANNEL, DUPLICATE, BUSINESS, GENERAL, PUBLIC_ID, RECREATED, UNRELATED }

    static Stream<Arguments> roots() {
        return Stream.of(Scope.values()).flatMap(scope -> Stream.of(Operation.values())
                .flatMap(operation -> Stream.of(Change.values()).flatMap(change -> Stream.of("T-237", " T-237 ")
                        .map(raw -> Arguments.of(scope, operation, change, raw)))));
    }

    @ParameterizedTest(name = "{0}/{1}/{2}/raw={3}")
    @MethodSource("roots")
    void owningDmlRejectsChangedOrAmbiguousRoot(Scope scope, Operation operation, Change change, String raw) {
        Fixture f = new Fixture(raw, scope, true);
        f.arm(operation, () -> {
            switch (change) {
                case NONE -> { }
                case MISSING -> f.jdbc.update("DELETE FROM tickets WHERE ticket_id = ?", raw);
                case OWNER -> f.jdbc.update("UPDATE tickets SET user_id = ? WHERE ticket_id = ?", OWNER + 1, raw);
                case CHANNEL -> f.jdbc.update("UPDATE tickets SET channel_id = ? WHERE ticket_id = ?", CHANNEL + 1, raw);
                case DUPLICATE -> f.insertRoot(OWNER + 1, raw, CHANNEL + 1, 99L, false, "foreign-public");
                case BUSINESS -> f.jdbc.update("UPDATE tickets SET business_id = 99 WHERE ticket_id = ?", raw);
                case GENERAL -> f.jdbc.update("UPDATE tickets SET is_general = is_general IS NOT TRUE WHERE ticket_id = ?", raw);
                case PUBLIC_ID -> f.jdbc.update("UPDATE tickets SET ticket_public_id = 'new-public' WHERE ticket_id = ?", raw);
                case RECREATED -> {
                    f.jdbc.update("DELETE FROM tickets WHERE ticket_id = ?", raw);
                    f.insertRoot(OWNER, raw, CHANNEL, f.business, f.general, "new-public");
                }
                case UNRELATED -> f.insertRoot(OWNER + 1, raw.equals(raw.trim()) ? "elsewhere" : raw.trim(),
                        CHANNEL + 1, 99L, false, "foreign-public");
            }
        });

        var result = f.invoke(operation);

        assertThat(f.boundary.entered).isTrue();
        if (change == Change.NONE || change == Change.UNRELATED) {
            assertThat(result).isEqualTo(new BotRuntimeTicketWriteService.MutationResult(true, true));
            f.assertSuccess(operation);
            if (change == Change.UNRELATED) {
                assertThat(f.jdbc.queryForObject("SELECT status FROM tickets WHERE user_id = ?", String.class, OWNER + 1))
                        .isEqualTo("closed");
            }
        } else {
            assertThat(result).isEqualTo(new BotRuntimeTicketWriteService.MutationResult(false, true));
            assertThat(f.snapshot()).isEqualTo(f.beforeDml);
            f.assertNoHandoff();
        }
    }

    static Stream<Arguments> historyDuplicates() {
        return Stream.of(Scope.values()).flatMap(scope -> Stream.of("T-237", " T-237 ")
                .flatMap(raw -> Stream.of("same", "other-owner", "other-ticket")
                        .map(variant -> Arguments.of(scope, raw, variant))));
    }

    @ParameterizedTest(name = "{0}/raw={1}/{2}")
    @MethodSource("historyDuplicates")
    void clientEditRejectsHistoryAmbiguityCreatedAfterLookup(Scope scope, String raw, String variant) {
        Fixture f = new Fixture(raw, scope, true);
        f.arm(Operation.CLIENT_EDIT, () -> f.insertHistory(
                variant.equals("other-ticket") ? "foreign-ticket" : raw,
                variant.equals("other-owner") ? OWNER + 1 : OWNER, CHANNEL, 123L, "client"));

        assertThat(f.invoke(Operation.CLIENT_EDIT)).isEqualTo(new BotRuntimeTicketWriteService.MutationResult(false, true));
        assertThat(f.boundary.entered).isTrue();
        assertThat(f.snapshot()).isEqualTo(f.beforeDml);
        f.assertNoHandoff();
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "owner", "channel", "ticket", "provider-id", "sender"})
    void changedChildTupleStillReturnsExistingNoUpdateResult(String change) {
        Fixture f = new Fixture("T-237", Scope.BUSINESS, true);
        f.arm(Operation.CLIENT_EDIT, () -> {
            switch (change) {
                case "missing" -> f.jdbc.update("DELETE FROM chat_history");
                case "owner" -> f.jdbc.update("UPDATE chat_history SET user_id = ?", OWNER + 1);
                case "channel" -> f.jdbc.update("UPDATE chat_history SET channel_id = ?", CHANNEL + 1);
                case "ticket" -> f.jdbc.update("UPDATE chat_history SET ticket_id = 'foreign'");
                case "provider-id" -> f.jdbc.update("UPDATE chat_history SET tg_message_id = 124");
                case "sender" -> f.jdbc.update("UPDATE chat_history SET sender = 'operator'");
            }
        });

        assertThat(f.invoke(Operation.CLIENT_EDIT)).isEqualTo(new BotRuntimeTicketWriteService.MutationResult(false, true));
        assertThat(f.boundary.entered).isTrue();
        assertThat(f.snapshot()).isEqualTo(f.beforeDml);
        f.assertNoHandoff();
    }

    static Stream<Arguments> statuses() {
        return Stream.of(Operation.values()).flatMap(operation -> Stream.of("resolved", "pending", " CLOSED ", null)
                .map(status -> Arguments.of(operation, status)));
    }

    @ParameterizedTest
    @MethodSource("statuses")
    void preservesStatusAndSqlWhitespaceCompatibility(Operation operation, String status) {
        Fixture f = new Fixture("T-237", Scope.BUSINESS, true);
        f.jdbc.update("UPDATE tickets SET status = ?", status);
        boolean updated = operation == Operation.CLIENT_EDIT || "resolved".equals(status);
        var before = f.snapshot();

        assertThat(f.invoke(operation)).isEqualTo(new BotRuntimeTicketWriteService.MutationResult(updated, true));
        if (updated) {
            f.assertSuccess(operation);
        } else {
            assertThat(f.snapshot()).isEqualTo(before);
            f.assertNoHandoff();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"user_id", "channel_id", "ticket_public_id"})
    void preservesUniqueNullableLegacyReopenProjection(String column) {
        Fixture f = new Fixture(" T-237 ", Scope.UNRESOLVED, false);
        f.jdbc.execute("UPDATE tickets SET " + column + " = NULL");

        assertThat(f.invoke(Operation.REOPEN)).isEqualTo(new BotRuntimeTicketWriteService.MutationResult(true, true));
        assertThat(f.jdbc.queryForObject("SELECT status FROM tickets", String.class)).isEqualTo("pending");
        verify(f.outbox).publishTicketReopened(f.raw, column.equals("channel_id") ? null : CHANNEL,
                BotRuntimeTicketWriteService.REOPEN_EVENT_TEXT);
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void rootRemainsLockedThroughOutboxHandoffAndIsReleasedAtCommit(Operation operation) {
        Fixture f = new Fixture("T-237", Scope.BUSINESS, true);
        AtomicReference<String> competingState = new AtomicReference<>();
        org.mockito.stubbing.Answer<Void> competitor = call -> {
            try (var connection = f.jdbc.getDataSource().getConnection(); var statement = connection.createStatement()) {
                statement.execute("SET lock_timeout = '250ms'");
                statement.executeUpdate("UPDATE tickets SET business_id = 99");
                competingState.set("updated");
            } catch (SQLException ex) {
                competingState.set(ex.getSQLState());
            }
            return null;
        };
        if (operation == Operation.REOPEN) {
            doAnswer(competitor).when(f.outbox).publishTicketReopened(f.raw, CHANNEL, BotRuntimeTicketWriteService.REOPEN_EVENT_TEXT);
        } else {
            doAnswer(competitor).when(f.outbox).publishClientMessageEdited(f.raw, CHANNEL, " edited ");
        }
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(f.jdbc.getDataSource()));

        var result = tx.execute(status -> f.invoke(operation));

        assertThat(result).isEqualTo(new BotRuntimeTicketWriteService.MutationResult(true, true));
        assertThat(competingState.get()).isEqualTo("55P03");
        assertThat(f.jdbc.queryForObject("SELECT business_id FROM tickets", Long.class)).isEqualTo(31L);
        assertThat(f.jdbc.update("UPDATE tickets SET business_id = 99")).isEqualTo(1);
    }

    private static final class BoundaryJdbc extends JdbcTemplate {
        Runnable mutation;
        String updatePrefix;
        boolean entered;

        BoundaryJdbc(JdbcTemplate fixture) { super(fixture.getDataSource()); }

        @Override
        public int update(String sql, Object... args) {
            if (!entered && mutation != null && sql.stripLeading().startsWith(updatePrefix)) {
                entered = true;
                mutation.run();
            }
            return super.update(sql, args);
        }
    }

    private static final class Fixture {
        final JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("bot_owning_r237");
        final BoundaryJdbc boundary = new BoundaryJdbc(jdbc);
        final DialogReplyTargetService targets = mock(DialogReplyTargetService.class);
        final DialogResponsibilityService responsibility = mock(DialogResponsibilityService.class);
        final DialogParticipantService participants = mock(DialogParticipantService.class);
        final UiEventOutboxAppendService outbox = mock(UiEventOutboxAppendService.class);
        final ProviderDeliveryLedgerService ledger = mock(ProviderDeliveryLedgerService.class);
        final PendingFeedbackRequestRepository requests = mock(PendingFeedbackRequestRepository.class);
        final FeedbackRepository feedback = mock(FeedbackRepository.class);
        final BotRuntimeTicketWriteService service = new BotRuntimeTicketWriteService(boundary, targets, responsibility,
                participants, outbox, ledger, requests, feedback, new LegacyTicketIdJdbcGuard(boundary));
        final String raw;
        final Long business;
        final Boolean general;
        List<List<Map<String, Object>>> beforeDml;

        Fixture(String raw, Scope scope, boolean primaryKey) {
            this.raw = raw;
            this.business = scope == Scope.BUSINESS ? 31L : null;
            this.general = scope == Scope.GENERAL ? Boolean.TRUE : scope == Scope.BUSINESS ? Boolean.FALSE : null;
            jdbc.execute("""
                    CREATE TABLE tickets (
                      user_id BIGINT, ticket_id TEXT, channel_id BIGINT, status TEXT,
                      business_id BIGINT, is_general BOOLEAN, ticket_public_id TEXT,
                      resolved_at TIMESTAMPTZ, resolved_by TEXT, reopen_count INT DEFAULT 0,
                      last_reopen_at TIMESTAMPTZ)
                    """);
            if (primaryKey) {
                jdbc.execute("ALTER TABLE tickets ADD PRIMARY KEY(user_id,ticket_id)");
            }
            jdbc.execute("""
                    CREATE TABLE chat_history (
                      id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                      ticket_id TEXT,user_id BIGINT,channel_id BIGINT,tg_message_id BIGINT,
                      sender TEXT,message TEXT,original_message TEXT,edited_at TIMESTAMPTZ,
                      timestamp TIMESTAMPTZ,message_type TEXT,ticket_owner_user_id BIGINT)
                    """);
            insertRoot(OWNER, raw, CHANNEL, business, general, "original-public");
            insertHistory(raw, OWNER, CHANNEL, 123L, "client");
        }

        void insertRoot(long owner, String id, long channel, Long business, Boolean general, String publicId) {
            jdbc.update("""
                    INSERT INTO tickets(user_id,ticket_id,channel_id,status,business_id,is_general,ticket_public_id,
                                        resolved_at,resolved_by,reopen_count)
                    VALUES (?, ?, ?, 'closed', ?, ?, ?, '2026-10-01T00:00:00Z', 'old-operator', 2)
                    """, owner, id, channel, business, general, publicId);
        }

        void insertHistory(String id, long owner, long channel, long provider, String sender) {
            jdbc.update("""
                    INSERT INTO chat_history(ticket_id,user_id,channel_id,tg_message_id,sender,message)
                    VALUES (?, ?, ?, ?, ?, 'original')
                    """, id, owner, channel, provider, sender);
        }

        void arm(Operation operation, Runnable mutation) {
            boundary.updatePrefix = operation == Operation.REOPEN ? "UPDATE tickets" : "UPDATE chat_history";
            boundary.mutation = () -> { mutation.run(); beforeDml = snapshot(); };
        }

        BotRuntimeTicketWriteService.MutationResult invoke(Operation operation) {
            return operation == Operation.REOPEN ? service.reopenTicket(raw, "operator")
                    : service.markClientMessageEdited(CHANNEL, 123L, " edited ");
        }

        List<List<Map<String, Object>>> snapshot() {
            return List.of(jdbc.queryForList("SELECT * FROM tickets ORDER BY user_id,ticket_id"),
                    jdbc.queryForList("SELECT * FROM chat_history ORDER BY id"));
        }

        void assertNoHandoff() {
            verifyNoInteractions(targets, responsibility, participants, outbox, ledger, requests, feedback);
        }

        void assertSuccess(Operation operation) {
            if (operation == Operation.REOPEN) {
                var root = jdbc.queryForMap("SELECT * FROM tickets WHERE user_id = ? AND ticket_id = ?", OWNER, raw);
                assertThat(root.get("status")).isEqualTo("pending");
                assertThat(root.get("resolved_at")).isNull();
                assertThat(root.get("resolved_by")).isNull();
                assertThat(root.get("reopen_count")).isEqualTo(3);
                assertThat(root.get("last_reopen_at")).isNotNull();
                verify(targets).touchTicketActivity(raw, Long.toString(OWNER));
                verify(responsibility).assignResponsibleIfMissing(raw, "operator");
                verify(outbox).publishTicketReopened(raw, CHANNEL, BotRuntimeTicketWriteService.REOPEN_EVENT_TEXT);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chat_history WHERE sender = 'system'", Long.class))
                        .isEqualTo(1L);
            } else {
                var row = jdbc.queryForMap("SELECT * FROM chat_history WHERE id = 1");
                assertThat(row.get("message")).isEqualTo("edited");
                assertThat(row.get("original_message")).isEqualTo("original");
                assertThat(row.get("edited_at")).isNotNull();
                verify(outbox).publishClientMessageEdited(raw, CHANNEL, " edited ");
            }
        }
    }
}
