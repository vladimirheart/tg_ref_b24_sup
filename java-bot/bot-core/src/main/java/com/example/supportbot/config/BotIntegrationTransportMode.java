package com.example.supportbot.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class BotIntegrationTransportMode {

    private final Environment environment;

    public BotIntegrationTransportMode(Environment environment) {
        this.environment = environment;
    }

    public boolean isRabbitMqMode() {
        return "rabbitmq".equalsIgnoreCase(mode());
    }

    public String mode() {
        return environment.getProperty("app.integration.transport.mode", "jdbc").trim().toLowerCase();
    }
}
