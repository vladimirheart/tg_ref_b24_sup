package com.example.supportbot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class BotDatabaseRuntimeModeTest {

    @Test
    void resolvesCanonicalPostgresqlRuntime() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("support-bot.database.mode", "postgresql")
            .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/supportbot")
            .withProperty("spring.datasource.username", "bot")
            .withProperty("spring.datasource.password", "secret");

        BotDatabaseRuntimeMode runtimeMode = new BotDatabaseRuntimeMode(environment);

        assertThat(runtimeMode.modeLabel()).isEqualTo("postgres");
    }

    @Test
    void missingPostgresqlContractFailsClosed() {
        assertThatThrownBy(() -> new BotDatabaseRuntimeMode(new MockEnvironment()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("PostgreSQL");
    }

    @Test
    void legacyModesAreRejected() {
        for (String mode : new String[]{"sqlite", "worker", "auto", "mysql"}) {
            MockEnvironment environment = new MockEnvironment()
                .withProperty("support-bot.database.mode", mode)
                .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/supportbot");
            assertThatThrownBy(() -> new BotDatabaseRuntimeMode(environment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only postgresql");
        }
    }
}
