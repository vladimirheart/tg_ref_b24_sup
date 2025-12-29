package com.example.panel;

import com.example.panel.config.EnvDefaultsInitializer;
import com.example.panel.security.SecurityBootstrap;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableScheduling
public class PanelApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(PanelApplication.class);
        app.addInitializers(new EnvDefaultsInitializer());
        app.run(args);
    }

    @Bean
    public ApplicationRunner bootstrapSecurity(SecurityBootstrap securityBootstrap) {
        return args -> securityBootstrap.ensureDefaultAdmin();
    }
}
