package com.example.panel.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DockerProductionStorageKnownLossCutoverGateSourceContractTest {

    private static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void gateUsesExactReviewedManifestAndUtf8CanonicalStatsWithoutMutation() throws IOException {
        String ps = read("scripts/docker-production-storage-cutover-gate.ps1");
        String helper = read("scripts/internal/storage-cutover-object-stat.sh");
        String manifest = read("ai-context/storage-known-unrecoverable-dialog-attachments.json");
        String attributes = read(".gitattributes");

        assertThat(ps)
            .contains("[switch]$ValidateOnly")
            .contains("storage-known-unrecoverable-dialog-attachments.json")
            .contains("encode(convert_to(storage_key, 'UTF8'), 'hex')")
            .contains("ConvertFrom-HexUtf8")
            .contains("availability_status")
            .contains("storage-cutover-object-stat.sh")
            .contains("known_unrecoverable_dialog_objects=")
            .contains("unexpected_missing_s3_dialog_objects=")
            .contains("stale_known_unrecoverable_entries=")
            .contains("missing_s3_panel_avatars=")
            .contains("invalid_panel_avatar_refs=")
            .contains("Reviewed known-unrecoverable")
            .contains("\"exec\", \"-T\", \"postgres\"")
            .contains("\"exec\", \"-T\", $Service, \"printenv\", $Name")
            .doesNotContain("Get-RunningServiceContainerIds")
            .doesNotContain("\"ps\", \"--status\"")
            .doesNotContain("UPDATE ")
            .doesNotContain("DELETE ")
            .doesNotContain("mc cp")
            .doesNotContain("mc mirror")
            .doesNotContain("mc rm")
            .doesNotContain("Remove-Item")
            .doesNotContain("rm -rf")
            .doesNotContain("--remove-orphans");

        assertThat(helper)
            .contains("mc stat \"$object\"")
            .contains("[GATE_OBJECT] present")
            .contains("[GATE_OBJECT] missing")
            .doesNotContain("mc cp")
            .doesNotContain("mc mirror")
            .doesNotContain("mc rm");

        assertThat(attributes).contains("scripts/internal/*.sh text eol=lf");
        assertThat(manifest).contains("\"schema_version\": 1");
        long manifestEntryCount = manifest.lines()
            .filter(line -> line.contains("\"metadata_id\""))
            .count();
        assertThat(manifestEntryCount).isEqualTo(20L);
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(REPO_ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
