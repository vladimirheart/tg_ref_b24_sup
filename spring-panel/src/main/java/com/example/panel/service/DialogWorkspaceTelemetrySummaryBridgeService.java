package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
public class DialogWorkspaceTelemetrySummaryBridgeService {

    private final DialogService dialogService;

    public DialogWorkspaceTelemetrySummaryBridgeService(DialogService dialogService) {
        this.dialogService = dialogService;
    }

    public Map<String, Object> loadSummary(int days, String experimentName) {
        return dialogService.loadWorkspaceTelemetrySummary(days, experimentName);
    }

    public Map<String, Object> loadSummary(int days,
                                           String experimentName,
                                           Instant fromUtc,
                                           Instant toUtc) {
        return dialogService.loadWorkspaceTelemetrySummary(days, experimentName, fromUtc, toUtc);
    }
}
