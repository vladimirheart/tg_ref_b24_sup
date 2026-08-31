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
    void applyWorkflowIsWindowsFirstAndCoversRedisAndMinIoWithVerificationAndRollback() throws IOException {
        String ps = read("scripts/docker-production-credential-migration-apply.ps1");
        String runbook = read("docs/runbooks/persisted-credential-rotation-apply.md");

        assertThat(ps)
            .contains("[ValidateSet(\"postgresql\", \"rabbitmq\", \"redis\", \"minio\")]")
            .contains("Resolve-CurrentPostgresPassword")
            .contains("Resolve-CurrentRabbitPassword")
            .contains("Resolve-CurrentRedisPassword")
            .contains("Resolve-CurrentMinIoCredentialPair")
            .contains("Update-PostgresPassword")
            .contains("Update-RabbitPassword")
            .contains("Update-RedisPassword")
            .contains("Test-MinIoBucketAccess")
            .contains("Wait-ForServiceCompletionSuccess")
            .contains("Copy-FileExact -SourcePath $envPath -TargetPath $backupPath")
            .contains("Best-effort PostgreSQL rollback failed")
            .contains("Best-effort RabbitMQ rollback failed")
            .contains("Best-effort Redis rollback failed")
            .contains("Best-effort coordinated MinIO rollback recreate failed")
            .contains("IGUANA_POSTGRES_PASSWORD")
            .contains("SPRING_DATASOURCE_PASSWORD")
            .contains("IGUANA_RABBITMQ_PASSWORD")
            .contains("SPRING_RABBITMQ_PASSWORD")
            .contains("IGUANA_REDIS_PASSWORD")
            .contains("SPRING_DATA_REDIS_PASSWORD")
            .contains("APP_STORAGE_OBJECT_ACCESS_KEY")
            .contains("APP_STORAGE_OBJECT_SECRET_KEY")
            .contains("redis-exporter")
            .contains("minio-init")
            .contains("docker-compose.production-observability.yml");

        assertThat(runbook)
            .contains("postgresql")
            .contains("rabbitmq")
            .contains("redis")
            .contains("minio")
            .contains("-TargetAccessKey")
            .contains("bucket access probe")
            .contains("01-225");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(REPO_ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
