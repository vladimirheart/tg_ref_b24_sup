package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class SlaRoutingRuleBehaviorService {

    public boolean matches(SlaRoutingRuleTypes.AutoAssignRule rule,
                           String candidateChannel,
                           String candidateBusiness,
                           String candidateLocation,
                           Set<String> candidateCategories,
                           String candidateClientStatus,
                           Integer candidateUnreadCount,
                           Integer candidateRating,
                           Long candidateMinutesLeft,
                           String candidateSlaState,
                           String candidateRequestNumber) {
        if (rule == null) {
            return false;
        }
        if (rule.channels() != null && !rule.channels().isEmpty() && (candidateChannel == null || !rule.channels().contains(candidateChannel))) {
            return false;
        }
        if (rule.businesses() != null && !rule.businesses().isEmpty() && (candidateBusiness == null || !rule.businesses().contains(candidateBusiness))) {
            return false;
        }
        if (rule.locations() != null && !rule.locations().isEmpty() && (candidateLocation == null || !rule.locations().contains(candidateLocation))) {
            return false;
        }
        if (rule.clientStatuses() != null && !rule.clientStatuses().isEmpty() && (candidateClientStatus == null || !rule.clientStatuses().contains(candidateClientStatus))) {
            return false;
        }
        if (rule.categories() != null && !rule.categories().isEmpty()) {
            if (candidateCategories == null || candidateCategories.isEmpty()) {
                return false;
            }
            boolean categoryMatched = rule.categoryMatchMode() == SlaRoutingRuleTypes.CategoryMatchMode.ALL
                    ? rule.categories().stream().allMatch(candidateCategories::contains)
                    : rule.categories().stream().anyMatch(candidateCategories::contains);
            if (!categoryMatched) {
                return false;
            }
        }
        if (rule.excludedCategories() != null && !rule.excludedCategories().isEmpty()
                && candidateCategories != null && !candidateCategories.isEmpty()
                && rule.excludedCategories().stream().anyMatch(candidateCategories::contains)) {
            return false;
        }
        if (rule.matchHasCategories() != null) {
            boolean hasCategories = candidateCategories != null && !candidateCategories.isEmpty();
            if (!rule.matchHasCategories().equals(hasCategories)) {
                return false;
            }
        }
        if (rule.unreadMin() != null && (candidateUnreadCount == null || candidateUnreadCount < rule.unreadMin())) {
            return false;
        }
        if (rule.unreadMax() != null && (candidateUnreadCount == null || candidateUnreadCount > rule.unreadMax())) {
            return false;
        }
        if (rule.ratingMin() != null && (candidateRating == null || candidateRating < rule.ratingMin())) {
            return false;
        }
        if (rule.ratingMax() != null && (candidateRating == null || candidateRating > rule.ratingMax())) {
            return false;
        }
        if (rule.minutesLeftLte() != null && (candidateMinutesLeft == null || candidateMinutesLeft > rule.minutesLeftLte())) {
            return false;
        }
        if (rule.minutesLeftGte() != null && (candidateMinutesLeft == null || candidateMinutesLeft < rule.minutesLeftGte())) {
            return false;
        }
        if (rule.slaStates() != null && !rule.slaStates().isEmpty() && (candidateSlaState == null || !rule.slaStates().contains(candidateSlaState))) {
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

    public boolean isEmptyRule(SlaRoutingRuleTypes.AutoAssignRule rule) {
        return rule == null
                || (rule.channels().isEmpty()
                && rule.businesses().isEmpty()
                && rule.locations().isEmpty()
                && rule.clientStatuses().isEmpty()
                && rule.categories().isEmpty()
                && rule.excludedCategories().isEmpty()
                && rule.matchHasCategories() == null
                && rule.unreadMin() == null
                && rule.unreadMax() == null
                && rule.ratingMin() == null
                && rule.ratingMax() == null
                && rule.minutesLeftLte() == null
                && rule.minutesLeftGte() == null
                && rule.slaStates().isEmpty()
                && rule.requestPrefixes().isEmpty()
                && rule.excludeRequestPrefixes().isEmpty());
    }
}
