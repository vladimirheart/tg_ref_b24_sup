package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.model.dialog.DialogListItem;
import com.example.panel.repository.ChannelRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DashboardAnalyticsService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    private static final DateTimeFormatter DATE_TIME_FORMATTER_SHORT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId DEFAULT_ZONE = ZoneId.systemDefault();
    private static final int DEFAULT_BUSINESS_HOURS_START = 9;
    private static final int DEFAULT_BUSINESS_HOURS_END = 18;
    private static final String UNKNOWN_NETWORK = "Без типа сети";

    private final SharedConfigService sharedConfigService;
    private final ChannelRepository channelRepository;
    private final ObjectMapper objectMapper;

    public DashboardAnalyticsService(SharedConfigService sharedConfigService,
                                     ChannelRepository channelRepository,
                                     ObjectMapper objectMapper) {
        this.sharedConfigService = sharedConfigService;
        this.channelRepository = channelRepository;
        this.objectMapper = objectMapper;
    }

    public record DashboardFilters(LocalDate startDate, LocalDate endDate, List<String> restaurants) {}

    public Map<String, Object> buildDashboardPayload(List<DialogListItem> dialogs, DashboardFilters filters) {
        List<DialogListItem> filtered = filterDialogs(dialogs, filters);
        StatsBlock stats = buildStats(filtered, filters);
        TimeStats timeStats = buildTimeStats(filtered);
        ActivityStats activityStats = buildActivityStats(filtered);
        List<StaffTimeStats> staffTimeStats = buildStaffTimeStats(filtered);
        ChartsBlock charts = buildCharts(filtered);

        Map<String, Object> payload = new HashMap<>();
        payload.put("stats", stats.toMap());
        payload.put("time_stats", timeStats.toMap());
        payload.put("activity_stats", activityStats.toMap());
        payload.put("staff_time_stats", staffTimeStats.stream().map(StaffTimeStats::toMap).toList());
        payload.put("charts", charts.toMap());
        return payload;
    }

    public List<DialogListItem> filterDialogs(List<DialogListItem> dialogs, DashboardFilters filters) {
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
        LocalDate date = dialogDate(dialog);
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
                    LocalDate date = dialogDate(dialog);
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

    private ActivityStats buildActivityStats(List<DialogListItem> dialogs) {
        WorkingHoursSelection workingHours = resolveWorkingHoursSelection(dialogs);
        int[][] matrix = new int[7][24];
        Map<LocalDateTime, Integer> byHour = new HashMap<>();
        int peakCount = 0;
        int peakDayIndex = 0;
        int peakHour = 0;
        int total = 0;

        for (DialogListItem dialog : dialogs) {
            LocalDateTime created = parseDateTime(dialog.createdAt(), dialog.createdDate(), dialog.createdTime());
            if (created == null) {
                continue;
            }
            int dayIndex = created.getDayOfWeek().getValue() - 1;
            int hour = created.getHour();
            matrix[dayIndex][hour] += 1;
            total += 1;
            LocalDateTime hourSlot = created.withMinute(0).withSecond(0).withNano(0);
            byHour.merge(hourSlot, 1, Integer::sum);

            int cellCount = matrix[dayIndex][hour];
            if (cellCount > peakCount) {
                peakCount = cellCount;
                peakDayIndex = dayIndex;
                peakHour = hour;
            }
        }

        int activeHourCount = byHour.size();
        double avgPerActiveHour = activeHourCount > 0 ? (double) total / activeHourCount : 0d;
        int maxCount = 0;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int dayIndex = 0; dayIndex < 7; dayIndex++) {
            List<Integer> values = new ArrayList<>(24);
            for (int hour = 0; hour < 24; hour++) {
                int count = matrix[dayIndex][hour];
                values.add(count);
                if (count > maxCount) {
                    maxCount = count;
                }
            }
            rows.add(Map.of(
                    "day_index", dayIndex,
                    "day_label", dayLabel(dayIndex),
                    "values", values
            ));
        }

        return new ActivityStats(
                avgPerActiveHour,
                formatHourlyLoad(avgPerActiveHour),
                activeHourCount,
                peakCount,
                peakCount > 0 ? buildPeakLabel(peakDayIndex, peakHour) : "Нет данных",
                rows,
                hourLabels(),
                maxCount,
                workingHours.startHour(),
                workingHours.endHour(),
                workingHours.label(),
                workingHours.sourceLabel()
        );
    }

    private WorkingHoursSelection resolveWorkingHoursSelection(List<DialogListItem> dialogs) {
        if (dialogs == null || dialogs.isEmpty()) {
            return WorkingHoursSelection.defaultSelection();
        }
        Map<Long, Long> countsByChannel = dialogs.stream()
                .map(DialogListItem::channelId)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(channelId -> channelId, Collectors.counting()));
        if (countsByChannel.isEmpty()) {
            return WorkingHoursSelection.defaultSelection();
        }

        Map<Long, Channel> channelsById = channelRepository.findAllById(countsByChannel.keySet()).stream()
                .filter(channel -> channel.getId() != null)
                .collect(Collectors.toMap(Channel::getId, channel -> channel));
        if (channelsById.isEmpty()) {
            return WorkingHoursSelection.defaultSelection();
        }

        Map<String, Long> hoursWeights = new HashMap<>();
        WorkingHoursSelection dominantSelection = null;
        long dominantCount = -1L;
        long dominantChannelId = Long.MAX_VALUE;
        for (Map.Entry<Long, Long> entry : countsByChannel.entrySet()) {
            Channel channel = channelsById.get(entry.getKey());
            WorkingHours hours = extractWorkingHours(channel);
            String key = hours.startHour() + ":" + hours.endHour();
            hoursWeights.merge(key, entry.getValue(), Long::sum);
            long channelId = entry.getKey() == null ? Long.MAX_VALUE : entry.getKey();
            if (entry.getValue() > dominantCount || (entry.getValue() == dominantCount && channelId < dominantChannelId)) {
                dominantCount = entry.getValue();
                dominantChannelId = channelId;
                String sourceLabel = channel != null && StringUtils.hasText(channel.getChannelName())
                        ? "Основной канал: " + channel.getChannelName().trim()
                        : "";
                dominantSelection = new WorkingHoursSelection(
                        hours.startHour(),
                        hours.endHour(),
                        formatWorkingHoursLabel(hours.startHour(), hours.endHour()),
                        sourceLabel
                );
            }
        }

        if (dominantSelection == null) {
            return WorkingHoursSelection.defaultSelection();
        }
        if (hoursWeights.size() <= 1) {
            return new WorkingHoursSelection(
                    dominantSelection.startHour(),
                    dominantSelection.endHour(),
                    dominantSelection.label(),
                    ""
            );
        }
        return dominantSelection;
    }

    private WorkingHours extractWorkingHours(Channel channel) {
        if (channel == null || !StringUtils.hasText(channel.getDeliverySettings())) {
            return WorkingHours.defaultHours();
        }
        try {
            JsonNode deliverySettings = objectMapper.readTree(channel.getDeliverySettings());
            JsonNode workingHoursNode = deliverySettings.path("working_hours");
            int startHour = readHourValue(workingHoursNode, DEFAULT_BUSINESS_HOURS_START, 0, 23,
                    "start_hour", "startHour", "start");
            int endCandidate = readHourValue(workingHoursNode, DEFAULT_BUSINESS_HOURS_END, 1, 24,
                    "end_hour", "endHour", "end");
            int endHour = endCandidate <= startHour ? Math.min(24, startHour + 1) : endCandidate;
            return new WorkingHours(startHour, endHour);
        } catch (Exception ignored) {
            return WorkingHours.defaultHours();
        }
    }

    private int readHourValue(JsonNode node, int defaultValue, int min, int max, String... fieldNames) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return defaultValue;
        }
        for (String fieldName : fieldNames) {
            JsonNode valueNode = node.path(fieldName);
            if (valueNode.isMissingNode() || valueNode.isNull()) {
                continue;
            }
            Integer parsed = parseInteger(valueNode.asText(null));
            if (parsed == null && valueNode.isNumber()) {
                parsed = valueNode.asInt();
            }
            if (parsed != null) {
                return Math.max(min, Math.min(max, parsed));
            }
        }
        return defaultValue;
    }

    private Integer parseInteger(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String formatWorkingHoursLabel(int startHour, int endHour) {
        return String.format(Locale.ROOT, "%02d:00 - %02d:00", startHour, endHour);
    }

    private ChartsBlock buildCharts(List<DialogListItem> dialogs) {
        LocationCatalog locationCatalog = loadLocationCatalog();
        Map<String, Long> byChannel = aggregate(dialogs, DialogListItem::channelLabel);
        Map<String, Long> byBusiness = aggregate(dialogs, dialog -> resolveBusinessLabel(dialog, locationCatalog));
        Map<String, Long> byNetwork = aggregate(dialogs, dialog -> resolvePartnerType(dialog, locationCatalog));
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

        return new ChartsBlock(byChannel, byBusiness, byNetwork, byCategory, byCity, topTen(byRestaurant));
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

    public LocalDate dialogDate(DialogListItem dialog) {
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
            return LocalDateTime.parse(normalized);
        } catch (DateTimeParseException ignored) {
            // try offset datetime format
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

    private LocationCatalog loadLocationCatalog() {
        JsonNode root = sharedConfigService.loadLocations();
        if (root == null || root.isMissingNode()) {
            return LocationCatalog.empty();
        }
        JsonNode tree = root.path("tree");
        if (!tree.isObject()) {
            return LocationCatalog.empty();
        }

        Map<String, LocationProfile> exactProfiles = new HashMap<>();
        Map<String, LocationProfile> businessLocationProfiles = new HashMap<>();
        Map<String, LocationProfile> businessCityProfiles = new HashMap<>();
        Map<String, LocationProfile> locationProfiles = new HashMap<>();
        Map<String, String> businessAliases = new HashMap<>();
        Set<String> ambiguousBusinessLocations = new java.util.HashSet<>();
        Set<String> ambiguousLocations = new java.util.HashSet<>();

        tree.fields().forEachRemaining(businessEntry -> {
            String business = businessEntry.getKey();
            businessAliases.put(normalizeLookupToken(business), business);
            JsonNode typeNode = businessEntry.getValue();
            if (!typeNode.isObject()) {
                return;
            }
            typeNode.fields().forEachRemaining(typeEntry -> {
                String partnerType = typeEntry.getKey();
                JsonNode cityNode = typeEntry.getValue();
                if (!cityNode.isObject()) {
                    return;
                }
                cityNode.fields().forEachRemaining(cityEntry -> {
                    String city = cityEntry.getKey();
                    LocationProfile cityProfile = new LocationProfile(business, partnerType);
                    businessCityProfiles.putIfAbsent(buildLookupKey(business, city), cityProfile);
                    JsonNode locationsNode = cityEntry.getValue();
                    if (!locationsNode.isArray()) {
                        return;
                    }
                    locationsNode.forEach(locationNode -> {
                        String location = locationNode.asText("");
                        if (!StringUtils.hasText(location)) {
                            return;
                        }
                        LocationProfile profile = new LocationProfile(business, partnerType);
                        exactProfiles.put(buildLookupKey(business, city, location), profile);
                        registerUniqueProfile(
                                businessLocationProfiles,
                                ambiguousBusinessLocations,
                                buildLookupKey(business, location),
                                profile);
                        registerUniqueProfile(
                                locationProfiles,
                                ambiguousLocations,
                                buildLookupKey(location),
                                profile);
                    });
                });
            });
        });

        ambiguousBusinessLocations.forEach(businessLocationProfiles::remove);
        ambiguousLocations.forEach(locationProfiles::remove);

        return new LocationCatalog(exactProfiles, businessLocationProfiles, businessCityProfiles, locationProfiles, businessAliases);
    }

    private void registerUniqueProfile(Map<String, LocationProfile> target,
                                       Set<String> ambiguousKeys,
                                       String key,
                                       LocationProfile profile) {
        if (!StringUtils.hasText(key) || ambiguousKeys.contains(key)) {
            return;
        }
        LocationProfile current = target.get(key);
        if (current == null) {
            target.put(key, profile);
            return;
        }
        if (!current.equals(profile)) {
            ambiguousKeys.add(key);
        }
    }

    private String resolveBusinessLabel(DialogListItem dialog, LocationCatalog locationCatalog) {
        LocationProfile profile = resolveLocationProfile(dialog, locationCatalog);
        if (profile != null) {
            return profile.business();
        }
        return canonicalBusinessLabel(dialog.businessLabel(), locationCatalog);
    }

    private String resolvePartnerType(DialogListItem dialog, LocationCatalog locationCatalog) {
        LocationProfile profile = resolveLocationProfile(dialog, locationCatalog);
        if (profile != null && StringUtils.hasText(profile.partnerType())) {
            return profile.partnerType();
        }
        return UNKNOWN_NETWORK;
    }

    private LocationProfile resolveLocationProfile(DialogListItem dialog, LocationCatalog locationCatalog) {
        if (dialog == null || locationCatalog == null) {
            return null;
        }
        String business = canonicalBusinessLabel(dialog.businessLabel(), locationCatalog);
        String city = normalizeLookupToken(dialog.city());
        String location = normalizeLookupToken(dialog.locationName());
        if (!StringUtils.hasText(location)) {
            return null;
        }

        if (StringUtils.hasText(business) && StringUtils.hasText(city)) {
            LocationProfile exact = locationCatalog.exactProfiles().get(buildLookupKey(business, city, location));
            if (exact != null) {
                return exact;
            }
        }
        if (StringUtils.hasText(business)) {
            LocationProfile businessLocation = locationCatalog.businessLocationProfiles().get(buildLookupKey(business, location));
            if (businessLocation != null) {
                return businessLocation;
            }
        }
        if (StringUtils.hasText(business) && StringUtils.hasText(city)) {
            LocationProfile businessCity = locationCatalog.businessCityProfiles().get(buildLookupKey(business, city));
            if (businessCity != null) {
                return businessCity;
            }
        }
        return locationCatalog.locationProfiles().get(buildLookupKey(location));
    }

    private String canonicalBusinessLabel(String rawBusiness, LocationCatalog locationCatalog) {
        String normalized = normalizeLookupToken(rawBusiness);
        if (!StringUtils.hasText(normalized)) {
            return "Без бизнеса";
        }
        return locationCatalog.businessAliases().getOrDefault(normalized, rawBusiness.trim());
    }

    private String buildLookupKey(String... parts) {
        return java.util.Arrays.stream(parts)
                .map(this::normalizeLookupToken)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("::"));
    }

    private String normalizeLookupToken(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim()
                .replace('ё', 'е')
                .replace('Ё', 'Е')
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private List<String> hourLabels() {
        List<String> labels = new ArrayList<>(24);
        for (int hour = 0; hour < 24; hour++) {
            labels.add(String.format(Locale.ROOT, "%02d:00", hour));
        }
        return labels;
    }

    private String buildPeakLabel(int dayIndex, int hour) {
        return dayLabel(dayIndex) + ", " + hourRangeLabel(hour);
    }

    private String dayLabel(int dayIndex) {
        return switch (dayIndex) {
            case 0 -> "Пн";
            case 1 -> "Вт";
            case 2 -> "Ср";
            case 3 -> "Чт";
            case 4 -> "Пт";
            case 5 -> "Сб";
            case 6 -> "Вс";
            default -> "—";
        };
    }

    private String hourRangeLabel(int hour) {
        return String.format(Locale.ROOT, "%02d:00 - %02d:00", hour, (hour + 1) % 24);
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

    private record ActivityStats(double avgPerActiveHour,
                                 String formattedAvgPerActiveHour,
                                 int activeHours,
                                 int peakCount,
                                 String peakLabel,
                                 List<Map<String, Object>> matrix,
                                 List<String> hourLabels,
                                 int maxCount,
                                 int businessHoursStart,
                                 int businessHoursEnd,
                                 String businessHoursLabel,
                                 String businessHoursSourceLabel) {
        Map<String, Object> toMap() {
            return Map.of(
                    "avg_per_active_hour", avgPerActiveHour,
                    "formatted_avg_per_active_hour", formattedAvgPerActiveHour,
                    "active_hours", activeHours,
                    "peak_count", peakCount,
                    "peak_label", peakLabel,
                    "matrix", matrix,
                    "hour_labels", hourLabels,
                    "max_count", maxCount,
                    "business_hours_start", businessHoursStart,
                    "business_hours_end", businessHoursEnd,
                    "business_hours_label", businessHoursLabel,
                    "business_hours_source_label", businessHoursSourceLabel
            );
        }
    }

    private record WorkingHours(int startHour, int endHour) {
        private static WorkingHours defaultHours() {
            return new WorkingHours(DEFAULT_BUSINESS_HOURS_START, DEFAULT_BUSINESS_HOURS_END);
        }
    }

    private record WorkingHoursSelection(int startHour,
                                         int endHour,
                                         String label,
                                         String sourceLabel) {
        private static WorkingHoursSelection defaultSelection() {
            return new WorkingHoursSelection(
                    DEFAULT_BUSINESS_HOURS_START,
                    DEFAULT_BUSINESS_HOURS_END,
                    String.format(Locale.ROOT, "%02d:00 - %02d:00", DEFAULT_BUSINESS_HOURS_START, DEFAULT_BUSINESS_HOURS_END),
                    ""
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

    private record ChartsBlock(Map<String, Long> channel,
                               Map<String, Long> business,
                               Map<String, Long> network,
                               Map<String, Long> category,
                               Map<String, Long> city,
                               Map<String, Long> restaurant) {
        Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("channel", channel);
            map.put("business", business);
            map.put("network", network);
            map.put("category", category);
            map.put("city", city);
            map.put("restaurant", restaurant);
            return map;
        }
    }

    private record LocationProfile(String business, String partnerType) {}

    private record LocationCatalog(Map<String, LocationProfile> exactProfiles,
                                   Map<String, LocationProfile> businessLocationProfiles,
                                   Map<String, LocationProfile> businessCityProfiles,
                                   Map<String, LocationProfile> locationProfiles,
                                   Map<String, String> businessAliases) {
        private static LocationCatalog empty() {
            return new LocationCatalog(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
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

    private static String formatHourlyLoad(double value) {
        if (value <= 0d) {
            return "0";
        }
        if (Math.abs(value - Math.rint(value)) < 0.05d) {
            return String.valueOf((int) Math.rint(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
