package com.example.supportbot.config;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SupportBotCoreConfiguration {

    @Value("${support-bot.attachments-dir:attachments}")
    private String attachmentsDir;

    @Value("${logging.file.name:}")
    private String logFileName;

    @PostConstruct
    void ensureDirectories() throws IOException {
        Path root = Paths.get(attachmentsDir);
        Files.createDirectories(root);
        Files.createDirectories(root.resolve("temp"));
        if (logFileName != null && !logFileName.isBlank()) {
            Path logPath = Paths.get(logFileName).toAbsolutePath().normalize();
            Path parent = logPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        }
    }

    @Bean
    public Path attachmentsRoot() {
        return Paths.get(attachmentsDir).toAbsolutePath().normalize();
    }
}
