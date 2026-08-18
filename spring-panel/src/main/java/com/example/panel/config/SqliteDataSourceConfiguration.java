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
            return builder.build();
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
