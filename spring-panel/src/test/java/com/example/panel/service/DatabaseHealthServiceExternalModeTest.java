package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.example.panel.config.PanelDatabaseRuntimeMode;
import com.example.panel.config.SqliteDataSourceProperties;
import com.example.panel.config.UsersSqliteDataSourceProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

class DatabaseHealthServiceExternalModeTest {

    @TempDir
    Path tempDir;

    @Test
    void externalModeDoesNotMaterializeSqliteCompatibilityFiles() {
        Path primaryPath = tempDir.resolve("panel_runtime.db");
        Path usersPath = tempDir.resolve("panel_identity.db");
        SqliteDataSourceProperties primaryProperties = new SqliteDataSourceProperties();
        primaryProperties.setPath(primaryPath.toString());
        UsersSqliteDataSourceProperties usersProperties = new UsersSqliteDataSourceProperties();
        usersProperties.setPath(usersPath.toString());

        DatabaseHealthService service = new DatabaseHealthService(
            mock(JdbcTemplate.class),
            mock(JdbcTemplate.class),
            primaryProperties,
            usersProperties,
            postgresqlMode()
        );

        assertThat(service.databasePath()).isEqualTo("jdbc:postgresql://localhost:5432/iguana");
        assertThat(Files.exists(primaryPath)).isFalse();
        assertThat(Files.exists(usersPath)).isFalse();
    }

    private static PanelDatabaseRuntimeMode postgresqlMode() {
        return new PanelDatabaseRuntimeMode(new MockEnvironment()
            .withProperty("app.datasource.mode", "postgresql")
            .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/iguana")
            .withProperty("spring.datasource.username", "iguana")
            .withProperty("spring.datasource.password", "iguana"));
    }
}
