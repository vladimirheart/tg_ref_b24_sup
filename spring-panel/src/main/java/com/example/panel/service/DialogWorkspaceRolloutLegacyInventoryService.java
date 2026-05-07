package com.example.panel.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class DialogWorkspaceRolloutLegacyInventoryService {

    private final DialogWorkspaceRolloutGovernanceConfigService configService;

    public DialogWorkspaceRolloutLegacyInventoryService(DialogWorkspaceRolloutGovernanceConfigService configService) {
        this.configService = configService;
    }

    public DialogWorkspaceRolloutSectionResult buildLegacyInventory(DialogWorkspaceRolloutGovernanceConfig config,
                                                                    String generatedAtUtc) {
        boolean enabled = config.packetRequired() || !config.legacyOnlyScenarios().isEmpty();
        OffsetDateTime reviewedAt = configService.parseReviewTimestamp(config.legacyInventoryReviewedAtRaw());
        boolean reviewTimestampInvalid = StringUtils.hasText(configService.normalizeNullString(config.legacyInventoryReviewedAtRaw()))
                && reviewedAt == null;
        Instant now = Instant.now();

        List<Map<String, Object>> scenarioDetails = config.legacyOnlyScenarios().stream()
                .map(scenario -> {
                    Map<String, Object> metadata = config.legacyOnlyScenarioMetadata()
                            .getOrDefault(scenario.toLowerCase(java.util.Locale.ROOT), Map.of());
                    String owner = configService.normalizeNullString(String.valueOf(metadata.get("owner")));
                    String deadlineAt = configService.normalizeNullString(String.valueOf(metadata.get("deadline_at_utc")));
                    boolean deadlineTimestampInvalid = configService.toBoolean(metadata.get("deadline_timestamp_invalid"));
                    Instant deadlineInstant = null;
                    if (StringUtils.hasText(deadlineAt)) {
                        OffsetDateTime parsedDeadline = configService.parseReviewTimestamp(deadlineAt);
                        if (parsedDeadline != null) {
                            deadlineInstant = parsedDeadline.toInstant();
                        } else {
                            deadlineTimestampInvalid = true;
                        }
                    }
                    boolean deadlinePresent = StringUtils.hasText(deadlineAt);
                    boolean deadlineOverdue = deadlineInstant != null && deadlineInstant.isBefore(now);
                    boolean ownerReady = StringUtils.hasText(owner);
                    boolean ready = ownerReady && deadlinePresent && !deadlineTimestampInvalid;
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("scenario", scenario);
                    item.put("owner", owner == null ? "" : owner);
                    item.put("owner_ready", ownerReady);
                    item.put("deadline_at_utc", deadlineAt == null ? "" : deadlineAt);
                    item.put("deadline_present", deadlinePresent);
                    item.put("deadline_timestamp_invalid", deadlineTimestampInvalid);
                    item.put("deadline_overdue", deadlineOverdue);
                    item.put("ready", ready);
                    item.put("note", configService.normalizeNullString(String.valueOf(metadata.get("note"))));
                    return item;
                })
                .toList();
        long ownersReady = scenarioDetails.stream().filter(item -> configService.toBoolean(item.get("owner_ready"))).count();
        long deadlinesReady = scenarioDetails.stream().filter(item -> configService.toBoolean(item.get("deadline_present")) && !configService.toBoolean(item.get("deadline_timestamp_invalid"))).count();
        long deadlineInvalidCount = scenarioDetails.stream().filter(item -> configService.toBoolean(item.get("deadline_timestamp_invalid"))).count();
        long deadlineOverdueCount = scenarioDetails.stream().filter(item -> configService.toBoolean(item.get("deadline_overdue"))).count();
        List<String> overdueScenarios = scenarioDetails.stream()
                .filter(item -> configService.toBoolean(item.get("deadline_overdue")))
                .map(item -> configService.normalizeNullString(String.valueOf(item.get("scenario"))))
                .filter(StringUtils::hasText)
                .toList();
        long managedScenarioCount = scenarioDetails.stream()
                .filter(item -> configService.toBoolean(item.get("ready")) && !configService.toBoolean(item.get("deadline_overdue")))
                .count();
        long unmanagedScenarioCount = Math.max(0, config.legacyOnlyScenarios().size() - managedScenarioCount);
        List<String> reviewQueueScenarios = scenarioDetails.stream()
                .filter(item -> !configService.toBoolean(item.get("ready"))
                        || configService.toBoolean(item.get("deadline_overdue"))
                        || configService.toBoolean(item.get("deadline_timestamp_invalid")))
                .map(item -> configService.normalizeNullString(String.valueOf(item.get("scenario"))))
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        Instant reviewQueueOldestDeadline = scenarioDetails.stream()
                .filter(item -> reviewQueueScenarios.contains(String.valueOf(item.get("scenario"))))
                .map(item -> configService.normalizeNullString(String.valueOf(item.get("deadline_at_utc"))))
                .filter(StringUtils::hasText)
                .map(value -> {
                    try {
                        return Instant.parse(value);
                    } catch (Exception ignored) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .min(Instant::compareTo)
                .orElse(null);
        boolean ready = config.legacyOnlyScenarios().isEmpty();
        boolean managed = !ready && unmanagedScenarioCount == 0 && deadlineInvalidCount == 0 && deadlineOverdueCount == 0;
        String status = !enabled ? "off" : ready ? "ok" : (managed ? "attention" : "hold");

        long ownerCoveragePct = config.legacyOnlyScenarios().isEmpty() ? 100L
                : Math.round((ownersReady * 100d) / config.legacyOnlyScenarios().size());
        long deadlineCoveragePct = config.legacyOnlyScenarios().isEmpty() ? 100L
                : Math.round((deadlinesReady * 100d) / config.legacyOnlyScenarios().size());
        long managedCoveragePct = config.legacyOnlyScenarios().isEmpty() ? 100L
                : Math.round((managedScenarioCount * 100d) / config.legacyOnlyScenarios().size());
        long overduePct = config.legacyOnlyScenarios().isEmpty() ? 0L
                : Math.round((deadlineOverdueCount * 100d) / config.legacyOnlyScenarios().size());
        long reviewAgeHours = reviewedAt != null
                ? Math.max(0L, Duration.between(reviewedAt, OffsetDateTime.now(ZoneOffset.UTC)).toHours())
                : -1L;
        long repeatCadenceDays = config.reviewCadenceDays() > 0 ? config.reviewCadenceDays() : 7L;
        OffsetDateTime repeatReviewDueAt = reviewedAt != null ? reviewedAt.plusDays(repeatCadenceDays) : null;
        boolean reviewFresh = config.legacyOnlyScenarios().isEmpty()
                || (reviewedAt != null && reviewAgeHours <= repeatCadenceDays * 24L && !reviewTimestampInvalid);
        long repeatReviewOverdueDays = !config.legacyOnlyScenarios().isEmpty()
                && repeatReviewDueAt != null
                && repeatReviewDueAt.isBefore(OffsetDateTime.now(ZoneOffset.UTC))
                ? Math.max(0L, Duration.between(repeatReviewDueAt, OffsetDateTime.now(ZoneOffset.UTC)).toDays())
                : 0L;
        boolean repeatReviewRequired = !config.legacyOnlyScenarios().isEmpty()
                && (reviewedAt == null || reviewAgeHours > repeatCadenceDays * 24L || deadlineOverdueCount > 0);
        String repeatReviewReason = deadlineOverdueCount > 0
                ? "overdue_commitments"
                : reviewedAt == null ? "review_missing"
                : reviewAgeHours > repeatCadenceDays * 24L ? "review_stale" : "";
        long reviewQueueRepeatCycles = !reviewQueueScenarios.isEmpty() && repeatCadenceDays > 0
                ? Math.max(1L, Math.max(
                repeatReviewOverdueDays > 0 ? 1L + (repeatReviewOverdueDays / Math.max(1L, repeatCadenceDays)) : 0L,
                reviewedAt != null && reviewAgeHours > 0
                        ? Math.max(0L, reviewAgeHours / Math.max(24L, repeatCadenceDays * 24L))
                        : 0L))
                : 0L;
        long reviewQueueOldestOverdueDays = reviewQueueOldestDeadline != null && reviewQueueOldestDeadline.isBefore(now)
                ? Math.max(0L, Duration.between(reviewQueueOldestDeadline, now).toDays())
                : 0L;
        boolean reviewQueueFollowupRequired = !reviewQueueScenarios.isEmpty()
                && (repeatReviewRequired || deadlineOverdueCount > 0 || deadlineInvalidCount > 0 || reviewQueueRepeatCycles > 1);
        List<String> escalatedScenarios = scenarioDetails.stream()
                .filter(item -> reviewQueueScenarios.contains(String.valueOf(item.get("scenario"))))
                .filter(item -> configService.toBoolean(item.get("deadline_overdue"))
                        || configService.toBoolean(item.get("deadline_timestamp_invalid"))
                        || reviewQueueRepeatCycles >= 3)
                .map(item -> configService.normalizeNullString(String.valueOf(item.get("scenario"))))
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        boolean escalationRequired = !escalatedScenarios.isEmpty() || reviewQueueOldestOverdueDays >= 7L;
        long escalatedCount = escalatedScenarios.size();
        List<String> consolidationCandidates = scenarioDetails.stream()
                .filter(item -> reviewQueueScenarios.contains(String.valueOf(item.get("scenario"))))
                .filter(item -> !configService.toBoolean(item.get("owner_ready"))
                        || !configService.toBoolean(item.get("deadline_present"))
                        || configService.toBoolean(item.get("deadline_timestamp_invalid")))
                .map(item -> configService.normalizeNullString(String.valueOf(item.get("scenario"))))
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        boolean consolidationRequired = !consolidationCandidates.isEmpty() && reviewQueueRepeatCycles > 1;
        String closurePressure = reviewQueueScenarios.isEmpty()
                ? "none"
                : escalationRequired ? "high" : reviewQueueFollowupRequired ? "moderate" : "controlled";
        String managementReviewSummary = !escalationRequired
                ? ""
                : "Management review нужен для %d queue-сценария(ев); oldest overdue=%dд."
                .formatted(Math.max(1L, escalatedCount), Math.max(0L, reviewQueueOldestOverdueDays));
        String reviewQueueSummary = reviewQueueScenarios.isEmpty()
                ? ""
                : reviewQueueFollowupRequired
                ? "В weekly closure review остаются %d сценария(ев); oldest due=%s; repeat cycles=%d."
                .formatted(reviewQueueScenarios.size(),
                        reviewQueueOldestDeadline != null ? reviewQueueOldestDeadline.toString() : "n/a",
                        reviewQueueRepeatCycles)
                : "Review queue под контролем: %d сценария(ев) ещё в работе.".formatted(reviewQueueScenarios.size());
        List<String> actionItems = new ArrayList<>();
        if (!config.legacyOnlyScenarios().isEmpty()) {
            if (ownersReady < config.legacyOnlyScenarios().size()) {
                actionItems.add("Назначьте owner для всех legacy-only сценариев.");
            }
            if (deadlinesReady < config.legacyOnlyScenarios().size() || deadlineInvalidCount > 0) {
                actionItems.add("Заполните корректные UTC sunset deadline для каждого открытого сценария.");
            }
            if (deadlineOverdueCount > 0) {
                actionItems.add("Закройте или перепланируйте просроченные sunset commitments.");
            }
            if (!StringUtils.hasText(config.legacyInventoryReviewedBy()) || reviewedAt == null) {
                actionItems.add("Зафиксируйте последний UTC review owner/deadline inventory.");
            }
            if (escalationRequired) {
                actionItems.add("Эскалируйте долгоживущие legacy review-queue сценарии на management review.");
            }
            if (consolidationRequired) {
                actionItems.add("Сконсолидируйте queue-сценарии без owner/deadline в единый weekly closure plan.");
            }
            if (reviewQueueFollowupRequired) {
                actionItems.add("Закройте weekly closure-loop для сценариев, которые повторно остаются в legacy review-queue.");
            }
        }
        String nextActionSummary = !actionItems.isEmpty()
                ? actionItems.get(0)
                : (reviewQueueScenarios.isEmpty()
                ? "Legacy review-queue не требует follow-up."
                : "Продолжайте weekly closure review без дополнительных escalation.");

        Set<String> reviewQueueScenarioSet = new LinkedHashSet<>(reviewQueueScenarios);
        Set<String> escalatedScenarioSet = new LinkedHashSet<>(escalatedScenarios);
        Set<String> consolidationSet = new LinkedHashSet<>(consolidationCandidates);
        List<Map<String, Object>> enrichedScenarioDetails = scenarioDetails.stream()
                .map(item -> {
                    Map<String, Object> enriched = new LinkedHashMap<>(item);
                    String scenario = String.valueOf(item.getOrDefault("scenario", ""));
                    enriched.put("queue_candidate", reviewQueueScenarioSet.contains(scenario));
                    enriched.put("escalation_candidate", escalatedScenarioSet.contains(scenario));
                    enriched.put("consolidation_candidate", consolidationSet.contains(scenario));
                    return enriched;
                })
                .toList();

        Map<String, Object> payload = Map.ofEntries(
                Map.entry("status", status),
                Map.entry("ready", ready),
                Map.entry("managed", managed),
                Map.entry("reviewed_by", config.legacyInventoryReviewedBy() == null ? "" : config.legacyInventoryReviewedBy()),
                Map.entry("reviewed_at", reviewedAt != null ? reviewedAt.toString() : ""),
                Map.entry("review_note", config.legacyInventoryReviewNote() == null ? "" : config.legacyInventoryReviewNote()),
                Map.entry("review_age_hours", reviewAgeHours),
                Map.entry("review_timestamp_invalid", reviewTimestampInvalid),
                Map.entry("repeat_review_cadence_days", repeatCadenceDays),
                Map.entry("review_fresh", reviewFresh),
                Map.entry("repeat_review_due_at_utc", repeatReviewDueAt != null ? repeatReviewDueAt.toString() : ""),
                Map.entry("repeat_review_overdue_days", repeatReviewOverdueDays),
                Map.entry("repeat_review_required", repeatReviewRequired),
                Map.entry("repeat_review_reason", repeatReviewReason),
                Map.entry("review_queue_followup_required", reviewQueueFollowupRequired),
                Map.entry("review_queue_repeat_cycles", reviewQueueRepeatCycles),
                Map.entry("review_queue_oldest_deadline_at_utc", reviewQueueOldestDeadline != null ? reviewQueueOldestDeadline.toString() : ""),
                Map.entry("review_queue_oldest_overdue_days", reviewQueueOldestOverdueDays),
                Map.entry("review_queue_closure_pressure", closurePressure),
                Map.entry("review_queue_escalation_required", escalationRequired),
                Map.entry("review_queue_management_review_required", escalationRequired),
                Map.entry("review_queue_management_review_summary", managementReviewSummary),
                Map.entry("review_queue_escalated_count", escalatedCount),
                Map.entry("review_queue_escalated_scenarios", escalatedScenarios),
                Map.entry("review_queue_consolidation_required", consolidationRequired),
                Map.entry("review_queue_consolidation_count", consolidationCandidates.size()),
                Map.entry("review_queue_consolidation_candidates", consolidationCandidates),
                Map.entry("review_queue_next_action_summary", nextActionSummary),
                Map.entry("review_queue_summary", reviewQueueSummary),
                Map.entry("open_count", config.legacyOnlyScenarios().size()),
                Map.entry("managed_count", managedScenarioCount),
                Map.entry("closure_rate_pct", managedCoveragePct),
                Map.entry("managed_coverage_pct", managedCoveragePct),
                Map.entry("unmanaged_count", unmanagedScenarioCount),
                Map.entry("owners_ready_count", ownersReady),
                Map.entry("owner_coverage_pct", ownerCoveragePct),
                Map.entry("deadlines_ready_count", deadlinesReady),
                Map.entry("deadline_coverage_pct", deadlineCoveragePct),
                Map.entry("deadline_invalid_count", deadlineInvalidCount),
                Map.entry("deadline_overdue_count", deadlineOverdueCount),
                Map.entry("deadline_overdue_pct", overduePct),
                Map.entry("overdue_scenarios", overdueScenarios),
                Map.entry("review_queue_count", reviewQueueScenarios.size()),
                Map.entry("review_queue_scenarios", reviewQueueScenarios),
                Map.entry("action_items", actionItems),
                Map.entry("scenario_details", enrichedScenarioDetails)
        );

        String currentValue = !enabled
                ? "not required"
                : ready
                ? "none"
                : "open=%d, managed=%d/%d, owner=%d/%d, deadline=%d/%d%s%s".formatted(
                config.legacyOnlyScenarios().size(),
                managedScenarioCount,
                config.legacyOnlyScenarios().size(),
                ownersReady,
                config.legacyOnlyScenarios().size(),
                deadlinesReady,
                config.legacyOnlyScenarios().size(),
                deadlineInvalidCount > 0 ? ", invalid_deadlines=%d".formatted(deadlineInvalidCount) : "",
                deadlineOverdueCount > 0 ? ", overdue=%d".formatted(deadlineOverdueCount) : "");
        String note = ready
                ? configService.firstNonBlank(config.legacyInventoryReviewNote(), config.legacyInventoryReviewedBy())
                : Stream.of(
                        String.join(", ", config.legacyOnlyScenarios()),
                        managed ? "sunset_plan=managed" : null,
                        ownersReady < config.legacyOnlyScenarios().size()
                                ? "missing_owner=%d".formatted(config.legacyOnlyScenarios().size() - ownersReady) : null,
                        deadlinesReady < config.legacyOnlyScenarios().size()
                                ? "missing_deadline=%d".formatted(config.legacyOnlyScenarios().size() - deadlinesReady) : null,
                        deadlineInvalidCount > 0 ? "invalid_deadline=%d".formatted(deadlineInvalidCount) : null,
                        deadlineOverdueCount > 0 ? "overdue_deadline=%d".formatted(deadlineOverdueCount) : null,
                        config.legacyInventoryReviewNote(),
                        reviewTimestampInvalid ? "invalid_utc" : null)
                        .filter(StringUtils::hasText)
                        .collect(Collectors.joining(" · "));

        return new DialogWorkspaceRolloutSectionResult(
                "legacy_only_inventory",
                "workspace",
                "Legacy-only scenario inventory",
                status,
                config.packetRequired() && !ready && !managed,
                "Явный список legacy-only сценариев нужен, чтобы контролируемо завершить dual-run и не потерять edge-cases.",
                currentValue,
                enabled ? "inventory empty or every open scenario has owner + UTC deadline" : "optional",
                reviewedAt != null ? reviewedAt.toString() : generatedAtUtc,
                note,
                payload
        );
    }
}
