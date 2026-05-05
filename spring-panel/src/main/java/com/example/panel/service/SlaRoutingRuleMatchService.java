package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

@Service
public class SlaRoutingRuleMatchService {

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
