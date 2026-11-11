package com.example.panel.model.dialog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Category names remain the ticket contract; priorities are template metadata. */
public final class DialogCategoryPriorities {
    public static final List<String> LEVELS = List.of("P1", "P2", "P3", "P4", "P5");
    private final Map<String, String> priorities = new LinkedHashMap<>();

    private DialogCategoryPriorities(Object templates) {
        if (!(templates instanceof List<?> list)) return;
        for (Object raw : list) {
            if (!(raw instanceof Map<?, ?> template)
                    || !(template.get("categories") instanceof List<?> categories)
                    || !(template.get("category_priorities") instanceof Map<?, ?> values)) continue;
            for (Object category : categories) {
                if (!(category instanceof String name)) continue;
                String priority = normalize(values.get(name));
                if (!priority.isEmpty()) {
                    // Old imported configurations can contain conflicting names. Use the highest level.
                    priorities.merge(key(name), priority, (left, right) -> left.compareTo(right) <= 0 ? left : right);
                }
            }
        }
    }

    public static DialogCategoryPriorities fromSettings(Map<String, Object> settings) {
        Object config = settings == null ? null : settings.get("dialog_config");
        return new DialogCategoryPriorities(config instanceof Map<?, ?> map ? map.get("category_templates") : null);
    }

    public String priorityFor(String category) {
        return priorities.getOrDefault(key(category), "");
    }

    public String labelFor(String category) {
        String priority = priorityFor(category);
        return priority.isEmpty() ? category : category + " · " + priority;
    }

    public String highestPriority(List<String> categories) {
        return categories.stream().map(this::priorityFor).filter(value -> !value.isEmpty())
                .min(String::compareTo).orElse("");
    }

    public static Object normalizeTemplates(Object raw) {
        if (!(raw instanceof List<?> templates)) return raw;
        List<Object> result = new ArrayList<>();
        Map<String, String> assigned = new LinkedHashMap<>();
        for (Object item : templates) {
            if (!(item instanceof Map<?, ?> template)) {
                result.add(item);
                continue;
            }
            Map<String, Object> copy = new LinkedHashMap<>();
            template.forEach((name, value) -> copy.put(String.valueOf(name), value));
            Object rawPriorities = template.get("category_priorities");
            if (rawPriorities == null) {
                result.add(copy);
                continue;
            }
            if (!(rawPriorities instanceof Map<?, ?> values)) {
                throw new IllegalArgumentException("Критичность категорий должна содержать значения от P1 до P5.");
            }
            Map<String, String> normalized = new LinkedHashMap<>();
            if (template.get("categories") instanceof List<?> categories) {
                for (Object category : categories) {
                    if (!(category instanceof String name)) continue;
                    Object value = values.get(name);
                    String priority = normalize(value);
                    if (value != null && !value.toString().isBlank() && priority.isEmpty()) {
                        throw new IllegalArgumentException("Для категории «" + name + "» выберите критичность от P1 до P5.");
                    }
                    if (priority.isEmpty()) continue; // Existing categories may still be unassigned.
                    String previous = assigned.putIfAbsent(key(name), priority);
                    if (previous != null && !previous.equals(priority)) {
                        throw new IllegalArgumentException("У категории «" + name + "» должна быть одинаковая критичность во всех шаблонах.");
                    }
                    normalized.put(name, priority);
                }
            }
            copy.put("category_priorities", normalized);
            result.add(copy);
        }
        return result;
    }

    private static String normalize(Object value) {
        String priority = value instanceof String text ? text.trim().toUpperCase(Locale.ROOT) : "";
        return LEVELS.contains(priority) ? priority : "";
    }

    private static String key(String category) {
        return category == null ? "" : category.trim().toLowerCase(Locale.ROOT);
    }
}
