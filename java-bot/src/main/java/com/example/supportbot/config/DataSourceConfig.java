package com.example.supportbot.config;

import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.util.StringUtils;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource dataSource(ConfigurableEnvironment environment) {
        String rawDatabaseUrl = environment.getProperty("DATABASE_URL", "");
        if (StringUtils.hasText(rawDatabaseUrl)) {
            DatabaseCredentials credentials = normalizePostgresUrl(rawDatabaseUrl);
            registerRuntimeProperty(environment, "spring.jpa.database-platform", "org.hibernate.dialect.PostgreSQLDialect");
            registerRuntimeProperty(environment, "spring.sql.init.platform", "postgres");

            DataSourceBuilder<?> builder = DataSourceBuilder.create();
            builder.driverClassName("org.postgresql.Driver");
            builder.url(credentials.jdbcUrl());
            builder.username(credentials.username());
            builder.password(credentials.password());
            return builder.build();
        }

        String configuredPath = environment.getProperty("support-bot.database.path", "");
        Path normalized = resolveSqlitePath(configuredPath);
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + normalized);

        registerRuntimeProperty(environment, "spring.jpa.database-platform", "org.hibernate.community.dialect.SQLiteDialect");
        registerRuntimeProperty(environment, "spring.sql.init.platform", "sqlite");
        return dataSource;
    }

    private static void registerRuntimeProperty(ConfigurableEnvironment env, String key, String value) {
        if (StringUtils.hasText(env.getProperty(key))) {
            return;
        }
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

    private static Path resolveSqlitePath(String configured) {
        if (StringUtils.hasText(configured)) {
            return normalizeAndEnsureParent(Paths.get(configured));
        }

        Path cwd = Paths.get("").toAbsolutePath().normalize();
        Path existing = findExistingSibling(cwd, "tickets.db");
        if (existing != null) {
            return existing;
        }

        return normalizeAndEnsureParent(cwd.resolve("tickets.db"));
    }

    private static Path normalizeAndEnsureParent(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (normalized.getParent() != null && !Files.exists(normalized.getParent())) {
            normalized.getParent().toFile().mkdirs();
        }
        return normalized;
    }

    private static Path findExistingSibling(Path start, String fileName) {
        Path current = start;
        for (int depth = 0; depth < 4 && current != null; depth++) {
            Path candidate = current.resolve(fileName).normalize();
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return null;
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
