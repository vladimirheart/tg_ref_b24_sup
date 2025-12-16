package com.example.panel.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;

@Configuration
@EnableConfigurationProperties(UsersSqliteDataSourceProperties.class)
public class UsersSqliteDataSourceConfiguration {

    private static final Logger log = LoggerFactory.getLogger(UsersSqliteDataSourceConfiguration.class);

    @Bean(name = "usersDataSource")
    public DataSource usersDataSource(UsersSqliteDataSourceProperties props) {
        var url = props.buildJdbcUrl();
        log.info("Using USERS SQLite database at {}", props.getNormalizedPath());

        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl(url);
        return ds;
    }

    @Bean(name = "usersJdbcTemplate")
    public JdbcTemplate usersJdbcTemplate(DataSource usersDataSource) {
        return new JdbcTemplate(usersDataSource);
    }
}
