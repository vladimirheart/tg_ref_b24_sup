package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class IntegrationNetworkService {

    private static final Logger log = LoggerFactory.getLogger(IntegrationNetworkService.class);
    private static final int DEFAULT_FAILOVER_DOWNTIME_SECONDS = 120;
    private static final Duration HEALTH_CHECK_TIMEOUT = Duration.ofSeconds(2);

    private final SharedConfigService sharedConfigService;
    private final ObjectMapper objectMapper;
    private final Map<String, Long> routeUnavailableUntil = new ConcurrentHashMap<>();

    public IntegrationNetworkService(SharedConfigService sharedConfigService, ObjectMapper objectMapper) {
        this.sharedConfigService = sharedConfigService;
        this.objectMapper = objectMapper;
    }

    public RouteSettings resolveProjectRoute() {
        Map<String, Object> settings = sharedConfigService.loadSettings();
        return resolveProjectRoute(settings);
    }

    public RouteSettings resolveBotRoute(Channel channel) {
        Map<String, Object> settings = sharedConfigService.loadSettings();
        Map<String, Object> channelRoute = extractChannelRoute(channel);
        if (!channelRoute.isEmpty()) {
            return resolveRoute(channelRoute, settings, true);
        }

        Map<String, Object> integrationNetwork = mapValue(settings.get("integration_network"));
        Map<String, Object> bots = mapValue(integrationNetwork.get("bots"));
        RouteSettings botRoute = resolveRoute(bots, settings, true);
        if (!"inherit".equals(botRoute.mode())) {
            return botRoute;
        }
        return resolveProjectRoute(settings);
    }

    public Map<String, String> buildProcessEnvironment(RouteSettings route) {
        RouteSettings resolved = route != null ? route : RouteSettings.direct();
        Map<String, String> env = new LinkedHashMap<>();
        env.put("APP_NETWORK_MODE", resolved.mode());
        if (hasText(resolved.profileId())) {
            env.put("APP_NETWORK_PROFILE_ID", resolved.profileId());
        }
        if (!resolved.profileIds().isEmpty()) {
            env.put("APP_NETWORK_PROFILE_IDS", String.join(",", resolved.profileIds()));
        }
        env.put("APP_NETWORK_FAILOVER_DOWNTIME_SECONDS", Integer.toString(resolved.failoverDowntimeSeconds()));
        if ("proxy".equals(resolved.mode()) && resolved.proxySettings() != null && resolved.proxySettings().isConfigured()) {
            ProxySettings proxy = resolved.proxySettings();
            String normalizedScheme = proxy.scheme().toLowerCase(Locale.ROOT);
            env.put("APP_NETWORK_PROXY_SCHEME", proxy.scheme());
            env.put("APP_NETWORK_PROXY_HOST", proxy.host());
            env.put("APP_NETWORK_PROXY_PORT", Integer.toString(proxy.port()));
            if (hasText(proxy.username())) {
                env.put("APP_NETWORK_PROXY_USERNAME", proxy.username());
            }
            if (hasText(proxy.password())) {
                env.put("APP_NETWORK_PROXY_PASSWORD", proxy.password());
            }
            if (hasText(proxy.token())) {
                env.put("APP_NETWORK_PROXY_TOKEN", proxy.token());
            }

            String proxyUrl = buildProxyUrl(proxy);
            env.put("HTTP_PROXY", proxyUrl);
            env.put("HTTPS_PROXY", proxyUrl);
            env.put("http_proxy", proxyUrl);
            env.put("https_proxy", proxyUrl);
            if (normalizedScheme.startsWith("socks") || "vless".equals(normalizedScheme)) {
                env.put("ALL_PROXY", proxyUrl);
                env.put("all_proxy", proxyUrl);
            }
            env.put("JAVA_TOOL_OPTIONS", mergeJavaToolOptions(System.getenv("JAVA_TOOL_OPTIONS"), buildJavaToolOptions(proxy)));
        } else if ("vpn".equals(resolved.mode()) && hasText(resolved.vpnName())) {
            env.put("APP_NETWORK_VPN_NAME", resolved.vpnName());
        }
        return env;
    }

    public HttpClient createChannelHttpClient(Channel channel, Duration timeout) {
        RouteSettings route = resolveBotRoute(channel);
        HttpClient.Builder builder = HttpClient.newBuilder()
            .connectTimeout(timeout != null ? timeout : Duration.ofSeconds(10));

        if ("proxy".equals(route.mode()) && route.proxySettings() != null && route.proxySettings().isConfigured()) {
            ProxySettings proxy = route.proxySettings();
            builder.proxy(ProxySelector.of(new InetSocketAddress(proxy.host(), proxy.port())));
            if (hasText(proxy.username())) {
                builder.authenticator(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(
                            proxy.username(),
                            Objects.toString(proxy.password(), "").toCharArray()
                        );
                    }
                });
            }
        }

        return builder.build();
    }

    public RouteProbeResult probeProfileRoute(Map<String, Object> rawProfile) {
        RouteSettings route = RouteSettings.fromMap(rawProfile, false);
        return probeRoute(route);
    }

    public RouteProbeResult probeRoute(RouteSettings route) {
        RouteSettings normalized = route != null ? route : RouteSettings.direct();
        String key = routeKey(normalized);
        long unavailableUntilMillis = readUnavailableUntil(key);
        long now = System.currentTimeMillis();
        long cooldownMillis = Math.max(0L, unavailableUntilMillis - now);
        int cooldownSeconds = (int) Math.ceil(cooldownMillis / 1000.0d);
        boolean reachable = isRouteReachable(normalized);
        if (reachable) {
            clearUnavailable(key);
            unavailableUntilMillis = 0L;
            cooldownSeconds = 0;
        }
        String host = "";
        int port = 0;
        if ("proxy".equals(normalized.mode()) && normalized.proxySettings() != null) {
            host = stringValue(normalized.proxySettings().host());
            port = normalized.proxySettings().port();
        } else if ("vpn".equals(normalized.mode())) {
            Endpoint endpoint = parseEndpoint(normalized.vpnEndpoint());
            if (endpoint != null) {
                host = stringValue(endpoint.host());
                port = endpoint.port();
            }
        }
        String message = buildProbeMessage(normalized, reachable, cooldownSeconds);
        return new RouteProbeResult(
            normalized.mode(),
            reachable,
            message,
            host,
            port,
            unavailableUntilMillis,
            cooldownSeconds
        );
    }

    private RouteSettings resolveProjectRoute(Map<String, Object> settings) {
        Map<String, Object> integrationNetwork = mapValue(settings.get("integration_network"));
        Map<String, Object> project = mapValue(integrationNetwork.get("project"));
        return resolveRoute(project, settings, false);
    }

    private RouteSettings resolveRoute(Map<String, Object> rawRoute, Map<String, Object> settings, boolean allowInherit) {
        RouteSettings route = RouteSettings.fromMap(rawRoute, allowInherit);
        if ("inherit".equals(route.mode()) && !allowInherit) {
            return RouteSettings.direct();
        }

        if ("profile".equals(route.mode()) && !route.profileIds().isEmpty()) {
            List<RouteSettings> candidates = resolveProfileCandidates(settings, route.profileIds(), allowInherit)
                .stream()
                .map(candidate -> candidate.withFailoverDowntimeSeconds(route.failoverDowntimeSeconds()))
                .toList();
            RouteSettings selected = pickAvailableRoute(candidates, route.failoverDowntimeSeconds());
            if (selected != null) {
                return selected.withFailoverContext(route.profileIds(), route.failoverDowntimeSeconds());
            }
        }
        return route;
    }

    private List<RouteSettings> resolveProfileCandidates(Map<String, Object> settings, List<String> profileIds, boolean allowInherit) {
        if (profileIds == null || profileIds.isEmpty()) {
            return List.of();
        }
        Object profilesRaw = settings.get("integration_network_profiles");
        if (!(profilesRaw instanceof List<?> profiles)) {
            return List.of();
        }
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        for (Object profileRaw : profiles) {
            Map<String, Object> profile = mapValue(profileRaw);
            String id = stringValue(profile.get("id"));
            if (hasText(id) && !byId.containsKey(id)) {
                byId.put(id, profile);
            }
        }
        List<RouteSettings> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String profileId : profileIds) {
            String normalizedId = stringValue(profileId);
            if (!hasText(normalizedId) || !seen.add(normalizedId)) {
                continue;
            }
            Map<String, Object> raw = byId.get(normalizedId);
            if (raw == null) {
                continue;
            }
            RouteSettings candidate = RouteSettings.fromMap(raw, allowInherit).withProfileId(normalizedId);
            if ("proxy".equals(candidate.mode()) && candidate.proxySettings() != null && candidate.proxySettings().isConfigured()) {
                result.add(candidate.withRouteKey(routeKey(candidate)));
                continue;
            }
            if ("vpn".equals(candidate.mode()) && hasText(candidate.vpnName())) {
                result.add(candidate.withRouteKey(routeKey(candidate)));
                continue;
            }
            if ("direct".equals(candidate.mode())) {
                result.add(candidate.withRouteKey(routeKey(candidate)));
            }
        }
        return result;
    }

    private RouteSettings pickAvailableRoute(List<RouteSettings> candidates, int downtimeSeconds) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        int ttl = normalizeDowntimeSeconds(downtimeSeconds);
        for (RouteSettings candidate : candidates) {
            String key = routeKey(candidate);
            if (isTemporarilyUnavailable(key)) {
                continue;
            }
            if (isRouteReachable(candidate)) {
                clearUnavailable(key);
                return candidate;
            }
            markUnavailable(key, ttl);
        }
        for (RouteSettings candidate : candidates) {
            String key = routeKey(candidate);
            if (isRouteReachable(candidate)) {
                clearUnavailable(key);
                return candidate;
            }
            markUnavailable(key, ttl);
        }
        return candidates.get(0);
    }

    private boolean isRouteReachable(RouteSettings candidate) {
        if (candidate == null) {
            return false;
        }
        if ("proxy".equals(candidate.mode())) {
            ProxySettings proxy = candidate.proxySettings();
            if (proxy == null || !proxy.isConfigured()) {
                return false;
            }
            return canConnect(proxy.host(), proxy.port());
        }
        if ("vpn".equals(candidate.mode())) {
            String endpoint = stringValue(candidate.vpnEndpoint());
            if (!hasText(endpoint)) {
                return hasText(candidate.vpnName());
            }
            Endpoint target = parseEndpoint(endpoint);
            if (target == null || !hasText(target.host()) || target.port() <= 0) {
                return true;
            }
            return canConnect(target.host(), target.port());
        }
        return true;
    }

    private boolean canConnect(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), (int) HEALTH_CHECK_TIMEOUT.toMillis());
            return true;
        } catch (Exception ex) {
            log.debug("Health check failed for {}:{}: {}", host, port, ex.getMessage());
            return false;
        }
    }

    private Endpoint parseEndpoint(String raw) {
        String value = stringValue(raw);
        if (!hasText(value)) {
            return null;
        }
        try {
            URI uri = URI.create(value.contains("://") ? value : "https://" + value);
            String host = uri.getHost();
            int port = uri.getPort();
            if (!hasText(host)) {
                return null;
            }
            if (port <= 0) {
                port = "http".equalsIgnoreCase(uri.getScheme()) ? 80 : 443;
            }
            return new Endpoint(host, port);
        } catch (Exception ignored) {
            int separator = value.lastIndexOf(':');
            if (separator > 0 && separator < value.length() - 1) {
                String host = value.substring(0, separator).trim();
                try {
                    int port = Integer.parseInt(value.substring(separator + 1).trim());
                    return new Endpoint(host, port);
                } catch (NumberFormatException ignoredPort) {
                    // fallback below
                }
            }
            return new Endpoint(value, 443);
        }
    }

    private boolean isTemporarilyUnavailable(String key) {
        if (!hasText(key)) {
            return false;
        }
        Long unavailableUntil = routeUnavailableUntil.get(key);
        if (unavailableUntil == null) {
            return false;
        }
        if (System.currentTimeMillis() >= unavailableUntil) {
            routeUnavailableUntil.remove(key);
            return false;
        }
        return true;
    }

    private void markUnavailable(String key, int downtimeSeconds) {
        if (!hasText(key)) {
            return;
        }
        routeUnavailableUntil.put(key, System.currentTimeMillis() + (long) downtimeSeconds * 1000L);
    }

    private void clearUnavailable(String key) {
        if (!hasText(key)) {
            return;
        }
        routeUnavailableUntil.remove(key);
    }

    private long readUnavailableUntil(String key) {
        if (!hasText(key)) {
            return 0L;
        }
        Long unavailableUntil = routeUnavailableUntil.get(key);
        if (unavailableUntil == null) {
            return 0L;
        }
        if (System.currentTimeMillis() >= unavailableUntil) {
            routeUnavailableUntil.remove(key);
            return 0L;
        }
        return unavailableUntil;
    }

    private String buildProbeMessage(RouteSettings route, boolean reachable, int cooldownSeconds) {
        if (route == null) {
            return "Маршрут не задан.";
        }
        if ("direct".equals(route.mode())) {
            return "Режим direct: проверка сети не требуется.";
        }
        if ("inherit".equals(route.mode())) {
            return "Режим inherit: используется унаследованный маршрут.";
        }
        if ("proxy".equals(route.mode())) {
            ProxySettings proxy = route.proxySettings();
            if (proxy == null || !proxy.isConfigured()) {
                return "Прокси-профиль заполнен не полностью.";
            }
            if (reachable) {
                return "Прокси доступен.";
            }
            if (cooldownSeconds > 0) {
                return "Прокси недоступен, действует failover cooldown.";
            }
            return "Прокси недоступен.";
        }
        if ("vpn".equals(route.mode())) {
            if (reachable) {
                return "VPN endpoint доступен.";
            }
            if (cooldownSeconds > 0) {
                return "VPN endpoint недоступен, действует failover cooldown.";
            }
            return "VPN endpoint недоступен.";
        }
        return reachable ? "Маршрут доступен." : "Маршрут недоступен.";
    }

    private String routeKey(RouteSettings route) {
        if (route == null) {
            return "";
        }
        if (hasText(route.routeKey())) {
            return route.routeKey();
        }
        if ("proxy".equals(route.mode()) && route.proxySettings() != null) {
            return "proxy|" + route.profileId() + "|" + route.proxySettings().fingerprint();
        }
        if ("vpn".equals(route.mode())) {
            return "vpn|" + route.profileId() + "|" + route.vpnName() + "|" + route.vpnEndpoint();
        }
        return route.mode() + "|" + route.profileId();
    }

    private int normalizeDowntimeSeconds(int value) {
        if (value <= 0) {
            return DEFAULT_FAILOVER_DOWNTIME_SECONDS;
        }
        return Math.max(10, Math.min(86_400, value));
    }

    private record Endpoint(String host, int port) {
    }

    public record RouteProbeResult(String mode,
                                   boolean reachable,
                                   String message,
                                   String host,
                                   int port,
                                   long unavailableUntilMillis,
                                   int cooldownSeconds) {
    }

    private Map<String, Object> extractChannelRoute(Channel channel) {
        if (channel == null || !hasText(channel.getDeliverySettings())) {
            return Map.of();
        }
        try {
            Map<String, Object> deliverySettings = objectMapper.readValue(channel.getDeliverySettings(), LinkedHashMap.class);
            return mapValue(deliverySettings.get("network_route"));
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String buildProxyUrl(ProxySettings proxy) {
        StringBuilder value = new StringBuilder();
        value.append(proxy.scheme()).append("://");
        String scheme = proxy.scheme().toLowerCase(Locale.ROOT);
        if ("vless".equals(scheme) && hasText(proxy.token())) {
            value.append(proxy.token()).append('@');
        } else if (hasText(proxy.username())) {
            value.append(proxy.username());
            if (hasText(proxy.password())) {
                value.append(':').append(proxy.password());
            }
            value.append('@');
        }
        value.append(proxy.host()).append(':').append(proxy.port());
        return value.toString();
    }

    private String buildJavaToolOptions(ProxySettings proxy) {
        List<String> options = new ArrayList<>();
        String scheme = proxy.scheme().toLowerCase(Locale.ROOT);
        if (scheme.startsWith("socks") || "vless".equals(scheme)) {
            options.add("-DsocksProxyHost=" + proxy.host());
            options.add("-DsocksProxyPort=" + proxy.port());
            if (hasText(proxy.username())) {
                options.add("-Djava.net.socks.username=" + proxy.username());
            }
            if (hasText(proxy.password())) {
                options.add("-Djava.net.socks.password=" + proxy.password());
            }
        } else {
            options.add("-Dhttp.proxyHost=" + proxy.host());
            options.add("-Dhttp.proxyPort=" + proxy.port());
            options.add("-Dhttps.proxyHost=" + proxy.host());
            options.add("-Dhttps.proxyPort=" + proxy.port());
            if (hasText(proxy.username())) {
                options.add("-Dhttp.proxyUser=" + proxy.username());
                options.add("-Dhttps.proxyUser=" + proxy.username());
                options.add("-Djdk.http.auth.proxying.disabledSchemes=");
                options.add("-Djdk.http.auth.tunneling.disabledSchemes=");
            }
            if (hasText(proxy.password())) {
                options.add("-Dhttp.proxyPassword=" + proxy.password());
                options.add("-Dhttps.proxyPassword=" + proxy.password());
            }
        }
        return String.join(" ", options);
    }

    private String mergeJavaToolOptions(String existing, String additional) {
        String normalizedExisting = existing == null ? "" : existing.trim();
        String normalizedAdditional = additional == null ? "" : additional.trim();
        if (normalizedExisting.isEmpty()) {
            return normalizedAdditional;
        }
        if (normalizedAdditional.isEmpty()) {
            return normalizedExisting;
        }
        return normalizedExisting + " " + normalizedAdditional;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, value) -> normalized.put(Objects.toString(key, ""), value));
            return normalized;
        }
        return Map.of();
    }

    private String stringValue(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record RouteSettings(String mode,
                                String profileId,
                                ProxySettings proxySettings,
                                String vpnName,
                                String vpnEndpoint,
                                int failoverDowntimeSeconds,
                                List<String> profileIds,
                                String routeKey) {

        public static RouteSettings direct() {
            return new RouteSettings("direct", "", ProxySettings.empty(), "", "", DEFAULT_FAILOVER_DOWNTIME_SECONDS, List.of(), "");
        }

        public static RouteSettings fromMap(Map<String, Object> raw, boolean allowInherit) {
            if (raw == null || raw.isEmpty()) {
                return allowInherit
                    ? new RouteSettings("inherit", "", ProxySettings.empty(), "", "", DEFAULT_FAILOVER_DOWNTIME_SECONDS, List.of(), "")
                    : direct();
            }

            String mode = normalizeMode(raw.get("mode"), allowInherit);
            String profileId = string(raw.get("profile_id"));
            if (profileId.isEmpty()) {
                profileId = string(raw.get("profileId"));
            }
            List<String> profileIds = parseProfileIds(raw, profileId);
            if (profileId.isEmpty() && !profileIds.isEmpty()) {
                profileId = profileIds.get(0);
            }
            ProxySettings proxy = ProxySettings.fromMap(asMap(raw.get("proxy")));
            String vpnName = string(raw.get("vpn_name"));
            if (vpnName.isEmpty()) {
                vpnName = string(raw.get("vpnName"));
            }
            Map<String, Object> vpn = asMap(raw.get("vpn"));
            if (vpnName.isEmpty()) {
                vpnName = string(vpn.get("name"));
            }
            String vpnEndpoint = string(vpn.get("endpoint"));
            int failoverDowntimeSeconds = normalizeDowntime(readInteger(raw, "failover_downtime_seconds", "failoverDowntimeSeconds"));
            return new RouteSettings(mode, profileId, proxy, vpnName, vpnEndpoint, failoverDowntimeSeconds, profileIds, "");
        }

        public RouteSettings withProfileId(String profileId) {
            List<String> mergedProfileIds = new ArrayList<>(profileIds);
            String normalized = string(profileId);
            if (!normalized.isEmpty() && !mergedProfileIds.contains(normalized)) {
                mergedProfileIds.add(normalized);
            }
            return new RouteSettings(mode, normalized, proxySettings, vpnName, vpnEndpoint, failoverDowntimeSeconds, List.copyOf(mergedProfileIds), routeKey);
        }

        public RouteSettings withFailoverDowntimeSeconds(int failoverDowntimeSeconds) {
            return new RouteSettings(mode, profileId, proxySettings, vpnName, vpnEndpoint, normalizeDowntime(failoverDowntimeSeconds), profileIds, routeKey);
        }

        public RouteSettings withFailoverContext(List<String> profileIds, int failoverDowntimeSeconds) {
            List<String> normalizedProfileIds = normalizeProfileIds(profileIds);
            String primaryProfileId = profileId;
            if (primaryProfileId.isBlank() && !normalizedProfileIds.isEmpty()) {
                primaryProfileId = normalizedProfileIds.get(0);
            }
            return new RouteSettings(mode, primaryProfileId, proxySettings, vpnName, vpnEndpoint, normalizeDowntime(failoverDowntimeSeconds), normalizedProfileIds, routeKey);
        }

        public RouteSettings withRouteKey(String routeKey) {
            return new RouteSettings(mode, profileId, proxySettings, vpnName, vpnEndpoint, failoverDowntimeSeconds, profileIds, string(routeKey));
        }

        private static String normalizeMode(Object rawMode, boolean allowInherit) {
            String mode = string(rawMode).toLowerCase(Locale.ROOT);
            if (mode.isEmpty()) {
                return allowInherit ? "inherit" : "direct";
            }
            if (List.of("direct", "proxy", "vpn", "profile").contains(mode)) {
                return mode;
            }
            if ("inherit".equals(mode) && allowInherit) {
                return mode;
            }
            return allowInherit ? "inherit" : "direct";
        }

        private static List<String> parseProfileIds(Map<String, Object> raw, String profileId) {
            List<String> parsed = normalizeProfileIds(asStringList(raw.get("profile_ids")));
            if (parsed.isEmpty()) {
                parsed = normalizeProfileIds(asStringList(raw.get("profileIds")));
            }
            if (parsed.isEmpty() && !string(profileId).isEmpty()) {
                return List.of(string(profileId));
            }
            return parsed;
        }

        private static List<String> asStringList(Object raw) {
            if (!(raw instanceof List<?> values)) {
                return List.of();
            }
            List<String> result = new ArrayList<>();
            for (Object value : values) {
                String normalized = string(value);
                if (!normalized.isEmpty()) {
                    result.add(normalized);
                }
            }
            return result;
        }

        private static List<String> normalizeProfileIds(List<String> values) {
            if (values == null || values.isEmpty()) {
                return List.of();
            }
            LinkedHashSet<String> unique = new LinkedHashSet<>();
            for (String value : values) {
                String normalized = string(value);
                if (!normalized.isEmpty()) {
                    unique.add(normalized);
                }
            }
            return List.copyOf(unique);
        }

        private static int readInteger(Map<String, Object> raw, String... keys) {
            for (String key : keys) {
                Object value = raw.get(key);
                if (value == null) {
                    continue;
                }
                try {
                    return Integer.parseInt(string(value));
                } catch (NumberFormatException ignored) {
                    // next key
                }
            }
            return 0;
        }

        private static int normalizeDowntime(int value) {
            if (value <= 0) {
                return DEFAULT_FAILOVER_DOWNTIME_SECONDS;
            }
            return Math.max(10, Math.min(86_400, value));
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> asMap(Object raw) {
            if (raw instanceof Map<?, ?> map) {
                Map<String, Object> normalized = new LinkedHashMap<>();
                map.forEach((key, value) -> normalized.put(Objects.toString(key, ""), value));
                return normalized;
            }
            return Map.of();
        }

        private static String string(Object raw) {
            return raw == null ? "" : String.valueOf(raw).trim();
        }
    }

    public record ProxySettings(String scheme, String host, int port, String username, String password, String token) {

        public static ProxySettings empty() {
            return new ProxySettings("http", "", 0, "", "", "");
        }

        public static ProxySettings fromMap(Map<String, Object> raw) {
            if (raw == null || raw.isEmpty()) {
                return empty();
            }
            String scheme = string(raw.get("scheme"));
            if (scheme.isEmpty()) {
                scheme = "http";
            }
            String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
            if (!List.of("http", "https", "socks5", "socks4", "vless").contains(normalizedScheme)) {
                normalizedScheme = "http";
            }
            String host = string(raw.get("host"));
            int port = integer(raw.get("port"));
            String username = string(raw.get("username"));
            String password = string(raw.get("password"));
            String token = string(raw.get("token"));
            if (token.isEmpty() && "vless".equals(normalizedScheme)) {
                token = !username.isEmpty() ? username : password;
            }
            return new ProxySettings(normalizedScheme, host, port, username, password, token);
        }

        public boolean isConfigured() {
            if ("vless".equals(scheme.toLowerCase(Locale.ROOT))) {
                return !host.isBlank() && port > 0 && !token.isBlank();
            }
            return !host.isBlank() && port > 0;
        }

        public String fingerprint() {
            return scheme + "|" + host + "|" + port + "|" + username + "|" + token;
        }

        private static int integer(Object raw) {
            if (raw instanceof Number number) {
                return number.intValue();
            }
            try {
                return Integer.parseInt(string(raw));
            } catch (NumberFormatException ex) {
                return 0;
            }
        }

        private static String string(Object raw) {
            return raw == null ? "" : String.valueOf(raw).trim();
        }
    }
}
