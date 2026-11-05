package com.example.panel.support;

import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public final class PostgresqlJdbcTestSupport {
    private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:16-alpine");
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE)
            .withDatabaseName("iguana_test")
            .withUsername("iguana")
            .withPassword("iguana");

    private static final String NATIVE_URL = System.getProperty("r290.edit.jdbc");

    static {
        if (NATIVE_URL == null) {
            POSTGRES.start();
        } else {
            String nonce = System.getProperty("r290.edit.nonce", "");
            if (!NATIVE_URL.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/r290_edit")
                    || !nonce.matches("[a-f0-9]{32}")) {
                throw new IllegalArgumentException("Only the nonce-owned native test database is accepted");
            }
            int port = java.net.URI.create(NATIVE_URL.substring(5)).getPort();
            if (port <= 1024 || port == 5432 || port > 65535) throw new IllegalArgumentException("Disposable port required");
            JdbcTemplate admin = new JdbcTemplate(new DriverManagerDataSource(NATIVE_URL, "r290_test", ""));
            if (!nonce.equals(admin.queryForObject("SELECT nonce FROM r290_native_owner", String.class))) {
                throw new IllegalStateException("Native test cluster ownership mismatch");
            }
        }
    }

    private PostgresqlJdbcTestSupport() {
    }

    public static JdbcTemplate freshJdbcTemplate(String prefix) {
        return freshJdbcTemplate(prefix, true);
    }

    public static JdbcTemplate freshJdbcTemplateDefaultStringType(String prefix) {
        return freshJdbcTemplate(prefix, false);
    }

    private static JdbcTemplate freshJdbcTemplate(String prefix, boolean unspecifiedStringType) {
        String schema = schemaName(prefix);
        JdbcTemplate admin = new JdbcTemplate(dataSource(jdbcUrl()));
        admin.execute("CREATE SCHEMA " + schema);

        String separator = jdbcUrl().contains("?") ? "&" : "?";
        String url = jdbcUrl() + separator + "currentSchema=" + schema;
        if (unspecifiedStringType) {
            url += "&stringtype=unspecified";
        }
        return new JdbcTemplate(dataSource(url));
    }

    private static DriverManagerDataSource dataSource(String url) {
        return NATIVE_URL == null
            ? new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
            : new DriverManagerDataSource(url, "r290_test", "");
    }

    private static String jdbcUrl() {
        return NATIVE_URL == null ? POSTGRES.getJdbcUrl() : NATIVE_URL;
    }

    private static String schemaName(String prefix) {
        String normalized = String.valueOf(prefix)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]", "_");
        if (normalized.isBlank()) {
            normalized = "test";
        }
        if (normalized.length() > 40) {
            normalized = normalized.substring(0, 40);
        }
        return normalized + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
