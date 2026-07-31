package com.example.panel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class AdminStorageInventoryService {

    private static final DateTimeFormatter REPORT_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final int DEFAULT_TOP = 5;
    private static final int MIN_TOP = 1;
    private static final int MAX_TOP = 20;

    private final ObjectMapper objectMapper;
    private final String pythonExecutable;
    private final String configuredRepositoryRoot;
    private final Duration timeout;

    public AdminStorageInventoryService(ObjectMapper objectMapper,
                                        @Value("${app.admin.python-executable:python}") String pythonExecutable,
                                        @Value("${app.admin.repository-root:}") String configuredRepositoryRoot,
                                        @Value("${app.admin.storage-inventory-timeout:PT90S}") Duration timeout) {
        this.objectMapper = objectMapper;
        this.pythonExecutable = StringUtils.hasText(pythonExecutable) ? pythonExecutable.trim() : "python";
        this.configuredRepositoryRoot = configuredRepositoryRoot == null ? "" : configuredRepositoryRoot.trim();
        this.timeout = timeout != null && !timeout.isNegative() && !timeout.isZero() ? timeout : Duration.ofSeconds(90);
    }

    public StorageInventoryRunResult runInventory(Integer requestedTop) throws IOException, InterruptedException {
        int top = normalizeTop(requestedTop);
        Path repositoryRoot = resolveRepositoryRoot();
        Path scriptPath = repositoryRoot.resolve("scripts").resolve("report-iguana-storage.py").normalize();
        if (!Files.exists(scriptPath) || !Files.isRegularFile(scriptPath)) {
            throw new IOException("Storage inventory script not found: " + scriptPath);
        }

        Path reportsRoot = repositoryRoot.resolve("run").resolve("storage-inventory").normalize();
        Files.createDirectories(reportsRoot);

        String timestamp = REPORT_TIMESTAMP.format(OffsetDateTime.now());
        Path markdownPath = reportsRoot.resolve(timestamp + "_storage-inventory.md");
        Path jsonPath = reportsRoot.resolve(timestamp + "_storage-inventory.json");
        Path latestMarkdownPath = reportsRoot.resolve("latest.md");
        Path latestJsonPath = reportsRoot.resolve("latest.json");

        List<String> command = List.of(
                pythonExecutable,
                scriptPath.toString(),
                "--top",
                Integer.toString(top),
                "--repo-root",
                repositoryRoot.toString(),
                "--markdown-out",
                markdownPath.toString(),
                "--json-out",
                jsonPath.toString()
        );

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(repositoryRoot.toFile());
        processBuilder.redirectErrorStream(true);
        processBuilder.environment().put("PYTHONUTF8", "1");
        processBuilder.environment().put("PYTHONIOENCODING", "UTF-8");

        long startedAt = System.nanoTime();
        Process process = processBuilder.start();
        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Storage inventory timed out after " + timeout.toSeconds() + " seconds.");
        }
        long durationMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();

        if (process.exitValue() != 0) {
            throw new IOException("Storage inventory script failed: " + output);
        }
        if (!Files.exists(jsonPath) || !Files.isRegularFile(jsonPath)) {
            throw new IOException("Storage inventory JSON report was not created: " + jsonPath);
        }

        Files.copy(markdownPath, latestMarkdownPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.copy(jsonPath, latestJsonPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        Map<String, Object> report = objectMapper.readValue(jsonPath.toFile(), MAP_TYPE);
        return new StorageInventoryRunResult(
                top,
                durationMs,
                repositoryRoot,
                scriptPath,
                output,
                markdownPath,
                latestMarkdownPath,
                jsonPath,
                latestJsonPath,
                report
        );
    }

    private int normalizeTop(Integer requestedTop) {
        if (requestedTop == null) {
            return DEFAULT_TOP;
        }
        return Math.max(MIN_TOP, Math.min(MAX_TOP, requestedTop));
    }

    private Path resolveRepositoryRoot() {
        if (StringUtils.hasText(configuredRepositoryRoot)) {
            Path configured = Path.of(configuredRepositoryRoot).toAbsolutePath().normalize();
            validateRepositoryRoot(configured);
            return configured;
        }

        Path current = Path.of("").toAbsolutePath().normalize();
        Path resolved = searchRepositoryRoot(current);
        if (resolved != null) {
            return resolved;
        }

        Path parent = current.getParent();
        resolved = searchRepositoryRoot(parent);
        if (resolved != null) {
            return resolved;
        }

        throw new IllegalStateException("Unable to resolve repository root for storage inventory.");
    }

    private Path searchRepositoryRoot(Path start) {
        Path cursor = start;
        while (cursor != null) {
            if (looksLikeRepositoryRoot(cursor)) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        return null;
    }

    private boolean looksLikeRepositoryRoot(Path candidate) {
        if (candidate == null) {
            return false;
        }
        Path scriptPath = candidate.resolve("scripts").resolve("report-iguana-storage.py").normalize();
        Path readmePath = candidate.resolve("README.md").normalize();
        return Files.exists(scriptPath) && Files.isRegularFile(scriptPath)
                && Files.exists(readmePath) && Files.isRegularFile(readmePath);
    }

    private void validateRepositoryRoot(Path candidate) {
        if (!looksLikeRepositoryRoot(candidate)) {
            throw new IllegalArgumentException("Configured APP_ADMIN_REPOSITORY_ROOT is invalid: " + candidate);
        }
    }

    public record StorageInventoryRunResult(int top,
                                            long durationMs,
                                            Path repositoryRoot,
                                            Path scriptPath,
                                            String markdownReport,
                                            Path markdownReportPath,
                                            Path latestMarkdownReportPath,
                                            Path jsonReportPath,
                                            Path latestJsonReportPath,
                                            Map<String, Object> report) {

        public Map<String, Object> toResponsePayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", true);
            payload.put("top", top);
            payload.put("duration_ms", durationMs);
            payload.put("repository_root", repositoryRoot.toString());
            payload.put("script_path", scriptPath.toString());
            payload.put("markdown_report", markdownReport);
            payload.put("markdown_report_path", markdownReportPath.toString());
            payload.put("latest_markdown_report_path", latestMarkdownReportPath.toString());
            payload.put("json_report_path", jsonReportPath.toString());
            payload.put("latest_json_report_path", latestJsonReportPath.toString());
            payload.put("report", report);
            return payload;
        }
    }
}
