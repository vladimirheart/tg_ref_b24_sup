package com.example.panel.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DockerProductionStorageRepairMappingsSourceContractTest {

    private static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void repairUsesExactMetadataKeysWithoutDatabaseOrSourceDeletion() throws IOException {
        String ps = read("scripts/docker-production-storage-repair-mappings.ps1");
        String sh = read("scripts/internal/storage-repair-mapping.sh");

        assertThat(ps)
            .contains("[switch]$ValidateOnly")
            .contains("encode(convert_to(storage_key, 'UTF8'), 'hex')")
            .contains("ConvertFrom-HexUtf8")
            .contains("Join-ObjectKey -Prefix $runtimePrefix -Domain \"attachments\" -LogicalKey $storageKey")
            .contains("Join-ObjectKey -Prefix \"\" -Domain \"attachments\" -LogicalKey $storageKey")
            .contains("/workspace/java-bot/attachments/$storageKey")
            .contains("/workspace/attachments/$storageKey")
            .contains("/workspace/scripts/internal/storage-repair-mapping.sh")
            .contains("[REPAIR_RESULT]")
            .contains("no database rows or local source files were modified")
            .doesNotContain("$shellCommand = @(")
            .doesNotContain("UPDATE ")
            .doesNotContain("DELETE ")
            .doesNotContain("mc rm")
            .doesNotContain("Remove-Item")
            .doesNotContain("rm -rf")
            .doesNotContain("--remove-orphans");

        assertThat(sh)
            .contains("set -u")
            .contains("mc stat \"$canonical\"")
            .contains("mc cp \"$IGUANA_REPAIR_LOCAL_PATH\" \"$canonical\"")
            .contains("mc cp \"$legacy\" \"$canonical\"")
            .contains("[REPAIR_RESULT] canonical")
            .contains("[REPAIR_RESULT] local")
            .contains("[REPAIR_RESULT] legacy")
            .contains("[REPAIR_RESULT] missing")
            .doesNotContain("mc rm")
            .doesNotContain("rm -rf")
            .doesNotContain("DELETE ")
            .doesNotContain("UPDATE ");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(REPO_ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
