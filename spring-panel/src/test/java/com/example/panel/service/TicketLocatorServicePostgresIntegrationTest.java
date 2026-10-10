package com.example.panel.service;

import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketLocatorServicePostgresIntegrationTest {

    private static final Path FOUNDATION_MIGRATION = Path.of(
            "src/main/resources/db/migration/postgresql/V51__multibusiness_foundation.sql"
    );
    private static final Path LOCATOR_MIGRATION = Path.of(
            "src/main/resources/db/migration/postgresql/V52__ticket_public_locator.sql"
    );

    @Test
    void resolvesOnlyPublicIdOrExplicitCompositeKeyAndKeepsLegacyTicketUnassigned() throws Exception {
        JdbcTemplate jdbc = freshSchema("ticket_locator_v52");
        jdbc.update("INSERT INTO users(id, username) VALUES (1, 'first'), (2, 'second')");
        jdbc.update("INSERT INTO tickets(user_id, ticket_id) VALUES (1, 'same-ticket'), (2, 'same-ticket')");
        applyFoundationMigration(jdbc);
        applyLocatorMigration(jdbc);

        String publicId = "0123456789abcdef0123456789abcdef";
        jdbc.update(
                "UPDATE tickets SET ticket_public_id = ? WHERE user_id = 1 AND ticket_id = 'same-ticket'",
                publicId
        );

        TicketLocatorService service = new TicketLocatorService(jdbc);

        assertThat(service.findByPublicId(publicId)).hasValue(
                new TicketLocatorService.TicketReference(1L, "same-ticket", publicId, null)
        );
        assertThat(service.findByCompositeKey(2L, "same-ticket")).hasValue(
                new TicketLocatorService.TicketReference(2L, "same-ticket", null, null)
        );
        assertThat(service.findByCompositeKey(null, "same-ticket")).isEmpty();
        assertThat(service.findByPublicId("same-ticket")).isEmpty();
        assertThat(service.findByPublicId(publicId.toUpperCase())).isEmpty();

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE tickets SET ticket_public_id = ? WHERE user_id = 2 AND ticket_id = 'same-ticket'",
                publicId
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE tickets SET ticket_public_id = ? WHERE user_id = 1 AND ticket_id = 'same-ticket'",
                "fedcba9876543210fedcba9876543210"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private JdbcTemplate freshSchema(String prefix) {
        JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplate(prefix);
        jdbc.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, username TEXT NOT NULL UNIQUE)");
        jdbc.execute("CREATE TABLE channels (id BIGINT PRIMARY KEY)");
        jdbc.execute("""
                CREATE TABLE tickets (
                    user_id BIGINT NOT NULL,
                    ticket_id TEXT NOT NULL,
                    PRIMARY KEY (user_id, ticket_id)
                )
                """);
        return jdbc;
    }

    private void applyFoundationMigration(JdbcTemplate jdbc) throws Exception {
        String sql = Files.readString(FOUNDATION_MIGRATION).replace("\r\n", "\n");
        for (String statement : sql.split(";")) {
            String trimmed = statement.trim();
            if (!trimmed.isEmpty()) {
                jdbc.execute(trimmed);
            }
        }
    }

    private void applyLocatorMigration(JdbcTemplate jdbc) throws Exception {
        jdbc.execute(Files.readString(LOCATOR_MIGRATION).replace("\r\n", "\n"));
    }
}
