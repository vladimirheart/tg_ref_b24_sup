package com.example.panel.controller;

import com.example.panel.service.DialogAuditService;
import com.example.panel.service.SharedConfigService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analytics")
public class AnalyticsWorkspaceContextController {

    private final DialogAuditService dialogAuditService;
    private final SharedConfigService sharedConfigService;

    public AnalyticsWorkspaceContextController(DialogAuditService dialogAuditService,
                                               SharedConfigService sharedConfigService) {
        this.dialogAuditService = dialogAuditService;
        this.sharedConfigService = sharedConfigService;
    }

    @PostMapping(value = "/workspace-context/standard", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('PAGE_ANALYTICS')")
    public ResponseEntity<?> updateWorkspaceContextStandard(
            @RequestBody(required = false) AnalyticsControllerSupport.WorkspaceContextStandardRequest request,
            Authentication authentication) {
        String reviewedBy = AnalyticsControllerSupport.normalize(String.valueOf(request != null ? request.reviewedBy() : null));
        if (reviewedBy == null) {
            reviewedBy = AnalyticsControllerSupport.resolveActor(authentication, null);
        }
        String actor = AnalyticsControllerSupport.resolveActor(authentication, reviewedBy);
        String reviewedAtRaw = AnalyticsControllerSupport.normalize(String.valueOf(request != null ? request.reviewedAtUtc() : null));

        OffsetDateTime reviewedAtUtc;
        if (reviewedAtRaw == null) {
            reviewedAtUtc = OffsetDateTime.now(ZoneOffset.UTC);
        } else {
            reviewedAtUtc = AnalyticsControllerSupport.parseUtcTimestamp(reviewedAtRaw);
            if (reviewedAtUtc == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "reviewed_at_utc must be a valid UTC timestamp (ISO-8601)"));
            }
        }

        List<String> scenarios = AnalyticsControllerSupport.sanitizeStringList(request != null ? request.scenarios() : null);
        List<String> mandatoryFields = AnalyticsControllerSupport.sanitizeStringList(request != null ? request.mandatoryFields() : null);
        Map<String, List<String>> scenarioMandatoryFields = AnalyticsControllerSupport.sanitizeStringListMap(
                request != null ? request.scenarioMandatoryFields() : null);
        List<String> sourceOfTruth = AnalyticsControllerSupport.sanitizeStringList(request != null ? request.sourceOfTruth() : null);
        Map<String, List<String>> scenarioSourceOfTruth = AnalyticsControllerSupport.sanitizeStringListMap(
                request != null ? request.scenarioSourceOfTruth() : null);
        List<String> priorityBlocks = AnalyticsControllerSupport.sanitizeStringList(request != null ? request.priorityBlocks() : null);
        Map<String, List<String>> scenarioPriorityBlocks = AnalyticsControllerSupport.sanitizeStringListMap(
                request != null ? request.scenarioPriorityBlocks() : null);
        Map<String, AnalyticsControllerSupport.ContextPlaybookPayload> playbooks = AnalyticsControllerSupport.sanitizeContextPlaybookMap(
                request != null ? request.playbooks() : null);
        String note = AnalyticsControllerSupport.truncate(
                AnalyticsControllerSupport.normalize(String.valueOf(request != null ? request.note() : null)),
                500);
        boolean required = request != null && Boolean.TRUE.equals(request.required());

        Map<String, Object> settings = new LinkedHashMap<>(sharedConfigService.loadSettings());
        Map<String, Object> dialogConfig = AnalyticsControllerSupport.mutableDialogConfig(settings);
        dialogConfig.put("workspace_rollout_context_contract_required", required);
        if (scenarios.isEmpty()) {
            dialogConfig.remove("workspace_rollout_context_contract_scenarios");
        } else {
            dialogConfig.put("workspace_rollout_context_contract_scenarios", scenarios);
        }
        if (scenarioMandatoryFields.isEmpty()) {
            dialogConfig.remove("workspace_rollout_context_contract_mandatory_fields_by_scenario");
        } else {
            dialogConfig.put("workspace_rollout_context_contract_mandatory_fields_by_scenario", scenarioMandatoryFields);
        }
        if (mandatoryFields.isEmpty()) {
            dialogConfig.remove("workspace_rollout_context_contract_mandatory_fields");
        } else {
            dialogConfig.put("workspace_rollout_context_contract_mandatory_fields", mandatoryFields);
        }
        if (sourceOfTruth.isEmpty()) {
            dialogConfig.remove("workspace_rollout_context_contract_source_of_truth");
        } else {
            dialogConfig.put("workspace_rollout_context_contract_source_of_truth", sourceOfTruth);
        }
        if (scenarioSourceOfTruth.isEmpty()) {
            dialogConfig.remove("workspace_rollout_context_contract_source_of_truth_by_scenario");
        } else {
            dialogConfig.put("workspace_rollout_context_contract_source_of_truth_by_scenario", scenarioSourceOfTruth);
        }
        if (priorityBlocks.isEmpty()) {
            dialogConfig.remove("workspace_rollout_context_contract_priority_blocks");
        } else {
            dialogConfig.put("workspace_rollout_context_contract_priority_blocks", priorityBlocks);
        }
        if (scenarioPriorityBlocks.isEmpty()) {
            dialogConfig.remove("workspace_rollout_context_contract_priority_blocks_by_scenario");
        } else {
            dialogConfig.put("workspace_rollout_context_contract_priority_blocks_by_scenario", scenarioPriorityBlocks);
        }
        if (playbooks.isEmpty()) {
            dialogConfig.remove("workspace_rollout_context_contract_playbooks");
        } else {
            Map<String, Object> payload = new LinkedHashMap<>();
            playbooks.forEach((key, value) -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("label", value.label());
                item.put("url", value.url());
                if (value.summary() != null) {
                    item.put("summary", value.summary());
                }
                payload.put(key, item);
            });
            dialogConfig.put("workspace_rollout_context_contract_playbooks", payload);
        }
        dialogConfig.put("workspace_rollout_context_contract_reviewed_by", reviewedBy);
        dialogConfig.put("workspace_rollout_context_contract_reviewed_at", reviewedAtUtc.toInstant().toString());
        if (note == null) {
            dialogConfig.remove("workspace_rollout_context_contract_review_note");
        } else {
            dialogConfig.put("workspace_rollout_context_contract_review_note", note);
        }
        settings.put("dialog_config", dialogConfig);
        sharedConfigService.saveSettings(settings);

        dialogAuditService.logWorkspaceTelemetry(
                actor,
                "workspace_context_contract_updated",
                "experiment",
                null,
                "analytics_context_standard",
                null,
                "workspace.v1",
                null,
                "workspace_v1_rollout",
                null,
                null,
                null,
                null,
                null,
                null
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("required", required);
        response.put("reviewed_by", reviewedBy);
        response.put("reviewed_at_utc", reviewedAtUtc.toInstant().toString());
        response.put("scenarios", scenarios);
        response.put("mandatory_fields", mandatoryFields);
        response.put("scenario_mandatory_fields", scenarioMandatoryFields);
        response.put("source_of_truth", sourceOfTruth);
        response.put("scenario_source_of_truth", scenarioSourceOfTruth);
        response.put("priority_blocks", priorityBlocks);
        response.put("scenario_priority_blocks", scenarioPriorityBlocks);
        response.put("playbooks", playbooks);
        response.put("note", note == null ? "" : note);
        return ResponseEntity.ok(response);
    }
}
