package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.example.panel.entity.Channel;
import com.example.panel.entity.Feedback;
import com.example.panel.entity.PendingFeedbackRequest;
import com.example.panel.repository.FeedbackRepository;
import com.example.panel.repository.PendingFeedbackRequestRepository;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import jakarta.persistence.EntityManagerFactory;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;

/** Cross-request and existing-row locks use real Hibernate and independent PostgreSQL connections. */
class BotRuntimeFeedbackSerializationPostgresTest {
    private static final long OWNER = 9_007_199_254_740_993L;
    private static final long CHANNEL = 9_007_199_254_740_995L;
    private static JdbcTemplate jdbc;
    private static EntityManagerFactory emf;
    private static PendingFeedbackRequestRepository realRequests;
    private static FeedbackRepository realFeedback;
    private PendingFeedbackRequestRepository requests;
    private FeedbackRepository feedback;
    private ObservedJdbc observed;
    private BotRuntimeTicketWriteService service;

    enum Scope { BUSINESS, GENERAL, UNRESOLVED }

    @BeforeAll
    static void database() throws Exception {
        jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("feedback_serial_r241");
        var factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(jdbc.getDataSource());
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setManagedTypes(PersistenceManagedTypes.of(Channel.class.getName(), Feedback.class.getName(),
                PendingFeedbackRequest.class.getName()));
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop",
                "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
        factory.afterPropertiesSet();
        emf = factory.getObject();
        var repositories = new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(emf));
        realRequests = repositories.getRepository(PendingFeedbackRequestRepository.class);
        realFeedback = repositories.getRepository(FeedbackRepository.class);
        jdbc.execute("CREATE TABLE tickets (ticket_id TEXT, user_id BIGINT, channel_id BIGINT, status TEXT, "
                + "ticket_public_id TEXT, business_id BIGINT, is_general BOOLEAN)");
        jdbc.execute(java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/db/migration/postgresql/V84__pending_feedback_context.sql")));
        jdbc.execute("CREATE TABLE ui_event_outbox (id BIGINT PRIMARY KEY, event_type TEXT, ticket_id TEXT, "
                + "channel_id BIGINT, message_text TEXT, message_type TEXT, attachment TEXT, rating INTEGER, "
                + "ticket_user_id BIGINT, ticket_public_id TEXT, ticket_business_id BIGINT, ticket_scope_kind TEXT, "
                + "created_at TIMESTAMPTZ)");
        jdbc.update("INSERT INTO channels(id, token, channel_name) VALUES (?, 'isolated', 'isolated')", CHANNEL);
    }

    @AfterAll
    static void close() { if (emf != null) emf.close(); }

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM pending_feedback_requests");
        jdbc.update("DELETE FROM feedbacks");
        jdbc.update("DELETE FROM ui_event_outbox");
        jdbc.update("DELETE FROM tickets");
        requests = mock(PendingFeedbackRequestRepository.class, delegatesTo(realRequests));
        feedback = mock(FeedbackRepository.class, delegatesTo(realFeedback));
        observed = new ObservedJdbc();
        var guard = new LegacyTicketIdJdbcGuard(observed);
        service = new BotRuntimeTicketWriteService(observed, mock(DialogReplyTargetService.class),
                mock(DialogResponsibilityService.class), mock(DialogParticipantService.class),
                new UiEventOutboxAppendService(observed, guard), mock(ProviderDeliveryLedgerService.class),
                requests, feedback, guard);
    }

    static Stream<Arguments> scopes() {
        return Stream.of(Scope.values()).flatMap(scope -> Stream.of("R241", " R241/?% Ю '")
                .map(raw -> Arguments.of(scope, raw)));
    }

    static Stream<Arguments> ratings() {
        return scopes().flatMap(args -> Stream.of(false, true)
                .map(existing -> Arguments.of(args.get()[0], args.get()[1], existing)));
    }

    @ParameterizedTest(name = "{0}/raw={1}/existing={2}")
    @MethodSource("ratings")
    void distinctValidRequestsSerializeToOneRatingAndKeepBothEvents(Scope scope, String raw, boolean existing) throws Exception {
        seed(scope, raw, existing);
        pending(2L, raw, "auto_close", OffsetDateTime.now().plusDays(1));
        Long originalId = existing ? jdbc.queryForObject("SELECT id FROM feedbacks", Long.class) : null;
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var first = new AtomicBoolean(true);
        doAnswer(call -> {
            if (first.getAndSet(false)) {
                entered.countDown();
                if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test barrier timeout");
            }
            return realFeedback.save(call.getArgument(0));
        }).when(feedback).save(any(Feedback.class));
        var executor = Executors.newFixedThreadPool(2);
        try {
            var a = executor.submit(() -> tx().execute(status -> service.storeFeedback(1L, 4)));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            var b = executor.submit(() -> tx().execute(status -> service.storeFeedback(2L, 5)));
            try { b.get(400, TimeUnit.MILLISECONDS); } catch (TimeoutException expected) { /* first request holds owner */ }
            release.countDown();
            assertThat(a.get(10, TimeUnit.SECONDS)).isEqualTo(new BotRuntimeTicketWriteService.MutationResult(true, true));
            assertThat(b.get(10, TimeUnit.SECONDS)).isEqualTo(new BotRuntimeTicketWriteService.MutationResult(true, true));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM feedbacks", Long.class)).isEqualTo(1);
            var rating = jdbc.queryForMap("SELECT * FROM feedbacks");
            assertThat(rating.get("rating")).isEqualTo(5);
            assertThat(rating.get("ticket_id")).isEqualTo(raw);
            assertThat(rating.get("user_id")).isEqualTo(OWNER);
            assertThat(rating.get("channel_id")).isEqualTo(CHANNEL);
            if (existing) assertThat(rating.get("id")).isEqualTo(originalId);
            assertThat(jdbc.queryForList("SELECT rating FROM ui_event_outbox ORDER BY id", Integer.class)).containsExactly(4, 5);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pending_feedback_requests WHERE expires_at <= CURRENT_TIMESTAMP", Long.class)).isEqualTo(2);
            var beforeReplay = snapshot();
            for (long id : new long[]{1L, 2L}) {
                var replay = tx().execute(status -> service.storeFeedback(id, 2));
                assertThat(replay).isEqualTo(new BotRuntimeTicketWriteService.MutationResult(false, true));
            }
            assertThat(snapshot()).isEqualTo(beforeReplay);
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @ParameterizedTest(name = "{0}/raw={1}")
    @MethodSource("scopes")
    void exclusiveOwnerLockIsHeldBeforeExistingRatingLookup(Scope scope, String raw) {
        seed(scope, raw, false);
        doAnswer(call -> {
            assertThat(lockState("tickets")).isEqualTo("55P03");
            return realFeedback.findFirstByTicketIdOrderByTimestampDesc(raw);
        }).when(feedback).findFirstByTicketIdOrderByTimestampDesc(raw);
        var result = tx().execute(status -> service.storeFeedback(1L, 5));
        assertThat(result).isEqualTo(new BotRuntimeTicketWriteService.MutationResult(true, true));
        assertThat(lockState("tickets")).isEqualTo("locked");
    }

    @ParameterizedTest(name = "{0}/raw={1}")
    @MethodSource("scopes")
    void selectedExistingRatingCannotChangeBetweenLookupAndSave(Scope scope, String raw) {
        seed(scope, raw, true);
        doAnswer(call -> {
            assertThat(lockState("feedbacks")).isEqualTo("55P03");
            return realFeedback.save(call.getArgument(0));
        }).when(feedback).save(any(Feedback.class));
        var result = tx().execute(status -> service.storeFeedback(1L, 5));
        assertThat(result).isEqualTo(new BotRuntimeTicketWriteService.MutationResult(true, true));
        assertThat(lockState("feedbacks")).isEqualTo("locked");
    }

    @ParameterizedTest(name = "{0}/raw={1}")
    @MethodSource("scopes")
    void requestThatExpiresWhileWaitingForOwnerDoesNotWrite(Scope scope, String raw) throws Exception {
        seed(scope, raw, false);
        var expiry = OffsetDateTime.now().plusSeconds(2);
        jdbc.update("UPDATE pending_feedback_requests SET expires_at = ? WHERE id = 1", expiry);
        var before = snapshot();
        var entered = new CountDownLatch(1);
        observed.beforeRootLock = entered::countDown;
        var executor = Executors.newSingleThreadExecutor();
        try (var blocker = jdbc.getDataSource().getConnection(); var statement = blocker.createStatement()) {
            blocker.setAutoCommit(false);
            statement.executeQuery("SELECT * FROM tickets FOR UPDATE");
            try {
                var answer = executor.submit(() -> tx().execute(status -> service.storeFeedback(1L, 5)));
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                long remaining = Duration.between(OffsetDateTime.now(), expiry.plusNanos(100_000_000)).toMillis();
                if (remaining > 0) new CountDownLatch(1).await(remaining, TimeUnit.MILLISECONDS);
                blocker.commit();
                assertThat(answer.get(10, TimeUnit.SECONDS)).isEqualTo(new BotRuntimeTicketWriteService.MutationResult(false, true));
                assertThat(snapshot()).isEqualTo(before);
            } finally { blocker.rollback(); }
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @ParameterizedTest(name = "{0}/raw={1}")
    @MethodSource("scopes")
    void rollbackRestoresRatingAndReleasesOwnerAndRatingLocks(Scope scope, String raw) {
        seed(scope, raw, true);
        var before = snapshot();
        var failure = new IllegalStateException("isolated expiry failure");
        doThrow(failure).when(requests).save(any(PendingFeedbackRequest.class));
        assertThatThrownBy(() -> tx().execute(status -> service.storeFeedback(1L, 5))).isSameAs(failure);
        assertThat(snapshot()).isEqualTo(before);
        assertThat(lockState("tickets")).isEqualTo("locked");
        assertThat(lockState("feedbacks")).isEqualTo("locked");
    }

    @ParameterizedTest(name = "{0}/raw={1}")
    @MethodSource("scopes")
    void foreignExistingRatingIsNotAdopted(Scope scope, String raw) {
        seed(scope, raw, true);
        jdbc.update("UPDATE feedbacks SET user_id = ?", OWNER + 20);
        var before = snapshot();
        var result = tx().execute(status -> service.storeFeedback(1L, 5));
        assertThat(result).isEqualTo(new BotRuntimeTicketWriteService.MutationResult(false, false));
        assertThat(snapshot()).isEqualTo(before);
        assertThat(lockState("tickets")).isEqualTo("locked");
        assertThat(lockState("feedbacks")).isEqualTo("locked");
    }

    private static String lockState(String table) {
        if (!List.of("tickets", "feedbacks").contains(table)) throw new IllegalArgumentException(table);
        try (var c = jdbc.getDataSource().getConnection(); var statement = c.createStatement()) {
            c.setAutoCommit(false);
            try {
                statement.executeQuery("SELECT * FROM " + table + " FOR UPDATE NOWAIT");
                return "locked";
            } finally { c.rollback(); }
        } catch (SQLException ex) { return ex.getSQLState(); }
    }

    private static void seed(Scope scope, String raw, boolean existing) {
        jdbc.update("INSERT INTO tickets(ticket_id,user_id,channel_id,status,ticket_public_id,business_id,is_general) VALUES (?, ?, ?, 'closed', ?, ?, ?)", raw, OWNER, CHANNEL,
                "a".repeat(32), scope == Scope.BUSINESS ? 31L : null, scope == Scope.GENERAL);
        pending(1L, raw, "operator_close", OffsetDateTime.now().plusDays(1));
        if (existing) jdbc.update("INSERT INTO feedbacks(user_id, channel_id, ticket_id, rating, timestamp) "
                + "VALUES (?, ?, ?, 1, ?)", OWNER, CHANNEL, raw, OffsetDateTime.now().minusDays(1));
    }

    private static void pending(long id, String raw, String source, OffsetDateTime expiry) {
        jdbc.update("INSERT INTO pending_feedback_requests(id, user_id, channel_id, ticket_id, source, created_at, expires_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)", id, OWNER, CHANNEL, raw, source,
                OffsetDateTime.now().minusMinutes(1), expiry);
    }

    private static List<List<Map<String, Object>>> snapshot() {
        return List.of(jdbc.queryForList("SELECT * FROM feedbacks ORDER BY id"),
                jdbc.queryForList("SELECT * FROM pending_feedback_requests ORDER BY id"),
                jdbc.queryForList("SELECT * FROM ui_event_outbox ORDER BY id"));
    }

    private static TransactionTemplate tx() { return new TransactionTemplate(new JpaTransactionManager(emf)); }

    private static final class ObservedJdbc extends JdbcTemplate {
        Runnable beforeRootLock;
        ObservedJdbc() { super(jdbc.getDataSource()); }
        @Override public <T> List<T> queryForList(String sql, Class<T> type, Object... args) {
            if (beforeRootLock != null && (sql.contains("FOR SHARE OF owner") || sql.contains("FOR UPDATE OF owner"))) {
                beforeRootLock.run();
            }
            return super.queryForList(sql, type, args);
        }
    }
}
