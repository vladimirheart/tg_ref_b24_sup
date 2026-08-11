package com.example.panel.config;

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
            environment.getProperty("app.datasource.mode"),
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
        Optional<ExternalDatabaseSettings> explicitSettings = fromSpringDatasource(
            springDatasourceUrl,
            springDatasourceUsername,
            springDatasourcePassword,
            springDatasourceDriver
        );
        Optional<ExternalDatabaseSettings> databaseUrlSettings = fromDatabaseUrl(databaseUrl);
        Optional<ExternalDatabaseSettings> resolved = explicitSettings.isPresent() ? explicitSettings : databaseUrlSettings;

        return switch (requestedMode) {
            case SQLITE -> Optional.empty();
            case AUTO -> resolved;
            case POSTGRESQL -> Optional.of(requireVendor(resolved, DatabaseMode.POSTGRESQL, "postgresql"));
            case MYSQL -> Optional.of(requireVendor(resolved, DatabaseMode.MYSQL, "mysql"));
        };
    }

    private static Optional<ExternalDatabaseSettings> fromSpringDatasource(String jdbcUrl,
                                                                           String username,
                                                                           String password,
                                                                           String driverClassName) {
        if (!StringUtils.hasText(jdbcUrl)) {
            return Optional.empty();
        }
        DatabaseMode vendor = detectVendorFromJdbcUrl(jdbcUrl);
        if (vendor == null || vendor == DatabaseMode.SQLITE) {
            return Optional.empty();
        }
        return Optional.of(new ExternalDatabaseSettings(
            jdbcUrl,
            defaultString(username),
            defaultString(password),
            StringUtils.hasText(driverClassName) ? driverClassName : defaultDriverClassName(vendor),
            defaultHibernateDialect(vendor),
            defaultFlywayLocation(vendor),
            vendor
        ));
    }

    private static Optional<ExternalDatabaseSettings> fromDatabaseUrl(String rawDatabaseUrl) {
        if (!StringUtils.hasText(rawDatabaseUrl)) {
            return Optional.empty();
        }
        if (rawDatabaseUrl.startsWith("jdbc:")) {
            DatabaseMode vendor = detectVendorFromJdbcUrl(rawDatabaseUrl);
            if (vendor == null || vendor == DatabaseMode.SQLITE) {
                return Optional.empty();
            }
            return Optional.of(new ExternalDatabaseSettings(
                rawDatabaseUrl,
                "",
                "",
                defaultDriverClassName(vendor),
                defaultHibernateDialect(vendor),
                defaultFlywayLocation(vendor),
                vendor
            ));
        }

        String normalized = rawDatabaseUrl;
        if (rawDatabaseUrl.startsWith("postgres://")) {
            normalized = rawDatabaseUrl.replaceFirst("postgres://", "postgresql://");
        }
        if (!normalized.startsWith("postgresql://")) {
            throw new IllegalArgumentException(
                "Invalid DATABASE_URL format. Use a JDBC URL or a PostgreSQL URI like postgres://user:pass@host:5432/db."
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
                defaultDriverClassName(DatabaseMode.POSTGRESQL),
                defaultHibernateDialect(DatabaseMode.POSTGRESQL),
                defaultFlywayLocation(DatabaseMode.POSTGRESQL),
                DatabaseMode.POSTGRESQL
            ));
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Invalid DATABASE_URL format", ex);
        }
    }

    private static ExternalDatabaseSettings requireVendor(Optional<ExternalDatabaseSettings> settings,
                                                          DatabaseMode expectedVendor,
                                                          String expectedName) {
        ExternalDatabaseSettings resolved = settings.orElseThrow(() -> new IllegalStateException(
            "External database mode '" + expectedName + "' requires spring.datasource.url or DATABASE_URL."
        ));
        if (resolved.vendor() != expectedVendor) {
            throw new IllegalStateException(
                "External database mode '" + expectedName + "' is incompatible with configured vendor '" +
                    resolved.vendor().name().toLowerCase() + "'."
            );
        }
        return resolved;
    }

    private static DatabaseMode detectVendorFromJdbcUrl(String jdbcUrl) {
        if (!StringUtils.hasText(jdbcUrl)) {
            return null;
        }
        String normalized = jdbcUrl.trim().toLowerCase();
        if (normalized.startsWith("jdbc:postgresql:")) {
            return DatabaseMode.POSTGRESQL;
        }
        if (normalized.startsWith("jdbc:mysql:")) {
            return DatabaseMode.MYSQL;
        }
        if (normalized.startsWith("jdbc:sqlite:")) {
            return DatabaseMode.SQLITE;
        }
        return null;
    }

    private static String defaultDriverClassName(DatabaseMode vendor) {
        return switch (vendor) {
            case POSTGRESQL -> "org.postgresql.Driver";
            case MYSQL -> "com.mysql.cj.jdbc.Driver";
            default -> "";
        };
    }

    private static String defaultHibernateDialect(DatabaseMode vendor) {
        return switch (vendor) {
            case POSTGRESQL -> "org.hibernate.dialect.PostgreSQLDialect";
            case MYSQL -> "org.hibernate.dialect.MySQLDialect";
            default -> "";
        };
    }

    private static String defaultFlywayLocation(DatabaseMode vendor) {
        return switch (vendor) {
            case POSTGRESQL -> "classpath:db/migration/postgresql";
            case MYSQL -> "classpath:db/migration/mysql";
            default -> "";
        };
    }

    private static String defaultString(String value) {
        return value == null ? "" : value;
    }
}
