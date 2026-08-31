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
    void applyWorkflowCoversPowerShellAndBashWithRedisAndMinIoParity() throws IOException {
        String ps = read("scripts/docker-production-credential-migration-apply.ps1");
        String sh = read("scripts/docker-production-credential-migration-apply.sh");
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
            .contains("docker-compose.production-observability.yml");

        assertThat(sh)
            .contains("--component must be one of: postgresql, rabbitmq, redis, minio.")
            .contains("resolve_current_postgres_password")
            .contains("resolve_current_rabbitmq_password")
            .contains("resolve_current_redis_password")
            .contains("test_minio_bucket_access")
            .contains("wait_for_service_completion_success")
            .contains("Best-effort PostgreSQL rollback failed")
            .contains("Best-effort RabbitMQ rollback failed")
            .contains("Best-effort Redis rollback failed")
            .contains("Best-effort coordinated MinIO rollback recreate failed")
            .contains("MSYS_NO_PATHCONV=1")
            .contains("MSYS2_ARG_CONV_EXCL='*'")
            .contains("APP_STORAGE_OBJECT_ACCESS_KEY")
            .contains("APP_STORAGE_OBJECT_SECRET_KEY")
            .contains("docker-compose.production-observability.yml");

        assertThat(runbook)
            .contains("scripts/docker-production-credential-migration-apply.ps1")
            .contains("scripts/docker-production-credential-migration-apply.sh")
            .contains("Git Bash")
            .contains("MSYS_NO_PATHCONV=1")
            .contains("postgresql")
            .contains("rabbitmq")
            .contains("redis")
            .contains("minio")
            .contains("--target-access-key")
            .contains("bucket access probe")
            .contains("01-226");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(REPO_ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
