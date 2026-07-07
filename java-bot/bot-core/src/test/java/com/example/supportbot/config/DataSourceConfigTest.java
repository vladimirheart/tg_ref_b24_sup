package com.example.supportbot.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataSourceConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void resolveSqlitePathPrefersRicherWorkspaceRuntimeOverNearbyCompatibilityCopy() throws Exception {
        Path workspaceRoot = tempDir.resolve("workspace");
        Path panelDir = workspaceRoot.resolve("spring-panel");
        Path javaBotDir = workspaceRoot.resolve("java-bot");
        Path botCoreDir = javaBotDir.resolve("bot-core");
        Files.createDirectories(panelDir);
        Files.createDirectories(botCoreDir);
        Files.createDirectories(workspaceRoot.resolve("ai-context"));

        Files.writeString(javaBotDir.resolve("panel_runtime.db"), "tiny");
        Files.writeString(workspaceRoot.resolve("panel_runtime.db"), "root-runtime-is-bigger");
        Path panelRuntime = Files.writeString(panelDir.resolve("panel_runtime.db"), "panel-runtime-is-the-richest-copy");

        Path resolved = DataSourceConfig.resolveSqlitePath("../panel_runtime.db", botCoreDir);

        assertEquals(panelRuntime.toAbsolutePath().normalize(), resolved);
    }

    @Test
    void resolveSqlitePathWithoutConfiguredPathPrefersLargestExistingCandidate() throws Exception {
        Path workspaceRoot = tempDir.resolve("workspace");
        Path panelDir = workspaceRoot.resolve("spring-panel");
        Path botCoreDir = workspaceRoot.resolve("java-bot").resolve("bot-core");
        Files.createDirectories(panelDir);
        Files.createDirectories(botCoreDir);
        Files.createDirectories(workspaceRoot.resolve(".git"));

        Files.writeString(botCoreDir.getParent().resolve("panel_runtime.db"), "tiny");
        Files.writeString(workspaceRoot.resolve("panel_runtime.db"), "root-runtime");
        Path panelRuntime = Files.writeString(panelDir.resolve("panel_runtime.db"), "panel-runtime-is-bigger-than-root");

        Path resolved = DataSourceConfig.resolveSqlitePath("", botCoreDir);

        assertEquals(panelRuntime.toAbsolutePath().normalize(), resolved);
    }
}
