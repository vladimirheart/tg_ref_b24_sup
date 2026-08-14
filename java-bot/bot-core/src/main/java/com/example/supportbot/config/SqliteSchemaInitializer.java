package com.example.supportbot.config;

import javax.sql.DataSource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class SqliteSchemaInitializer implements ApplicationRunner {
    // Local SQLite bootstrap only. External PostgreSQL runtime must connect to a prebuilt schema instead.

    private final DataSource dataSource;
    private final BotDatabaseRuntimeMode databaseRuntimeMode;

    public SqliteSchemaInitializer(DataSource dataSource, BotDatabaseRuntimeMode databaseRuntimeMode) {
        this.dataSource = dataSource;
        this.databaseRuntimeMode = databaseRuntimeMode;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!databaseRuntimeMode.isSqliteMode()) {
            return;
        }

        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("schema-sqlite.sql"));
        populator.setSqlScriptEncoding("UTF-8");
        DatabasePopulatorUtils.execute(populator, dataSource);
    }
}
