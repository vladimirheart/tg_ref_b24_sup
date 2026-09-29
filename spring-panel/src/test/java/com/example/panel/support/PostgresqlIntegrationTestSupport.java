package com.example.panel.support;

import com.example.panel.service.BotProcessService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public abstract class PostgresqlIntegrationTestSupport {
    private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:16-alpine");
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE)
            .withDatabaseName("iguana_test")
            .withUsername("iguana")
            .withPassword("iguana");

    static {
        POSTGRES.start();
    }

    @MockBean
    private BotProcessService botProcessService;

    @MockBean(name = "taskScheduler")
    private TaskScheduler taskScheduler;

    @DynamicPropertySource
    static void postgresql(DynamicPropertyRegistry registry) throws IOException {
        Path sharedConfigDir = Files.createTempDirectory("panel-postgresql-shared-config-");
        Path storageDir = Files.createTempDirectory("panel-postgresql-storage-");

        registry.add("app.datasource.mode", () -> "postgresql");
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/postgresql");
        registry.add("shared-config.dir", () -> sharedConfigDir.toString());
        registry.add("app.storage.attachments", () -> storageDir.resolve("attachments").toString());
        registry.add("app.storage.knowledge-base", () -> storageDir.resolve("knowledge_base").toString());
        registry.add("app.storage.passport-photos", () -> storageDir.resolve("passport_photos").toString());
        registry.add("app.storage.avatars", () -> storageDir.resolve("avatars").toString());
        registry.add("app.storage.object.required-for-postgresql", () -> "false");
        registry.add("app.coordination.required-for-postgresql", () -> "false");
        registry.add("app.bots.internal-api.token", () -> "01-277-test-internal-bot-api-token");
        registry.add("app.security.remember-me-key", () -> "01-277-test-remember-me-key");
        registry.add("app.security.bootstrap-admin.username", () -> "s4_postgresql_test_admin");
        registry.add("app.security.bootstrap-admin.password", () -> "s4-postgresql-test-admin-password");
        registry.add("app.bots.auto-start-enabled", () -> "false");
        registry.add("monitoring.credentials.master-key", () -> "01-277-test-monitoring-master-key");
    }
}
