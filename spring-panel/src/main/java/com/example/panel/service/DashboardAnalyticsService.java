package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DashboardAnalyticsService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    private static final DateTimeFormatter DATE_TIME_FORMATTER_SHORT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId DEFAULT_ZONE = ZoneId.systemDefault();

    public record DashboardFilters(LocalDate startDate, LocalDate endDate, List<String> restaurants) {}

    public Map<String, Object> buildDashboardPayload(List<DialogListItem> dialogs, DashboardFilters filters) {
        List<DialogListItem> filtered = applyFilters(dialogs, filters);
        StatsBlock stats = buildStats(filtered, filters);
        TimeStats timeStats = buildTimeStats(filtered);
        List<StaffTimeStats> staffTimeStats = buildStaffTimeStats(filtered);
        ChartsBlock charts = buildCharts(filtered);

        Map<String, Object> payload = new HashMap<>();
        payload.put("stats", stats.toMap());
        payload.put("time_stats", timeStats.toMap());
        payload.put("staff_time_stats", staffTimeStats.stream().map(StaffTimeStats::toMap).toList());
        payload.put("charts", charts.toMap());
        return payload;
    }

    private List<DialogListItem> applyFilters(List<DialogListItem> dialogs, DashboardFilters filters) {
        if (dialogs == null || dialogs.isEmpty()) {
            return List.of();
        }
        return dialogs.stream()
                .filter(dialog -> matchesDate(dialog, filters))
                .filter(dialog -> matchesRestaurant(dialog, filters))
                .toList();
    }

    private boolean matchesDate(DialogListItem dialog, DashboardFilters filters) {
        if (filters == null || (filters.startDate == null && filters.endDate == null)) {
            return true;
        }
        LocalDate date = extractDate(dialog);
        if (date == null) {
            return false;
        }
        if (filters.startDate != null && date.isBefore(filters.startDate)) {
            return false;
        }
        return filters.endDate == null || !date.isAfter(filters.endDate);
    }

    private boolean matchesRestaurant(DialogListItem dialog, DashboardFilters filters) {
        if (filters == null || filters.restaurants == null || filters.restaurants.isEmpty()) {
            return true;
        }
        String locationName = safeLower(dialog.locationName());
        if (!StringUtils.hasText(locationName)) {
            return false;
        }
        return filters.restaurants.stream()
                .filter(StringUtils::hasText)
                .map(this::safeLower)
                .anyMatch(locationName::equalsIgnoreCase);
    }

    private StatsBlock buildStats(List<DialogListItem> dialogs, DashboardFilters filters) {
        int total = dialogs.size();
        StatsBlock.PeriodStats current = new StatsBlock.PeriodStats(total);
        StatsBlock.PeriodStats previous = buildPreviousStats(dialogs, filters);
        return new StatsBlock(current, previous);
    }

    private StatsBlock.PeriodStats buildPreviousStats(List<DialogListItem> dialogs, DashboardFilters filters) {
        if (filters == null || filters.startDate == null || filters.endDate == null) {
            return new StatsBlock.PeriodStats(0);
        }
        LocalDate start = filters.startDate;
        LocalDate end = filters.endDate;
        long days = Math.max(1, end.toEpochDay() - start.toEpochDay() + 1);
        LocalDate prevEnd = start.minusDays(1);
        LocalDate prevStart = prevEnd.minusDays(days - 1);
        int previousTotal = (int) dialogs.stream()
                .filter(dialog -> matchesRestaurant(dialog, filters))
                .filter(dialog -> {
                    LocalDate date = extractDate(dialog);
                    return date != null && !date.isBefore(prevStart) && !date.isAfter(prevEnd);
                })
                .count();
        return new StatsBlock.PeriodStats(previousTotal);
    }

    private TimeStats buildTimeStats(List<DialogListItem> dialogs) {
        List<Integer> durations = extractDurations(dialogs);
        int resolvedCount = durations.size();
        int totalMinutes = durations.stream().mapToInt(Integer::intValue).sum();
        int avgMinutes = resolvedCount > 0 ? Math.round((float) totalMinutes / resolvedCount) : 0;
        return new TimeStats(totalMinutes, avgMinutes, resolvedCount);
    }

    private List<StaffTimeStats> buildStaffTimeStats(List<DialogListItem> dialogs) {
        Map<String, List<Integer>> durationsByStaff = new HashMap<>();
        for (DialogListItem dialog : dialogs) {
            String staff = StringUtils.hasText(dialog.responsible()) ? dialog.responsible() : "Не назначен";
            Integer minutes = extractDurationMinutes(dialog);
            if (minutes == null) {
                continue;
            }
            durationsByStaff.computeIfAbsent(staff, key -> new ArrayList<>()).add(minutes);
        }
        return durationsByStaff.entrySet().stream()
                .map(entry -> StaffTimeStats.from(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(StaffTimeStats::totalMinutes).reversed())
                .toList();
    }

    private ChartsBlock buildCharts(List<DialogListItem> dialogs) {
        Map<String, Long> byBusiness = aggregate(dialogs, DialogListItem::businessLabel);
        Map<String, Long> byNetwork = aggregate(dialogs, dialog -> {
            String channel = dialog.channelLabel();
            return StringUtils.hasText(channel) ? channel : "Без канала";
        });
        Map<String, Long> byCategory = aggregate(dialogs, dialog -> {
            String categories = dialog.categoriesSafe();
            if (!StringUtils.hasText(categories) || "—".equals(categories)) {
                return "Без категории";
            }
            if (categories.contains(",")) {
                return categories.split(",")[0].trim();
            }
            return categories;
        });
        Map<String, Long> byCity = aggregate(dialogs, dialog -> {
            String city = dialog.city();
            return StringUtils.hasText(city) ? city : "Без города";
        });
        Map<String, Long> byRestaurant = aggregate(dialogs, dialog -> {
            String location = dialog.locationName();
            return StringUtils.hasText(location) ? location : "Без ресторана";
        });

        return new ChartsBlock(byBusiness, byNetwork, byCategory, byCity, topTen(byRestaurant));
    }

    private Map<String, Long> aggregate(List<DialogListItem> dialogs, java.util.function.Function<DialogListItem, String> extractor) {
        Map<String, Long> counts = new HashMap<>();
        for (DialogListItem dialog : dialogs) {
            String key = normalizeLabel(extractor.apply(dialog));
            counts.merge(key, 1L, Long::sum);
        }
        return sortByValueDesc(counts);
    }

    private Map<String, Long> sortByValueDesc(Map<String, Long> source) {
        return source.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    private Map<String, Long> topTen(Map<String, Long> source) {
        return source.entrySet().stream()
                .limit(10)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    private List<Integer> extractDurations(List<DialogListItem> dialogs) {
        List<Integer> durations = new ArrayList<>();
        for (DialogListItem dialog : dialogs) {
            Integer minutes = extractDurationMinutes(dialog);
            if (minutes != null) {
                durations.add(minutes);
            }
        }
        return durations;
    }

    private Integer extractDurationMinutes(DialogListItem dialog) {
        LocalDateTime created = parseDateTime(dialog.createdAt(), dialog.createdDate(), dialog.createdTime());
        LocalDateTime resolved = parseDateTime(dialog.resolvedAt(), null, null);
        if (created == null || resolved == null) {
            return null;
        }
        long minutes = java.time.Duration.between(created, resolved).toMinutes();
        if (minutes < 0) {
            return null;
        }
        return (int) minutes;
    }

    private LocalDate extractDate(DialogListItem dialog) {
        LocalDateTime dateTime = parseDateTime(dialog.createdAt(), dialog.createdDate(), null);
        return dateTime != null ? dateTime.toLocalDate() : null;
    }

    private LocalDateTime parseDateTime(String createdAt, String createdDate, String createdTime) {
        if (StringUtils.hasText(createdAt)) {
            LocalDateTime parsed = tryParseDateTime(createdAt);
            if (parsed != null) {
                return parsed;
            }
        }
        if (StringUtils.hasText(createdDate)) {
            LocalDate parsedDate = tryParseDate(createdDate);
            if (parsedDate != null) {
                if (StringUtils.hasText(createdTime)) {
                    LocalDateTime parsed = tryParseDateTime(createdDate + " " + createdTime);
                    if (parsed != null) {
                        return parsed;
                    }
                }
                return parsedDate.atStartOfDay();
            }
        }
        return null;
    }

    private LocalDateTime tryParseDateTime(String value) {
        String normalized = value.trim();
        for (DateTimeFormatter formatter : List.of(DATE_TIME_FORMATTER, DATE_TIME_FORMATTER_SHORT)) {
            try {
                return LocalDateTime.parse(normalized, formatter);
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        try {
            return java.time.OffsetDateTime.parse(normalized).atZoneSameInstant(DEFAULT_ZONE).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private LocalDate tryParseDate(String value) {
        String normalized = value.trim();
        try {
            return LocalDate.parse(normalized, DATE_FORMATTER);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDate.parse(normalized);
            } catch (DateTimeParseException second) {
                return null;
            }
        }
    }

    private String normalizeLabel(String value) {
        if (!StringUtils.hasText(value)) {
            return "Без данных";
        }
        return value.trim();
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private record StatsBlock(PeriodStats current, PeriodStats previous) {
        Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("current", current.toMap());
            map.put("previous", previous.toMap());
            return map;
        }

        private record PeriodStats(int total) {
            Map<String, Object> toMap() {
                return Map.of("total", total);
            }
        }
    }

    private record TimeStats(int totalMinutes, int avgMinutes, int resolvedCount) {
        Map<String, Object> toMap() {
            return Map.of(
                    "total", totalMinutes,
                    "formatted_total", formatDuration(totalMinutes),
                    "avg", avgMinutes,
                    "formatted_avg", formatMinutes(avgMinutes),
                    "resolved_count", resolvedCount
            );
        }
    }

    private record StaffTimeStats(String name, int totalMinutes, int avgMinutes, int resolvedCount) {
        Map<String, Object> toMap() {
            return Map.of(
                    "name", name,
                    "total_minutes", totalMinutes,
                    "formatted_total", formatDuration(totalMinutes),
                    "formatted_avg", formatMinutes(avgMinutes),
                    "formatted_avg_response", formatMinutes(avgMinutes),
                    "resolved_count", resolvedCount
            );
        }

        static StaffTimeStats from(String name, List<Integer> durations) {
            int total = durations.stream().filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
            int count = (int) durations.stream().filter(Objects::nonNull).count();
            int avg = count > 0 ? Math.round((float) total / count) : 0;
            return new StaffTimeStats(name, total, avg, count);
        }
    }

    private record ChartsBlock(Map<String, Long> business,
                               Map<String, Long> network,
                               Map<String, Long> category,
                               Map<String, Long> city,
                               Map<String, Long> restaurant) {
        Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("business", business);
            map.put("network", network);
            map.put("category", category);
            map.put("city", city);
            map.put("restaurant", restaurant);
            return map;
        }
    }

    private static String formatDuration(int minutes) {
        if (minutes <= 0) {
            return "0 ч";
        }
        int hours = minutes / 60;
        int remaining = minutes % 60;
        if (hours == 0) {
            return remaining + " мин";
        }
        if (remaining == 0) {
            return hours + " ч";
        }
        return hours + " ч " + remaining + " мин";
    }

    private static String formatMinutes(int minutes) {
        if (minutes <= 0) {
            return "0 мин";
        }
        if (minutes >= 60) {
            return formatDuration(minutes);
        }
        return minutes + " мин";
    }
}
