package com.example.panel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.panel.service.NetBoxApiService.DownloadedFile;
import com.example.panel.service.NetBoxSyncSettingsService.NetBoxSyncSettings;
import com.example.panel.storage.ObjectPassportPhotoStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.JdbcTemplate;

class NetBoxObjectPassportSyncServiceTest {

    @Test
    void syncNowKeepsSiteWhenPhotoRefreshFails() {
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        NetBoxSyncSettingsService settingsService = mock(NetBoxSyncSettingsService.class);
        NetBoxApiService netBoxApiService = mock(NetBoxApiService.class);
        ObjectPassportService objectPassportService = mock(ObjectPassportService.class);
        ObjectPassportPhotoStorageService photoStorageService = mock(ObjectPassportPhotoStorageService.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SettingsCatalogService settingsCatalogService = mock(SettingsCatalogService.class);

        NetBoxObjectPassportSyncService service = new NetBoxObjectPassportSyncService(
                sharedConfigService,
                settingsService,
                netBoxApiService,
                objectPassportService,
                photoStorageService,
                jdbcTemplate,
                new ObjectMapper(),
                settingsCatalogService
        );

        NetBoxSyncSettings settings = new NetBoxSyncSettings(
                "https://netbox.example.com",
                "secret",
                false,
                60,
                false,
                List.of()
        );
        Map<String, Object> sharedSettings = new LinkedHashMap<>();
        sharedSettings.put("netbox_sync", settings.toMap());
        Map<String, Object> site = new LinkedHashMap<>();
        site.put("id", "160");
        site.put("name", "Main site");
        site.put("status", Map.of("label", "Активен"));
        Map<String, Object> attachment = new LinkedHashMap<>();
        attachment.put("id", "501");
        attachment.put("image", "/media/site-501.jpg");
        attachment.put("name", "site-501.jpg");

        when(sharedConfigService.loadSettings()).thenReturn(sharedSettings);
        when(settingsService.load(anyMap())).thenReturn(settings);
        when(netBoxApiService.fetchSites(settings)).thenReturn(List.of(site));
        when(netBoxApiService.fetchDevices(settings, "160")).thenReturn(List.of());
        when(netBoxApiService.fetchCircuits(settings, "160")).thenReturn(List.of());
        when(netBoxApiService.fetchSiteImages(settings, "160")).thenReturn(List.of(attachment));
        when(netBoxApiService.downloadFile(settings, "/media/site-501.jpg", "site-501.jpg"))
                .thenThrow(new IllegalStateException("NetBox вернул HTTP 500 для https://netbox.example.com/media/site-501.jpg"));
        when(objectPassportService.findPassportByNetBoxSiteId("160")).thenReturn(null);
        when(objectPassportService.upsertPassportByNetBoxSiteId(eq("160"), anyMap())).thenReturn(Map.of());
        when(settingsCatalogService.getDefaultItConnectionCategories()).thenReturn(Map.of());

        NetBoxObjectPassportSyncService.SyncStatusSnapshot result = service.syncNow("manual");

        assertEquals("success", result.state());
        assertEquals(1, result.result().totalSites());
        assertEquals(1, result.result().createdPassports());
        assertEquals(0, result.result().importedPhotos());
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("imported without photo refresh")));

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(objectPassportService).upsertPassportByNetBoxSiteId(eq("160"), payloadCaptor.capture());
        Object rawPhotos = payloadCaptor.getValue().get("photos");
        assertTrue(rawPhotos instanceof List<?>);
        assertTrue(((List<?>) rawPhotos).isEmpty());
    }

    @Test
    void syncNowReusesExistingItConnectionValueWithoutCategory() {
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        NetBoxSyncSettingsService settingsService = mock(NetBoxSyncSettingsService.class);
        NetBoxApiService netBoxApiService = mock(NetBoxApiService.class);
        ObjectPassportService objectPassportService = mock(ObjectPassportService.class);
        ObjectPassportPhotoStorageService photoStorageService = mock(ObjectPassportPhotoStorageService.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SettingsCatalogService settingsCatalogService = mock(SettingsCatalogService.class);

        NetBoxObjectPassportSyncService service = new NetBoxObjectPassportSyncService(
                sharedConfigService,
                settingsService,
                netBoxApiService,
                objectPassportService,
                photoStorageService,
                jdbcTemplate,
                new ObjectMapper(),
                settingsCatalogService
        );

        NetBoxSyncSettings settings = new NetBoxSyncSettings(
                "https://netbox.example.com",
                "secret",
                false,
                60,
                false,
                List.of()
        );
        Map<String, Object> sharedSettings = new LinkedHashMap<>();
        sharedSettings.put("netbox_sync", settings.toMap());
        Map<String, Object> site = new LinkedHashMap<>();
        site.put("id", "160");
        site.put("name", "Main site");
        site.put("status", Map.of("label", "Активен"));
        Map<String, Object> device = new LinkedHashMap<>();
        device.put("name", "router-1");
        device.put("device_role", Map.of("name", "Маршрутизатор"));
        device.put("device_type", Map.of(
                "manufacturer", Map.of("name", "Cisco"),
                "model", "ISR1000"
        ));
        device.put("status", Map.of("label", "Активен"));

        when(sharedConfigService.loadSettings()).thenReturn(sharedSettings);
        when(settingsService.load(anyMap())).thenReturn(settings);
        when(netBoxApiService.fetchSites(settings)).thenReturn(List.of(site));
        when(netBoxApiService.fetchDevices(settings, "160")).thenReturn(List.of(device));
        when(netBoxApiService.fetchCircuits(settings, "160")).thenReturn(List.of());
        when(netBoxApiService.fetchSiteImages(settings, "160")).thenReturn(List.of());
        when(objectPassportService.findPassportByNetBoxSiteId("160")).thenReturn(null);
        when(objectPassportService.upsertPassportByNetBoxSiteId(eq("160"), anyMap())).thenReturn(Map.of());
        when(settingsCatalogService.getDefaultItConnectionCategories()).thenReturn(Map.of(
                "equipment_type", "Тип оборудования",
                "equipment_vendor", "Производитель оборудования",
                "equipment_model", "Модель оборудования",
                "equipment_status", "Статус оборудования"
        ));
        when(jdbcTemplate.queryForList(
                "SELECT id, value, state, is_deleted, extra_json FROM settings_parameters WHERE param_type = ?",
                "it_connection"
        )).thenReturn(List.of(Map.of(
                "id", 77L,
                "value", "Cisco",
                "state", "Активен",
                "is_deleted", 0,
                "extra_json", "{}"
        )));

        NetBoxObjectPassportSyncService.SyncStatusSnapshot result = service.syncNow("manual");

        assertEquals("success", result.state());
        verify(jdbcTemplate).update(
                "UPDATE settings_parameters SET value = ?, state = ?, is_deleted = 0, deleted_at = NULL, extra_json = ? WHERE id = ?",
                "Cisco",
                "Активен",
                "{\"category\":\"equipment_vendor\",\"category_label\":\"Производитель оборудования\",\"equipment_type\":\"\",\"equipment_vendor\":\"Cisco\",\"equipment_model\":\"\",\"equipment_status\":\"\"}",
                77L
        );
    }

    @Test
    void syncNowImportsEquipmentCatalogEntriesForSettingsPage() {
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        NetBoxSyncSettingsService settingsService = mock(NetBoxSyncSettingsService.class);
        NetBoxApiService netBoxApiService = mock(NetBoxApiService.class);
        ObjectPassportService objectPassportService = mock(ObjectPassportService.class);
        ObjectPassportPhotoStorageService photoStorageService = mock(ObjectPassportPhotoStorageService.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SettingsCatalogService settingsCatalogService = mock(SettingsCatalogService.class);

        NetBoxObjectPassportSyncService service = new NetBoxObjectPassportSyncService(
                sharedConfigService,
                settingsService,
                netBoxApiService,
                objectPassportService,
                photoStorageService,
                jdbcTemplate,
                new ObjectMapper(),
                settingsCatalogService
        );

        NetBoxSyncSettings settings = new NetBoxSyncSettings(
                "https://netbox.example.com",
                "secret",
                false,
                60,
                false,
                List.of()
        );
        Map<String, Object> sharedSettings = new LinkedHashMap<>();
        sharedSettings.put("netbox_sync", settings.toMap());
        Map<String, Object> site = new LinkedHashMap<>();
        site.put("id", "160");
        site.put("name", "Main site");
        site.put("status", Map.of("label", "Активен"));
        Map<String, Object> device = new LinkedHashMap<>();
        device.put("name", "router-1");
        device.put("device_role", Map.of("name", "Маршрутизатор"));
        device.put("device_type", Map.of(
                "manufacturer", Map.of("name", "Cisco"),
                "model", "ISR1000"
        ));
        device.put("status", Map.of("label", "Активен"));

        when(sharedConfigService.loadSettings()).thenReturn(sharedSettings);
        when(settingsService.load(anyMap())).thenReturn(settings);
        when(netBoxApiService.fetchSites(settings)).thenReturn(List.of(site));
        when(netBoxApiService.fetchDevices(settings, "160")).thenReturn(List.of(device));
        when(netBoxApiService.fetchCircuits(settings, "160")).thenReturn(List.of());
        when(netBoxApiService.fetchSiteImages(settings, "160")).thenReturn(List.of());
        when(objectPassportService.findPassportByNetBoxSiteId("160")).thenReturn(null);
        when(objectPassportService.upsertPassportByNetBoxSiteId(eq("160"), anyMap())).thenReturn(Map.of());
        when(settingsCatalogService.getDefaultItConnectionCategories()).thenReturn(Map.of(
                "equipment_type", "Тип оборудования",
                "equipment_vendor", "Производитель оборудования",
                "equipment_model", "Модель оборудования",
                "equipment_status", "Статус оборудования"
        ));
        when(jdbcTemplate.queryForList(
                "SELECT id, value, state, is_deleted, extra_json FROM settings_parameters WHERE param_type = ?",
                "it_connection"
        )).thenReturn(List.of());
        when(jdbcTemplate.queryForList(
                "SELECT equipment_type, equipment_vendor, equipment_model FROM it_equipment_catalog"
        )).thenReturn(List.of());

        NetBoxObjectPassportSyncService.SyncStatusSnapshot result = service.syncNow("manual");

        assertEquals("success", result.state());
        verify(jdbcTemplate).update(
                "INSERT INTO it_equipment_catalog(" +
                        "equipment_type, equipment_vendor, equipment_model, photo_url, serial_number, accessories, created_at, updated_at" +
                        ") VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                "Маршрутизатор",
                "Cisco",
                "ISR1000",
                "",
                "",
                ""
        );
    }
}
