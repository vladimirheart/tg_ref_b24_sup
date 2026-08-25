package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.repository.MonitoringCheckHistoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProviderHealthMonitoringServiceTest {

    @Test
    void refreshByIdBuildsOkSnapshotForHealthyTelegramChannel() throws Exception {
        ChannelRepository channelRepository = mock(ChannelRepository.class);
        BotProcessService botProcessService = mock(BotProcessService.class);
        MonitoringCheckHistoryRepository historyRepository = mock(MonitoringCheckHistoryRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        IntegrationNetworkService integrationNetworkService = mock(IntegrationNetworkService.class);

        OffsetDateTime now = OffsetDateTime.of(2026, 8, 25, 10, 0, 0, 0, ZoneOffset.UTC);
        Channel channel = channel(17L, "Telegram Ops", "telegram", true);

        when(channelRepository.findById(17L)).thenReturn(Optional.of(channel));
        when(botProcessService.status(17L)).thenReturn(BotProcessService.BotProcessStatus.running(now.minusHours(2)));
        when(jdbcTemplate.queryForList(any(String.class), any(Timestamp.class), any(Timestamp.class))).thenReturn(List.of(
            Map.of(
                "channel_id", 17L,
                "last_inbound_at", now.minusHours(1),
                "inbound_24h", 12L,
                "last_outbound_at", now.minusMinutes(30),
                "outbound_24h", 7L
            )
        ));

        ProviderHealthMonitoringService service = new ProviderHealthMonitoringService(
            channelRepository,
            botProcessService,
            historyRepository,
            jdbcTemplate,
            integrationNetworkService,
            new ObjectMapper(),
            (target, platform) -> new ProviderHealthMonitoringService.ProviderProbeResult(
                ProviderHealthMonitoringService.STATUS_OK,
                true,
                "Telegram getMe passed",
                "ops_support_bot",
                200,
                95L
            ),
            Clock.fixed(Instant.parse("2026-08-25T10:00:00Z"), ZoneOffset.UTC)
        );

        ProviderHealthMonitoringService.ProviderChannelHealth item = service.refreshById(17L);

        assertThat(item.overallStatus()).isEqualTo(ProviderHealthMonitoringService.STATUS_OK);
        assertThat(item.runtimeStatus()).isEqualTo("running");
        assertThat(item.providerStatus()).isEqualTo(ProviderHealthMonitoringService.STATUS_OK);
        assertThat(item.ingressStatus()).isEqualTo(ProviderHealthMonitoringService.ACTIVITY_ACTIVE);
        assertThat(item.outboundStatus()).isEqualTo(ProviderHealthMonitoringService.ACTIVITY_ACTIVE);
        assertThat(item.inbound24h()).isEqualTo(12L);
        assertThat(item.outbound24h()).isEqualTo(7L);

        verify(historyRepository).record(
            eq("provider_health"),
            eq(17L),
            eq("provider_probe"),
            eq("ok"),
            contains("runtime=running"),
            any(),
            eq(200),
            anyLong(),
            any(OffsetDateTime.class)
        );
    }

    @Test
    void buildOverviewMarksStoppedVkRuntimeAsCritical() {
        ChannelRepository channelRepository = mock(ChannelRepository.class);
        BotProcessService botProcessService = mock(BotProcessService.class);
        MonitoringCheckHistoryRepository historyRepository = mock(MonitoringCheckHistoryRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        IntegrationNetworkService integrationNetworkService = mock(IntegrationNetworkService.class);

        OffsetDateTime now = OffsetDateTime.of(2026, 8, 25, 10, 0, 0, 0, ZoneOffset.UTC);
        Channel channel = channel(21L, "VK Retail", "vk", true);
        channel.setPlatformConfig("{\"group_id\":12345,\"confirmation_token\":\"confirm\"}");

        when(channelRepository.findAll()).thenReturn(List.of(channel));
        when(botProcessService.status(21L)).thenReturn(BotProcessService.BotProcessStatus.stopped());
        when(jdbcTemplate.queryForList(any(String.class), any(Timestamp.class), any(Timestamp.class))).thenReturn(List.of(
            Map.of(
                "channel_id", 21L,
                "last_inbound_at", now.minusDays(5),
                "inbound_24h", 0L,
                "last_outbound_at", now.minusDays(4),
                "outbound_24h", 0L
            )
        ));

        ProviderHealthMonitoringService service = new ProviderHealthMonitoringService(
            channelRepository,
            botProcessService,
            historyRepository,
            jdbcTemplate,
            integrationNetworkService,
            new ObjectMapper(),
            (target, platform) -> new ProviderHealthMonitoringService.ProviderProbeResult(
                ProviderHealthMonitoringService.STATUS_OK,
                true,
                "VK groups.getById passed",
                "Retail support",
                200,
                120L
            ),
            Clock.fixed(Instant.parse("2026-08-25T10:00:00Z"), ZoneOffset.UTC)
        );

        ProviderHealthMonitoringService.OverviewSnapshot snapshot = service.buildOverview();

        assertThat(snapshot.items()).hasSize(1);
        ProviderHealthMonitoringService.ProviderChannelHealth item = snapshot.items().get(0);
        assertThat(item.overallStatus()).isEqualTo(ProviderHealthMonitoringService.STATUS_CRITICAL);
        assertThat(item.runtimeStatus()).isEqualTo("stopped");
        assertThat(item.ingressStatus()).isEqualTo(ProviderHealthMonitoringService.ACTIVITY_STALE);
        assertThat(item.outboundStatus()).isEqualTo(ProviderHealthMonitoringService.ACTIVITY_STALE);
        assertThat(snapshot.availabilityOverview().critical()).isEqualTo(1);
        assertThat(snapshot.availabilityOverview().active()).isEqualTo(1);
    }

    private Channel channel(long id,
                            String name,
                            String platform,
                            boolean active) {
        Channel channel = new Channel();
        channel.setId(id);
        channel.setChannelName(name);
        channel.setPlatform(platform);
        channel.setActive(active);
        channel.setToken("token-" + id);
        return channel;
    }
}
