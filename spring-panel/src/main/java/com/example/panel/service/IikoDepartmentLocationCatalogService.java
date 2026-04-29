package com.example.panel.service;

import com.example.panel.entity.IikoApiMonitor;
import com.example.panel.repository.IikoApiMonitorRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class IikoDepartmentLocationCatalogService {

    private static final Logger log = LoggerFactory.getLogger(IikoDepartmentLocationCatalogService.class);

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(15);
    private static final String DEFAULT_SOURCE = "shared_config";
    private static final String LIVE_SOURCE = "iiko_api";
    private static final String TYPE_CORPORATE = "Корпоративная сеть";
    private static final String TYPE_FRANCHISE = "Партнёры-франчайзи";
    private static final String BUSINESS_BLINBERI = "БлинБери";
    private static final String BUSINESS_SUSHIVESLA = "СушиВёсла";
    private static final Map<String, String> BUSINESS_BY_CODE = Map.of(
            "ББ", BUSINESS_BLINBERI,
            "СВ", BUSINESS_SUSHIVESLA
    );
    private static final List<String> BUSINESS_ORDER = List.of(BUSINESS_BLINBERI, BUSINESS_SUSHIVESLA);
    private static final List<String> LOCATION_TYPE_ORDER = List.of(TYPE_CORPORATE, TYPE_FRANCHISE);

    private final IikoApiMonitorRepository monitorRepository;
    private final SharedConfigService sharedConfigService;
    private final ObjectMapper objectMapper;
    private final IikoDepartmentGateway gateway;

    private volatile CachedCatalog cachedCatalog;

    @Autowired
    public IikoDepartmentLocationCatalogService(IikoApiMonitorRepository monitorRepository,
                                                SharedConfigService sharedConfigService,
                                                ObjectMapper objectMapper) {
        this(monitorRepository, sharedConfigService, objectMapper, new HttpIikoDepartmentGateway(objectMapper));
    }

    IikoDepartmentLocationCatalogService(IikoApiMonitorRepository monitorRepository,
                                         SharedConfigService sharedConfigService,
                                         ObjectMapper objectMapper,
                                         IikoDepartmentGateway gateway) {
        this.monitorRepository = monitorRepository;
        this.sharedConfigService = sharedConfigService;
        this.objectMapper = objectMapper;
        this.gateway = gateway;
    }

    public LocationCatalogSnapshot loadCatalog() {
        LocationCatalogSnapshot fallback = loadFallbackCatalog();
        CachedCatalog cached = cachedCatalog;
        if (cached != null && cached.isFresh()) {
            return cached.snapshot();
        }

        LocationCatalogSnapshot resolved = loadLiveCatalog(fallback);
        cachedCatalog = new CachedCatalog(resolved, Instant.now());
        return resolved;
    }

    LocationCatalogSnapshot buildCatalogFromDepartmentNames(Collection<String> departmentNames,
                                                            Map<String, Object> fallbackTree) {
        List<String> knownCities = extractKnownCities(fallbackTree);
        Map<String, Object> tree = buildTree(departmentNames, knownCities);
        return new LocationCatalogSnapshot(tree, Map.of(), LIVE_SOURCE, false, List.of());
    }

    private LocationCatalogSnapshot loadLiveCatalog(LocationCatalogSnapshot fallback) {
        List<ApiCredential> credentials = loadCredentials();
        if (credentials.isEmpty()) {
            return fallback;
        }

        Set<String> departmentNames = new LinkedHashSet<>();
        List<String> warnings = new ArrayList<>();
        for (ApiCredential credential : credentials) {
            try {
                String token = gateway.requestAccessToken(credential.baseUrl(), credential.apiLogin());
                List<String> organizationIds = gateway.loadOrganizationIds(credential.baseUrl(), token);
                if (organizationIds.isEmpty()) {
                    warnings.add("Для " + credential.baseUrl() + " не найдено организаций");
                    continue;
                }
                departmentNames.addAll(gateway.loadActiveDepartmentNames(credential.baseUrl(), token, organizationIds));
            } catch (Exception ex) {
                String message = "Не удалось загрузить департаменты из iiko " + credential.baseUrl() + ": " + ex.getMessage();
                warnings.add(message);
                log.warn(message);
            }
        }

        if (departmentNames.isEmpty()) {
            return fallback.withWarnings(warnings);
        }

        Map<String, Object> tree = buildTree(departmentNames, extractKnownCities(fallback.tree()));
        if (tree.isEmpty()) {
            return fallback.withWarnings(warnings);
        }
        return new LocationCatalogSnapshot(tree, Map.of(), LIVE_SOURCE, false, List.copyOf(warnings));
    }

    private List<ApiCredential> loadCredentials() {
        return monitorRepository.findAllByOrderByMonitorNameAscIdAsc().stream()
                .filter(monitor -> Boolean.TRUE.equals(monitor.getEnabled()))
                .map(monitor -> new ApiCredential(
                        normalizeText(monitor.getBaseUrl()),
                        normalizeText(monitor.getApiLogin())))
                .filter(credential -> StringUtils.hasText(credential.baseUrl()) && StringUtils.hasText(credential.apiLogin()))
                .distinct()
                .toList();
    }

    private LocationCatalogSnapshot loadFallbackCatalog() {
        JsonNode payload = sharedConfigService.loadLocations();
        if (payload == null || !payload.isObject()) {
            return new LocationCatalogSnapshot(Map.of(), Map.of(), DEFAULT_SOURCE, true, List.of());
        }
        Map<String, Object> root = objectMapper.convertValue(payload, Map.class);
        Map<String, Object> tree = toStringObjectMap(root.get("tree"));
        Map<String, Object> statuses = toStringObjectMap(root.get("statuses"));
        return new LocationCatalogSnapshot(tree, statuses, DEFAULT_SOURCE, true, List.of());
    }

    private Map<String, Object> buildTree(Collection<String> departmentNames, List<String> knownCities) {
        Map<String, Map<String, Map<String, Set<String>>>> rawTree = new LinkedHashMap<>();
        List<String> sortedCities = knownCities.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed().thenComparing(String.CASE_INSENSITIVE_ORDER))
                .toList();

        if (departmentNames != null) {
            for (String departmentName : departmentNames) {
                ParsedDepartment parsed = parseDepartment(departmentName, sortedCities);
                if (parsed == null) {
                    continue;
                }
                rawTree
                        .computeIfAbsent(parsed.business(), key -> new LinkedHashMap<>())
                        .computeIfAbsent(parsed.locationType(), key -> new LinkedHashMap<>())
                        .computeIfAbsent(parsed.city(), key -> new LinkedHashSet<>())
                        .add(parsed.locationName());
            }
        }

        return sortTree(rawTree);
    }

    private ParsedDepartment parseDepartment(String rawName, List<String> knownCities) {
        String departmentName = normalizeText(rawName);
        if (!StringUtils.hasText(departmentName) || containsClosed(departmentName)) {
            return null;
        }

        boolean franchise = departmentName.regionMatches(true, 0, "ФР_", 0, 3);
        String withoutFranchisePrefix = franchise ? departmentName.substring(3).trim() : departmentName;
        String businessCode = resolveBusinessCode(withoutFranchisePrefix);
        if (!StringUtils.hasText(businessCode)) {
            return null;
        }

        String business = BUSINESS_BY_CODE.get(businessCode);
        if (!StringUtils.hasText(business)) {
            return null;
        }

        String remainder = withoutFranchisePrefix.substring(businessCode.length()).trim();
        if (!StringUtils.hasText(remainder)) {
            return null;
        }

        String city = resolveCity(remainder, knownCities);
        String locationName;
        if (StringUtils.hasText(city)) {
            locationName = remainder.substring(city.length()).trim();
        } else {
            city = firstToken(remainder);
            locationName = city.length() < remainder.length() ? remainder.substring(city.length()).trim() : "";
        }

        if (!StringUtils.hasText(city)) {
            return null;
        }
        if (!StringUtils.hasText(locationName)) {
            locationName = departmentName;
        }

        return new ParsedDepartment(
                business,
                franchise ? TYPE_FRANCHISE : TYPE_CORPORATE,
                city,
                locationName
        );
    }

    private String resolveBusinessCode(String value) {
        for (String code : BUSINESS_BY_CODE.keySet()) {
            if (startsWithCode(value, code)) {
                return code;
            }
        }
        return null;
    }

    private boolean startsWithCode(String value, String code) {
        if (!StringUtils.hasText(value) || !value.startsWith(code)) {
            return false;
        }
        if (value.length() == code.length()) {
            return true;
        }
        char next = value.charAt(code.length());
        return Character.isWhitespace(next) || next == '_' || next == '-';
    }

    private String resolveCity(String remainder, List<String> knownCities) {
        if (!StringUtils.hasText(remainder)) {
            return null;
        }
        for (String knownCity : knownCities) {
            if (!StringUtils.hasText(knownCity)) {
                continue;
            }
            if (matchesCityPrefix(remainder, knownCity)) {
                return knownCity;
            }
        }
        return null;
    }

    private boolean matchesCityPrefix(String remainder, String city) {
        if (remainder.equalsIgnoreCase(city)) {
            return true;
        }
        if (remainder.length() <= city.length()) {
            return false;
        }
        if (!remainder.regionMatches(true, 0, city, 0, city.length())) {
            return false;
        }
        char next = remainder.charAt(city.length());
        return Character.isWhitespace(next) || next == '-' || next == ',' || next == '.';
    }

    private List<String> extractKnownCities(Map<String, Object> tree) {
        Set<String> cities = new LinkedHashSet<>();
        if (tree == null) {
            return List.of();
        }
        for (Object typeValue : tree.values()) {
            Map<String, Object> typeMap = toStringObjectMap(typeValue);
            for (Object cityValue : typeMap.values()) {
                Map<String, Object> cityMap = toStringObjectMap(cityValue);
                for (String city : cityMap.keySet()) {
                    if (StringUtils.hasText(city)) {
                        cities.add(city.trim());
                    }
                }
            }
        }
        return new ArrayList<>(cities);
    }

    private Map<String, Object> sortTree(Map<String, Map<String, Map<String, Set<String>>>> source) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        List<String> businesses = new ArrayList<>(source.keySet());
        businesses.sort(byPreferredOrder(BUSINESS_ORDER));
        for (String business : businesses) {
            Map<String, Map<String, Set<String>>> typeMap = source.getOrDefault(business, Map.of());
            LinkedHashMap<String, Object> sortedTypeMap = new LinkedHashMap<>();
            List<String> locationTypes = new ArrayList<>(typeMap.keySet());
            locationTypes.sort(byPreferredOrder(LOCATION_TYPE_ORDER));
            for (String locationType : locationTypes) {
                Map<String, Set<String>> cityMap = typeMap.getOrDefault(locationType, Map.of());
                LinkedHashMap<String, Object> sortedCityMap = new LinkedHashMap<>();
                List<String> cities = new ArrayList<>(cityMap.keySet());
                cities.sort(String.CASE_INSENSITIVE_ORDER);
                for (String city : cities) {
                    List<String> locations = new ArrayList<>(cityMap.getOrDefault(city, Set.of()));
                    locations.sort(String.CASE_INSENSITIVE_ORDER);
                    sortedCityMap.put(city, locations);
                }
                sortedTypeMap.put(locationType, sortedCityMap);
            }
            result.put(business, sortedTypeMap);
        }
        return result;
    }

    private Comparator<String> byPreferredOrder(List<String> preferredOrder) {
        return Comparator
                .comparingInt((String value) -> {
                    int index = preferredOrder.indexOf(value);
                    return index >= 0 ? index : Integer.MAX_VALUE;
                })
                .thenComparing(String.CASE_INSENSITIVE_ORDER);
    }

    private boolean containsClosed(String value) {
        return StringUtils.hasText(value) && value.toUpperCase(Locale.ROOT).contains("CLOSED");
    }

    private String firstToken(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        int boundary = trimmed.indexOf(' ');
        return boundary >= 0 ? trimmed.substring(0, boundary).trim() : trimmed;
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toStringObjectMap(Object rawValue) {
        if (rawValue instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, value) -> {
                if (key != null) {
                    result.put(String.valueOf(key), value);
                }
            });
            return result;
        }
        return Map.of();
    }

    public record LocationCatalogSnapshot(Map<String, Object> tree,
                                          Map<String, Object> statuses,
                                          String source,
                                          boolean fallback,
                                          List<String> warnings) {

        LocationCatalogSnapshot withWarnings(List<String> warnings) {
            return new LocationCatalogSnapshot(
                    tree == null ? Map.of() : tree,
                    statuses == null ? Map.of() : statuses,
                    StringUtils.hasText(source) ? source : DEFAULT_SOURCE,
                    fallback,
                    warnings == null ? List.of() : List.copyOf(warnings)
            );
        }
    }

    interface IikoDepartmentGateway {
        String requestAccessToken(String baseUrl, String apiLogin) throws Exception;

        List<String> loadOrganizationIds(String baseUrl, String token) throws Exception;

        List<String> loadActiveDepartmentNames(String baseUrl, String token, List<String> organizationIds) throws Exception;
    }

    private record CachedCatalog(LocationCatalogSnapshot snapshot, Instant cachedAt) {
        private boolean isFresh() {
            return snapshot != null && cachedAt != null && cachedAt.plus(CACHE_TTL).isAfter(Instant.now());
        }
    }

    private record ApiCredential(String baseUrl, String apiLogin) {

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ApiCredential credential)) {
                return false;
            }
            return Objects.equals(baseUrl, credential.baseUrl)
                    && Objects.equals(apiLogin, credential.apiLogin);
        }

        @Override
        public int hashCode() {
            return Objects.hash(baseUrl, apiLogin);
        }
    }

    private record ParsedDepartment(String business,
                                    String locationType,
                                    String city,
                                    String locationName) {
    }

    private static final class HttpIikoDepartmentGateway implements IikoDepartmentGateway {

        private final ObjectMapper objectMapper;
        private final HttpClient httpClient;

        private HttpIikoDepartmentGateway(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(HTTP_TIMEOUT)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
        }

        @Override
        public String requestAccessToken(String baseUrl, String apiLogin) throws Exception {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("apiLogin", apiLogin);
            JsonNode response = postJson(baseUrl, "/api/1/access_token", payload, null);
            String token = firstNonBlank(response.path("token").asText(null), response.path("accessToken").asText(null));
            if (!StringUtils.hasText(token)) {
                throw new IllegalStateException("iiko не вернул access token");
            }
            return token.trim();
        }

        @Override
        public List<String> loadOrganizationIds(String baseUrl, String token) throws Exception {
            JsonNode response = postJson(baseUrl, "/api/1/organizations", objectMapper.createObjectNode(), token);
            List<String> organizationIds = new ArrayList<>();
            for (JsonNode organization : response.path("organizations")) {
                String id = firstNonBlank(
                        organization.path("id").asText(null),
                        organization.path("organizationId").asText(null)
                );
                if (StringUtils.hasText(id)) {
                    organizationIds.add(id.trim());
                }
            }
            return organizationIds.stream().distinct().toList();
        }

        @Override
        public List<String> loadActiveDepartmentNames(String baseUrl, String token, List<String> organizationIds) throws Exception {
            ObjectNode payload = objectMapper.createObjectNode();
            ArrayNode organizationIdsNode = payload.putArray("organizationIds");
            for (String organizationId : organizationIds) {
                if (StringUtils.hasText(organizationId)) {
                    organizationIdsNode.add(organizationId.trim());
                }
            }
            JsonNode response = postJson(baseUrl, "/api/1/terminal_groups", payload, token);
            List<String> departmentNames = new ArrayList<>();
            collectTerminalGroupNames(response.path("terminalGroups"), departmentNames);
            return departmentNames.stream().distinct().toList();
        }

        private void collectTerminalGroupNames(JsonNode wrappersNode, List<String> departmentNames) {
            if (wrappersNode == null || !wrappersNode.isArray()) {
                return;
            }
            for (JsonNode wrapper : wrappersNode) {
                for (JsonNode item : wrapper.path("items")) {
                    String name = firstNonBlank(item.path("name").asText(null));
                    if (StringUtils.hasText(name)) {
                        departmentNames.add(name.trim());
                    }
                }
            }
        }

        private JsonNode postJson(String baseUrl, String path, JsonNode body, String token) throws Exception {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl(baseUrl) + path))
                    .timeout(HTTP_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body == null ? objectMapper.createObjectNode() : body)));
            if (StringUtils.hasText(token)) {
                requestBuilder.header("Authorization", "Bearer " + token.trim());
            }
            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("HTTP " + response.statusCode() + " при запросе " + path);
            }
            return objectMapper.readTree(firstNonBlank(response.body(), "{}"));
        }

        private String normalizeBaseUrl(String baseUrl) {
            String value = firstNonBlank(baseUrl);
            if (!StringUtils.hasText(value)) {
                throw new IllegalArgumentException("Пустой baseUrl iiko");
            }
            String normalized = value.trim();
            while (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            return normalized;
        }

        private static String firstNonBlank(String... values) {
            if (values == null) {
                return null;
            }
            for (String value : values) {
                if (StringUtils.hasText(value)) {
                    return value.trim();
                }
            }
            return null;
        }
    }
}
