package com.example.panel.service;

import com.example.panel.entity.Incident;
import com.example.panel.entity.IncidentEvent;
import com.example.panel.entity.IncidentRelation;
import com.example.panel.entity.IncidentRoute;
import com.example.panel.entity.IncidentWatcher;
import com.example.panel.entity.Task;
import com.example.panel.repository.IncidentEventRepository;
import com.example.panel.repository.IncidentRelationRepository;
import com.example.panel.repository.IncidentRepository;
import com.example.panel.repository.IncidentRouteRepository;
import com.example.panel.repository.IncidentWatcherRepository;
import com.example.panel.repository.TaskRepository;
import com.example.panel.repository.TicketRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IncidentService {

    private static final Logger log = LoggerFactory.getLogger(IncidentService.class);
    private static final Set<String> STATUSES = Set.of("open", "acknowledged", "investigating", "resolved", "closed");
    private static final Set<String> SEVERITIES = Set.of("low", "medium", "high", "critical");
    private static final Set<String> RELATION_TYPES = Set.of("ticket", "task", "object_passport");
    private static final Set<String> ROUTE_TYPES = Set.of("webhook", "user", "users", "department", "all_operators");

    private final IncidentRepository incidentRepository;
    private final IncidentEventRepository incidentEventRepository;
    private final IncidentRelationRepository incidentRelationRepository;
    private final IncidentWatcherRepository incidentWatcherRepository;
    private final IncidentRouteRepository incidentRouteRepository;
    private final TicketRepository ticketRepository;
    private final TaskRepository taskRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final NotificationRoutingService notificationRoutingService;
    private final IncidentRouteDeliveryOutboxService incidentRouteDeliveryOutboxService;

    public IncidentService(IncidentRepository incidentRepository,
                           IncidentEventRepository incidentEventRepository,
                           IncidentRelationRepository incidentRelationRepository,
                           IncidentWatcherRepository incidentWatcherRepository,
                           IncidentRouteRepository incidentRouteRepository,
                           TicketRepository ticketRepository,
                           TaskRepository taskRepository,
                           JdbcTemplate jdbcTemplate,
                           ObjectMapper objectMapper,
                           NotificationRoutingService notificationRoutingService,
                           IncidentRouteDeliveryOutboxService incidentRouteDeliveryOutboxService) {
        this.incidentRepository = incidentRepository;
        this.incidentEventRepository = incidentEventRepository;
        this.incidentRelationRepository = incidentRelationRepository;
        this.incidentWatcherRepository = incidentWatcherRepository;
        this.incidentRouteRepository = incidentRouteRepository;
        this.ticketRepository = ticketRepository;
        this.taskRepository = taskRepository;
                this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.notificationRoutingService = notificationRoutingService;
        this.incidentRouteDeliveryOutboxService = incidentRouteDeliveryOutboxService;
			}

			private Incident saveNewIncidentWithGeneratedKey(
					Incident incident) {

				incident.setIncidentKey(
					"INC-PENDING-" + UUID.randomUUID()
				);

				incident =
					incidentRepository.saveAndFlush(
						incident
					);

				incident.setIncidentKey(
					"INC-" + incident.getId()
				);

				return incidentRepository.saveAndFlush(
					incident
				);
			}

	public Map<String, Object> listIncidents(String status,
											 String severity,
                                             String relationType,
                                             String relationKey,
                                             String query,
                                             String signalType,
                                             Integer limit) {
        int safeLimit = limit == null || limit <= 0 ? 100 : Math.min(limit, 200);
        String normalizedStatus = normalizeStatus(status, null);
        String normalizedSeverity = normalizeSeverity(severity, null);
        String normalizedQuery = normalizeNullableText(query);
        String normalizedSignalType = normalizeNullableText(signalType);
        List<Incident> incidents = loadFilteredIncidents(normalizedStatus, relationType, relationKey, normalizedSignalType);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Incident incident : incidents) {
            if (normalizedSeverity != null && !normalizedSeverity.equals(incident.getSeverity())) {
                continue;
            }
            if (!matchesQuery(incident, normalizedQuery)) {
                continue;
            }
            items.add(buildIncidentSummary(incident));
            if (items.size() >= safeLimit) {
                break;
            }
        }
        return Map.of(
            "success", true,
            "items", items,
            "total", items.size()
        );
    }

    public List<Map<String, Object>> listIncidentSummariesForTicket(String ticketId) {
        return listIncidentSummariesForRelation("ticket", ticketId);
    }

    public List<Map<String, Object>> listIncidentSummariesForTask(Long taskId) {
        if (taskId == null) {
            return List.of();
        }
        return listIncidentSummariesForRelation("task", String.valueOf(taskId));
    }

    public List<Map<String, Object>> listIncidentSummariesForObjectPassport(long passportId) {
        return listIncidentSummariesForRelation("object_passport", String.valueOf(passportId));
    }

    public List<Map<String, Object>> listIncidentSummariesForSignalType(String signalType) {
        String normalized = normalizeNullableText(signalType);
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }
        return incidentRepository.findBySignalTypeOrderByUpdatedAtDescIdDesc(normalized).stream()
            .map(this::buildIncidentSummary)
            .toList();
    }

    public List<Map<String, Object>> listIncidentSummariesForSignal(String signalType, String signalKey) {
        String normalizedSignalType = normalizeNullableText(signalType);
        String normalizedSignalKey = normalizeNullableText(signalKey);
        if (!StringUtils.hasText(normalizedSignalType) || !StringUtils.hasText(normalizedSignalKey)) {
            return List.of();
        }
        return incidentRepository.findBySignalTypeAndSignalKeyOrderByUpdatedAtDescIdDesc(normalizedSignalType, normalizedSignalKey).stream()
            .map(this::buildIncidentSummary)
            .toList();
    }

    public Map<String, Object> getIncident(Long id) {
        Incident incident = requireIncident(id);
        return Map.of(
            "success", true,
            "incident", buildIncidentDetails(incident)
        );
    }

    @Transactional
    public Map<String, Object> createIncident(Map<String, Object> payload, String actor) {
        OffsetDateTime now = OffsetDateTime.now();
        Incident incident = new Incident();
        incident.setTitle(requiredText(payload.get("title"), "Укажите заголовок incident."));
        incident.setSummary(normalizeNullableText(payload.get("summary")));
        incident.setDescription(normalizeNullableText(payload.get("description")));
        incident.setStatus(normalizeStatus(payload.get("status"), "open"));
        incident.setSeverity(normalizeSeverity(payload.get("severity"), "medium"));
        incident.setSource(normalizeNullableText(payload.get("source")));
        incident.setSignalType(normalizeNullableText(payload.get("signal_type")));
        incident.setSignalKey(normalizeNullableText(payload.get("signal_key")));
        incident.setOwner(normalizeNullableIdentity(payload.get("owner")));
        incident.setCreatedBy(normalizeNullableIdentity(actor));
        incident.setCreatedAt(now);
        incident.setUpdatedAt(now);
        if ("acknowledged".equals(incident.getStatus())) {
            incident.setAcknowledgedAt(now);
        }
        if (isResolvedStatus(incident.getStatus())) {
            incident.setResolvedAt(now);
        }
        incident =
			saveNewIncidentWithGeneratedKey(
				incident
			);

        syncRelations(incident, payload, actor);
        syncWatchers(incident, extractWatchers(payload), actor);
        syncRoutes(incident, extractRoutes(payload), now);
        appendEvent(incident, "created", "Incident создан", payload, actor, now);
        notifyIncidentParticipants(incident, "incident_created", "Создан incident " + incident.getIncidentKey() + ": " + incident.getTitle(), actor);
        incidentRouteDeliveryOutboxService.enqueueIncidentRoutes(incident, "incident_created", "Incident создан", payload, actor);
        log.info(
            "Incident created id={} key={} status={} severity={} actor={}",
            incident.getId(),
            incident.getIncidentKey(),
            incident.getStatus(),
            incident.getSeverity(),
            normalizeNullableIdentity(actor)
        );
        return Map.of(
            "success", true,
            "incident", buildIncidentDetails(incident)
        );
    }

    @Transactional
    public Map<String, Object> updateIncident(Long id, Map<String, Object> payload, String actor) {
        Incident incident = requireIncident(id);
        OffsetDateTime now = OffsetDateTime.now();
        List<String> changes = new ArrayList<>();

        if (payload.containsKey("title")) {
            String value = requiredText(payload.get("title"), "Укажите заголовок incident.");
            if (!Objects.equals(value, incident.getTitle())) {
                incident.setTitle(value);
                changes.add("title");
            }
        }
        if (payload.containsKey("summary")) {
            String value = normalizeNullableText(payload.get("summary"));
            if (!Objects.equals(value, incident.getSummary())) {
                incident.setSummary(value);
                changes.add("summary");
            }
        }
        if (payload.containsKey("description")) {
            String value = normalizeNullableText(payload.get("description"));
            if (!Objects.equals(value, incident.getDescription())) {
                incident.setDescription(value);
                changes.add("description");
            }
        }
        if (payload.containsKey("severity")) {
            String value = normalizeSeverity(payload.get("severity"), incident.getSeverity());
            if (!Objects.equals(value, incident.getSeverity())) {
                incident.setSeverity(value);
                changes.add("severity");
            }
        }
        if (payload.containsKey("status")) {
            String value = normalizeStatus(payload.get("status"), incident.getStatus());
            if (!Objects.equals(value, incident.getStatus())) {
                incident.setStatus(value);
                if ("acknowledged".equals(value) && incident.getAcknowledgedAt() == null) {
                    incident.setAcknowledgedAt(now);
                }
                if (isResolvedStatus(value)) {
                    incident.setResolvedAt(now);
                } else {
                    incident.setResolvedAt(null);
                }
                changes.add("status");
            }
        }
        if (payload.containsKey("owner")) {
            String value = normalizeNullableIdentity(payload.get("owner"));
            if (!Objects.equals(value, incident.getOwner())) {
                incident.setOwner(value);
                changes.add("owner");
            }
        }
        if (payload.containsKey("source")) {
            String value = normalizeNullableText(payload.get("source"));
            if (!Objects.equals(value, incident.getSource())) {
                incident.setSource(value);
                changes.add("source");
            }
        }
        if (payload.containsKey("signal_type")) {
            String value = normalizeNullableText(payload.get("signal_type"));
            if (!Objects.equals(value, incident.getSignalType())) {
                incident.setSignalType(value);
                changes.add("signal_type");
            }
        }
        if (payload.containsKey("signal_key")) {
            String value = normalizeNullableText(payload.get("signal_key"));
            if (!Objects.equals(value, incident.getSignalKey())) {
                incident.setSignalKey(value);
                changes.add("signal_key");
            }
        }

        boolean relationsChanged = payload.containsKey("relations")
            || payload.containsKey("ticket_id")
            || payload.containsKey("ticket_ids")
            || payload.containsKey("task_id")
            || payload.containsKey("task_ids")
            || payload.containsKey("object_passport_id")
            || payload.containsKey("object_passport_ids");
        if (relationsChanged) {
            syncRelations(incident, payload, actor);
            changes.add("relations");
        }
        if (payload.containsKey("watchers")) {
            syncWatchers(incident, extractWatchers(payload), actor);
            changes.add("watchers");
        }
        if (payload.containsKey("routes")) {
            syncRoutes(incident, extractRoutes(payload), now);
            changes.add("routes");
        }

        if (!changes.isEmpty()) {
            incident.setUpdatedAt(now);
            incidentRepository.save(incident);
            appendEvent(
                incident,
                "updated",
                "Обновлены поля: " + String.join(", ", changes),
                Map.of("changes", changes),
                actor,
                now
            );
            notifyIncidentParticipants(incident, "incident_updated", "Обновлён incident " + incident.getIncidentKey() + ": " + incident.getTitle(), actor);
            incidentRouteDeliveryOutboxService.enqueueIncidentRoutes(
                incident,
                "incident_updated",
                "Обновлены поля: " + String.join(", ", changes),
                Map.of("changes", changes),
                actor
            );
            log.info(
                "Incident updated id={} key={} changes={} status={} actor={}",
                incident.getId(),
                incident.getIncidentKey(),
                changes,
                incident.getStatus(),
                normalizeNullableIdentity(actor)
            );
        }

        return Map.of(
            "success", true,
            "incident", buildIncidentDetails(incident)
        );
    }

    @Transactional
    public Map<String, Object> addIncidentEvent(Long id, Map<String, Object> payload, String actor) {
        Incident incident = requireIncident(id);
        OffsetDateTime now = OffsetDateTime.now();
        String eventType = normalizeEventType(payload.get("event_type"));
        String eventText = requiredText(payload.get("event_text"), "Укажите текст события incident.");
        appendEvent(incident, eventType, eventText, payload.get("payload"), actor, now);
        incident.setUpdatedAt(now);
        incidentRepository.save(incident);
        notifyIncidentParticipants(incident, "incident_event", "Новое событие в incident " + incident.getIncidentKey() + ": " + eventText, actor);
        incidentRouteDeliveryOutboxService.enqueueIncidentRoutes(incident, eventType, eventText, payload.get("payload"), actor);
        log.info(
            "Incident event appended id={} key={} eventType={} actor={}",
            incident.getId(),
            incident.getIncidentKey(),
            eventType,
            normalizeNullableIdentity(actor)
        );
        return Map.of(
            "success", true,
            "incident", buildIncidentDetails(incident)
        );
    }

    @Transactional
    public Map<String, Object> openOrRefreshSignalIncident(String signalType,
                                                           String signalKey,
                                                           String title,
                                                           String summary,
                                                           String description,
                                                           String severity,
                                                           String source,
                                                           Object payload,
                                                           String actor) {
        String normalizedSignalType = requiredText(signalType, "Укажите signal type incident.");
        String normalizedSignalKey = requiredText(signalKey, "Укажите signal key incident.");
        OffsetDateTime now = OffsetDateTime.now();
        Incident incident = incidentRepository.findBySignalTypeAndSignalKeyOrderByUpdatedAtDescIdDesc(
                normalizedSignalType,
                normalizedSignalKey
            ).stream()
            .filter(item -> !isResolvedStatus(item.getStatus()))
            .findFirst()
            .orElse(null);
        if (incident == null) {
            incident = new Incident();
            incident.setTitle(requiredText(title, "Укажите заголовок incident."));
            incident.setSummary(normalizeNullableText(summary));
            incident.setDescription(normalizeNullableText(description));
            incident.setStatus("open");
            incident.setSeverity(normalizeSeverity(severity, "high"));
            incident.setSource(normalizeNullableText(source));
            incident.setSignalType(normalizedSignalType);
            incident.setSignalKey(normalizedSignalKey);
            incident.setCreatedBy(normalizeNullableIdentity(actor));
            incident.setCreatedAt(now);
            incident.setUpdatedAt(now);
            incident =
				saveNewIncidentWithGeneratedKey(
					incident
				);
            appendEvent(incident, "signal_opened", "Signal incident created", payload, actor, now);
        } else {
            incident.setTitle(requiredText(title, "Укажите заголовок incident."));
            incident.setSummary(normalizeNullableText(summary));
            incident.setDescription(normalizeNullableText(description));
            incident.setSeverity(normalizeSeverity(severity, incident.getSeverity() != null ? incident.getSeverity() : "high"));
            incident.setSource(normalizeNullableText(source));
            incident.setStatus("investigating");
            incident.setResolvedAt(null);
            incident.setUpdatedAt(now);
            incidentRepository.save(incident);
            appendEvent(incident, "signal_refreshed", "Signal incident refreshed", payload, actor, now);
        }
        notifyIncidentParticipants(incident, "incident_signal_updated", "Обновлён signal incident " + incident.getIncidentKey(), actor);
        incidentRouteDeliveryOutboxService.enqueueIncidentRoutes(
            incident,
            "incident_signal_updated",
            "Signal incident updated",
            payload,
            actor
        );
        return Map.of("success", true, "incident", buildIncidentDetails(incident));
    }

    @Transactional
    public Map<String, Object> resolveSignalIncident(String signalType,
                                                     String signalKey,
                                                     String eventText,
                                                     Object payload,
                                                     String actor) {
        String normalizedSignalType = normalizeNullableText(signalType);
        String normalizedSignalKey = normalizeNullableText(signalKey);
        if (!StringUtils.hasText(normalizedSignalType) || !StringUtils.hasText(normalizedSignalKey)) {
            return Map.of("success", true, "resolved", false);
        }
        Incident incident = incidentRepository.findBySignalTypeAndSignalKeyOrderByUpdatedAtDescIdDesc(
                normalizedSignalType,
                normalizedSignalKey
            ).stream()
            .filter(item -> !isResolvedStatus(item.getStatus()))
            .findFirst()
            .orElse(null);
        if (incident == null) {
            return Map.of("success", true, "resolved", false);
        }
        OffsetDateTime now = OffsetDateTime.now();
        incident.setStatus("resolved");
        incident.setResolvedAt(now);
        incident.setUpdatedAt(now);
        incidentRepository.save(incident);
        appendEvent(incident, "signal_resolved", requiredText(eventText, "Укажите текст события incident."), payload, actor, now);
        notifyIncidentParticipants(incident, "incident_signal_resolved", "Signal incident resolved " + incident.getIncidentKey(), actor);
        incidentRouteDeliveryOutboxService.enqueueIncidentRoutes(incident, "incident_signal_resolved", eventText, payload, actor);
        return Map.of("success", true, "resolved", true, "incident", buildIncidentDetails(incident));
    }

    @Transactional
    public void appendSignalEvent(String signalType,
                                  String signalKey,
                                  String eventType,
                                  String eventText,
                                  Object payload,
                                  String actor) {
        String normalizedSignalType = normalizeNullableText(signalType);
        String normalizedSignalKey = normalizeNullableText(signalKey);
        if (!StringUtils.hasText(normalizedSignalType) || !StringUtils.hasText(normalizedSignalKey)) {
            return;
        }
        Incident incident = incidentRepository.findBySignalTypeAndSignalKeyOrderByUpdatedAtDescIdDesc(
                normalizedSignalType,
                normalizedSignalKey
            ).stream()
            .filter(item -> !isResolvedStatus(item.getStatus()))
            .findFirst()
            .orElse(null);
        if (incident == null) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        incident.setUpdatedAt(now);
        incidentRepository.save(incident);
        appendEvent(incident, normalizeEventType(eventType), requiredText(eventText, "Укажите текст события incident."), payload, actor, now);
        incidentRouteDeliveryOutboxService.enqueueIncidentRoutes(
            incident,
            normalizeEventType(eventType),
            requiredText(eventText, "Укажите текст события incident."),
            payload,
            actor
        );
    }

    @Transactional
    public Map<String, Object> addWatcher(Long incidentId, Object watcherIdentity, String actor) {
        Incident incident = requireIncident(incidentId);
        String watcher = normalizeRequiredIdentity(watcherIdentity, "Укажите watcher.");
        List<String> watchers = new ArrayList<>(loadWatcherIdentities(incidentId));
        if (!watchers.contains(watcher)) {
            watchers.add(watcher);
            syncWatchers(incident, watchers, actor);
            appendEvent(incident, "watcher_added", "Добавлен watcher " + watcher, Map.of("watcher", watcher), actor, OffsetDateTime.now());
            incident.setUpdatedAt(OffsetDateTime.now());
            incidentRepository.save(incident);
        }
        return Map.of(
            "success", true,
            "incident", buildIncidentDetails(incident)
        );
    }

    @Transactional
    public Map<String, Object> removeWatcher(Long incidentId, String watcherIdentity, String actor) {
        Incident incident = requireIncident(incidentId);
        String watcher = normalizeRequiredIdentity(watcherIdentity, "Укажите watcher.");
        List<String> watchers = new ArrayList<>(loadWatcherIdentities(incidentId));
        if (watchers.removeIf(item -> item.equalsIgnoreCase(watcher))) {
            syncWatchers(incident, watchers, actor);
            appendEvent(incident, "watcher_removed", "Удалён watcher " + watcher, Map.of("watcher", watcher), actor, OffsetDateTime.now());
            incident.setUpdatedAt(OffsetDateTime.now());
            incidentRepository.save(incident);
        }
        return Map.of(
            "success", true,
            "incident", buildIncidentDetails(incident)
        );
    }

    @Transactional
    public Map<String, Object> addRoute(Long incidentId, Map<String, Object> payload, String actor) {
        Incident incident = requireIncident(incidentId);
        IncidentRoute route = new IncidentRoute();
        route.setIncident(incident);
        String routeType = normalizeRouteType(payload.get("route_type"));
        route.setRouteType(routeType);
        route.setRouteTarget(resolveRouteTarget(routeType, payload.get("route_target")));
        route.setRouteStatus(normalizeNullableText(payload.get("route_status")));
        route.setNote(normalizeNullableText(payload.get("note")));
        OffsetDateTime now = OffsetDateTime.now();
        route.setCreatedAt(now);
        route.setUpdatedAt(now);
        incidentRouteRepository.save(route);
        incident.setUpdatedAt(now);
        incidentRepository.save(incident);
        appendEvent(incident, "route_added", "Добавлен маршрут " + route.getRouteType() + " -> " + route.getRouteTarget(), payload, actor, now);
        notifyIncidentParticipants(incident, "incident_route_updated", "Обновлён routing incident " + incident.getIncidentKey(), actor);
        incidentRouteDeliveryOutboxService.enqueueRouteReplay(incident, route.getId(), actor);
        return Map.of(
            "success", true,
            "incident", buildIncidentDetails(incident)
        );
    }

    @Transactional
    public Map<String, Object> updateRoute(Long incidentId, Long routeId, Map<String, Object> payload, String actor) {
        Incident incident = requireIncident(incidentId);
        IncidentRoute route = incidentRouteRepository.findById(routeId)
            .filter(item -> item.getIncident() != null && Objects.equals(item.getIncident().getId(), incidentId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Маршрут incident не найден"));
        if (payload.containsKey("route_type")) {
            route.setRouteType(normalizeRouteType(payload.get("route_type")));
        }
        if (payload.containsKey("route_target")) {
            route.setRouteTarget(resolveRouteTarget(route.getRouteType(), payload.get("route_target")));
        }
        if (payload.containsKey("route_status")) {
            route.setRouteStatus(normalizeNullableText(payload.get("route_status")));
        }
        if (payload.containsKey("note")) {
            route.setNote(normalizeNullableText(payload.get("note")));
        }
        OffsetDateTime now = OffsetDateTime.now();
        route.setUpdatedAt(now);
        incidentRouteRepository.save(route);
        incident.setUpdatedAt(now);
        incidentRepository.save(incident);
        appendEvent(incident, "route_updated", "Обновлён маршрут " + route.getRouteType() + " -> " + route.getRouteTarget(), payload, actor, now);
        notifyIncidentParticipants(incident, "incident_route_updated", "Обновлён routing incident " + incident.getIncidentKey(), actor);
        incidentRouteDeliveryOutboxService.enqueueRouteReplay(incident, route.getId(), actor);
        return Map.of(
            "success", true,
            "incident", buildIncidentDetails(incident)
        );
    }

    @Transactional
    public Map<String, Object> redeliverRoute(Long incidentId, Long routeId, String actor) {
        Incident incident = requireIncident(incidentId);
        int queued = incidentRouteDeliveryOutboxService.enqueueRouteReplay(incident, routeId, actor);
        if (queued <= 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Маршрут incident не найден");
        }
        appendEvent(incident, "route_redelivery_requested", "Запрошена повторная доставка маршрута " + routeId,
            Map.of("route_id", routeId), actor, OffsetDateTime.now());
        incident.setUpdatedAt(OffsetDateTime.now());
        incidentRepository.save(incident);
        return Map.of("success", true, "queued", queued, "incident", buildIncidentDetails(incident));
    }

    @Transactional
    public Map<String, Object> redeliverFailedRoutes(Long incidentId, Integer limit, String actor) {
        Incident incident = requireIncident(incidentId);
        int safeLimit = limit == null ? 25 : limit;
        int queued = incidentRouteDeliveryOutboxService.enqueueFailedRouteReplays(incident, safeLimit, actor);
        if (queued > 0) {
            appendEvent(incident, "route_redelivery_batch_requested",
                "Запрошена повторная доставка failed routes (" + queued + ")",
                Map.of("count", queued, "limit", safeLimit), actor, OffsetDateTime.now());
            incident.setUpdatedAt(OffsetDateTime.now());
            incidentRepository.save(incident);
        }
        return Map.of("success", true, "queued", queued, "limit", safeLimit, "incident", buildIncidentDetails(incident));
    }

    private List<Map<String, Object>> listIncidentSummariesForRelation(String relationType, String relationKey) {
        String normalizedKey = normalizeNullableText(relationKey);
        if (!StringUtils.hasText(normalizedKey)) {
            return List.of();
        }
        List<IncidentRelation> relations = incidentRelationRepository.findByRelationTypeAndRelationKeyOrderByCreatedAtDescIdDesc(
            relationType,
            normalizedKey
        );
        if (relations.isEmpty()) {
            return List.of();
        }
        List<Incident> incidents = incidentRepository.findByIdInOrderByUpdatedAtDescIdDesc(
            relations.stream()
                .map(IncidentRelation::getIncident)
                .filter(Objects::nonNull)
                .map(Incident::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList()
        );
        List<Map<String, Object>> items = new ArrayList<>();
        for (Incident incident : incidents) {
            items.add(buildIncidentSummary(incident));
        }
        return items;
    }

    private List<Incident> loadFilteredIncidents(String status, String relationType, String relationKey, String signalType) {
        if (StringUtils.hasText(relationType) && StringUtils.hasText(relationKey)) {
            String normalizedType = normalizeRelationType(relationType);
            String normalizedKey = normalizeNullableText(relationKey);
            List<IncidentRelation> relations = incidentRelationRepository.findByRelationTypeAndRelationKeyOrderByCreatedAtDescIdDesc(
                normalizedType,
                normalizedKey
            );
            if (relations.isEmpty()) {
                return List.of();
            }
            List<Long> incidentIds = relations.stream()
                .map(IncidentRelation::getIncident)
                .filter(Objects::nonNull)
                .map(Incident::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
            List<Incident> incidents = incidentRepository.findByIdInOrderByUpdatedAtDescIdDesc(incidentIds);
            if (!StringUtils.hasText(status)) {
                return filterBySignalType(incidents, signalType);
            }
            List<Incident> filtered = new ArrayList<>();
            for (Incident incident : incidents) {
                if (status.equals(incident.getStatus()) && matchesSignalType(incident, signalType)) {
                    filtered.add(incident);
                }
            }
            return filtered;
        }
        if (StringUtils.hasText(signalType)) {
            List<Incident> incidents = incidentRepository.findBySignalTypeOrderByUpdatedAtDescIdDesc(signalType);
            if (!StringUtils.hasText(status)) {
                return incidents;
            }
            List<Incident> filtered = new ArrayList<>();
            for (Incident incident : incidents) {
                if (status.equals(incident.getStatus())) {
                    filtered.add(incident);
                }
            }
            return filtered;
        }
        if (StringUtils.hasText(status)) {
            return incidentRepository.findByStatusOrderByUpdatedAtDescIdDesc(status);
        }
        return incidentRepository.findTop200ByOrderByUpdatedAtDescIdDesc();
    }

    private List<Incident> filterBySignalType(List<Incident> incidents, String signalType) {
        if (!StringUtils.hasText(signalType) || incidents == null || incidents.isEmpty()) {
            return incidents == null ? List.of() : incidents;
        }
        List<Incident> filtered = new ArrayList<>();
        for (Incident incident : incidents) {
            if (matchesSignalType(incident, signalType)) {
                filtered.add(incident);
            }
        }
        return filtered;
    }

    private boolean matchesSignalType(Incident incident, String signalType) {
        if (!StringUtils.hasText(signalType)) {
            return true;
        }
        return incident != null
            && StringUtils.hasText(incident.getSignalType())
            && signalType.equalsIgnoreCase(incident.getSignalType().trim());
    }

    private boolean matchesQuery(Incident incident, String query) {
        if (!StringUtils.hasText(query)) {
            return true;
        }
        if (incident == null) {
            return false;
        }
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        return containsIgnoreCase(incident.getIncidentKey(), normalized)
            || containsIgnoreCase(incident.getTitle(), normalized)
            || containsIgnoreCase(incident.getSummary(), normalized)
            || containsIgnoreCase(incident.getSignalKey(), normalized)
            || containsIgnoreCase(incident.getOwner(), normalized)
            || containsIgnoreCase(incident.getSource(), normalized);
    }

    private boolean containsIgnoreCase(String value, String query) {
        return StringUtils.hasText(value)
            && StringUtils.hasText(query)
            && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private Incident requireIncident(Long id) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите incident id");
        }
        return incidentRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident не найден"));
    }

    private Map<String, Object> buildIncidentSummary(Incident incident) {
        List<IncidentRelation> relations = incidentRelationRepository.findByIncidentIdOrderByPrimaryRelationDescCreatedAtAscIdAsc(incident.getId());
        List<IncidentWatcher> watchers = incidentWatcherRepository.findByIncidentIdOrderByWatcherIdentityAsc(incident.getId());
        List<IncidentRoute> routes = incidentRouteRepository.findByIncidentIdOrderByCreatedAtAscIdAsc(incident.getId());
        List<IncidentEvent> events = incidentEventRepository.findByIncidentIdOrderByCreatedAtAscIdAsc(incident.getId());
        Map<Long, Map<String, Object>> routeDeliverySnapshots = incidentRouteDeliveryOutboxService.loadLatestRouteDeliverySnapshots(incident.getId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", incident.getId());
        payload.put("incident_key", incident.getIncidentKey());
        payload.put("title", incident.getTitle());
        payload.put("summary", incident.getSummary() == null ? "" : incident.getSummary());
        payload.put("status", incident.getStatus());
        payload.put("severity", incident.getSeverity());
        payload.put("source", incident.getSource() == null ? "" : incident.getSource());
        payload.put("owner", incident.getOwner() == null ? "" : incident.getOwner());
        payload.put("updated_at", incident.getUpdatedAt() != null ? incident.getUpdatedAt().toString() : null);
        payload.put("created_at", incident.getCreatedAt() != null ? incident.getCreatedAt().toString() : null);
        payload.put("acknowledged_at", incident.getAcknowledgedAt() != null ? incident.getAcknowledgedAt().toString() : null);
        payload.put("resolved_at", incident.getResolvedAt() != null ? incident.getResolvedAt().toString() : null);
        payload.put("relation_count", relations.size());
        payload.put("watcher_count", watchers.size());
        payload.put("route_count", routes.size());
        payload.put("failed_route_count", routes.stream()
            .filter(route -> {
                Map<String, Object> snapshot = routeDeliverySnapshots.get(route.getId());
                return snapshot != null && "failed".equals(String.valueOf(snapshot.get("status")));
            })
            .count());
        payload.put("event_count", events.size());
        payload.put("relations", relations.stream().map(this::toRelationPayload).toList());
        payload.put("routes", routes.stream().map(route -> toRoutePayload(route, routeDeliverySnapshots.get(route.getId()))).toList());
        return payload;
    }

    private Map<String, Object> buildIncidentDetails(Incident incident) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>(buildIncidentSummary(incident));
        payload.put("description", incident.getDescription() == null ? "" : incident.getDescription());
        payload.put("signal_type", incident.getSignalType() == null ? "" : incident.getSignalType());
        payload.put("signal_key", incident.getSignalKey() == null ? "" : incident.getSignalKey());
        payload.put("created_by", incident.getCreatedBy() == null ? "" : incident.getCreatedBy());
        Map<Long, Map<String, Object>> routeDeliverySnapshots = incidentRouteDeliveryOutboxService.loadLatestRouteDeliverySnapshots(incident.getId());
        payload.put("watchers", incidentWatcherRepository.findByIncidentIdOrderByWatcherIdentityAsc(incident.getId()).stream()
            .map(this::toWatcherPayload)
            .toList());
        payload.put("routes", incidentRouteRepository.findByIncidentIdOrderByCreatedAtAscIdAsc(incident.getId()).stream()
            .map(route -> toRoutePayload(route, routeDeliverySnapshots.get(route.getId())))
            .toList());
        payload.put("events", incidentEventRepository.findByIncidentIdOrderByCreatedAtAscIdAsc(incident.getId()).stream()
            .map(this::toEventPayload)
            .toList());
        return payload;
    }

    private Map<String, Object> toRelationPayload(IncidentRelation relation) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", relation.getId());
        payload.put("relation_type", relation.getRelationType());
        payload.put("relation_key", relation.getRelationKey());
        payload.put("relation_label", relation.getRelationLabel() == null ? "" : relation.getRelationLabel());
        payload.put("primary", Boolean.TRUE.equals(relation.getPrimaryRelation()));
        payload.put("created_at", relation.getCreatedAt() != null ? relation.getCreatedAt().toString() : null);
        payload.put("created_by", relation.getCreatedBy() == null ? "" : relation.getCreatedBy());
        return payload;
    }

    private Map<String, Object> toWatcherPayload(IncidentWatcher watcher) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", watcher.getId());
        payload.put("watcher_identity", watcher.getWatcherIdentity());
        payload.put("added_at", watcher.getAddedAt() != null ? watcher.getAddedAt().toString() : null);
        payload.put("added_by", watcher.getAddedBy() == null ? "" : watcher.getAddedBy());
        return payload;
    }

    private Map<String, Object> toRoutePayload(IncidentRoute route,
                                               Map<String, Object> deliverySnapshot) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", route.getId());
        payload.put("route_type", route.getRouteType());
        payload.put("route_target", route.getRouteTarget());
        payload.put("route_status", route.getRouteStatus() == null ? "" : route.getRouteStatus());
        payload.put("note", route.getNote() == null ? "" : route.getNote());
        payload.put("created_at", route.getCreatedAt() != null ? route.getCreatedAt().toString() : null);
        payload.put("updated_at", route.getUpdatedAt() != null ? route.getUpdatedAt().toString() : null);
        payload.put("delivery", deliverySnapshot == null ? Map.of() : deliverySnapshot);
        return payload;
    }

    private Map<String, Object> toEventPayload(IncidentEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", event.getId());
        payload.put("event_type", event.getEventType());
        payload.put("event_text", event.getEventText());
        payload.put("actor", event.getActor() == null ? "" : event.getActor());
        payload.put("payload_json", event.getPayloadJson() == null ? "" : event.getPayloadJson());
        payload.put("created_at", event.getCreatedAt() != null ? event.getCreatedAt().toString() : null);
        return payload;
    }

    private void syncRelations(Incident incident, Map<String, Object> payload, String actor) {
        List<RelationDraft> relations = extractRelations(payload);
        if (relations.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Incident должен быть связан хотя бы с одним dialog/task/object.");
        }
        incidentRelationRepository.deleteByIncidentId(incident.getId());
        OffsetDateTime now = OffsetDateTime.now();
        boolean primaryAssigned = false;
        for (RelationDraft relation : relations) {
            validateRelation(relation);
            IncidentRelation entity = new IncidentRelation();
            entity.setIncident(incident);
            entity.setRelationType(relation.relationType());
            entity.setRelationKey(relation.relationKey());
            entity.setRelationLabel(resolveRelationLabel(relation));
            entity.setMetadataJson(writeJson(relation.metadata()));
            boolean primary = !primaryAssigned && (relation.primary() || relations.size() == 1);
            entity.setPrimaryRelation(primary);
            entity.setCreatedAt(now);
            entity.setCreatedBy(normalizeNullableIdentity(actor));
            incidentRelationRepository.save(entity);
            if (primary) {
                primaryAssigned = true;
            }
        }
    }

    private void syncWatchers(Incident incident, Collection<String> watchers, String actor) {
        incidentWatcherRepository.deleteByIncidentId(incident.getId());
        OffsetDateTime now = OffsetDateTime.now();
        for (String watcher : sanitizeIdentities(watchers)) {
            IncidentWatcher entity = new IncidentWatcher();
            entity.setIncident(incident);
            entity.setWatcherIdentity(watcher);
            entity.setAddedAt(now);
            entity.setAddedBy(normalizeNullableIdentity(actor));
            incidentWatcherRepository.save(entity);
        }
    }

    private void syncRoutes(Incident incident, List<RouteDraft> routes, OffsetDateTime now) {
        List<IncidentRoute> existing = incidentRouteRepository.findByIncidentIdOrderByCreatedAtAscIdAsc(incident.getId());
        if (!existing.isEmpty()) {
            incidentRouteRepository.deleteAll(existing);
        }
        for (RouteDraft route : routes) {
            IncidentRoute entity = new IncidentRoute();
            entity.setIncident(incident);
            String routeType = normalizeRouteType(route.routeType());
            entity.setRouteType(routeType);
            entity.setRouteTarget(resolveRouteTarget(routeType, route.routeTarget()));
            entity.setRouteStatus(normalizeNullableText(route.routeStatus()));
            entity.setNote(normalizeNullableText(route.note()));
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            incidentRouteRepository.save(entity);
        }
    }

    private List<RelationDraft> extractRelations(Map<String, Object> payload) {
        List<RelationDraft> relations = new ArrayList<>();
        if (payload == null) {
            return relations;
        }
        Object rawRelations = payload.get("relations");
        if (rawRelations instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> rawMap)) {
                    continue;
                }
                relations.add(new RelationDraft(
                    normalizeRelationType(rawMap.get("relation_type")),
                    requiredText(rawMap.get("relation_key"), "Укажите ключ связи incident."),
                    normalizeNullableText(rawMap.get("relation_label")),
                    parseBoolean(rawMap.get("primary")),
                    normalizeMetadata(rawMap.get("metadata"))
                ));
            }
        }
        addScalarRelations(relations, payload.get("ticket_id"), "ticket", null);
        addScalarRelations(relations, payload.get("ticket_ids"), "ticket", null);
        addScalarRelations(relations, payload.get("task_id"), "task", null);
        addScalarRelations(relations, payload.get("task_ids"), "task", null);
        addScalarRelations(relations, payload.get("object_passport_id"), "object_passport", null);
        addScalarRelations(relations, payload.get("object_passport_ids"), "object_passport", null);
        LinkedHashMap<String, RelationDraft> unique = new LinkedHashMap<>();
        for (RelationDraft relation : relations) {
            String key = relation.relationType() + "::" + relation.relationKey();
            unique.putIfAbsent(key, relation);
        }
        return new ArrayList<>(unique.values());
    }

    private void addScalarRelations(List<RelationDraft> target, Object raw, String relationType, String relationLabel) {
        if (raw == null) {
            return;
        }
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                String relationKey = normalizeNullableText(item);
                if (StringUtils.hasText(relationKey)) {
                    target.add(new RelationDraft(relationType, relationKey, relationLabel, target.isEmpty(), Map.of()));
                }
            }
            return;
        }
        String relationKey = normalizeNullableText(raw);
        if (StringUtils.hasText(relationKey)) {
            target.add(new RelationDraft(relationType, relationKey, relationLabel, target.isEmpty(), Map.of()));
        }
    }

    private List<String> extractWatchers(Map<String, Object> payload) {
        Object raw = payload == null ? null : payload.get("watchers");
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            List<String> watchers = new ArrayList<>();
            for (Object item : list) {
                String watcher = normalizeNullableIdentity(item);
                if (watcher != null) {
                    watchers.add(watcher);
                }
            }
            return watchers;
        }
        String watcher = normalizeNullableIdentity(raw);
        return watcher == null ? List.of() : List.of(watcher);
    }

    private List<RouteDraft> extractRoutes(Map<String, Object> payload) {
        Object raw = payload == null ? null : payload.get("routes");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<RouteDraft> routes = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> rawMap)) {
                continue;
            }
            routes.add(new RouteDraft(
                normalizeNullableText(rawMap.get("route_type")),
                normalizeNullableText(rawMap.get("route_target")),
                normalizeNullableText(rawMap.get("route_status")),
                normalizeNullableText(rawMap.get("note"))
            ));
        }
        return routes;
    }

    private void validateRelation(RelationDraft relation) {
        if (relation == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректная связь incident.");
        }
        String relationType = normalizeRelationType(relation.relationType());
        String relationKey = requiredText(relation.relationKey(), "Укажите ключ связи incident.");
        switch (relationType) {
            case "ticket" -> {
                if (ticketRepository.findByIdTicketId(relationKey).isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Диалог " + relationKey + " не найден для incident.");
                }
            }
            case "task" -> {
                Long taskId = parseLong(relationKey);
                if (taskId == null || !taskRepository.existsById(taskId)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Задача " + relationKey + " не найдена для incident.");
                }
            }
            case "object_passport" -> {
                Long passportId = parseLong(relationKey);
                if (passportId == null || !objectPassportExists(passportId)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Паспорт объекта " + relationKey + " не найден для incident.");
                }
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неподдерживаемый relation type incident: " + relationType);
        }
    }

    private boolean objectPassportExists(Long passportId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM object_passports WHERE id = ?",
            Integer.class,
            passportId
        );
        return count != null && count > 0;
    }

    private String resolveRelationLabel(RelationDraft relation) {
        if (relation == null) {
            return null;
        }
        if (StringUtils.hasText(relation.relationLabel())) {
            return relation.relationLabel();
        }
        return switch (relation.relationType()) {
            case "ticket" -> "Dialog " + relation.relationKey();
            case "task" -> resolveTaskLabel(relation.relationKey());
            case "object_passport" -> resolveObjectPassportLabel(relation.relationKey());
            default -> relation.relationKey();
        };
    }

    private String resolveTaskLabel(String relationKey) {
        Long taskId = parseLong(relationKey);
        if (taskId == null) {
            return "Task " + relationKey;
        }
        Optional<Task> task = taskRepository.findById(taskId);
        if (task.isEmpty()) {
            return "Task " + relationKey;
        }
        Task entity = task.get();
        String displayNo = entity.getSeq() != null ? "DL_" + entity.getSeq() : "DL_" + entity.getId();
        if (StringUtils.hasText(entity.getTitle())) {
            return displayNo + ": " + entity.getTitle().trim();
        }
        return displayNo;
    }

    private String resolveObjectPassportLabel(String relationKey) {
        Long passportId = parseLong(relationKey);
        if (passportId == null) {
            return "Passport #" + relationKey;
        }
        Map<String, Object> payload = jdbcTemplate.query(
            """
                SELECT p.id, p.passport_number, o.name
                  FROM object_passports p
                  LEFT JOIN objects o ON o.id = p.object_id
                 WHERE p.id = ?
                 LIMIT 1
                """,
            rs -> {
                if (!rs.next()) {
                    return null;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("passport_number", rs.getString("passport_number"));
                row.put("name", rs.getString("name"));
                return row;
            },
            passportId
        );
        if (payload == null) {
            return "Passport #" + passportId;
        }
        String name = normalizeNullableText(payload.get("name"));
        if (StringUtils.hasText(name)) {
            return name;
        }
        String passportNumber = normalizeNullableText(payload.get("passport_number"));
        if (StringUtils.hasText(passportNumber)) {
            return passportNumber;
        }
        return "Passport #" + passportId;
    }

    private List<String> loadWatcherIdentities(Long incidentId) {
        return incidentWatcherRepository.findByIncidentIdOrderByWatcherIdentityAsc(incidentId).stream()
            .map(IncidentWatcher::getWatcherIdentity)
            .filter(StringUtils::hasText)
            .map(value -> value.trim().toLowerCase(Locale.ROOT))
            .distinct()
            .toList();
    }

    private void appendEvent(Incident incident,
                             String eventType,
                             String eventText,
                             Object payload,
                             String actor,
                             OffsetDateTime createdAt) {
        IncidentEvent event = new IncidentEvent();
        event.setIncident(incident);
        event.setEventType(normalizeEventType(eventType));
        event.setEventText(requiredText(eventText, "Укажите текст события incident."));
        event.setPayloadJson(writeJson(payload));
        event.setActor(normalizeNullableIdentity(actor));
        event.setCreatedAt(createdAt != null ? createdAt : OffsetDateTime.now());
        incidentEventRepository.save(event);
    }

    private void notifyIncidentParticipants(Incident incident, String event, String text, String actor) {
        Set<String> recipients = new LinkedHashSet<>();
        if (StringUtils.hasText(incident.getOwner())) {
            recipients.add(incident.getOwner().trim().toLowerCase(Locale.ROOT));
        }
        recipients.addAll(loadWatcherIdentities(incident.getId()));
        if (recipients.isEmpty()) {
            return;
        }
        notificationRoutingService.notify(
            "incidents",
            event,
            recipients,
            text,
            "/dialogs?incidentId=" + incident.getIncidentKey(),
            actor
        );
    }

    private Set<String> sanitizeIdentities(Collection<String> raw) {
        if (raw == null) {
            return Set.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (String item : raw) {
            String normalized = normalizeNullableIdentity(item);
            if (normalized != null) {
                values.add(normalized);
            }
        }
        return values;
    }

    private Map<String, Object> normalizeMetadata(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> payload = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                payload.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return payload;
        }
        return Map.of();
    }

    private String writeJson(Object payload) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Не удалось сериализовать incident payload", ex);
        }
    }

    private String normalizeStatus(Object raw, String fallback) {
        String normalized = normalizeNullableText(raw);
        if (!StringUtils.hasText(normalized)) {
            return fallback;
        }
        String lowered = normalized.toLowerCase(Locale.ROOT);
        if (!STATUSES.contains(lowered)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неподдерживаемый статус incident: " + normalized);
        }
        return lowered;
    }

    private String normalizeSeverity(Object raw, String fallback) {
        String normalized = normalizeNullableText(raw);
        if (!StringUtils.hasText(normalized)) {
            return fallback;
        }
        String lowered = normalized.toLowerCase(Locale.ROOT);
        if (!SEVERITIES.contains(lowered)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неподдерживаемая критичность incident: " + normalized);
        }
        return lowered;
    }

    private String normalizeRelationType(Object raw) {
        String normalized = normalizeNullableText(raw);
        if (!StringUtils.hasText(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите relation type incident.");
        }
        String lowered = normalized.toLowerCase(Locale.ROOT);
        if (!RELATION_TYPES.contains(lowered)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неподдерживаемый relation type incident: " + normalized);
        }
        return lowered;
    }

    private String normalizeEventType(Object raw) {
        String normalized = normalizeNullableText(raw);
        return StringUtils.hasText(normalized) ? normalized.toLowerCase(Locale.ROOT) : "comment";
    }

    private String normalizeRouteType(Object raw) {
        String normalized = normalizeNullableText(raw);
        if (!StringUtils.hasText(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите тип маршрута incident.");
        }
        String lowered = normalized.toLowerCase(Locale.ROOT);
        String canonical = switch (lowered) {
            case "operator", "operators", "all_operators" -> "all_operators";
            default -> lowered;
        };
        if (!ROUTE_TYPES.contains(canonical)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неподдерживаемый route type incident: " + normalized);
        }
        return canonical;
    }

    private String resolveRouteTarget(String routeType,
                                      Object rawRouteTarget) {
        if ("all_operators".equals(normalizeNullableText(routeType))) {
            return "all_operators";
        }
        return requiredText(rawRouteTarget, "Укажите цель маршрута incident.");
    }

    private boolean isResolvedStatus(String status) {
        return "resolved".equals(status) || "closed".equals(status);
    }

    private boolean parseBoolean(Object raw) {
        if (raw instanceof Boolean bool) {
            return bool;
        }
        if (raw instanceof Number number) {
            return number.intValue() != 0;
        }
        String normalized = normalizeNullableText(raw);
        return normalized != null && !"false".equalsIgnoreCase(normalized) && !"0".equals(normalized);
    }

    private Long parseLong(Object raw) {
        String value = normalizeNullableText(raw);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String requiredText(Object raw, String message) {
        String normalized = normalizeNullableText(raw);
        if (!StringUtils.hasText(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return normalized;
    }

    private String normalizeNullableText(Object raw) {
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw).trim();
        return value.isEmpty() ? null : value;
    }

    private String normalizeRequiredIdentity(Object raw, String message) {
        String normalized = normalizeNullableIdentity(raw);
        if (!StringUtils.hasText(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return normalized;
    }

    private String normalizeNullableIdentity(Object raw) {
        String value = normalizeNullableText(raw);
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private record RelationDraft(String relationType,
                                 String relationKey,
                                 String relationLabel,
                                 boolean primary,
                                 Map<String, Object> metadata) {
    }

    private record RouteDraft(String routeType,
                              String routeTarget,
                              String routeStatus,
                              String note) {
    }
}
