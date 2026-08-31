package com.example.panel.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

class UsersSqliteDataSourceConfigurationTest {

    @TempDir
    Path tempDir;

    private final UsersSqliteDataSourceConfiguration configuration = new UsersSqliteDataSourceConfiguration();

    @Test
    void usersJdbcTemplateUsesPrimaryTemplateInExternalMode() {
        JdbcTemplate primaryJdbcTemplate = mock(JdbcTemplate.class);
        UsersSqliteDataSourceProperties properties = new UsersSqliteDataSourceProperties();
        properties.setPath(tempDir.resolve("panel_identity.db").toString());

        JdbcTemplate runtimeJdbcTemplate = configuration.usersJdbcTemplate(
            properties,
            primaryJdbcTemplate,
            new PanelDatabaseRuntimeMode(new MockEnvironment()
                .withProperty("app.datasource.mode", "postgresql")
                .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/iguana")
                .withProperty("spring.datasource.username", "iguana")
                .withProperty("spring.datasource.password", "iguana"))
        );

        assertThat(runtimeJdbcTemplate).isSameAs(primaryJdbcTemplate);
    }

    @Test
    void usersJdbcTemplateAlwaysUsesPrimaryTemplate() {
        JdbcTemplate primaryJdbcTemplate = mock(JdbcTemplate.class);
        UsersSqliteDataSourceProperties properties = new UsersSqliteDataSourceProperties();
        properties.setPath(tempDir.resolve("panel_identity.db").toString());

        JdbcTemplate runtimeJdbcTemplate = configuration.usersJdbcTemplate(
            properties,
            primaryJdbcTemplate,
            new PanelDatabaseRuntimeMode(new MockEnvironment())
        );

        assertThat(runtimeJdbcTemplate).isSameAs(primaryJdbcTemplate);
    }
}
