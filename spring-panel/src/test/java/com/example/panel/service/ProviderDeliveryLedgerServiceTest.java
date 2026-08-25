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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProviderDeliveryLedgerServiceTest {

    @Test
    void buildOverviewAggregatesLatestChannelStateAndRecentAttempts() {
        ProviderDeliveryLedgerRepository repository = mock(ProviderDeliveryLedgerRepository.class);
        MonitoringCheckHistoryRepository historyRepository = mock(MonitoringCheckHistoryRepository.class);
        ChannelRepository channelRepository = mock(ChannelRepository.class);

        Channel telegram = channel(17L, "Telegram Ops", "telegram", true);
        Channel vk = channel(21L, "VK Retail", "vk", true);

        OffsetDateTime now = OffsetDateTime.of(2026, 8, 25, 11, 0, 0, 0, ZoneOffset.UTC);
        ProviderDeliveryLedgerEntry telegramLatest = entry(17L, "T-100", "telegram", "success", "success", "ok", "none", now.minusMinutes(10));
        ProviderDeliveryLedgerEntry vkLatest = entry(21L, "T-101", "vk", "failed", "rate_limited", "warning", "transient", now.minusMinutes(5));
        vkLatest.setHttpStatus(429);
        vkLatest.setProviderMessage("Too many requests");

        when(channelRepository.findAll()).thenReturn(List.of(telegram, vk));
        when(repository.summarizeByChannelSince(any())).thenReturn(List.of(
            new ProviderDeliveryLedgerRepository.ChannelAttemptStats(
                17L, 6L, 6L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                now.minusMinutes(10), now.minusMinutes(10), null
            ),
            new ProviderDeliveryLedgerRepository.ChannelAttemptStats(
                21L, 4L, 2L, 2L, 2L, 0L, 2L, 2L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                now.minusMinutes(5), now.minusHours(2), now.minusMinutes(5)
            )
        ));
        when(repository.findRecent(500)).thenReturn(List.of(vkLatest, telegramLatest));
        when(repository.findRecent(50)).thenReturn(List.of(vkLatest, telegramLatest));

        ProviderDeliveryLedgerService service = new ProviderDeliveryLedgerService(
            repository,
            historyRepository,
            channelRepository,
            Clock.fixed(Instant.parse("2026-08-25T11:00:00Z"), ZoneOffset.UTC)
        );

        ProviderDeliveryLedgerService.OverviewSnapshot snapshot = service.buildOverview();

        assertThat(snapshot.items()).hasSize(2);
        assertThat(snapshot.overview().attempts24h()).isEqualTo(10L);
        assertThat(snapshot.overview().success24h()).isEqualTo(8L);
        assertThat(snapshot.overview().failure24h()).isEqualTo(2L);
        assertThat(snapshot.overview().rateLimited24h()).isEqualTo(2L);
        assertThat(snapshot.items().get(0).channelId()).isEqualTo(21L);
        assertThat(snapshot.items().get(0).status()).isEqualTo("warning");
        assertThat(snapshot.items().get(1).status()).isEqualTo("ok");
        assertThat(snapshot.recentAttempts()).hasSize(2);
    }

    @Test
    void recordAttemptPersistsLedgerRowAndMonitoringHistory() {
        ProviderDeliveryLedgerRepository repository = mock(ProviderDeliveryLedgerRepository.class);
        MonitoringCheckHistoryRepository historyRepository = mock(MonitoringCheckHistoryRepository.class);
        ChannelRepository channelRepository = mock(ChannelRepository.class);

        ProviderDeliveryLedgerService service = new ProviderDeliveryLedgerService(
            repository,
            historyRepository,
            channelRepository,
            Clock.fixed(Instant.parse("2026-08-25T11:30:00Z"), ZoneOffset.UTC)
        );

        Channel channel = channel(33L, "MAX Delivery", "max", true);
        DialogReplyTransportService.DialogReplyTransportResult result =
            DialogReplyTransportService.DialogReplyTransportResult.failure(
                "MAX: rate limit",
                "rate_limited",
                "warning",
                "transient",
                429,
                "rate_limit",
                "Too many requests",
                "{\"code\":\"rate_limit\"}",
                240L
            );

        service.recordAttempt(channel, "T-330", 990033L, "ai_agent", "image", 7001L, result);

        verify(repository).save(any(ProviderDeliveryLedgerEntry.class));
        verify(historyRepository).record(
            eq("provider_delivery"),
            eq(33L),
            eq("delivery_attempt"),
            eq("warning"),
            eq("MAX image rate_limited ticket=T-330"),
            any(),
            eq(429),
            eq(240L),
            any(OffsetDateTime.class)
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
                                              String ticketId,
                                              String platform,
                                              String deliveryStatus,
                                              String classification,
                                              String severityLevel,
                                              String retryState,
                                              OffsetDateTime attemptedAt) {
        ProviderDeliveryLedgerEntry entry = new ProviderDeliveryLedgerEntry();
        entry.setChannelId(channelId);
        entry.setTicketId(ticketId);
        entry.setPlatform(platform);
        entry.setProvider(platform);
        entry.setSenderKind("operator");
        entry.setMessageKind("text");
        entry.setDeliveryStatus(deliveryStatus);
        entry.setClassification(classification);
        entry.setSeverityLevel(severityLevel);
        entry.setRetryState(retryState);
        entry.setAttemptedAt(attemptedAt);
        return entry;
    }
}
