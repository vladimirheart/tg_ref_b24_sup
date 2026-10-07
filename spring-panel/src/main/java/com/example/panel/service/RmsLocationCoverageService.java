package com.example.panel.service;

import com.example.panel.entity.RmsLicenseMonitor;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RmsLocationCoverageService {

    private static final String SETTINGS_KEY = "rms_location_coverage";
    private static final List<String> DEFAULT_EXCLUDED_LOCATION_NAMES = List.of(
            "производство",
            "центральный склад",
            "центр. склад"
    );
    private static final int MAX_EXCLUDED_LOCATION_NAMES = 50;
    private static final int MAX_EXCLUDED_LOCATION_NAME_LENGTH = 120;

    private static final String STATUS_CLOSED = "\u0417\u0430\u043a\u0440\u044b\u0442";
    private static final String TYPE_FRANCHISE = "\u041f\u0430\u0440\u0442\u043d\u0451\u0440\u044b-\u0444\u0440\u0430\u043d\u0447\u0430\u0439\u0437\u0438";
    private static final Map<String, List<String>> BUSINESS_ALIASES = Map.of(
            "\u0411\u043b\u0438\u043d\u0411\u0435\u0440\u0438", List.of("\u0431\u0431", "\u0431\u043b\u0438\u043d\u0431\u0435\u0440\u0438", "\u0431\u043b\u0438\u043d\u0431\u0435\u0440\u0440\u0438"),
            "\u0421\u0443\u0448\u0438\u0412\u0451\u0441\u043b\u0430", List.of("\u0441\u0432", "\u0441\u0443\u0448\u0438\u0432\u0435\u0441\u043b\u0430")
    );

    private final SharedConfigService sharedConfigService;

    public RmsLocationCoverageService(SharedConfigService sharedConfigService) {
        this.sharedConfigService = sharedConfigService;
    }

    public CoverageSnapshot buildCoverage(List<RmsLicenseMonitor> monitors) {
        CoveragePolicy policy = loadPolicy();
        List<LocationRef> activeLocations = loadActiveLocations();
        Set<String> excludedNames = normalizedExcludedNames(policy.excludedLocationNames());
        List<LocationRef> locations = new ArrayList<>();
        int policyExcluded = 0;
        for (LocationRef location : activeLocations) {
            if (excludedNames.contains(normalize(location.location()))) {
                policyExcluded++;
            } else {
                locations.add(location);
            }
        }
        List<MonitorRef> candidates = new ArrayList<>();
        int chainExcluded = 0;
        for (RmsLicenseMonitor monitor : monitors == null ? List.<RmsLicenseMonitor>of() : monitors) {
            if (monitor == null || Boolean.TRUE.equals(monitor.getDeleted())) {
                continue;
            }
            if (isChainMonitor(monitor)) {
                chainExcluded++;
                continue;
            }
            String normalizedName = normalize(monitor.getServerName());
            if (!StringUtils.hasText(normalizedName)) {
                continue;
            }
            candidates.add(new MonitorRef(monitor.getId(), monitor.getServerName(), monitor.getRmsAddress(), normalizedName));
        }

        Map<String, List<MonitorRef>> monitorsByName = new LinkedHashMap<>();
        for (MonitorRef candidate : candidates) {
            monitorsByName.computeIfAbsent(candidate.normalizedName(), ignored -> new ArrayList<>()).add(candidate);
        }

        List<Resolution> resolutions = new ArrayList<>();
        for (LocationRef location : locations) {
            LinkedHashMap<String, MonitorRef> uniqueMatches = new LinkedHashMap<>();
            for (String expected : expectedMonitorNames(location)) {
                for (MonitorRef candidate : monitorsByName.getOrDefault(expected, List.of())) {
                    uniqueMatches.putIfAbsent(candidate.identity(), candidate);
                }
            }
            resolutions.add(new Resolution(location, new ArrayList<>(uniqueMatches.values())));
        }

        Map<String, Integer> provisionalUsage = new LinkedHashMap<>();
        for (Resolution resolution : resolutions) {
            if (resolution.matches().size() == 1) {
                provisionalUsage.merge(resolution.matches().get(0).identity(), 1, Integer::sum);
            }
        }

        int matched = 0;
        int missing = 0;
        int ambiguous = 0;
        List<CoverageIssue> issues = new ArrayList<>();
        for (Resolution resolution : resolutions) {
            if (resolution.matches().isEmpty()) {
                missing++;
                issues.add(CoverageIssue.missing(resolution.location()));
                continue;
            }
            boolean sharedMonitor = resolution.matches().size() == 1
                    && provisionalUsage.getOrDefault(resolution.matches().get(0).identity(), 0) > 1;
            if (resolution.matches().size() > 1 || sharedMonitor) {
                ambiguous++;
                issues.add(CoverageIssue.ambiguous(resolution.location(), resolution.matches()));
                continue;
            }
            matched++;
        }

        issues.sort(Comparator.comparing(CoverageIssue::sortKey, String.CASE_INSENSITIVE_ORDER));
        double coveragePercent = locations.isEmpty() ? 100.0 : Math.round((matched * 1000.0) / locations.size()) / 10.0;
        return new CoverageSnapshot(
                activeLocations.size(),
                locations.size(),
                policyExcluded,
                matched,
                missing,
                ambiguous,
                candidates.size(),
                chainExcluded,
                coveragePercent,
                List.copyOf(issues)
        );
    }

    public CoveragePolicy loadPolicy() {
        Map<String, Object> settings = sharedConfigService.loadSettings();
        Object rawPolicy = settings.get(SETTINGS_KEY);
        if (!(rawPolicy instanceof Map<?, ?> policyMap) || !policyMap.containsKey("excluded_location_names")) {
            return new CoveragePolicy(DEFAULT_EXCLUDED_LOCATION_NAMES);
        }
        return new CoveragePolicy(parseExcludedLocationNames(policyMap.get("excluded_location_names"), false));
    }

    public CoveragePolicy savePolicy(Map<String, Object> payload) {
        Map<String, Object> source = payload == null ? Map.of() : payload;
        List<String> excludedNames = parseExcludedLocationNames(source.get("excluded_location_names"), true);
        CoveragePolicy policy = new CoveragePolicy(excludedNames);
        Map<String, Object> root = new LinkedHashMap<>(sharedConfigService.loadSettings());
        root.put(SETTINGS_KEY, policy.toMap());
        sharedConfigService.saveSettings(root);
        return policy;
    }

    private List<String> parseExcludedLocationNames(Object raw, boolean strict) {
        if (!(raw instanceof Collection<?> values)) {
            if (strict) {
                throw new IllegalArgumentException("excluded_location_names должен быть массивом строк.");
            }
            return DEFAULT_EXCLUDED_LOCATION_NAMES;
        }
        LinkedHashMap<String, String> unique = new LinkedHashMap<>();
        for (Object item : values) {
            if (!(item instanceof String nameValue)) {
                throw new IllegalArgumentException("Каждое исключение покрытия должно быть строкой.");
            }
            String name = nameValue.trim();
            if (name.isEmpty()) continue;
            if (name.length() > MAX_EXCLUDED_LOCATION_NAME_LENGTH) {
                throw new IllegalArgumentException("Имя исключения покрытия слишком длинное.");
            }
            String normalized = normalize(name);
            if (StringUtils.hasText(normalized)) {
                unique.putIfAbsent(normalized, name);
            }
        }
        if (unique.size() > MAX_EXCLUDED_LOCATION_NAMES) {
            throw new IllegalArgumentException("Слишком много исключений покрытия: максимум " + MAX_EXCLUDED_LOCATION_NAMES + ".");
        }
        return List.copyOf(unique.values());
    }

    private Set<String> normalizedExcludedNames(Collection<String> names) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String name : names == null ? List.<String>of() : names) {
            String value = normalize(name);
            if (StringUtils.hasText(value)) normalized.add(value);
        }
        return normalized;
    }

    private List<LocationRef> loadActiveLocations() {
        JsonNode root = sharedConfigService.loadLocations();
        if (root == null || !root.isObject()) {
            return List.of();
        }
        JsonNode tree = root.path("tree");
        JsonNode statuses = root.path("statuses");
        if (!tree.isObject()) {
            return List.of();
        }
        List<LocationRef> result = new ArrayList<>();
        tree.fields().forEachRemaining(businessEntry -> {
            String business = text(businessEntry.getKey());
            JsonNode types = businessEntry.getValue();
            if (!types.isObject()) return;
            types.fields().forEachRemaining(typeEntry -> {
                String locationType = text(typeEntry.getKey());
                JsonNode cities = typeEntry.getValue();
                if (!cities.isObject()) return;
                cities.fields().forEachRemaining(cityEntry -> {
                    String city = text(cityEntry.getKey());
                    JsonNode locationNames = cityEntry.getValue();
                    if (!locationNames.isArray()) return;
                    for (JsonNode locationNode : locationNames) {
                        String location = text(locationNode.asText(""));
                        if (!StringUtils.hasText(location)) continue;
                        String statusKey = String.join("::", "location", business, locationType, city, location);
                        String status = statuses.path(statusKey).asText("");
                        if (!STATUS_CLOSED.equalsIgnoreCase(status.trim())) {
                            result.add(new LocationRef(business, locationType, city, location));
                        }
                    }
                });
            });
        });
        result.sort(Comparator.comparing(LocationRef::sortKey, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private Set<String> expectedMonitorNames(LocationRef location) {
        LinkedHashSet<String> expected = new LinkedHashSet<>();
        List<String> aliases = BUSINESS_ALIASES.getOrDefault(location.business(), List.of(normalize(location.business())));
        boolean franchise = TYPE_FRANCHISE.equalsIgnoreCase(location.locationType());
        for (String alias : aliases) {
            String core = alias + " " + location.city() + " " + location.location();
            if (franchise) {
                expected.add(normalize("\u0444\u0440 " + core));
                expected.add(normalize(alias + " \u0444\u0440 " + location.city() + " " + location.location()));
            } else {
                expected.add(normalize(core));
            }
        }
        String rawLocation = normalize(location.location());
        if (looksLikeSelfDescribingLocation(rawLocation, aliases)) {
            expected.add(rawLocation);
        }
        expected.removeIf(value -> !StringUtils.hasText(value));
        return expected;
    }

    private boolean looksLikeSelfDescribingLocation(String normalizedLocation, Collection<String> aliases) {
        if (!StringUtils.hasText(normalizedLocation)) {
            return false;
        }
        for (String alias : aliases) {
            String normalizedAlias = normalize(alias);
            if (normalizedLocation.equals(normalizedAlias)
                    || normalizedLocation.startsWith(normalizedAlias + " ")
                    || normalizedLocation.startsWith("\u0444\u0440 " + normalizedAlias + " ")
                    || normalizedLocation.startsWith(normalizedAlias + " \u0444\u0440 ")) {
                return true;
            }
        }
        return false;
    }

    private boolean isChainMonitor(RmsLicenseMonitor monitor) {
        String normalizedType = normalize(monitor.getServerType()).replace(' ', '_');
        return normalizedType.contains("chain");
    }

    private String normalize(String value) {
        String normalized = text(value).toLowerCase(Locale.ROOT).replace('\u0451', '\u0435');
        normalized = normalized.replaceAll("[^\\p{L}\\p{Nd}]+", " ");
        return normalized.replaceAll("\\s+", " ").trim();
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private record LocationRef(String business, String locationType, String city, String location) {
        private String sortKey() {
            return String.join(" / ", business, locationType, city, location);
        }
    }

    private record MonitorRef(Long id, String name, String rmsAddress, String normalizedName) {
        private String identity() {
            return id != null ? "id:" + id : "address:" + String.valueOf(rmsAddress);
        }

        private Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", id);
            map.put("name", name);
            map.put("rms_address", rmsAddress);
            return map;
        }
    }

    private record Resolution(LocationRef location, List<MonitorRef> matches) {
    }

    public record CoverageIssue(String kind,
                                String business,
                                String locationType,
                                String city,
                                String location,
                                List<Map<String, Object>> candidates) {
        private static CoverageIssue missing(LocationRef location) {
            return new CoverageIssue("missing", location.business(), location.locationType(), location.city(), location.location(), List.of());
        }

        private static CoverageIssue ambiguous(LocationRef location, List<MonitorRef> matches) {
            return new CoverageIssue(
                    "ambiguous",
                    location.business(),
                    location.locationType(),
                    location.city(),
                    location.location(),
                    matches.stream().map(MonitorRef::toMap).toList()
            );
        }

        private String sortKey() {
            return String.join(" / ", business, locationType, city, location);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("kind", kind);
            map.put("business", business);
            map.put("location_type", locationType);
            map.put("city", city);
            map.put("location", location);
            map.put("candidates", candidates);
            return map;
        }
    }

    public record CoveragePolicy(List<String> excludedLocationNames) {
        public CoveragePolicy {
            excludedLocationNames = List.copyOf(excludedLocationNames == null ? List.of() : excludedLocationNames);
        }

        public Map<String, Object> toMap() {
            return Map.of("excluded_location_names", excludedLocationNames);
        }
    }

    public record CoverageSnapshot(int activeLocationCount,
                                   int coverageLocationCount,
                                   int excludedLocationCount,
                                   int matchedLocationCount,
                                   int missingLocationCount,
                                   int ambiguousLocationCount,
                                   int candidateMonitorCount,
                                   int chainMonitorExcludedCount,
                                   double coveragePercent,
                                   List<CoverageIssue> issues) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("active_location_count", activeLocationCount);
            map.put("coverage_location_count", coverageLocationCount);
            map.put("excluded_location_count", excludedLocationCount);
            map.put("matched_location_count", matchedLocationCount);
            map.put("missing_location_count", missingLocationCount);
            map.put("ambiguous_location_count", ambiguousLocationCount);
            map.put("candidate_monitor_count", candidateMonitorCount);
            map.put("chain_monitor_excluded_count", chainMonitorExcludedCount);
            map.put("coverage_percent", coveragePercent);
            map.put("issues", issues.stream().map(CoverageIssue::toMap).toList());
            return map;
        }
    }
}
