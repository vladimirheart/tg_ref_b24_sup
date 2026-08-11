package com.example.panel.config;

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
        assertEquals(DatabaseMode.POSTGRESQL, settings.get().vendor());
        assertEquals("jdbc:postgresql://db.example.local:5432/iguana?sslmode=require", settings.get().jdbcUrl());
        assertEquals("iguana", settings.get().username());
        assertEquals("secret", settings.get().password());
        assertEquals("classpath:db/migration/postgresql", settings.get().flywayLocation());
    }

    @Test
    void autoModePrefersExplicitSpringDatasourceSettings() {
        Optional<ExternalDatabaseSettings> settings = ExternalDatabaseSettingsResolver.resolve(
            "auto",
            "jdbc:mysql://db.example.local:3306/iguana",
            "root",
            "pw",
            null,
            "postgres://ignored:ignored@localhost:5432/other"
        );

        assertTrue(settings.isPresent());
        assertEquals(DatabaseMode.MYSQL, settings.get().vendor());
        assertEquals("jdbc:mysql://db.example.local:3306/iguana", settings.get().jdbcUrl());
        assertEquals("classpath:db/migration/mysql", settings.get().flywayLocation());
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
    void postgresqlModeRequiresMatchingVendor() {
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

        assertTrue(error.getMessage().contains("postgresql"));
    }
}
