package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SlaRoutingRuleAuditService {

    public RoutingAuditAnalysis analyze(List<Map<String, Object>> criticalCandidates,
                                        Object rawRules,
                                        Instant generatedAt,
                                        int broadCoveragePct,
                                        boolean requireLayers,
                                        boolean requireOwner,
                                        boolean requireReview,
                                        boolean blockOnConflict,
                                        long reviewTtlHours) {
        List<Map<String, Object>> safeCandidates = criticalCandidates == null ? List.of() : criticalCandidates;
        List<AutoAssignRuleDefinition> definitions = parseAutoAssignRuleDefinitions(rawRules);
        List<AutoAssignRuleDefinition> activeDefinitions = definitions.stream()
                .filter(definition -> definition.rule() != null)
                .toList();

        List<Map<String, Object>> issues = new ArrayList<>();
        List<Map<String, Object>> rules = new ArrayList<>();
        Map<String, RuleUsageStats> usageStatsByRoute = new LinkedHashMap<>();
        Map<String, Set<String>> conflictTicketsByRoute = new LinkedHashMap<>();
        Map<String, Set<String>> tiedRoutesByRoute = new LinkedHashMap<>();
        Map<String, Integer> layerCounts = new LinkedHashMap<>();

        for (AutoAssignRuleDefinition definition : definitions) {
            layerCounts.merge(definition.layer(), 1, Integer::sum);
            usageStatsByRoute.put(definition.ruleId(), new RuleUsageStats());
        }

        for (Map<String, Object> candidate : safeCandidates) {
            List<AutoAssignRuleDefinition> matchedDefinitions = activeDefinitions.stream()
                    .filter(definition -> matchesDefinition(definition, candidate))
                    .toList();
            matchedDefinitions.forEach(definition -> usageStatsByRoute.get(definition.ruleId()).matchedTickets().add(ticketId(candidate)));
            List<AutoAssignRuleDefinition> winners = resolveWinningDefinitions(matchedDefinitions);
            if (!winners.isEmpty()) {
                winners.forEach(definition -> usageStatsByRoute.get(definition.ruleId()).selectedTickets().add(ticketId(candidate)));
            }
            if (winners.size() > 1) {
                Set<String> winnerRouteIds = winners.stream()
                        .map(AutoAssignRuleDefinition::ruleId)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                for (AutoAssignRuleDefinition winner : winners) {
                    conflictTicketsByRoute.computeIfAbsent(winner.ruleId(), key -> new LinkedHashSet<>()).add(ticketId(candidate));
                    tiedRoutesByRoute.computeIfAbsent(winner.ruleId(), key -> new LinkedHashSet<>()).addAll(
                            winnerRouteIds.stream().filter(routeId -> !winner.ruleId().equals(routeId)).toList());
                }
            }
        }

        for (AutoAssignRuleDefinition definition : definitions) {
            RuleUsageStats usageStats = usageStatsByRoute.getOrDefault(definition.ruleId(), new RuleUsageStats());
            int matchedCount = usageStats.matchedTickets().size();
            int selectedCount = usageStats.selectedTickets().size();
            double coverageRate = safeCandidates.isEmpty() ? 0d : (double) matchedCount / safeCandidates.size();
            boolean broadRule = !safeCandidates.isEmpty()
                    && coverageRate >= (broadCoveragePct / 100d)
                    && definition.rule().specificityScore() <= 2;
            boolean unusedRule = matchedCount == 0;
            boolean missingLayer = "legacy".equals(definition.layer());
            boolean ownerMissing = trimToNull(definition.owner()) == null;
            boolean reviewMissing = definition.reviewedAtUtc() == null;
            boolean reviewStale = definition.reviewedAtUtc() != null
                    && definition.reviewedAtUtc().plus(Duration.ofHours(reviewTtlHours)).isBefore(generatedAt);
            boolean hasConflict = conflictTicketsByRoute.containsKey(definition.ruleId());

            List<String> ruleIssues = new ArrayList<>();
            if (hasConflict) {
                ruleIssues.add("ambiguous_overlap");
                issues.add(buildGovernanceIssue("rollout_blocker", blockOnConflict ? "hold" : "attention", "rule_conflict",
                        definition.ruleId(), "Обнаружен конфликт SLA-routing правил с одинаковым приоритетом/specificity.",
                        "tickets=%d".formatted(conflictTicketsByRoute.getOrDefault(definition.ruleId(), Set.of()).size()),
                        conflictTicketsByRoute.getOrDefault(definition.ruleId(), Set.of()).stream().limit(3).toList(),
                        tiedRoutesByRoute.getOrDefault(definition.ruleId(), Set.of()).stream().limit(3).toList()));
            }
            if (broadRule) {
                ruleIssues.add("too_broad");
                issues.add(buildGovernanceIssue("backlog_candidate", "attention", "broad_rule", definition.ruleId(),
                        "Правило покрывает слишком большой процент критичных кейсов и рискует стать catch-all.",
                        "coverage=%.1f%%".formatted(coverageRate * 100d),
                        usageStats.matchedTickets().stream().limit(3).toList(), List.of(definition.layer())));
            }
            if (unusedRule) {
                ruleIssues.add("unused");
                issues.add(buildGovernanceIssue("backlog_candidate", "attention", "unused_rule", definition.ruleId(),
                        "Правило не матчится ни на один критичный кейс и выглядит как конфигурационный долг.",
                        "matched=0", List.of(), List.of(definition.layer())));
            }
            if (requireLayers && missingLayer) {
                ruleIssues.add("layer_missing");
                issues.add(buildGovernanceIssue("backlog_candidate", "attention", "layer_missing", definition.ruleId(),
                        "Для production governance правило должно быть отнесено к layer: global/domain/emergency_override.",
                        "layer=legacy", List.of(), List.of()));
            }
            if (requireOwner && ownerMissing) {
                ruleIssues.add("owner_missing");
                issues.add(buildGovernanceIssue("backlog_candidate", "attention", "owner_missing", definition.ruleId(),
                        "У SLA-routing правила должен быть назначен owner для ревизии и cleanup.",
                        "owner=missing", List.of(), List.of()));
            }
            if (definition.reviewedAtInvalid()) {
                ruleIssues.add("reviewed_at_invalid_utc");
                issues.add(buildGovernanceIssue("rollout_blocker", requireReview ? "hold" : "attention", "review_invalid_utc",
                        definition.ruleId(), "Поле reviewed_at у SLA-routing правила невалидно и должно быть задано в UTC.",
                        "reviewed_at=invalid", List.of(), List.of()));
            } else if (requireReview && reviewMissing) {
                ruleIssues.add("review_missing");
                issues.add(buildGovernanceIssue("rollout_blocker", "hold", "review_missing", definition.ruleId(),
                        "Для routing governance нужен review timestamp в UTC.", "reviewed_at=missing", List.of(), List.of()));
            } else if (requireReview && reviewStale) {
                ruleIssues.add("review_stale");
                issues.add(buildGovernanceIssue("rollout_blocker", "hold", "review_stale", definition.ruleId(),
                        "Review SLA-routing правила устарел и должен быть обновлён.", "ttl_hours=%d".formatted(reviewTtlHours),
                        List.of(), List.of()));
            }

            String status = "ok";
            if (ruleIssues.contains("review_missing") || ruleIssues.contains("review_stale")
                    || ruleIssues.contains("reviewed_at_invalid_utc") || (blockOnConflict && hasConflict)) {
                status = "hold";
            } else if (!ruleIssues.isEmpty()) {
                status = "attention";
            }
            Map<String, Object> rulePayload = new LinkedHashMap<>();
            rulePayload.put("rule_id", definition.ruleId());
            rulePayload.put("layer", definition.layer());
            rulePayload.put("owner", definition.owner() == null ? "" : definition.owner());
            rulePayload.put("reviewed_at_utc", definition.reviewedAtUtc() != null ? definition.reviewedAtUtc().toString() : "");
            rulePayload.put("reviewed_at_invalid_utc", definition.reviewedAtInvalid());
            rulePayload.put("priority", definition.rule().priority());
            rulePayload.put("specificity_score", definition.rule().specificityScore());
            rulePayload.put("matched_candidates", matchedCount);
            rulePayload.put("selected_candidates", selectedCount);
            rulePayload.put("coverage_rate", coverageRate);
            rulePayload.put("route", definition.rule().route());
            rulePayload.put("assignee_target", formatRuleAssigneeTarget(definition.rule()));
            rulePayload.put("issues", ruleIssues);
            rulePayload.put("status", status);
            rules.add(rulePayload);
        }

        Map<String, Long> decisionsByLayer = rules.stream().collect(Collectors.groupingBy(
                row -> String.valueOf(row.get("layer")), LinkedHashMap::new,
                Collectors.summingLong(row -> toLong(row.get("selected_candidates")))));
        Map<String, Long> decisionsByRoute = rules.stream().collect(Collectors.groupingBy(
                row -> String.valueOf(row.get("route")), LinkedHashMap::new,
                Collectors.summingLong(row -> toLong(row.get("selected_candidates")))));
        long conflictingRulesCount = conflictTicketsByRoute.keySet().size();
        long conflictingTicketsCount = conflictTicketsByRoute.values().stream().flatMap(Set::stream).distinct().count();
        long mandatoryIssueTotal = issues.stream().filter(issue -> "rollout_blocker".equals(String.valueOf(issue.get("classification")))).count();
        long advisoryIssueTotal = Math.max(0L, issues.size() - mandatoryIssueTotal);
        long conflictIssueTotal = issues.stream().filter(issue -> String.valueOf(issue.get("type")).contains("conflict")).count();
        long reviewIssueTotal = issues.stream().filter(issue -> String.valueOf(issue.get("type")).contains("review") || String.valueOf(issue.get("type")).contains("decision")).count();
        long ownershipIssueTotal = issues.stream().filter(issue -> String.valueOf(issue.get("type")).contains("owner") || String.valueOf(issue.get("type")).contains("layer")).count();

        return new RoutingAuditAnalysis(definitions.size(), issues, rules, layerCounts, decisionsByLayer, decisionsByRoute,
                conflictTicketsByRoute, conflictingRulesCount, conflictingTicketsCount, mandatoryIssueTotal,
                advisoryIssueTotal, conflictIssueTotal, reviewIssueTotal, ownershipIssueTotal);
    }

    public record RoutingAuditAnalysis(int rulesTotal,
                                       List<Map<String, Object>> issues,
                                       List<Map<String, Object>> rules,
                                       Map<String, Integer> layerCounts,
                                       Map<String, Long> decisionsByLayer,
                                       Map<String, Long> decisionsByRoute,
                                       Map<String, Set<String>> conflictTicketsByRoute,
                                       long conflictingRulesCount,
                                       long conflictingTicketsCount,
                                       long mandatoryIssueTotal,
                                       long advisoryIssueTotal,
                                       long conflictIssueTotal,
                                       long reviewIssueTotal,
                                       long ownershipIssueTotal) {
    }

    private List<AutoAssignRuleDefinition> resolveWinningDefinitions(List<AutoAssignRuleDefinition> matchedDefinitions) {
        if (matchedDefinitions == null || matchedDefinitions.isEmpty()) {
            return List.of();
        }
        int bestSpecificity = matchedDefinitions.stream()
                .map(definition -> definition.rule().specificityScore())
                .max(Integer::compareTo)
                .orElse(Integer.MIN_VALUE);
        int bestPriority = matchedDefinitions.stream()
                .filter(definition -> definition.rule().specificityScore() == bestSpecificity)
                .map(definition -> definition.rule().priority())
                .max(Integer::compareTo)
                .orElse(Integer.MIN_VALUE);
        return matchedDefinitions.stream()
                .filter(definition -> definition.rule().specificityScore() == bestSpecificity)
                .filter(definition -> definition.rule().priority() == bestPriority)
                .toList();
    }

    private List<AutoAssignRuleDefinition> parseAutoAssignRuleDefinitions(Object rawRules) {
        if (!(rawRules instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<AutoAssignRuleDefinition> rules = new ArrayList<>();
        int ruleIndex = 0;
        for (Object item : list) {
            ruleIndex++;
            if (!(item instanceof Map<?, ?> ruleMap)) {
                continue;
            }
            String assignee = trimToNull(String.valueOf(ruleMap.get("assign_to")));
            List<String> assigneePool = parseAssigneePool(ruleMap.get("assign_to_pool"));
            if (assignee == null && assigneePool.isEmpty()) {
                continue;
            }
            Set<String> channels = parseRuleMatchValues(ruleMap.get("match_channel"), ruleMap.get("match_channels"));
            Set<String> businesses = parseRuleMatchValues(ruleMap.get("match_business"), ruleMap.get("match_businesses"));
            Set<String> locations = parseRuleMatchValues(ruleMap.get("match_location"), ruleMap.get("match_locations"));
            Set<String> clientStatuses = parseRuleMatchValues(ruleMap.get("match_client_status"), ruleMap.get("match_client_statuses"));
            Set<String> categories = parseRuleCategories(ruleMap.get("match_category"), ruleMap.get("match_categories"));
            Set<String> excludedCategories = parseRuleCategories(ruleMap.get("exclude_category"), ruleMap.get("exclude_categories"));
            CategoryMatchMode categoryMatchMode = parseCategoryMatchMode(ruleMap.get("match_categories_mode"));
            Boolean matchHasCategories = parseOptionalBoolean(ruleMap.get("match_has_categories"));
            Integer unreadMin = parseOptionalNonNegativeInt(ruleMap.get("match_unread_min"));
            Integer unreadMax = parseOptionalNonNegativeInt(ruleMap.get("match_unread_max"));
            Integer ratingMin = parseOptionalNonNegativeInt(ruleMap.get("match_rating_min"));
            Integer ratingMax = parseOptionalNonNegativeInt(ruleMap.get("match_rating_max"));
            Long minutesLeftLte = parseOptionalLong(ruleMap.get("match_minutes_left_lte"));
            Long minutesLeftGte = parseOptionalLong(ruleMap.get("match_minutes_left_gte"));
            Set<String> slaStates = parseRuleSlaStates(ruleMap.get("match_sla_state"), ruleMap.get("match_sla_states"));
            Set<String> requestPrefixes = parseRuleRequestPrefixes(ruleMap.get("match_request_prefix"), ruleMap.get("match_request_prefixes"));
            Set<String> excludeRequestPrefixes = parseRuleRequestPrefixes(ruleMap.get("exclude_request_prefix"), ruleMap.get("exclude_request_prefixes"));
            int priority = parsePriority(ruleMap.get("priority"));
            if (channels.isEmpty() && businesses.isEmpty() && locations.isEmpty() && clientStatuses.isEmpty()
                    && categories.isEmpty() && excludedCategories.isEmpty() && matchHasCategories == null
                    && unreadMin == null && unreadMax == null && ratingMin == null && ratingMax == null
                    && minutesLeftLte == null && minutesLeftGte == null && slaStates.isEmpty()
                    && requestPrefixes.isEmpty() && excludeRequestPrefixes.isEmpty()) {
                continue;
            }
            String route = trimToNull(String.valueOf(ruleMap.get("rule_id")));
            if (route == null) {
                route = trimToNull(String.valueOf(ruleMap.get("name")));
            }
            PoolAssignStrategy poolStrategy = parsePoolAssignStrategy(ruleMap.get("assign_to_pool_strategy"));
            AutoAssignRule rule = new AutoAssignRule(channels, businesses, locations, clientStatuses, categories,
                    excludedCategories, categoryMatchMode, matchHasCategories, unreadMin, unreadMax, ratingMin,
                    ratingMax, minutesLeftLte, minutesLeftGte, slaStates, requestPrefixes, excludeRequestPrefixes,
                    priority, assignee, assigneePool, route, poolStrategy);
            String ruleId = route != null ? route : "rule_" + ruleIndex;
            String layer = normalizeRuleLayer(ruleMap.get("layer"));
            String owner = trimToNull(String.valueOf(ruleMap.get("owner")));
            String reviewedAtRaw = trimToNull(String.valueOf(ruleMap.get("reviewed_at")));
            Instant reviewedAt = parseUtcInstant(reviewedAtRaw);
            boolean reviewedAtInvalid = reviewedAtRaw != null && reviewedAt == null;
            rules.add(new AutoAssignRuleDefinition(rule, ruleId, layer, owner, reviewedAt, reviewedAtInvalid));
        }
        return rules;
    }

    private boolean matchesDefinition(AutoAssignRuleDefinition definition, Map<String, Object> candidate) {
        return definition != null
                && definition.rule() != null
                && definition.rule().matches(
                normalizeMatchValue(candidate == null ? null : candidate.get("channel")),
                normalizeMatchValue(candidate == null ? null : candidate.get("business")),
                normalizeMatchValue(candidate == null ? null : candidate.get("location")),
                parseCandidateCategories(candidate == null ? null : candidate.get("categories")),
                normalizeMatchValue(candidate == null ? null : candidate.get("client_status")),
                parseOptionalNonNegativeInt(candidate == null ? null : candidate.get("unread_count")),
                parseOptionalNonNegativeInt(candidate == null ? null : candidate.get("rating")),
                parseOptionalLong(candidate == null ? null : candidate.get("minutes_left")),
                normalizeSlaState(candidate == null ? null : candidate.get("sla_state")),
                trimToNull(String.valueOf(candidate == null ? null : candidate.get("request_number")))
        );
    }

    private String formatRuleAssigneeTarget(AutoAssignRule rule) {
        if (rule == null) {
            return "";
        }
        if (rule.assignee() != null) {
            return rule.assignee();
        }
        if (rule.assigneePool() != null && !rule.assigneePool().isEmpty()) {
            return String.join(", ", rule.assigneePool());
        }
        return "";
    }

    private String ticketId(Map<String, Object> candidate) {
        String ticketId = trimToNull(String.valueOf(candidate == null ? null : candidate.get("ticket_id")));
        return ticketId != null ? ticketId : "unknown";
    }

    private Map<String, Object> buildGovernanceIssue(String classification,
                                                     String status,
                                                     String type,
                                                     String ruleId,
                                                     String summary,
                                                     String detail,
                                                     List<String> ticketIds,
                                                     List<String> relatedRulesOrMeta) {
        return Map.of(
                "classification", classification,
                "status", status,
                "type", type,
                "rule_id", ruleId,
                "summary", summary,
                "detail", detail == null ? "" : detail,
                "tickets", ticketIds == null ? List.of() : ticketIds,
                "related", relatedRulesOrMeta == null ? List.of() : relatedRulesOrMeta
        );
    }

    private String normalizeRuleLayer(Object rawValue) {
        String normalized = trimToNull(String.valueOf(rawValue));
        if (normalized == null) {
            return "legacy";
        }
        return switch (normalized.toLowerCase(Locale.ROOT)) {
            case "global", "base" -> "global";
            case "domain", "team", "queue" -> "domain";
            case "emergency", "emergency_override", "override" -> "emergency_override";
            default -> "legacy";
        };
    }

    private Instant parseUtcInstant(String rawValue) {
        String normalized = trimToNull(rawValue);
        if (normalized == null) {
            return null;
        }
        try {
            return Instant.parse(normalized);
        } catch (DateTimeParseException ignored) {
            // continue
        }
        try {
            return OffsetDateTime.parse(normalized).withOffsetSameInstant(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private Boolean parseOptionalBoolean(Object rawValue) {
        if (rawValue instanceof Boolean bool) {
            return bool;
        }
        if (rawValue instanceof Number number) {
            return number.intValue() != 0;
        }
        String raw = trimToNull(String.valueOf(rawValue));
        if (raw == null) {
            return null;
        }
        String normalized = raw.toLowerCase(Locale.ROOT);
        if ("true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized) || "0".equals(normalized) || "no".equals(normalized)) {
            return false;
        }
        return null;
    }

    private Set<String> parseRuleRequestPrefixes(Object rawSingle, Object rawMultiple) {
        Set<String> values = new LinkedHashSet<>();
        addRequestPrefix(values, rawSingle);
        if (rawMultiple instanceof List<?> list) {
            for (Object value : list) {
                addRequestPrefix(values, value);
            }
        } else if (rawMultiple instanceof String text) {
            for (String chunk : text.split("[,;\\n]")) {
                addRequestPrefix(values, chunk);
            }
        }
        return values;
    }

    private void addRequestPrefix(Set<String> values, Object rawValue) {
        String normalized = trimToNull(String.valueOf(rawValue));
        if (normalized != null) {
            values.add(normalized.toLowerCase(Locale.ROOT));
        }
    }

    private Set<String> parseRuleMatchValues(Object rawSingle, Object rawMultiple) {
        Set<String> values = new LinkedHashSet<>();
        addMatchValue(values, rawSingle);
        if (rawMultiple instanceof List<?> list) {
            for (Object value : list) {
                addMatchValue(values, value);
            }
        } else if (rawMultiple instanceof String text) {
            for (String chunk : text.split("[,\n]")) {
                addMatchValue(values, chunk);
            }
        }
        return values;
    }

    private void addMatchValue(Set<String> values, Object rawValue) {
        String normalized = normalizeMatchValue(rawValue);
        if (normalized != null) {
            values.add(normalized);
        }
    }

    private CategoryMatchMode parseCategoryMatchMode(Object rawMode) {
        String mode = rawMode == null ? null : trimToNull(String.valueOf(rawMode));
        if (mode == null) {
            return CategoryMatchMode.ANY;
        }
        return switch (mode.trim().toLowerCase(Locale.ROOT)) {
            case "all", "every", "all_of" -> CategoryMatchMode.ALL;
            default -> CategoryMatchMode.ANY;
        };
    }

    private Set<String> parseRuleSlaStates(Object rawState, Object rawStates) {
        Set<String> values = new LinkedHashSet<>();
        addSlaState(values, normalizeSlaState(rawState));
        if (rawStates instanceof List<?> list) {
            for (Object value : list) {
                addSlaState(values, normalizeSlaState(value));
            }
        } else if (rawStates instanceof String text) {
            for (String chunk : text.split("[,\n]")) {
                addSlaState(values, normalizeSlaState(chunk));
            }
        }
        return values;
    }

    private void addSlaState(Set<String> values, String state) {
        if (state != null) {
            values.add(state);
        }
    }

    private String normalizeSlaState(Object value) {
        String normalized = trimToNull(String.valueOf(value));
        if (normalized == null) {
            return null;
        }
        String lowered = normalized.toLowerCase(Locale.ROOT);
        return switch (lowered) {
            case "breached", "overdue", "expired" -> "breached";
            case "at_risk", "risk", "warning" -> "at_risk";
            case "normal", "ok" -> "normal";
            case "closed" -> "closed";
            default -> null;
        };
    }

    private PoolAssignStrategy parsePoolAssignStrategy(Object rawValue) {
        String raw = trimToNull(String.valueOf(rawValue));
        if (raw == null) {
            return PoolAssignStrategy.HASH_BY_TICKET;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "round_robin", "rr" -> PoolAssignStrategy.ROUND_ROBIN;
            case "least_loaded", "least_load", "load" -> PoolAssignStrategy.LEAST_LOADED;
            default -> PoolAssignStrategy.HASH_BY_TICKET;
        };
    }

    private Integer parseOptionalNonNegativeInt(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof Number number) {
            return number.intValue() < 0 ? null : number.intValue();
        }
        try {
            int parsed = Integer.parseInt(String.valueOf(rawValue).trim());
            return parsed < 0 ? null : parsed;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Long parseOptionalLong(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(rawValue).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private int parsePriority(Object rawValue) {
        if (rawValue == null) {
            return 0;
        }
        if (rawValue instanceof Number number) {
            return Math.max(Math.min(number.intValue(), 100), -100);
        }
        try {
            int parsed = Integer.parseInt(String.valueOf(rawValue).trim());
            return Math.max(Math.min(parsed, 100), -100);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private Set<String> parseRuleCategories(Object singleCategory, Object rawCategories) {
        Set<String> categories = new HashSet<>();
        String single = normalizeMatchValue(singleCategory);
        if (single != null) {
            categories.add(single);
        }
        categories.addAll(parseCandidateCategories(rawCategories));
        return categories;
    }

    private Set<String> parseCandidateCategories(Object rawCategories) {
        Set<String> categories = new HashSet<>();
        if (rawCategories == null) {
            return categories;
        }
        if (rawCategories instanceof List<?> list) {
            for (Object item : list) {
                String normalized = normalizeMatchValue(item);
                if (normalized != null) {
                    categories.add(normalized);
                }
            }
            return categories;
        }
        String raw = trimToNull(String.valueOf(rawCategories));
        if (raw == null) {
            return categories;
        }
        for (String chunk : raw.split("[,\n]")) {
            String normalized = normalizeMatchValue(chunk);
            if (normalized != null) {
                categories.add(normalized);
            }
        }
        return categories;
    }

    private List<String> parseAssigneePool(Object rawPool) {
        List<String> assigneePool = new ArrayList<>();
        if (rawPool instanceof List<?> list) {
            for (Object item : list) {
                String normalized = trimToNull(String.valueOf(item));
                if (normalized != null && !assigneePool.contains(normalized)) {
                    assigneePool.add(normalized);
                }
            }
        } else if (rawPool instanceof String text) {
            for (String chunk : text.split("[,\n]")) {
                String normalized = trimToNull(chunk);
                if (normalized != null && !assigneePool.contains(normalized)) {
                    assigneePool.add(normalized);
                }
            }
        }
        return assigneePool;
    }

    private String normalizeMatchValue(Object rawValue) {
        String normalized = trimToNull(String.valueOf(rawValue));
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed) ? null : trimmed;
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        String raw = trimToNull(String.valueOf(value));
        if (raw == null) {
            return 0L;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private record AutoAssignRuleDefinition(AutoAssignRule rule,
                                            String ruleId,
                                            String layer,
                                            String owner,
                                            Instant reviewedAtUtc,
                                            boolean reviewedAtInvalid) {
    }

    private record RuleUsageStats(Set<String> matchedTickets, Set<String> selectedTickets) {
        private RuleUsageStats() {
            this(new LinkedHashSet<>(), new LinkedHashSet<>());
        }
    }

    private enum PoolAssignStrategy {
        HASH_BY_TICKET,
        ROUND_ROBIN,
        LEAST_LOADED
    }

    private enum CategoryMatchMode {
        ANY,
        ALL
    }

    private record AutoAssignRule(Set<String> channels,
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
        boolean matches(String candidateChannel,
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
            if (excludedCategories != null && !excludedCategories.isEmpty() && candidateCategories != null && !candidateCategories.isEmpty()
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
            if (slaStates != null && !slaStates.isEmpty()) {
                if (candidateSlaState == null || !slaStates.contains(candidateSlaState)) {
                    return false;
                }
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

        int specificityScore() {
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

        String route() {
            if (routeName != null) {
                return routeName;
            }
            if (assignee != null) {
                return "rule:" + assignee;
            }
            if (assigneePool != null && !assigneePool.isEmpty()) {
                return "rule_pool:" + assigneePool.get(0) + ":" + poolAssignStrategy.name().toLowerCase(Locale.ROOT);
            }
            return "rule:unknown";
        }
    }
}
