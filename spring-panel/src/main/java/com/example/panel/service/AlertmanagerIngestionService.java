package com.example.panel.service;

import com.example.panel.config.AlertmanagerIngestionProperties;
import com.example.panel.model.observability.AlertmanagerWebhookPayload;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AlertmanagerIngestionService {

    public static final String SIGNAL_TYPE = "alertmanager";
    public static final String SOURCE = "alertmanager";
    public static final String ACTOR = "alertmanager";

    private static final Logger log = LoggerFactory.getLogger(AlertmanagerIngestionService.class);
    private static final List<String> ACTIONABLE_SEVERITIES = List.of("critical", "high");
    private static final Duration DEFAULT_LEASE_TTL = Duration.ofSeconds(30);

    private final AlertmanagerIngestionProperties properties;
    private final IncidentService incidentService;
    private final RuntimeCoordinationService runtimeCoordinationService;
    private final MeterRegistry meterRegistry;

    public AlertmanagerIngestionService(AlertmanagerIngestionProperties properties,
                                        IncidentService incidentService,
                                        RuntimeCoordinationService runtimeCoordinationService,
                                        MeterRegistry meterRegistry) {
        this.properties = properties;
        this.incidentService = incidentService;
        this.runtimeCoordinationService = runtimeCoordinationService;
        this.meterRegistry = meterRegistry;
    }

    public Map<String, Object> ingest(AlertmanagerWebhookPayload payload) {
        if (payload == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Alertmanager payload is required.");
        }

        int processed = 0;
        int firing = 0;
        int resolved = 0;
        int deduplicated = 0;
        int ignored = 0;
        List<Map<String, Object>> outcomes = new ArrayList<>();

        for (AlertmanagerWebhookPayload.Alert alert : payload.safeAlerts()) {
            if (alert == null) {
                ignored++;
                continue;
            }

            AlertContext context = buildContext(payload, alert);
            if (!context.actionable()) {
                ignored++;
                recordMetric(context.status(), "ignored");
                outcomes.add(outcomePayload(context, "ignored"));
                continue;
            }

            TransitionOutcome outcome = processWithLease(context);
            processed++;
            if ("firing".equals(context.status())) {
                firing++;
            } else if ("resolved".equals(context.status())) {
                resolved++;
            }
            if (outcome == TransitionOutcome.DEDUPLICATED) {
                deduplicated++;
            }
            recordMetric(context.status(), outcome.metricValue());
            outcomes.add(outcomePayload(context, outcome.metricValue()));

            log.info(
                "Alertmanager ingestion status={} severity={} alert={} fingerprint={} outcome={}",
                context.status(),
                context.severity(),
                context.alertName(),
                context.fingerprint(),
                outcome.metricValue()
            );
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("processed", processed);
        result.put("firing", firing);
        result.put("resolved", resolved);
        result.put("deduplicated", deduplicated);
        result.put("ignored", ignored);
        result.put("outcomes", outcomes);
        return result;
    }

    private TransitionOutcome processWithLease(AlertContext context) {
        AtomicBoolean executed = new AtomicBoolean(false);
        AtomicReference<TransitionOutcome> outcome = new AtomicReference<>();

        runtimeCoordinationService.runWithLease(
            "alertmanager-ingestion-" + context.fingerprint(),
            safeLeaseTtl(),
            () -> {
                executed.set(true);
                outcome.set(processTransition(context));
            }
        );

        if (!executed.get()) {
            recordMetric(context.status(), "deferred");
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Alertmanager event could not acquire the shared ingestion lease; retry is required."
            );
        }

        return Objects.requireNonNullElse(outcome.get(), TransitionOutcome.DEDUPLICATED);
    }

    private TransitionOutcome processTransition(AlertContext context) {
        boolean activeIncident = hasActiveIncident(context.fingerprint());

        if ("firing".equals(context.status())) {
            if (activeIncident) {
                return TransitionOutcome.DEDUPLICATED;
            }

            incidentService.openOrRefreshSignalIncident(
                SIGNAL_TYPE,
                context.fingerprint(),
                buildTitle(context),
                context.summary(),
                context.description(),
                context.severity(),
                SOURCE,
                context.eventPayload(),
                ACTOR,
                buildInitialRoutes()
            );
            return TransitionOutcome.OPENED;
        }

        if ("resolved".equals(context.status())) {
            if (!activeIncident) {
                return TransitionOutcome.DEDUPLICATED;
            }

            incidentService.resolveSignalIncident(
                SIGNAL_TYPE,
                context.fingerprint(),
                "Alertmanager alert resolved: " + context.alertName(),
                context.eventPayload(),
                ACTOR
            );
            return TransitionOutcome.RESOLVED;
        }

        return TransitionOutcome.IGNORED;
    }

    private boolean hasActiveIncident(String fingerprint) {
        for (Map<String, Object> incident : incidentService.listIncidentSummariesForSignal(SIGNAL_TYPE, fingerprint)) {
            String status = normalize(incident.get("status"));
            if (!"resolved".equals(status) && !"closed".equals(status)) {
                return true;
            }
        }
        return false;
    }

    private AlertContext buildContext(AlertmanagerWebhookPayload payload,
                                      AlertmanagerWebhookPayload.Alert alert) {
        Map<String, String> labels = new LinkedHashMap<>(payload.safeCommonLabels());
        labels.putAll(alert.safeLabels());

        Map<String, String> annotations = new LinkedHashMap<>(payload.safeCommonAnnotations());
        annotations.putAll(alert.safeAnnotations());

        String status = normalize(firstNonBlank(alert.status(), payload.status()));
        String severity = normalize(labels.get("severity"));
        String alertName = defaultIfBlank(labels.get("alertname"), "unnamed-alert");
        String service = defaultIfBlank(labels.get("service"), "unknown");
        String summary = defaultIfBlank(annotations.get("summary"), alertName + " " + defaultIfBlank(status, "event"));
        String description = defaultIfBlank(
            annotations.get("description"),
            "Alertmanager event for service " + service + "."
        );
        String fingerprint = normalize(alert.fingerprint());
        if (!StringUtils.hasText(fingerprint)) {
            fingerprint = fallbackFingerprint(labels);
        }

        boolean actionable = ACTIONABLE_SEVERITIES.contains(severity)
            && ("firing".equals(status) || "resolved".equals(status));

        Map<String, Object> eventPayload = new LinkedHashMap<>();
        eventPayload.put("signal_family", SIGNAL_TYPE);
        eventPayload.put("alertmanager_version", defaultIfBlank(payload.version(), ""));
        eventPayload.put("group_key", defaultIfBlank(payload.groupKey(), ""));
        eventPayload.put("receiver", defaultIfBlank(payload.receiver(), ""));
        eventPayload.put("group_status", defaultIfBlank(payload.status(), ""));
        eventPayload.put("alert_status", defaultIfBlank(status, ""));
        eventPayload.put("fingerprint", fingerprint);
        eventPayload.put("alertname", alertName);
        eventPayload.put("service", service);
        eventPayload.put("severity", defaultIfBlank(severity, ""));
        eventPayload.put("labels", labels);
        eventPayload.put("annotations", annotations);
        eventPayload.put("starts_at", defaultIfBlank(alert.startsAt(), ""));
        eventPayload.put("ends_at", defaultIfBlank(alert.endsAt(), ""));
        eventPayload.put("generator_url", defaultIfBlank(alert.generatorURL(), ""));

        return new AlertContext(
            status,
            severity,
            alertName,
            service,
            fingerprint,
            truncate(summary, 500),
            truncate(description, 2_000),
            actionable,
            eventPayload
        );
    }

    private List<Map<String, Object>> buildInitialRoutes() {
        String routeType = defaultIfBlank(properties.getRouteType(), "all_operators");
        String routeTarget = defaultIfBlank(properties.getRouteTarget(), "all_operators");

        Map<String, Object> route = new LinkedHashMap<>();
        route.put("route_type", routeType);
        route.put("route_target", routeTarget);
        route.put("route_status", "queued");
        route.put("note", "Alertmanager approved incident delivery route");
        return List.of(route);
    }

    private String buildTitle(AlertContext context) {
        String suffix = "unknown".equals(context.service()) ? "" : " [" + context.service() + "]";
        return truncate("Alertmanager: " + context.alertName() + suffix, 300);
    }

    private String fallbackFingerprint(Map<String, String> labels) {
        String canonical = labels.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> entry.getKey() + "=" + Objects.toString(entry.getValue(), ""))
            .reduce((left, right) -> left + "\n" + right)
            .orElse("alertmanager");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable for Alertmanager fingerprint fallback.", ex);
        }
    }

    private Map<String, Object> outcomePayload(AlertContext context, String outcome) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("fingerprint", context.fingerprint());
        item.put("status", defaultIfBlank(context.status(), ""));
        item.put("severity", defaultIfBlank(context.severity(), ""));
        item.put("alertname", context.alertName());
        item.put("outcome", outcome);
        return item;
    }

    private void recordMetric(String status, String outcome) {
        meterRegistry.counter(
            "iguana.alertmanager.ingestion.events",
            "status", metricStatus(status),
            "outcome", metricOutcome(outcome)
        ).increment();
    }

    private String metricStatus(String value) {
        return switch (defaultIfBlank(normalize(value), "unknown")) {
            case "firing" -> "firing";
            case "resolved" -> "resolved";
            default -> "unknown";
        };
    }

    private String metricOutcome(String value) {
        return switch (defaultIfBlank(normalize(value), "unknown")) {
            case "opened" -> "opened";
            case "resolved" -> "resolved";
            case "deduplicated" -> "deduplicated";
            case "ignored" -> "ignored";
            case "deferred" -> "deferred";
            default -> "unknown";
        };
    }

    private Duration safeLeaseTtl() {
        Duration configured = properties.getLeaseTtl();
        if (configured == null || configured.isZero() || configured.isNegative()) {
            return DEFAULT_LEASE_TTL;
        }
        return configured;
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String normalize(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private enum TransitionOutcome {
        OPENED("opened"),
        RESOLVED("resolved"),
        DEDUPLICATED("deduplicated"),
        IGNORED("ignored");

        private final String metricValue;

        TransitionOutcome(String metricValue) {
            this.metricValue = metricValue;
        }

        String metricValue() {
            return metricValue;
        }
    }

    private record AlertContext(
        String status,
        String severity,
        String alertName,
        String service,
        String fingerprint,
        String summary,
        String description,
        boolean actionable,
        Map<String, Object> eventPayload
    ) {
    }
}
