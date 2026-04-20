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

        assertTrue(environment.getProperty("APP_DB_TICKETS").endsWith("tickets.db"));
        assertTrue(environment.getProperty("APP_DB_USERS").endsWith("users.db"));
        assertTrue(environment.getProperty("APP_DB_SETTINGS").endsWith("settings.db"));
    }

    @Test
    void initializeRespectsExistingValidProperty() throws Exception {
        Path customUsersDb = Files.createFile(tempDir.resolve("custom-users.db"));

        GenericApplicationContext context = new GenericApplicationContext();
        MockEnvironment environment = new MockEnvironment()
                .withProperty("APP_DB_USERS", customUsersDb.toString());
        context.setEnvironment(environment);

        new EnvDefaultsInitializer().initialize(context);

        assertEquals(customUsersDb.toString(), environment.getProperty("APP_DB_USERS"));
        assertTrue(environment.getProperty("APP_DB_TICKETS").endsWith("tickets.db"));
    }
}
