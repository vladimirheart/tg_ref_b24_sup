package com.example.panel.config;

import com.zaxxer.hikari.HikariDataSource;
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
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.StringUtils;

import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Configuration
@EnableConfigurationProperties({
    SqliteDataSourceProperties.class,
    BotProcessProperties.class
})
public class SqliteDataSourceConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SqliteDataSourceConfiguration.class);
    private static final long DEFAULT_HIKARI_LEAK_DETECTION_MS = 15_000L;

    @Bean
    @Primary
    public DataSource dataSource(SqliteDataSourceProperties properties, ConfigurableEnvironment environment) {
        Optional<ExternalDatabaseSettings> externalDatabaseSettings = ExternalDatabaseSettingsResolver.resolve(environment);
        if (externalDatabaseSettings.isPresent()) {
            ExternalDatabaseSettings settings = externalDatabaseSettings.get();

            // Hibernate 6 can detect PostgreSQL/MySQL directly from JDBC metadata.
            // Supplying PostgreSQLDialect explicitly only produces a deprecation
            // warning and is unnecessary for external database mode.
            registerRuntimeProperty(environment, "spring.sql.init.mode", "never");

            log.info("Using external {} database at {}", settings.vendor().name().toLowerCase(), settings.jdbcUrl());

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
            DataSource externalDataSource = builder.build();
            configureExternalHikari(externalDataSource, environment);
            return externalDataSource;
        }

        Path normalized = properties.getNormalizedPath();
        String url = properties.buildJdbcUrl();
        log.info("Using SQLite database at {}", normalized);

        DataSource dataSource = SqliteConnectionConfigSupport.createDataSource(
            url,
            properties.getJournalMode(),
            properties.getBusyTimeoutMs()
        );
        // SQLite is not a Hibernate core dialect, so explicit community dialect
        // selection is still required for compatibility mode.
        registerRuntimeProperty(environment, "spring.jpa.database-platform", "org.hibernate.community.dialect.SQLiteDialect");
        registerRuntimeProperty(environment, "spring.sql.init.mode", "never");
        return dataSource;
    }

    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "transactionManager")
    @Primary
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    private static void configureExternalHikari(DataSource dataSource, ConfigurableEnvironment environment) {
        if (!(dataSource instanceof HikariDataSource hikari)) {
            return;
        }

        long leakDetectionMs = readLongSetting(
            environment.getProperty("APP_DB_LEAK_DETECTION_MS"),
            DEFAULT_HIKARI_LEAK_DETECTION_MS
        );
        if (leakDetectionMs > 0L && leakDetectionMs < 2_000L) {
            leakDetectionMs = 2_000L;
        }
        if (leakDetectionMs >= 2_000L) {
            hikari.setLeakDetectionThreshold(leakDetectionMs);
        }

        String maxPoolRaw = environment.getProperty("APP_DB_MAX_POOL_SIZE");
        if (StringUtils.hasText(maxPoolRaw)) {
            int configuredMax = readIntSetting(maxPoolRaw, hikari.getMaximumPoolSize());
            if (configuredMax > 0) {
                hikari.setMaximumPoolSize(configuredMax);
            }
        }

        log.info(
            "External Hikari pool configured: maxPoolSize={}, connectionTimeoutMs={}, leakDetectionMs={}",
            hikari.getMaximumPoolSize(),
            hikari.getConnectionTimeout(),
            hikari.getLeakDetectionThreshold()
        );
    }

    private static long readLongSetting(String raw, long defaultValue) {
        if (!StringUtils.hasText(raw)) {
            return defaultValue;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static int readIntSetting(String raw, int defaultValue) {
        if (!StringUtils.hasText(raw)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
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
}
