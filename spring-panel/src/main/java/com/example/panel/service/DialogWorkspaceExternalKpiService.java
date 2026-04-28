package com.example.panel.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DialogWorkspaceExternalKpiService {

    private static final boolean DEFAULT_EXTERNAL_KPI_GATE_ENABLED = false;
    private static final long DEFAULT_EXTERNAL_KPI_REVIEW_TTL_HOURS = 168L;
    private static final boolean DEFAULT_EXTERNAL_KPI_DATA_FRESHNESS_REQUIRED = false;
    private static final long DEFAULT_EXTERNAL_KPI_DATA_FRESHNESS_TTL_HOURS = 48L;
    private static final boolean DEFAULT_EXTERNAL_KPI_DASHBOARD_LINKS_REQUIRED = false;
    private static final boolean DEFAULT_EXTERNAL_KPI_DASHBOARD_STATUS_REQUIRED = false;
    private static final boolean DEFAULT_EXTERNAL_KPI_OWNER_RUNBOOK_REQUIRED = false;
    private static final boolean DEFAULT_EXTERNAL_KPI_DATAMART_HEALTH_REQUIRED = false;
    private static final boolean DEFAULT_EXTERNAL_KPI_DATAMART_HEALTH_FRESHNESS_REQUIRED = false;
    private static final long DEFAULT_EXTERNAL_KPI_DATAMART_HEALTH_TTL_HOURS = 48L;
    private static final boolean DEFAULT_EXTERNAL_KPI_DATAMART_PROGRAM_BLOCKER_REQUIRED = false;
    private static final boolean DEFAULT_EXTERNAL_KPI_DATAMART_PROGRAM_FRESHNESS_REQUIRED = false;
    private static final long DEFAULT_EXTERNAL_KPI_DATAMART_PROGRAM_TTL_HOURS = 24L * 14L;
    private static final boolean DEFAULT_EXTERNAL_KPI_DATAMART_TIMELINE_REQUIRED = false;
    private static final long DEFAULT_EXTERNAL_KPI_DATAMART_TIMELINE_GRACE_HOURS = 24L;
    private static final boolean DEFAULT_EXTERNAL_KPI_DATAMART_DEPENDENCY_TICKET_REQUIRED = false;
    private static final boolean DEFAULT_EXTERNAL_KPI_DATAMART_DEPENDENCY_TICKET_FRESHNESS_REQUIRED = false;
    private static final boolean DEFAULT_EXTERNAL_KPI_DATAMART_DEPENDENCY_TICKET_OWNER_REQUIRED = false;
    private static final boolean DEFAULT_EXTERNAL_KPI_DATAMART_DEPENDENCY_TICKET_OWNER_CONTACT_REQUIRED = false;
    private static final boolean DEFAULT_EXTERNAL_KPI_DATAMART_DEPENDENCY_TICKET_OWNER_CONTACT_ACTIONABLE_REQUIRED = false;
    private static final long DEFAULT_EXTERNAL_KPI_DATAMART_DEPENDENCY_TICKET_TTL_HOURS = 24L * 14L;
    private static final String DEFAULT_EXTERNAL_KPI_CONTRACT_VERSION = "v1";
    private static final Set<String> DEFAULT_EXTERNAL_KPI_CONTRACT_MANDATORY_FIELDS = Set.of("frt", "ttr", "sla_breach", "cost_per_contact");
    private static final int DEFAULT_EXTERNAL_KPI_CONTRACT_OPTIONAL_MIN_COVERAGE_PCT = 80;

    private final SharedConfigService sharedConfigService;

    public DialogWorkspaceExternalKpiService(SharedConfigService sharedConfigService) {
        this.sharedConfigService = sharedConfigService;
    }

    public Map<String, Object> buildExternalKpiSignal() {
        Map<String, Object> signal = new LinkedHashMap<>();
        boolean gateEnabled = resolveBooleanDialogConfigValue(
                "workspace_rollout_external_kpi_gate_enabled",
                DEFAULT_EXTERNAL_KPI_GATE_ENABLED);
        boolean omnichannelReady = resolveBooleanDialogConfigValue(
                "workspace_rollout_external_kpi_omnichannel_ready",
                false);
        boolean financeReady = resolveBooleanDialogConfigValue(
                "workspace_rollout_external_kpi_finance_ready",
                false);
        String note = String.valueOf(resolveDialogConfigValue("workspace_rollout_external_kpi_note"));
        String datamartOwner = String.valueOf(resolveDialogConfigValue("workspace_rollout_external_kpi_datamart_owner"));
        String datamartRunbookUrl = String.valueOf(resolveDialogConfigValue("workspace_rollout_external_kpi_datamart_runbook_url"));
        String datamartDependencyTicketUrl = normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_external_kpi_datamart_dependency_ticket_url")));
        String datamartDependencyTicketOwner = normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_external_kpi_datamart_dependency_ticket_owner")));
        String datamartDependencyTicketOwnerContact = normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_external_kpi_datamart_dependency_ticket_owner_contact")));
        String datamartDependencyTicketUpdatedAtRaw = String.valueOf(resolveDialogConfigValue("workspace_rollout_external_kpi_datamart_dependency_ticket_updated_at"));
        String reviewedBy = String.valueOf(resolveDialogConfigValue("workspace_rollout_external_kpi_reviewed_by"));
        String reviewedAtRaw = String.valueOf(resolveDialogConfigValue("workspace_rollout_external_kpi_reviewed_at"));
        long reviewTtlHours = resolveLongDialogConfigValue(
                "workspace_rollout_external_kpi_review_ttl_hours",
                DEFAULT_EXTERNAL_KPI_REVIEW_TTL_HOURS,
                1,
                24 * 90L);
        boolean dataFreshnessRequired = resolveBooleanDialogConfigValue(
                "workspace_rollout_external_kpi_data_freshness_required",
                DEFAULT_EXTERNAL_KPI_DATA_FRESHNESS_REQUIRED);
        boolean dashboardLinksRequired = resolveBooleanDialogConfigValue(
                "workspace_rollout_external_kpi_dashboard_links_required",
                DEFAULT_EXTERNAL_KPI_DASHBOARD_LINKS_REQUIRED);
        boolean dashboardStatusRequired = resolveBooleanDialogConfigValue(
                "workspace_rollout_external_kpi_dashboard_status_required",
                DEFAULT_EXTERNAL_KPI_DASHBOARD_STATUS_REQUIRED);
        boolean ownerRunbookRequired = resolveBooleanDialogConfigValue(
                "workspace_rollout_external_kpi_owner_runbook_required",
                DEFAULT_EXTERNAL_KPI_OWNER_RUNBOOK_REQUIRED);
        boolean datamartHealthRequired = resolveBooleanDialogConfigValue(
                "workspace_rollout_external_kpi_datamart_health_required",
                DEFAULT_EXTERNAL_KPI_DATAMART_HEALTH_REQUIRED);
        boolean datamartHealthFreshnessRequired = resolveBooleanDialogConfigValue(
                "workspace_rollout_external_kpi_datamart_health_freshness_required",
                DEFAULT_EXTERNAL_KPI_DATAMART_HEALTH_FRESHNESS_REQUIRED);
        boolean datamartProgramBlockerRequired = resolveBooleanDialogConfigValue(
                "workspace_rollout_external_kpi_datamart_program_blocker_required",
                DEFAULT_EXTERNAL_KPI_DATAMART_PROGRAM_BLOCKER_REQUIRED);
        boolean datamartProgramFreshnessRequired = resolveBooleanDialogConfigValue(
                "workspace_rollout_external_kpi_datamart_program_freshness_required",
                DEFAULT_EXTERNAL_KPI_DATAMART_PROGRAM_FRESHNESS_REQUIRED);
        boolean datamartTimelineRequired = resolveBooleanDialogConfigValue(
                "workspace_rollout_external_kpi_datamart_timeline_required",
                DEFAULT_EXTERNAL_KPI_DATAMART_TIMELINE_REQUIRED);
        boolean datamartDependencyTicketRequired = resolveBooleanDialogConfigValue(
                "workspace_rollout_external_kpi_datamart_dependency_ticket_required",
                DEFAULT_EXTERNAL_KPI_DATAMART_DEPENDENCY_TICKET_REQUIRED);
        boolean datamartDependencyTicketFreshnessRequired = resolveBooleanDialogConfigValue(
                "workspace_rollout_external_kpi_datamart_dependency_ticket_freshness_required",
                DEFAULT_EXTERNAL_KPI_DATAMART_DEPENDENCY_TICKET_FRESHNESS_REQUIRED);
        boolean datamartDependencyTicketOwnerRequired = resolveBooleanDialogConfigValue(
                "workspace_rollout_external_kpi_datamart_dependency_ticket_owner_required",
                DEFAULT_EXTERNAL_KPI_DATAMART_DEPENDENCY_TICKET_OWNER_REQUIRED);
        boolean datamartDependencyTicketOwnerContactRequired = resolveBooleanDialogConfigValue(
                "workspace_rollout_external_kpi_datamart_dependency_ticket_owner_contact_required",
                DEFAULT_EXTERNAL_KPI_DATAMART_DEPENDENCY_TICKET_OWNER_CONTACT_REQUIRED);
        boolean datamartDependencyTicketOwnerContactActionableRequired = resolveBooleanDialogConfigValue(
                "workspace_rollout_external_kpi_datamart_dependency_ticket_owner_contact_actionable_required",
                DEFAULT_EXTERNAL_KPI_DATAMART_DEPENDENCY_TICKET_OWNER_CONTACT_ACTIONABLE_REQUIRED);
        boolean datamartContractRequired = resolveBooleanDialogConfigValue(
                "workspace_rollout_external_kpi_datamart_contract_required",
                false);
        String datamartContractVersion = normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_external_kpi_datamart_contract_version")));
        Set<String> datamartContractMandatoryFields = parseExternalKpiContractFields(
                resolveDialogConfigValue("workspace_rollout_external_kpi_datamart_contract_mandatory_fields"),
                DEFAULT_EXTERNAL_KPI_CONTRACT_MANDATORY_FIELDS);
        Set<String> datamartContractOptionalFields = parseExternalKpiContractFields(
                resolveDialogConfigValue("workspace_rollout_external_kpi_datamart_contract_optional_fields"),
                Set.of());
        Set<String> datamartContractAvailableFields = parseExternalKpiContractFields(
                resolveDialogConfigValue("workspace_rollout_external_kpi_datamart_contract_available_fields"),
                Set.of());
        boolean datamartContractOptionalCoverageRequired = resolveBooleanDialogConfigValue(
                "workspace_rollout_external_kpi_datamart_contract_optional_coverage_required",
                false);
        int datamartContractOptionalMinCoveragePct = (int) resolveLongDialogConfigValue(
                "workspace_rollout_external_kpi_datamart_contract_optional_min_coverage_pct",
                DEFAULT_EXTERNAL_KPI_CONTRACT_OPTIONAL_MIN_COVERAGE_PCT,
                0,
                100);
        String datamartHealthStatus = normalizeDatamartHealthStatus(
                normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_external_kpi_datamart_health_status"))));
        String dashboardStatus = normalizeDatamartHealthStatus(
                normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_external_kpi_dashboard_status"))));
        String dashboardStatusNote = normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_external_kpi_dashboard_status_note")));
        String datamartHealthNote = normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_external_kpi_datamart_health_note")));
        String datamartHealthUpdatedAtRaw = String.valueOf(resolveDialogConfigValue("workspace_rollout_external_kpi_datamart_health_updated_at"));
        long datamartHealthTtlHours = resolveLongDialogConfigValue(
                "workspace_rollout_external_kpi_datamart_health_ttl_hours",
                DEFAULT_EXTERNAL_KPI_DATAMART_HEALTH_TTL_HOURS,
                1,
                24 * 90L);
        String datamartProgramStatus = normalizeDatamartProgramStatus(
                normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_external_kpi_datamart_program_status"))));
        String datamartProgramNote = normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_external_kpi_datamart_program_note")));
        String datamartProgramBlockerUrl = normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_external_kpi_datamart_program_blocker_url")));
        String datamartProgramUpdatedAtRaw = String.valueOf(resolveDialogConfigValue("workspace_rollout_external_kpi_datamart_program_updated_at"));
        long datamartProgramTtlHours = resolveLongDialogConfigValue(
                "workspace_rollout_external_kpi_datamart_program_ttl_hours",
                DEFAULT_EXTERNAL_KPI_DATAMART_PROGRAM_TTL_HOURS,
                1,
                24 * 90L);
        String datamartTargetReadyAtRaw = String.valueOf(resolveDialogConfigValue("workspace_rollout_external_kpi_datamart_target_ready_at"));
        long datamartTimelineGraceHours = resolveLongDialogConfigValue(
                "workspace_rollout_external_kpi_datamart_timeline_grace_hours",
                DEFAULT_EXTERNAL_KPI_DATAMART_TIMELINE_GRACE_HOURS,
                0,
                24 * 30L);
        String dataUpdatedAtRaw = String.valueOf(resolveDialogConfigValue("workspace_rollout_external_kpi_data_updated_at"));
        long dataFreshnessTtlHours = resolveLongDialogConfigValue(
                "workspace_rollout_external_kpi_data_freshness_ttl_hours",
                DEFAULT_EXTERNAL_KPI_DATA_FRESHNESS_TTL_HOURS,
                1,
                24 * 30L);
        long datamartDependencyTicketTtlHours = resolveLongDialogConfigValue(
                "workspace_rollout_external_kpi_datamart_dependency_ticket_ttl_hours",
                DEFAULT_EXTERNAL_KPI_DATAMART_DEPENDENCY_TICKET_TTL_HOURS,
                1,
                24 * 90L);
        if ("null".equalsIgnoreCase(note)) {
            note = "";
        }
        OffsetDateTime reviewedAt = parseReviewTimestamp(reviewedAtRaw);
        boolean reviewTimestampInvalid = StringUtils.hasText(normalizeNullString(reviewedAtRaw)) && reviewedAt == null;
        boolean reviewPresent = reviewedAt != null && StringUtils.hasText(reviewedBy);
        boolean reviewFresh = false;
        long reviewAgeHours = -1;
        if (reviewedAt != null) {
            reviewAgeHours = Math.max(0, java.time.Duration.between(reviewedAt, OffsetDateTime.now(ZoneOffset.UTC)).toHours());
            reviewFresh = reviewAgeHours <= reviewTtlHours;
        }
        boolean reviewReady = !gateEnabled || (reviewPresent && reviewFresh);
        OffsetDateTime dataUpdatedAt = parseReviewTimestamp(dataUpdatedAtRaw);
        boolean dataUpdatedInvalid = StringUtils.hasText(normalizeNullString(dataUpdatedAtRaw)) && dataUpdatedAt == null;
        boolean dataUpdatedPresent = dataUpdatedAt != null;
        boolean dataFresh = false;
        long dataAgeHours = -1;
        if (dataUpdatedAt != null) {
            dataAgeHours = Math.max(0, java.time.Duration.between(dataUpdatedAt, OffsetDateTime.now(ZoneOffset.UTC)).toHours());
            dataFresh = dataAgeHours <= dataFreshnessTtlHours;
        }
        boolean dataFreshnessReady = !dataFreshnessRequired || (dataUpdatedPresent && dataFresh);
        String omnichannelDashboardUrl = normalizeNullString(String.valueOf(resolveDialogConfigValue("cross_product_omnichannel_dashboard_url")));
        String financeDashboardUrl = normalizeNullString(String.valueOf(resolveDialogConfigValue("cross_product_finance_dashboard_url")));
        boolean dashboardLinksPresent = StringUtils.hasText(omnichannelDashboardUrl) && StringUtils.hasText(financeDashboardUrl);
        boolean dashboardLinksReady = !dashboardLinksRequired || dashboardLinksPresent;
        boolean dashboardStatusReady = !dashboardStatusRequired || "healthy".equals(dashboardStatus);
        boolean datamartRunbookUrlPresent = StringUtils.hasText(datamartRunbookUrl);
        boolean datamartRunbookUrlValid = !datamartRunbookUrlPresent || isValidExternalReferenceUrl(datamartRunbookUrl);
        boolean ownerRunbookPresent = StringUtils.hasText(datamartOwner) && datamartRunbookUrlPresent;
        boolean ownerRunbookReady = !ownerRunbookRequired || (ownerRunbookPresent && datamartRunbookUrlValid);
        boolean datamartHealthy = "healthy".equals(datamartHealthStatus);
        boolean datamartHealthReady = !datamartHealthRequired || datamartHealthy;
        OffsetDateTime datamartHealthUpdatedAt = parseReviewTimestamp(datamartHealthUpdatedAtRaw);
        boolean datamartHealthUpdatedInvalid = StringUtils.hasText(normalizeNullString(datamartHealthUpdatedAtRaw))
                && datamartHealthUpdatedAt == null;
        boolean datamartHealthUpdatedPresent = datamartHealthUpdatedAt != null;
        boolean datamartHealthFresh = false;
        long datamartHealthAgeHours = -1;
        if (datamartHealthUpdatedAt != null) {
            datamartHealthAgeHours = Math.max(0, java.time.Duration.between(datamartHealthUpdatedAt, OffsetDateTime.now(ZoneOffset.UTC)).toHours());
            datamartHealthFresh = datamartHealthAgeHours <= datamartHealthTtlHours;
        }
        boolean datamartHealthFreshnessReady = !datamartHealthFreshnessRequired || (datamartHealthUpdatedPresent && datamartHealthFresh);
        boolean datamartProgramBlockerUrlPresent = StringUtils.hasText(datamartProgramBlockerUrl);
        boolean datamartProgramBlockerUrlValid = !datamartProgramBlockerUrlPresent || isValidExternalReferenceUrl(datamartProgramBlockerUrl);
        boolean datamartProgramBlocked = "blocked".equals(datamartProgramStatus);
        boolean datamartProgramBlockerReady = !datamartProgramBlocked
                || (datamartProgramBlockerUrlPresent && datamartProgramBlockerUrlValid);
        boolean datamartProgramReady = !datamartProgramBlockerRequired
                || (!datamartProgramBlocked && datamartProgramBlockerReady);
        OffsetDateTime datamartProgramUpdatedAt = parseReviewTimestamp(datamartProgramUpdatedAtRaw);
        boolean datamartProgramUpdatedInvalid = StringUtils.hasText(normalizeNullString(datamartProgramUpdatedAtRaw))
                && datamartProgramUpdatedAt == null;
        boolean datamartProgramUpdatedPresent = datamartProgramUpdatedAt != null;
        boolean datamartProgramFresh = false;
        long datamartProgramAgeHours = -1;
        if (datamartProgramUpdatedAt != null) {
            datamartProgramAgeHours = Math.max(0, java.time.Duration.between(datamartProgramUpdatedAt, OffsetDateTime.now(ZoneOffset.UTC)).toHours());
            datamartProgramFresh = datamartProgramAgeHours <= datamartProgramTtlHours;
        }
        boolean datamartProgramFreshnessReady = !datamartProgramFreshnessRequired || (datamartProgramUpdatedPresent && datamartProgramFresh);
        OffsetDateTime datamartTargetReadyAt = parseReviewTimestamp(datamartTargetReadyAtRaw);
        boolean datamartTargetReadyInvalid = StringUtils.hasText(normalizeNullString(datamartTargetReadyAtRaw))
                && datamartTargetReadyAt == null;
        boolean datamartTargetPresent = datamartTargetReadyAt != null;
        boolean datamartTargetOverdue = false;
        long datamartTimelineHoursToTarget = Long.MIN_VALUE;
        if (datamartTargetReadyAt != null) {
            OffsetDateTime overdueThreshold = datamartTargetReadyAt.plusHours(datamartTimelineGraceHours);
            datamartTargetOverdue = OffsetDateTime.now(ZoneOffset.UTC).isAfter(overdueThreshold)
                    && !"ready".equals(datamartProgramStatus);
            datamartTimelineHoursToTarget = java.time.Duration.between(OffsetDateTime.now(ZoneOffset.UTC), overdueThreshold).toHours();
        }
        boolean datamartTimelineReady = !datamartTimelineRequired
                || "ready".equals(datamartProgramStatus)
                || (datamartTargetPresent && !datamartTargetOverdue);
        boolean datamartDependencyTicketPresent = StringUtils.hasText(datamartDependencyTicketUrl);
        boolean datamartDependencyTicketValid = !datamartDependencyTicketPresent || isValidExternalReferenceUrl(datamartDependencyTicketUrl);
        OffsetDateTime datamartDependencyTicketUpdatedAt = parseReviewTimestamp(datamartDependencyTicketUpdatedAtRaw);
        boolean datamartDependencyTicketUpdatedInvalid = StringUtils.hasText(normalizeNullString(datamartDependencyTicketUpdatedAtRaw))
                && datamartDependencyTicketUpdatedAt == null;
        boolean datamartDependencyTicketUpdatedPresent = datamartDependencyTicketUpdatedAt != null;
        boolean datamartDependencyTicketFresh = false;
        long datamartDependencyTicketAgeHours = -1;
        if (datamartDependencyTicketUpdatedAt != null) {
            datamartDependencyTicketAgeHours = Math.max(0, java.time.Duration.between(datamartDependencyTicketUpdatedAt, OffsetDateTime.now(ZoneOffset.UTC)).toHours());
            datamartDependencyTicketFresh = datamartDependencyTicketAgeHours <= datamartDependencyTicketTtlHours;
        }
        boolean datamartDependencyTicketFreshnessReady = !datamartDependencyTicketFreshnessRequired
                || (datamartDependencyTicketUpdatedPresent && datamartDependencyTicketFresh);
        boolean datamartDependencyTicketReady = !datamartDependencyTicketRequired
                || (datamartDependencyTicketPresent && datamartDependencyTicketValid);
        boolean datamartDependencyTicketOwnerPresent = StringUtils.hasText(datamartDependencyTicketOwner);
        boolean datamartDependencyTicketOwnerReady = !datamartDependencyTicketOwnerRequired || datamartDependencyTicketOwnerPresent;
        boolean datamartDependencyTicketOwnerContactPresent = StringUtils.hasText(datamartDependencyTicketOwnerContact);
        boolean datamartDependencyTicketOwnerContactReady = !datamartDependencyTicketOwnerContactRequired || datamartDependencyTicketOwnerContactPresent;
        boolean datamartDependencyTicketOwnerContactActionable = isValidOwnerContact(datamartDependencyTicketOwnerContact);
        boolean datamartDependencyTicketOwnerContactActionableReady = !datamartDependencyTicketOwnerContactActionableRequired
                || datamartDependencyTicketOwnerContactActionable;
        Set<String> datamartContractMissingMandatoryFields = datamartContractMandatoryFields.stream()
                .filter(field -> !datamartContractAvailableFields.contains(field))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        Set<String> datamartContractMissingOptionalFields = datamartContractOptionalFields.stream()
                .filter(field -> !datamartContractAvailableFields.contains(field))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        Set<String> datamartContractOverlappingFields = datamartContractMandatoryFields.stream()
                .filter(datamartContractOptionalFields::contains)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        Set<String> datamartContractFields = new java.util.LinkedHashSet<>(datamartContractMandatoryFields);
        datamartContractFields.addAll(datamartContractOptionalFields);
        Set<String> datamartContractAvailableOutsideFields = datamartContractAvailableFields.stream()
                .filter(field -> !datamartContractFields.contains(field))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        boolean datamartContractConfigurationConflict = !datamartContractOverlappingFields.isEmpty();
        int datamartContractMandatoryCoveragePct = calculateContractCoveragePercent(
                datamartContractMandatoryFields,
                datamartContractMissingMandatoryFields);
        int datamartContractOptionalCoveragePct = calculateContractCoveragePercent(
                datamartContractOptionalFields,
                datamartContractMissingOptionalFields);
        int datamartContractBlockingGapCount = datamartContractMissingMandatoryFields.size();
        int datamartContractNonBlockingGapCount = datamartContractMissingOptionalFields.size();
        String datamartContractGapSeverity = resolveDatamartContractGapSeverity(
                datamartContractBlockingGapCount,
                datamartContractNonBlockingGapCount);
        boolean datamartContractOptionalCoverageGateActive = datamartContractRequired
                && datamartContractOptionalCoverageRequired;
        boolean datamartContractOptionalCoverageReady = !datamartContractOptionalCoverageGateActive
                || datamartContractOptionalCoveragePct >= datamartContractOptionalMinCoveragePct;
        boolean datamartContractReady = (!datamartContractRequired || datamartContractMissingMandatoryFields.isEmpty())
                && datamartContractOptionalCoverageReady
                && !datamartContractConfigurationConflict;
        boolean readyForDecision = !gateEnabled || (omnichannelReady
                && financeReady
                && reviewReady
                && dataFreshnessReady
                && dashboardLinksReady
                && dashboardStatusReady
                && ownerRunbookReady
                && datamartHealthReady
                && datamartHealthFreshnessReady
                && datamartProgramReady
                && datamartProgramFreshnessReady
                && datamartTimelineReady
                && datamartDependencyTicketReady
                && datamartDependencyTicketOwnerReady
                && datamartDependencyTicketOwnerContactReady
                && datamartDependencyTicketOwnerContactActionableReady
                && datamartDependencyTicketFreshnessReady
                && datamartContractReady);

        java.util.List<String> datamartRiskReasons = new java.util.ArrayList<>();
        if (!ownerRunbookReady) {
            datamartRiskReasons.add("owner_runbook_missing_or_invalid");
        }
        if (!datamartHealthReady) {
            datamartRiskReasons.add("datamart_health_unhealthy");
        }
        if (!datamartHealthFreshnessReady) {
            datamartRiskReasons.add("datamart_health_stale");
        }
        if (!datamartProgramReady) {
            datamartRiskReasons.add("datamart_program_blocked");
        }
        if (!datamartProgramFreshnessReady) {
            datamartRiskReasons.add("datamart_program_status_stale");
        }
        if (!datamartTimelineReady) {
            datamartRiskReasons.add("datamart_timeline_overdue");
        }
        if (!datamartDependencyTicketReady) {
            datamartRiskReasons.add("dependency_ticket_missing_or_invalid");
        }
        if (!datamartDependencyTicketFreshnessReady) {
            datamartRiskReasons.add("dependency_ticket_stale");
        }
        if (reviewTimestampInvalid) {
            datamartRiskReasons.add("review_timestamp_invalid");
        }
        if (dataUpdatedInvalid) {
            datamartRiskReasons.add("data_updated_timestamp_invalid");
        }
        if (datamartHealthUpdatedInvalid) {
            datamartRiskReasons.add("datamart_health_timestamp_invalid");
        }
        if (datamartProgramUpdatedInvalid) {
            datamartRiskReasons.add("datamart_program_timestamp_invalid");
        }
        if (datamartTargetReadyInvalid) {
            datamartRiskReasons.add("datamart_target_timestamp_invalid");
        }
        if (datamartDependencyTicketUpdatedInvalid) {
            datamartRiskReasons.add("dependency_ticket_timestamp_invalid");
        }
        if (!datamartDependencyTicketOwnerReady) {
            datamartRiskReasons.add("dependency_ticket_owner_missing");
        }
        if (!datamartDependencyTicketOwnerContactReady) {
            datamartRiskReasons.add("dependency_ticket_owner_contact_missing");
        }
        if (!datamartDependencyTicketOwnerContactActionableReady) {
            datamartRiskReasons.add("dependency_ticket_owner_contact_not_actionable");
        }
        if (!datamartContractMissingMandatoryFields.isEmpty()) {
            datamartRiskReasons.add("datamart_contract_missing_mandatory_fields");
        }
        if (datamartContractConfigurationConflict) {
            datamartRiskReasons.add("datamart_contract_configuration_conflict");
        }
        if (datamartContractOptionalCoverageGateActive && !datamartContractOptionalCoverageReady) {
            datamartRiskReasons.add("datamart_contract_optional_coverage_below_threshold");
        }

        String datamartRiskLevel = "low";
        if (!datamartRiskReasons.isEmpty()) {
            datamartRiskLevel = datamartRiskReasons.size() >= 3 ? "high" : "medium";
        }
        if (datamartProgramBlocked || datamartTargetOverdue) {
            datamartRiskLevel = "high";
        }
        signal.put("enabled", gateEnabled);
        signal.put("omnichannel_ready", omnichannelReady);
        signal.put("finance_ready", financeReady);
        signal.put("datamart_owner", normalizeNullString(datamartOwner));
        signal.put("datamart_runbook_url", normalizeNullString(datamartRunbookUrl));
        signal.put("datamart_dependency_ticket_required", datamartDependencyTicketRequired);
        signal.put("datamart_dependency_ticket_url", datamartDependencyTicketUrl);
        signal.put("datamart_dependency_ticket_owner_required", datamartDependencyTicketOwnerRequired);
        signal.put("datamart_dependency_ticket_owner", datamartDependencyTicketOwner);
        signal.put("datamart_dependency_ticket_owner_present", datamartDependencyTicketOwnerPresent);
        signal.put("datamart_dependency_ticket_owner_ready", datamartDependencyTicketOwnerReady);
        signal.put("datamart_dependency_ticket_owner_contact_required", datamartDependencyTicketOwnerContactRequired);
        signal.put("datamart_dependency_ticket_owner_contact", datamartDependencyTicketOwnerContact);
        signal.put("datamart_dependency_ticket_owner_contact_present", datamartDependencyTicketOwnerContactPresent);
        signal.put("datamart_dependency_ticket_owner_contact_ready", datamartDependencyTicketOwnerContactReady);
        signal.put("datamart_dependency_ticket_owner_contact_actionable_required", datamartDependencyTicketOwnerContactActionableRequired);
        signal.put("datamart_dependency_ticket_owner_contact_actionable", datamartDependencyTicketOwnerContactActionable);
        signal.put("datamart_dependency_ticket_owner_contact_actionable_ready", datamartDependencyTicketOwnerContactActionableReady);
        signal.put("datamart_contract_required", datamartContractRequired);
        signal.put("datamart_contract_version", StringUtils.hasText(datamartContractVersion) ? datamartContractVersion : DEFAULT_EXTERNAL_KPI_CONTRACT_VERSION);
        signal.put("datamart_contract_mandatory_fields", new ArrayList<>(datamartContractMandatoryFields));
        signal.put("datamart_contract_optional_fields", new ArrayList<>(datamartContractOptionalFields));
        signal.put("datamart_contract_available_fields", new ArrayList<>(datamartContractAvailableFields));
        signal.put("datamart_contract_missing_mandatory_fields", new ArrayList<>(datamartContractMissingMandatoryFields));
        signal.put("datamart_contract_missing_optional_fields", new ArrayList<>(datamartContractMissingOptionalFields));
        signal.put("datamart_contract_overlapping_fields", new ArrayList<>(datamartContractOverlappingFields));
        signal.put("datamart_contract_available_outside_fields", new ArrayList<>(datamartContractAvailableOutsideFields));
        signal.put("datamart_contract_configuration_conflict", datamartContractConfigurationConflict);
        signal.put("datamart_contract_mandatory_coverage_pct", datamartContractMandatoryCoveragePct);
        signal.put("datamart_contract_optional_coverage_pct", datamartContractOptionalCoveragePct);
        signal.put("datamart_contract_blocking_gap_count", datamartContractBlockingGapCount);
        signal.put("datamart_contract_non_blocking_gap_count", datamartContractNonBlockingGapCount);
        signal.put("datamart_contract_gap_severity", datamartContractGapSeverity);
        signal.put("datamart_contract_optional_coverage_required", datamartContractOptionalCoverageRequired);
        signal.put("datamart_contract_optional_coverage_gate_active", datamartContractOptionalCoverageGateActive);
        signal.put("datamart_contract_optional_min_coverage_pct", datamartContractOptionalMinCoveragePct);
        signal.put("datamart_contract_optional_coverage_ready", datamartContractOptionalCoverageReady);
        signal.put("datamart_contract_ready", datamartContractReady);
        signal.put("datamart_dependency_ticket_present", datamartDependencyTicketPresent);
        signal.put("datamart_dependency_ticket_valid", datamartDependencyTicketValid);
        signal.put("datamart_dependency_ticket_ready", datamartDependencyTicketReady);
        signal.put("datamart_dependency_ticket_freshness_required", datamartDependencyTicketFreshnessRequired);
        signal.put("datamart_dependency_ticket_updated_at", datamartDependencyTicketUpdatedAt != null ? datamartDependencyTicketUpdatedAt.toString() : "");
        signal.put("datamart_dependency_ticket_ttl_hours", datamartDependencyTicketTtlHours);
        signal.put("datamart_dependency_ticket_updated_present", datamartDependencyTicketUpdatedPresent);
        signal.put("datamart_dependency_ticket_updated_timestamp_invalid", datamartDependencyTicketUpdatedInvalid);
        signal.put("dependency_ticket_timestamp_invalid", datamartDependencyTicketUpdatedInvalid);
        signal.put("datamart_dependency_ticket_fresh", datamartDependencyTicketFresh);
        signal.put("datamart_dependency_ticket_age_hours", datamartDependencyTicketAgeHours);
        signal.put("datamart_dependency_ticket_freshness_ready", datamartDependencyTicketFreshnessReady);
        signal.put("reviewed_by", normalizeNullString(reviewedBy));
        signal.put("reviewed_at", reviewedAt != null ? reviewedAt.toString() : "");
        signal.put("review_ttl_hours", reviewTtlHours);
        signal.put("review_present", reviewPresent);
        signal.put("review_timestamp_invalid", reviewTimestampInvalid);
        signal.put("review_fresh", reviewFresh);
        signal.put("review_age_hours", reviewAgeHours);
        signal.put("data_freshness_required", dataFreshnessRequired);
        signal.put("data_updated_at", dataUpdatedAt != null ? dataUpdatedAt.toString() : "");
        signal.put("data_freshness_ttl_hours", dataFreshnessTtlHours);
        signal.put("data_updated_present", dataUpdatedPresent);
        signal.put("data_updated_timestamp_invalid", dataUpdatedInvalid);
        signal.put("data_fresh", dataFresh);
        signal.put("data_age_hours", dataAgeHours);
        signal.put("dashboard_links_required", dashboardLinksRequired);
        signal.put("dashboard_links_present", dashboardLinksPresent);
        signal.put("dashboard_links_ready", dashboardLinksReady);
        signal.put("dashboard_status_required", dashboardStatusRequired);
        signal.put("dashboard_status", dashboardStatus);
        signal.put("dashboard_status_note", dashboardStatusNote);
        signal.put("dashboard_status_ready", dashboardStatusReady);
        signal.put("owner_runbook_required", ownerRunbookRequired);
        signal.put("owner_runbook_present", ownerRunbookPresent);
        signal.put("datamart_runbook_url_present", datamartRunbookUrlPresent);
        signal.put("datamart_runbook_url_valid", datamartRunbookUrlValid);
        signal.put("owner_runbook_ready", ownerRunbookReady);
        signal.put("datamart_health_required", datamartHealthRequired);
        signal.put("datamart_health_status", datamartHealthStatus);
        signal.put("datamart_health_note", datamartHealthNote);
        signal.put("datamart_health_ready", datamartHealthReady);
        signal.put("datamart_health_freshness_required", datamartHealthFreshnessRequired);
        signal.put("datamart_health_updated_at", datamartHealthUpdatedAt != null ? datamartHealthUpdatedAt.toString() : "");
        signal.put("datamart_health_ttl_hours", datamartHealthTtlHours);
        signal.put("datamart_health_updated_present", datamartHealthUpdatedPresent);
        signal.put("datamart_health_updated_timestamp_invalid", datamartHealthUpdatedInvalid);
        signal.put("datamart_health_timestamp_invalid", datamartHealthUpdatedInvalid);
        signal.put("datamart_health_fresh", datamartHealthFresh);
        signal.put("datamart_health_age_hours", datamartHealthAgeHours);
        signal.put("datamart_health_freshness_ready", datamartHealthFreshnessReady);
        signal.put("datamart_program_blocker_required", datamartProgramBlockerRequired);
        signal.put("datamart_program_status", datamartProgramStatus);
        signal.put("datamart_program_note", datamartProgramNote);
        signal.put("datamart_program_blocker_url", datamartProgramBlockerUrl);
        signal.put("datamart_program_blocked", datamartProgramBlocked);
        signal.put("datamart_program_blocker_url_present", datamartProgramBlockerUrlPresent);
        signal.put("datamart_program_blocker_url_valid", datamartProgramBlockerUrlValid);
        signal.put("datamart_program_blocker_ready", datamartProgramBlockerReady);
        signal.put("datamart_program_ready", datamartProgramReady);
        signal.put("datamart_program_freshness_required", datamartProgramFreshnessRequired);
        signal.put("datamart_program_updated_at", datamartProgramUpdatedAt != null ? datamartProgramUpdatedAt.toString() : "");
        signal.put("datamart_program_ttl_hours", datamartProgramTtlHours);
        signal.put("datamart_program_updated_present", datamartProgramUpdatedPresent);
        signal.put("datamart_program_updated_timestamp_invalid", datamartProgramUpdatedInvalid);
        signal.put("datamart_program_timestamp_invalid", datamartProgramUpdatedInvalid);
        signal.put("datamart_program_fresh", datamartProgramFresh);
        signal.put("datamart_program_age_hours", datamartProgramAgeHours);
        signal.put("datamart_program_freshness_ready", datamartProgramFreshnessReady);
        signal.put("datamart_timeline_required", datamartTimelineRequired);
        signal.put("datamart_target_ready_at", datamartTargetReadyAt != null ? datamartTargetReadyAt.toString() : "");
        signal.put("datamart_timeline_grace_hours", datamartTimelineGraceHours);
        signal.put("datamart_target_present", datamartTargetPresent);
        signal.put("datamart_target_timestamp_invalid", datamartTargetReadyInvalid);
        signal.put("datamart_target_overdue", datamartTargetOverdue);
        signal.put("datamart_timeline_hours_to_target", datamartTimelineHoursToTarget);
        signal.put("datamart_timeline_ready", datamartTimelineReady);
        signal.put("datamart_risk_level", datamartRiskLevel);
        signal.put("datamart_risk_reasons", datamartRiskReasons);
        signal.put("ready_for_decision", readyForDecision);
        signal.put("note", note != null ? note.trim() : "");
        return signal;
    }


    private int calculateContractCoveragePercent(Set<String> contractFields, Set<String> missingFields) {
        if (contractFields == null || contractFields.isEmpty()) {
            return 100;
        }
        int covered = Math.max(0, contractFields.size() - (missingFields == null ? 0 : missingFields.size()));
        return (int) Math.round((covered * 100.0d) / contractFields.size());
    }

    private String resolveDatamartContractGapSeverity(int blockingGapCount, int nonBlockingGapCount) {
        if (blockingGapCount > 0 && nonBlockingGapCount > 0) {
            return "mixed";
        }
        if (blockingGapCount > 0) {
            return "blocking";
        }
        if (nonBlockingGapCount > 0) {
            return "non_blocking";
        }
        return "none";
    }

    private boolean isValidExternalReferenceUrl(String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            return false;
        }
        try {
            URI parsed = URI.create(rawUrl.trim());
            String scheme = parsed.getScheme();
            if (!StringUtils.hasText(scheme)) {
                return false;
            }
            String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
            if (!"http".equals(normalizedScheme) && !"https".equals(normalizedScheme)) {
                return false;
            }
            return StringUtils.hasText(parsed.getHost());
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private boolean isValidOwnerContact(String rawContact) {
        String contact = normalizeNullString(rawContact);
        if (!StringUtils.hasText(contact)) {
            return false;
        }
        if (contact.startsWith("@") && contact.length() > 1) {
            return true;
        }
        if (contact.startsWith("mailto:")) {
            return contact.length() > "mailto:".length() && contact.substring("mailto:".length()).contains("@");
        }
        if (contact.startsWith("slack://")) {
            return contact.length() > "slack://".length();
        }
        if (isValidExternalReferenceUrl(contact)) {
            return true;
        }
        int atIndex = contact.indexOf('@');
        return atIndex > 0 && atIndex < contact.length() - 1;
    }

    private String normalizeDatamartProgramStatus(String rawValue) {
        String normalized = normalizeNullString(rawValue).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "ready", "in_progress", "blocked" -> normalized;
            default -> "unknown";
        };
    }

    private String normalizeDatamartHealthStatus(String rawValue) {
        String normalized = normalizeNullString(rawValue).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "healthy", "degraded", "down" -> normalized;
            default -> "unknown";
        };
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

    private long resolveLongDialogConfigValue(String key, long fallback, long minInclusive, long maxInclusive) {
        Object value = resolveDialogConfigValue(key);
        if (value == null) {
            return fallback;
        }
        long parsed;
        if (value instanceof Number number) {
            parsed = number.longValue();
        } else {
            try {
                parsed = Long.parseLong(String.valueOf(value).trim());
            } catch (Exception ignored) {
                return fallback;
            }
        }
        if (parsed < minInclusive || parsed > maxInclusive) {
            return fallback;
        }
        return parsed;
    }

    private Long resolveNullableLongDialogConfigValue(String key, long minInclusive, long maxInclusive) {
        Object value = resolveDialogConfigValue(key);
        if (value == null) {
            return null;
        }
        long parsed;
        if (value instanceof Number number) {
            parsed = number.longValue();
        } else {
            try {
                parsed = Long.parseLong(String.valueOf(value).trim());
            } catch (Exception ignored) {
                return null;
            }
        }
        if (parsed < minInclusive || parsed > maxInclusive) {
            return null;
        }
        return parsed;
    }

    private boolean resolveBooleanDialogConfigValue(String key, boolean fallback) {
        Object value = resolveDialogConfigValue(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String normalized = String.valueOf(value).trim().toLowerCase();
        if (!StringUtils.hasText(normalized)) {
            return fallback;
        }
        if ("true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized) || "0".equals(normalized) || "no".equals(normalized)) {
            return false;
        }
        return fallback;
    }

    private Set<String> parseExternalKpiContractFields(Object rawValue, Set<String> fallback) {
        if (rawValue == null) {
            return fallback;
        }
        List<String> values = new ArrayList<>();
        if (rawValue instanceof List<?> list) {
            for (Object item : list) {
                values.add(String.valueOf(item));
            }
        } else {
            values.addAll(List.of(String.valueOf(rawValue).split(",")));
        }
        Set<String> normalized = values.stream()
                .map(value -> normalizeNullString(String.valueOf(value)).toLowerCase(Locale.ROOT))
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        return normalized.isEmpty() ? fallback : normalized;
    }

    private Object resolveDialogConfigValue(String key) {
        Map<String, Object> settings = sharedConfigService.loadSettings();
        if (settings == null || settings.isEmpty()) {
            return null;
        }
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> map)) {
            return null;
        }
        return map.get(key);
    }

}
