package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.example.panel.config.PanelDatabaseRuntimeMode;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

class DatabaseHealthServiceExternalModeTest {

    @Test
    void externalModeUsesCanonicalDatasourceDescription() {
        DatabaseHealthService service = new DatabaseHealthService(
            mock(JdbcTemplate.class),
            mock(JdbcTemplate.class),
            postgresqlMode()
        );

        assertThat(service.databasePath()).isEqualTo("jdbc:postgresql://localhost:5432/iguana");
    }

    private static PanelDatabaseRuntimeMode postgresqlMode() {
        return new PanelDatabaseRuntimeMode(new MockEnvironment()
            .withProperty("app.datasource.mode", "postgresql")
            .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/iguana")
            .withProperty("spring.datasource.username", "iguana")
            .withProperty("spring.datasource.password", "iguana"));
    }
}
