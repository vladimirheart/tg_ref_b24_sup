package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class IntegrationNetworkService {

    private final SharedConfigService sharedConfigService;
    private final ObjectMapper objectMapper;

    public IntegrationNetworkService(SharedConfigService sharedConfigService, ObjectMapper objectMapper) {
        this.sharedConfigService = sharedConfigService;
        this.objectMapper = objectMapper;
    }

    public RouteSettings resolveProjectRoute() {
        Map<String, Object> settings = sharedConfigService.loadSettings();
        Map<String, Object> integrationNetwork = mapValue(settings.get("integration_network"));
        Map<String, Object> project = mapValue(integrationNetwork.get("project"));
        return resolveRoute(project, settings, false);
    }

    public RouteSettings resolveBotRoute(Channel channel) {
        Map<String, Object> channelRoute = extractChannelRoute(channel);
        if (!channelRoute.isEmpty()) {
            return resolveRoute(channelRoute, sharedConfigService.loadSettings(), true);
        }

        Map<String, Object> settings = sharedConfigService.loadSettings();
        Map<String, Object> integrationNetwork = mapValue(settings.get("integration_network"));
        Map<String, Object> bots = mapValue(integrationNetwork.get("bots"));
        RouteSettings botRoute = resolveRoute(bots, settings, true);
        if (!"inherit".equals(botRoute.mode())) {
            return botRoute;
        }
        return resolveProjectRoute();
    }

    public Map<String, String> buildProcessEnvironment(RouteSettings route) {
        RouteSettings resolved = route != null ? route : RouteSettings.direct();
        Map<String, String> env = new LinkedHashMap<>();
        env.put("APP_NETWORK_MODE", resolved.mode());
        if (hasText(resolved.profileId())) {
            env.put("APP_NETWORK_PROFILE_ID", resolved.profileId());
        }
        if ("proxy".equals(resolved.mode()) && resolved.proxySettings() != null && resolved.proxySettings().isConfigured()) {
            ProxySettings proxy = resolved.proxySettings();
            env.put("APP_NETWORK_PROXY_SCHEME", proxy.scheme());
            env.put("APP_NETWORK_PROXY_HOST", proxy.host());
            env.put("APP_NETWORK_PROXY_PORT", Integer.toString(proxy.port()));
            if (hasText(proxy.username())) {
                env.put("APP_NETWORK_PROXY_USERNAME", proxy.username());
            }
            if (hasText(proxy.password())) {
                env.put("APP_NETWORK_PROXY_PASSWORD", proxy.password());
            }

            String proxyUrl = buildProxyUrl(proxy);
            env.put("HTTP_PROXY", proxyUrl);
            env.put("HTTPS_PROXY", proxyUrl);
            env.put("http_proxy", proxyUrl);
            env.put("https_proxy", proxyUrl);
            if (proxy.scheme().toLowerCase(Locale.ROOT).startsWith("socks")) {
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

    private RouteSettings resolveRoute(Map<String, Object> rawRoute, Map<String, Object> settings, boolean allowInherit) {
        RouteSettings route = RouteSettings.fromMap(rawRoute, allowInherit);
        if ("profile".equals(route.mode()) && hasText(route.profileId())) {
            RouteSettings profileRoute = resolveProfileRoute(settings, route.profileId(), allowInherit);
            if (profileRoute != null) {
                return profileRoute.withProfileId(route.profileId());
            }
        }
        if ("inherit".equals(route.mode()) && !allowInherit) {
            return RouteSettings.direct();
        }
        return route;
    }

    private RouteSettings resolveProfileRoute(Map<String, Object> settings, String profileId, boolean allowInherit) {
        Object profilesRaw = settings.get("integration_network_profiles");
        if (!(profilesRaw instanceof List<?> profiles)) {
            return null;
        }
        for (Object profileRaw : profiles) {
            Map<String, Object> profile = mapValue(profileRaw);
            if (profileId.equals(stringValue(profile.get("id")))) {
                return RouteSettings.fromMap(profile, allowInherit);
            }
        }
        return null;
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
        if (hasText(proxy.username())) {
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
        if (scheme.startsWith("socks")) {
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

    public record RouteSettings(String mode, String profileId, ProxySettings proxySettings, String vpnName) {

        public static RouteSettings direct() {
            return new RouteSettings("direct", "", ProxySettings.empty(), "");
        }

        public static RouteSettings fromMap(Map<String, Object> raw, boolean allowInherit) {
            if (raw == null || raw.isEmpty()) {
                return allowInherit ? new RouteSettings("inherit", "", ProxySettings.empty(), "") : direct();
            }

            String mode = normalizeMode(raw.get("mode"), allowInherit);
            String profileId = string(raw.get("profile_id"));
            if (profileId.isEmpty()) {
                profileId = string(raw.get("profileId"));
            }
            ProxySettings proxy = ProxySettings.fromMap(asMap(raw.get("proxy")));
            String vpnName = string(raw.get("vpn_name"));
            if (vpnName.isEmpty()) {
                vpnName = string(raw.get("vpnName"));
            }
            if (vpnName.isEmpty()) {
                vpnName = string(asMap(raw.get("vpn")).get("name"));
            }
            return new RouteSettings(mode, profileId, proxy, vpnName);
        }

        public RouteSettings withProfileId(String profileId) {
            return new RouteSettings(mode, profileId, proxySettings, vpnName);
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

    public record ProxySettings(String scheme, String host, int port, String username, String password) {

        public static ProxySettings empty() {
            return new ProxySettings("http", "", 0, "", "");
        }

        public static ProxySettings fromMap(Map<String, Object> raw) {
            if (raw == null || raw.isEmpty()) {
                return empty();
            }
            String scheme = string(raw.get("scheme"));
            if (scheme.isEmpty()) {
                scheme = "http";
            }
            String host = string(raw.get("host"));
            int port = integer(raw.get("port"));
            String username = string(raw.get("username"));
            String password = string(raw.get("password"));
            return new ProxySettings(scheme, host, port, username, password);
        }

        public boolean isConfigured() {
            return !host.isBlank() && port > 0;
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
