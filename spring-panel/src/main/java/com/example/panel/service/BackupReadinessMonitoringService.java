package com.example.panel.service;

import com.example.panel.entity.BackupReadinessMonitor;
import com.example.panel.repository.BackupReadinessMonitorRepository;
import com.example.panel.repository.MonitoringCheckHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class BackupReadinessMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(BackupReadinessMonitoringService.class);

    public static final String STATUS_OK = "ok";
    public static final String STATUS_WARNING = "warning";
    public static final String STATUS_CRITICAL = "critical";
    public static final String STATUS_ERROR = "error";
    public static final String STATUS_DISABLED = "disabled";
    public static final String AVAILABILITY_UP = "up";
    public static final String AVAILABILITY_DOWN = "down";
    public static final String AVAILABILITY_DISABLED = "disabled";
    public static final String AVAILABILITY_UNKNOWN = "unknown";

    private static final String MONITOR_KIND = "backup_readiness";
    private static final String CHECK_KIND_PROBE = "backup_probe";
    private static final String CHECK_KIND_RESTORE = "restore_evidence";
    private static final int DEFAULT_FRESHNESS_THRESHOLD_HOURS = 24;
    private static final int DEFAULT_RESTORE_THRESHOLD_DAYS = 14;
    private static final int MAX_SUMMARY_LENGTH = 300;
    private static final int MAX_DETAILS_LENGTH = 1_200;
    private static final int MAX_NOTE_LENGTH = 1_000;

    private final BackupReadinessMonitorRepository repository;
    private final MonitoringCheckHistoryRepository historyRepository;

    public BackupReadinessMonitoringService(BackupReadinessMonitorRepository repository,
                                            MonitoringCheckHistoryRepository historyRepository) {
        this.repository = repository;
        this.historyRepository = historyRepository;
    }

    @Transactional(transactionManager = "monitoringTransactionManager", readOnly = true)
    public List<BackupReadinessMonitor> findAll() {
        return repository.findAllByOrderByMonitorNameAscIdAsc();
    }

    public BackupReadinessMonitor createMonitor(MonitorDraft draft) {
        MonitorDraft normalized = normalizeDraft(draft, null);
        repository.findByMonitorName(normalized.monitorName()).ifPresent(existing -> {
            throw new IllegalArgumentException("Монитор с таким именем уже существует");
        });

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        BackupReadinessMonitor item = new BackupReadinessMonitor();
        item.setMonitorName(normalized.monitorName());
        item.setBackupKind(normalized.backupKind());
        item.setPathPattern(normalized.pathPattern());
        item.setEnabled(normalized.enabled());
        item.setFreshnessThresholdHours(normalized.freshnessThresholdHours());
        item.setRestoreThresholdDays(normalized.restoreThresholdDays());
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        item.setLastStatus(Boolean.TRUE.equals(item.getEnabled()) ? STATUS_WARNING : STATUS_DISABLED);
        item.setLastSummary(Boolean.TRUE.equals(item.getEnabled())
            ? "Backup readiness monitor создан и ждёт первой проверки"
            : "Мониторинг отключен");
        repository.save(item);
        return refreshMonitor(item);
    }

    public BackupReadinessMonitor updateMonitor(long monitorId, MonitorDraft draft) {
        BackupReadinessMonitor item = repository.findById(monitorId)
            .orElseThrow(() -> new IllegalArgumentException("Монитор backup readiness не найден"));
        MonitorDraft normalized = normalizeDraft(draft, item);
        repository.findByMonitorName(normalized.monitorName()).ifPresent(existing -> {
            if (!existing.getId().equals(item.getId())) {
                throw new IllegalArgumentException("Монитор с таким именем уже существует");
            }
        });

        item.setMonitorName(normalized.monitorName());
        item.setBackupKind(normalized.backupKind());
        item.setPathPattern(normalized.pathPattern());
        item.setEnabled(normalized.enabled());
        item.setFreshnessThresholdHours(normalized.freshnessThresholdHours());
        item.setRestoreThresholdDays(normalized.restoreThresholdDays());
        item.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        repository.save(item);
        return refreshMonitor(item);
    }

    public void deleteMonitor(long monitorId) {
        if (!repository.existsById(monitorId)) {
            throw new IllegalArgumentException("Монитор backup readiness не найден");
        }
        repository.deleteById(monitorId);
    }

    public BackupReadinessMonitor refreshById(long monitorId) {
        BackupReadinessMonitor item = repository.findById(monitorId)
            .orElseThrow(() -> new IllegalArgumentException("Монитор backup readiness не найден"));
        return refreshMonitor(item);
    }

    public RefreshSummary refreshAll() {
        List<BackupReadinessMonitor> monitors = repository.findAllByOrderByMonitorNameAscIdAsc();
        int checked = 0;
        for (BackupReadinessMonitor monitor : monitors) {
            refreshMonitor(monitor);
            checked++;
        }
        return new RefreshSummary(monitors.size(), checked);
    }

    public BackupReadinessMonitor confirmRestoreEvidence(long monitorId, RestoreEvidenceDraft draft) {
        BackupReadinessMonitor item = repository.findById(monitorId)
            .orElseThrow(() -> new IllegalArgumentException("Монитор backup readiness не найден"));
        RestoreEvidenceDraft normalized = normalizeRestoreDraft(draft);
        item.setLastRestoreVerifiedAt(normalized.verifiedAt());
        item.setLastRestoreNote(normalized.note());
        item.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        repository.save(item);
        recordHistory(
            item.getId(),
            CHECK_KIND_RESTORE,
            STATUS_OK,
            "Restore evidence подтверждён вручную",
            buildRestoreEvidenceDetails(item),
            null,
            normalized.verifiedAt()
        );
        return refreshMonitor(item);
    }

    public List<MonitoringCheckHistoryRepository.HistoryEntry> loadHistory(long monitorId, int limit) {
        if (!repository.existsById(monitorId)) {
            throw new IllegalArgumentException("Монитор backup readiness не найден");
        }
        return historyRepository.findRecent(MONITOR_KIND, monitorId, limit);
    }

    public String resolveSeverity(BackupReadinessMonitor item) {
        if (item == null) {
            return STATUS_ERROR;
        }
        String normalized = normalizeStatus(item.getLastStatus());
        if (!normalized.isEmpty()) {
            return normalized;
        }
        if (!Boolean.TRUE.equals(item.getEnabled())) {
            return STATUS_DISABLED;
        }
        return STATUS_WARNING;
    }

    public String resolveAvailability(BackupReadinessMonitor item) {
        if (item == null) {
            return AVAILABILITY_UNKNOWN;
        }
        if (!Boolean.TRUE.equals(item.getEnabled())) {
            return AVAILABILITY_DISABLED;
        }
        String status = resolveSeverity(item);
        if (STATUS_OK.equals(status) || STATUS_WARNING.equals(status)) {
            return AVAILABILITY_UP;
        }
        if (STATUS_CRITICAL.equals(status) || STATUS_ERROR.equals(status)) {
            return AVAILABILITY_DOWN;
        }
        return AVAILABILITY_UNKNOWN;
    }

    public AvailabilityOverview buildAvailabilityOverview(List<BackupReadinessMonitor> monitors) {
        int total = 0;
        int up = 0;
        int down = 0;
        int unknown = 0;
        int disabled = 0;
        if (monitors != null) {
            for (BackupReadinessMonitor monitor : monitors) {
                total++;
                String availability = resolveAvailability(monitor);
                if (AVAILABILITY_UP.equals(availability)) {
                    up++;
                } else if (AVAILABILITY_DOWN.equals(availability)) {
                    down++;
                } else if (AVAILABILITY_DISABLED.equals(availability)) {
                    disabled++;
                } else {
                    unknown++;
                }
            }
        }
        int active = Math.max(0, total - disabled);
        double availabilityPercent = active == 0 ? 0d : (up * 100.0d / active);
        return new AvailabilityOverview(total, up, down, unknown, disabled, Math.round(availabilityPercent * 10.0d) / 10.0d);
    }

    private BackupReadinessMonitor refreshMonitor(BackupReadinessMonitor item) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        long startedAtNs = System.nanoTime();
        item.setLastCheckedAt(now);
        item.setUpdatedAt(now);

        if (!Boolean.TRUE.equals(item.getEnabled())) {
            item.setLastStatus(STATUS_DISABLED);
            item.setLastSummary("Мониторинг отключен");
            item.setLastErrorMessage(null);
            repository.save(item);
            recordHistory(item.getId(), CHECK_KIND_PROBE, STATUS_DISABLED, item.getLastSummary(), buildProbeDetails(item), elapsedMillis(startedAtNs), now);
            return item;
        }

        try {
            ArtifactSnapshot artifact = resolveArtifact(item.getPathPattern());
            item.setLastBackupAt(artifact.lastModifiedAt());
            item.setLastBackupSizeBytes(artifact.sizeBytes());
            item.setLastBackupPath(artifact.absolutePath());

            String backupSeverity = evaluateBackupSeverity(item, now);
            String restoreSeverity = evaluateRestoreSeverity(item, now);
            String overallSeverity = combineSeverity(backupSeverity, restoreSeverity);
            String summary = trim(buildSummary(item, backupSeverity, restoreSeverity, now), MAX_SUMMARY_LENGTH);

            item.setLastStatus(overallSeverity);
            item.setLastSummary(summary);
            item.setLastErrorMessage(null);
            repository.save(item);
            recordHistory(item.getId(), CHECK_KIND_PROBE, overallSeverity, summary, buildProbeDetails(item), elapsedMillis(startedAtNs), now);
            return item;
        } catch (Exception ex) {
            item.setLastStatus(STATUS_ERROR);
            item.setLastSummary("Не удалось проверить backup artifact");
            item.setLastErrorMessage(trimErrorMessage(ex.getMessage()));
            item.setLastBackupAt(null);
            item.setLastBackupSizeBytes(null);
            item.setLastBackupPath(null);
            repository.save(item);
            recordHistory(item.getId(), CHECK_KIND_PROBE, STATUS_ERROR, item.getLastSummary(), buildProbeDetails(item), elapsedMillis(startedAtNs), now);
            log.warn("Backup readiness probe failed for {}: {}", item.getMonitorName(), ex.getMessage());
            return item;
        }
    }

    private MonitorDraft normalizeDraft(MonitorDraft draft, BackupReadinessMonitor existing) {
        if (draft == null) {
            throw new IllegalArgumentException("Параметры монитора не переданы");
        }
        String monitorName = normalizeRequired(draft.monitorName(), "Укажите имя монитора");
        String backupKind = normalize(draft.backupKind());
        if (backupKind == null) {
            backupKind = existing != null && StringUtils.hasText(existing.getBackupKind())
                ? existing.getBackupKind().trim()
                : "generic";
        }
        String pathPattern = normalizeRequired(draft.pathPattern(), "Укажите путь к backup artifact или каталог");
        int freshnessThresholdHours = normalizeThreshold(
            draft.freshnessThresholdHours(),
            existing != null ? existing.getFreshnessThresholdHours() : DEFAULT_FRESHNESS_THRESHOLD_HOURS,
            1,
            24 * 365,
            "freshness_threshold_hours"
        );
        int restoreThresholdDays = normalizeThreshold(
            draft.restoreThresholdDays(),
            existing != null ? existing.getRestoreThresholdDays() : DEFAULT_RESTORE_THRESHOLD_DAYS,
            1,
            3650,
            "restore_threshold_days"
        );
        boolean enabled = draft.enabled() == null
            ? existing == null || Boolean.TRUE.equals(existing.getEnabled())
            : draft.enabled();
        return new MonitorDraft(monitorName, backupKind, pathPattern, enabled, freshnessThresholdHours, restoreThresholdDays);
    }

    private RestoreEvidenceDraft normalizeRestoreDraft(RestoreEvidenceDraft draft) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (draft == null) {
            return new RestoreEvidenceDraft(now, null);
        }
        OffsetDateTime verifiedAt = draft.verifiedAt() != null ? draft.verifiedAt() : now;
        if (verifiedAt.isAfter(now.plusMinutes(5))) {
            throw new IllegalArgumentException("verified_at не может быть в будущем");
        }
        String note = normalize(draft.note());
        if (note != null && note.length() > MAX_NOTE_LENGTH) {
            throw new IllegalArgumentException("restore note не должен превышать 1000 символов");
        }
        return new RestoreEvidenceDraft(verifiedAt.withOffsetSameInstant(ZoneOffset.UTC), note);
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private int normalizeThreshold(Integer value, Integer fallback, int min, int max, String fieldName) {
        int candidate = value != null ? value : (fallback != null ? fallback : min);
        if (candidate < min || candidate > max) {
            throw new IllegalArgumentException(fieldName + " должен быть в диапазоне " + min + "-" + max);
        }
        return candidate;
    }

    private ArtifactSnapshot resolveArtifact(String rawPattern) throws IOException {
        Path candidate = toPath(rawPattern);
        if (containsGlob(rawPattern)) {
            Path parent = candidate.getParent();
            if (parent == null || !Files.isDirectory(parent)) {
                throw new IllegalStateException("Каталог для glob-паттерна backup artifact не найден");
            }
            String filePattern = candidate.getFileName() != null ? candidate.getFileName().toString() : "*";
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + filePattern);
            return newestRegularFile(parent, path -> matcher.matches(path.getFileName()))
                .orElseThrow(() -> new IllegalStateException("По glob-паттерну не найден ни один backup artifact"));
        }

        if (!Files.exists(candidate)) {
            throw new IllegalStateException("Указанный путь backup artifact не существует");
        }
        if (Files.isRegularFile(candidate)) {
            return toSnapshot(candidate);
        }
        if (Files.isDirectory(candidate)) {
            return newestRegularFile(candidate, path -> true)
                .orElseThrow(() -> new IllegalStateException("В каталоге не найден ни один регулярный backup artifact"));
        }
        throw new IllegalStateException("Поддерживаются только регулярные файлы, каталоги и glob-паттерны");
    }

    private Path toPath(String rawPattern) {
        try {
            return Path.of(rawPattern).toAbsolutePath().normalize();
        } catch (InvalidPathException ex) {
            throw new IllegalArgumentException("Некорректный путь backup artifact");
        }
    }

    private boolean containsGlob(String rawPattern) {
        return rawPattern != null && (rawPattern.contains("*") || rawPattern.contains("?"));
    }

    private Optional<ArtifactSnapshot> newestRegularFile(Path directory, java.util.function.Predicate<Path> predicate) throws IOException {
        try (Stream<Path> stream = Files.list(directory)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(predicate)
                .map(this::toSnapshotUnchecked)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .max(Comparator.comparing(ArtifactSnapshot::lastModifiedAt));
        }
    }

    private Optional<ArtifactSnapshot> toSnapshotUnchecked(Path path) {
        try {
            return Optional.of(toSnapshot(path));
        } catch (IOException ex) {
            log.debug("Skipping unreadable backup artifact {}: {}", path, ex.getMessage());
            return Optional.empty();
        }
    }

    private ArtifactSnapshot toSnapshot(Path path) throws IOException {
        FileTime lastModified = Files.getLastModifiedTime(path);
        long sizeBytes = Files.size(path);
        return new ArtifactSnapshot(
            path.toAbsolutePath().normalize().toString(),
            lastModified.toInstant().atOffset(ZoneOffset.UTC),
            sizeBytes
        );
    }

    private String evaluateBackupSeverity(BackupReadinessMonitor item, OffsetDateTime now) {
        OffsetDateTime lastBackupAt = item.getLastBackupAt();
        if (lastBackupAt == null) {
            return STATUS_CRITICAL;
        }
        double ageHours = ageHours(lastBackupAt, now);
        double threshold = Optional.ofNullable(item.getFreshnessThresholdHours()).orElse(DEFAULT_FRESHNESS_THRESHOLD_HOURS);
        if (ageHours > threshold) {
            return STATUS_CRITICAL;
        }
        if (ageHours > threshold * 0.75d) {
            return STATUS_WARNING;
        }
        return STATUS_OK;
    }

    private String evaluateRestoreSeverity(BackupReadinessMonitor item, OffsetDateTime now) {
        OffsetDateTime restoreVerifiedAt = item.getLastRestoreVerifiedAt();
        if (restoreVerifiedAt == null) {
            return STATUS_CRITICAL;
        }
        double ageDays = ageDays(restoreVerifiedAt, now);
        double threshold = Optional.ofNullable(item.getRestoreThresholdDays()).orElse(DEFAULT_RESTORE_THRESHOLD_DAYS);
        if (ageDays > threshold) {
            return STATUS_CRITICAL;
        }
        if (ageDays > threshold * 0.75d) {
            return STATUS_WARNING;
        }
        return STATUS_OK;
    }

    private double ageHours(OffsetDateTime timestamp, OffsetDateTime now) {
        if (timestamp == null) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.max(0d, Duration.between(timestamp, now).toMinutes() / 60.0d);
    }

    private double ageDays(OffsetDateTime timestamp, OffsetDateTime now) {
        if (timestamp == null) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.max(0d, Duration.between(timestamp, now).toMinutes() / 1440.0d);
    }

    private String combineSeverity(String left, String right) {
        Map<String, Integer> rank = Map.of(
            STATUS_OK, 1,
            STATUS_WARNING, 2,
            STATUS_CRITICAL, 3,
            STATUS_ERROR, 4,
            STATUS_DISABLED, 0
        );
        String normalizedLeft = normalizeStatus(left);
        String normalizedRight = normalizeStatus(right);
        return rank.getOrDefault(normalizedLeft, 4) >= rank.getOrDefault(normalizedRight, 4) ? normalizedLeft : normalizedRight;
    }

    private String normalizeStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String buildSummary(BackupReadinessMonitor item,
                                String backupSeverity,
                                String restoreSeverity,
                                OffsetDateTime now) {
        List<String> fragments = new ArrayList<>();
        OffsetDateTime lastBackupAt = item.getLastBackupAt();
        if (STATUS_CRITICAL.equals(backupSeverity)) {
            fragments.add("backup stale: " + formatHours(ageHours(lastBackupAt, now)) + " при пороге " + item.getFreshnessThresholdHours() + "ч");
        } else if (STATUS_WARNING.equals(backupSeverity)) {
            fragments.add("backup приближается к stale: " + formatHours(ageHours(lastBackupAt, now)));
        } else {
            fragments.add("backup fresh");
        }

        OffsetDateTime lastRestoreVerifiedAt = item.getLastRestoreVerifiedAt();
        if (lastRestoreVerifiedAt == null) {
            fragments.add("restore evidence отсутствует");
        } else if (STATUS_CRITICAL.equals(restoreSeverity)) {
            fragments.add("restore evidence stale: " + formatDays(ageDays(lastRestoreVerifiedAt, now)) + " при пороге " + item.getRestoreThresholdDays() + "д");
        } else if (STATUS_WARNING.equals(restoreSeverity)) {
            fragments.add("restore evidence скоро устареет: " + formatDays(ageDays(lastRestoreVerifiedAt, now)));
        } else {
            fragments.add("restore evidence актуален");
        }
        return String.join("; ", fragments);
    }

    private String buildProbeDetails(BackupReadinessMonitor item) {
        List<String> fragments = new ArrayList<>();
        if (item.getLastBackupPath() != null) {
            fragments.add("artifact=" + item.getLastBackupPath());
        }
        if (item.getLastBackupAt() != null) {
            fragments.add("backup_at=" + item.getLastBackupAt());
        }
        if (item.getLastBackupSizeBytes() != null) {
            fragments.add("backup_size_bytes=" + item.getLastBackupSizeBytes());
        }
        fragments.add("freshness_threshold_hours=" + item.getFreshnessThresholdHours());
        if (item.getLastRestoreVerifiedAt() != null) {
            fragments.add("restore_verified_at=" + item.getLastRestoreVerifiedAt());
        } else {
            fragments.add("restore_verified_at=missing");
        }
        if (StringUtils.hasText(item.getLastRestoreNote())) {
            fragments.add("restore_note=" + item.getLastRestoreNote().trim());
        }
        if (StringUtils.hasText(item.getLastErrorMessage())) {
            fragments.add("error=" + item.getLastErrorMessage().trim());
        }
        return trim(String.join("; ", fragments), MAX_DETAILS_LENGTH);
    }

    private String buildRestoreEvidenceDetails(BackupReadinessMonitor item) {
        List<String> fragments = new ArrayList<>();
        fragments.add("restore_verified_at=" + item.getLastRestoreVerifiedAt());
        if (StringUtils.hasText(item.getLastRestoreNote())) {
            fragments.add("restore_note=" + item.getLastRestoreNote().trim());
        }
        if (StringUtils.hasText(item.getLastBackupPath())) {
            fragments.add("artifact=" + item.getLastBackupPath().trim());
        }
        return trim(String.join("; ", fragments), MAX_DETAILS_LENGTH);
    }

    private void recordHistory(Long monitorId,
                               String checkKind,
                               String status,
                               String summary,
                               String details,
                               Long durationMs,
                               OffsetDateTime createdAt) {
        if (monitorId == null) {
            return;
        }
        historyRepository.record(
            MONITOR_KIND,
            monitorId,
            checkKind,
            status,
            trim(summary, MAX_SUMMARY_LENGTH),
            trim(details, MAX_DETAILS_LENGTH),
            null,
            durationMs,
            createdAt
        );
    }

    private String formatHours(double value) {
        return String.format(Locale.US, "%.1fч", value);
    }

    private String formatDays(double value) {
        return String.format(Locale.US, "%.1fд", value);
    }

    private String trimErrorMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return "Не удалось прочитать backup artifact";
        }
        return trim(message.trim(), MAX_SUMMARY_LENGTH);
    }

    private String trim(String value, int limit) {
        if (!StringUtils.hasText(value)) {
            return value == null ? null : value.trim();
        }
        String normalized = value.trim();
        if (normalized.length() <= limit) {
            return normalized;
        }
        return normalized.substring(0, limit);
    }

    private long elapsedMillis(long startedAtNs) {
        return Math.max(0L, (System.nanoTime() - startedAtNs) / 1_000_000L);
    }

    public record MonitorDraft(String monitorName,
                               String backupKind,
                               String pathPattern,
                               Boolean enabled,
                               Integer freshnessThresholdHours,
                               Integer restoreThresholdDays) {
    }

    public record RestoreEvidenceDraft(OffsetDateTime verifiedAt, String note) {
    }

    public record RefreshSummary(int total, int checked) {
    }

    public record AvailabilityOverview(int total,
                                       int up,
                                       int down,
                                       int unknown,
                                       int disabled,
                                       double availabilityPercent) {
    }

    private record ArtifactSnapshot(String absolutePath, OffsetDateTime lastModifiedAt, long sizeBytes) {
    }
}
