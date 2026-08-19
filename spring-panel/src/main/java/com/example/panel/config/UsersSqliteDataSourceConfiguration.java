package com.example.panel.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@EnableConfigurationProperties(UsersSqliteDataSourceProperties.class)
public class UsersSqliteDataSourceConfiguration {

    private static final Logger log = LoggerFactory.getLogger(UsersSqliteDataSourceConfiguration.class);

    @Bean(name = "usersJdbcTemplate")
    public JdbcTemplate usersJdbcTemplate(UsersSqliteDataSourceProperties props,
                                          JdbcTemplate primaryJdbcTemplate,
                                          PanelDatabaseRuntimeMode databaseRuntimeMode) {
        if (databaseRuntimeMode.isExternalDatabaseEnabled()) {
            log.info("Using primary external {} datasource as USERS runtime template", databaseRuntimeMode.modeLabel());
            return primaryJdbcTemplate;
        }
        log.info("Using USERS SQLite database at {}", props.getNormalizedPath());
        return new JdbcTemplate(SqliteConnectionConfigSupport.createDataSource(props));
    }
}
