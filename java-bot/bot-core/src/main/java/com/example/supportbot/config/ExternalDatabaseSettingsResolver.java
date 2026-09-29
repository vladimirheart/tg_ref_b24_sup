package com.example.supportbot.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

final class ExternalDatabaseSettingsResolver {

    private ExternalDatabaseSettingsResolver() {
    }

    static Optional<ExternalDatabaseSettings> resolve(Environment environment) {
        return resolve(
            environment.getProperty("support-bot.database.mode"),
            environment.getProperty("spring.datasource.url"),
            environment.getProperty("spring.datasource.username"),
            environment.getProperty("spring.datasource.password"),
            environment.getProperty("spring.datasource.driver-class-name"),
            environment.getProperty("DATABASE_URL")
        );
    }

    static Optional<ExternalDatabaseSettings> resolve(String modeValue,
                                                      String springDatasourceUrl,
                                                      String springDatasourceUsername,
                                                      String springDatasourcePassword,
                                                      String springDatasourceDriver,
                                                      String databaseUrl) {
        DatabaseMode.from(modeValue);
        Optional<ExternalDatabaseSettings> explicitSettings = fromSpringDatasource(
            springDatasourceUrl,
            springDatasourceUsername,
            springDatasourcePassword,
            springDatasourceDriver
        );
        return explicitSettings.isPresent() ? explicitSettings : fromDatabaseUrl(databaseUrl);
    }

    private static Optional<ExternalDatabaseSettings> fromSpringDatasource(String jdbcUrl,
                                                                           String username,
                                                                           String password,
                                                                           String driverClassName) {
        if (!StringUtils.hasText(jdbcUrl)) {
            return Optional.empty();
        }
        requirePostgresqlJdbcUrl(jdbcUrl, "support-bot datasource");
        return Optional.of(new ExternalDatabaseSettings(
            jdbcUrl,
            defaultString(username),
            defaultString(password),
            StringUtils.hasText(driverClassName) ? driverClassName : "org.postgresql.Driver",
            "org.hibernate.dialect.PostgreSQLDialect",
            "postgres"
        ));
    }

    private static Optional<ExternalDatabaseSettings> fromDatabaseUrl(String rawDatabaseUrl) {
        if (!StringUtils.hasText(rawDatabaseUrl)) {
            return Optional.empty();
        }
        if (rawDatabaseUrl.startsWith("jdbc:")) {
            requirePostgresqlJdbcUrl(rawDatabaseUrl, "support-bot DATABASE_URL");
            return Optional.of(new ExternalDatabaseSettings(
                rawDatabaseUrl,
                "",
                "",
                "org.postgresql.Driver",
                "org.hibernate.dialect.PostgreSQLDialect",
                "postgres"
            ));
        }

        String normalized = rawDatabaseUrl;
        if (rawDatabaseUrl.startsWith("postgres://")) {
            normalized = rawDatabaseUrl.replaceFirst("postgres://", "postgresql://");
        }
        if (!normalized.startsWith("postgresql://")) {
            throw new IllegalArgumentException(
                "Invalid DATABASE_URL format. Only PostgreSQL JDBC URLs or postgres:// URIs are supported."
            );
        }
        try {
            URI uri = new URI(normalized);
            String userInfo = uri.getUserInfo();
            String username = "";
            String password = "";
            if (userInfo != null) {
                String[] parts = userInfo.split(":", 2);
                username = parts[0];
                if (parts.length > 1) {
                    password = parts[1];
                }
            }
            StringBuilder jdbc = new StringBuilder("jdbc:postgresql://");
            if (uri.getHost() != null) {
                jdbc.append(uri.getHost());
            }
            if (uri.getPort() > 0) {
                jdbc.append(':').append(uri.getPort());
            }
            if (uri.getPath() != null) {
                jdbc.append(uri.getPath());
            }
            if (StringUtils.hasText(uri.getQuery())) {
                jdbc.append('?').append(uri.getQuery());
            }
            return Optional.of(new ExternalDatabaseSettings(
                jdbc.toString(),
                username,
                password,
                "org.postgresql.Driver",
                "org.hibernate.dialect.PostgreSQLDialect",
                "postgres"
            ));
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Invalid PostgreSQL DATABASE_URL format", ex);
        }
    }

    private static void requirePostgresqlJdbcUrl(String jdbcUrl, String source) {
        if (!jdbcUrl.trim().toLowerCase().startsWith("jdbc:postgresql:")) {
            throw new IllegalStateException(source + " supports only PostgreSQL JDBC URLs.");
        }
    }

    private static String defaultString(String value) {
        return value == null ? "" : value;
    }
}
