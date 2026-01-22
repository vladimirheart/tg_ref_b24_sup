package com.example.panel.service;

import com.example.panel.config.SqliteDataSourceProperties;
import com.example.panel.entity.Channel;
import com.example.panel.model.channel.BotCredential;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BotProcessService {

    private static final Logger log = LoggerFactory.getLogger(BotProcessService.class);

    private final SharedConfigService sharedConfigService;
    private final SqliteDataSourceProperties ticketsDbProperties;
    private final Map<Long, Process> processes = new ConcurrentHashMap<>();
    private final Map<Long, OffsetDateTime> startedAt = new ConcurrentHashMap<>();
    private static final Pattern PID_FILE_PATTERN = Pattern.compile("bot-(\\d+)\\.pid");

    public BotProcessService(SharedConfigService sharedConfigService,
                             SqliteDataSourceProperties ticketsDbProperties) {
        this.sharedConfigService = sharedConfigService;
        this.ticketsDbProperties = ticketsDbProperties;
    }

    public BotProcessStatus start(Channel channel) {
        Long channelId = channel.getId();
        if (channelId == null) {
            return BotProcessStatus.error("Канал не сохранён, сначала сохраните настройки.");
        }
        stop(channelId);

        BotCredential credential = resolveCredential(channel);
        if (credential == null || credential.token().isBlank()) {
            return BotProcessStatus.error("Не найдены учётные данные бота для канала.");
        }

        try {
            String botModule = resolveBotModule(channel);
            ProcessBuilder builder = new ProcessBuilder(
                mvnwCommand(),
                "-q",
                "-Dorg.slf4j.simpleLogger.showDateTime=true",
                "-Dorg.slf4j.simpleLogger.dateTimeFormat=yyyy-MM-dd HH:mm:ss.SSSXXX",
                "-pl",
                botModule,
                "-am",
                "spring-boot:run"
            );
            Path botWorkingDir = resolveBotWorkingDir();
            builder.directory(botWorkingDir.toFile());
            Path logFile = resolveLogFile(botWorkingDir);
            Files.createDirectories(logFile.getParent());
            builder.redirectErrorStream(true);
            builder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));
            Map<String, String> env = builder.environment();
            env.put("APP_DB_TICKETS", ticketsDbProperties.getNormalizedPath().toString());
            env.put("TELEGRAM_BOT_TOKEN", credential.token());
            env.put("TELEGRAM_BOT_USERNAME", Objects.toString(channel.getBotUsername(), ""));
            env.put("GROUP_CHAT_ID", Objects.toString(channel.getSupportChatId(), "0"));
            env.put("VK_BOT_ENABLED", "vk".equalsIgnoreCase(channel.getPlatform()) ? "true" : "false");
            if ("vk".equalsIgnoreCase(channel.getPlatform())) {
                env.put("VK_BOT_TOKEN", credential.token());
                env.put("VK_OPERATOR_CHAT_ID", Objects.toString(channel.getSupportChatId(), "0"));
            }
            env.putIfAbsent("SPRING_PROFILES_ACTIVE", "default");
            env.put("APP_BOT_LOG_PATH", logFile.toString());
            Process process = builder.start();
            processes.put(channelId, process);
            OffsetDateTime now = OffsetDateTime.now();
            startedAt.put(channelId, now);
            writePidFile(botWorkingDir, channelId, process.pid());
            log.info("Started bot process for channel {} at {}", channelId, now);
            return BotProcessStatus.running(now);
        } catch (Exception ex) {
            log.error("Failed to start bot process for channel {}", channelId, ex);
            return BotProcessStatus.error("Не удалось запустить бота: " + ex.getMessage());
        }
    }

    public BotProcessStatus stop(Long channelId) {
        Process process = processes.remove(channelId);
        if (process != null) {
            stopProcess(process.toHandle(), "in-memory", channelId);
        }
        Path botWorkingDir = resolveBotWorkingDir();
        Path pidFile = resolvePidFile(botWorkingDir, channelId);
        stopProcessFromPidFile(pidFile, channelId);
        startedAt.remove(channelId);
        log.info("Stopped bot process for channel {}", channelId);
        return BotProcessStatus.stopped();
    }

    public BotProcessStatus status(Long channelId) {
        Process process = processes.get(channelId);
        if (process != null && process.isAlive()) {
            return BotProcessStatus.running(startedAt.get(channelId));
        }
        return BotProcessStatus.stopped();
    }

    @PreDestroy
    public void stopAll() {
        log.info("Stopping all bot processes due to panel shutdown");
        processes.forEach((channelId, process) -> stopProcess(process.toHandle(), "shutdown", channelId));
        processes.clear();
        try {
            Path runDir = resolveBotWorkingDir().resolve("../run").normalize();
            if (Files.isDirectory(runDir)) {
                try (Stream<Path> files = Files.list(runDir)) {
                    files.filter(path -> PID_FILE_PATTERN.matcher(path.getFileName().toString()).matches())
                        .forEach(path -> {
                            Long channelId = parseChannelIdFromPidFile(path.getFileName().toString());
                            stopProcessFromPidFile(path, channelId);
                        });
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to stop bot processes from pid files during shutdown", ex);
        }
    }

    private BotCredential resolveCredential(Channel channel) {
        Long credentialId = channel.getCredentialId();
        List<BotCredential> credentials = sharedConfigService.loadBotCredentials();

        if (credentialId != null) {
            return credentials.stream()
                .filter(cred -> Objects.equals(cred.id(), credentialId))
                .findFirst()
                .orElse(fallbackToChannelToken(channel));
        }

        BotCredential fromShared = credentials.stream()
            .filter(cred -> channel.getPlatform() == null || channel.getPlatform().equalsIgnoreCase(cred.platform()))
            .findFirst()
            .orElse(null);

        if (fromShared != null && fromShared.token() != null && !fromShared.token().isBlank()) {
            return fromShared;
        }

        return fallbackToChannelToken(channel);
    }

    private BotCredential fallbackToChannelToken(Channel channel) {
        String token = channel.getToken();
        if (token == null || token.isBlank()) {
            return null;
        }
        return new BotCredential(
            null,
            "db:channels#" + channel.getId(),
            Objects.toString(channel.getPlatform(), "telegram"),
            token,
            true
        );
    }

    private Path resolveBotWorkingDir() {
        Path current = Paths.get("").toAbsolutePath().normalize();
        for (int depth = 0; depth < 5 && current != null; depth++) {
            Path candidate = current.resolve("java-bot").normalize();
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("java-bot directory not found near " + Paths.get("").toAbsolutePath().normalize());
    }

    private String resolveBotModule(Channel channel) {
        if (channel != null && "vk".equalsIgnoreCase(channel.getPlatform())) {
            return "bot-vk";
        }
        return "bot-telegram";
    }

    private Path resolveLogFile(Path botWorkingDir) {
        String override = System.getenv("APP_BOT_LOG_PATH");
        if (override != null && !override.isBlank()) {
            return Paths.get(override).toAbsolutePath().normalize();
        }
        Path logDir = botWorkingDir.resolve("../logs").normalize();
        return logDir.resolve("support-bot.log").toAbsolutePath().normalize();
    }

    private Path resolvePidFile(Path botWorkingDir, Long channelId) {
        Path runDir = botWorkingDir.resolve("../run").normalize();
        return runDir.resolve("bot-" + channelId + ".pid").toAbsolutePath().normalize();
    }

    private void writePidFile(Path botWorkingDir, Long channelId, long pid) {
        try {
            Path pidFile = resolvePidFile(botWorkingDir, channelId);
            Files.createDirectories(pidFile.getParent());
            Files.writeString(pidFile, Long.toString(pid));
        } catch (Exception ex) {
            log.warn("Failed to write pid file for channel {}", channelId, ex);
        }
    }

    private void stopProcessFromPidFile(Path pidFile, Long channelId) {
        if (!Files.exists(pidFile)) {
            log.warn("PID file not found for channel {}", channelId);
            return;
        }
        try {
            String content = Files.readString(pidFile).trim();
            if (!content.isEmpty()) {
                long pid = Long.parseLong(content);
                ProcessHandle.of(pid).ifPresent(handle -> stopProcess(handle, "pid-file", channelId));
            }
        } catch (Exception ex) {
            log.warn("Failed to stop process from pid file {} for channel {}", pidFile, channelId, ex);
        } finally {
            try {
                Files.deleteIfExists(pidFile);
            } catch (Exception ex) {
                log.warn("Failed to delete pid file {} for channel {}", pidFile, channelId, ex);
            }
        }
    }

    private void stopProcess(ProcessHandle handle, String source, Long channelId) {
        if (!handle.isAlive()) {
            log.warn("Process for channel {} is already not alive", channelId);
            return;
        }
        log.info("Stopping bot process for channel {} via {}", channelId, source);
        List<ProcessHandle> descendants = handle.descendants()
            .filter(ProcessHandle::isAlive)
            .toList();
        descendants.forEach(ProcessHandle::destroy);
        handle.destroy();
        waitForExit(handle, 5);
        boolean stillAlive = handle.isAlive() || descendants.stream().anyMatch(ProcessHandle::isAlive);
        if (stillAlive) {
            log.warn("Bot process for channel {} did not stop gracefully, forcing termination", channelId);
            descendants.forEach(ProcessHandle::destroyForcibly);
            handle.destroyForcibly();
            waitForExit(handle, 5);
        }
    }

    private String mvnwCommand() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("win") ? "mvnw.cmd" : "./mvnw";
    }

    private void waitForExit(ProcessHandle handle, long timeoutSeconds) {
        try {
            handle.onExit().get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception ex) {
            log.warn("Failed to wait for bot process termination for channel", ex);
        }
    }

    private Long parseChannelIdFromPidFile(String fileName) {
        Matcher matcher = PID_FILE_PATTERN.matcher(fileName);
        if (matcher.matches()) {
            return Long.parseLong(matcher.group(1));
        }
        return null;
    }

    public record BotProcessStatus(boolean running, String message, OffsetDateTime startedAt) {
        public static BotProcessStatus running(OffsetDateTime startedAt) {
            return new BotProcessStatus(true, "running", startedAt);
        }

        public static BotProcessStatus stopped() {
            return new BotProcessStatus(false, "stopped", null);
        }

        public static BotProcessStatus error(String message) {
            return new BotProcessStatus(false, message, null);
        }
    }
}
