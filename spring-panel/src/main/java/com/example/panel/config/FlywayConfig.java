package com.example.panel.config;

import org.flywaydb.core.api.exception.FlywayValidateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {

    private static final Logger logger = LoggerFactory.getLogger(FlywayConfig.class);

    @Bean
    public FlywayMigrationStrategy repairOnValidationError() {
        return flyway -> {
            try {
                flyway.migrate();
            } catch (FlywayValidateException ex) {
                logger.warn("Flyway validation failed, attempting repair before retrying migration", ex);
                flyway.repair();
                logger.warn("Retrying Flyway migration with out-of-order enabled to apply missed migrations.");
                org.flywaydb.core.Flyway.configure()
                        .configuration(flyway.getConfiguration())
                        .outOfOrder(true)
                        .load()
                        .migrate();
            }
        };
    }
}
