package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import com.example.panel.repository.TaskHistoryRepository;
import com.example.panel.repository.TaskRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ManagerReportService {

    private static final Logger log = LoggerFactory.getLogger(ManagerReportService.class);

    private final SharedConfigService sharedConfigService;
    private final TaskRepository taskRepository;
    private final TaskHistoryRepository taskHistoryRepository;
    private final DashboardAnalyticsService dashboardAnalyticsService;

    public ManagerReportService(SharedConfigService sharedConfigService,
                                TaskRepository taskRepository,
                                TaskHistoryRepository taskHistoryRepository,
                                DashboardAnalyticsService dashboardAnalyticsService) {
        this.sharedConfigService = sharedConfigService;
        this.taskRepository = taskRepository;
        this.taskHistoryRepository = taskHistoryRepository;
        this.dashboardAnalyticsService = dashboardAnalyticsService;
    }

    public Map<String, Object> buildManagerReport(List<DialogListItem> dialogs,
                                                  DashboardAnalyticsService.DashboardFilters filters) {
        List<DialogListItem> filtered = dashboardAnalyticsService.filterDialogs(dialogs, filters);
        List<Binding> bindings = loadBindings();

        Map<String, ManagerAgg> managers = new HashMap<>();
        for (DialogListItem dialog : filtered) {
            String location = normalize(dialog.locationName());
            if (!StringUtils.hasText(location)) {
                continue;
            }
            Binding binding = findBinding(bindings, location, dashboardAnalyticsService.dialogDate(dialog));
            String manager = binding != null ? binding.manager : "Не назначен";
            String supervisor = binding != null ? binding.supervisor : "Не назначен";
            ManagerAgg agg = managers.computeIfAbsent(manager, key -> new ManagerAgg(manager, supervisor));
            agg.total += 1;
            agg.locations.merge(location, 1L, Long::sum);
        }

        List<Map<String, Object>> managerRows = managers.values().stream()
            .sorted(Comparator.comparingInt((ManagerAgg a) -> a.total).reversed())
            .map(ManagerAgg::toMap)
            .toList();

        Map<String, Integer> supervisorTotals = new LinkedHashMap<>();
        for (ManagerAgg agg : managers.values()) {
            supervisorTotals.merge(agg.supervisor, agg.total, Integer::sum);
        }

        return Map.of(
            "managers", managerRows,
            "supervisors", supervisorTotals,
            "bindings_count", bindings.size()
        );
    }

    public Map<String, Object> buildOlapPreview(List<DialogListItem> dialogs,
                                                 DashboardAnalyticsService.DashboardFilters filters,
                                                 OlapPreviewRequest request) {
        List<DialogListItem> filtered = dashboardAnalyticsService.filterDialogs(dialogs, filters);
        String dimension = StringUtils.hasText(request.dimension()) ? request.dimension() : "location";

        Map<String, MetricAgg> grouped = new LinkedHashMap<>();
        if (request.includeAppeals()) {
            for (DialogListItem dialog : filtered) {
                String key = resolveDimension(dimension, dialog);
                grouped.computeIfAbsent(key, k -> new MetricAgg()).appeals += 1;
            }
        }

        OffsetDateTime from = filters.startDate() != null ? filters.startDate().atStartOfDay().atOffset(OffsetDateTime.now().getOffset()) : OffsetDateTime.parse("2000-01-01T00:00:00Z");
        OffsetDateTime to = filters.endDate() != null
            ? filters.endDate().plusDays(1).atStartOfDay().minusSeconds(1).atOffset(OffsetDateTime.now().getOffset())
            : OffsetDateTime.now().plusYears(20);

        long tasks = request.includeTasks() ? taskRepository.countByCreatedAtBetween(from, to) : 0;
        long systemChanges = request.includeSystemChanges() ? taskHistoryRepository.countByAtBetween(from, to) : 0;

        List<Map<String, Object>> rows = new ArrayList<>();
        grouped.forEach((key, value) -> rows.add(Map.of(
            "dimension", key,
            "appeals", value.appeals,
            "tasks", tasks,
            "system_changes", systemChanges
        )));

        if (rows.isEmpty()) {
            rows.add(Map.of("dimension", "Итого", "appeals", 0, "tasks", tasks, "system_changes", systemChanges));
        }

        return Map.of("rows", rows, "totals", Map.of(
            "appeals", filtered.size(),
            "tasks", tasks,
            "system_changes", systemChanges
        ));
    }

    public record OlapPreviewRequest(String dimension, boolean includeAppeals, boolean includeTasks, boolean includeSystemChanges) {}

    private List<Binding> loadBindings() {
        Map<String, Object> settings = sharedConfigService.loadSettings();
        if (settings == null || settings.isEmpty()) {
            return List.of();
        }

        Object raw = settings.get("manager_location_bindings");
        if (!(raw instanceof List<?> items)) {
            return List.of();
        }
        List<Binding> result = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> map)) continue;
            String location = normalize(readString(map, "location", ""));
            if (!StringUtils.hasText(location)) continue;
            Binding binding = new Binding(
                location,
                normalize(readString(map, "manager", "Не назначен")),
                normalize(readString(map, "supervisor", "Не назначен")),
                parseDate(map.get("start_date")),
                parseDate(map.get("end_date"))
            );

            if (binding.startDate != null && binding.endDate != null && binding.endDate.isBefore(binding.startDate)) {
                log.warn("Skip invalid manager binding for location '{}': end_date {} is before start_date {}",
                    binding.location, binding.endDate, binding.startDate);
                continue;
            }
            result.add(binding);
        }
        return result;
    }

    private Binding findBinding(List<Binding> bindings, String location, LocalDate date) {
        return bindings.stream()
            .filter(b -> b.location.equalsIgnoreCase(location))
            .filter(b -> (date == null || b.startDate == null || !date.isBefore(b.startDate))
                && (date == null || b.endDate == null || !date.isAfter(b.endDate)))
            .max(Comparator.comparing((Binding b) -> b.startDate == null ? LocalDate.MIN : b.startDate))
            .orElse(null);
    }

    private LocalDate parseDate(Object value) {
        if (value == null) return null;
        try {
            String str = String.valueOf(value).trim();
            return str.isEmpty() ? null : LocalDate.parse(str);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String resolveDimension(String dimension, DialogListItem dialog) {
        return switch (dimension.toLowerCase(Locale.ROOT)) {
            case "city" -> normalize(dialog.city());
            case "category" -> normalize(dialog.categories());
            case "responsible" -> normalize(dialog.responsible());
            default -> normalize(dialog.locationName());
        };
    }

    private String readString(Map<?, ?> source, String key, String defaultValue) {
        Object value = source.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "Без данных";
        }
        return value.trim();
    }

    private record Binding(String location, String manager, String supervisor, LocalDate startDate, LocalDate endDate) {}

    private static class ManagerAgg {
        private final String manager;
        private final String supervisor;
        private int total;
        private final Map<String, Long> locations = new LinkedHashMap<>();

        private ManagerAgg(String manager, String supervisor) {
            this.manager = manager;
            this.supervisor = supervisor;
        }

        private Map<String, Object> toMap() {
            List<Map<String, Object>> locationRows = locations.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("location", entry.getKey());
                    row.put("count", entry.getValue());
                    return row;
                })
                .toList();
            return Map.of(
                "manager", manager,
                "supervisor", supervisor,
                "total", total,
                "locations", locationRows
            );
        }
    }

    private static class MetricAgg {
        private int appeals;
    }
}
