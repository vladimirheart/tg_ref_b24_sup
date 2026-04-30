package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
public class DialogWorkspaceTelemetrySummaryService {

    private final DialogWorkspaceTelemetrySummaryAssemblerService dialogWorkspaceTelemetrySummaryAssemblerService;

    public DialogWorkspaceTelemetrySummaryService(DialogWorkspaceTelemetrySummaryAssemblerService dialogWorkspaceTelemetrySummaryAssemblerService) {
        this.dialogWorkspaceTelemetrySummaryAssemblerService = dialogWorkspaceTelemetrySummaryAssemblerService;
    }

    public Map<String, Object> loadSummary(int days, String experimentName) {
        return dialogWorkspaceTelemetrySummaryAssemblerService.loadSummary(days, experimentName);
    }

    public Map<String, Object> loadSummary(int days,
                                           String experimentName,
                                           Instant fromUtc,
                                           Instant toUtc) {
        return dialogWorkspaceTelemetrySummaryAssemblerService.loadSummary(days, experimentName, fromUtc, toUtc);
    }
}
