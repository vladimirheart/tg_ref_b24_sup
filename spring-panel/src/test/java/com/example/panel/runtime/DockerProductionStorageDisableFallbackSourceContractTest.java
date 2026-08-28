package com.example.panel.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DockerProductionStorageDisableFallbackSourceContractTest {

    private static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void helperPersistsFalseAndRecreatesOnlyPanelRuntimeAfterGreenGates() throws IOException {
        String ps = Files.readString(
            REPO_ROOT.resolve("scripts/docker-production-storage-disable-fallback.ps1"),
            StandardCharsets.UTF_8
        );

        assertThat(ps)
            .contains("[switch]$ValidateOnly")
            .contains("docker-production-storage-cutover-gate.ps1")
            .contains("docker-production-client-avatar-cutover-audit.ps1")
            .contains("Running pre-cutover authoritative storage gate")
            .contains("Running post-cutover authoritative storage gate")
            .contains("APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED=false")
            .contains(".storage-cutover-")
            .contains("Get-DockerServiceContainerIds")
            .contains("Resolve-RuntimeComposeProjectName")
            .contains("label=com.docker.compose.service=")
            .contains("label=com.docker.compose.project=")
            .contains("com.docker.compose.project")
            .contains("ConvertFrom-Json")
            .contains("--project-name")
            .contains("Preserving runtime scale")
            .contains("\"--scale\", \"ops-worker=$workerReplicas\"")
            .contains("\"--scale\", \"panel-web=$webReplicas\"")
            .contains("\"--no-deps\", \"--force-recreate\"")
            .contains("Wait-ContainersHealthy")
            .contains("Assert-FallbackDisabledInContainers")
            .contains("STORAGE FALLBACK CUTOVER COMPLETED")
            .contains("MinIO, PostgreSQL, RabbitMQ, Redis, panel-direct, bots, observability and backup services were not recreated")
            .doesNotContain("Get-ComposeServiceContainerIds")
            .doesNotContain("$ComposePrefix + @(\"ps\"")
            .doesNotContain("--remove-orphans")
            .doesNotContain("--build")
            .doesNotContain(" compose down")
            .doesNotContain("\"down\"")
            .doesNotContain("mc cp")
            .doesNotContain("mc mirror")
            .doesNotContain("mc rm")
            .doesNotContain("UPDATE ")
            .doesNotContain("DELETE ")
            .doesNotContain("Remove-Item")
            .doesNotContain("rm -rf");
    }
}
