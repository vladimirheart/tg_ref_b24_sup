package com.example.panel.service;

import com.example.panel.config.BotProcessProperties;
import com.example.panel.entity.Channel;
import com.example.panel.model.channel.BotCredential;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
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
    private static final Pattern UNSAFE_FILENAME_CHARS = Pattern.compile("[^a-zA-Z0-9._-]+");
    private static final Pattern SPRING_BOOT_STARTED_PATTERN =
        Pattern.compile("(?m)\\bStarted\\s+.+Application\\s+in\\s+.+$");
    private static final Pattern STARTUP_FAILURE_PATTERN =
        Pattern.compile("(?m)^\\*{10,}\\s*$\\R^APPLICATION FAILED TO START\\s*$", Pattern.MULTILINE);

    private final SharedConfigService sharedConfigService;
    private final BotProcessProperties botProcessProperties;
    private final BotRuntimeContractService botRuntimeContractService;
    private final Map<Long, Process> processes = new ConcurrentHashMap<>();
    private final Map<Long, OffsetDateTime> startedAt = new ConcurrentHashMap<>();
    private static final Pattern PID_FILE_PATTERN = Pattern.compile("bot-(\\d+)\\.pid");

    public BotProcessService(SharedConfigService sharedConfigService,
                             BotProcessProperties botProcessProperties,
                             BotRuntimeContractService botRuntimeContractService) {
        this.sharedConfigService = sharedConfigService;
        this.botProcessProperties = botProcessProperties;
        this.botRuntimeContractService = botRuntimeContractService;
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
            String botModule = botRuntimeContractService.resolveBotModule(channel);
            Path botWorkingDir = resolveBotWorkingDir();
            Files.createDirectories(resolveMavenRepoDir(botWorkingDir));
            BotRuntimeContractService.BotLaunchPlan launchPlan = resolveLaunchPlan(botWorkingDir, botModule);
            ProcessBuilder builder = new ProcessBuilder(launchPlan.command());
            builder.directory(botWorkingDir.toFile());
            Path logFile = resolveLogFile(botWorkingDir, channel);
            Path processOutputLogFile = resolveProcessOutputLogFile(logFile);
            Files.createDirectories(logFile.getParent());
            Files.createDirectories(processOutputLogFile.getParent());
            long processOutputStartOffset = Files.exists(processOutputLogFile) ? Files.size(processOutputLogFile) : 0L;
            builder.redirectErrorStream(true);
            builder.redirectOutput(ProcessBuilder.Redirect.appendTo(processOutputLogFile.toFile()));
            builder.environment().putAll(botRuntimeContractService.buildEnvironment(channel, credential, logFile));
            Process process = builder.start();
            OffsetDateTime now = OffsetDateTime.now();
            BotProcessStatus startupStatus =
                awaitProcessReadiness(process, processOutputLogFile, processOutputStartOffset, channelId, now);
            if (!startupStatus.running()) {
                if (process.isAlive()) {
                    stopProcess(process.toHandle(), "startup-readiness-failure", channelId);
                }
                return startupStatus;
            }
            processes.put(channelId, process);
            startedAt.put(channelId, now);
            writePidFile(botWorkingDir, channelId, process.pid());
            log.info("Started bot process for channel {} at {} via {}", channelId, now, launchPlan.description());
            return startupStatus;
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

    public BotRuntimeContractService.BotRuntimeContract describeRuntimeContract(Channel channel) {
        return botRuntimeContractService.describe(channel, resolveBotWorkingDir());
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

    Path resolveBotWorkingDir() {
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

    BotProcessStatus awaitProcessReadiness(Process process,
                                           Path processOutputLogFile,
                                           long processOutputStartOffset,
                                           Long channelId,
                                           OffsetDateTime startedAt) {
        long deadlineNanos = System.nanoTime() + startupReadinessTimeout().toNanos();
        String latestStartupLog = "";
        while (System.nanoTime() < deadlineNanos) {
            latestStartupLog = readProcessOutputSince(processOutputLogFile, processOutputStartOffset);
            if (containsStartupFailure(latestStartupLog)) {
                String failureMessage = extractStartupFailureMessage(latestStartupLog);
                log.warn("Bot process for channel {} reported startup failure: {}", channelId, failureMessage);
                return BotProcessStatus.error(failureMessage);
            }
            if (containsStartedMarker(latestStartupLog) && process.isAlive()) {
                return BotProcessStatus.running(startedAt);
            }
            if (!process.isAlive()) {
                String failureMessage = extractEarlyExitMessage(latestStartupLog);
                log.warn("Bot process for channel {} exited during startup: {}", channelId, failureMessage);
                return BotProcessStatus.error(failureMessage);
            }
            try {
                Thread.sleep(startupPollInterval().toMillis());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return BotProcessStatus.error("Ожидание готовности бота было прервано.");
            }
        }
        latestStartupLog = readProcessOutputSince(processOutputLogFile, processOutputStartOffset);
        if (containsStartedMarker(latestStartupLog) && process.isAlive()) {
            return BotProcessStatus.running(startedAt);
        }
        String timeoutMessage = extractStartupTimeoutMessage(latestStartupLog);
        log.warn("Bot process for channel {} did not confirm readiness in time: {}", channelId, timeoutMessage);
        return BotProcessStatus.error(timeoutMessage);
    }

    private Path resolveLogFile(Path botWorkingDir, Channel channel) {
        String override = System.getenv("APP_BOT_LOG_PATH");
        if (override != null && !override.isBlank()) {
            return Paths.get(override).toAbsolutePath().normalize();
        }
        Path logDir = botWorkingDir.resolve("../logs").normalize();
        String platform = sanitizeFileNameSegment(Objects.toString(channel != null ? channel.getPlatform() : null, "telegram"));
        String channelId = channel != null && channel.getId() != null ? String.valueOf(channel.getId()) : "unknown";
        return logDir.resolve("support-bot-" + platform + "-" + channelId + ".log").toAbsolutePath().normalize();
    }

    private Path resolvePidFile(Path botWorkingDir, Long channelId) {
        Path runDir = botWorkingDir.resolve("../run").normalize();
        return runDir.resolve("bot-" + channelId + ".pid").toAbsolutePath().normalize();
    }

    private Path resolveProcessOutputLogFile(Path logFile) {
        String fileName = logFile.getFileName() != null ? logFile.getFileName().toString() : "support-bot.log";
        int extensionIndex = fileName.lastIndexOf('.');
        String baseName = extensionIndex >= 0 ? fileName.substring(0, extensionIndex) : fileName;
        String extension = extensionIndex >= 0 ? fileName.substring(extensionIndex) : ".log";
        String processFileName = baseName + "-process" + extension;
        Path parent = logFile.getParent();
        return (parent != null ? parent.resolve(processFileName) : Paths.get(processFileName))
            .toAbsolutePath()
            .normalize();
    }

    private String sanitizeFileNameSegment(String value) {
        String sanitized = UNSAFE_FILENAME_CHARS.matcher(Objects.toString(value, "").trim()).replaceAll("-");
        sanitized = sanitized.replaceAll("^-+", "").replaceAll("-+$", "");
        return sanitized.isBlank() ? "unknown" : sanitized.toLowerCase();
    }

    Duration startupReadinessTimeout() {
        return botProcessProperties.resolveStartupReadinessTimeout();
    }

    Duration startupPollInterval() {
        return botProcessProperties.resolveStartupPollInterval();
    }

    private Path resolveMavenRepoDir(Path botWorkingDir) {
        return botWorkingDir.resolve("../spring-panel/.m2/repository").toAbsolutePath().normalize();
    }

    private String readProcessOutputSince(Path processOutputLogFile, long startOffset) {
        if (processOutputLogFile == null || !Files.exists(processOutputLogFile)) {
            return "";
        }
        try (var channel = Files.newByteChannel(processOutputLogFile, StandardOpenOption.READ);
             var buffer = new ByteArrayOutputStream()) {
            long size = channel.size();
            channel.position(Math.min(Math.max(startOffset, 0L), size));
            byte[] chunk = new byte[4096];
            int read;
            while ((read = channel.read(java.nio.ByteBuffer.wrap(chunk))) > 0) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toString(StandardCharsets.UTF_8);
        } catch (IOException ex) {
            log.warn("Failed to read process output log {}", processOutputLogFile, ex);
            return "";
        }
    }

    private boolean containsStartedMarker(String processOutput) {
        return processOutput != null && SPRING_BOOT_STARTED_PATTERN.matcher(processOutput).find();
    }

    private boolean containsStartupFailure(String processOutput) {
        return processOutput != null && STARTUP_FAILURE_PATTERN.matcher(processOutput).find();
    }

    private String extractStartupFailureMessage(String processOutput) {
        if (processOutput == null || processOutput.isBlank()) {
            return "Бот завершился во время старта.";
        }
        String description = extractSectionValue(processOutput, "Description:");
        if (!description.isBlank()) {
            return "Бот не прошёл инициализацию: " + description;
        }
        String action = extractSectionValue(processOutput, "Action:");
        if (!action.isBlank()) {
            return "Бот не прошёл инициализацию: " + action;
        }
        String lastLine = lastNonBlankLine(processOutput);
        if (!lastLine.isBlank()) {
            return "Бот не прошёл инициализацию: " + lastLine;
        }
        return "Бот не прошёл инициализацию.";
    }

    private String extractEarlyExitMessage(String processOutput) {
        if (containsStartupFailure(processOutput)) {
            return extractStartupFailureMessage(processOutput);
        }
        String lastLine = lastNonBlankLine(processOutput);
        if (!lastLine.isBlank()) {
            return "Бот завершился во время старта: " + lastLine;
        }
        return "Бот завершился во время старта без подтверждения готовности.";
    }

    private String extractStartupTimeoutMessage(String processOutput) {
        String lastLine = lastNonBlankLine(processOutput);
        if (!lastLine.isBlank()) {
            return "Не удалось подтвердить готовность бота после старта. Последняя строка лога: " + lastLine;
        }
        return "Не удалось подтвердить готовность бота после старта.";
    }

    private String extractSectionValue(String processOutput, String sectionHeader) {
        String[] lines = Objects.toString(processOutput, "").split("\\R");
        boolean inSection = false;
        StringBuilder section = new StringBuilder();
        for (String line : lines) {
            if (!inSection) {
                if (sectionHeader.equals(line.trim())) {
                    inSection = true;
                }
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                if (section.length() > 0) {
                    break;
                }
                continue;
            }
            if (trimmed.endsWith(":") && section.length() > 0) {
                break;
            }
            if (section.length() > 0) {
                section.append(' ');
            }
            section.append(trimmed);
        }
        return section.toString();
    }

    private String lastNonBlankLine(String processOutput) {
        String[] lines = Objects.toString(processOutput, "").split("\\R");
        for (int i = lines.length - 1; i >= 0; i--) {
            String trimmed = lines[i].trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return "";
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

    BotRuntimeContractService.BotLaunchPlan resolveLaunchPlan(Path botWorkingDir, String botModule) {
        return botRuntimeContractService.resolveLaunchPlan(botWorkingDir, botModule);
    }

    Path resolveExecutableJar(Path botWorkingDir, String botModule) {
        return botRuntimeContractService.resolveExecutableJar(botWorkingDir, botModule);
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
