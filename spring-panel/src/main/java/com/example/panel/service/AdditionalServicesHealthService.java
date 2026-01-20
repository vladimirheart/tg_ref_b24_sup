package com.example.panel.service;

import com.example.panel.config.BotProcessProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AdditionalServicesHealthService {

    private static final Logger log = LoggerFactory.getLogger(AdditionalServicesHealthService.class);
    private static final int MAX_SEARCH_DEPTH = 5;
    private static final Set<String> TELEGRAM_MARKERS = Set.of("bot-telegram", "telegrambotapplication");
    private static final Set<String> VK_MARKERS = Set.of("bot-vk", "vkbotapplication");

    private final BotProcessProperties botProcessProperties;

    public AdditionalServicesHealthService(BotProcessProperties botProcessProperties) {
        this.botProcessProperties = botProcessProperties;
    }

    public void checkServices() {
        checkBotDatabaseDir();
        checkBotRuntimeFiles();
        checkBotProcesses();
    }

    private void checkBotDatabaseDir() {
        Path dir = botProcessProperties.resolveDatabaseDir();
        if (!Files.exists(dir)) {
            log.warn("Bot database directory does not exist: {}", dir);
            return;
        }
        if (!Files.isDirectory(dir)) {
            log.warn("Bot database path is not a directory: {}", dir);
            return;
        }
        if (!Files.isReadable(dir) || !Files.isWritable(dir)) {
            log.warn("Bot database directory is not readable/writable: {}", dir);
            return;
        }
        log.info("Bot database directory is available: {}", dir);
    }

    private void checkBotRuntimeFiles() {
        Optional<Path> javaBotDir = locateJavaBotDir();
        if (javaBotDir.isEmpty()) {
            log.warn("java-bot directory was not found near {}", Paths.get("").toAbsolutePath().normalize());
            return;
        }
        Path baseDir = javaBotDir.get();
        boolean hasMvnw = Files.exists(baseDir.resolve("mvnw")) || Files.exists(baseDir.resolve("mvnw.cmd"));
        if (!hasMvnw) {
            log.warn("Maven wrapper not found inside {}", baseDir);
            return;
        }
        log.info("Bot runtime is available at {}", baseDir);
    }

    private void checkBotProcesses() {
        boolean telegramRunning = isProcessRunning(TELEGRAM_MARKERS);
        boolean vkRunning = isProcessRunning(VK_MARKERS);
        log.info("Telegram bot process status: {}", telegramRunning ? "running" : "stopped");
        log.info("VK bot process status: {}", vkRunning ? "running" : "stopped");
    }

    private Optional<Path> locateJavaBotDir() {
        Path current = Paths.get("").toAbsolutePath().normalize();
        for (int depth = 0; depth <= MAX_SEARCH_DEPTH && current != null; depth++) {
            Path candidate = current.resolve("java-bot").normalize();
            if (Files.isDirectory(candidate)) {
                return Optional.of(candidate);
            }
            current = current.getParent();
        }
        return Optional.empty();
    }

    private boolean isProcessRunning(Set<String> markers) {
        List<String> markerList = markers.stream()
            .map(marker -> marker.toLowerCase(Locale.ROOT))
            .toList();
        return ProcessHandle.allProcesses()
            .anyMatch(handle -> matchesMarkers(handle, markerList));
    }

    private boolean matchesMarkers(ProcessHandle handle, List<String> markers) {
        ProcessHandle.Info info;
        try {
            info = handle.info();
        } catch (SecurityException ex) {
            return false;
        }
        StringBuilder payload = new StringBuilder();
        info.command().ifPresent(command -> payload.append(command).append(' '));
        info.commandLine().ifPresent(commandLine -> payload.append(commandLine).append(' '));
        info.arguments().ifPresent(args -> payload.append(String.join(" ", Arrays.asList(args))).append(' '));
        String candidate = payload.toString().toLowerCase(Locale.ROOT);
        if (candidate.isBlank()) {
            return false;
        }
        for (String marker : markers) {
            if (candidate.contains(marker)) {
                return true;
            }
        }
        return false;
    }
}
