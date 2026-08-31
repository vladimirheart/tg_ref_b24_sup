package com.example.panel.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PersistedCredentialRotationApplySourceContractTest {

    private static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void applyWorkflowIsWindowsFirstAndTargetsPostgresAndRabbitMqWithRollback() throws IOException {
        String ps = read("scripts/docker-production-credential-migration-apply.ps1");
        String runbook = read("docs/runbooks/persisted-credential-rotation-apply.md");

        assertThat(ps)
            .contains("[ValidateSet(\"postgresql\", \"rabbitmq\")]")
            .contains("Resolve-CurrentPostgresPassword")
            .contains("Resolve-CurrentRabbitPassword")
            .contains("Update-PostgresPassword")
            .contains("Update-RabbitPassword")
            .contains("Copy-FileExact -SourcePath $envPath -TargetPath $backupPath")
            .contains("Best-effort PostgreSQL rollback failed")
            .contains("Best-effort RabbitMQ rollback failed")
            .contains("IGUANA_POSTGRES_PASSWORD")
            .contains("SPRING_DATASOURCE_PASSWORD")
            .contains("IGUANA_RABBITMQ_PASSWORD")
            .contains("SPRING_RABBITMQ_PASSWORD")
            .contains("postgres-exporter")
            .contains("bot-telegram");

        assertThat(runbook)
            .contains("postgresql")
            .contains("rabbitmq")
            .contains("-Apply")
            .contains("01-224");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(REPO_ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
