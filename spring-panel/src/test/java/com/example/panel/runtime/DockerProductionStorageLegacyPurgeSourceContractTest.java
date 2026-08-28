package com.example.panel.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DockerProductionStorageLegacyPurgeSourceContractTest {

    private static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void legacyInventoryIsReadOnlyAndKeepsEvidenceOutsideRepo() throws IOException {
        String ps = read("scripts/docker-production-storage-legacy-inventory.ps1");

        assertThat(ps)
            .contains("[switch]$ValidateOnly")
            .contains("iguana-legacy-storage-inventory")
            .contains("dialog-or-orphan-review")
            .contains("separate-audit:avatars")
            .contains("excluded:knowledge_base")
            .contains("excluded:passport_photos")
            .contains("excluded:forms")
            .contains("Export-Csv")
            .contains("no repository files, database rows, MinIO objects, or legacy source files were modified")
            .doesNotContain("Remove-Item")
            .doesNotContain("Move-Item")
            .doesNotContain("DELETE ")
            .doesNotContain("mc rm")
            .doesNotContain("docker compose ps");
    }

    @Test
    void exactMappingAuditUsesHashesExactMetadataAndUnauthorizedManifest() throws IOException {
        String ps = read("scripts/docker-production-storage-local-exact-mapping-audit.ps1");

        assertThat(ps)
            .contains("[switch]$ValidateOnly")
            .contains("param([string]$Exe, [string[]]$Arguments, [string]$Message)")
            .contains("$out = @(& $Exe @Arguments 2>&1)")
            .doesNotContain("param([string]$Exe, [string[]]$Args, [string]$Message)")
            .contains("encode(convert_to(storage_key, 'UTF8'), 'hex')")
            .contains("Get-FileHash -LiteralPath")
            .contains("candidate_set_sha256")
            .contains("quarantine_authorized=$false")
            .contains("physical_delete_authorized=$false")
            .contains("known_unrecoverable_now_has_local_source")
            .contains("duplicate_relative_path_has_different_sha256")
            .contains("no_exact_chat_attachment_metadata_storage_key")
            .contains("label=com.docker.compose.project=tg_ref_b24_sup")
            .contains("docker-production-storage-cutover-gate.ps1")
            .contains("docker-production-client-avatar-cutover-audit.ps1")
            .contains("Invoke-PowerShellScriptStreaming")
            .contains("-Stage \"storage-cutover-gate\"")
            .contains("-Stage \"client-avatar-cutover-audit\"")
            .contains("stage=runtime-storage-contract")
            .contains("stage=attachment-metadata")
            .contains("stage=local-sha256")
            .contains("stage=exact-mapping")
            .doesNotContain("Invoke-Native \"powershell.exe\"")
            .doesNotContain("Move-Item")
            .doesNotContain("Remove-Item")
            .doesNotContain("DELETE ")
            .doesNotContain("mc rm")
            .doesNotContain("\"ps\", \"--status\"");
    }

    @Test
    void quarantineIsManifestDrivenHashCheckedWhatIfSafeAndNeverDeletes() throws IOException {
        String ps = read("scripts/docker-production-storage-quarantine.ps1");

        assertThat(ps)
            .contains("SupportsShouldProcess = $true")
            .contains("[switch]$Apply")
            .contains("[switch]$ValidateOnly")
            .contains("param([string]$Exe, [string[]]$Arguments, [string]$Message)")
            .contains("$out = @(& $Exe @Arguments 2>&1)")
            .doesNotContain("param([string]$Exe, [string[]]$Args, [string]$Message)")
            .contains("function Get-FileLengthReadOnly")
            .contains("function Get-Sha256ReadOnly")
            .contains("[IO.FileInfo]::new($Path)")
            .contains("[IO.File]::Open(")
            .contains("[Security.Cryptography.SHA256]::Create()")
            .contains("$currentLength = Get-FileLengthReadOnly -Path $source")
            .contains("$hash = Get-Sha256ReadOnly -Path $source")
            .contains("$invocationWhatIf = [bool]$WhatIfPreference")
            .contains("candidate_set_sha256")
            .contains("Manifest is not authorized for quarantine")
            .contains("QuarantineRoot must be explicit when -Apply is used")
            .contains("QuarantineRoot must be on the same volume as every source file")
            .contains(".env.storage-cutover-20260828-162458.bak")
            .contains("physical_delete_authorized")
            .contains("Manifest FullName does not match RootName + RelativePath")
            .contains("Move-Item -LiteralPath")
            .contains("[WHATIF]")
            .contains("label=com.docker.compose.project=tg_ref_b24_sup")
            .contains("docker-production-storage-cutover-gate.ps1")
            .contains("docker-production-client-avatar-cutover-audit.ps1")
            .contains("Invoke-PowerShellScriptStreaming")
            .contains("-Stage \"storage-cutover-gate\"")
            .contains("-Stage \"client-avatar-cutover-audit\"")
            .contains("stage=manifest-source-verification")
            .doesNotContain("Invoke-Native \"powershell.exe\"")
            .doesNotContain("Get-Item -LiteralPath $source")
            .doesNotContain("Remove-Item")
            .doesNotContain("DELETE ")
            .doesNotContain("mc rm")
            .doesNotContain("rm -rf")
            .doesNotContain("--remove-orphans")
            .doesNotContain("\"ps\", \"--status\"");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(REPO_ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
