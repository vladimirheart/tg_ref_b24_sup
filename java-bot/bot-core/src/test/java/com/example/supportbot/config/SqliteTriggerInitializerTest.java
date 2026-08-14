package com.example.supportbot.config;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

class SqliteTriggerInitializerTest {

    @Test
    void runSkipsTriggerBootstrapWhenExternalModeIsActive() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SqliteTriggerInitializer initializer = new SqliteTriggerInitializer(
                jdbcTemplate,
                new BotDatabaseRuntimeMode(
                        new MockEnvironment()
                                .withProperty("support-bot.database.mode", "postgresql")
                                .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/iguana")
                )
        );

        initializer.run(new DefaultApplicationArguments(new String[0]));

        verify(jdbcTemplate, never()).execute(anyString());
        verify(jdbcTemplate, never()).execute(org.mockito.ArgumentMatchers.<ConnectionCallback<Set<String>>>any());
    }

    @Test
    void runKeepsLegacyTriggerBootstrapInSqliteMode() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.execute(org.mockito.ArgumentMatchers.<ConnectionCallback<Set<String>>>any())).thenReturn(Set.of());
        SqliteTriggerInitializer initializer = new SqliteTriggerInitializer(
                jdbcTemplate,
                new BotDatabaseRuntimeMode(new MockEnvironment())
        );

        initializer.run(new DefaultApplicationArguments(new String[0]));

        verify(jdbcTemplate, times(1)).execute(org.mockito.ArgumentMatchers.<ConnectionCallback<Set<String>>>any());
        verify(jdbcTemplate, times(2)).execute(anyString());
    }
}
