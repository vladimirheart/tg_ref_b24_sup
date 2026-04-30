package com.example.panel.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class SettingsIntegrationNetworkProbeService {

    private final IntegrationNetworkService integrationNetworkService;

    public SettingsIntegrationNetworkProbeService(IntegrationNetworkService integrationNetworkService) {
        this.integrationNetworkService = integrationNetworkService;
    }

    public Map<String, Object> probeProfiles(Map<String, Object> payload) {
        List<Map<String, Object>> requestedProfiles = normalizeRequestedProfiles(payload);
        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> profile : requestedProfiles) {
            IntegrationNetworkService.RouteProbeResult probeResult = integrationNetworkService.probeProfileRoute(profile);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", String.valueOf(profile.getOrDefault("id", "")).trim());
            item.put("name", String.valueOf(profile.getOrDefault("name", "")).trim());
            item.put("mode", probeResult.mode());
            item.put("reachable", probeResult.reachable());
            item.put("message", probeResult.message());
            item.put("host", probeResult.host());
            item.put("port", probeResult.port());
            item.put("cooldown_seconds", probeResult.cooldownSeconds());
            item.put("unavailable_until_millis", probeResult.unavailableUntilMillis());
            results.add(item);
        }
        return Map.of("success", true, "items", results);
    }

    List<Map<String, Object>> normalizeRequestedProfiles(Map<String, Object> payload) {
        Map<String, Object> safePayload = payload != null ? payload : Map.of();
        List<Map<String, Object>> requestedProfiles = new ArrayList<>();
        Object rawProfiles = safePayload.get("profiles");
        if (rawProfiles instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    requestedProfiles.add(normalizeProfileMap(map));
                }
            }
        } else if (safePayload.get("profile") instanceof Map<?, ?> map) {
            requestedProfiles.add(normalizeProfileMap(map));
        }
        return requestedProfiles;
    }

    private Map<String, Object> normalizeProfileMap(Map<?, ?> raw) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        raw.forEach((key, value) -> normalized.put(Objects.toString(key, ""), value));
        return normalized;
    }
}
