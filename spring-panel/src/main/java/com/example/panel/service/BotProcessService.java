package com.example.panel.service;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BotProcessService {

    private static final Logger log = LoggerFactory.getLogger(BotProcessService.class);

    private final SharedConfigService sharedConfigService;
    private final BotDatabaseRegistry botDatabaseRegistry;
    private final Map<Long, Process> processes = new ConcurrentHashMap<>();
    private final Map<Long, OffsetDateTime> startedAt = new ConcurrentHashMap<>();

    public BotProcessService(SharedConfigService sharedConfigService,
                             BotDatabaseRegistry botDatabaseRegistry) {
        this.sharedConfigService = sharedConfigService;
        this.botDatabaseRegistry = botDatabaseRegistry;
    }

    public BotProcessStatus start(Channel channel) {
        Long channelId = channel.getId();
        if (channelId == null) {
            return BotProcessStatus.error("Канал не сохранён, сначала сохраните настройки.");
        }
        Process existing = processes.get(channelId);
        if (existing != null && existing.isAlive()) {
            return BotProcessStatus.running(startedAt.get(channelId));
        }

        BotCredential credential = resolveCredential(channel);
        if (credential == null || credential.token().isBlank()) {
            return BotProcessStatus.error("Не найдены учётные данные бота для канала.");
        }

        try {
            ProcessBuilder builder = new ProcessBuilder(mvnwCommand(), "-q", "spring-boot:run");
            builder.directory(resolveBotWorkingDir().toFile());
            Map<String, String> env = builder.environment();
            env.put("APP_DB_TICKETS", botDatabaseRegistry.ensureBotDatabase(channelId, channel.getPlatform()).toString());
            env.put("TELEGRAM_BOT_TOKEN", credential.token());
            env.put("TELEGRAM_BOT_USERNAME", Objects.toString(channel.getBotUsername(), ""));
            env.put("GROUP_CHAT_ID", Objects.toString(channel.getSupportChatId(), "0"));
            env.put("VK_BOT_ENABLED", "vk".equalsIgnoreCase(channel.getPlatform()) ? "true" : "false");
            if ("vk".equalsIgnoreCase(channel.getPlatform())) {
                env.put("VK_BOT_TOKEN", credential.token());
                env.put("VK_OPERATOR_CHAT_ID", Objects.toString(channel.getSupportChatId(), "0"));
            }
            env.putIfAbsent("SPRING_PROFILES_ACTIVE", "default");
            Process process = builder.start();
            processes.put(channelId, process);
            OffsetDateTime now = OffsetDateTime.now();
            startedAt.put(channelId, now);
            log.info("Started bot process for channel {} at {}", channelId, now);
            return BotProcessStatus.running(now);
        } catch (Exception ex) {
            log.error("Failed to start bot process for channel {}", channelId, ex);
            return BotProcessStatus.error("Не удалось запустить бота: " + ex.getMessage());
        }
    }

    public BotProcessStatus stop(Long channelId) {
        Process process = processes.get(channelId);
        if (process == null || !process.isAlive()) {
            processes.remove(channelId);
            startedAt.remove(channelId);
            return BotProcessStatus.stopped();
        }
        process.destroy();
        processes.remove(channelId);
        startedAt.remove(channelId);
        return BotProcessStatus.stopped();
    }

    public BotProcessStatus status(Long channelId) {
        Process process = processes.get(channelId);
        if (process != null && process.isAlive()) {
            return BotProcessStatus.running(startedAt.get(channelId));
        }
        return BotProcessStatus.stopped();
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

    private String mvnwCommand() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("win") ? "mvnw.cmd" : "./mvnw";
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
