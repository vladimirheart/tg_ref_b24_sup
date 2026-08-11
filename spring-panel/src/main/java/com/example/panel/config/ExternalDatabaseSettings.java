package com.example.panel.config;

public record ExternalDatabaseSettings(
    String jdbcUrl,
    String username,
    String password,
    String driverClassName,
    String hibernateDialect,
    String flywayLocation,
    DatabaseMode vendor
) {
}
