package com.example.supportbot.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class ExternalDatabaseSettingsResolverTest {

    @Test
    void autoModeNormalizesPostgresDatabaseUrl() {
        Optional<ExternalDatabaseSettings> settings = ExternalDatabaseSettingsResolver.resolve(
            "auto",
            null,
            null,
            null,
            null,
            "postgres://iguana:secret@db.example.local:5432/iguana?sslmode=require"
        );

        assertTrue(settings.isPresent());
        assertEquals("jdbc:postgresql://db.example.local:5432/iguana?sslmode=require", settings.get().jdbcUrl());
        assertEquals("iguana", settings.get().username());
        assertEquals("secret", settings.get().password());
        assertEquals("postgres", settings.get().schemaPlatform());
    }

    @Test
    void sqliteModeIgnoresExternalDatabaseSettings() {
        Optional<ExternalDatabaseSettings> settings = ExternalDatabaseSettingsResolver.resolve(
            "sqlite",
            "jdbc:postgresql://db.example.local:5432/iguana",
            "iguana",
            "secret",
            null,
            null
        );

        assertFalse(settings.isPresent());
    }

    @Test
    void workerModeIgnoresInheritedExternalDatabaseSettingsBeforeParsingThem() {
        Optional<ExternalDatabaseSettings> settings = ExternalDatabaseSettingsResolver.resolve(
            "worker",
            "jdbc:mysql://must-not-be-used.example.local:3306/iguana",
            "root",
            "secret",
            null,
            "postgres://also:ignored@db.example.local:5432/iguana"
        );

        assertFalse(settings.isPresent());
    }

    @Test
    void postgresqlModeRejectsNonPostgresJdbcUrl() {
        IllegalStateException error = assertThrows(
            IllegalStateException.class,
            () -> ExternalDatabaseSettingsResolver.resolve(
                "postgresql",
                "jdbc:mysql://db.example.local:3306/iguana",
                "root",
                "pw",
                null,
                null
            )
        );

        assertTrue(error.getMessage().contains("PostgreSQL"));
    }
}
