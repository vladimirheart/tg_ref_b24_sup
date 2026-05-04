package com.example.panel.service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class SlaRoutingRuleTypes {

    private SlaRoutingRuleTypes() {
    }

    public record AutoAssignRuleDefinition(AutoAssignRule rule,
                                           String ruleId,
                                           String layer,
                                           String owner,
                                           Instant reviewedAtUtc,
                                           boolean reviewedAtInvalid) {
    }

    public enum PoolAssignStrategy {
        HASH_BY_TICKET,
        ROUND_ROBIN,
        LEAST_LOADED
    }

    public enum CategoryMatchMode {
        ANY,
        ALL
    }

    public record AutoAssignRule(Set<String> channels,
                                 Set<String> businesses,
                                 Set<String> locations,
                                 Set<String> clientStatuses,
                                 Set<String> categories,
                                 Set<String> excludedCategories,
                                 CategoryMatchMode categoryMatchMode,
                                 Boolean matchHasCategories,
                                 Integer unreadMin,
                                 Integer unreadMax,
                                 Integer ratingMin,
                                 Integer ratingMax,
                                 Long minutesLeftLte,
                                 Long minutesLeftGte,
                                 Set<String> slaStates,
                                 Set<String> requestPrefixes,
                                 Set<String> excludeRequestPrefixes,
                                 int priority,
                                 String assignee,
                                 List<String> assigneePool,
                                 String routeName,
                                 PoolAssignStrategy poolAssignStrategy) {
        public boolean matches(String candidateChannel,
                               String candidateBusiness,
                               String candidateLocation,
                               Set<String> candidateCategories,
                               String candidateClientStatus,
                               Integer candidateUnreadCount,
                               Integer candidateRating,
                               Long candidateMinutesLeft,
                               String candidateSlaState,
                               String candidateRequestNumber) {
            if (channels != null && !channels.isEmpty() && (candidateChannel == null || !channels.contains(candidateChannel))) {
                return false;
            }
            if (businesses != null && !businesses.isEmpty() && (candidateBusiness == null || !businesses.contains(candidateBusiness))) {
                return false;
            }
            if (locations != null && !locations.isEmpty() && (candidateLocation == null || !locations.contains(candidateLocation))) {
                return false;
            }
            if (clientStatuses != null && !clientStatuses.isEmpty() && (candidateClientStatus == null || !clientStatuses.contains(candidateClientStatus))) {
                return false;
            }
            if (categories != null && !categories.isEmpty()) {
                if (candidateCategories == null || candidateCategories.isEmpty()) {
                    return false;
                }
                boolean categoryMatched = categoryMatchMode == CategoryMatchMode.ALL
                        ? categories.stream().allMatch(candidateCategories::contains)
                        : categories.stream().anyMatch(candidateCategories::contains);
                if (!categoryMatched) {
                    return false;
                }
            }
            if (excludedCategories != null && !excludedCategories.isEmpty()
                    && candidateCategories != null && !candidateCategories.isEmpty()
                    && excludedCategories.stream().anyMatch(candidateCategories::contains)) {
                return false;
            }
            if (matchHasCategories != null) {
                boolean hasCategories = candidateCategories != null && !candidateCategories.isEmpty();
                if (matchHasCategories != hasCategories) {
                    return false;
                }
            }
            if (unreadMin != null && (candidateUnreadCount == null || candidateUnreadCount < unreadMin)) {
                return false;
            }
            if (unreadMax != null && (candidateUnreadCount == null || candidateUnreadCount > unreadMax)) {
                return false;
            }
            if (ratingMin != null && (candidateRating == null || candidateRating < ratingMin)) {
                return false;
            }
            if (ratingMax != null && (candidateRating == null || candidateRating > ratingMax)) {
                return false;
            }
            if (minutesLeftLte != null && (candidateMinutesLeft == null || candidateMinutesLeft > minutesLeftLte)) {
                return false;
            }
            if (minutesLeftGte != null && (candidateMinutesLeft == null || candidateMinutesLeft < minutesLeftGte)) {
                return false;
            }
            if (slaStates != null && !slaStates.isEmpty() && (candidateSlaState == null || !slaStates.contains(candidateSlaState))) {
                return false;
            }
            if (requestPrefixes != null && !requestPrefixes.isEmpty()) {
                String requestValue = candidateRequestNumber == null ? null : candidateRequestNumber.toLowerCase(Locale.ROOT);
                boolean matched = requestValue != null && requestPrefixes.stream().anyMatch(requestValue::startsWith);
                if (!matched) {
                    return false;
                }
            }
            if (excludeRequestPrefixes != null && !excludeRequestPrefixes.isEmpty()) {
                String requestValue = candidateRequestNumber == null ? null : candidateRequestNumber.toLowerCase(Locale.ROOT);
                if (requestValue != null && excludeRequestPrefixes.stream().anyMatch(requestValue::startsWith)) {
                    return false;
                }
            }
            return true;
        }

        public int specificityScore() {
            int score = 0;
            if (channels != null && !channels.isEmpty()) score++;
            if (businesses != null && !businesses.isEmpty()) score++;
            if (locations != null && !locations.isEmpty()) score++;
            if (clientStatuses != null && !clientStatuses.isEmpty()) score++;
            if (categories != null && !categories.isEmpty()) score++;
            if (excludedCategories != null && !excludedCategories.isEmpty()) score++;
            if (matchHasCategories != null) score++;
            if (unreadMin != null) score++;
            if (unreadMax != null) score++;
            if (ratingMin != null) score++;
            if (ratingMax != null) score++;
            if (minutesLeftLte != null) score++;
            if (minutesLeftGte != null) score++;
            if (slaStates != null && !slaStates.isEmpty()) score++;
            if (requestPrefixes != null && !requestPrefixes.isEmpty()) score++;
            if (excludeRequestPrefixes != null && !excludeRequestPrefixes.isEmpty()) score++;
            return score;
        }

        public String route() {
            if (routeName != null) return routeName;
            if (assignee != null) return "rule:" + assignee;
            if (assigneePool != null && !assigneePool.isEmpty()) {
                return "rule_pool:" + assigneePool.get(0) + ":" + poolAssignStrategy.name().toLowerCase(Locale.ROOT);
            }
            return "rule:unknown";
        }
    }
}
