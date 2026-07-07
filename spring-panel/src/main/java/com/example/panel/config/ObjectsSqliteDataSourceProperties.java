package com.example.panel.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.datasource.objects-sqlite")
public class ObjectsSqliteDataSourceProperties extends AbstractSqliteDataSourceProperties {
    private static final Logger log = LoggerFactory.getLogger(ObjectsSqliteDataSourceProperties.class);

    public ObjectsSqliteDataSourceProperties() {
        super("objects.db", "app.datasource.objects-sqlite.path", "objects");
    }

    @Override
    protected Logger logger() {
        return log;
    }
}
