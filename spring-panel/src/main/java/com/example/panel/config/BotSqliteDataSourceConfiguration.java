package com.example.panel.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@EnableConfigurationProperties(BotSqliteDataSourceProperties.class)
public class BotSqliteDataSourceConfiguration {

    private static final Logger log = LoggerFactory.getLogger(BotSqliteDataSourceConfiguration.class);

    @Bean(name = "botDataSource")
    public DataSource botDataSource(BotSqliteDataSourceProperties props) {
        log.info("Using BOT SQLite database at {}", props.getNormalizedPath());
        return SqliteConnectionConfigSupport.createDataSource(props);
    }

    @Bean(name = "botJdbcTemplate")
    public JdbcTemplate botJdbcTemplate(DataSource botDataSource) {
        return new JdbcTemplate(botDataSource);
    }
}
