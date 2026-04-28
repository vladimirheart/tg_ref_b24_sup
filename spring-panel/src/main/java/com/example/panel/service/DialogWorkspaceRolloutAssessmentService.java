package com.example.panel.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DialogWorkspaceRolloutAssessmentService {

    private final DialogWorkspaceExternalKpiService dialogWorkspaceExternalKpiService;

    public DialogWorkspaceRolloutAssessmentService(DialogWorkspaceExternalKpiService dialogWorkspaceExternalKpiService) {
        this.dialogWorkspaceExternalKpiService = dialogWorkspaceExternalKpiService;
    }

    public Map<String, Object> buildRolloutDecision(Map<String, Object> cohortComparison,
                                                    Map<String, Object> guardrails) {
        Map<String, Object> decision = new LinkedHashMap<>();
        Map<String, Object> safeCohortComparison = cohortComparison == null ? Map.of() : cohortComparison;
        Map<String, Object> safeGuardrails = guardrails == null ? Map.of() : guardrails;
        String winner = String.valueOf(safeCohortComparison.getOrDefault("winner", "insufficient_data"));
        boolean sampleSizeOk = toBoolean(safeCohortComparison.get("sample_size_ok"));
        Map<String, Object> kpiSignal = safeCohortComparison.get("kpi_signal") instanceof Map<?, ?> kpi
                ? castObjectMap(kpi)
                : Map.of();
        Map<String, Object> kpiOutcomeSignal = safeCohortComparison.get("kpi_outcome_signal") instanceof Map<?, ?> kpiOutcome
                ? castObjectMap(kpiOutcome)
                : Map.of();
        boolean kpiSignalReady = toBoolean(kpiSignal.get("ready_for_decision"));
        boolean kpiOutcomeReady = toBoolean(kpiOutcomeSignal.get("ready_for_decision"));
        boolean kpiOutcomeRegressions = toBoolean(kpiOutcomeSignal.get("has_regression"));
        Map<String, Object> externalKpiSignal = dialogWorkspaceExternalKpiService.buildExternalKpiSignal();
        boolean externalKpiReady = toBoolean(externalKpiSignal.get("ready_for_decision"));
        String guardrailStatus = String.valueOf(safeGuardrails.getOrDefault("status", "ok"));
        boolean hasGuardrailIssues = "attention".equalsIgnoreCase(guardrailStatus);

        String action;
        String rationale;
        if (!sampleSizeOk) {
            action = "hold";
            rationale = "Недостаточно данных в control/test выборках для безопасного rollout decision.";
        } else if (!kpiSignalReady) {
            action = "hold";
            rationale = "Недостаточно продуктовых KPI-сигналов (FRT/TTR/SLA breach) для автоматического rollout decision.";
        } else if (!kpiOutcomeReady) {
            action = "hold";
            rationale = "Недостаточно измерений продуктовых KPI-результатов (FRT/TTR/SLA breach) для автоматического rollout decision.";
        } else if (!externalKpiReady) {
            action = "hold";
            rationale = "Внешние omni-channel/финансовые KPI не подтверждены: rollout остаётся на hold до прохождения data-mart checkpoint.";
        } else if (kpiOutcomeRegressions) {
            action = "hold";
            rationale = "Зафиксирована деградация по FRT/TTR/SLA breach в test cohort: rollout оставлен на hold до стабилизации.";
        } else if (hasGuardrailIssues) {
            action = "rollback";
            rationale = "Guardrails в статусе attention: rollout нужно приостановить и разобрать отклонения.";
        } else if ("test".equalsIgnoreCase(winner)) {
            action = "scale_up";
            rationale = "Test cohort выигрывает без технических регрессий: можно расширять долю workspace_v1.";
        } else {
            action = "hold";
            rationale = "Control cohort остаётся стабильнее: оставляем текущий охват и продолжаем наблюдение.";
        }

        decision.put("action", action);
        decision.put("winner", winner);
        decision.put("guardrails_status", guardrailStatus);
        decision.put("sample_size_ok", sampleSizeOk);
        decision.put("kpi_signal_ready", kpiSignalReady);
        decision.put("kpi_outcome_ready", kpiOutcomeReady);
        decision.put("kpi_outcome_regressions", kpiOutcomeRegressions);
        decision.put("external_kpi_signal", externalKpiSignal);
        decision.put("rationale", rationale);
        return decision;
    }

    public Map<String, Object> buildRolloutScorecard(Map<String, Object> totals,
                                                     Map<String, Object> cohortComparison,
                                                     Map<String, Object> guardrails,
                                                     Map<String, Object> rolloutDecision) {
        Map<String, Object> safeTotals = totals == null ? Map.of() : totals;
        Map<String, Object> safeCohortComparison = cohortComparison == null ? Map.of() : cohortComparison;
        Map<String, Object> safeGuardrails = guardrails == null ? Map.of() : guardrails;
        Map<String, Object> safeRolloutDecision = rolloutDecision == null ? Map.of() : rolloutDecision;
        Map<String, Object> kpiSignal = safeCohortComparison.get("kpi_signal") instanceof Map<?, ?> value
                ? castObjectMap(value)
                : Map.of();
        Map<String, Object> outcomeSignal = safeCohortComparison.get("kpi_outcome_signal") instanceof Map<?, ?> value
                ? castObjectMap(value)
                : Map.of();
        Map<String, Object> externalSignal = safeRolloutDecision.get("external_kpi_signal") instanceof Map<?, ?> value
                ? castObjectMap(value)
                : Map.of();

        List<Map<String, Object>> items = new ArrayList<>();
        long sampleMin = toLong(safeCohortComparison.get("sample_size_min_events"));
        items.add(buildScorecardItem(
                "sample_size",
                "experiment",
                "Control/Test sample size",
                toBoolean(safeCohortComparison.get("sample_size_ok")) ? "ok" : "hold",
                true,
                "Нужно достаточно событий в обеих когортах до принятия rollout decision.",
                "control=%d, test=%d".formatted(
                        toLong(safeCohortComparison.get("control_events")),
                        toLong(safeCohortComparison.get("test_events"))),
                sampleMin > 0 ? ">= %d событий на когорту".formatted(sampleMin) : "Дефолтный порог",
                null,
                null
        ));

        String guardrailStatus = "attention".equalsIgnoreCase(String.valueOf(safeGuardrails.get("status")))
                ? "attention"
                : "ok";
        items.add(buildScorecardItem(
                "guardrails",
                "guardrails",
                "Technical guardrails",
                guardrailStatus,
                "attention".equals(guardrailStatus),
                "Render error / fallback / abandon / slow open не должны уходить в attention.",
                "alerts=%d".formatted(safeListOfMaps(safeGuardrails.get("alerts")).size()),
                "status=ok",
                null,
                null
        ));

        long minKpiEvents = toLong(kpiSignal.get("min_events_per_cohort"));
        double minCoverage = kpiSignal.get("min_coverage_rate_per_cohort") instanceof Number number
                ? number.doubleValue()
                : 0d;
        items.add(buildScorecardItem(
                "primary_kpi_signal",
                "product_kpi",
                "Primary KPI coverage",
                toBoolean(kpiSignal.get("ready_for_decision")) ? "ok" : "hold",
                true,
                "FRT / TTR / SLA breach должны иметь достаточно покрытия и событий в control/test.",
                "required=%s".formatted(String.join(", ", safeStringList(kpiSignal.get("required_kpis")))),
                "events>=%d, coverage>=%.1f%%".formatted(minKpiEvents, minCoverage * 100d),
                null,
                null
        ));

        Map<String, Object> outcomeMetrics = outcomeSignal.get("metrics") instanceof Map<?, ?> value
                ? castObjectMap(value)
                : Map.of();
        appendOutcomeMetricScorecardItem(items, outcomeMetrics, "frt", "Outcome KPI: FRT");
        appendOutcomeMetricScorecardItem(items, outcomeMetrics, "ttr", "Outcome KPI: TTR");
        appendOutcomeMetricScorecardItem(items, outcomeMetrics, "sla_breach", "Outcome KPI: SLA breach");

        double contextReadyRate = safeDouble(safeTotals.get("context_profile_ready_rate"));
        items.add(buildScorecardItem(
                "customer_context",
                "workspace",
                "Customer context readiness",
                contextReadyRate >= 0.95d ? "ok" : "hold",
                false,
                "Контекст клиента должен быть готов без постоянного fallback в сторонние экраны.",
                "%.1f%% ready".formatted(contextReadyRate * 100d),
                ">= 95.0%",
                null,
                null
        ));

        double contextSourceReadyRate = safeDouble(safeTotals.get("context_source_ready_rate"));
        items.add(buildScorecardItem(
                "customer_context_sources",
                "workspace",
                "Customer context sources",
                contextSourceReadyRate >= 0.95d ? "ok" : "attention",
                false,
                "Обязательные CRM/contract/external источники должны быть подключены и не иметь source-gap.",
                "%.1f%% ready".formatted(contextSourceReadyRate * 100d),
                ">= 95.0%",
                null,
                null
        ));

        double contextAttributePolicyReadyRate = safeDouble(safeTotals.get("context_attribute_policy_ready_rate"));
        items.add(buildScorecardItem(
                "customer_context_attribute_policy",
                "workspace",
                "Customer profile source/freshness policy",
                contextAttributePolicyReadyRate >= 0.95d ? "ok" : "attention",
                false,
                "Mandatory customer profile должен иметь формализованный source-of-truth и валидную UTC freshness policy.",
                "%.1f%% ready".formatted(contextAttributePolicyReadyRate * 100d),
                ">= 95.0%",
                null,
                null
        ));

        double contextBlockReadyRate = safeDouble(safeTotals.get("context_block_ready_rate"));
        items.add(buildScorecardItem(
                "customer_context_blocks",
                "workspace",
                "Customer context block priority",
                contextBlockReadyRate >= 0.95d ? "ok" : "attention",
                false,
                "Приоритетные блоки customer context должны быть готовы и стандартизированы для оператора.",
                "%.1f%% ready".formatted(contextBlockReadyRate * 100d),
                ">= 95.0%",
                null,
                null
        ));

        double parityReadyRate = safeDouble(safeTotals.get("workspace_parity_ready_rate"));
        long parityGapEvents = toLong(safeTotals.get("workspace_parity_gap_events"));
        long workspaceOpenEvents = toLong(safeTotals.get("workspace_open_events"));
        items.add(buildScorecardItem(
                "workspace_parity",
                "workspace",
                "Workspace parity with legacy",
                workspaceOpenEvents <= 0 ? "hold" : (parityReadyRate >= 0.95d ? "ok" : "attention"),
                false,
                "Workspace должен покрывать основной operator-flow, а legacy modal оставаться rollback-only.",
                workspaceOpenEvents <= 0
                        ? "Недостаточно workspace_open_ms событий"
                        : "%.1f%% ready, gaps=%d".formatted(parityReadyRate * 100d, parityGapEvents),
                ">= 95.0% parity-ready opens",
                null,
                null
        ));

        String externalMeasuredAt = firstNonBlank(
                normalizeUtcTimestamp(externalSignal.get("reviewed_at")),
                normalizeUtcTimestamp(externalSignal.get("data_updated_at")),
                normalizeUtcTimestamp(externalSignal.get("datamart_health_updated_at")),
                normalizeUtcTimestamp(externalSignal.get("datamart_program_updated_at")),
                normalizeUtcTimestamp(externalSignal.get("datamart_dependency_ticket_updated_at")),
                normalizeUtcTimestamp(externalSignal.get("datamart_target_ready_at"))
        );
        boolean externalEnabled = toBoolean(externalSignal.get("enabled"));
        boolean externalReady = toBoolean(externalSignal.get("ready_for_decision"));
        items.add(buildScorecardItem(
                "external_kpi_gate",
                "external_dependencies",
                "External KPI checkpoint",
                externalEnabled ? (externalReady ? "ok" : "hold") : "off",
                externalEnabled && !externalReady,
                "Omni-channel / finance / data-mart зависимости не должны блокировать rollout.",
                "omni=%s, finance=%s".formatted(
                        toBoolean(externalSignal.get("omnichannel_ready")) ? "ready" : "pending",
                        toBoolean(externalSignal.get("finance_ready")) ? "ready" : "pending"),
                externalEnabled ? "ready_for_decision=true" : "gate disabled",
                externalMeasuredAt,
                String.valueOf(externalSignal.getOrDefault("note", "")).trim()
        ));
        appendExternalCheckpointScorecardItems(items, externalSignal, externalEnabled);

        return Map.of(
                "generated_at", Instant.now().toString(),
                "decision_action", String.valueOf(safeRolloutDecision.getOrDefault("action", "hold")),
                "items", items
        );
    }

    private void appendOutcomeMetricScorecardItem(List<Map<String, Object>> items,
                                                  Map<String, Object> outcomeMetrics,
                                                  String metricKey,
                                                  String label) {
        Map<String, Object> metric = outcomeMetrics.get(metricKey) instanceof Map<?, ?> value
                ? castObjectMap(value)
                : Map.of();
        if (metric.isEmpty()) {
            return;
        }
        boolean ready = toBoolean(metric.get("ready"));
        boolean regression = toBoolean(metric.get("regression"));
        String status = !ready ? "hold" : (regression ? "attention" : "ok");
        boolean blocking = !ready || regression;
        String currentValue;
        String threshold;
        if ("latency_ms".equals(String.valueOf(metric.get("type")))) {
            currentValue = "control=%s ms, test=%s ms".formatted(
                    formatNullableLong(metric.get("control_value")),
                    formatNullableLong(metric.get("test_value")));
            threshold = "Δ <= %.1f%%".formatted(safeDouble(metric.get("max_relative_regression")) * 100d);
        } else {
            currentValue = "control=%s, test=%s".formatted(
                    formatNullableLong(metric.get("control_value")),
                    formatNullableLong(metric.get("test_value")));
            threshold = "multiplier <= %.2f".formatted(safeDouble(metric.get("max_relative_multiplier")));
        }
        items.add(buildScorecardItem(
                "outcome_" + metricKey,
                "product_outcome",
                label,
                status,
                blocking,
                "Проверка outcome-метрик после включения workspace в cohort.",
                currentValue,
                threshold,
                null,
                null
        ));
    }

    private void appendExternalCheckpointScorecardItems(List<Map<String, Object>> items,
                                                        Map<String, Object> externalSignal,
                                                        boolean externalEnabled) {
        if (items == null || externalSignal == null) {
            return;
        }

        appendBinaryExternalCheckpointScorecardItem(
                items,
                "external_review",
                "External review freshness",
                externalEnabled,
                true,
                toBoolean(externalSignal.get("review_present")),
                toBoolean(externalSignal.get("review_fresh")),
                toBoolean(externalSignal.get("review_timestamp_invalid")),
                normalizeUtcTimestamp(externalSignal.get("reviewed_at")),
                "reviewed_by=%s".formatted(String.valueOf(externalSignal.getOrDefault("reviewed_by", "")).trim()),
                "review present & <= %s h".formatted(formatNullableLong(externalSignal.get("review_ttl_hours"))),
                "Ручной review перед rollout должен быть подтверждён и оставаться свежим."
        );

        appendBinaryExternalCheckpointScorecardItem(
                items,
                "external_data_freshness",
                "External KPI data freshness",
                externalEnabled,
                toBoolean(externalSignal.get("data_freshness_required")),
                toBoolean(externalSignal.get("data_updated_present")),
                toBoolean(externalSignal.get("data_fresh")),
                toBoolean(externalSignal.get("data_updated_timestamp_invalid")),
                normalizeUtcTimestamp(externalSignal.get("data_updated_at")),
                externalDataFreshnessCurrentValue(externalSignal),
                "fresh <= %s h".formatted(formatNullableLong(externalSignal.get("data_freshness_ttl_hours"))),
                "Omni-channel / finance KPI должны опираться на свежий UTC-срез данных."
        );

        appendExternalCompositeCheckpointScorecardItem(
                items,
                "external_dashboards",
                "External dashboards readiness",
                externalEnabled,
                toBoolean(externalSignal.get("dashboard_links_required")) || toBoolean(externalSignal.get("dashboard_status_required")),
                toBoolean(externalSignal.get("dashboard_links_ready")) && toBoolean(externalSignal.get("dashboard_status_ready")),
                "links=%s, status=%s".formatted(
                        toBoolean(externalSignal.get("dashboard_links_present")) ? "ready" : "missing",
                        String.valueOf(externalSignal.getOrDefault("dashboard_status", "off")).trim()),
                buildExternalDashboardThresholdLabel(externalSignal),
                null,
                String.valueOf(externalSignal.getOrDefault("dashboard_status_note", "")).trim(),
                "Ссылки на дашборды и статус витрин должны быть валидны до расширения rollout."
        );

        appendBinaryExternalCheckpointScorecardItem(
                items,
                "external_datamart_health",
                "Data-mart health",
                externalEnabled,
                toBoolean(externalSignal.get("datamart_health_required")) || toBoolean(externalSignal.get("datamart_health_freshness_required")),
                toBoolean(externalSignal.get("datamart_health_ready")),
                toBoolean(externalSignal.get("datamart_health_freshness_ready")),
                toBoolean(externalSignal.get("datamart_health_timestamp_invalid"))
                        || toBoolean(externalSignal.get("datamart_health_updated_timestamp_invalid")),
                normalizeUtcTimestamp(externalSignal.get("datamart_health_updated_at")),
                "status=%s, freshness=%s".formatted(
                        String.valueOf(externalSignal.getOrDefault("datamart_health_status", "unknown")).trim(),
                        toBoolean(externalSignal.get("datamart_health_fresh")) ? "fresh" : "stale"),
                buildDatamartHealthThresholdLabel(externalSignal),
                "Data-mart health и свежесть его статуса не должны блокировать продуктовый rollout."
        );

        appendExternalCompositeCheckpointScorecardItem(
                items,
                "external_datamart_program",
                "Data-mart delivery program",
                externalEnabled,
                toBoolean(externalSignal.get("datamart_program_blocker_required"))
                        || toBoolean(externalSignal.get("datamart_program_freshness_required"))
                        || toBoolean(externalSignal.get("datamart_timeline_required")),
                toBoolean(externalSignal.get("datamart_program_ready"))
                        && toBoolean(externalSignal.get("datamart_program_freshness_ready"))
                        && toBoolean(externalSignal.get("datamart_timeline_ready")),
                buildDatamartProgramCurrentValue(externalSignal),
                buildDatamartProgramThresholdLabel(externalSignal),
                firstNonBlank(
                        normalizeUtcTimestamp(externalSignal.get("datamart_program_updated_at")),
                        normalizeUtcTimestamp(externalSignal.get("datamart_target_ready_at"))
                ),
                buildDatamartProgramNote(externalSignal),
                "Программа data-mart должна быть без blockers, со свежим статусом и в пределах timeline."
        );

        appendExternalCompositeCheckpointScorecardItem(
                items,
                "external_dependency_ticket",
                "Dependency ticket readiness",
                externalEnabled,
                toBoolean(externalSignal.get("datamart_dependency_ticket_required"))
                        || toBoolean(externalSignal.get("datamart_dependency_ticket_freshness_required"))
                        || toBoolean(externalSignal.get("datamart_dependency_ticket_owner_required"))
                        || toBoolean(externalSignal.get("datamart_dependency_ticket_owner_contact_required"))
                        || toBoolean(externalSignal.get("datamart_dependency_ticket_owner_contact_actionable_required")),
                toBoolean(externalSignal.get("datamart_dependency_ticket_ready"))
                        && toBoolean(externalSignal.get("datamart_dependency_ticket_freshness_ready"))
                        && toBoolean(externalSignal.get("datamart_dependency_ticket_owner_ready"))
                        && toBoolean(externalSignal.get("datamart_dependency_ticket_owner_contact_ready"))
                        && toBoolean(externalSignal.get("datamart_dependency_ticket_owner_contact_actionable_ready")),
                buildDependencyTicketCurrentValue(externalSignal),
                buildDependencyTicketThresholdLabel(externalSignal),
                normalizeUtcTimestamp(externalSignal.get("datamart_dependency_ticket_updated_at")),
                buildDependencyTicketNote(externalSignal),
                "Критические внешние зависимости должны иметь ticket, owner и actionable contact."
        );

        appendExternalCompositeCheckpointScorecardItem(
                items,
                "external_datamart_contract",
                "Data-mart contract coverage",
                externalEnabled,
                toBoolean(externalSignal.get("datamart_contract_required"))
                        || toBoolean(externalSignal.get("datamart_contract_optional_coverage_required")),
                toBoolean(externalSignal.get("datamart_contract_ready")),
                buildDatamartContractCurrentValue(externalSignal),
                buildDatamartContractThresholdLabel(externalSignal),
                null,
                buildDatamartContractNote(externalSignal),
                "Контракт внешних KPI должен покрывать обязательные поля и не иметь конфликтов."
        );
    }

    private void appendBinaryExternalCheckpointScorecardItem(List<Map<String, Object>> items,
                                                             String key,
                                                             String label,
                                                             boolean externalEnabled,
                                                             boolean required,
                                                             boolean present,
                                                             boolean ready,
                                                             boolean invalidTimestamp,
                                                             String measuredAtUtc,
                                                             String currentValue,
                                                             String threshold,
                                                             String summary) {
        if (!externalEnabled) {
            items.add(buildScorecardItem(key, "external_dependencies", label, "off", false, summary, "gate disabled", "off", null, null));
            return;
        }
        if (!required) {
            items.add(buildScorecardItem(key, "external_dependencies", label, "off", false, summary, "off", "not required", null, null));
            return;
        }
        String normalizedCurrentValue = StringUtils.hasText(currentValue) ? currentValue : (present ? "present" : "missing");
        if (invalidTimestamp) {
            normalizedCurrentValue = "invalid_utc";
        } else if (!ready && !present) {
            normalizedCurrentValue = "missing";
        } else if (ready) {
            normalizedCurrentValue = normalizedCurrentValue + " (ready)";
        }
        items.add(buildScorecardItem(
                key,
                "external_dependencies",
                label,
                invalidTimestamp ? "hold" : (ready ? "ok" : "hold"),
                true,
                summary,
                normalizedCurrentValue,
                threshold,
                invalidTimestamp ? null : measuredAtUtc,
                invalidTimestamp ? "Ожидается корректная UTC timestamp." : null
        ));
    }

    private void appendExternalCompositeCheckpointScorecardItem(List<Map<String, Object>> items,
                                                                String key,
                                                                String label,
                                                                boolean externalEnabled,
                                                                boolean required,
                                                                boolean ready,
                                                                String currentValue,
                                                                String threshold,
                                                                String measuredAtUtc,
                                                                String note,
                                                                String summary) {
        if (!externalEnabled) {
            items.add(buildScorecardItem(key, "external_dependencies", label, "off", false, summary, "gate disabled", "off", null, null));
            return;
        }
        if (!required) {
            items.add(buildScorecardItem(key, "external_dependencies", label, "off", false, summary, "off", "not required", null, null));
            return;
        }
        items.add(buildScorecardItem(
                key,
                "external_dependencies",
                label,
                ready ? "ok" : "hold",
                true,
                summary,
                StringUtils.hasText(currentValue) ? currentValue : "pending",
                StringUtils.hasText(threshold) ? threshold : "ready",
                measuredAtUtc,
                StringUtils.hasText(note) ? note : null
        ));
    }

    private String externalDataFreshnessCurrentValue(Map<String, Object> externalSignal) {
        return "updated=%s, freshness=%s".formatted(
                toBoolean(externalSignal.get("data_updated_present")) ? "present" : "missing",
                toBoolean(externalSignal.get("data_fresh")) ? "fresh" : "stale");
    }

    private String buildExternalDashboardThresholdLabel(Map<String, Object> externalSignal) {
        List<String> requirements = new ArrayList<>();
        if (toBoolean(externalSignal.get("dashboard_links_required"))) {
            requirements.add("links=ready");
        }
        if (toBoolean(externalSignal.get("dashboard_status_required"))) {
            requirements.add("status=healthy");
        }
        return requirements.isEmpty() ? "not required" : String.join(", ", requirements);
    }

    private String buildDatamartHealthThresholdLabel(Map<String, Object> externalSignal) {
        List<String> requirements = new ArrayList<>();
        if (toBoolean(externalSignal.get("datamart_health_required"))) {
            requirements.add("status=healthy");
        }
        if (toBoolean(externalSignal.get("datamart_health_freshness_required"))) {
            requirements.add("fresh <= %s h".formatted(formatNullableLong(externalSignal.get("datamart_health_ttl_hours"))));
        }
        return requirements.isEmpty() ? "not required" : String.join(", ", requirements);
    }

    private String buildDatamartProgramCurrentValue(Map<String, Object> externalSignal) {
        return "status=%s, freshness=%s, timeline=%s".formatted(
                String.valueOf(externalSignal.getOrDefault("datamart_program_status", "unknown")).trim(),
                toBoolean(externalSignal.get("datamart_program_fresh")) ? "fresh" : "stale",
                toBoolean(externalSignal.get("datamart_timeline_ready")) ? "ready" : "hold");
    }

    private String buildDatamartProgramThresholdLabel(Map<String, Object> externalSignal) {
        List<String> requirements = new ArrayList<>();
        if (toBoolean(externalSignal.get("datamart_program_blocker_required"))) {
            requirements.add("status!=blocked");
        }
        if (toBoolean(externalSignal.get("datamart_program_freshness_required"))) {
            requirements.add("fresh <= %s h".formatted(formatNullableLong(externalSignal.get("datamart_program_ttl_hours"))));
        }
        if (toBoolean(externalSignal.get("datamart_timeline_required"))) {
            requirements.add("target within grace");
        }
        return requirements.isEmpty() ? "not required" : String.join(", ", requirements);
    }

    private String buildDatamartProgramNote(Map<String, Object> externalSignal) {
        List<String> notes = new ArrayList<>();
        String programNote = String.valueOf(externalSignal.getOrDefault("datamart_program_note", "")).trim();
        if (StringUtils.hasText(programNote)) {
            notes.add(programNote);
        }
        String blockerUrl = String.valueOf(externalSignal.getOrDefault("datamart_program_blocker_url", "")).trim();
        if (StringUtils.hasText(blockerUrl)) {
            notes.add("blocker=" + blockerUrl);
        }
        List<String> riskReasons = safeStringList(externalSignal.get("datamart_risk_reasons"));
        if (!riskReasons.isEmpty()) {
            notes.add("risk=" + String.join("|", riskReasons));
        }
        return notes.isEmpty() ? null : String.join(", ", notes);
    }

    private String buildDependencyTicketCurrentValue(Map<String, Object> externalSignal) {
        return "ticket=%s, freshness=%s, owner=%s, contact=%s".formatted(
                toBoolean(externalSignal.get("datamart_dependency_ticket_present")) ? "ready" : "missing",
                toBoolean(externalSignal.get("datamart_dependency_ticket_fresh")) ? "fresh" : "stale",
                toBoolean(externalSignal.get("datamart_dependency_ticket_owner_present")) ? "ready" : "missing",
                toBoolean(externalSignal.get("datamart_dependency_ticket_owner_contact_actionable")) ? "actionable" : "not_actionable");
    }

    private String buildDependencyTicketThresholdLabel(Map<String, Object> externalSignal) {
        List<String> requirements = new ArrayList<>();
        if (toBoolean(externalSignal.get("datamart_dependency_ticket_required"))) {
            requirements.add("ticket=ready");
        }
        if (toBoolean(externalSignal.get("datamart_dependency_ticket_freshness_required"))) {
            requirements.add("fresh <= %s h".formatted(formatNullableLong(externalSignal.get("datamart_dependency_ticket_ttl_hours"))));
        }
        if (toBoolean(externalSignal.get("datamart_dependency_ticket_owner_required"))) {
            requirements.add("owner=ready");
        }
        if (toBoolean(externalSignal.get("datamart_dependency_ticket_owner_contact_required"))) {
            requirements.add("contact=ready");
        }
        if (toBoolean(externalSignal.get("datamart_dependency_ticket_owner_contact_actionable_required"))) {
            requirements.add("contact=actionable");
        }
        return requirements.isEmpty() ? "not required" : String.join(", ", requirements);
    }

    private String buildDependencyTicketNote(Map<String, Object> externalSignal) {
        List<String> notes = new ArrayList<>();
        String url = String.valueOf(externalSignal.getOrDefault("datamart_dependency_ticket_url", "")).trim();
        if (StringUtils.hasText(url)) {
            notes.add("url=" + url);
        }
        String owner = String.valueOf(externalSignal.getOrDefault("datamart_dependency_ticket_owner", "")).trim();
        if (StringUtils.hasText(owner)) {
            notes.add("owner=" + owner);
        }
        String contact = String.valueOf(externalSignal.getOrDefault("datamart_dependency_ticket_owner_contact", "")).trim();
        if (StringUtils.hasText(contact)) {
            notes.add("contact=" + contact);
        }
        return notes.isEmpty() ? null : String.join(", ", notes);
    }

    private String buildDatamartContractCurrentValue(Map<String, Object> externalSignal) {
        return "mandatory=%s%%, optional=%s%%, blocking_gaps=%s, non_blocking_gaps=%s".formatted(
                formatNullableLong(externalSignal.get("datamart_contract_mandatory_coverage_pct")),
                formatNullableLong(externalSignal.get("datamart_contract_optional_coverage_pct")),
                formatNullableLong(externalSignal.get("datamart_contract_blocking_gap_count")),
                formatNullableLong(externalSignal.get("datamart_contract_non_blocking_gap_count")));
    }

    private String buildDatamartContractThresholdLabel(Map<String, Object> externalSignal) {
        List<String> requirements = new ArrayList<>();
        if (toBoolean(externalSignal.get("datamart_contract_required"))) {
            requirements.add("mandatory coverage=100%");
        }
        if (toBoolean(externalSignal.get("datamart_contract_optional_coverage_required"))
                && toBoolean(externalSignal.get("datamart_contract_optional_coverage_gate_active"))) {
            requirements.add("optional >= %s%%".formatted(formatNullableLong(externalSignal.get("datamart_contract_optional_min_coverage_pct"))));
        }
        requirements.add("no configuration conflict");
        return String.join(", ", requirements);
    }

    private String buildDatamartContractNote(Map<String, Object> externalSignal) {
        List<String> notes = new ArrayList<>();
        List<String> missingMandatory = safeStringList(externalSignal.get("datamart_contract_missing_mandatory_fields"));
        if (!missingMandatory.isEmpty()) {
            notes.add("missing_mandatory=" + String.join("|", missingMandatory));
        }
        List<String> missingOptional = safeStringList(externalSignal.get("datamart_contract_missing_optional_fields"));
        if (!missingOptional.isEmpty()) {
            notes.add("missing_optional=" + String.join("|", missingOptional));
        }
        List<String> overlaps = safeStringList(externalSignal.get("datamart_contract_overlapping_fields"));
        if (!overlaps.isEmpty()) {
            notes.add("overlap=" + String.join("|", overlaps));
        }
        if (toBoolean(externalSignal.get("datamart_contract_configuration_conflict"))) {
            notes.add("configuration_conflict");
        }
        return notes.isEmpty() ? null : String.join(", ", notes);
    }

    private Map<String, Object> buildScorecardItem(String key,
                                                   String category,
                                                   String label,
                                                   String status,
                                                   boolean blocking,
                                                   String summary,
                                                   String currentValue,
                                                   String threshold,
                                                   String measuredAtUtc,
                                                   String note) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", key);
        item.put("category", category);
        item.put("label", label);
        item.put("status", normalizeScorecardStatus(status));
        item.put("blocking", blocking);
        item.put("summary", normalizeNullString(summary));
        item.put("current_value", normalizeNullString(currentValue));
        item.put("threshold", normalizeNullString(threshold));
        item.put("measured_at", normalizeUtcTimestamp(measuredAtUtc));
        item.put("note", normalizeNullString(note));
        return item;
    }

    private String normalizeScorecardStatus(String value) {
        String normalized = normalizeNullString(value).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "ok", "attention", "hold", "off" -> normalized;
            default -> "hold";
        };
    }

    private Map<String, Object> castObjectMap(Map<?, ?> source) {
        Map<String, Object> payload = new LinkedHashMap<>();
        source.forEach((key, value) -> payload.put(String.valueOf(key), value));
        return payload;
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value == null) {
            return false;
        }
        String normalized = String.valueOf(value).trim();
        return "true".equalsIgnoreCase(normalized) || "1".equals(normalized);
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private List<Map<String, Object>> safeListOfMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add(castObjectMap(map));
            }
        }
        return result;
    }

    private List<String> safeStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(String::valueOf)
                .map(this::normalizeNullString)
                .filter(StringUtils::hasText)
                .toList();
    }

    private double safeDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return 0d;
        }
    }

    private String formatNullableLong(Object value) {
        Long number = extractNullableLong(value);
        return number != null ? String.valueOf(number) : "—";
    }

    private Long extractNullableLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String normalizeUtcTimestamp(Object rawValue) {
        OffsetDateTime parsed = parseReviewTimestamp(rawValue == null ? null : String.valueOf(rawValue));
        return parsed != null ? parsed.withOffsetSameInstant(ZoneOffset.UTC).toString() : "";
    }

    private String normalizeNullString(String value) {
        if (value == null || "null".equalsIgnoreCase(value)) {
            return "";
        }
        return value.trim();
    }

    private OffsetDateTime parseReviewTimestamp(String rawValue) {
        String value = normalizeNullString(rawValue);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).withOffsetSameInstant(ZoneOffset.UTC);
        } catch (Exception ignored) {
            // fallback to legacy datetime-local without timezone
        }
        try {
            return LocalDateTime.parse(value).atOffset(ZoneOffset.UTC);
        } catch (Exception ignored) {
            return null;
        }
    }
}
