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

    static {
        POSTGRES.start();
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
        JdbcTemplate admin = new JdbcTemplate(dataSource(POSTGRES.getJdbcUrl()));
        admin.execute("CREATE SCHEMA " + schema);

        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        String url = POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema;
        if (unspecifiedStringType) {
            url += "&stringtype=unspecified";
        }
        return new JdbcTemplate(dataSource(url));
    }

    private static DriverManagerDataSource dataSource(String url) {
        return new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
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
