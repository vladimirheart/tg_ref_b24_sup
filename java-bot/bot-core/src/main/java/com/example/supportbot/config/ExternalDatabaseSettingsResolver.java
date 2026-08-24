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
        DatabaseMode requestedMode = DatabaseMode.from(modeValue);
        if (requestedMode == DatabaseMode.SQLITE || requestedMode == DatabaseMode.WORKER) {
            // Local compatibility/worker modes must ignore inherited canonical datasource credentials entirely.
            return Optional.empty();
        }

        Optional<ExternalDatabaseSettings> explicitSettings = fromSpringDatasource(
            springDatasourceUrl,
            springDatasourceUsername,
            springDatasourcePassword,
            springDatasourceDriver
        );
        Optional<ExternalDatabaseSettings> databaseUrlSettings = fromDatabaseUrl(databaseUrl);
        Optional<ExternalDatabaseSettings> resolved = explicitSettings.isPresent() ? explicitSettings : databaseUrlSettings;

        return switch (requestedMode) {
            case AUTO -> resolved;
            case POSTGRESQL -> Optional.of(requirePostgresql(resolved));
            case SQLITE, WORKER -> Optional.empty();
        };
    }

    private static Optional<ExternalDatabaseSettings> fromSpringDatasource(String jdbcUrl,
                                                                           String username,
                                                                           String password,
                                                                           String driverClassName) {
        if (!StringUtils.hasText(jdbcUrl)) {
            return Optional.empty();
        }
        if (!jdbcUrl.trim().toLowerCase().startsWith("jdbc:postgresql:")) {
            throw new IllegalStateException("support-bot external database mode supports only PostgreSQL JDBC URLs.");
        }
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
            if (!rawDatabaseUrl.trim().toLowerCase().startsWith("jdbc:postgresql:")) {
                throw new IllegalStateException("support-bot external DATABASE_URL supports only PostgreSQL.");
            }
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
                "Invalid DATABASE_URL format. Use a PostgreSQL JDBC URL or postgres://user:pass@host:5432/db."
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
            throw new IllegalArgumentException("Invalid DATABASE_URL format", ex);
        }
    }

    private static ExternalDatabaseSettings requirePostgresql(Optional<ExternalDatabaseSettings> settings) {
        return settings.orElseThrow(() -> new IllegalStateException(
            "support-bot external PostgreSQL mode requires spring.datasource.url or DATABASE_URL."
        ));
    }

    private static String defaultString(String value) {
        return value == null ? "" : value;
    }
}
