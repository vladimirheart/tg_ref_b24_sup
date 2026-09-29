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
        String configuredMax = environment.getProperty("APP_DB_MAX_POOL_SIZE");
        if (!StringUtils.hasText(configuredMax)) {
            return;
        }
        try {
            int maxPoolSize = Integer.parseInt(configuredMax.trim());
            if (maxPoolSize > 0) {
                hikari.setMaximumPoolSize(maxPoolSize);
            }
        } catch (NumberFormatException ignored) {
            // Keep Hikari's default when an operator provides an invalid optional limit.
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
