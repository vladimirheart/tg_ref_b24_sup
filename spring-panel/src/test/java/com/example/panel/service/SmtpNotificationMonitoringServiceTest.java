package com.example.panel.service;

import com.example.panel.entity.SmtpNotificationMonitor;
import com.example.panel.repository.MonitoringCheckHistoryRepository;
import com.example.panel.repository.SmtpNotificationMonitorRepository;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmtpNotificationMonitoringServiceTest {

    @Test
    void refreshByIdMarksMonitorHealthyWhenRelayProbeSucceeds() throws Exception {
        SmtpNotificationMonitorRepository repository = mock(SmtpNotificationMonitorRepository.class);
        MonitoringCheckHistoryRepository historyRepository = mock(MonitoringCheckHistoryRepository.class);
        SmtpNotificationMonitor monitor = monitor(41L, "smtp.example.com", 587, "starttls");

        when(repository.findById(41L)).thenReturn(Optional.of(monitor));
        when(repository.save(any(SmtpNotificationMonitor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SmtpNotificationMonitoringService service = new SmtpNotificationMonitoringService(
            repository,
            historyRepository,
            (host, port, protocolMode, timeoutMs) -> new SmtpNotificationMonitoringService.RelayProbeResult(
                "220 smtp.example.com ready",
                "TLSv1.3",
                "TLS_AES_256_GCM_SHA384",
                95L
            )
        );

        SmtpNotificationMonitor result = service.refreshById(41L);

        assertThat(result.getLastStatus()).isEqualTo(SmtpNotificationMonitoringService.STATUS_OK);
        assertThat(result.getLastBanner()).contains("220");
        assertThat(result.getLastTlsProtocol()).isEqualTo("TLSv1.3");
        verify(historyRepository).record(
            eq("smtp_notification"),
            eq(41L),
            eq("smtp_probe"),
            eq("ok"),
            contains("smtp ok"),
            any(),
            isNull(),
            anyLong(),
            any(OffsetDateTime.class)
        );
    }

    @Test
    void refreshByIdMarksMonitorCriticalWhenRelayProbeFails() throws Exception {
        SmtpNotificationMonitorRepository repository = mock(SmtpNotificationMonitorRepository.class);
        MonitoringCheckHistoryRepository historyRepository = mock(MonitoringCheckHistoryRepository.class);
        SmtpNotificationMonitor monitor = monitor(42L, "smtp.example.com", 465, "tls");

        when(repository.findById(42L)).thenReturn(Optional.of(monitor));
        when(repository.save(any(SmtpNotificationMonitor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SmtpNotificationMonitoringService service = new SmtpNotificationMonitoringService(
            repository,
            historyRepository,
            (host, port, protocolMode, timeoutMs) -> {
                throw new IllegalStateException("TLS handshake failed");
            }
        );

        SmtpNotificationMonitor result = service.refreshById(42L);

        assertThat(result.getLastStatus()).isEqualTo(SmtpNotificationMonitoringService.STATUS_CRITICAL);
        assertThat(result.getLastErrorMessage()).contains("TLS handshake failed");
        verify(historyRepository).record(
            eq("smtp_notification"),
            eq(42L),
            eq("smtp_probe"),
            eq("critical"),
            contains("SMTP probe failed"),
            any(),
            isNull(),
            anyLong(),
            any(OffsetDateTime.class)
        );
    }

    @Test
    void buildAvailabilityOverviewCountsStates() {
        SmtpNotificationMonitorRepository repository = mock(SmtpNotificationMonitorRepository.class);
        MonitoringCheckHistoryRepository historyRepository = mock(MonitoringCheckHistoryRepository.class);
        SmtpNotificationMonitoringService service = new SmtpNotificationMonitoringService(
            repository,
            historyRepository,
            (host, port, protocolMode, timeoutMs) -> null
        );

        SmtpNotificationMonitor up = monitor(1L, "smtp1.example.com", 587, "starttls");
        up.setLastStatus("ok");
        SmtpNotificationMonitor down = monitor(2L, "smtp2.example.com", 587, "starttls");
        down.setLastStatus("critical");
        SmtpNotificationMonitor disabled = monitor(3L, "smtp3.example.com", 25, "plain");
        disabled.setEnabled(false);
        disabled.setLastStatus("disabled");

        SmtpNotificationMonitoringService.AvailabilityOverview overview = service.buildAvailabilityOverview(List.of(up, down, disabled));

        assertThat(overview.total()).isEqualTo(3);
        assertThat(overview.up()).isEqualTo(1);
        assertThat(overview.down()).isEqualTo(1);
        assertThat(overview.disabled()).isEqualTo(1);
        assertThat(overview.availabilityPercent()).isEqualTo(50.0d);
    }

    private SmtpNotificationMonitor monitor(long id,
                                            String host,
                                            int port,
                                            String protocolMode) {
        SmtpNotificationMonitor monitor = new SmtpNotificationMonitor();
        monitor.setId(id);
        monitor.setMonitorName("smtp-monitor-" + id);
        monitor.setRelayHost(host);
        monitor.setRelayPort(port);
        monitor.setProtocolMode(protocolMode);
        monitor.setConnectTimeoutMs(5_000);
        monitor.setEnabled(true);
        monitor.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(5));
        monitor.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));
        return monitor;
    }
}
