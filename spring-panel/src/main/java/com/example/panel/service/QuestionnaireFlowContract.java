package com.example.panel.service;

import java.util.*;

/** The v3 flow is interpreted without legacy normalization or synthetic questions. */
public final class QuestionnaireFlowContract {
    private QuestionnaireFlowContract() {}

    public static boolean supports(Map<String, Object> template) {
        try { questions(template); return true; }
        catch (IllegalArgumentException ex) { return false; }
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> questions(Map<?, ?> template) {
        if (!(template.get("question_flow") instanceof List<?> raw) || raw.isEmpty() || raw.size() > 200)
            throw invalid();
        List<Map<String, Object>> flow = new ArrayList<>();
        Set<String> ids = new HashSet<>(), keys = new HashSet<>();
        for (Object item : raw) {
            if (!(item instanceof Map<?, ?> q) || !canonical(q.get("id")) || !ids.add((String) q.get("id"))) throw invalid();
            String type = type(q);
            if (!Set.of("custom", "text", "select", "preset").contains(type)
                    || (q.get("binding_key") != null && !canonical(q.get("binding_key")))
                    || !keys.add(key(q)) || ("problem".equals(key(q)) && Set.of("select", "preset").contains(type))) throw invalid();
            if ((q.get("required") != null && !(q.get("required") instanceof Boolean))
                    || (q.get("include_in_dashboard") != null && !(q.get("include_in_dashboard") instanceof Boolean))
                    || (q.get("text") != null && !(q.get("text") instanceof String))) throw invalid();
            Object order = q.getOrDefault("order", null);
            if (order != null && (!(order instanceof Number n) || n.doubleValue() != n.intValue())) throw invalid();
            Set<String> optionIds = new HashSet<>(), labels = new HashSet<>();
            if (q.get("options") != null) {
                if (!(q.get("options") instanceof List<?> options) || options.size() > 1000) throw invalid();
                for (Object option : options) {
                    if (!(option instanceof Map<?, ?> o) || !canonical(o.get("id")) || !canonical(o.get("label"))
                            || !optionIds.add((String) o.get("id")) || !labels.add((String) o.get("label"))) throw invalid();
                }
            }
            if ("select".equals(type) ? optionIds.isEmpty() : !optionIds.isEmpty()) throw invalid();
            if (!"preset".equals(type) && q.get("preset") != null) throw invalid();
            if (q.get("excluded_options") != null && !(q.get("excluded_options") instanceof List<?>)) throw invalid();
            flow.add((Map<String, Object>) q);
        }
        // List.sort is stable, matching all three bot consumers when order is omitted or equal.
        flow.sort(Comparator.comparingInt(q -> q.get("order") instanceof Number n ? n.intValue() : 0));
        Map<String, Integer> indexes = new HashMap<>();
        for (int i = 0; i < flow.size(); i++) indexes.put((String) flow.get(i).get("id"), i);
        for (int i = 0; i < flow.size(); i++) {
            var q = flow.get(i);
            if (q.get("routes") == null) continue;
            if (!(q.get("routes") instanceof List<?> routes) || routes.size() > 1000) throw invalid();
            Set<String> values = new HashSet<>();
            for (Object item : routes) {
                if (!(item instanceof Map<?, ?> route) || !canonical(route.get("value_id"))
                        || !canonical(route.get("next_question_id")) || !values.add((String) route.get("value_id"))
                        || indexes.getOrDefault(route.get("next_question_id"), -1) <= i) throw invalid();
                if ("select".equals(type(q))) {
                    if (((List<?>) q.get("options")).stream().noneMatch(o -> ((Map<?, ?>) o).get("id").equals(route.get("value_id")))) throw invalid();
                } else if (!"preset".equals(type(q))) throw invalid();
            }
        }
        return List.copyOf(flow);
    }

    public static String key(Map<?, ?> q) { return q.get("binding_key") instanceof String b ? b : (String) q.get("id"); }
    public static String type(Map<?, ?> q) { return q.get("type") instanceof String s ? s : "custom"; }
    private static boolean canonical(Object value) { return value instanceof String s && !s.isBlank() && s.equals(s.strip()); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid v3 questionnaire flow, keys, options or forward routes"); }
}
