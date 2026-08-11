package com.example.supportbot.config;

record ExternalDatabaseSettings(
    String jdbcUrl,
    String username,
    String password,
    String driverClassName,
    String hibernateDialect,
    String schemaPlatform
) {
}
