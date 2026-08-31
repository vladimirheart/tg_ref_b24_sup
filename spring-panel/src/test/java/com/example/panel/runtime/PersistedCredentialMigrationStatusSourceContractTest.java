package com.example.panel.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PersistedCredentialMigrationStatusSourceContractTest {

    private static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void statusHelpersClassifyPersistedCredentialContoursWithoutMutation() throws IOException {
        String ps = read("scripts/docker-production-credential-migration-status.ps1");
        String sh = read("scripts/docker-production-credential-migration-status.sh");
        String runbook = read("docs/runbooks/persisted-credential-migration-status.md");

        assertThat(ps)
            .contains("overall_status")
            .contains("migration_required")
            .contains("Test-PostgresCredential")
            .contains("Test-RabbitMqCredential")
            .contains("Test-RedisCredential")
            .contains("Test-GrafanaCredential")
            .contains("Get-MinIoRuntimeEnvMatch")
            .doesNotContain("WriteAllText($envPath")
            .doesNotContain("Set-Content -LiteralPath $envPath");

        assertThat(sh)
            .contains("overall_status=\"mixed\"")
            .contains("migration_required")
            .contains("docker exec -e \"PGPASSWORD=${postgres_password}\"")
            .contains("rabbitmqctl authenticate_user")
            .contains("redis-cli -a")
            .contains("curl -fsS -u")
            .doesNotContain("docker compose up")
            .doesNotContain("> \"${ENV_FILE}\"");

        assertThat(runbook)
            .contains("fresh")
            .contains("ready")
            .contains("migration_required")
            .contains("01-223");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(REPO_ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
