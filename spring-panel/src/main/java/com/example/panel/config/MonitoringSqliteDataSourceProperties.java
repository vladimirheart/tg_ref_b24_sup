package com.example.panel.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.datasource.monitoring-sqlite")
public class MonitoringSqliteDataSourceProperties extends AbstractSqliteDataSourceProperties {
    private static final Logger log = LoggerFactory.getLogger(MonitoringSqliteDataSourceProperties.class);

    public MonitoringSqliteDataSourceProperties() {
        super("monitoring.db", "app.datasource.monitoring-sqlite.path", "monitoring");
    }

    @Override
    protected Logger logger() {
        return log;
    }
}
