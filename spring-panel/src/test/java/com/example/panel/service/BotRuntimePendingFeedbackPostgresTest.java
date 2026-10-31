package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.example.panel.entity.Channel;
import com.example.panel.entity.Feedback;
import com.example.panel.entity.PendingFeedbackRequest;
import com.example.panel.repository.FeedbackRepository;
import com.example.panel.repository.PendingFeedbackRequestRepository;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.sql.SQLException;
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

/** Real Hibernate repositories and JDBC outbox share an isolated PostgreSQL transaction. */
class BotRuntimePendingFeedbackPostgresTest {
    private static final long OWNER = 9_007_199_254_740_993L;
    private static final long CHANNEL = 9_007_199_254_740_995L;
    private static JdbcTemplate jdbc;
    private static EntityManagerFactory emf;
    private static PendingFeedbackRequestRepository realRequests;
    private static FeedbackRepository realFeedback;
    private PendingFeedbackRequestRepository requests;
    private FeedbackRepository feedback;
    private ObservedJdbc observed;

    enum Scope { BUSINESS, GENERAL, UNRESOLVED }

    @BeforeAll
    static void database() {
        jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("pending_feedback_r239");
        var factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(jdbc.getDataSource());
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setManagedTypes(PersistenceManagedTypes.of(Channel.class.getName(), Feedback.class.getName(),
                PendingFeedbackRequest.class.getName()));
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop",
                "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
        factory.afterPropertiesSet();
        emf = factory.getObject();
        EntityManager shared = SharedEntityManagerCreator.createSharedEntityManager(emf);
        var repositories = new JpaRepositoryFactory(shared);
        realRequests = repositories.getRepository(PendingFeedbackRequestRepository.class);
        realFeedback = repositories.getRepository(FeedbackRepository.class);
        jdbc.execute("CREATE TABLE tickets (ticket_id TEXT, user_id BIGINT, channel_id BIGINT, status TEXT, "
                + "ticket_public_id TEXT, business_id BIGINT, is_general BOOLEAN)");
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
    }

    static Stream<Arguments> scopes() {
        return Stream.of(Scope.values()).flatMap(scope -> Stream.of("R239", " R239/?% Ю '")
                .map(raw -> Arguments.of(scope, raw)));
    }

    static Stream<Arguments> duplicates() {
        return scopes().flatMap(args -> Stream.of(false, true)
                .map(existing -> Arguments.of(args.get()[0], args.get()[1], existing)));
    }

    @ParameterizedTest(name = "{0}/raw={1}/existing={2}")
    @MethodSource("duplicates")
    void concurrentRepliesConsumeOneRequestAndPublishOneEvent(Scope scope, String raw, boolean existing) throws Exception {
        seed(scope, raw, existing);
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
            var a = executor.submit(() -> tx().execute(status -> service().storeFeedback(1L, 4)));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            var b = executor.submit(() -> tx().execute(status -> service().storeFeedback(1L, 5)));
            try { b.get(300, TimeUnit.MILLISECONDS); } catch (TimeoutException expected) { /* locked by first reply */ }
            release.countDown();
            var results = List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
            assertThat(results).containsExactlyInAnyOrder(new BotRuntimeTicketWriteService.MutationResult(true, true),
                    new BotRuntimeTicketWriteService.MutationResult(false, true));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM feedbacks", Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT rating FROM feedbacks", Integer.class)).isEqualTo(4);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ui_event_outbox", Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT rating FROM ui_event_outbox", Integer.class)).isEqualTo(4);
            assertThat(jdbc.queryForObject("SELECT ticket_id FROM feedbacks", String.class)).isEqualTo(raw);
            assertThat(jdbc.queryForObject("SELECT user_id FROM feedbacks", Long.class)).isEqualTo(OWNER);
            assertThat(jdbc.queryForObject("SELECT channel_id FROM feedbacks", Long.class)).isEqualTo(CHANNEL);
            var replay = tx().execute(status -> service().storeFeedback(1L, 3));
            assertThat(replay)
                    .isEqualTo(new BotRuntimeTicketWriteService.MutationResult(false, true));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ui_event_outbox", Long.class)).isEqualTo(1);
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @ParameterizedTest(name = "{0}/raw={1}")
    @MethodSource("scopes")
    void pendingRowIsLockedBeforeRatingSaveAndReleasedAtCommit(Scope scope, String raw) {
        seed(scope, raw, false);
        doAnswer(call -> {
            assertThat(pendingLockState()).isEqualTo("55P03");
            return realFeedback.save(call.getArgument(0));
        }).when(feedback).save(any(Feedback.class));
        var result = tx().execute(status -> service().storeFeedback(1L, 5));
        assertThat(result)
                .isEqualTo(new BotRuntimeTicketWriteService.MutationResult(true, true));
        assertThat(pendingLockState()).isEqualTo("locked");
    }

    static Stream<Arguments> failures() {
        return duplicates().flatMap(args -> Stream.of("rating", "outbox", "expiry")
                .map(stage -> Arguments.of(args.get()[0], args.get()[1], args.get()[2], stage)));
    }

    @ParameterizedTest(name = "{0}/raw={1}/existing={2}/failure={3}")
    @MethodSource("failures")
    void anyWriteFailureRollsBackRatingEventAndExpiry(Scope scope, String raw, boolean existing, String stage) {
        seed(scope, raw, existing);
        var before = snapshot();
        var failure = new IllegalStateException("isolated " + stage + " failure");
        if (stage.equals("rating")) doThrow(failure).when(feedback).save(any(Feedback.class));
        else if (stage.equals("expiry")) doThrow(failure).when(requests).save(any(PendingFeedbackRequest.class));
        else observed.failure = failure;
        assertThatThrownBy(() -> tx().execute(status -> service().storeFeedback(1L, 5)))
                .isSameAs(failure);
        assertThat(snapshot()).isEqualTo(before);
        assertThat(pendingLockState()).isEqualTo("locked");
    }

    private static String pendingLockState() {
        try (var c = jdbc.getDataSource().getConnection(); var statement = c.createStatement()) {
            c.setAutoCommit(false);
            try {
                statement.executeQuery("SELECT id FROM pending_feedback_requests WHERE id = 1 FOR UPDATE NOWAIT");
                return "locked";
            } finally { c.rollback(); }
        } catch (SQLException ex) { return ex.getSQLState(); }
    }

    private void seed(Scope scope, String raw, boolean existing) {
        jdbc.update("INSERT INTO tickets VALUES (?, ?, ?, 'closed', ?, ?, ?)", raw, OWNER, CHANNEL,
                "a".repeat(32), scope == Scope.BUSINESS ? 31L : null, scope == Scope.GENERAL);
        jdbc.update("INSERT INTO pending_feedback_requests(id, user_id, channel_id, ticket_id, source, created_at, expires_at) "
                + "VALUES (1, ?, ?, ?, 'isolated', ?, ?)", OWNER, CHANNEL, raw,
                OffsetDateTime.now().minusMinutes(1), OffsetDateTime.now().plusDays(1));
        if (existing) jdbc.update("INSERT INTO feedbacks(user_id, channel_id, ticket_id, rating, timestamp) "
                + "VALUES (?, ?, ?, 1, ?)", OWNER, CHANNEL, raw, OffsetDateTime.now().minusDays(1));
    }

    private List<List<Map<String, Object>>> snapshot() {
        return List.of(jdbc.queryForList("SELECT * FROM feedbacks ORDER BY id"),
                jdbc.queryForList("SELECT * FROM pending_feedback_requests ORDER BY id"),
                jdbc.queryForList("SELECT * FROM ui_event_outbox ORDER BY id"));
    }

    private static TransactionTemplate tx() { return new TransactionTemplate(new JpaTransactionManager(emf)); }

    private BotRuntimeTicketWriteService service() {
        var guard = new LegacyTicketIdJdbcGuard(jdbc);
        return new BotRuntimeTicketWriteService(jdbc, mock(DialogReplyTargetService.class),
                mock(DialogResponsibilityService.class), mock(DialogParticipantService.class),
                new UiEventOutboxAppendService(observed, guard), mock(ProviderDeliveryLedgerService.class),
                requests, feedback, guard);
    }

    private static final class ObservedJdbc extends JdbcTemplate {
        RuntimeException failure;
        ObservedJdbc() { super(jdbc.getDataSource()); }
        @Override public int update(String sql, Object... args) {
            if (failure != null && sql.stripLeading().startsWith("INSERT INTO ui_event_outbox")) throw failure;
            return super.update(sql, args);
        }
    }
}
