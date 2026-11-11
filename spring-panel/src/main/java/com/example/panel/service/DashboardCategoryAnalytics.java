package com.example.panel.service;

import com.example.panel.model.dialog.DialogCategoryPriorities;
import com.example.panel.model.dialog.DialogListItem;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class DashboardCategoryAnalytics {
    private DashboardCategoryAnalytics() {}

    static Result build(List<DialogListItem> dialogs, DialogCategoryPriorities priorities) {
        Map<String, Long> categories = new LinkedHashMap<>();
        Map<String, Long> levels = new LinkedHashMap<>();
        if (!dialogs.isEmpty()) DialogCategoryPriorities.LEVELS.forEach(level -> levels.put(level, 0L));
        for (DialogListItem dialog : dialogs) {
            List<String> names = Arrays.stream(dialog.categoriesSafe().split(","))
                    .map(String::trim).filter(name -> !name.isEmpty() && !"—".equals(name))
                    .distinct().toList();
            if (names.isEmpty()) categories.merge("Без категории", 1L, Long::sum);
            for (String name : names) categories.merge(priorities.labelFor(name), 1L, Long::sum);
            String priority = priorities.highestPriority(names);
            levels.merge(priority.isEmpty() ? "Без критичности" : priority, 1L, Long::sum);
        }
        Map<String, Long> sorted = new LinkedHashMap<>();
        categories.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey(Comparator.naturalOrder())))
                .forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        return new Result(sorted, levels);
    }

    record Result(Map<String, Long> categories, Map<String, Long> priorities) {}
}
