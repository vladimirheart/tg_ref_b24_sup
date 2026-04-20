package com.example.panel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class DialogWorkspaceExternalProfileService {

    private static final Logger log = LoggerFactory.getLogger(DialogWorkspaceExternalProfileService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final int DEFAULT_TIMEOUT_MS = 2500;
    private static final int MIN_TIMEOUT_MS = 300;
    private static final int MAX_TIMEOUT_MS = 10000;
    private static final int DEFAULT_CACHE_TTL_SECONDS = 120;
    private static final int MIN_CACHE_TTL_SECONDS = 0;
    private static final int MAX_CACHE_TTL_SECONDS = 3600;
    private static final Pattern SAFE_HTTP_HEADER_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9-]{1,64}$");
    private static final Object CACHE_LOCK = new Object();
    private static volatile String cacheUrl = null;
    private static volatile Map<String, Object> cachePayload = Map.of();
    private static volatile Instant cacheExpiresAt = Instant.EPOCH;

    public Map<String, Object> resolveProfile(Map<?, ?> dialogConfig, String resolvedUrl) {
        if (dialogConfig == null || !StringUtils.hasText(resolvedUrl)) {
            return Map.of();
        }
        int cacheTtlSeconds = clampCacheTtl(dialogConfig.get("workspace_client_external_profile_cache_ttl_seconds"));
        Map<String, Object> cached = resolveCachedProfile(resolvedUrl, cacheTtlSeconds);
        if (!cached.isEmpty()) {
            return cached;
        }
        int timeoutMs = clampTimeout(dialogConfig.get("workspace_client_external_profile_timeout_ms"));
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(resolvedUrl))
                    .GET()
                    .timeout(Duration.ofMillis(timeoutMs));
            applyAuthHeader(requestBuilder, dialogConfig);
            HttpResponse<String> response = HTTP_CLIENT.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("workspace external profile fetch failed: status={} url={}", response.statusCode(), resolvedUrl);
                return resolveCachedProfileOnFailure(resolvedUrl);
            }
            Object parsed = OBJECT_MAPPER.readValue(response.body(), new TypeReference<Object>() {});
            Map<String, Object> normalized = normalizePayload(parsed);
            if (!normalized.isEmpty()) {
                storeProfileCache(resolvedUrl, normalized, cacheTtlSeconds);
                return normalized;
            }
            if (parsed instanceof Map<?, ?> || parsed instanceof java.util.List<?>) {
                return resolveCachedProfileOnFailure(resolvedUrl);
            }
            log.warn("workspace external profile fetch returned unsupported payload type: url={} type={}",
                    resolvedUrl,
                    parsed != null ? parsed.getClass().getSimpleName() : "null");
        } catch (Exception exception) {
            log.warn("workspace external profile fetch failed: url={} detail={}", resolvedUrl, exception.toString());
            return resolveCachedProfileOnFailure(resolvedUrl);
        }
        return Map.of();
    }

    private void applyAuthHeader(HttpRequest.Builder requestBuilder, Map<?, ?> dialogConfig) {
        if (requestBuilder == null || dialogConfig == null) {
            return;
        }
        String token = trimToNull(String.valueOf(dialogConfig.get("workspace_client_external_profile_auth_token")));
        if (!StringUtils.hasText(token)) {
            return;
        }
        String configuredHeader = trimToNull(String.valueOf(dialogConfig.get("workspace_client_external_profile_auth_header")));
        String headerName = StringUtils.hasText(configuredHeader) ? configuredHeader : "Authorization";
        if (!SAFE_HTTP_HEADER_NAME_PATTERN.matcher(headerName).matches()) {
            log.warn("workspace external profile auth header ignored due to unsafe header name: {}", headerName);
            return;
        }
        requestBuilder.header(headerName, token);
    }

    private Map<String, Object> normalizePayload(Object payload) {
        Object candidate = payload;
        if (candidate instanceof Map<?, ?> map && map.get("profile") != null) {
            candidate = map.get("profile");
        }
        if (!(candidate instanceof Map<?, ?> profileMap)) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        profileMap.forEach((keyRaw, valueRaw) -> {
            String key = normalizeKey(keyRaw != null ? String.valueOf(keyRaw) : null);
            if (!StringUtils.hasText(key) || valueRaw == null) {
                return;
            }
            normalized.put(key, valueRaw);
        });
        return normalized;
    }

    private Map<String, Object> resolveCachedProfile(String resolvedUrl, int cacheTtlSeconds) {
        if (cacheTtlSeconds <= 0 || !resolvedUrl.equals(cacheUrl)) {
            return Map.of();
        }
        if (cacheExpiresAt != null && Instant.now().isBefore(cacheExpiresAt)) {
            return cachePayload;
        }
        return Map.of();
    }

    private Map<String, Object> resolveCachedProfileOnFailure(String resolvedUrl) {
        if (resolvedUrl.equals(cacheUrl) && !cachePayload.isEmpty()) {
            log.info("workspace external profile fetch fallback to stale cache: url={} keys={}",
                    resolvedUrl,
                    cachePayload.size());
            return cachePayload;
        }
        return Map.of();
    }

    private void storeProfileCache(String resolvedUrl, Map<String, Object> profile, int cacheTtlSeconds) {
        if (!StringUtils.hasText(resolvedUrl) || profile == null || profile.isEmpty() || cacheTtlSeconds <= 0) {
            return;
        }
        synchronized (CACHE_LOCK) {
            cacheUrl = resolvedUrl;
            cachePayload = Map.copyOf(profile);
            cacheExpiresAt = Instant.now().plusSeconds(cacheTtlSeconds);
        }
    }

    private int clampTimeout(Object value) {
        if (value == null) {
            return DEFAULT_TIMEOUT_MS;
        }
        try {
            int parsed = Integer.parseInt(String.valueOf(value).trim());
            if (parsed < MIN_TIMEOUT_MS) {
                return MIN_TIMEOUT_MS;
            }
            return Math.min(parsed, MAX_TIMEOUT_MS);
        } catch (NumberFormatException ignored) {
            return DEFAULT_TIMEOUT_MS;
        }
    }

    private int clampCacheTtl(Object value) {
        if (value == null) {
            return DEFAULT_CACHE_TTL_SECONDS;
        }
        try {
            int parsed = Integer.parseInt(String.valueOf(value).trim());
            if (parsed < MIN_CACHE_TTL_SECONDS) {
                return MIN_CACHE_TTL_SECONDS;
            }
            return Math.min(parsed, MAX_CACHE_TTL_SECONDS);
        } catch (NumberFormatException ignored) {
            return DEFAULT_CACHE_TTL_SECONDS;
        }
    }

    private String normalizeKey(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        String normalized = rawValue.trim()
                .toLowerCase()
                .replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("_+", "_");
        if (normalized.startsWith("_")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("_")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return StringUtils.hasText(trimmed) ? trimmed : null;
    }
}
