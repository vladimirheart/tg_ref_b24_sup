package com.example.panel.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LegacySqliteImportOperationsSourceContractTest {

    private static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void stagingAndVerificationHelpersKeepSourcesOutsideTheRuntimeRoles() throws IOException {
        String stagingPs = read("scripts/stage-legacy-sqlite-import.ps1");
        String stagingSh = read("scripts/stage-legacy-sqlite-import.sh");
        String verificationPs = read("scripts/verify-legacy-sqlite-import.ps1");
        String runbook = read("docs/runbooks/postgresql-production-contour.md");

        assertThat(stagingPs)
            .contains("Get-FileHash")
            .contains("manifest.json")
            .contains("Hash mismatch while staging")
            .contains("StagingDirectory must stay under");
        assertThat(stagingSh)
            .contains("sha256sum")
            .contains("manifest.tsv")
            .contains("Hash mismatch while staging");
        assertThat(verificationPs)
            .contains("legacy_sqlite_recovery")
            .contains("changed_bot_shard_markers")
            .contains("source_modified_at::text")
            .contains("Critical legacy SQLite table counts are covered by PostgreSQL");
        assertThat(read("spring-panel/src/main/java/com/example/panel/service/LegacySqliteImportService.java"))
            .contains("?mode=ro&immutable=1");
        assertThat(read("spring-panel/src/main/java/com/example/panel/service/LegacyBotShardConsolidationService.java"))
            .contains("?mode=ro&immutable=1");
        assertThat(runbook)
            .contains("stage-legacy-sqlite-import.ps1")
            .contains("verify-legacy-sqlite-import.ps1")
            .contains("IGUANA_LEGACY_SQLITE_AUTO_IMPORT=false");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(REPO_ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
