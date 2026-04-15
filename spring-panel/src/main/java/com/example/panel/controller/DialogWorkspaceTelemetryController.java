package com.example.panel.controller;

import com.example.panel.service.DialogWorkspaceTelemetryService;
import com.fasterxml.jackson.annotation.JsonAlias;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dialogs")
public class DialogWorkspaceTelemetryController {

    private final DialogWorkspaceTelemetryService dialogWorkspaceTelemetryService;

    public DialogWorkspaceTelemetryController(DialogWorkspaceTelemetryService dialogWorkspaceTelemetryService) {
        this.dialogWorkspaceTelemetryService = dialogWorkspaceTelemetryService;
    }

    @PostMapping("/workspace-telemetry")
    public ResponseEntity<?> workspaceTelemetry(@RequestBody(required = false) WorkspaceTelemetryRequest request,
                                                Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : "anonymous";
        DialogWorkspaceTelemetryService.WorkspaceTelemetryRequest payload =
                request == null ? null : new DialogWorkspaceTelemetryService.WorkspaceTelemetryRequest(
                        request.eventType(),
                        request.timestamp(),
                        request.eventGroup(),
                        request.ticketId(),
                        request.reason(),
                        request.errorCode(),
                        request.contractVersion(),
                        request.durationMs(),
                        request.experimentName(),
                        request.experimentCohort(),
                        request.operatorSegment(),
                        request.primaryKpis(),
                        request.secondaryKpis(),
                        request.templateId(),
                        request.templateName()
                );
        return dialogWorkspaceTelemetryService.logTelemetry(operator, payload);
    }

    @GetMapping("/workspace-telemetry/summary")
    public ResponseEntity<?> workspaceTelemetrySummary(@RequestParam(name = "days", defaultValue = "7") Integer days,
                                                       @RequestParam(name = "experiment_name", required = false) String experimentName,
                                                       @RequestParam(name = "from_utc", required = false) String fromUtcRaw,
                                                       @RequestParam(name = "to_utc", required = false) String toUtcRaw) {
        return dialogWorkspaceTelemetryService.loadSummary(days, experimentName, fromUtcRaw, toUtcRaw);
    }

    public record WorkspaceTelemetryRequest(@JsonAlias("event_type") String eventType,
                                            String timestamp,
                                            @JsonAlias("event_group") String eventGroup,
                                            @JsonAlias("ticket_id") String ticketId,
                                            String reason,
                                            @JsonAlias("error_code") String errorCode,
                                            @JsonAlias("contract_version") String contractVersion,
                                            @JsonAlias("duration_ms") Long durationMs,
                                            @JsonAlias("experiment_name") String experimentName,
                                            @JsonAlias("experiment_cohort") String experimentCohort,
                                            @JsonAlias("operator_segment") String operatorSegment,
                                            @JsonAlias("primary_kpis") List<String> primaryKpis,
                                            @JsonAlias("secondary_kpis") List<String> secondaryKpis,
                                            @JsonAlias("template_id") String templateId,
                                            @JsonAlias("template_name") String templateName) {
    }
}
