package com.example.panel.controller;

import com.example.panel.service.DialogAuditService;
import com.example.panel.service.DialogTriagePreferenceService;
import com.fasterxml.jackson.annotation.JsonAlias;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dialogs")
public class DialogTriagePreferencesController {

    private final DialogTriagePreferenceService dialogTriagePreferenceService;
    private final DialogAuditService dialogAuditService;

    public DialogTriagePreferencesController(DialogTriagePreferenceService dialogTriagePreferenceService,
                                             DialogAuditService dialogAuditService) {
        this.dialogTriagePreferenceService = dialogTriagePreferenceService;
        this.dialogAuditService = dialogAuditService;
    }

    @GetMapping("/triage-preferences")
    public ResponseEntity<?> triagePreferences(Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : null;
        if (!StringUtils.hasText(operator)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "error", "Требуется авторизация"));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("preferences", dialogTriagePreferenceService.loadForOperator(operator));
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/triage-preferences")
    public ResponseEntity<?> updateTriagePreferences(@RequestBody(required = false) TriagePreferencesRequest request,
                                                     Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : null;
        if (!StringUtils.hasText(operator)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "error", "Требуется авторизация"));
        }
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "request body is required"));
        }
        String view = dialogTriagePreferenceService.normalizeView(request.view());
        String sortMode = dialogTriagePreferenceService.normalizeSortMode(request.sortMode());
        Integer slaWindowMinutes = dialogTriagePreferenceService.normalizeSlaWindowMinutes(request.slaWindowMinutes());
        String pageSize = dialogTriagePreferenceService.normalizePageSizePreference(request.pageSize());
        Map<String, Object> savedPreferences = dialogTriagePreferenceService.saveForOperator(
                operator,
                view,
                sortMode,
                slaWindowMinutes,
                pageSize
        );
        String updatedAtUtc = savedPreferences.get("updated_at_utc") instanceof String value
                ? value
                : Instant.now().toString();

        dialogAuditService.logWorkspaceTelemetry(
                operator,
                "triage_preferences_saved",
                "triage",
                null,
                "view=%s;sort=%s;sla=%s;page=%s".formatted(
                        view,
                        sortMode,
                        slaWindowMinutes != null ? slaWindowMinutes : "all",
                        pageSize),
                null,
                "triage_preferences.v1",
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                null,
                null);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("preferences", savedPreferences);
        payload.put("updated_at_utc", updatedAtUtc);
        return ResponseEntity.ok(payload);
    }

    public record TriagePreferencesRequest(@JsonAlias("view") String view,
                                           @JsonAlias("sort_mode") String sortMode,
                                           @JsonAlias("sla_window_minutes") Integer slaWindowMinutes,
                                           @JsonAlias("page_size") String pageSize) {}
}
