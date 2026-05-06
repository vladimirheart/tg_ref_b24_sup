package com.example.panel.service;

import com.example.panel.service.LocationsIikoSyncSettingsService.LocationIikoSyncSettings;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class IikoDepartmentLocationsSyncService {

    private static final Logger log = LoggerFactory.getLogger(IikoDepartmentLocationsSyncService.class);
    private static final DateTimeFormatter UTC_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.UTC);

    private final IikoDepartmentLocationCatalogService locationCatalogService;
    private final SharedConfigService sharedConfigService;
    private final SettingsParameterService settingsParameterService;
    private final LocationsIikoSyncSettingsService locationsIikoSyncSettingsService;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "iiko-department-locations-sync");
        thread.setDaemon(true);
        return thread;
    });

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile SyncStatusSnapshot status = SyncStatusSnapshot.idle();
    private volatile Instant lastFinishedAt;

    public IikoDepartmentLocationsSyncService(IikoDepartmentLocationCatalogService locationCatalogService,
                                              SharedConfigService sharedConfigService,
                                              SettingsParameterService settingsParameterService,
                                              LocationsIikoSyncSettingsService locationsIikoSyncSettingsService) {
        this.locationCatalogService = locationCatalogService;
        this.sharedConfigService = sharedConfigService;
        this.settingsParameterService = settingsParameterService;
        this.locationsIikoSyncSettingsService = locationsIikoSyncSettingsService;
    }

    public SyncTriggerResponse triggerManualSync() {
        return triggerAsync("manual", true);
    }

    public void runScheduledSyncIfDue() {
        LocationIikoSyncSettings settings = locationsIikoSyncSettingsService.load(sharedConfigService.loadSettings());
        if (!settings.enabled()) {
            return;
        }
        if (running.get()) {
            return;
        }
        Instant now = Instant.now();
        if (lastFinishedAt != null && lastFinishedAt.plusSeconds(settings.intervalMinutes() * 60L).isAfter(now)) {
            return;
        }
        triggerAsync("schedule", true);
    }

    public SyncStatusSnapshot getStatus() {
        LocationIikoSyncSettings settings = locationsIikoSyncSettingsService.load(sharedConfigService.loadSettings());
        String nextRunAtUtc = null;
        if (settings.enabled() && lastFinishedAt != null) {
            nextRunAtUtc = formatUtc(lastFinishedAt.plusSeconds(settings.intervalMinutes() * 60L));
        }
        if (settings.enabled() && lastFinishedAt == null && !"running".equals(status.state())) {
            nextRunAtUtc = "after_startup_tick";
        }
        return status.withSchedule(settings.enabled(), settings.intervalMinutes(), nextRunAtUtc);
    }

    SyncStatusSnapshot syncNow(String trigger, boolean forceRefresh) {
        Instant startedAt = Instant.now();
        updateStatus(new SyncStatusSnapshot(
                "running",
                5,
                "Подготавливаем синхронизацию",
                trigger,
                formatUtc(startedAt),
                null,
                false,
                List.of(),
                null,
                true,
                0,
                null
        ));
        try {
            updateProgress(25, "Запрашиваем департаменты из iikoServer API");
            IikoDepartmentLocationCatalogService.LocationCatalogSnapshot snapshot = locationCatalogService.loadCatalog(forceRefresh);

            if (snapshot == null || !"iiko_api".equals(snapshot.source()) || snapshot.tree().isEmpty()) {
                SyncStatusSnapshot result = finish(
                        "skipped",
                        trigger,
                        startedAt,
                        false,
                        snapshot != null ? snapshot.warnings() : List.of(),
                        "Live-структура из iiko не получена"
                );
                return result;
            }

            updateProgress(60, "Сравниваем live-данные с текущим shared config");
            Map<String, Object> existingPayload = locationCatalogService.buildEffectiveLocationsPayload(null);
            Map<String, Object> effectivePayload = locationCatalogService.buildEffectiveLocationsPayload(snapshot);
            boolean changed = !Objects.equals(existingPayload, effectivePayload);

            if (changed) {
                updateProgress(85, "Сохраняем shared config и обновляем справочные параметры");
                sharedConfigService.saveLocations(effectivePayload);
                settingsParameterService.syncParametersFromLocationsPayload(effectivePayload);
            } else {
                updateProgress(85, "Изменений не найдено, сохранение не требуется");
            }

            SyncStatusSnapshot result = finish(
                    "success",
                    trigger,
                    startedAt,
                    changed,
                    snapshot.warnings(),
                    changed ? "Синхронизация завершена, данные обновлены" : "Синхронизация завершена, изменений нет"
            );
            log.info("iiko department locations sync completed: trigger={}, changed={}, warnings={}",
                    trigger,
                    changed,
                    snapshot.warnings().size());
            return result;
        } catch (Exception ex) {
            log.warn("iiko department locations sync failed: trigger={}", trigger, ex);
            SyncStatusSnapshot result = finish(
                    "error",
                    trigger,
                    startedAt,
                    false,
                    List.of(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()),
                    "Синхронизация завершилась ошибкой"
            );
            return result;
        } finally {
            lastFinishedAt = Instant.now();
            running.set(false);
        }
    }

    private SyncTriggerResponse triggerAsync(String trigger, boolean forceRefresh) {
        if (!running.compareAndSet(false, true)) {
            return new SyncTriggerResponse(false, getStatus());
        }
        Instant startedAt = Instant.now();
        updateStatus(new SyncStatusSnapshot(
                "running",
                2,
                "Ставим задачу синхронизации в очередь",
                trigger,
                formatUtc(startedAt),
                null,
                false,
                List.of(),
                status.lastSuccessAtUtc(),
                true,
                0,
                null
        ));
        executorService.submit(() -> syncNow(trigger, forceRefresh));
        return new SyncTriggerResponse(true, getStatus());
    }

    private void updateProgress(int progressPercent, String message) {
        SyncStatusSnapshot current = status;
        updateStatus(new SyncStatusSnapshot(
                "running",
                progressPercent,
                message,
                current.trigger(),
                current.startedAtUtc(),
                null,
                false,
                current.warnings(),
                null,
                true,
                0,
                null
        ));
    }

    private SyncStatusSnapshot finish(String state,
                                      String trigger,
                                      Instant startedAt,
                                      boolean changed,
                                      List<String> warnings,
                                      String message) {
        Instant finishedAt = Instant.now();
        SyncStatusSnapshot snapshot = new SyncStatusSnapshot(
                state,
                100,
                message,
                trigger,
                formatUtc(startedAt),
                formatUtc(finishedAt),
                changed,
                warnings == null ? List.of() : List.copyOf(warnings),
                "success".equals(state) ? formatUtc(finishedAt) : status.lastSuccessAtUtc(),
                false,
                0,
                null
        );
        updateStatus(snapshot);
        return snapshot;
    }

    private void updateStatus(SyncStatusSnapshot snapshot) {
        status = snapshot;
    }

    private String formatUtc(Instant instant) {
        return instant == null ? null : UTC_FORMATTER.format(instant);
    }

    @PreDestroy
    void shutdown() {
        executorService.shutdownNow();
    }

    public record SyncTriggerResponse(boolean started, SyncStatusSnapshot status) {
    }

    public record SyncStatusSnapshot(String state,
                                     int progressPercent,
                                     String message,
                                     String trigger,
                                     String startedAtUtc,
                                     String finishedAtUtc,
                                     boolean changed,
                                     List<String> warnings,
                                     String lastSuccessAtUtc,
                                     boolean running,
                                     int intervalMinutes,
                                     String nextRunAtUtc) {

        static SyncStatusSnapshot idle() {
            return new SyncStatusSnapshot(
                    "idle",
                    0,
                    "Синхронизация ещё не запускалась",
                    "",
                    null,
                    null,
                    false,
                    List.of(),
                    null,
                    false,
                    0,
                    null
            );
        }

        SyncStatusSnapshot withSchedule(boolean enabled, int intervalMinutes, String nextRunAtUtc) {
            String messageValue = message;
            if (!enabled && !"running".equals(state)) {
                messageValue = "Автосинхронизация выключена";
            }
            return new SyncStatusSnapshot(
                    enabled || "running".equals(state) ? state : "disabled",
                    progressPercent,
                    messageValue,
                    trigger,
                    startedAtUtc,
                    finishedAtUtc,
                    changed,
                    warnings,
                    lastSuccessAtUtc,
                    running,
                    intervalMinutes,
                    nextRunAtUtc
            );
        }
    }
}
