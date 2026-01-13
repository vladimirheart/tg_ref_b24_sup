package com.example.panel.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableConfigurationProperties({
    SqliteDataSourceProperties.class,
    ClientsSqliteDataSourceProperties.class,
    KnowledgeSqliteDataSourceProperties.class,
    ObjectsSqliteDataSourceProperties.class,
    SettingsSqliteDataSourceProperties.class,
    BotProcessProperties.class
})
    public class SqliteDataSourceConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SqliteDataSourceConfiguration.class);

    @Bean
    @Primary
    public DataSource dataSource(SqliteDataSourceProperties properties, ConfigurableEnvironment environment) {
        String rawDatabaseUrl = environment.getProperty("DATABASE_URL", "");
        if (StringUtils.hasText(rawDatabaseUrl)) {
            DatabaseCredentials credentials = normalizePostgresUrl(rawDatabaseUrl);
            registerRuntimeProperty(environment, "spring.jpa.database-platform", "org.hibernate.dialect.PostgreSQLDialect");
            registerRuntimeProperty(environment, "spring.sql.init.mode", "never");

            DataSourceBuilder<?> builder = DataSourceBuilder.create();
            builder.driverClassName("org.postgresql.Driver");
            builder.url(credentials.jdbcUrl());
            builder.username(credentials.username());
            builder.password(credentials.password());
            return builder.build();
        }

        Path normalized = properties.getNormalizedPath();
        log.info("Using SQLite database at {}", normalized);

        SQLiteConfig config = new SQLiteConfig();
        // Existing databases store timestamps without timezone info (e.g. "2025-12-03 15:04:53.370"),
        // so align the driver format accordingly to avoid Flyway parsing errors during startup.
        config.setDateStringFormat("yyyy-MM-dd HH:mm:ss.SSS");

        SQLiteDataSource dataSource = new SQLiteDataSource(config);
        dataSource.setUrl("jdbc:sqlite:" + normalized);
        registerRuntimeProperty(environment, "spring.jpa.database-platform", "org.hibernate.community.dialect.SQLiteDialect");
        registerRuntimeProperty(environment, "spring.sql.init.mode", "never");
        return dataSource;
    }

    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    private static void registerRuntimeProperty(ConfigurableEnvironment env, String key, String value) {
        MutablePropertySources propertySources = env.getPropertySources();
        PropertySource<?> existing = propertySources.get("runtime-properties");
        Map<String, Object> map;
        if (existing instanceof MapPropertySource mapSource) {
            map = new HashMap<>(mapSource.getSource());
            propertySources.remove("runtime-properties");
        } else {
            map = new HashMap<>();
        }
        map.putIfAbsent(key, value);
        propertySources.addFirst(new MapPropertySource("runtime-properties", map));
    }

    private static DatabaseCredentials normalizePostgresUrl(String rawUrl) {
        if (rawUrl.startsWith("jdbc:")) {
            return new DatabaseCredentials(rawUrl, "", "");
        }
        String normalized = rawUrl;
        if (rawUrl.startsWith("postgres://")) {
            normalized = rawUrl.replaceFirst("postgres://", "postgresql://");
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
            String host = uri.getHost();
            int port = uri.getPort();
            String path = uri.getPath();
            String query = uri.getQuery();
            StringBuilder jdbc = new StringBuilder("jdbc:postgresql://");
            jdbc.append(host != null ? host : "");
            if (port > 0) {
                jdbc.append(':').append(port);
            }
            if (path != null) {
                jdbc.append(path);
            }
            if (StringUtils.hasText(query)) {
                jdbc.append('?').append(query);
            }
            return new DatabaseCredentials(jdbc.toString(), username, password);
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Invalid DATABASE_URL format", ex);
        }
    }

    private record DatabaseCredentials(String jdbcUrl, String username, String password) {
    }
}

