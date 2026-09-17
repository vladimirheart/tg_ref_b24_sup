package com.example.panel.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class UsersDataSourceConfiguration {

    private static final Logger log = LoggerFactory.getLogger(UsersDataSourceConfiguration.class);

    @Bean(name = "usersJdbcTemplate")
    public JdbcTemplate usersJdbcTemplate(JdbcTemplate primaryJdbcTemplate,
                                          PanelDatabaseRuntimeMode databaseRuntimeMode) {
        log.info("Using primary {} datasource as USERS runtime template", databaseRuntimeMode.modeLabel());
        return primaryJdbcTemplate;
    }
}
