package com.example.supportbot;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootApplication
@EnableScheduling
public class SupportBotApplication {

    @Value("${support-bot.attachments-dir:attachments}")
    private String attachmentsDir;

    public static void main(String[] args) {
        SpringApplication.run(SupportBotApplication.class, args);
    }

    @PostConstruct
    void ensureDirectories() throws IOException {
        Path root = Paths.get(attachmentsDir);
        Files.createDirectories(root);
        Files.createDirectories(root.resolve("temp"));
    }

    @Bean
    public Path attachmentsRoot() {
        return Paths.get(attachmentsDir).toAbsolutePath().normalize();
    }
}