package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class OutboundTransportDeliveryLedgerServiceTest {

    private static PostgreSQLContainer<?> postgres;
    private static final String TEST_URL = testUrl();
    private static String testUrl() {
        String url=System.getenv("FEEDBACK_CHAIN_PG_JDBC_URL");
        if(url!=null){assertThat(url).matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/r259_chain");return url;}
        postgres=new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("r259_chain").withUsername("r259_test").withPassword("r259-test-only")
            .withCreateContainerCmdModifier(cmd->cmd.getHostConfig().withMemory(192L*1024*1024).withNanoCPUs(1_000_000_000L));
        postgres.start();return postgres.getJdbcUrl();
    }
    @AfterAll static void cleanup(){if(postgres!=null)postgres.stop();}

    private JdbcTemplate jdbcTemplate;
    private OutboundTransportDeliveryLedgerService service;

    @BeforeEach
    void setUp() {
        String schema = "bot_delivery_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        JdbcTemplate admin = new JdbcTemplate(new DriverManagerDataSource(
            TEST_URL, "r259_test", "r259-test-only"
        ));
        admin.execute("CREATE SCHEMA " + schema);
        String separator = TEST_URL.contains("?") ? "&" : "?";
        jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
            TEST_URL + separator + "currentSchema=" + schema + "&stringtype=unspecified",
            "r259_test", "r259-test-only"
        ));
        service = new OutboundTransportDeliveryLedgerService(jdbcTemplate);
    }

    @Test
    void insertKeepsPaddedAndPlainTicketIdsDistinct() {
        for (String ticketId : new String[] {"T-42", " T-42 ", "null", " null "}) {
            String eventId = UUID.randomUUID().toString();
            assertThat(service.beginDelivery(eventId, "feedback.prompt.dispatch", "route", 10L, 20L,
                ticketId, 30L)).isTrue();
            assertThat(ticketIdOf(eventId)).isEqualTo(ticketId);
        }
    }

    @Test
    void failedDeliveryRetryKeepsExactTicketIdAndDeliveredEventRemainsIdempotent() {
        String eventId = UUID.randomUUID().toString();
        assertThat(service.beginDelivery(eventId, "feedback.prompt.dispatch", "route", 10L, 20L,
            " T-42 ", 30L)).isTrue();
        service.markFailed(eventId, new IllegalStateException("transient"));
        assertThat(service.beginDelivery(eventId, "feedback.prompt.dispatch", "route", 10L, 20L,
            " null ", 30L)).isTrue();
        assertThat(ticketIdOf(eventId)).isEqualTo(" null ");
        service.markSending(eventId);
        service.markDelivered(eventId);
        assertThat(service.beginDelivery(eventId, "feedback.prompt.dispatch", "route", 10L, 20L,
            "T-42", 30L)).isFalse();
        assertThat(ticketIdOf(eventId)).isEqualTo(" null ");
    }

    @Test
    void blankOrMissingTicketIdentityIsStoredAsNull() {
        for (String ticketId : new String[] {"   ", null}) {
            String eventId = UUID.randomUUID().toString();
            assertThat(service.beginDelivery(eventId, "feedback.prompt.dispatch", "route", 10L, 20L,
                ticketId, 30L)).isTrue();
            assertThat(ticketIdOf(eventId)).isNull();
        }
    }

    private String ticketIdOf(String eventId) {
        return jdbcTemplate.queryForObject(
            "SELECT ticket_id FROM integration_outbound_event_deliveries WHERE event_id = ?",
            String.class, eventId
        );
    }
}
