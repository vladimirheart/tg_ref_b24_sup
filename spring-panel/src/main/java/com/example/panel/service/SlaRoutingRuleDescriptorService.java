package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class SlaRoutingRuleDescriptorService {

    public int specificityScore(SlaRoutingRuleTypes.AutoAssignRule rule) {
        if (rule == null) {
            return 0;
        }
        int score = 0;
        if (rule.channels() != null && !rule.channels().isEmpty()) score++;
        if (rule.businesses() != null && !rule.businesses().isEmpty()) score++;
        if (rule.locations() != null && !rule.locations().isEmpty()) score++;
        if (rule.clientStatuses() != null && !rule.clientStatuses().isEmpty()) score++;
        if (rule.categories() != null && !rule.categories().isEmpty()) score++;
        if (rule.excludedCategories() != null && !rule.excludedCategories().isEmpty()) score++;
        if (rule.matchHasCategories() != null) score++;
        if (rule.unreadMin() != null) score++;
        if (rule.unreadMax() != null) score++;
        if (rule.ratingMin() != null) score++;
        if (rule.ratingMax() != null) score++;
        if (rule.minutesLeftLte() != null) score++;
        if (rule.minutesLeftGte() != null) score++;
        if (rule.slaStates() != null && !rule.slaStates().isEmpty()) score++;
        if (rule.requestPrefixes() != null && !rule.requestPrefixes().isEmpty()) score++;
        if (rule.excludeRequestPrefixes() != null && !rule.excludeRequestPrefixes().isEmpty()) score++;
        return score;
    }

    public String route(SlaRoutingRuleTypes.AutoAssignRule rule) {
        if (rule == null) {
            return "rule:unknown";
        }
        if (rule.routeName() != null) return rule.routeName();
        if (rule.assignee() != null) return "rule:" + rule.assignee();
        if (rule.assigneePool() != null && !rule.assigneePool().isEmpty()) {
            return "rule_pool:" + rule.assigneePool().get(0) + ":" + rule.poolAssignStrategy().name().toLowerCase(Locale.ROOT);
        }
        return "rule:unknown";
    }

    public String formatAssigneeTarget(SlaRoutingRuleTypes.AutoAssignRule rule) {
        if (rule == null) return "";
        if (rule.assignee() != null) return rule.assignee();
        if (rule.assigneePool() != null && !rule.assigneePool().isEmpty()) return String.join(", ", rule.assigneePool());
        return "";
    }
}
