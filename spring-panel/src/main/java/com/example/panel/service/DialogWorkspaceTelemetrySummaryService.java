package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
public class DialogWorkspaceTelemetrySummaryService {

    private final DialogWorkspaceTelemetrySummaryBridgeService dialogWorkspaceTelemetrySummaryBridgeService;

    public DialogWorkspaceTelemetrySummaryService(DialogWorkspaceTelemetrySummaryBridgeService dialogWorkspaceTelemetrySummaryBridgeService) {
        this.dialogWorkspaceTelemetrySummaryBridgeService = dialogWorkspaceTelemetrySummaryBridgeService;
    }

    public Map<String, Object> loadSummary(int days, String experimentName) {
        return dialogWorkspaceTelemetrySummaryBridgeService.loadSummary(days, experimentName);
    }

    public Map<String, Object> loadSummary(int days,
                                           String experimentName,
                                           Instant fromUtc,
                                           Instant toUtc) {
        return dialogWorkspaceTelemetrySummaryBridgeService.loadSummary(days, experimentName, fromUtc, toUtc);
    }
}
