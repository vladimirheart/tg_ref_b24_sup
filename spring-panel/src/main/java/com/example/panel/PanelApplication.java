package com.example.panel;

import com.example.panel.runtime.RuntimeReplicaPolicy;
import com.example.panel.runtime.RuntimeRole;
import com.example.panel.runtime.RuntimeWorkload;

import com.example.panel.config.EnvDefaultsInitializer;
import com.example.panel.security.SecurityBootstrap;
import com.example.panel.service.AdditionalServicesHealthService;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableScheduling
@EnableRabbit
public class PanelApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(PanelApplication.class);
        app.addInitializers(new EnvDefaultsInitializer());
        app.run(args);
    }

    @Bean
    @RuntimeWorkload(
        id = "security-bootstrap",
        roles = {RuntimeRole.MIGRATOR},
        replicaPolicy = RuntimeReplicaPolicy.SINGLETON
    )
    public ApplicationRunner bootstrapSecurity(ObjectProvider<SecurityBootstrap> securityBootstrap) {
        return args -> {
            SecurityBootstrap bootstrap = securityBootstrap.getIfAvailable();
            if (bootstrap != null) {
                bootstrap.ensureDefaultAdmin();
            }
        };
    }

    @Bean
    @RuntimeWorkload(
        id = "additional-services-health-check",
        roles = {},
        replicaPolicy = RuntimeReplicaPolicy.PROCESS_LOCAL
    )
    public ApplicationRunner checkAdditionalServices(ObjectProvider<AdditionalServicesHealthService> healthService) {
        return args -> {
            AdditionalServicesHealthService service = healthService.getIfAvailable();
            if (service != null) {
                service.checkServices();
            }
        };
    }
}
