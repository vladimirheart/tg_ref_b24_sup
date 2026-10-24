package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.entity.CredentialRotationRegistryEntry;
import com.example.panel.repository.*;
import com.example.panel.security.PanelSecurityProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CredentialRouteApplicabilityTest {
    final Map<String, CredentialRotationRegistryEntry> stored = new LinkedHashMap<>();
    final Channel channel = new Channel();
    final CredentialRotationRegistryRepository repository = mock(CredentialRotationRegistryRepository.class);
    final IncidentService incidents = mock(IncidentService.class);
    final CredentialRotationRegistryService service;

    CredentialRouteApplicabilityTest() {
        channel.setId(3L); channel.setChannelName("тостов");
        var shared = mock(SharedConfigService.class);
        var channels = mock(ChannelRepository.class);
        var imports = mock(CredentialRotationExternalMetadataImportService.class);
        var environment = mock(Environment.class);
        when(shared.loadSettings()).thenReturn(Map.of());
        when(channels.findAll()).thenReturn(List.of(channel));
        when(environment.getProperty(anyString(), anyString())).thenAnswer(call -> call.getArgument(1));
        when(repository.findAllByOrderByDisplayNameAscIdAsc()).thenAnswer(call -> new ArrayList<>(stored.values()));
        when(repository.findById(anyLong())).thenAnswer(call -> stored.values().stream().filter(value -> value.getId().equals(call.getArgument(0))).findFirst());
        when(repository.save(any())).thenAnswer(call -> {
            CredentialRotationRegistryEntry value = call.getArgument(0);
            if (value.getId() == null) value.setId((long) stored.size() + 1);
            stored.put(value.getEntryKey(), value); return value;
        });
        service = new CredentialRotationRegistryService(repository, mock(MonitoringCheckHistoryRepository.class),
            shared, channels, mock(IikoApiMonitorRepository.class), mock(LocationsIikoServerSourceSettingsService.class),
            mock(NetBoxSyncSettingsService.class), mock(JdbcTemplate.class), new PanelSecurityProperties(),
            new ObjectMapper(), imports, incidents, environment, Clock.fixed(Instant.parse("2026-10-24T09:00:00Z"), ZoneOffset.UTC));
    }

    void route(String mode, String scheme, String user, String password, String token) throws Exception {
        channel.setDeliverySettings(new ObjectMapper().writeValueAsString(Map.of("network_route", Map.of(
            "mode", mode, "profile_id", "telegram-ftl-dev-ru-mirror", "proxy", Map.of(
                "scheme", scheme, "username", user, "password", password, "token", token)))));
    }

    @ParameterizedTest @ValueSource(strings = {"profile", "inherit", "direct", "vpn", ""})
    void unusedLocalPlaceholdersDoNotCreateCredentialsOrIncidents(String mode) throws Exception {
        route(mode, "http", "", "", ""); service.refreshAll();
        assertThat(stored.keySet()).noneMatch(key -> key.startsWith("channel.3.network_route"));
        verify(incidents, never()).openOrRefreshSignalIncident(eq("credential_rotation"), startsWith("channel.3.network_route"), any(), any(), any(), any(), any(), any(), any());
    }

    @Test void passwordlessProxyAndNonVlessTokenAreOptional() throws Exception {
        route("proxy", "socks5", "", "", ""); service.refreshAll();
        assertThat(stored.keySet()).noneMatch(key -> key.startsWith("channel.3.network_route"));
    }

    @Test void requiredMissingPasswordBecomesNotRequiredThenCriticalAgainWithoutDeletingHistory() throws Exception {
        route("proxy", "http", "user", "", ""); service.refreshAll();
        var password = stored.get("channel.3.network_route.proxy.password");
        assertThat(password.getLastStatus()).isEqualTo("missing_secret");
        assertThat(password.getStatusLevel()).isEqualTo("critical");
        long id = password.getId();
        route("profile", "http", "user", "", ""); service.refreshAll();
        assertThat(stored.get(password.getEntryKey()).getId()).isEqualTo(id);
        assertThat(password.getLastStatus()).isEqualTo("not_required");
        assertThat(password.getStatusLevel()).isEqualTo("ok");
        verify(incidents).resolveSignalIncident(eq("credential_rotation"), eq(password.getEntryKey()), any(), any(), any());
        service.updateMetadata(id, new CredentialRotationRegistryService.MetadataPatch("operator", "", null, null, null));
        assertThat(password.getLastStatus()).isEqualTo("not_required");
        route("proxy", "http", "user", "", ""); service.refreshAll();
        assertThat(password.getLastStatus()).isEqualTo("missing_secret");
    }

    @Test void missingVlessTokenIsRequiredButPasswordIsNot() throws Exception {
        route("proxy", "vless", "user", "", ""); service.refreshAll();
        assertThat(stored).doesNotContainKey("channel.3.network_route.proxy.password");
        assertThat(stored.get("channel.3.network_route.proxy.token").getLastStatus()).isEqualTo("missing_secret");
    }

    @Test void existingBogusEmptyTokenIsRetainedAsNotRequiredEvenWhenProxyObjectRemoved() throws Exception {
        var old = new CredentialRotationRegistryEntry(); old.setId(200L);
        old.setEntryKey("channel.3.network_route.proxy.token"); old.setStatusLevel("critical");
        old.setLastStatus("missing_secret"); old.setSourcePresent(true); old.setSecretPresent(false);
        stored.put(old.getEntryKey(), old);
        channel.setDeliverySettings("{}"); service.refreshAll();
        assertThat(old.getLastStatus()).isEqualTo("not_required");
        assertThat(old.getSourcePresent()).isTrue();
        assertThat(stored.get(old.getEntryKey())).isSameAs(old);
    }
}
