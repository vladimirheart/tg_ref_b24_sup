package com.example.panel.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class PanelIntegrationTransportMode {

    private final Environment environment;

    public PanelIntegrationTransportMode(Environment environment) {
        this.environment = environment;
    }

    public boolean isRabbitMqMode() {
        return "rabbitmq".equalsIgnoreCase(mode());
    }

    public String mode() {
        return environment.getProperty("app.integration.transport.mode", "jdbc").trim().toLowerCase();
    }
}
