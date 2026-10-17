package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.example.supportbot.entity.Channel;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class UiEventOutboxScopePostgresTest {
    private static final String RAW_ID = " event/Ю?% ";
    private static final String PUBLIC_ID = "a".repeat(32);
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
        DockerImageName.parse("postgres:16-alpine"));
    static { POSTGRES.start(); }
    private JdbcTemplate jdbc;
    private UiEventOutboxService writer;
    private Channel channel;

    @BeforeEach
    void setUp() {
        String schema = "ui_outbox_" + UUID.randomUUID().toString().replace("-", "");
        var admin = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        admin.execute("CREATE SCHEMA " + schema);
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
            POSTGRES.getJdbcUrl() + "&currentSchema=" + schema, POSTGRES.getUsername(), POSTGRES.getPassword()));
        jdbc.execute("CREATE TABLE tickets(user_id BIGINT, ticket_id TEXT, ticket_public_id TEXT, "
            + "business_id BIGINT, is_general BOOLEAN, PRIMARY KEY(user_id, ticket_id))");
        jdbc.execute("""
            CREATE TABLE ui_event_outbox(id BIGINT PRIMARY KEY, event_type TEXT NOT NULL, ticket_id TEXT NOT NULL,
                channel_id BIGINT, message_text TEXT, message_type TEXT, attachment TEXT, rating INTEGER,
                created_at TIMESTAMPTZ NOT NULL, ticket_user_id BIGINT, ticket_public_id TEXT,
                ticket_scope_kind TEXT, ticket_business_id BIGINT)
            """);
        writer = new UiEventOutboxService(jdbc);
        channel = new Channel();
        channel.setId(41L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"created", "message", "edited", "feedback", "closed", "auto-closed", "reopened"})
    void everyBotPublisherWritesExactCanonicalOwnerInTheSameInsert(String publisher) {
        jdbc.update("INSERT INTO tickets VALUES (7, ?, ?, 10, FALSE), (8, ?, ?, 20, FALSE)",
            RAW_ID, PUBLIC_ID, RAW_ID.trim(), "b".repeat(32));

        publish(publisher);

        var row = jdbc.queryForMap("SELECT * FROM ui_event_outbox");
        assertThat(row.get("ticket_id")).isEqualTo(RAW_ID);
        assertThat(row.get("ticket_user_id")).isEqualTo(7L);
        assertThat(row.get("ticket_public_id")).isEqualTo(PUBLIC_ID);
        assertThat(row.get("ticket_scope_kind")).isEqualTo("BUSINESS");
        assertThat(row.get("ticket_business_id")).isEqualTo(10L);
        assertThat(row.get("channel_id")).isEqualTo(41L);
        assertThat(row.get("created_at")).isNotNull();
        if ("message".equals(publisher)) {
            assertThat(row.get("message_text")).isEqualTo("message");
            assertThat(row.get("message_type")).isEqualTo("photo");
            assertThat(row.get("attachment")).isEqualTo("storage/key");
        }
        if ("feedback".equals(publisher)) assertThat(row.get("rating")).isEqualTo(5);
        jdbc.update("UPDATE tickets SET business_id = 20 WHERE user_id = 7");
        assertThat(jdbc.queryForObject("SELECT ticket_business_id FROM ui_event_outbox", Long.class)).isEqualTo(10L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GENERAL", "UNRESOLVED", "NULL_LOCATOR"})
    void explicitGeneralAndUnresolvedRemainSeparateAndNeverReceiveGuessedLocator(String kind) {
        jdbc.update("INSERT INTO tickets VALUES (7, ?, ?, NULL, ?)", RAW_ID,
            "NULL_LOCATOR".equals(kind) ? null : PUBLIC_ID, "GENERAL".equals(kind));
        writer.publishTicketCreated(RAW_ID, channel, "preview");
        var row = jdbc.queryForMap("SELECT * FROM ui_event_outbox");
        assertThat(row.get("ticket_scope_kind")).isEqualTo("GENERAL".equals(kind) ? "GENERAL" : "UNRESOLVED");
        assertThat(row.get("ticket_business_id")).isNull();
        assertThat(row.get("ticket_public_id")).isEqualTo("NULL_LOCATOR".equals(kind) ? null : PUBLIC_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "duplicate", "unknown-marker", "missing-scope-schema"})
    void invalidIdentityOrSchemaDoesNotCreateAnEventOrBreakTheMainFlow(String condition) {
        switch (condition) {
            case "duplicate" -> jdbc.update("INSERT INTO tickets VALUES (7, ?, ?, 10, FALSE), (8, ?, ?, NULL, TRUE)",
                RAW_ID, PUBLIC_ID, RAW_ID, "b".repeat(32));
            case "unknown-marker" -> jdbc.update("INSERT INTO tickets VALUES (7, ?, ?, NULL, NULL)", RAW_ID, PUBLIC_ID);
            case "missing-scope-schema" -> jdbc.execute("ALTER TABLE ui_event_outbox DROP COLUMN ticket_scope_kind");
            default -> { }
        }
        assertDoesNotThrow(() -> writer.publishTicketCreated(RAW_ID, channel, "private preview"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ui_event_outbox", Long.class)).isZero();
    }

    @Test
    void missingOwnerSchemaStillKeepsTheExistingBestEffortContract() {
        jdbc.execute("DROP TABLE tickets");
        assertDoesNotThrow(() -> writer.publishClientMessage(RAW_ID, channel, "message", "text", null));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ui_event_outbox", Long.class)).isZero();
    }

    private void publish(String publisher) {
        switch (publisher) {
            case "created" -> writer.publishTicketCreated(RAW_ID, channel, " preview ");
            case "message" -> writer.publishClientMessage(RAW_ID, channel, " message ", " photo ", " storage/key ");
            case "edited" -> writer.publishClientMessageEdited(RAW_ID, 41L, " edited ");
            case "feedback" -> writer.publishFeedbackCreated(RAW_ID, channel, 5);
            case "closed" -> writer.publishTicketClosed(RAW_ID, channel, " closed ", false);
            case "auto-closed" -> writer.publishTicketClosed(RAW_ID, channel, " auto ", true);
            case "reopened" -> writer.publishTicketReopened(RAW_ID, channel, " reopened ");
            default -> throw new IllegalArgumentException(publisher);
        }
    }
}
