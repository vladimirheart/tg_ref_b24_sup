package com.example.supportbot.max;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.example.supportbot")
@EnableScheduling
public class MaxBotApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(MaxBotApplication.class);
        app.setDefaultProperties(java.util.Map.of("spring.main.web-application-type", "servlet"));
        app.run(args);
    }
}
