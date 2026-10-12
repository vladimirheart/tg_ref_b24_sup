package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class OutboundTransportDeliveryLedgerServiceTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
        DockerImageName.parse("postgres:16-alpine")
    ).withDatabaseName("iguana_test").withUsername("iguana").withPassword("iguana");

    static {
        POSTGRES.start();
    }

    private JdbcTemplate jdbcTemplate;
    private OutboundTransportDeliveryLedgerService service;

    @BeforeEach
    void setUp() {
        String schema = "bot_delivery_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        JdbcTemplate admin = new JdbcTemplate(new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()
        ));
        admin.execute("CREATE SCHEMA " + schema);
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
            POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema + "&stringtype=unspecified",
            POSTGRES.getUsername(), POSTGRES.getPassword()
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
