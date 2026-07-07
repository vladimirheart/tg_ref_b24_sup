package com.example.panel.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;

class EnvDefaultsInitializerTest {

    @TempDir
    Path tempDir;

    @Test
    void initializeAppliesDefaultsForMissingDbPaths() throws Exception {
        GenericApplicationContext context = new GenericApplicationContext();
        MockEnvironment environment = new MockEnvironment();
        context.setEnvironment(environment);

        new EnvDefaultsInitializer().initialize(context);

        assertTrue(environment.getProperty("APP_DB_PANEL_RUNTIME").endsWith("panel_runtime.db"));
        assertTrue(environment.getProperty("APP_DB_TICKETS").endsWith("panel_runtime.db"));
        assertTrue(environment.getProperty("APP_DB_PANEL_IDENTITY").endsWith("panel_identity.db"));
        assertTrue(environment.getProperty("APP_DB_USERS").endsWith("panel_identity.db"));
        assertTrue(environment.getProperty("APP_DB_SETTINGS").endsWith("settings.db"));
    }

    @Test
    void initializeRespectsExistingValidProperty() throws Exception {
        Path customUsersDb = Files.createFile(tempDir.resolve("custom-panel-identity.db"));

        GenericApplicationContext context = new GenericApplicationContext();
        MockEnvironment environment = new MockEnvironment()
                .withProperty("APP_DB_PANEL_IDENTITY", customUsersDb.toString());
        context.setEnvironment(environment);

        new EnvDefaultsInitializer().initialize(context);

        assertEquals(customUsersDb.toString(), environment.getProperty("APP_DB_PANEL_IDENTITY"));
        assertEquals(customUsersDb.toString(), environment.getProperty("APP_DB_USERS"));
        assertTrue(environment.getProperty("APP_DB_PANEL_RUNTIME").endsWith("panel_runtime.db"));
        assertTrue(environment.getProperty("APP_DB_TICKETS").endsWith("panel_runtime.db"));
    }

    @Test
    void chooseBestExistingCandidatePrefersNonEmptyModuleDatabaseOverEmptyRootCopy() throws Exception {
        Path workspaceRoot = tempDir;
        Path panelDir = Files.createDirectories(workspaceRoot.resolve("spring-panel"));

        Path rootRuntime = Files.writeString(workspaceRoot.resolve("panel_runtime.db"), "root");
        Path panelRuntime = Files.writeString(panelDir.resolve("panel_runtime.db"), "panel-module-data-is-richer");
        Path rootIdentity = Files.writeString(workspaceRoot.resolve("panel_identity.db"), "identity");
        Files.createFile(panelDir.resolve("panel_identity.db"));

        EnvDefaultsInitializer initializer = new EnvDefaultsInitializer();

        Path panelHome = initializer.locatePanelHome(workspaceRoot, workspaceRoot);
        assertEquals(panelDir, panelHome);

        Path bestRuntime = initializer.chooseBestExistingCandidate(
            initializer.collectCandidatePaths(workspaceRoot, panelHome, null, new String[]{"panel_runtime.db"})
        );
        assertEquals(panelRuntime, bestRuntime);

        Path bestIdentity = initializer.chooseBestExistingCandidate(
            initializer.collectCandidatePaths(workspaceRoot, panelHome, panelRuntime.toString(), new String[]{"panel_identity.db"})
        );
        assertEquals(rootIdentity, bestIdentity);
        assertTrue(Files.size(bestRuntime) > Files.size(rootRuntime));
    }
}
