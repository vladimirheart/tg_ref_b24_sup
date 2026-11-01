package com.example.panel.service.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.panel.config.IntegrationRabbitProperties;
import com.example.panel.service.LegacyTicketIdJdbcGuard;
import com.example.panel.service.RuntimeCoordinationService;
import com.example.panel.support.FeedbackPromptOutboxTestSupport;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/** Real competing PostgreSQL sessions; broker sends/confirms remain test doubles. */
class OutboundFeedbackPromptPublishLockPostgresqlTest {
    private static final long USER = 9_007_199_254_740_993L;
    private static final long CHANNEL = USER + 2;
    private static JdbcTemplate jdbc;
    private RabbitTemplate rabbit;
    private OutboundFeedbackPromptPublishOutboxService service;

    @BeforeAll static void database() throws Exception {
        jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("feedback_relay_locks_r248");
        FeedbackPromptOutboxTestSupport.createSchema(jdbc);
    }

    @BeforeEach void reset() {
        for (String table : new String[]{"integration_transport_outbox", "pending_feedback_requests", "tickets"}) {
            jdbc.update("DELETE FROM " + table);
        }
        rabbit = mock(RabbitTemplate.class);
        var settings = new IntegrationRabbitProperties();
        settings.setOutboundExchange("isolated.outbound");
        service = new OutboundFeedbackPromptPublishOutboxService(jdbc, new ObjectMapper(), rabbit, settings,
            mock(RuntimeCoordinationService.class), new LegacyTicketIdJdbcGuard(jdbc));
    }

    static Stream<Arguments> owners() {
        return Stream.of("BUSINESS", "GENERAL", "UNRESOLVED").flatMap(scope ->
            Stream.of("T-R248", " padded/Ю%?' ").map(raw -> Arguments.of(scope, raw)));
    }

    static Stream<Arguments> mutations() {
        return owners().flatMap(owner -> Stream.of("root-update", "root-delete", "pending-update", "pending-delete")
            .map(mutation -> Arguments.of(owner.get()[0], owner.get()[1], mutation)));
    }

    static Stream<Arguments> confirms() {
        return owners().flatMap(owner -> Stream.of(true, false)
            .map(ack -> Arguments.of(owner.get()[0], owner.get()[1], ack)));
    }

    @ParameterizedTest @MethodSource("mutations")
    void holdsPendingAndRootDuringSendAndUntilConfirm(String scope, String raw, String mutation) throws Exception {
        var event = seed(scope, raw);
        var sent = new CountDownLatch(1);
        var releaseSend = new CountDownLatch(1);
        var correlation = new AtomicReference<CorrelationData>();
        doAnswer(call -> {
            correlation.set(call.getArgument(4));
            sent.countDown();
            assertThat(releaseSend.await(5, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(rabbit).convertAndSend(anyString(), anyString(), any(), any(), any(CorrelationData.class));
        var done = new CompletableFuture<Void>();
        Thread dispatch = dispatch(done);
        try {
            assertThat(sent.await(5, TimeUnit.SECONDS)).isTrue();
            assertLocked(raw, mutation);
            releaseSend.countDown();
            await(() -> Arrays.stream(dispatch.getStackTrace()).anyMatch(frame ->
                frame.getMethodName().equals("awaitBrokerConfirm")), "relay waiting for broker confirm");
            assertLocked(raw, mutation);
            correlation.get().getFuture().complete(new CorrelationData.Confirm(true, null));
            done.get(5, TimeUnit.SECONDS);
            assertThat(status(event)).isEqualTo("published");
            assertUnlocked(raw, mutation);
        } finally {
            releaseSend.countDown();
            if (correlation.get() != null) correlation.get().getFuture().complete(new CorrelationData.Confirm(true, null));
            dispatch.join(5000);
            assertThat(dispatch.isAlive()).isFalse();
        }
    }

    @ParameterizedTest @MethodSource("confirms")
    void releasesLocksAfterAckOrNackAndPreservesRetry(String scope, String raw, boolean ack) throws Exception {
        var event = seed(scope, raw);
        doAnswer(call -> {
            call.<CorrelationData>getArgument(4).getFuture().complete(new CorrelationData.Confirm(ack, "isolated nack"));
            return null;
        }).when(rabbit).convertAndSend(anyString(), anyString(), any(), any(), any(CorrelationData.class));
        service.dispatchBatch();
        assertThat(status(event)).isEqualTo(ack ? "published" : "failed");
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM integration_transport_outbox", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT processing_started_at FROM integration_transport_outbox", java.sql.Timestamp.class)).isNull();
        if (!ack) {
            assertThat(jdbc.queryForObject("SELECT published_at FROM integration_transport_outbox", java.sql.Timestamp.class)).isNull();
            assertThat(jdbc.queryForObject("SELECT available_at > updated_at FROM integration_transport_outbox", Boolean.class)).isTrue();
        }
        for (String mutation : new String[]{"root-update", "root-delete", "pending-update", "pending-delete"}) {
            assertUnlocked(raw, mutation);
        }
    }

    @ParameterizedTest @MethodSource("owners")
    void rejectsExpiryChangedWhileWaitingForPendingLock(String scope, String raw) throws Exception {
        var event = seed(scope, raw);
        doAnswer(call -> {
            call.<CorrelationData>getArgument(4).getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).convertAndSend(anyString(), anyString(), any(), any(), any(CorrelationData.class));
        var done = new CompletableFuture<Void>();
        Thread dispatch = null;
        try (Connection blocker = jdbc.getDataSource().getConnection()) {
            blocker.setAutoCommit(false);
            int pid;
            try (var statement = blocker.createStatement(); var rows = statement.executeQuery("SELECT pg_backend_pid()")) {
                rows.next(); pid = rows.getInt(1);
            }
            try (var statement = blocker.createStatement()) {
                statement.execute("SELECT id FROM pending_feedback_requests WHERE id=100 FOR UPDATE");
            }
            dispatch = dispatch(done);
            await(() -> jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE ?=ANY(pg_blocking_pids(pid)))", Boolean.class, pid),
                "relay blocked on the persisted pending row");
            assertThat(done.isDone()).isFalse();
            try (var statement = blocker.createStatement()) {
                statement.executeUpdate("UPDATE pending_feedback_requests SET expires_at=clock_timestamp()-interval '1 second' WHERE id=100");
            }
            blocker.commit();
        } finally {
            if (dispatch != null) dispatch.join(6000);
        }
        done.get(1, TimeUnit.SECONDS);
        assertThat(status(event)).isEqualTo("failed");
        verifyNoInteractions(rabbit);
        assertUnlocked(raw, "pending-update");
        assertUnlocked(raw, "root-update");
    }

    private Thread dispatch(CompletableFuture<Void> done) {
        Thread thread = new Thread(() -> {
            try { service.dispatchBatch(); done.complete(null); }
            catch (Throwable failure) { done.completeExceptionally(failure); }
        }, "r248-isolated-relay");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private void assertLocked(String raw, String mutation) throws Exception {
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try {
                assertThatThrownBy(() -> mutate(connection, raw, mutation))
                    .isInstanceOf(SQLException.class).extracting(failure -> ((SQLException) failure).getSQLState()).isEqualTo("55P03");
            } finally { connection.rollback(); }
        }
    }

    private void assertUnlocked(String raw, String mutation) throws Exception {
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try { mutate(connection, raw, mutation); }
            finally { connection.rollback(); }
        }
    }

    private void mutate(Connection connection, String raw, String mutation) throws SQLException {
        String table = mutation.startsWith("root-") ? "tickets" : "pending_feedback_requests";
        try (var lock = connection.prepareStatement("SELECT ticket_id FROM " + table + " WHERE ticket_id=? FOR UPDATE NOWAIT")) {
            lock.setString(1, raw);
            try (var rows = lock.executeQuery()) { assertThat(rows.next()).isTrue(); }
        }
        String sql = mutation.endsWith("delete") ? "DELETE FROM " + table + " WHERE ticket_id=?"
            : mutation.startsWith("root-") ? "UPDATE tickets SET channel_id=channel_id+1 WHERE ticket_id=?"
            : "UPDATE pending_feedback_requests SET expires_at=clock_timestamp()-interval '1 second' WHERE ticket_id=?";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, raw);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private void await(BooleanSupplier condition, String description) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
        assertThat(condition.getAsBoolean()).as(description).isTrue();
    }

    private OutboundFeedbackPromptEvent seed(String scope, String raw) {
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,channel_id,ticket_public_id,business_id,is_general) VALUES(?,?,?,?,?,?)",
            USER, raw, CHANNEL, "0123456789abcdef0123456789abcdef", scope.equals("BUSINESS") ? USER+6 : null, scope.equals("GENERAL"));
        FeedbackPromptOutboxTestSupport.request(jdbc, 100L, USER, CHANNEL, raw);
        String id = UUID.randomUUID().toString();
        var event = new OutboundFeedbackPromptEvent(id, "feedback.prompt.dispatch", id, "telegram", CHANNEL, 100L, USER, raw, "Rate");
        service.enqueue(event, "isolated.route");
        return event;
    }

    private String status(OutboundFeedbackPromptEvent event) {
        return jdbc.queryForObject("SELECT status FROM integration_transport_outbox WHERE event_id=?", String.class, event.eventId());
    }
}
