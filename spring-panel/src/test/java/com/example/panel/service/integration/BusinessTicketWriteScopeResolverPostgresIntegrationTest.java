package com.example.panel.service.integration;

import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BusinessTicketWriteScopeResolverPostgresIntegrationTest {

    private static final Path MIGRATION = Path.of(
        "src/main/resources/db/migration/postgresql/V51__multibusiness_foundation.sql");

    @Test
    void onlyExplicitOwnerOfUniqueActiveSingleBusinessChannelCanWrite() throws Exception {
        JdbcTemplate jdbc = freshSchema("ticket_write_scope_single");
        long sushi = businessId(jdbc, "sushi");
        long bliny = businessId(jdbc, "bliny");
        BusinessTicketWriteScopeResolver resolver = new BusinessTicketWriteScopeResolver(jdbc);
        jdbc.update("INSERT INTO channels(id, channel_name, platform, is_active) "
            + "VALUES (10, 'Support', 'telegram', TRUE)");

        assertRejected(resolver, 10L, sushi);
        jdbc.update("INSERT INTO business_channel_bindings(channel_id, business_id, resolution_mode) "
            + "VALUES (10, ?, 'SINGLE_BUSINESS')", sushi);
        assertThat(resolver.requireSingleBusinessBinding(10L, sushi)).isEqualTo(sushi);
        assertRejected(resolver, 10L, bliny);
        assertRejected(resolver, 10L, null);
        assertRejected(resolver, 0L, sushi);

        jdbc.update("INSERT INTO business_channel_bindings(channel_id, business_id, resolution_mode) "
            + "VALUES (10, ?, 'EXPLICIT_SELECTION')", bliny);
        assertRejected(resolver, 10L, sushi);
        jdbc.update("UPDATE business_channel_bindings SET is_active = FALSE "
            + "WHERE channel_id = 10 AND business_id = ?", bliny);
        assertThat(resolver.requireSingleBusinessBinding(10L, sushi)).isEqualTo(sushi);

        jdbc.update("UPDATE business_channel_bindings SET resolution_mode = 'VERIFIED_EXTERNAL_MAPPING' "
            + "WHERE channel_id = 10 AND business_id = ?", sushi);
        assertRejected(resolver, 10L, sushi);
        jdbc.update("UPDATE business_channel_bindings SET resolution_mode = 'SINGLE_BUSINESS' "
            + "WHERE channel_id = 10 AND business_id = ?", sushi);
        jdbc.update("UPDATE businesses SET status = 'archived' WHERE id = ?", sushi);
        assertRejected(resolver, 10L, sushi);
        jdbc.update("UPDATE businesses SET status = 'active' WHERE id = ?", sushi);
        jdbc.update("UPDATE channels SET is_active = FALSE WHERE id = 10");
        assertRejected(resolver, 10L, sushi);
    }

    private void assertRejected(BusinessTicketWriteScopeResolver resolver, Long channelId, Long businessId) {
        assertThatThrownBy(() -> resolver.requireSingleBusinessBinding(channelId, businessId))
            .isInstanceOf(IllegalStateException.class);
    }

    private JdbcTemplate freshSchema(String prefix) throws Exception {
        JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType(prefix);
        jdbc.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, username TEXT NOT NULL UNIQUE)");
        jdbc.execute("CREATE TABLE channels (id BIGINT PRIMARY KEY, channel_name TEXT NOT NULL DEFAULT '', "
            + "platform TEXT DEFAULT 'telegram', is_active BOOLEAN NOT NULL DEFAULT TRUE)");
        jdbc.execute("CREATE TABLE tickets (user_id BIGINT NOT NULL, ticket_id TEXT NOT NULL, "
            + "PRIMARY KEY (user_id, ticket_id))");
        for (String statement : Files.readString(MIGRATION).replace("\r\n", "\n").split(";")) {
            if (!statement.isBlank()) {
                jdbc.execute(statement.trim());
            }
        }
        return jdbc;
    }

    private long businessId(JdbcTemplate jdbc, String code) {
        return jdbc.queryForObject("SELECT id FROM businesses WHERE code = ?", Long.class, code);
    }
}
