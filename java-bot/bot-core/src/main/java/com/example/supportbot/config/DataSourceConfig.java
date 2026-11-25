package com.example.supportbot.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource dataSource(ConfigurableEnvironment environment) {
        DatabaseMode.from(environment.getProperty("support-bot.database.mode"));
        ExternalDatabaseSettings settings = ExternalDatabaseSettingsResolver.resolve(environment)
            .orElseThrow(() -> new IllegalStateException(
                "java-bot requires PostgreSQL. Configure spring.datasource.url or DATABASE_URL."
            ));

        applyExternalRuntimeProperties(environment, settings);

        DataSourceBuilder<?> builder = DataSourceBuilder.create();
        if (StringUtils.hasText(settings.driverClassName())) {
            builder.driverClassName(settings.driverClassName());
        }
        builder.url(settings.jdbcUrl());
        if (StringUtils.hasText(settings.username())) {
            builder.username(settings.username());
        }
        if (StringUtils.hasText(settings.password())) {
            builder.password(settings.password());
        }
        DataSource dataSource = builder.build();
        configureExternalHikari(dataSource, environment);
        ChildTransportDatabase.validate(dataSource, environment);
        return dataSource;
    }

    static void applyExternalRuntimeProperties(ConfigurableEnvironment environment, ExternalDatabaseSettings settings) {
        registerRuntimePropertyOverride(environment, "spring.jpa.database-platform", settings.hibernateDialect());
        registerRuntimePropertyOverride(environment, "spring.jpa.hibernate.ddl-auto", "none");
        registerRuntimePropertyOverride(environment, "spring.sql.init.mode", "never");
    }

    private static void configureExternalHikari(DataSource dataSource, ConfigurableEnvironment environment) {
        if (!(dataSource instanceof HikariDataSource hikari)) {
            return;
        }
        int maximum = readPoolSetting(environment, "APP_DB_MAX_POOL_SIZE", 10);
        int minimum = readPoolSetting(environment, "APP_DB_MIN_IDLE", Math.min(5, maximum));
        int idleTimeout = readPoolSetting(environment, "APP_DB_IDLE_TIMEOUT_MS", 120_000);
        if (maximum < 1 || minimum < 0 || minimum > maximum
                || (idleTimeout != 0 && idleTimeout < 10_000)) {
            throw new IllegalArgumentException("Invalid bot JDBC pool: require maximum >= 1, "
                + "0 <= minimumIdle <= maximum, idleTimeout = 0 or >= 10000ms");
        }
        hikari.setMaximumPoolSize(maximum);
        hikari.setMinimumIdle(minimum);
        hikari.setIdleTimeout(idleTimeout);
        org.slf4j.LoggerFactory.getLogger(DataSourceConfig.class).info(
            "Bot Hikari pool configured: maximumPoolSize={}, minimumIdle={}, idleTimeoutMs={}",
            maximum, minimum, idleTimeout);
    }

    private static int readPoolSetting(ConfigurableEnvironment environment, String key, int fallback) {
        String raw = environment.getProperty(key);
        if (!StringUtils.hasText(raw)) return fallback;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(key + " must be an integer", invalid);
        }
    }

    private static void registerRuntimePropertyOverride(ConfigurableEnvironment env, String key, String value) {
        MutablePropertySources propertySources = env.getPropertySources();
        PropertySource<?> existing = propertySources.get("runtime-properties");
        Map<String, Object> map;
        if (existing instanceof MapPropertySource mapSource) {
            map = new HashMap<>(mapSource.getSource());
            propertySources.remove("runtime-properties");
        } else {
            map = new HashMap<>();
        }
        map.put(key, value);
        propertySources.addFirst(new MapPropertySource("runtime-properties", map));
    }
}
