package com.example.panel.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.datasource.sqlite")
public class SqliteDataSourceProperties extends AbstractSqliteDataSourceProperties {
    private static final Logger log = LoggerFactory.getLogger(SqliteDataSourceProperties.class);

    public SqliteDataSourceProperties() {
        super("panel_runtime.db", "app.datasource.sqlite.path", "primary");
    }

    @Override
    protected Logger logger() {
        return log;
    }
}

