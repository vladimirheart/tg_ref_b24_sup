package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.entity.CredentialRotationRegistryEntry;
import com.example.panel.entity.IikoApiMonitor;
import com.example.panel.model.channel.BotCredential;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.repository.CredentialRotationRegistryRepository;
import com.example.panel.repository.IikoApiMonitorRepository;
import com.example.panel.repository.MonitoringCheckHistoryRepository;
import com.example.panel.security.PanelSecurityProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class CredentialRotationRegistryServiceTest {

    @Test
    void buildSnapshotDiscoversSecretsAndMarksMetadataGapByDefault() {
        CredentialRotationRegistryRepository repository = mock(CredentialRotationRegistryRepository.class);
        MonitoringCheckHistoryRepository historyRepository = mock(MonitoringCheckHistoryRepository.class);
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        ChannelRepository channelRepository = mock(ChannelRepository.class);
        IikoApiMonitorRepository iikoApiMonitorRepository = mock(IikoApiMonitorRepository.class);
        LocationsIikoServerSourceSettingsService locationsService = mock(LocationsIikoServerSourceSettingsService.class);
        NetBoxSyncSettingsService netBoxService = mock(NetBoxSyncSettingsService.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PanelSecurityProperties securityProperties = new PanelSecurityProperties();
        Environment environment = mock(Environment.class);
        IncidentService incidentService = mock(IncidentService.class);
        CredentialRotationExternalMetadataImportService externalMetadataImportService =
            mock(CredentialRotationExternalMetadataImportService.class);

        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("knowledge_base_config", Map.of("enabled", true, "token", "notion-token", "source_url", "https://notion.so/db"));
        settings.put("dialog_config", Map.of(
            "workspace_client_external_profile_url", "https://example.test/api/profile",
            "workspace_client_external_profile_auth_token", "workspace-token"
        ));
        when(sharedConfigService.loadSettings()).thenReturn(settings);
        when(externalMetadataImportService.loadImportedMetadata(settings)).thenReturn(Map.of());
        when(sharedConfigService.loadBotCredentials()).thenReturn(List.of(
            new BotCredential(10L, "Telegram Main", "telegram", "telegram-token", true)
        ));

        Channel vkChannel = new Channel();
        vkChannel.setId(77L);
        vkChannel.setPlatform("vk");
        vkChannel.setChannelName("VK Retail");
        vkChannel.setCredentialId(null);
        vkChannel.setToken("vk-channel-token");
        vkChannel.setPlatformConfig("""
            {"group_id":12345,"confirmation_token":"confirm-token","secret":"webhook-secret"}
            """);
        when(channelRepository.findAll()).thenReturn(List.of(vkChannel));

        when(netBoxService.load(settings)).thenReturn(new NetBoxSyncSettingsService.NetBoxSyncSettings(
            "https://netbox.example.test",
            "netbox-token",
            true,
            60,
            false,
            List.of("site-1")
        ));
        when(locationsService.loadForRuntime(settings)).thenReturn(List.of(
            new LocationsIikoServerSourceSettingsService.LocationIikoServerSource(
                "src-1",
                "HQ source",
                "https://iiko.example.test",
                "api-login",
                "api-secret",
                true
            )
        ));

        IikoApiMonitor iikoApiMonitor = new IikoApiMonitor();
        iikoApiMonitor.setId(5L);
        iikoApiMonitor.setMonitorName("iiko RU");
        iikoApiMonitor.setApiLogin("monitor-token");
        when(iikoApiMonitorRepository.findAllByOrderByMonitorNameAscIdAsc()).thenReturn(List.of(iikoApiMonitor));

        when(jdbcTemplate.queryForList(any(String.class), eq("employee_discount_automation_credentials.v1"))).thenReturn(List.of(
            Map.of(
                "value", "ops.lead",
                "extra_json", """
                    {
                      "bitrix24": {"portal_url":"https://portal.example.test","webhook_url":"https://portal.example.test/rest/1/secret"},
                      "iiko_profiles": {
                        "https://iiko.example.test": {
                          "base_url":"https://iiko.example.test",
                          "api_secret":"iiko-profile-secret"
                        }
                      }
                    }
                    """
            )
        ));

        when(environment.getProperty("app.bots.internal-api.token", "iguana-internal-bot-token"))
            .thenReturn("internal-token");
        when(environment.getProperty("app.bots.internal-api.signature-secret", "")).thenReturn("");
        securityProperties.setRememberMeKey("remember-me-secret");

        when(repository.findAllByOrderByDisplayNameAscIdAsc()).thenReturn(List.of());
        AtomicLong sequence = new AtomicLong(1L);
        doAnswer(invocation -> {
            CredentialRotationRegistryEntry item = invocation.getArgument(0);
            if (item.getId() == null) {
                item.setId(sequence.getAndIncrement());
            }
            return item;
        }).when(repository).save(any(CredentialRotationRegistryEntry.class));

        CredentialRotationRegistryService service = new CredentialRotationRegistryService(
            repository,
            historyRepository,
            sharedConfigService,
            channelRepository,
            iikoApiMonitorRepository,
            locationsService,
            netBoxService,
            jdbcTemplate,
            securityProperties,
            new ObjectMapper(),
            externalMetadataImportService,
            incidentService,
            environment,
            Clock.fixed(Instant.parse("2026-08-25T12:00:00Z"), ZoneOffset.UTC)
        );

        CredentialRotationRegistryService.RegistrySnapshot snapshot = service.buildSnapshot();

        assertThat(snapshot.overview().total()).isGreaterThanOrEqualTo(10);
        assertThat(snapshot.items())
            .extracting(CredentialRotationRegistryEntry::getEntryKey)
            .contains(
                "settings.netbox.api_token",
                "settings.notion.token",
                "settings.dialog.workspace_external_profile_auth_token",
                "channel.77.vk.confirmation_token",
                "channel.77.vk.webhook_secret",
                "monitoring.iiko_api_monitors.5",
                "employee-discount.ops.lead.bitrix24.webhook_url",
                "employee-discount.ops.lead.iiko.https-iiko.example.test.api_secret"
            );

        CredentialRotationRegistryEntry netBoxEntry = snapshot.items().stream()
            .filter(item -> "settings.netbox.api_token".equals(item.getEntryKey()))
            .findFirst()
            .orElseThrow();
        assertThat(netBoxEntry.getLastStatus()).isEqualTo(CredentialRotationRegistryService.STATUS_TRACKING_MISSING);
        assertThat(netBoxEntry.getStatusLevel()).isEqualTo(CredentialRotationRegistryService.LEVEL_WARNING);
        assertThat(netBoxEntry.getSecretPresent()).isTrue();
        verify(incidentService, never()).openOrRefreshSignalIncident(any(), any(), any(), any(), any(), any(), any(), anyMap(), any());
    }

    @Test
    void buildSnapshotMarksUndiscoveredEntryAsRemovedSource() {
        CredentialRotationRegistryRepository repository = mock(CredentialRotationRegistryRepository.class);
        MonitoringCheckHistoryRepository historyRepository = mock(MonitoringCheckHistoryRepository.class);
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        ChannelRepository channelRepository = mock(ChannelRepository.class);
        IikoApiMonitorRepository iikoApiMonitorRepository = mock(IikoApiMonitorRepository.class);
        LocationsIikoServerSourceSettingsService locationsService = mock(LocationsIikoServerSourceSettingsService.class);
        NetBoxSyncSettingsService netBoxService = mock(NetBoxSyncSettingsService.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PanelSecurityProperties securityProperties = new PanelSecurityProperties();
        Environment environment = mock(Environment.class);
        IncidentService incidentService = mock(IncidentService.class);
        CredentialRotationExternalMetadataImportService externalMetadataImportService =
            mock(CredentialRotationExternalMetadataImportService.class);

        when(sharedConfigService.loadSettings()).thenReturn(Map.of());
        when(externalMetadataImportService.loadImportedMetadata(Map.of())).thenReturn(Map.of());
        when(sharedConfigService.loadBotCredentials()).thenReturn(List.of());
        when(channelRepository.findAll()).thenReturn(List.of());
        when(iikoApiMonitorRepository.findAllByOrderByMonitorNameAscIdAsc()).thenReturn(List.of());
        when(locationsService.loadForRuntime(Map.of())).thenReturn(List.of());
        when(jdbcTemplate.queryForList(any(String.class), eq("employee_discount_automation_credentials.v1"))).thenReturn(List.of());
        when(environment.getProperty("app.bots.internal-api.token", "iguana-internal-bot-token")).thenReturn("");
        when(environment.getProperty("app.bots.internal-api.signature-secret", "")).thenReturn("");
        securityProperties.setRememberMeKey("");

        CredentialRotationRegistryEntry stale = new CredentialRotationRegistryEntry();
        stale.setId(42L);
        stale.setEntryKey("obsolete.secret");
        stale.setDisplayName("Obsolete secret");
        stale.setIntegrationKind("legacy");
        stale.setCredentialKind("shared_secret");
        stale.setSourceType("settings_json");
        stale.setSourceRef("settings.json#obsolete");
        stale.setSourcePresent(true);
        stale.setSecretPresent(true);
        stale.setCreatedAt(OffsetDateTime.parse("2026-08-01T00:00:00Z"));
        stale.setUpdatedAt(OffsetDateTime.parse("2026-08-01T00:00:00Z"));
        when(repository.findAllByOrderByDisplayNameAscIdAsc()).thenReturn(List.of(stale));
        doAnswer(invocation -> invocation.getArgument(0)).when(repository).save(any(CredentialRotationRegistryEntry.class));

        CredentialRotationRegistryService service = new CredentialRotationRegistryService(
            repository,
            historyRepository,
            sharedConfigService,
            channelRepository,
            iikoApiMonitorRepository,
            locationsService,
            netBoxService,
            jdbcTemplate,
            securityProperties,
            new ObjectMapper(),
            externalMetadataImportService,
            incidentService,
            environment,
            Clock.fixed(Instant.parse("2026-08-25T12:00:00Z"), ZoneOffset.UTC)
        );

        CredentialRotationRegistryService.RegistrySnapshot snapshot = service.buildSnapshot();

        CredentialRotationRegistryEntry staleEntry = snapshot.items().stream()
            .filter(item -> "obsolete.secret".equals(item.getEntryKey()))
            .findFirst()
            .orElseThrow();
        assertThat(staleEntry.getLastStatus()).isEqualTo(CredentialRotationRegistryService.STATUS_SOURCE_REMOVED);
        assertThat(staleEntry.getStatusLevel()).isEqualTo(CredentialRotationRegistryService.LEVEL_CRITICAL);
        assertThat(staleEntry.getSourcePresent()).isFalse();
        assertThat(staleEntry.getSecretPresent()).isFalse();
        verify(incidentService).openOrRefreshSignalIncident(
            eq(CredentialRotationRegistryService.INCIDENT_SIGNAL_TYPE),
            eq("obsolete.secret"),
            any(),
            any(),
            any(),
            eq(CredentialRotationRegistryService.LEVEL_CRITICAL),
            eq("credential_rotation_registry"),
            anyMap(),
            eq("system")
        );
    }

    @Test
    void buildSnapshotOpensReadableMissingSecretIncidentPayload() {
        CredentialRotationRegistryRepository repository = mock(CredentialRotationRegistryRepository.class);
        MonitoringCheckHistoryRepository historyRepository = mock(MonitoringCheckHistoryRepository.class);
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        ChannelRepository channelRepository = mock(ChannelRepository.class);
        IikoApiMonitorRepository iikoApiMonitorRepository = mock(IikoApiMonitorRepository.class);
        LocationsIikoServerSourceSettingsService locationsService = mock(LocationsIikoServerSourceSettingsService.class);
        NetBoxSyncSettingsService netBoxService = mock(NetBoxSyncSettingsService.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PanelSecurityProperties securityProperties = new PanelSecurityProperties();
        Environment environment = mock(Environment.class);
        IncidentService incidentService = mock(IncidentService.class);
        CredentialRotationExternalMetadataImportService externalMetadataImportService =
            mock(CredentialRotationExternalMetadataImportService.class);

        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("netbox_sync", Map.of(
            "base_url", "https://netbox.example.test",
            "api_token", ""
        ));
        when(sharedConfigService.loadSettings()).thenReturn(settings);
        when(externalMetadataImportService.loadImportedMetadata(settings)).thenReturn(Map.of());
        when(sharedConfigService.loadBotCredentials()).thenReturn(List.of());
        when(channelRepository.findAll()).thenReturn(List.of());
        when(iikoApiMonitorRepository.findAllByOrderByMonitorNameAscIdAsc()).thenReturn(List.of());
        when(locationsService.loadForRuntime(settings)).thenReturn(List.of());
        when(netBoxService.load(settings)).thenReturn(new NetBoxSyncSettingsService.NetBoxSyncSettings(
            "https://netbox.example.test",
            "",
            true,
            60,
            false,
            List.of("site-1")
        ));
        when(jdbcTemplate.queryForList(any(String.class), eq("employee_discount_automation_credentials.v1"))).thenReturn(List.of());
        when(environment.getProperty("app.bots.internal-api.token", "iguana-internal-bot-token")).thenReturn("");
        when(environment.getProperty("app.bots.internal-api.signature-secret", "")).thenReturn("");
        securityProperties.setRememberMeKey("");

        when(repository.findAllByOrderByDisplayNameAscIdAsc()).thenReturn(List.of());
        AtomicLong sequence = new AtomicLong(1L);
        doAnswer(invocation -> {
            CredentialRotationRegistryEntry item = invocation.getArgument(0);
            if (item.getId() == null) {
                item.setId(sequence.getAndIncrement());
            }
            return item;
        }).when(repository).save(any(CredentialRotationRegistryEntry.class));

        CredentialRotationRegistryService service = new CredentialRotationRegistryService(
            repository,
            historyRepository,
            sharedConfigService,
            channelRepository,
            iikoApiMonitorRepository,
            locationsService,
            netBoxService,
            jdbcTemplate,
            securityProperties,
            new ObjectMapper(),
            externalMetadataImportService,
            incidentService,
            environment,
            Clock.fixed(Instant.parse("2026-08-25T12:00:00Z"), ZoneOffset.UTC)
        );

        service.buildSnapshot();

        ArgumentCaptor<String> summaryCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> descriptionCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);

        verify(incidentService).openOrRefreshSignalIncident(
            eq(CredentialRotationRegistryService.INCIDENT_SIGNAL_TYPE),
            eq("settings.netbox.api_token"),
            any(),
            summaryCaptor.capture(),
            descriptionCaptor.capture(),
            eq(CredentialRotationRegistryService.LEVEL_CRITICAL),
            eq("credential_rotation_registry"),
            payloadCaptor.capture(),
            eq("system")
        );

        assertThat(summaryCaptor.getValue()).contains("секрет").doesNotContain("hygiene condition");
        assertThat(descriptionCaptor.getValue()).contains("Почему severity = critical").contains("Следующее действие");
        assertThat(payloadCaptor.getValue())
            .containsEntry("signal_family", "credential_rotation")
            .containsEntry("incident_escalates_to_workbench", true);
        assertThat(String.valueOf(payloadCaptor.getValue().get("incident_severity_policy")))
            .contains("Warning-состояния");
        assertThat(String.valueOf(payloadCaptor.getValue().get("incident_next_action")))
            .contains("заново сохраните секрет");
    }

    @Test
    void updateMetadataComputesHealthyRotationWindowAndRecordsHistory() {
        CredentialRotationRegistryRepository repository = mock(CredentialRotationRegistryRepository.class);
        MonitoringCheckHistoryRepository historyRepository = mock(MonitoringCheckHistoryRepository.class);
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        ChannelRepository channelRepository = mock(ChannelRepository.class);
        IikoApiMonitorRepository iikoApiMonitorRepository = mock(IikoApiMonitorRepository.class);
        LocationsIikoServerSourceSettingsService locationsService = mock(LocationsIikoServerSourceSettingsService.class);
        NetBoxSyncSettingsService netBoxService = mock(NetBoxSyncSettingsService.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PanelSecurityProperties securityProperties = new PanelSecurityProperties();
        Environment environment = mock(Environment.class);
        IncidentService incidentService = mock(IncidentService.class);
        CredentialRotationExternalMetadataImportService externalMetadataImportService =
            mock(CredentialRotationExternalMetadataImportService.class);

        CredentialRotationRegistryEntry entry = new CredentialRotationRegistryEntry();
        entry.setId(15L);
        entry.setEntryKey("settings.netbox.api_token");
        entry.setDisplayName("NetBox sync API token");
        entry.setIntegrationKind("netbox");
        entry.setCredentialKind("api_token");
        entry.setSourceType("settings_json");
        entry.setSourceRef("settings.json#netbox_sync.api_token");
        entry.setSourcePresent(true);
        entry.setSecretPresent(true);
        entry.setCreatedAt(OffsetDateTime.parse("2026-08-01T00:00:00Z"));
        entry.setUpdatedAt(OffsetDateTime.parse("2026-08-01T00:00:00Z"));
        when(repository.findById(15L)).thenReturn(Optional.of(entry));
        doAnswer(invocation -> invocation.getArgument(0)).when(repository).save(any(CredentialRotationRegistryEntry.class));

        CredentialRotationRegistryService service = new CredentialRotationRegistryService(
            repository,
            historyRepository,
            sharedConfigService,
            channelRepository,
            iikoApiMonitorRepository,
            locationsService,
            netBoxService,
            jdbcTemplate,
            securityProperties,
            new ObjectMapper(),
            externalMetadataImportService,
            incidentService,
            environment,
            Clock.fixed(Instant.parse("2026-08-25T12:00:00Z"), ZoneOffset.UTC)
        );

        CredentialRotationRegistryEntry updated = service.updateMetadata(
            15L,
            new CredentialRotationRegistryService.MetadataPatch(
                "ops.lead",
                "Quarterly rotation window",
                "",
                "2026-08-01T00:00:00Z",
                90
            )
        );

        assertThat(updated.getLastStatus()).isEqualTo(CredentialRotationRegistryService.STATUS_HEALTHY);
        assertThat(updated.getStatusLevel()).isEqualTo(CredentialRotationRegistryService.LEVEL_OK);
        assertThat(updated.getNextRotationDueAt()).isEqualTo(OffsetDateTime.parse("2026-10-30T00:00:00Z"));
        assertThat(updated.getOwnerName()).isEqualTo("ops.lead");

        verify(historyRepository).record(
            eq("credential_rotation"),
            eq(15L),
            eq("rotation_registry"),
            eq("healthy"),
            any(String.class),
            any(String.class),
            eq(null),
            anyLong(),
            any(OffsetDateTime.class)
        );
        verify(incidentService).resolveSignalIncident(
            eq(CredentialRotationRegistryService.INCIDENT_SIGNAL_TYPE),
            eq("settings.netbox.api_token"),
            any(),
            anyMap(),
            eq("system")
        );
    }

    @Test
    void buildSnapshotDiscoversNetworkRouteSecretsAndAppliesImportedMetadata() {
        CredentialRotationRegistryRepository repository = mock(CredentialRotationRegistryRepository.class);
        MonitoringCheckHistoryRepository historyRepository = mock(MonitoringCheckHistoryRepository.class);
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        ChannelRepository channelRepository = mock(ChannelRepository.class);
        IikoApiMonitorRepository iikoApiMonitorRepository = mock(IikoApiMonitorRepository.class);
        LocationsIikoServerSourceSettingsService locationsService = mock(LocationsIikoServerSourceSettingsService.class);
        NetBoxSyncSettingsService netBoxService = mock(NetBoxSyncSettingsService.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PanelSecurityProperties securityProperties = new PanelSecurityProperties();
        Environment environment = mock(Environment.class);
        IncidentService incidentService = mock(IncidentService.class);
        CredentialRotationExternalMetadataImportService externalMetadataImportService =
            mock(CredentialRotationExternalMetadataImportService.class);

        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("integration_network", Map.of(
            "project", Map.of(
                "mode", "proxy",
                "proxy", Map.of(
                    "scheme", "http",
                    "host", "corp-proxy.local",
                    "port", 3128,
                    "username", "svc_panel",
                    "password", "proxy-password"
                )
            )
        ));
        settings.put("integration_network_profiles", List.of(
            Map.of(
                "id", "corp-vless",
                "name", "Corp VLESS",
                "mode", "proxy",
                "proxy", Map.of(
                    "scheme", "vless",
                    "host", "vless.internal",
                    "port", 7443,
                    "token", "vless-token"
                )
            )
        ));
        when(sharedConfigService.loadSettings()).thenReturn(settings);
        when(sharedConfigService.loadBotCredentials()).thenReturn(List.of());
        when(externalMetadataImportService.loadImportedMetadata(settings)).thenReturn(Map.of(
            "network.project.proxy.password",
            new CredentialRotationExternalMetadataImportService.ImportedMetadata(
                "network.project.proxy.password",
                "vault-main",
                "network/project/proxy/password",
                OffsetDateTime.parse("2026-12-31T00:00:00Z"),
                OffsetDateTime.parse("2026-08-01T00:00:00Z"),
                180,
                "ops.network",
                "Imported from vault metadata",
                false
            )
        ));

        Channel channel = new Channel();
        channel.setId(8L);
        channel.setChannelName("Retail Bot");
        channel.setDeliverySettings("""
            {
              "network_route": {
                "mode": "proxy",
                "proxy": {
                  "scheme": "socks5",
                  "host": "bot-proxy.internal",
                  "port": 1080,
                  "username": "svc_bot",
                  "password": "bot-proxy-password"
                }
              }
            }
            """);
        when(channelRepository.findAll()).thenReturn(List.of(channel));

        when(netBoxService.load(settings)).thenReturn(new NetBoxSyncSettingsService.NetBoxSyncSettings(
            "",
            "",
            false,
            60,
            false,
            List.of()
        ));
        when(locationsService.loadForRuntime(settings)).thenReturn(List.of());
        when(iikoApiMonitorRepository.findAllByOrderByMonitorNameAscIdAsc()).thenReturn(List.of());
        when(jdbcTemplate.queryForList(any(String.class), eq("employee_discount_automation_credentials.v1"))).thenReturn(List.of());
        when(environment.getProperty("app.bots.internal-api.token", "iguana-internal-bot-token")).thenReturn("");
        when(environment.getProperty("app.bots.internal-api.signature-secret", "")).thenReturn("");
        securityProperties.setRememberMeKey("");

        when(repository.findAllByOrderByDisplayNameAscIdAsc()).thenReturn(List.of());
        AtomicLong sequence = new AtomicLong(1L);
        doAnswer(invocation -> {
            CredentialRotationRegistryEntry item = invocation.getArgument(0);
            if (item.getId() == null) {
                item.setId(sequence.getAndIncrement());
            }
            return item;
        }).when(repository).save(any(CredentialRotationRegistryEntry.class));

        CredentialRotationRegistryService service = new CredentialRotationRegistryService(
            repository,
            historyRepository,
            sharedConfigService,
            channelRepository,
            iikoApiMonitorRepository,
            locationsService,
            netBoxService,
            jdbcTemplate,
            securityProperties,
            new ObjectMapper(),
            externalMetadataImportService,
            incidentService,
            environment,
            Clock.fixed(Instant.parse("2026-08-25T12:00:00Z"), ZoneOffset.UTC)
        );

        CredentialRotationRegistryService.RegistrySnapshot snapshot = service.buildSnapshot();

        assertThat(snapshot.items())
            .extracting(CredentialRotationRegistryEntry::getEntryKey)
            .contains(
                "network.project.proxy.password",
                "network.profile.corp-vless.proxy.token",
                "channel.8.network_route.proxy.password"
            );

        CredentialRotationRegistryEntry importedEntry = snapshot.items().stream()
            .filter(item -> "network.project.proxy.password".equals(item.getEntryKey()))
            .findFirst()
            .orElseThrow();
        assertThat(importedEntry.getExpiresAt()).isEqualTo(OffsetDateTime.parse("2026-12-31T00:00:00Z"));
        assertThat(importedEntry.getRotatedAt()).isEqualTo(OffsetDateTime.parse("2026-08-01T00:00:00Z"));
        assertThat(importedEntry.getRotationIntervalDays()).isEqualTo(180);
        assertThat(importedEntry.getOwnerName()).isEqualTo("ops.network");
        assertThat(importedEntry.getLastStatus()).isEqualTo(CredentialRotationRegistryService.STATUS_HEALTHY);
        assertThat(importedEntry.getStatusLevel()).isEqualTo(CredentialRotationRegistryService.LEVEL_OK);
    }
}
