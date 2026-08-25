package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.entity.ProviderDeliveryLedgerEntry;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.repository.MonitoringCheckHistoryRepository;
import com.example.panel.repository.ProviderDeliveryLedgerRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProviderDeliveryAlertingServiceTest {

    @Test
    void buildOverviewIncludesBurnRateAndRelatedIncidents() {
        ProviderDeliveryLedgerRepository repository = mock(ProviderDeliveryLedgerRepository.class);
        MonitoringCheckHistoryRepository historyRepository = mock(MonitoringCheckHistoryRepository.class);
        ChannelRepository channelRepository = mock(ChannelRepository.class);
        IncidentService incidentService = mock(IncidentService.class);

        OffsetDateTime now = OffsetDateTime.of(2026, 8, 25, 12, 0, 0, 0, ZoneOffset.UTC);
        Channel telegram = channel(17L, "Telegram Ops", "telegram", true);

        when(channelRepository.findAll()).thenReturn(List.of(telegram));
        when(repository.summarizeByChannelSince(any())).thenReturn(
            List.of(
                new ProviderDeliveryLedgerRepository.ChannelAttemptStats(
                    17L, 10L, 2L, 8L, 0L, 8L, 1L, 7L, 1L, 0L, 0L, 5L, 2L, 1L, 0L,
                    now.minusMinutes(2), now.minusHours(2), now.minusMinutes(2)
                )
            ),
            List.of(
                new ProviderDeliveryLedgerRepository.ChannelAttemptStats(
                    17L, 14L, 4L, 10L, 0L, 10L, 2L, 8L, 2L, 0L, 0L, 6L, 2L, 2L, 0L,
                    now.minusMinutes(2), now.minusHours(2), now.minusMinutes(2)
                )
            )
        );
        ProviderDeliveryLedgerEntry latest = entry(17L, "telegram", "provider_error", 502, now.minusMinutes(2));
        when(repository.findRecent(500)).thenReturn(List.of(latest));
        when(incidentService.listIncidentSummariesForSignalType("provider_delivery")).thenReturn(List.of(
            Map.of(
                "id", 41L,
                "incident_key", "INC-41",
                "title", "Sustained provider delivery failures: Telegram Ops",
                "status", "open",
                "severity", "critical",
                "updated_at", now.toString(),
                "signal_key", "channel-17/delivery_failures"
            )
        ));

        ProviderDeliveryAlertingService service = new ProviderDeliveryAlertingService(
            repository,
            historyRepository,
            channelRepository,
            incidentService,
            Clock.fixed(Instant.parse("2026-08-25T12:00:00Z"), ZoneOffset.UTC)
        );

        ProviderDeliveryAlertingService.OverviewSnapshot snapshot = service.buildOverview();

        assertThat(snapshot.items()).hasSize(1);
        ProviderDeliveryAlertingService.ChannelAlertSnapshot item = snapshot.items().get(0);
        assertThat(item.alertStatus()).isEqualTo("critical");
        assertThat(item.failureSignal().status()).isEqualTo("critical");
        assertThat(item.relatedIncidents()).hasSize(1);
        assertThat(snapshot.overview().criticalChannels()).isEqualTo(1);
        assertThat(snapshot.overview().failurePressureChannels()).isEqualTo(1);
        assertThat(snapshot.overview().activeIncidents()).isEqualTo(1);
    }

    @Test
    void refreshAllOpensSignalIncidentWhenBurnRateBecomesActionable() {
        ProviderDeliveryLedgerRepository repository = mock(ProviderDeliveryLedgerRepository.class);
        MonitoringCheckHistoryRepository historyRepository = mock(MonitoringCheckHistoryRepository.class);
        ChannelRepository channelRepository = mock(ChannelRepository.class);
        IncidentService incidentService = mock(IncidentService.class);

        OffsetDateTime now = OffsetDateTime.of(2026, 8, 25, 12, 30, 0, 0, ZoneOffset.UTC);
        Channel telegram = channel(17L, "Telegram Ops", "telegram", true);

        when(channelRepository.findAll()).thenReturn(List.of(telegram));
        when(repository.summarizeByChannelSince(any())).thenReturn(
            List.of(
                new ProviderDeliveryLedgerRepository.ChannelAttemptStats(
                    17L, 8L, 1L, 7L, 0L, 7L, 0L, 6L, 1L, 0L, 0L, 4L, 2L, 1L, 0L,
                    now.minusMinutes(1), now.minusHours(1), now.minusMinutes(1)
                )
            ),
            List.of(
                new ProviderDeliveryLedgerRepository.ChannelAttemptStats(
                    17L, 12L, 2L, 10L, 0L, 10L, 0L, 8L, 2L, 0L, 0L, 6L, 2L, 2L, 0L,
                    now.minusMinutes(1), now.minusHours(1), now.minusMinutes(1)
                )
            )
        );
        when(repository.findRecent(500)).thenReturn(List.of(entry(17L, "telegram", "provider_error", 500, now.minusMinutes(1))));
        when(incidentService.listIncidentSummariesForSignalType("provider_delivery")).thenReturn(List.of());
        when(historyRepository.findRecent(eq("provider_delivery_alerting"), anyLong(), eq(1))).thenReturn(List.of());

        ProviderDeliveryAlertingService service = new ProviderDeliveryAlertingService(
            repository,
            historyRepository,
            channelRepository,
            incidentService,
            Clock.fixed(Instant.parse("2026-08-25T12:30:00Z"), ZoneOffset.UTC)
        );

        ProviderDeliveryAlertingService.RefreshSummary summary = service.refreshAll();

        assertThat(summary.checked()).isEqualTo(1);
        assertThat(summary.actionable()).isEqualTo(1);
        verify(historyRepository).record(eq("provider_delivery_alerting"), eq(17L), eq("delivery_burn_rate"), eq("critical"), any(), any(), eq(null), eq(null), any(OffsetDateTime.class));
        verify(incidentService).openOrRefreshSignalIncident(
            eq("provider_delivery"),
            eq("channel-17/delivery_failures"),
            any(),
            any(),
            any(),
            eq("critical"),
            eq("provider_delivery_alerting"),
            any(),
            eq("system")
        );
        verify(incidentService, never()).resolveSignalIncident(eq("provider_delivery"), eq("channel-17/delivery_failures"), any(), any(), eq("system"));
    }

    @Test
    void refreshAllResolvesIncidentWhenBurnRateRecovers() {
        ProviderDeliveryLedgerRepository repository = mock(ProviderDeliveryLedgerRepository.class);
        MonitoringCheckHistoryRepository historyRepository = mock(MonitoringCheckHistoryRepository.class);
        ChannelRepository channelRepository = mock(ChannelRepository.class);
        IncidentService incidentService = mock(IncidentService.class);

        OffsetDateTime now = OffsetDateTime.of(2026, 8, 25, 13, 0, 0, 0, ZoneOffset.UTC);
        Channel telegram = channel(17L, "Telegram Ops", "telegram", true);

        when(channelRepository.findAll()).thenReturn(List.of(telegram));
        when(repository.summarizeByChannelSince(any())).thenReturn(
            List.of(
                new ProviderDeliveryLedgerRepository.ChannelAttemptStats(
                    17L, 12L, 12L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                    now.minusMinutes(2), now.minusMinutes(2), null
                )
            ),
            List.of(
                new ProviderDeliveryLedgerRepository.ChannelAttemptStats(
                    17L, 16L, 16L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                    now.minusMinutes(2), now.minusMinutes(2), null
                )
            )
        );
        when(repository.findRecent(500)).thenReturn(List.of(entry(17L, "telegram", "success", null, now.minusMinutes(2))));
        when(incidentService.listIncidentSummariesForSignalType("provider_delivery")).thenReturn(List.of(
            Map.of(
                "id", 42L,
                "incident_key", "INC-42",
                "title", "Sustained provider delivery failures: Telegram Ops",
                "status", "investigating",
                "severity", "critical",
                "updated_at", now.toString(),
                "signal_key", "channel-17/delivery_failures"
            )
        ));
        when(historyRepository.findRecent(eq("provider_delivery_alerting"), anyLong(), eq(1))).thenReturn(List.of(
            new MonitoringCheckHistoryRepository.HistoryEntry(
                1L,
                "provider_delivery_alerting",
                17L,
                "delivery_burn_rate",
                "critical",
                "previous",
                "failure_status=critical; failure_fingerprint=critical|8|7|14.00|8|7|14.00; rate_limit_status=ok; rate_limit_fingerprint=ok",
                null,
                null,
                now.minusMinutes(15)
            )
        ));

        ProviderDeliveryAlertingService service = new ProviderDeliveryAlertingService(
            repository,
            historyRepository,
            channelRepository,
            incidentService,
            Clock.fixed(Instant.parse("2026-08-25T13:00:00Z"), ZoneOffset.UTC)
        );

        service.refreshAll();

        verify(incidentService).resolveSignalIncident(
            eq("provider_delivery"),
            eq("channel-17/delivery_failures"),
            any(),
            any(),
            eq("system")
        );
    }

    private Channel channel(long id, String name, String platform, boolean active) {
        Channel channel = new Channel();
        channel.setId(id);
        channel.setChannelName(name);
        channel.setPlatform(platform);
        channel.setActive(active);
        channel.setToken("token-" + id);
        return channel;
    }

    private ProviderDeliveryLedgerEntry entry(long channelId,
                                              String platform,
                                              String classification,
                                              Integer httpStatus,
                                              OffsetDateTime attemptedAt) {
        ProviderDeliveryLedgerEntry entry = new ProviderDeliveryLedgerEntry();
        entry.setChannelId(channelId);
        entry.setPlatform(platform);
        entry.setProvider(platform);
        entry.setSenderKind("operator");
        entry.setMessageKind("text");
        entry.setDeliveryStatus("success".equals(classification) ? "success" : "failed");
        entry.setClassification(classification);
        entry.setSeverityLevel("success".equals(classification) ? "ok" : "critical");
        entry.setRetryState("success".equals(classification) ? "none" : "transient");
        entry.setHttpStatus(httpStatus);
        entry.setProviderMessage(classification);
        entry.setAttemptedAt(attemptedAt);
        return entry;
    }
}
