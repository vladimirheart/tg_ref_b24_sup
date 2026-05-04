package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class SlaRoutingRuleMatchNormalizerService {

    private final SlaRoutingRuleScalarParserService scalarParserService;

    public SlaRoutingRuleMatchNormalizerService(SlaRoutingRuleScalarParserService scalarParserService) {
        this.scalarParserService = scalarParserService;
    }

    public SlaRoutingRuleMatchNormalizerService() {
        this(new SlaRoutingRuleScalarParserService());
    }

    public Set<String> parseRuleMatchValues(Object rawSingle, Object rawMultiple) {
        Set<String> values = new LinkedHashSet<>();
        addMatchValue(values, rawSingle);
        if (rawMultiple instanceof List<?> list) {
            for (Object value : list) addMatchValue(values, value);
        } else if (rawMultiple instanceof String text) {
            for (String chunk : text.split("[,\n]")) addMatchValue(values, chunk);
        }
        return values;
    }

    public Set<String> parseRuleCategories(Object singleCategory, Object rawCategories) {
        Set<String> categories = new HashSet<>();
        String single = normalizeMatchValue(singleCategory);
        if (single != null) categories.add(single);
        categories.addAll(parseCandidateCategories(rawCategories));
        return categories;
    }

    public Set<String> parseCandidateCategories(Object rawCategories) {
        Set<String> categories = new HashSet<>();
        if (rawCategories == null) return categories;
        if (rawCategories instanceof List<?> list) {
            for (Object item : list) {
                String normalized = normalizeMatchValue(item);
                if (normalized != null) categories.add(normalized);
            }
            return categories;
        }
        String raw = scalarParserService.trimToNull(String.valueOf(rawCategories));
        if (raw == null) return categories;
        for (String chunk : raw.split("[,\n]")) {
            String normalized = normalizeMatchValue(chunk);
            if (normalized != null) categories.add(normalized);
        }
        return categories;
    }

    public Set<String> parseRuleSlaStates(Object rawState, Object rawStates) {
        Set<String> values = new LinkedHashSet<>();
        addSlaState(values, normalizeSlaState(rawState));
        if (rawStates instanceof List<?> list) {
            for (Object value : list) addSlaState(values, normalizeSlaState(value));
        } else if (rawStates instanceof String text) {
            for (String chunk : text.split("[,\n]")) addSlaState(values, normalizeSlaState(chunk));
        }
        return values;
    }

    public Set<String> parseRuleRequestPrefixes(Object rawSingle, Object rawMultiple) {
        Set<String> values = new LinkedHashSet<>();
        addRequestPrefix(values, rawSingle);
        if (rawMultiple instanceof List<?> list) {
            for (Object value : list) addRequestPrefix(values, value);
        } else if (rawMultiple instanceof String text) {
            for (String chunk : text.split("[,;\\n]")) addRequestPrefix(values, chunk);
        }
        return values;
    }

    public List<String> parseAssigneePool(Object rawPool) {
        List<String> assigneePool = new ArrayList<>();
        if (rawPool instanceof List<?> list) {
            for (Object item : list) {
                String normalized = scalarParserService.trimToNull(String.valueOf(item));
                if (normalized != null && !assigneePool.contains(normalized)) assigneePool.add(normalized);
            }
        } else if (rawPool instanceof String text) {
            for (String chunk : text.split("[,\n]")) {
                String normalized = scalarParserService.trimToNull(chunk);
                if (normalized != null && !assigneePool.contains(normalized)) assigneePool.add(normalized);
            }
        }
        return assigneePool;
    }

    public SlaRoutingRuleTypes.CategoryMatchMode parseCategoryMatchMode(Object rawMode) {
        String mode = rawMode == null ? null : scalarParserService.trimToNull(String.valueOf(rawMode));
        if (mode == null) return SlaRoutingRuleTypes.CategoryMatchMode.ANY;
        return switch (mode.trim().toLowerCase(Locale.ROOT)) {
            case "all", "every", "all_of" -> SlaRoutingRuleTypes.CategoryMatchMode.ALL;
            default -> SlaRoutingRuleTypes.CategoryMatchMode.ANY;
        };
    }

    public SlaRoutingRuleTypes.PoolAssignStrategy parsePoolAssignStrategy(Object rawValue) {
        String raw = scalarParserService.trimToNull(String.valueOf(rawValue));
        if (raw == null) return SlaRoutingRuleTypes.PoolAssignStrategy.HASH_BY_TICKET;
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "round_robin", "rr" -> SlaRoutingRuleTypes.PoolAssignStrategy.ROUND_ROBIN;
            case "least_loaded", "least_load", "load" -> SlaRoutingRuleTypes.PoolAssignStrategy.LEAST_LOADED;
            default -> SlaRoutingRuleTypes.PoolAssignStrategy.HASH_BY_TICKET;
        };
    }

    public String normalizeRuleLayer(Object rawValue) {
        String normalized = scalarParserService.trimToNull(String.valueOf(rawValue));
        if (normalized == null) return "legacy";
        return switch (normalized.toLowerCase(Locale.ROOT)) {
            case "global", "base" -> "global";
            case "domain", "team", "queue" -> "domain";
            case "emergency", "emergency_override", "override" -> "emergency_override";
            default -> "legacy";
        };
    }

    public String normalizeMatchValue(Object rawValue) {
        String normalized = scalarParserService.trimToNull(String.valueOf(rawValue));
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    public String normalizeSlaState(Object value) {
        String normalized = scalarParserService.trimToNull(String.valueOf(value));
        if (normalized == null) return null;
        String lowered = normalized.toLowerCase(Locale.ROOT);
        return switch (lowered) {
            case "breached", "overdue", "expired" -> "breached";
            case "at_risk", "risk", "warning" -> "at_risk";
            case "normal", "ok" -> "normal";
            case "closed" -> "closed";
            default -> null;
        };
    }

    private void addRequestPrefix(Set<String> values, Object rawValue) {
        String normalized = scalarParserService.trimToNull(String.valueOf(rawValue));
        if (normalized != null) values.add(normalized.toLowerCase(Locale.ROOT));
    }

    private void addMatchValue(Set<String> values, Object rawValue) {
        String normalized = normalizeMatchValue(rawValue);
        if (normalized != null) values.add(normalized);
    }

    private void addSlaState(Set<String> values, String state) {
        if (state != null) values.add(state);
    }
}
