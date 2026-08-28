package com.example.panel.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DockerProductionStorageLocalSourceAuditSourceContractTest {

    private static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void localSourceAuditIsReadOnlyAndPreservesUtf8Metadata() throws IOException {
        String ps = Files.readString(
            REPO_ROOT.resolve("scripts/docker-production-storage-local-source-audit.ps1"),
            StandardCharsets.UTF_8
        );

        assertThat(ps)
            .contains("[switch]$ValidateOnly")
            .contains("encode(convert_to(storage_key, 'UTF8'), 'hex')")
            .contains("encode(convert_to(COALESCE(legacy_attachment_ref, ''), 'UTF8'), 'hex')")
            .contains("ConvertFrom-HexUtf8")
            .contains("Get-LegacyAttachmentRoot")
            .contains("Get-ChildItem -LiteralPath $root -File -Recurse")
            .contains("rows_with_unique_local_candidate=")
            .contains("rows_with_ambiguous_local_candidates=")
            .contains("rows_with_no_local_candidate=")
            .contains("no database rows, MinIO objects, or local files were modified")
            .doesNotContain("UPDATE ")
            .doesNotContain("DELETE ")
            .doesNotContain("mc cp")
            .doesNotContain("mc mirror")
            .doesNotContain("mc rm")
            .doesNotContain("Remove-Item")
            .doesNotContain("rm -rf")
            .doesNotContain("--remove-orphans");
    }
}
