package com.example.panel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.panel.entity.Incident;
import com.example.panel.repository.IncidentRepository;
import com.example.panel.repository.IncidentRouteRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IncidentOpsMetricsServiceTest {

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private IncidentRouteRepository incidentRouteRepository;

    @InjectMocks
    private IncidentOpsMetricsService service;

    @Test
    void buildSummaryAggregatesQueueAndDurationMetrics() {
        when(incidentRepository.countByStatusNotIn(anyCollection())).thenReturn(6L);
        when(incidentRepository.countBySeverityAndStatusNotIn(eq("critical"), anyCollection())).thenReturn(2L);
        when(incidentRepository.countByCreatedAtBeforeAndStatusNotIn(any(OffsetDateTime.class), anyCollection())).thenReturn(3L);
        when(incidentRepository.countByCreatedAtGreaterThanEqual(any(OffsetDateTime.class))).thenReturn(5L);
        when(incidentRepository.countByResolvedAtGreaterThanEqual(any(OffsetDateTime.class))).thenReturn(4L);
        when(incidentRouteRepository.countByRouteStatus("failed")).thenReturn(1L);

        OffsetDateTime base = OffsetDateTime.of(2026, 8, 23, 20, 0, 0, 0, ZoneOffset.UTC);
        Incident first = incident(base.minusMinutes(40), base.minusMinutes(30), base.minusMinutes(10));
        Incident second = incident(base.minusMinutes(80), base.minusMinutes(40), null);
        when(incidentRepository.findByCreatedAtGreaterThanEqual(any(OffsetDateTime.class)))
            .thenReturn(List.of(first, second));

        Map<String, Object> summary = service.buildSummary();

        assertEquals(true, summary.get("success"));
        assertEquals(6L, summary.get("active_count"));
        assertEquals(2L, summary.get("critical_active_count"));
        assertEquals(3L, summary.get("aged_open_count"));
        assertEquals(1L, summary.get("failed_route_count"));
        assertEquals(5L, summary.get("created_24h_count"));
        assertEquals(4L, summary.get("resolved_24h_count"));
        assertEquals(25.0d, (Double) summary.get("avg_ack_minutes_7d"), 0.001d);
        assertEquals(30.0d, (Double) summary.get("avg_resolve_minutes_7d"), 0.001d);
        assertEquals(2, summary.get("ack_sample_count_7d"));
        assertEquals(1, summary.get("resolve_sample_count_7d"));
    }

    private Incident incident(OffsetDateTime createdAt,
                              OffsetDateTime acknowledgedAt,
                              OffsetDateTime resolvedAt) {
        Incident incident = new Incident();
        incident.setCreatedAt(createdAt);
        incident.setAcknowledgedAt(acknowledgedAt);
        incident.setResolvedAt(resolvedAt);
        return incident;
    }
}