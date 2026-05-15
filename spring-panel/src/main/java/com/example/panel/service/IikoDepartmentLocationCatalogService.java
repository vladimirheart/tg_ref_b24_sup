package com.example.panel.service;

import com.example.panel.service.LocationsIikoServerSourceSettingsService.LocationIikoServerSource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

@Service
public class IikoDepartmentLocationCatalogService {

    private static final Logger log = LoggerFactory.getLogger(IikoDepartmentLocationCatalogService.class);

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(15);
    private static final String DEFAULT_SOURCE = "shared_config";
    private static final String LIVE_SOURCE = "iiko_api";
    private static final String DEFAULT_COUNTRY = "Россия";
    private static final String STATUS_ACTIVE = "Активен";
    private static final String STATUS_CLOSED = "Закрыт";
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

    private final LocationsIikoServerSourceSettingsService locationsIikoServerSourceSettingsService;
    private final SharedConfigService sharedConfigService;
    private final ObjectMapper objectMapper;
    private final IikoDepartmentGateway gateway;

    private volatile CachedCatalog cachedCatalog;

    @Autowired
    public IikoDepartmentLocationCatalogService(LocationsIikoServerSourceSettingsService locationsIikoServerSourceSettingsService,
                                                SharedConfigService sharedConfigService,
                                                ObjectMapper objectMapper) {
        this(locationsIikoServerSourceSettingsService, sharedConfigService, objectMapper, new HttpIikoDepartmentGateway(objectMapper));
    }

    IikoDepartmentLocationCatalogService(LocationsIikoServerSourceSettingsService locationsIikoServerSourceSettingsService,
                                         SharedConfigService sharedConfigService,
                                         ObjectMapper objectMapper,
                                         IikoDepartmentGateway gateway) {
        this.locationsIikoServerSourceSettingsService = locationsIikoServerSourceSettingsService;
        this.sharedConfigService = sharedConfigService;
        this.objectMapper = objectMapper;
        this.gateway = gateway;
    }

    public LocationCatalogSnapshot loadCatalog() {
        return loadCatalog(false);
    }

    public LocationCatalogSnapshot loadCatalog(boolean forceRefresh) {
        LocationCatalogSnapshot fallback = loadFallbackCatalog();
        CachedCatalog cached = cachedCatalog;
        if (!forceRefresh && cached != null && cached.isFresh()) {
            return cached.snapshot();
        }

        LocationCatalogSnapshot resolved = loadLiveCatalog(fallback);
        cachedCatalog = new CachedCatalog(resolved, Instant.now());
        return resolved;
    }

    public Map<String, Object> buildEffectiveLocationsPayload(LocationCatalogSnapshot snapshot) {
        Map<String, Object> basePayload = loadFallbackPayload();
        return mergeCatalogIntoPayload(basePayload, snapshot);
    }

    LocationCatalogSnapshot buildCatalogFromDepartmentNames(Collection<String> departmentNames,
                                                            Map<String, Object> fallbackTree) {
        return buildCatalogFromDepartmentNames(departmentNames, fallbackTree, Map.of());
    }

    LocationCatalogSnapshot buildCatalogFromDepartmentNames(Collection<String> departmentNames,
                                                            Map<String, Object> fallbackTree,
                                                            Map<String, Object> fallbackStatuses) {
        List<String> knownCities = extractKnownCities(fallbackTree);
        List<ParsedDepartment> activeDepartments = parseDepartments(departmentNames, knownCities);
        Map<String, Object> tree = buildMergedTree(activeDepartments, fallbackTree);
        Map<String, Object> statuses = buildLocationStatuses(tree, activeDepartments, fallbackStatuses);
        return new LocationCatalogSnapshot(tree, statuses, LIVE_SOURCE, false, List.of());
    }

    private LocationCatalogSnapshot loadLiveCatalog(LocationCatalogSnapshot fallback) {
        List<ApiCredential> credentials = loadCredentials();
        if (credentials.isEmpty()) {
            return fallback;
        }

        Set<String> locationNames = new LinkedHashSet<>();
        List<String> warnings = new ArrayList<>();
        for (ApiCredential credential : credentials) {
            try {
                String token = gateway.requestAccessToken(
                        credential.baseUrl(),
                        credential.apiLogin(),
                        credential.apiSecret()
                );
                List<String> departments = gateway.loadActiveDepartmentNames(credential.baseUrl(), token);
                if (departments.isEmpty()) {
                    warnings.add("Для " + credential.baseUrl() + " не найдено активных департаментов");
                    continue;
                }
                locationNames.addAll(departments);
            } catch (Exception ex) {
                String message = "Не удалось загрузить департаменты из iiko " + credential.baseUrl() + ": " + ex.getMessage();
                warnings.add(message);
                log.warn(message);
            }
        }

        if (locationNames.isEmpty()) {
            return fallback.withWarnings(warnings);
        }

        LocationCatalogSnapshot snapshot = buildCatalogFromDepartmentNames(
                locationNames,
                fallback.tree(),
                fallback.statuses()
        );
        if (snapshot.tree().isEmpty()) {
            return fallback.withWarnings(warnings);
        }
        return snapshot.withWarnings(warnings);
    }

    private List<ApiCredential> loadCredentials() {
        return locationsIikoServerSourceSettingsService.loadForRuntime(sharedConfigService.loadSettings()).stream()
                .filter(LocationIikoServerSource::enabled)
                .map(source -> new ApiCredential(
                        normalizeText(source.baseUrl()),
                        normalizeText(source.apiLogin()),
                        normalizeText(source.apiSecret())))
                .filter(credential ->
                        StringUtils.hasText(credential.baseUrl())
                                && StringUtils.hasText(credential.apiLogin())
                                && StringUtils.hasText(credential.apiSecret()))
                .distinct()
                .toList();
    }

    private LocationCatalogSnapshot loadFallbackCatalog() {
        Map<String, Object> root = loadFallbackPayload();
        Map<String, Object> tree = toStringObjectMap(root.get("tree"));
        Map<String, Object> statuses = toStringObjectMap(root.get("statuses"));
        return new LocationCatalogSnapshot(tree, statuses, DEFAULT_SOURCE, true, List.of());
    }

    private Map<String, Object> loadFallbackPayload() {
        JsonNode payload = sharedConfigService.loadLocations();
        if (payload == null || !payload.isObject()) {
            return new LinkedHashMap<>();
        }
        return objectMapper.convertValue(payload, Map.class);
    }

    Map<String, Object> mergeCatalogIntoPayload(Map<String, Object> basePayload,
                                                LocationCatalogSnapshot snapshot) {
        LinkedHashMap<String, Object> merged = new LinkedHashMap<>();
        if (basePayload != null && !basePayload.isEmpty()) {
            merged.putAll(basePayload);
        }

        Map<String, Object> resolvedTree = snapshot != null && !snapshot.tree().isEmpty()
                ? new LinkedHashMap<>(snapshot.tree())
                : toStringObjectMap(merged.get("tree"));
        Map<String, Object> resolvedStatuses = snapshot != null && !snapshot.statuses().isEmpty()
                ? new LinkedHashMap<>(snapshot.statuses())
                : toStringObjectMap(merged.get("statuses"));

        merged.put("tree", resolvedTree);
        merged.put("statuses", resolvedStatuses);

        if (snapshot != null && LIVE_SOURCE.equals(snapshot.source()) && !resolvedTree.isEmpty()) {
            merged.put("city_meta", mergeGeneratedMeta(
                    buildCityMetaFromTree(resolvedTree),
                    merged.get("city_meta")));
            merged.put("location_meta", mergeGeneratedMeta(
                    buildLocationMetaFromTree(resolvedTree),
                    merged.get("location_meta")));
        }

        return merged;
    }

    private Map<String, Object> buildTree(Collection<String> departmentNames, List<String> knownCities) {
        return buildMergedTree(parseDepartments(departmentNames, knownCities), Map.of());
    }

    private List<ParsedDepartment> parseDepartments(Collection<String> departmentNames, List<String> knownCities) {
        List<String> sortedCities = knownCities.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed().thenComparing(String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<ParsedDepartment> result = new ArrayList<>();
        if (departmentNames == null) {
            return result;
        }
        for (String departmentName : departmentNames) {
            ParsedDepartment parsed = parseDepartment(departmentName, sortedCities);
            if (parsed != null) {
                result.add(parsed);
            }
        }
        return result;
    }

    private Map<String, Object> buildMergedTree(List<ParsedDepartment> activeDepartments,
                                                Map<String, Object> fallbackTree) {
        Map<String, Map<String, Map<String, Set<String>>>> rawTree = new LinkedHashMap<>();
        for (ParsedDepartment parsed : activeDepartments) {
            addLocation(rawTree, parsed.business(), parsed.locationType(), parsed.city(), parsed.locationName());
        }
        mergeFallbackTree(rawTree, fallbackTree);
        return sortTree(rawTree);
    }

    private Map<String, Object> buildLocationStatuses(Map<String, Object> mergedTree,
                                                      List<ParsedDepartment> activeDepartments,
                                                      Map<String, Object> fallbackStatuses) {
        LinkedHashMap<String, Object> statuses = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : toStringObjectMap(fallbackStatuses).entrySet()) {
            if (!isLocationStatusKey(entry.getKey())) {
                statuses.put(entry.getKey(), entry.getValue());
            }
        }

        Set<String> activeKeys = new LinkedHashSet<>();
        for (ParsedDepartment department : activeDepartments) {
            activeKeys.add(makeStatusKey("location", department.business(), department.locationType(), department.city(), department.locationName()));
        }

        forEachLocation(mergedTree, (business, locationType, city, locationName) -> {
            String key = makeStatusKey("location", business, locationType, city, locationName);
            statuses.put(key, activeKeys.contains(key) ? STATUS_ACTIVE : STATUS_CLOSED);
        });
        return statuses;
    }

    private void mergeFallbackTree(Map<String, Map<String, Map<String, Set<String>>>> rawTree,
                                   Map<String, Object> fallbackTree) {
        forEachLocation(fallbackTree, (business, locationType, city, locationName) ->
                addLocation(rawTree, business, locationType, city, locationName));
    }

    private void addLocation(Map<String, Map<String, Map<String, Set<String>>>> rawTree,
                             String business,
                             String locationType,
                             String city,
                             String locationName) {
        if (!StringUtils.hasText(business)
                || !StringUtils.hasText(locationType)
                || !StringUtils.hasText(city)
                || !StringUtils.hasText(locationName)) {
            return;
        }
        rawTree
                .computeIfAbsent(business, key -> new LinkedHashMap<>())
                .computeIfAbsent(locationType, key -> new LinkedHashMap<>())
                .computeIfAbsent(city, key -> new LinkedHashSet<>())
                .add(locationName);
    }

    private void forEachLocation(Map<String, Object> tree,
                                 LocationConsumer consumer) {
        if (tree == null || consumer == null) {
            return;
        }
        for (Map.Entry<String, Object> businessEntry : toStringObjectMap(tree).entrySet()) {
            String business = normalizeText(businessEntry.getKey());
            Map<String, Object> types = toStringObjectMap(businessEntry.getValue());
            for (Map.Entry<String, Object> typeEntry : types.entrySet()) {
                String locationType = normalizeText(typeEntry.getKey());
                Map<String, Object> cities = toStringObjectMap(typeEntry.getValue());
                for (Map.Entry<String, Object> cityEntry : cities.entrySet()) {
                    String city = normalizeText(cityEntry.getKey());
                    for (String locationName : toStringList(cityEntry.getValue())) {
                        consumer.accept(business, locationType, city, normalizeText(locationName));
                    }
                }
            }
        }
    }

    private boolean isLocationStatusKey(String key) {
        return StringUtils.hasText(key) && key.startsWith("location::");
    }

    private String makeStatusKey(String level, String... parts) {
        StringBuilder builder = new StringBuilder(level == null ? "" : level.trim());
        if (parts != null) {
            for (String part : parts) {
                builder.append("::").append(part == null ? "" : part.trim());
            }
        }
        return builder.toString();
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
            locationName = stripLeadingLocationDelimiters(remainder.substring(city.length()).trim());
        } else {
            city = firstToken(remainder);
            if (!startsWithLetterOrDigit(city)) {
                return null;
            }
            locationName = city.length() < remainder.length()
                    ? stripLeadingLocationDelimiters(remainder.substring(city.length()).trim())
                    : "";
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
        return Character.isWhitespace(next) || next == ',' || next == '.';
    }

    private boolean startsWithLetterOrDigit(String value) {
        return StringUtils.hasText(value) && Character.isLetterOrDigit(value.charAt(0));
    }

    private String stripLeadingLocationDelimiters(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        int index = 0;
        while (index < value.length()) {
            char ch = value.charAt(index);
            if (Character.isWhitespace(ch) || ch == '-' || ch == '_' || ch == ',' || ch == '.') {
                index += 1;
                continue;
            }
            break;
        }
        return value.substring(index).trim();
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

    private Map<String, Object> buildCityMetaFromTree(Map<String, Object> tree) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> businessEntry : toStringObjectMap(tree).entrySet()) {
            String business = normalizeText(businessEntry.getKey());
            Map<String, Object> types = toStringObjectMap(businessEntry.getValue());
            for (Map.Entry<String, Object> typeEntry : types.entrySet()) {
                String partnerType = normalizeText(typeEntry.getKey());
                Map<String, Object> cities = toStringObjectMap(typeEntry.getValue());
                for (String city : cities.keySet()) {
                    if (!StringUtils.hasText(business) || !StringUtils.hasText(partnerType) || !StringUtils.hasText(city)) {
                        continue;
                    }
                    result.put(String.join("::", business, partnerType, city), defaultMeta(partnerType));
                }
            }
        }
        return result;
    }

    private Map<String, Object> buildLocationMetaFromTree(Map<String, Object> tree) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> businessEntry : toStringObjectMap(tree).entrySet()) {
            String business = normalizeText(businessEntry.getKey());
            Map<String, Object> types = toStringObjectMap(businessEntry.getValue());
            for (Map.Entry<String, Object> typeEntry : types.entrySet()) {
                String partnerType = normalizeText(typeEntry.getKey());
                Map<String, Object> cities = toStringObjectMap(typeEntry.getValue());
                for (Map.Entry<String, Object> cityEntry : cities.entrySet()) {
                    String city = normalizeText(cityEntry.getKey());
                    for (String location : toStringList(cityEntry.getValue())) {
                        if (!StringUtils.hasText(business)
                                || !StringUtils.hasText(partnerType)
                                || !StringUtils.hasText(city)
                                || !StringUtils.hasText(location)) {
                            continue;
                        }
                        result.put(String.join("::", business, partnerType, city, location), defaultMeta(partnerType));
                    }
                }
            }
        }
        return result;
    }

    private Map<String, Object> defaultMeta(String partnerType) {
        LinkedHashMap<String, Object> meta = new LinkedHashMap<>();
        meta.put("country", DEFAULT_COUNTRY);
        meta.put("partner_type", StringUtils.hasText(partnerType) ? partnerType : "");
        return meta;
    }

    private Map<String, Object> mergeGeneratedMeta(Map<String, Object> generated,
                                                   Object existingRaw) {
        LinkedHashMap<String, Object> merged = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : toStringObjectMap(generated).entrySet()) {
            merged.put(entry.getKey(), new LinkedHashMap<>(toStringObjectMap(entry.getValue())));
        }
        for (Map.Entry<String, Object> entry : toStringObjectMap(existingRaw).entrySet()) {
            LinkedHashMap<String, Object> attrs = new LinkedHashMap<>(toStringObjectMap(merged.get(entry.getKey())));
            attrs.putAll(toStringObjectMap(entry.getValue()));
            merged.put(entry.getKey(), attrs);
        }
        return merged;
    }

    private List<String> toStringList(Object rawValue) {
        if (rawValue instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    String value = item.toString().trim();
                    if (!value.isEmpty()) {
                        result.add(value);
                    }
                }
            }
            return result;
        }
        return List.of();
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
        String requestAccessToken(String baseUrl, String apiLogin, String apiSecret) throws Exception;

        List<String> loadActiveDepartmentNames(String baseUrl, String token) throws Exception;
    }

    private record CachedCatalog(LocationCatalogSnapshot snapshot, Instant cachedAt) {
        private boolean isFresh() {
            return snapshot != null && cachedAt != null && cachedAt.plus(CACHE_TTL).isAfter(Instant.now());
        }
    }

    private record ApiCredential(String baseUrl, String apiLogin, String apiSecret) {

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ApiCredential credential)) {
                return false;
            }
            return Objects.equals(baseUrl, credential.baseUrl)
                    && Objects.equals(apiLogin, credential.apiLogin)
                    && Objects.equals(apiSecret, credential.apiSecret);
        }

        @Override
        public int hashCode() {
            return Objects.hash(baseUrl, apiLogin, apiSecret);
        }
    }

    private record ParsedDepartment(String business,
                                    String locationType,
                                    String city,
                                    String locationName) {
    }

    @FunctionalInterface
    private interface LocationConsumer {
        void accept(String business, String locationType, String city, String locationName);
    }

    static final class HttpIikoDepartmentGateway implements IikoDepartmentGateway {

        private final ObjectMapper objectMapper;
        private final HttpClient httpClient;

        HttpIikoDepartmentGateway(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(HTTP_TIMEOUT)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
        }

        @Override
        public String requestAccessToken(String baseUrl, String apiLogin, String apiSecret) throws Exception {
            String url = buildUrl(
                    baseUrl,
                    "/resto/api/auth",
                    Map.of(
                            "login", apiLogin,
                            "pass", apiSecret
                    )
            );
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(HTTP_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("HTTP " + response.statusCode() + " при получении access token");
            }
            String body = firstNonBlank(response.body(), "{}");
            String token = tryExtractToken(body);
            if (!StringUtils.hasText(token)) {
                throw new IllegalStateException("iiko не вернул access token");
            }
            return token.trim();
        }

        @Override
        public List<String> loadActiveDepartmentNames(String baseUrl, String token) throws Exception {
            String url = buildUrl(
                    baseUrl,
                    "/resto/api/corporation/departments/",
                    Map.of("key", token)
            );
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(HTTP_TIMEOUT)
                    .header("Accept", "application/xml, text/xml, */*")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("HTTP " + response.statusCode() + " при запросе departments");
            }
            List<String> departmentNames = new ArrayList<>();
            for (Element department : parseDepartmentElements(firstNonBlank(response.body(), ""))) {
                if (!isActiveDepartment(department)) {
                    continue;
                }
                String name = firstNonBlank(
                        childText(department, "name"),
                        childText(department, "departmentName"),
                        childText(department, "title")
                );
                if (StringUtils.hasText(name)) {
                    departmentNames.add(name.trim());
                }
            }
            return departmentNames.stream().distinct().toList();
        }

        private List<Element> parseDepartmentElements(String xml) throws Exception {
            if (!StringUtils.hasText(xml)) {
                return List.of();
            }
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            NodeList items = document.getElementsByTagName("corporateItemDto");
            List<Element> departments = new ArrayList<>();
            for (int index = 0; index < items.getLength(); index++) {
                if (!(items.item(index) instanceof Element element)) {
                    continue;
                }
                String type = firstNonBlank(childText(element, "type"), element.getAttribute("type"));
                if ("DEPARTMENT".equalsIgnoreCase(type)) {
                    departments.add(element);
                }
            }
            return departments;
        }

        private boolean isActiveDepartment(Element department) {
            if (department == null) {
                return false;
            }
            String isActive = firstNonBlank(childText(department, "isActive"), childText(department, "active"));
            if (StringUtils.hasText(isActive)) {
                return parseBoolean(isActive, false);
            }
            if (parseBoolean(firstNonBlank(childText(department, "deleted"), childText(department, "isDeleted")), false)) {
                return false;
            }
            if (parseBoolean(firstNonBlank(childText(department, "disabled"), childText(department, "isDisabled")), false)) {
                return false;
            }
            if (parseBoolean(childText(department, "archived"), false)) {
                return false;
            }
            return true;
        }

        private String tryExtractToken(String body) {
            if (!StringUtils.hasText(body)) {
                return null;
            }
            try {
                JsonNode parsed = objectMapper.readTree(body);
                return firstNonBlank(
                        parsed.path("access_token").asText(null),
                        parsed.path("accessToken").asText(null),
                        parsed.path("token").asText(null)
                );
            } catch (Exception ignored) {
                return body.replace("\"", "").trim();
            }
        }

        private String childText(Element parent, String tagName) {
            if (parent == null || !StringUtils.hasText(tagName)) {
                return null;
            }
            NodeList nodes = parent.getElementsByTagName(tagName);
            if (nodes.getLength() == 0 || nodes.item(0) == null) {
                return null;
            }
            String value = nodes.item(0).getTextContent();
            return StringUtils.hasText(value) ? value.trim() : null;
        }

        private boolean parseBoolean(String rawValue, boolean fallback) {
            if (!StringUtils.hasText(rawValue)) {
                return fallback;
            }
            String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
            if ("1".equals(normalized) || "true".equals(normalized) || "yes".equals(normalized)) {
                return true;
            }
            if ("0".equals(normalized) || "false".equals(normalized) || "no".equals(normalized)) {
                return false;
            }
            return fallback;
        }

        private String buildUrl(String baseUrl, String path, Map<String, String> query) {
            StringBuilder result = new StringBuilder(normalizeBaseUrl(baseUrl)).append(path);
            if (query != null && !query.isEmpty()) {
                List<String> parts = new ArrayList<>();
                for (Map.Entry<String, String> entry : query.entrySet()) {
                    parts.add(encode(entry.getKey()) + "=" + encode(entry.getValue()));
                }
                result.append("?").append(String.join("&", parts));
            }
            return result.toString();
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

        private String encode(String value) {
            return URLEncoder.encode(firstNonBlank(value, ""), StandardCharsets.UTF_8);
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
