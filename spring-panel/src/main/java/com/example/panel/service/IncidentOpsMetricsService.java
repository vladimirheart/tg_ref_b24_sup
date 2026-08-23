package com.example.panel.service;

import com.example.panel.entity.Incident;
import com.example.panel.repository.IncidentRepository;
import com.example.panel.repository.IncidentRouteRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Service;

@Service
public class IncidentOpsMetricsService {

    private static final Collection<String> TERMINAL_STATUSES = Set.of("resolved", "closed");
    private static final int SUMMARY_WINDOW_HOURS = 24;
    private static final int DURATION_WINDOW_DAYS = 7;
    private static final int AGING_THRESHOLD_MINUTES = 60;

    private final IncidentRepository incidentRepository;
    private final IncidentRouteRepository incidentRouteRepository;

    public IncidentOpsMetricsService(IncidentRepository incidentRepository,
                                     IncidentRouteRepository incidentRouteRepository) {
        this.incidentRepository = incidentRepository;
        this.incidentRouteRepository = incidentRouteRepository;
    }

    public Map<String, Object> buildSummary() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime since24Hours = now.minusHours(SUMMARY_WINDOW_HOURS);
        OffsetDateTime since7Days = now.minusDays(DURATION_WINDOW_DAYS);
        OffsetDateTime agingThreshold = now.minusMinutes(AGING_THRESHOLD_MINUTES);

        List<Incident> durationWindow = incidentRepository.findByCreatedAtGreaterThanEqual(since7Days);
        DurationMetric acknowledgement = averageDurationMinutes(durationWindow, Incident::getAcknowledgedAt);
        DurationMetric resolution = averageDurationMinutes(durationWindow, Incident::getResolvedAt);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("generated_at", now.toString());
        payload.put("summary_window_hours", SUMMARY_WINDOW_HOURS);
        payload.put("duration_window_days", DURATION_WINDOW_DAYS);
        payload.put("aging_threshold_minutes", AGING_THRESHOLD_MINUTES);
        payload.put("active_count", incidentRepository.countByStatusNotIn(TERMINAL_STATUSES));
        payload.put("critical_active_count", incidentRepository.countBySeverityAndStatusNotIn("critical", TERMINAL_STATUSES));
        payload.put("aged_open_count", incidentRepository.countByCreatedAtBeforeAndStatusNotIn(agingThreshold, TERMINAL_STATUSES));
        payload.put("failed_route_count", incidentRouteRepository.countByRouteStatus("failed"));
        payload.put("created_24h_count", incidentRepository.countByCreatedAtGreaterThanEqual(since24Hours));
        payload.put("resolved_24h_count", incidentRepository.countByResolvedAtGreaterThanEqual(since24Hours));
        payload.put("avg_ack_minutes_7d", acknowledgement.averageMinutes());
        payload.put("avg_resolve_minutes_7d", resolution.averageMinutes());
        payload.put("ack_sample_count_7d", acknowledgement.sampleCount());
        payload.put("resolve_sample_count_7d", resolution.sampleCount());
        return payload;
    }

    private DurationMetric averageDurationMinutes(List<Incident> incidents,
                                                  Function<Incident, OffsetDateTime> endTimeExtractor) {
        if (incidents == null || incidents.isEmpty()) {
            return new DurationMetric(null, 0);
        }

        long totalSeconds = 0L;
        int samples = 0;
        for (Incident incident : incidents) {
            if (incident == null || incident.getCreatedAt() == null) {
                continue;
            }
            OffsetDateTime endTime = endTimeExtractor.apply(incident);
            if (endTime == null || endTime.isBefore(incident.getCreatedAt())) {
                continue;
            }
            totalSeconds += Duration.between(incident.getCreatedAt(), endTime).getSeconds();
            samples++;
        }
        if (samples == 0) {
            return new DurationMetric(null, 0);
        }
        double minutes = totalSeconds / 60.0d / samples;
        double rounded = Math.round(minutes * 10.0d) / 10.0d;
        return new DurationMetric(rounded, samples);
    }

    private record DurationMetric(Double averageMinutes, int sampleCount) {
    }
}