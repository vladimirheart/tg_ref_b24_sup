package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class IntegrationNetworkService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final SharedConfigService sharedConfigService;
    private final ObjectMapper objectMapper;

    public IntegrationNetworkService(SharedConfigService sharedConfigService,
                                     ObjectMapper objectMapper) {
        this.sharedConfigService = sharedConfigService;
        this.objectMapper = objectMapper;
    }

    public HttpClient createProjectHttpClient(Duration connectTimeout) {
        return createHttpClient(resolveProjectRoute(), connectTimeout);
    }

    public HttpClient createChannelHttpClient(Channel channel, Duration connectTimeout) {
        return createHttpClient(resolveChannelRoute(channel), connectTimeout);
    }

    public RouteSettings resolveProjectRoute() {
        NetworkSettings settings = loadSettings();
        return settings.project();
    }

    public RouteSettings resolveBotRoute(Channel channel) {
        NetworkSettings settings = loadSettings();
        RouteSettings channelRoute = extractChannelRoute(channel);
        if (channelRoute != null && !channelRoute.isInherited()) {
            return channelRoute;
        }
        RouteSettings featureRoute = settings.bots();
        if (featureRoute != null && !featureRoute.isInherited()) {
            return featureRoute;
        }
        return settings.project();
    }

    public RouteSettings resolveChannelRoute(Channel channel) {
        RouteSettings route = extractChannelRoute(channel);
        if (route != null && !route.isInherited()) {
            return route;
        }
        return resolveProjectRoute();
    }

    public Map<String, String> buildProcessEnvironment(RouteSettings route) {
        Map<String, String> env = new LinkedHashMap<>();
        if (route == null || route.direct() || route.isInherited()) {
            env.put("APP_NETWORK_MODE", route == null ? "direct" : route.mode());
            return env;
        }
        env.put("APP_NETWORK_MODE", route.mode());
        if (route.proxy()) {
            ProxySettings proxy = route.proxySettings();
            String proxyUrl = buildProxyUrl(proxy);
            env.put("HTTP_PROXY", proxyUrl);
            env.put("HTTPS_PROXY", proxyUrl);
            env.put("ALL_PROXY", proxyUrl);
            if (proxy.socks()) {
                env.put("SOCKS_PROXY", proxyUrl);
            }
            env.put("APP_NETWORK_PROXY_SCHEME", proxy.scheme());
            env.put("APP_NETWORK_PROXY_HOST", proxy.host());
            env.put("APP_NETWORK_PROXY_PORT", Integer.toString(proxy.port()));
            appendJavaToolOption(env, buildJavaProxyOptions(proxy));
        }
        if (route.vpn()) {
            VpnSettings vpn = route.vpnSettings();
            putIfHasText(env, "APP_NETWORK_VPN_NAME", vpn.name());
            putIfHasText(env, "APP_NETWORK_VPN_ENDPOINT", vpn.endpoint());
            putIfHasText(env, "APP_NETWORK_VPN_CONFIG_PATH", vpn.configPath());
            putIfHasText(env, "APP_NETWORK_VPN_NOTES", vpn.notes());
            vpn.extraEnv().forEach((key, value) -> putIfHasText(env, key, value));
        }
        return env;
    }

    public Map<String, Object> normalizeSettingsPayload(Object raw) {
        return toMap(loadSettings().toMap(raw));
    }

    private HttpClient createHttpClient(RouteSettings route, Duration connectTimeout) {
        HttpClient.Builder builder = HttpClient.newBuilder();
        if (connectTimeout != null) {
            builder.connectTimeout(connectTimeout);
        }
        if (route != null && route.proxy()) {
            ProxySettings proxy = route.proxySettings();
            builder.proxy(ProxySelector.of(new InetSocketAddress(proxy.host(), proxy.port())));
            if (hasText(proxy.username())) {
                builder.authenticator(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(proxy.username(),
                            Objects.toString(proxy.password(), "").toCharArray());
                    }
                });
            }
        }
        return builder.build();
    }

    private RouteSettings extractChannelRoute(Channel channel) {
        if (channel == null) {
            return RouteSettings.inherited();
        }
        Map<String, Object> deliverySettings = parseMap(channel.getDeliverySettings());
        Object raw = deliverySettings.get("network_route");
        return RouteSettings.fromMap(toMap(raw), true);
    }

    private NetworkSettings loadSettings() {
        Map<String, Object> settings = sharedConfigService.loadSettings();
        Object raw = settings.get("integration_network");
        return NetworkSettings.fromMap(toMap(raw));
    }

    private Map<String, Object> parseMap(String raw) {
        if (!hasText(raw)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(raw, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, value) -> normalized.put(Objects.toString(key, ""), value));
            return normalized;
        }
        return Map.of();
    }

    private void appendJavaToolOption(Map<String, String> env, String option) {
        if (!hasText(option)) {
            return;
        }
        String current = Objects.toString(env.getOrDefault("JAVA_TOOL_OPTIONS", System.getenv("JAVA_TOOL_OPTIONS")), "").trim();
        env.put("JAVA_TOOL_OPTIONS", current.isEmpty() ? option : current + " " + option);
    }

    private String buildJavaProxyOptions(ProxySettings proxy) {
        if (proxy.socks()) {
            return String.join(" ",
                "-DsocksProxyHost=" + proxy.host(),
                "-DsocksProxyPort=" + proxy.port());
        }
        return String.join(" ",
            "-Dhttp.proxyHost=" + proxy.host(),
            "-Dhttp.proxyPort=" + proxy.port(),
            "-Dhttps.proxyHost=" + proxy.host(),
            "-Dhttps.proxyPort=" + proxy.port());
    }

    private String buildProxyUrl(ProxySettings proxy) {
        StringBuilder builder = new StringBuilder();
        builder.append(proxy.scheme()).append("://");
        if (hasText(proxy.username())) {
            builder.append(proxy.username());
            if (hasText(proxy.password())) {
                builder.append(':').append(proxy.password());
            }
            builder.append('@');
        }
        builder.append(proxy.host()).append(':').append(proxy.port());
        return builder.toString();
    }

    private void putIfHasText(Map<String, String> target, String key, String value) {
        if (hasText(value)) {
            target.put(key, value.trim());
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public record NetworkSettings(RouteSettings project, RouteSettings bots) {
        static NetworkSettings fromMap(Map<String, Object> raw) {
            Map<String, Object> source = raw != null ? raw : Map.of();
            return new NetworkSettings(
                RouteSettings.fromMap(asMap(source.get("project")), false),
                RouteSettings.fromMap(asMap(source.get("bots")), true)
            );
        }

        Map<String, Object> toMap(Object ignored) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("project", project.toMap(false));
            payload.put("bots", bots.toMap(true));
            return payload;
        }

        private static Map<String, Object> asMap(Object raw) {
            if (raw instanceof Map<?, ?> map) {
                Map<String, Object> normalized = new LinkedHashMap<>();
                map.forEach((key, value) -> normalized.put(Objects.toString(key, ""), value));
                return normalized;
            }
            return Map.of();
        }
    }

    public record RouteSettings(String mode, ProxySettings proxySettings, VpnSettings vpnSettings) {

        static RouteSettings inherited() {
            return fromMap(Map.of("mode", "inherit"), true);
        }

        static RouteSettings fromMap(Map<String, Object> raw, boolean allowInherit) {
            Map<String, Object> source = raw != null ? raw : Map.of();
            String defaultMode = allowInherit ? "inherit" : "direct";
            String mode = normalizeMode(source.get("mode"), defaultMode, allowInherit);
            return new RouteSettings(
                mode,
                ProxySettings.fromMap(asMap(source.get("proxy"))),
                VpnSettings.fromMap(asMap(source.get("vpn")))
            );
        }

        public boolean proxy() {
            return "proxy".equals(mode);
        }

        public boolean vpn() {
            return "vpn".equals(mode);
        }

        public boolean direct() {
            return "direct".equals(mode);
        }

        public boolean isInherited() {
            return "inherit".equals(mode);
        }

        public Map<String, Object> toMap(boolean allowInherit) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("mode", normalizeMode(mode, allowInherit ? "inherit" : "direct", allowInherit));
            payload.put("proxy", proxySettings.toMap());
            payload.put("vpn", vpnSettings.toMap());
            return payload;
        }

        private static String normalizeMode(Object raw, String fallback, boolean allowInherit) {
            String value = Objects.toString(raw, fallback).trim().toLowerCase(Locale.ROOT);
            if (allowInherit && "inherit".equals(value)) {
                return "inherit";
            }
            if ("proxy".equals(value) || "vpn".equals(value) || "direct".equals(value)) {
                return value;
            }
            return fallback;
        }

        private static Map<String, Object> asMap(Object raw) {
            if (raw instanceof Map<?, ?> map) {
                Map<String, Object> normalized = new LinkedHashMap<>();
                map.forEach((key, value) -> normalized.put(Objects.toString(key, ""), value));
                return normalized;
            }
            return Map.of();
        }
    }

    public record ProxySettings(String scheme, String host, int port, String username, String password) {
        static ProxySettings fromMap(Map<String, Object> raw) {
            Map<String, Object> source = raw != null ? raw : Map.of();
            return new ProxySettings(
                normalizeScheme(source.get("scheme")),
                text(source.get("host")),
                normalizePort(source.get("port")),
                text(source.get("username")),
                text(source.get("password"))
            );
        }

        public boolean socks() {
            return scheme.startsWith("socks");
        }

        public Map<String, Object> toMap() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("scheme", scheme);
            payload.put("host", host);
            payload.put("port", port);
            payload.put("username", username);
            payload.put("password", password);
            return payload;
        }

        private static String normalizeScheme(Object raw) {
            String value = text(raw).toLowerCase(Locale.ROOT);
            return switch (value) {
                case "https", "socks5", "socks4" -> value;
                default -> "http";
            };
        }

        private static int normalizePort(Object raw) {
            if (raw instanceof Number number) {
                return clampPort(number.intValue());
            }
            String value = text(raw);
            if (!value.isEmpty()) {
                try {
                    return clampPort(Integer.parseInt(value));
                } catch (NumberFormatException ignored) {
                    return 8080;
                }
            }
            return 8080;
        }

        private static int clampPort(int port) {
            return port >= 1 && port <= 65535 ? port : 8080;
        }
    }

    public record VpnSettings(String name, String endpoint, String configPath, String notes, Map<String, String> extraEnv) {
        static VpnSettings fromMap(Map<String, Object> raw) {
            Map<String, Object> source = raw != null ? raw : Map.of();
            return new VpnSettings(
                text(source.get("name")),
                text(source.get("endpoint")),
                text(source.get("config_path")),
                text(source.get("notes")),
                normalizeEnv(asMap(source.get("env")))
            );
        }

        public Map<String, Object> toMap() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("name", name);
            payload.put("endpoint", endpoint);
            payload.put("config_path", configPath);
            payload.put("notes", notes);
            payload.put("env", extraEnv);
            return payload;
        }

        private static Map<String, String> normalizeEnv(Map<String, Object> raw) {
            Map<String, String> result = new LinkedHashMap<>();
            raw.forEach((key, value) -> {
                String normalizedKey = text(key).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "_");
                String normalizedValue = text(value);
                if (!normalizedKey.isBlank() && !normalizedValue.isBlank()) {
                    result.put(normalizedKey, normalizedValue);
                }
            });
            return result;
        }

        private static Map<String, Object> asMap(Object raw) {
            if (raw instanceof Map<?, ?> map) {
                Map<String, Object> normalized = new LinkedHashMap<>();
                map.forEach((key, value) -> normalized.put(Objects.toString(key, ""), value));
                return normalized;
            }
            return Map.of();
        }
    }

    private static String text(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }
}
