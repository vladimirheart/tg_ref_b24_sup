package com.example.panel.service;

import java.util.Map;

record DialogWorkspaceRolloutSectionResult(
        String key,
        String domain,
        String label,
        String status,
        boolean blocking,
        String rationale,
        String currentValue,
        String expectedValue,
        String recordedAt,
        String note,
        Map<String, Object> payload
) {
}
