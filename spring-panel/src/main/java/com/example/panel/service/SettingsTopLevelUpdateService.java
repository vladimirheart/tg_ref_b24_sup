package com.example.panel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SettingsTopLevelUpdateService {

    private static final Logger logger = LoggerFactory.getLogger(SettingsTopLevelUpdateService.class);

    private final AutoCloseConfigNormalizer autoCloseConfigNormalizer;
    private final BotSettingsPayloadNormalizer botSettingsPayloadNormalizer;
    private final LocationsIikoServerSourceSettingsService locationsIikoServerSourceSettingsService;
    private final LocationsIikoSyncSettingsService locationsIikoSyncSettingsService;
    private final NotificationRoutingService notificationRoutingService;

    public SettingsTopLevelUpdateService(AutoCloseConfigNormalizer autoCloseConfigNormalizer,
                                         BotSettingsPayloadNormalizer botSettingsPayloadNormalizer,
                                         LocationsIikoServerSourceSettingsService locationsIikoServerSourceSettingsService,
                                         LocationsIikoSyncSettingsService locationsIikoSyncSettingsService,
                                         NotificationRoutingService notificationRoutingService) {
        this.autoCloseConfigNormalizer = autoCloseConfigNormalizer;
        this.botSettingsPayloadNormalizer = botSettingsPayloadNormalizer;
        this.locationsIikoServerSourceSettingsService = locationsIikoServerSourceSettingsService;
        this.locationsIikoSyncSettingsService = locationsIikoSyncSettingsService;
        this.notificationRoutingService = notificationRoutingService;
    }

    public boolean applyTopLevelUpdates(Map<String, Object> payload,
                                        Map<String, Object> settings) {
        boolean modified = false;

        if (payload.containsKey("auto_close_config")) {
            settings.put("auto_close_config", autoCloseConfigNormalizer.normalize(payload.get("auto_close_config")));
            if (payload.containsKey("auto_close_hours")) {
                logger.info("Ignoring deprecated auto_close_hours payload because auto_close_config is canonical source of truth");
            }
            settings.remove("auto_close_hours");
            modified = true;
        } else if (payload.containsKey("auto_close_hours")) {
            logger.warn("Ignoring deprecated auto_close_hours payload because top-level auto_close_hours is no longer supported");
            settings.remove("auto_close_hours");
            modified = true;
        }

        if (payload.containsKey("categories")) {
            settings.put("categories", normalizeStringList(payload.get("categories"), false));
            modified = true;
        }

        if (payload.containsKey("client_statuses")) {
            settings.put("client_statuses", normalizeStringList(payload.get("client_statuses"), true));
            modified = true;
        }

        if (payload.containsKey("client_status_colors")) {
            settings.put("client_status_colors", normalizeStringMap(payload.get("client_status_colors")));
            modified = true;
        }

        modified |= copyIfPresent(payload, settings, "business_cell_styles");
        modified |= copyIfPresent(payload, settings, "network_profiles");
        modified |= copyBotSettingsIfPresent(payload, settings);
        modified |= migrateLegacyUnblockCooldownIfPresent(payload, settings);
        modified |= copyIfPresent(payload, settings, "integration_network");
        modified |= copyIfPresent(payload, settings, "integration_network_profiles");
        modified |= copyIfPresent(payload, settings, "reporting_config");
        modified |= copyIfPresent(payload, settings, "manager_location_bindings");
        modified |= notificationRoutingService.applyPayload(payload, settings);
        modified |= locationsIikoServerSourceSettingsService.applyPayload(payload, settings);
        modified |= locationsIikoSyncSettingsService.applyPayload(payload, settings);

        return modified;
    }

    private boolean copyBotSettingsIfPresent(Map<String, Object> payload,
                                             Map<String, Object> settings) {
        if (!payload.containsKey("bot_settings")) {
            return false;
        }
        settings.put("bot_settings", botSettingsPayloadNormalizer.normalize(payload.get("bot_settings")));
        if (payload.containsKey("unblock_request_cooldown_minutes")) {
            logger.warn("Ignoring deprecated root unblock_request_cooldown_minutes payload because canonical value must be sent inside bot_settings");
        }
        settings.remove("unblock_request_cooldown_minutes");
        return true;
    }

    private boolean migrateLegacyUnblockCooldownIfPresent(Map<String, Object> payload,
                                                          Map<String, Object> settings) {
        if (!payload.containsKey("unblock_request_cooldown_minutes") || payload.containsKey("bot_settings")) {
            return false;
        }
        logger.warn("Ignoring deprecated root unblock_request_cooldown_minutes payload because canonical value must be sent inside bot_settings");
        settings.remove("unblock_request_cooldown_minutes");
        return true;
    }

    private boolean copyIfPresent(Map<String, Object> payload,
                                  Map<String, Object> settings,
                                  String key) {
        if (!payload.containsKey(key)) {
            return false;
        }
        settings.put(key, payload.get(key));
        return true;
    }

    private List<String> normalizeStringList(Object raw, boolean distinct) {
        List<String> values = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return values;
        }
        for (Object item : list) {
            String value = item != null ? item.toString().trim() : "";
            if (!StringUtils.hasText(value)) {
                continue;
            }
            if (!distinct || !values.contains(value)) {
                values.add(value);
            }
        }
        return values;
    }

    private Map<String, String> normalizeStringMap(Object raw) {
        Map<String, String> values = new LinkedHashMap<>();
        if (!(raw instanceof Map<?, ?> map)) {
            return values;
        }
        map.forEach((key, value) -> {
            String normalizedKey = key != null ? key.toString().trim() : "";
            String normalizedValue = value != null ? value.toString().trim() : "";
            if (StringUtils.hasText(normalizedKey) && StringUtils.hasText(normalizedValue)) {
                values.put(normalizedKey, normalizedValue);
            }
        });
        return values;
    }
}
