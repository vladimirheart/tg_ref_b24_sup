package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class SlaRoutingRuleRequestMatchService {

    public boolean matches(SlaRoutingRuleTypes.AutoAssignRule rule, String candidateRequestNumber) {
        if (rule == null) {
            return false;
        }
        if (rule.requestPrefixes() != null && !rule.requestPrefixes().isEmpty()) {
            String requestValue = candidateRequestNumber == null ? null : candidateRequestNumber.toLowerCase(Locale.ROOT);
            boolean matched = requestValue != null && rule.requestPrefixes().stream().anyMatch(requestValue::startsWith);
            if (!matched) {
                return false;
            }
        }
        if (rule.excludeRequestPrefixes() != null && !rule.excludeRequestPrefixes().isEmpty()) {
            String requestValue = candidateRequestNumber == null ? null : candidateRequestNumber.toLowerCase(Locale.ROOT);
            if (requestValue != null && rule.excludeRequestPrefixes().stream().anyMatch(requestValue::startsWith)) {
                return false;
            }
        }
        return true;
    }

    public boolean isEmptyRule(SlaRoutingRuleTypes.AutoAssignRule rule) {
        return rule == null || (rule.requestPrefixes().isEmpty() && rule.excludeRequestPrefixes().isEmpty());
    }
}
