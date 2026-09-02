package com.example.panel.service;

import com.example.panel.config.BotProcessProperties;
import com.example.panel.entity.Channel;
import com.example.panel.model.channel.BotCredential;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
    private static final long MIN_STARTUP_STABILITY_WINDOW_MS = 750L;
    private static final List<String> WORKER_DATABASE_ENVIRONMENT_KEYS = List.of(
        "SPRING_DATASOURCE_URL",
        "SPRING_DATASOURCE_USERNAME",
        "SPRING_DATASOURCE_PASSWORD",
        "SPRING_DATASOURCE_DRIVER_CLASS_NAME",
        "DATABASE_URL",
        "APP_DB_BOT",
        "APP_DB_BOT_RUNTIME",
        "APP_DB_PANEL_RUNTIME",
        "APP_DB_TICKETS",
        "SUPPORT_BOT_DATABASE_PATH"
    );

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

    static void applyBotProcessEnvironment(ProcessBuilder builder,
                                           Map<String, String> contractEnvironment) {
        Map<String, String> targetEnvironment = builder.environment();
        boolean isolatedWorker = "worker".equalsIgnoreCase(contractEnvironment.get("APP_DB_MODE"));
        if (isolatedWorker) {
            WORKER_DATABASE_ENVIRONMENT_KEYS.forEach(targetEnvironment::remove);
        }
        targetEnvironment.putAll(contractEnvironment);
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
            releaseReservedServerPortIfOccupied(channel);
            Files.createDirectories(resolveMavenRepoDir(botWorkingDir));
            BotRuntimeContractService.BotLaunchPlan launchPlan = resolveLaunchPlan(botWorkingDir, botModule);
            ProcessBuilder builder = new ProcessBuilder(launchPlan.command());
            builder.directory(botWorkingDir.toFile());
            Path logFile = resolveLogFile(botWorkingDir, channel);
            Path processOutputLogFile = resolveProcessOutputLogFile(logFile);
            Files.createDirectories(logFile.getParent());
            Files.createDirectories(processOutputLogFile.getParent());
            long processOutputStartOffset = 0L;
            builder.redirectErrorStream(true);
            builder.redirectOutput(ProcessBuilder.Redirect.to(processOutputLogFile.toFile()));
            applyBotProcessEnvironment(
                builder,
                botRuntimeContractService.buildEnvironment(channel, credential, logFile)
            );
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
            saveSharedStatus(channelId, startupStatus);
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
        saveSharedStatus(channelId, BotProcessStatus.stopped());
        log.info("Stopped bot process for channel {}", channelId);
        return BotProcessStatus.stopped();
    }

    public BotProcessStatus status(Long channelId) {
        Process process = processes.get(channelId);
        if (process != null && process.isAlive()) {
            return BotProcessStatus.running(startedAt.get(channelId));
        }
        if (process != null) {
            processes.remove(channelId);
            startedAt.remove(channelId);
        }
        Path botWorkingDir = resolveBotWorkingDir();
        Path pidFile = resolvePidFile(botWorkingDir, channelId);
        ProcessHandle handle = resolveProcessHandleFromPidFile(pidFile, channelId, true);
        if (handle != null && handle.isAlive()) {
            OffsetDateTime detectedStart = handle.info().startInstant()
                .map(instant -> OffsetDateTime.ofInstant(instant, java.time.ZoneId.systemDefault()))
                .orElse(null);
            if (detectedStart != null) {
                startedAt.put(channelId, detectedStart);
            }
            BotProcessStatus status = BotProcessStatus.running(detectedStart);
            saveSharedStatus(channelId, status);
            return status;
        }
        return loadSharedStatus(channelId).orElse(BotProcessStatus.stopped());
    }

    public BotRuntimeContractService.BotRuntimeContract describeRuntimeContract(Channel channel) {
        return botRuntimeContractService.describe(channel, resolveBotWorkingDir());
    }

    public void stopAllForStartup() {
        stopAllProcesses("panel startup");
    }

    @PreDestroy
    public void stopAll() {
        stopAllProcesses("panel shutdown");
    }

    private void stopAllProcesses(String reason) {
        log.info("Stopping all bot processes due to {}", reason);
        processes.forEach((channelId, process) -> stopProcess(process.toHandle(), reason, channelId));
        processes.clear();
        startedAt.clear();
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
        Long startedMarkerSeenAtNanos = null;
        String latestStartupLog = "";
        while (System.nanoTime() < deadlineNanos) {
            latestStartupLog = readProcessOutputSince(processOutputLogFile, processOutputStartOffset);
            if (containsStartupFailure(latestStartupLog)) {
                String failureMessage = extractStartupFailureMessage(latestStartupLog);
                log.warn("Bot process for channel {} reported startup failure: {}", channelId, failureMessage);
                return BotProcessStatus.error(failureMessage);
            }
            if (containsStartedMarker(latestStartupLog)) {
                if (!process.isAlive()) {
                    String failureMessage = extractEarlyExitMessage(latestStartupLog);
                    log.warn("Bot process for channel {} exited right after startup marker: {}", channelId, failureMessage);
                    return BotProcessStatus.error(failureMessage);
                }
                if (startedMarkerSeenAtNanos == null) {
                    startedMarkerSeenAtNanos = System.nanoTime();
                }
                if (System.nanoTime() - startedMarkerSeenAtNanos >= startupStabilityWindow().toNanos()) {
                    return BotProcessStatus.running(startedAt);
                }
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
        String configuredLogDir = System.getenv("APP_BOT_LOG_DIR");
        Path logDir;
        if (configuredLogDir != null && !configuredLogDir.isBlank()) {
            Path configuredPath = Paths.get(configuredLogDir);
            logDir = configuredPath.isAbsolute()
                ? configuredPath
                : botWorkingDir.resolve(configuredPath);
        } else {
            logDir = botWorkingDir.resolve("../logs");
        }
        logDir = logDir.toAbsolutePath().normalize();

        String platform = sanitizeFileNameSegment(Objects.toString(channel != null ? channel.getPlatform() : null, "telegram"));
        String channelId = channel != null && channel.getId() != null ? String.valueOf(channel.getId()) : "unknown";
        return logDir.resolve("support-bot-" + platform + "-" + channelId + ".log").normalize();
    }

    private Path resolvePidFile(Path botWorkingDir, Long channelId) {
        Path runDir = botWorkingDir.resolve("../run").normalize();
        return runDir.resolve("bot-" + channelId + ".pid").toAbsolutePath().normalize();
    }

    private void saveSharedStatus(Long channelId, BotProcessStatus status) {
        if (channelId == null || status == null) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("status", status.message());
            payload.put("startedAt", status.startedAt() == null ? null : status.startedAt().toString());
            payload.put("updatedAt", OffsetDateTime.now().toString());
            sharedConfigService.saveRuntimeStatus("runtime/bot-" + channelId + ".json", payload);
        } catch (RuntimeException ex) {
            log.warn("Failed to save shared bot runtime status for channel {}: {}", channelId, ex.getMessage());
        }
    }

    private java.util.Optional<BotProcessStatus> loadSharedStatus(Long channelId) {
        if (channelId == null) {
            return java.util.Optional.empty();
        }
        Map<String, Object> payload = sharedConfigService.loadRuntimeStatus("runtime/bot-" + channelId + ".json");
        String status = Objects.toString(payload.get("status"), "").trim();
        if (status.isEmpty()) {
            return java.util.Optional.empty();
        }
        OffsetDateTime sharedStartedAt = null;
        try {
            String rawStartedAt = Objects.toString(payload.get("startedAt"), "").trim();
            if (!rawStartedAt.isEmpty()) {
                sharedStartedAt = OffsetDateTime.parse(rawStartedAt);
            }
        } catch (RuntimeException ignored) {
            // Status remains useful even if an older runtime wrote an invalid timestamp.
        }
        return java.util.Optional.of("running".equalsIgnoreCase(status)
            ? BotProcessStatus.running(sharedStartedAt)
            : "stopped".equalsIgnoreCase(status) ? BotProcessStatus.stopped() : BotProcessStatus.error(status));
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

    Duration startupStabilityWindow() {
        long pollWindowMs = Math.max(startupPollInterval().toMillis() * 2L, MIN_STARTUP_STABILITY_WINDOW_MS);
        long timeoutCapMs = Math.max(250L, startupReadinessTimeout().toMillis() / 4L);
        return Duration.ofMillis(Math.min(pollWindowMs, timeoutCapMs));
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

    void releaseReservedServerPortIfOccupied(Channel channel) {
        Integer reservedPort = resolveReservedServerPort(channel);
        Long channelId = channel != null ? channel.getId() : null;
        if (reservedPort == null || channelId == null) {
            return;
        }
        Long listeningPid = resolveListeningPid(reservedPort);
        if (listeningPid == null) {
            return;
        }
        ProcessHandle handle = ProcessHandle.of(listeningPid).orElse(null);
        if (handle == null || !handle.isAlive()) {
            return;
        }
        if (!isRecoverableReservedPortOwner(handle, channel, reservedPort)) {
            log.warn(
                "Reserved MAX port {} for channel {} is occupied by pid {} and was not auto-recovered.",
                reservedPort,
                channelId,
                listeningPid
            );
            return;
        }
        log.warn(
            "Reserved MAX port {} for channel {} is occupied by stale pid {}, attempting recovery.",
            reservedPort,
            channelId,
            listeningPid
        );
        stopProcess(handle, "max-reserved-port-recovery", channelId);
        waitForPortRelease(reservedPort, Duration.ofSeconds(5));
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

    Integer resolveReservedServerPort(Channel channel) {
        if (channel == null || channel.getId() == null) {
            return null;
        }
        String platform = Objects.toString(channel.getPlatform(), "").trim().toLowerCase(Locale.ROOT);
        if (!"max".equals(platform)) {
            return null;
        }
        return botProcessProperties.resolveMaxPort(channel.getId());
    }

    Long resolveListeningPid(int port) {
        if (port <= 0 || port > 65535) {
            return null;
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("win")) {
                String output = runCommandAndCapture(List.of("cmd", "/c", "netstat -ano -p tcp"));
                return parseWindowsNetstatListeningPid(output, port);
            }
            Long lsofPid = parseNumericPid(runCommandAndCapture(List.of(
                "sh",
                "-lc",
                "lsof -nP -iTCP:" + port + " -sTCP:LISTEN -t 2>/dev/null | head -n 1"
            )));
            if (lsofPid != null) {
                return lsofPid;
            }
            String ssOutput = runCommandAndCapture(List.of(
                "sh",
                "-lc",
                "ss -ltnp '( sport = :" + port + " )' 2>/dev/null"
            ));
            return parseSsListeningPid(ssOutput, port);
        } catch (Exception ex) {
            log.debug("Failed to resolve listening pid for port {}", port, ex);
            return null;
        }
    }

    boolean isRecoverableReservedPortOwner(ProcessHandle handle, Channel channel, int reservedPort) {
        if (handle == null || channel == null || reservedPort <= 0) {
            return false;
        }
        String platform = Objects.toString(channel.getPlatform(), "").trim().toLowerCase(Locale.ROOT);
        if (!"max".equals(platform)) {
            return false;
        }
        String command = handle.info().command().orElse("");
        String[] arguments = handle.info().arguments().orElse(new String[0]);
        String fingerprint = (command + " " + String.join(" ", arguments)).trim().toLowerCase(Locale.ROOT);
        if (fingerprint.isBlank()) {
            return true;
        }
        return fingerprint.contains("bot-max")
            || fingerprint.contains("maxbotapplication")
            || fingerprint.contains("spring-boot:run")
            || fingerprint.contains("java");
    }

    static Long parseWindowsNetstatListeningPid(String output, int port) {
        if (output == null || output.isBlank() || port <= 0) {
            return null;
        }
        String[] lines = output.split("\\R");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || !trimmed.toUpperCase(Locale.ROOT).startsWith("TCP")) {
                continue;
            }
            String[] parts = trimmed.split("\\s+");
            if (parts.length < 5) {
                continue;
            }
            String state = parts[parts.length - 2];
            if (!"LISTENING".equalsIgnoreCase(state)) {
                continue;
            }
            Integer candidatePort = extractPort(parts[1]);
            if (candidatePort == null || candidatePort != port) {
                continue;
            }
            return parseNumericPid(parts[parts.length - 1]);
        }
        return null;
    }

    static Long parseSsListeningPid(String output, int port) {
        if (output == null || output.isBlank() || port <= 0) {
            return null;
        }
        String[] lines = output.split("\\R");
        Pattern pidPattern = Pattern.compile("pid=(\\d+)");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || !trimmed.contains("LISTEN")) {
                continue;
            }
            Integer candidatePort = extractPort(trimmed);
            if (candidatePort == null || candidatePort != port) {
                continue;
            }
            Matcher matcher = pidPattern.matcher(trimmed);
            if (matcher.find()) {
                return parseNumericPid(matcher.group(1));
            }
        }
        return null;
    }

    private static Integer extractPort(String value) {
        String normalized = Objects.toString(value, "").trim();
        if (normalized.isEmpty()) {
            return null;
        }
        int lastColon = normalized.lastIndexOf(':');
        if (lastColon < 0 || lastColon + 1 >= normalized.length()) {
            return null;
        }
        try {
            return Integer.parseInt(normalized.substring(lastColon + 1).replace("]", "").trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Long parseNumericPid(String raw) {
        String normalized = Objects.toString(raw, "").trim();
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(normalized);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String runCommandAndCapture(List<String> command) throws IOException, InterruptedException {
        List<String> safeCommand = command == null ? List.of() : new ArrayList<>(command);
        if (safeCommand.isEmpty()) {
            return "";
        }
        Process process = new ProcessBuilder(safeCommand)
            .redirectErrorStream(true)
            .start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() > 0) {
                    output.append(System.lineSeparator());
                }
                output.append(line);
            }
        }
        process.waitFor(5, TimeUnit.SECONDS);
        return output.toString();
    }

    private void waitForPortRelease(int port, Duration timeout) {
        Duration safeTimeout = timeout == null || timeout.isNegative() || timeout.isZero()
            ? Duration.ofSeconds(5)
            : timeout;
        long deadlineNanos = System.nanoTime() + safeTimeout.toNanos();
        while (System.nanoTime() < deadlineNanos) {
            if (resolveListeningPid(port) == null) {
                return;
            }
            try {
                Thread.sleep(100L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
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
            ProcessHandle handle = resolveProcessHandleFromPidFile(pidFile, channelId, false);
            if (handle != null) {
                stopProcess(handle, "pid-file", channelId);
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

    private ProcessHandle resolveProcessHandleFromPidFile(Path pidFile, Long channelId, boolean cleanupStalePidFile) {
        if (pidFile == null || !Files.exists(pidFile)) {
            return null;
        }
        try {
            String content = Files.readString(pidFile).trim();
            if (content.isEmpty()) {
                if (cleanupStalePidFile) {
                    Files.deleteIfExists(pidFile);
                }
                return null;
            }
            long pid = Long.parseLong(content);
            ProcessHandle handle = ProcessHandle.of(pid).orElse(null);
            if (handle != null && handle.isAlive()) {
                return handle;
            }
            if (cleanupStalePidFile) {
                Files.deleteIfExists(pidFile);
            }
        } catch (Exception ex) {
            log.warn("Failed to resolve process from pid file {} for channel {}", pidFile, channelId, ex);
            if (cleanupStalePidFile) {
                try {
                    Files.deleteIfExists(pidFile);
                } catch (IOException deleteEx) {
                    log.warn("Failed to delete stale pid file {} for channel {}", pidFile, channelId, deleteEx);
                }
            }
        }
        return null;
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
