package com.example.supportbot.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class ExternalDatabaseSettingsResolverTest {

    @Test
    void postgresqlModeNormalizesPostgresDatabaseUrl() {
        Optional<ExternalDatabaseSettings> settings = ExternalDatabaseSettingsResolver.resolve(
            "postgresql", null, null, null, null,
            "postgres://iguana:secret@db.example.local:5432/iguana?sslmode=require"
        );

        assertTrue(settings.isPresent());
        assertEquals("jdbc:postgresql://db.example.local:5432/iguana?sslmode=require", settings.get().jdbcUrl());
        assertEquals("iguana", settings.get().username());
        assertEquals("secret", settings.get().password());
        assertEquals("postgres", settings.get().schemaPlatform());
    }

    @Test
    void explicitPostgresqlJdbcSettingsAreAccepted() {
        Optional<ExternalDatabaseSettings> settings = ExternalDatabaseSettingsResolver.resolve(
            "postgresql",
            "jdbc:postgresql://db.example.local:5432/iguana",
            "iguana",
            "secret",
            null,
            null
        );
        assertTrue(settings.isPresent());
        assertEquals("org.postgresql.Driver", settings.get().driverClassName());
    }

    @Test
    void nonPostgresqlModesAreRejected() {
        for (String mode : new String[]{"sqlite", "worker", "auto", "mysql"}) {
            assertThrows(IllegalArgumentException.class, () -> ExternalDatabaseSettingsResolver.resolve(
                mode, "jdbc:postgresql://db.example.local:5432/iguana", "iguana", "secret", null, null
            ));
        }
    }

    @Test
    void postgresqlModeRejectsNonPostgresqlJdbcUrl() {
        IllegalStateException error = assertThrows(
            IllegalStateException.class,
            () -> ExternalDatabaseSettingsResolver.resolve(
                "postgresql", "jdbc:mysql://db.example.local:3306/iguana", "root", "pw", null, null
            )
        );
        assertTrue(error.getMessage().contains("PostgreSQL"));
    }
}
