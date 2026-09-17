package com.example.panel.config;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LegacySqliteArchiveConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(LegacySqliteArchiveConfiguration.class)
        .withPropertyValues(
            "app.datasource.sqlite.path=archive-panel.db",
            "app.datasource.monitoring-sqlite.path=archive-monitoring.db",
            "app.datasource.bot-sqlite.path=archive-bot.db"
        );

    @Test
    void bindsLegacySourcePathsWithoutCreatingRuntimeDatasource() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(SqliteDataSourceProperties.class);
            assertThat(context).hasSingleBean(MonitoringSqliteDataSourceProperties.class);
            assertThat(context).hasSingleBean(BotSqliteDataSourceProperties.class);
            assertThat(context).doesNotHaveBean(DataSource.class);

            assertThat(context.getBean(SqliteDataSourceProperties.class).getPath())
                .isEqualTo("archive-panel.db");
            assertThat(context.getBean(MonitoringSqliteDataSourceProperties.class).getPath())
                .isEqualTo("archive-monitoring.db");
            assertThat(context.getBean(BotSqliteDataSourceProperties.class).getPath())
                .isEqualTo("archive-bot.db");
        });
    }
}
