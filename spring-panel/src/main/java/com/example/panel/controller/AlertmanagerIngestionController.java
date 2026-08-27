package com.example.panel.controller;

import com.example.panel.model.observability.AlertmanagerWebhookPayload;
import com.example.panel.security.AlertmanagerIngestionGuardService;
import com.example.panel.service.AlertmanagerIngestionService;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/observability")
public class AlertmanagerIngestionController {

    private final AlertmanagerIngestionGuardService guardService;
    private final AlertmanagerIngestionService ingestionService;

    public AlertmanagerIngestionController(AlertmanagerIngestionGuardService guardService,
                                           AlertmanagerIngestionService ingestionService) {
        this.guardService = guardService;
        this.ingestionService = ingestionService;
    }

    @PostMapping("/alertmanager")
    public ResponseEntity<Map<String, Object>> ingest(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestBody AlertmanagerWebhookPayload payload
    ) {
        guardService.authorize(authorization);
        return ResponseEntity.ok(ingestionService.ingest(payload));
    }
}
