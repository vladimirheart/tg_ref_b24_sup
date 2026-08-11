package com.example.supportbot.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

class BotDatabaseRuntimeModeTest {

    @Test
    void defaultsToSqliteWithoutExternalDatasource() {
        BotDatabaseRuntimeMode runtimeMode = new BotDatabaseRuntimeMode(new MockEnvironment());

        assertThat(runtimeMode.isSqliteMode()).isTrue();
        assertThat(runtimeMode.isExternalMode()).isFalse();
        assertThat(runtimeMode.modeLabel()).isEqualTo("sqlite");
    }

    @Test
    void resolvesPostgresWhenDatasourceUrlIsConfigured() {
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "spring.datasource.url", "jdbc:postgresql://localhost:5432/supportbot",
                "spring.datasource.username", "bot",
                "spring.datasource.password", "secret"
        )));

        BotDatabaseRuntimeMode runtimeMode = new BotDatabaseRuntimeMode(environment);

        assertThat(runtimeMode.isSqliteMode()).isFalse();
        assertThat(runtimeMode.isExternalMode()).isTrue();
        assertThat(runtimeMode.modeLabel()).isEqualTo("postgres");
    }

    @Test
    void keepsSqliteWhenModeExplicitlyForcesIt() {
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "support-bot.database.mode", "sqlite",
                "spring.datasource.url", "jdbc:postgresql://localhost:5432/supportbot"
        )));

        BotDatabaseRuntimeMode runtimeMode = new BotDatabaseRuntimeMode(environment);

        assertThat(runtimeMode.isSqliteMode()).isTrue();
        assertThat(runtimeMode.modeLabel()).isEqualTo("sqlite");
    }
}
