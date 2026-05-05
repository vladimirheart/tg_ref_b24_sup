package com.example.panel.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class SlaRoutingLifecycleStateService {

    private final SlaRoutingRuleScalarParserService scalarParserService;

    @Autowired
    public SlaRoutingLifecycleStateService(SlaRoutingRuleScalarParserService scalarParserService) {
        this.scalarParserService = scalarParserService;
    }

    public SlaRoutingLifecycleStateService() {
        this(new SlaRoutingRuleScalarParserService());
    }

    public String normalizeLifecycleState(String... values) {
        for (String value : values) {
            String normalized = scalarParserService.trimToNull(value);
            if (normalized == null) {
                continue;
            }
            String lowered = normalized.toLowerCase(Locale.ROOT);
            switch (lowered) {
                case "open", "new", "waiting_operator", "waiting_client" -> {
                    return "open";
                }
                case "closed", "resolved", "auto_closed" -> {
                    return "closed";
                }
                default -> {
                    return lowered;
                }
            }
        }
        return "unknown";
    }
}
