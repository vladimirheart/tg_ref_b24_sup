package com.example.panel.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.datasource.clients-sqlite")
public class ClientsSqliteDataSourceProperties extends AbstractSqliteDataSourceProperties {
    private static final Logger log = LoggerFactory.getLogger(ClientsSqliteDataSourceProperties.class);

    public ClientsSqliteDataSourceProperties() {
        super("clients.db", "app.datasource.clients-sqlite.path", "clients");
    }

    @Override
    protected Logger logger() {
        return log;
    }
}
