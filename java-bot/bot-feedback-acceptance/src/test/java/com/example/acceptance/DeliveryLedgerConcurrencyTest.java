package com.example.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.supportbot.service.OutboundTransportDeliveryLedgerService;
import com.zaxxer.hikari.HikariDataSource;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/** Deterministic real-PostgreSQL claim race; no working database, provider or broker. */
class DeliveryLedgerConcurrencyTest {
    private static PostgreSQLContainer<?> postgres;
    private static HikariDataSource pool;
    private static JdbcTemplate jdbc, admin;
    private static String schema;
    private OutboundTransportDeliveryLedgerService ledger;
    @BeforeAll static void database() {
        String url = System.getenv("FEEDBACK_CHAIN_PG_JDBC_URL");
        if (url == null) {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("r259_chain")
                .withUsername("r259_test").withPassword("r259-test-only")
                .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withMemory(192L*1024*1024).withNanoCPUs(1_000_000_000L));
            postgres.start(); url = postgres.getJdbcUrl();
        } else assertThat(url).matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/r259_chain");
        admin = new JdbcTemplate(new DriverManagerDataSource(url,"r259_test","r259-test-only"));
        schema = "r260_ledger_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("CREATE SCHEMA " + schema);
        pool = new HikariDataSource(); pool.setJdbcUrl(url); pool.setUsername("r259_test"); pool.setPassword("r259-test-only");
        pool.setSchema(schema); pool.setMaximumPoolSize(4); pool.setMinimumIdle(0); pool.setConnectionTimeout(5000);
        jdbc = new JdbcTemplate(pool);
    }
    @AfterAll static void cleanup() {
        if (pool != null) pool.close();
        if (admin != null && schema != null) {
            assertThat(schema).matches("r260_ledger_[0-9a-f]{32}"); admin.execute("DROP SCHEMA " + schema + " CASCADE");
        }
        if (postgres != null) postgres.stop();
    }
    @BeforeEach void reset() {
        ledger = new OutboundTransportDeliveryLedgerService(jdbc);
        jdbc.execute("TRUNCATE integration_outbound_event_deliveries");
    }
    private boolean claim(OutboundTransportDeliveryLedgerService service) {
        return service.beginDelivery("r260-event", "feedback.prompt.dispatch", "isolated.route", 9007199254740995L,
            9007199254740993L, " padded/Ю%?' ", 100L);
    }
    @RepeatedTest(5) void concurrentRetriesHaveOnlyOneClaimantForTheSameObservedFailure() throws Exception {
        assertThat(claim(ledger)).isTrue(); ledger.markFailed("r260-event", new IllegalStateException("before provider"));
        var barrier = new CyclicBarrier(2);
        JdbcTemplate competing = new JdbcTemplate(pool) {
            @Override public <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) {
                List<T> snapshot = super.query(sql, mapper, args);
                if (sql.contains("SELECT status, updated_at") && sql.contains("integration_outbound_event_deliveries")) {
                    try { barrier.await(5,TimeUnit.SECONDS); }
                    catch (Exception failure) { throw new IllegalStateException("Claimants did not read the same generation", failure); }
                }
                return snapshot;
            }
        };
        var first = new OutboundTransportDeliveryLedgerService(competing);
        var second = new OutboundTransportDeliveryLedgerService(competing);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var a = workers.submit(() -> claim(first)); var b = workers.submit(() -> claim(second));
            assertThat(List.of(a.get(10,TimeUnit.SECONDS), b.get(10,TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(true,false);
        } finally { workers.shutdownNow(); }
        assertThat(jdbc.queryForObject("SELECT ticket_id FROM integration_outbound_event_deliveries",String.class)).isEqualTo(" padded/Ю%?' ");
    }
    @ParameterizedTest @ValueSource(strings={"processing","sending","uncertain","failed","delivered","unknown-state"})
    void staleOrUnprovenStateIsNeverAutomaticallyReclaimed(String status) {
        assertThat(claim(ledger)).isTrue();
        jdbc.update("UPDATE integration_outbound_event_deliveries SET status=?,updated_at=clock_timestamp()-interval '1 hour'",status);
        assertThat(claim(ledger)).isFalse();
        assertThat(jdbc.queryForObject("SELECT status FROM integration_outbound_event_deliveries",String.class)).isEqualTo(status);
    }
    @Test void lateFailureCannotReopenDeliveredEvent() {
        assertThat(claim(ledger)).isTrue(); jdbc.update("UPDATE integration_outbound_event_deliveries SET status='delivered'");
        ledger.markFailed("r260-event",new IllegalStateException("late failure"));
        assertThat(jdbc.queryForObject("SELECT status FROM integration_outbound_event_deliveries",String.class)).isEqualTo("delivered");
        assertThat(claim(ledger)).isFalse();
    }
}
