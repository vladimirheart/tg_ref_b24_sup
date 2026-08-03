package com.example.panel.controller;

import com.example.panel.service.NetBoxObjectPassportSyncService;
import com.example.panel.service.NetBoxSyncSettingsService;
import com.example.panel.service.SettingsTopLevelUpdateService;
import com.example.panel.service.SharedConfigService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings/netbox-sync")
@PreAuthorize("hasAuthority('PAGE_SETTINGS')")
public class SettingsNetBoxSyncController {

    private static final Logger log = LoggerFactory.getLogger(SettingsNetBoxSyncController.class);

    private final NetBoxObjectPassportSyncService syncService;
    private final NetBoxSyncSettingsService netBoxSyncSettingsService;
    private final SharedConfigService sharedConfigService;
    private final SettingsTopLevelUpdateService settingsTopLevelUpdateService;

    public SettingsNetBoxSyncController(NetBoxObjectPassportSyncService syncService,
                                        NetBoxSyncSettingsService netBoxSyncSettingsService,
                                        SharedConfigService sharedConfigService,
                                        SettingsTopLevelUpdateService settingsTopLevelUpdateService) {
        this.syncService = syncService;
        this.netBoxSyncSettingsService = netBoxSyncSettingsService;
        this.sharedConfigService = sharedConfigService;
        this.settingsTopLevelUpdateService = settingsTopLevelUpdateService;
    }

    @GetMapping("/status")
    public NetBoxObjectPassportSyncService.SyncStatusSnapshot status() {
        return syncService.getStatus();
    }

    @PostMapping("/save")
    public Map<String, Object> save(@RequestBody(required = false) Map<String, Object> payload) {
        log.info("NetBox settings save requested: {}", summarizePayload(payload));
        persistNetBoxSyncSettings(payload);
        Map<String, Object> savedSettings = loadSavedSettingsForClient();
        log.info("NetBox settings saved: {}, file={}", summarizeSavedSettings(savedSettings), sharedConfigService.resolvePath("settings.json"));
        return Map.of(
                "success", true,
                "savedSettings", savedSettings
        );
    }

    @PostMapping("/run")
    public Map<String, Object> run(@RequestBody(required = false) Map<String, Object> payload) {
        log.info("NetBox sync run requested: {}", summarizePayload(payload));
        persistNetBoxSyncSettings(payload);
        Map<String, Object> savedSettings = loadSavedSettingsForClient();
        log.info("NetBox sync run will use saved settings: {}", summarizeSavedSettings(savedSettings));
        NetBoxObjectPassportSyncService.SyncTriggerResponse result = syncService.triggerManualSync();
        return Map.of(
                "success", true,
                "started", result.started(),
                "status", result.status(),
                "savedSettings", savedSettings
        );
    }

    @PostMapping("/sites")
    public Map<String, Object> sites(@RequestBody(required = false) Map<String, Object> payload) {
        NetBoxSyncSettingsService.NetBoxSyncSettings effectiveSettings = resolveEffectiveSettings(payload);
        return Map.of(
                "success", true,
                "sites", syncService.loadAvailableSites(effectiveSettings),
                "selectedSiteIds", effectiveSettings.selectedSiteIds()
        );
    }

    private void persistNetBoxSyncSettings(Map<String, Object> payload) {
        if (payload == null || !payload.containsKey("netbox_sync")) {
            return;
        }
        Map<String, Object> settings = new LinkedHashMap<>(sharedConfigService.loadSettings());
        boolean modified = settingsTopLevelUpdateService.applyTopLevelUpdates(payload, settings);
        if (modified) {
            sharedConfigService.saveSettings(settings);
        }
    }

    private Map<String, Object> loadSavedSettingsForClient() {
        return netBoxSyncSettingsService.loadForClient(sharedConfigService.loadSettings());
    }

    private NetBoxSyncSettingsService.NetBoxSyncSettings resolveEffectiveSettings(Map<String, Object> payload) {
        Map<String, Object> settings = new LinkedHashMap<>(sharedConfigService.loadSettings());
        if (payload != null && payload.containsKey("netbox_sync")) {
            settingsTopLevelUpdateService.applyTopLevelUpdates(payload, settings);
        }
        return netBoxSyncSettingsService.load(settings);
    }

    @SuppressWarnings("unchecked")
    private String summarizePayload(Map<String, Object> payload) {
        if (payload == null) {
            return "payload=null";
        }
        Object raw = payload.get("netbox_sync");
        if (!(raw instanceof Map<?, ?> map)) {
            return "netbox_sync=missing";
        }
        Object baseUrl = map.get("base_url");
        Object apiToken = map.get("api_token");
        Object clearToken = map.get("clear_api_token");
        Object enabled = map.get("enabled");
        Object interval = map.get("interval_minutes");
        Object selectedSiteIds = map.get("selected_site_ids");
        int tokenLength = apiToken == null ? 0 : String.valueOf(apiToken).trim().length();
        int selectedCount = selectedSiteIds instanceof java.util.List<?> list ? list.size() : 0;
        return "base_url=" + String.valueOf(baseUrl)
                + ", token_length=" + tokenLength
                + ", clear_api_token=" + String.valueOf(clearToken)
                + ", enabled=" + String.valueOf(enabled)
                + ", interval_minutes=" + String.valueOf(interval)
                + ", selected_site_ids=" + selectedCount;
    }

    private String summarizeSavedSettings(Map<String, Object> settings) {
        if (settings == null || settings.isEmpty()) {
            return "savedSettings=empty";
        }
        Object baseUrl = settings.get("base_url");
        Object tokenSaved = settings.get("api_token_saved");
        Object enabled = settings.get("enabled");
        Object interval = settings.get("interval_minutes");
        Object overwritePending = settings.get("full_overwrite_pending");
        Object selectedSiteIds = settings.get("selected_site_ids");
        int selectedCount = selectedSiteIds instanceof java.util.List<?> list ? list.size() : 0;
        return "base_url=" + String.valueOf(baseUrl)
                + ", api_token_saved=" + String.valueOf(tokenSaved)
                + ", enabled=" + String.valueOf(enabled)
                + ", interval_minutes=" + String.valueOf(interval)
                + ", full_overwrite_pending=" + String.valueOf(overwritePending)
                + ", selected_site_ids=" + selectedCount;
    }
}
