package com.example.panel.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.datasource.knowledge-sqlite")
public class KnowledgeSqliteDataSourceProperties extends AbstractSqliteDataSourceProperties {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeSqliteDataSourceProperties.class);

    public KnowledgeSqliteDataSourceProperties() {
        super("knowledge_base.db", "app.datasource.knowledge-sqlite.path", "knowledge");
    }

    @Override
    protected Logger logger() {
        return log;
    }
}
