package com.example.supportbot.max;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.example.supportbot")
@EnableJpaRepositories(basePackages = "com.example.supportbot.repository")
@EntityScan(basePackages = "com.example.supportbot")
@EnableScheduling
public class MaxBotApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(MaxBotApplication.class);
        String[] effectiveArgs = java.util.stream.Stream.concat(
                java.util.Arrays.stream(args),
                java.util.stream.Stream.of("--spring.main.web-application-type=servlet"))
            .toArray(String[]::new);
        app.run(effectiveArgs);
    }
}
