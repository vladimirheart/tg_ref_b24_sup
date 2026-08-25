package com.example.panel.service;

import com.example.panel.entity.PublicIngressMonitor;
import com.example.panel.repository.MonitoringCheckHistoryRepository;
import com.example.panel.repository.PublicIngressMonitorRepository;
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

class PublicIngressMonitoringServiceTest {

    @Test
    void refreshByIdMarksHttpsMonitorHealthyWhenDnsHttpAndTlsAreHealthy() throws Exception {
        PublicIngressMonitorRepository repository = mock(PublicIngressMonitorRepository.class);
        MonitoringCheckHistoryRepository historyRepository = mock(MonitoringCheckHistoryRepository.class);
        PublicIngressMonitor monitor = monitor(11L, "https://support.example.com/webhook", null);

        when(repository.findById(11L)).thenReturn(Optional.of(monitor));
        when(repository.save(any(PublicIngressMonitor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PublicIngressMonitoringService service = new PublicIngressMonitoringService(
            repository,
            historyRepository,
            host -> new PublicIngressMonitoringService.DnsProbeResult(List.of("203.0.113.10", "2001:db8::10")),
            (endpoint, expectedStatus) -> new PublicIngressMonitoringService.HttpProbeResult(204, 120),
            (host, port) -> new PublicIngressMonitoringService.TlsProbeResult(OffsetDateTime.now(ZoneOffset.UTC).plusDays(45), 45)
        );

        PublicIngressMonitor result = service.refreshById(11L);

        assertThat(result.getLastStatus()).isEqualTo(PublicIngressMonitoringService.STATUS_OK);
        assertThat(result.getLastDnsAddresses()).contains("203.0.113.10");
        assertThat(result.getLastHttpStatus()).isEqualTo(204);
        assertThat(result.getLastTlsDaysLeft()).isEqualTo(45);
        verify(historyRepository).record(
            eq("public_ingress"),
            eq(11L),
            eq("ingress_probe"),
            eq("ok"),
            contains("dns ok"),
            any(),
            isNull(),
            anyLong(),
            any(OffsetDateTime.class)
        );
    }

    @Test
    void refreshByIdMarksMonitorWarningWhenTlsExpiryIsClose() throws Exception {
        PublicIngressMonitorRepository repository = mock(PublicIngressMonitorRepository.class);
        MonitoringCheckHistoryRepository historyRepository = mock(MonitoringCheckHistoryRepository.class);
        PublicIngressMonitor monitor = monitor(12L, "https://support.example.com/api", null);

        when(repository.findById(12L)).thenReturn(Optional.of(monitor));
        when(repository.save(any(PublicIngressMonitor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PublicIngressMonitoringService service = new PublicIngressMonitoringService(
            repository,
            historyRepository,
            host -> new PublicIngressMonitoringService.DnsProbeResult(List.of("203.0.113.11")),
            (endpoint, expectedStatus) -> new PublicIngressMonitoringService.HttpProbeResult(200, 85),
            (host, port) -> new PublicIngressMonitoringService.TlsProbeResult(OffsetDateTime.now(ZoneOffset.UTC).plusDays(7), 7)
        );

        PublicIngressMonitor result = service.refreshById(12L);

        assertThat(result.getLastStatus()).isEqualTo(PublicIngressMonitoringService.STATUS_WARNING);
        assertThat(result.getLastSummary()).contains("tls expires in 7 d");
        verify(historyRepository).record(
            eq("public_ingress"),
            eq(12L),
            eq("ingress_probe"),
            eq("warning"),
            contains("tls expires in 7 d"),
            any(),
            isNull(),
            anyLong(),
            any(OffsetDateTime.class)
        );
    }

    @Test
    void refreshByIdMarksMonitorCriticalWhenDnsFailsEvenIfOtherSignalsWouldBeHealthy() throws Exception {
        PublicIngressMonitorRepository repository = mock(PublicIngressMonitorRepository.class);
        MonitoringCheckHistoryRepository historyRepository = mock(MonitoringCheckHistoryRepository.class);
        PublicIngressMonitor monitor = monitor(13L, "https://support.example.com/callback", 405);

        when(repository.findById(13L)).thenReturn(Optional.of(monitor));
        when(repository.save(any(PublicIngressMonitor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PublicIngressMonitoringService service = new PublicIngressMonitoringService(
            repository,
            historyRepository,
            host -> {
                throw new IllegalStateException("resolver timeout");
            },
            (endpoint, expectedStatus) -> new PublicIngressMonitoringService.HttpProbeResult(405, 92),
            (host, port) -> new PublicIngressMonitoringService.TlsProbeResult(OffsetDateTime.now(ZoneOffset.UTC).plusDays(30), 30)
        );

        PublicIngressMonitor result = service.refreshById(13L);

        assertThat(result.getLastStatus()).isEqualTo(PublicIngressMonitoringService.STATUS_CRITICAL);
        assertThat(result.getLastErrorMessage()).contains("dns failed");
        verify(historyRepository).record(
            eq("public_ingress"),
            eq(13L),
            eq("ingress_probe"),
            eq("critical"),
            contains("dns failed"),
            any(),
            isNull(),
            anyLong(),
            any(OffsetDateTime.class)
        );
    }

    private PublicIngressMonitor monitor(long id, String endpointUrl, Integer expectedHttpStatus) {
        PublicIngressMonitor monitor = new PublicIngressMonitor();
        monitor.setId(id);
        monitor.setMonitorName("public-ingress-" + id);
        monitor.setEndpointUrl(endpointUrl);
        monitor.setScheme("https");
        monitor.setHost("support.example.com");
        monitor.setPort(443);
        monitor.setExpectedHttpStatus(expectedHttpStatus);
        monitor.setEnabled(true);
        monitor.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(10));
        monitor.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));
        return monitor;
    }
}
