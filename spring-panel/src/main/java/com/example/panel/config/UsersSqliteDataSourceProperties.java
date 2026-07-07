package com.example.panel.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.datasource.users-sqlite")
public class UsersSqliteDataSourceProperties extends AbstractSqliteDataSourceProperties {
    private static final Logger log = LoggerFactory.getLogger(UsersSqliteDataSourceProperties.class);

    public UsersSqliteDataSourceProperties() {
        super("users.db", "app.datasource.users-sqlite.path", "users");
    }

    @Override
    protected Logger logger() {
        return log;
    }
}
