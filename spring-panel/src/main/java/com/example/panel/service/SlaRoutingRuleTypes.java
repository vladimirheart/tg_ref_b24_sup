package com.example.panel.service;

import java.time.Instant;
import java.util.List;
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
    }
}
