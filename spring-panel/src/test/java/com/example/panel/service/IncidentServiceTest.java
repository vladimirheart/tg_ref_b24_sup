package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.panel.entity.Incident;
import com.example.panel.entity.IncidentEvent;
import com.example.panel.entity.IncidentRelation;
import com.example.panel.entity.IncidentRoute;
import com.example.panel.entity.IncidentWatcher;
import com.example.panel.entity.Task;
import com.example.panel.entity.Ticket;
import com.example.panel.entity.TicketId;
import com.example.panel.repository.IncidentEventRepository;
import com.example.panel.repository.IncidentRelationRepository;
import com.example.panel.repository.IncidentRepository;
import com.example.panel.repository.IncidentRouteRepository;
import com.example.panel.repository.IncidentWatcherRepository;
import com.example.panel.repository.TaskRepository;
import com.example.panel.repository.TicketRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

class IncidentServiceTest {

    @Test
    void createIncidentBuildsCanonicalRelationsWatchersAndEvents() {
        IncidentRepository incidentRepository = mock(IncidentRepository.class);
        IncidentEventRepository incidentEventRepository = mock(IncidentEventRepository.class);
        IncidentRelationRepository incidentRelationRepository = mock(IncidentRelationRepository.class);
        IncidentWatcherRepository incidentWatcherRepository = mock(IncidentWatcherRepository.class);
        IncidentRouteRepository incidentRouteRepository = mock(IncidentRouteRepository.class);
        TicketRepository ticketRepository = mock(TicketRepository.class);
        TaskRepository taskRepository = mock(TaskRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        NotificationRoutingService notificationRoutingService = mock(NotificationRoutingService.class);
        IncidentRouteDeliveryOutboxService incidentRouteDeliveryOutboxService = mock(IncidentRouteDeliveryOutboxService.class);

        IncidentService service = new IncidentService(
                incidentRepository,
                incidentEventRepository,
                incidentRelationRepository,
                incidentWatcherRepository,
                incidentRouteRepository,
                ticketRepository,
                taskRepository,
                jdbcTemplate,
                new ObjectMapper(),
                notificationRoutingService,
                incidentRouteDeliveryOutboxService
        );

        AtomicLong relationIds = new AtomicLong(1);
        AtomicLong watcherIds = new AtomicLong(1);
        AtomicLong eventIds = new AtomicLong(1);
        List<IncidentRelation> savedRelations = new ArrayList<>();
        List<IncidentWatcher> savedWatchers = new ArrayList<>();
        List<IncidentEvent> savedEvents = new ArrayList<>();
        List<IncidentRoute> savedRoutes = new ArrayList<>();

        when(incidentRepository.save(any(Incident.class))).thenAnswer(invocation -> {
            Incident incident = invocation.getArgument(0);
            if (incident.getId() == null) {
                incident.setId(77L);
            }
            return incident;
        });
        when(incidentRepository.saveAndFlush(any(Incident.class))).thenAnswer(invocation -> {
            Incident incident = invocation.getArgument(0);
            if (incident.getId() == null) {
                incident.setId(77L);
            }
            return incident;
        });
        when(incidentRelationRepository.save(any(IncidentRelation.class))).thenAnswer(invocation -> {
            IncidentRelation relation = invocation.getArgument(0);
            relation.setId(relationIds.getAndIncrement());
            savedRelations.add(relation);
            return relation;
        });
        when(incidentWatcherRepository.save(any(IncidentWatcher.class))).thenAnswer(invocation -> {
            IncidentWatcher watcher = invocation.getArgument(0);
            watcher.setId(watcherIds.getAndIncrement());
            savedWatchers.add(watcher);
            return watcher;
        });
        when(incidentEventRepository.save(any(IncidentEvent.class))).thenAnswer(invocation -> {
            IncidentEvent event = invocation.getArgument(0);
            event.setId(eventIds.getAndIncrement());
            savedEvents.add(event);
            return event;
        });
        when(incidentRouteRepository.save(any(IncidentRoute.class))).thenAnswer(invocation -> {
            IncidentRoute route = invocation.getArgument(0);
            route.setId(1L);
            savedRoutes.add(route);
            return route;
        });

        when(incidentRelationRepository.findByIncidentIdOrderByPrimaryRelationDescCreatedAtAscIdAsc(77L))
                .thenAnswer(invocation -> new ArrayList<>(savedRelations));
        when(incidentWatcherRepository.findByIncidentIdOrderByWatcherIdentityAsc(77L))
                .thenAnswer(invocation -> new ArrayList<>(savedWatchers));
        when(incidentEventRepository.findByIncidentIdOrderByCreatedAtAscIdAsc(77L))
                .thenAnswer(invocation -> new ArrayList<>(savedEvents));
        when(incidentRouteRepository.findByIncidentIdOrderByCreatedAtAscIdAsc(77L))
                .thenAnswer(invocation -> new ArrayList<>(savedRoutes));
        doNothing().when(incidentRelationRepository).deleteByIncidentId(77L);
        doNothing().when(incidentWatcherRepository).deleteByIncidentId(77L);

        Ticket ticket = new Ticket();
        TicketId ticketId = new TicketId();
        ticketId.setTicketId("T-INC-1");
        ticketId.setUserId(1001L);
        ticket.setId(ticketId);
        when(ticketRepository.findByIdTicketId("T-INC-1")).thenReturn(Optional.of(ticket));

        Task task = new Task();
        task.setId(15L);
        task.setSeq(33L);
        task.setTitle("Follow up task");
        when(taskRepository.existsById(15L)).thenReturn(true);
        when(taskRepository.findById(15L)).thenReturn(Optional.of(task));

        when(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM object_passports WHERE id = ?",
                Integer.class,
                4L
        )).thenReturn(1);
        when(jdbcTemplate.query(any(String.class), any(ResultSetExtractor.class), eq(4L)))
                .thenReturn(new LinkedHashMap<>(Map.of("id", 4L, "name", "Object Passport 4")));

        Map<String, Object> result = service.createIncident(Map.of(
                "title", "Payment outage",
                "summary", "Checkout is degraded",
                "severity", "critical",
                "ticket_id", "T-INC-1",
                "task_id", 15,
                "object_passport_id", 4,
                "watchers", List.of("OpsLead", "FinanceLead")
        ), "Commander");

        @SuppressWarnings("unchecked")
        Map<String, Object> incident = (Map<String, Object>) result.get("incident");

        assertThat(result).containsEntry("success", true);
        assertThat(incident).containsEntry("incident_key", "INC-77");
        assertThat(incident).containsEntry("status", "open");
        assertThat(incident).containsEntry("severity", "critical");
        assertThat((List<?>) incident.get("relations")).hasSize(3);
        assertThat((List<?>) incident.get("watchers")).hasSize(2);
        assertThat((List<?>) incident.get("events")).hasSize(1);
        verify(notificationRoutingService).notify(eq("incidents"), eq("incident_created"), any(), any(), any(), eq("Commander"));
        verify(incidentRouteDeliveryOutboxService).enqueueIncidentRoutes(any(Incident.class), eq("incident_created"), eq("Incident создан"), any(), eq("Commander"));
    }

    @Test
    void signalIncidentDetailsKeepStructuredPayloadForWorkbench() {
        IncidentRepository incidentRepository = mock(IncidentRepository.class);
        IncidentEventRepository incidentEventRepository = mock(IncidentEventRepository.class);
        IncidentRelationRepository incidentRelationRepository = mock(IncidentRelationRepository.class);
        IncidentWatcherRepository incidentWatcherRepository = mock(IncidentWatcherRepository.class);
        IncidentRouteRepository incidentRouteRepository = mock(IncidentRouteRepository.class);
        TicketRepository ticketRepository = mock(TicketRepository.class);
        TaskRepository taskRepository = mock(TaskRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        NotificationRoutingService notificationRoutingService = mock(NotificationRoutingService.class);
        IncidentRouteDeliveryOutboxService incidentRouteDeliveryOutboxService = mock(IncidentRouteDeliveryOutboxService.class);

        IncidentService service = new IncidentService(
            incidentRepository,
            incidentEventRepository,
            incidentRelationRepository,
            incidentWatcherRepository,
            incidentRouteRepository,
            ticketRepository,
            taskRepository,
            jdbcTemplate,
            new ObjectMapper(),
            notificationRoutingService,
            incidentRouteDeliveryOutboxService
        );

        AtomicLong eventIds = new AtomicLong(1L);
        List<IncidentEvent> savedEvents = new ArrayList<>();
        Incident[] holder = new Incident[1];

        when(incidentRepository.findBySignalTypeAndSignalKeyOrderByUpdatedAtDescIdDesc("credential_rotation", "settings.netbox.api_token"))
            .thenReturn(List.of());
        when(incidentRepository.saveAndFlush(any(Incident.class))).thenAnswer(invocation -> {
            Incident incident = invocation.getArgument(0);
            if (incident.getId() == null) {
                incident.setId(91L);
            }
            holder[0] = incident;
            return incident;
        });
        when(incidentRepository.findById(91L)).thenAnswer(invocation -> Optional.of(holder[0]));
        when(incidentEventRepository.save(any(IncidentEvent.class))).thenAnswer(invocation -> {
            IncidentEvent event = invocation.getArgument(0);
            event.setId(eventIds.getAndIncrement());
            savedEvents.add(event);
            return event;
        });
        when(incidentRelationRepository.findByIncidentIdOrderByPrimaryRelationDescCreatedAtAscIdAsc(91L)).thenReturn(List.of());
        when(incidentWatcherRepository.findByIncidentIdOrderByWatcherIdentityAsc(91L)).thenReturn(List.of());
        when(incidentRouteRepository.findByIncidentIdOrderByCreatedAtAscIdAsc(91L)).thenReturn(List.of());
        when(incidentEventRepository.findByIncidentIdOrderByCreatedAtAscIdAsc(91L)).thenAnswer(invocation -> new ArrayList<>(savedEvents));

        service.openOrRefreshSignalIncident(
            "credential_rotation",
            "settings.netbox.api_token",
            "Критичный риск по секрету: NetBox sync API token",
            "Источник найден, но секрет пустой.",
            "Диагноз: Источник найден, но секрет пустой.",
            "critical",
            "credential_rotation_registry",
            Map.of(
                "signal_family", "credential_rotation",
                "incident_context_version", 1,
                "incident_reason", "Источник найден, но секрет пустой.",
                "incident_severity_policy", "Инцидент создаётся только при критичном состоянии.",
                "incident_severity_reason", "Источник найден, но секрет отсутствует.",
                "incident_next_action", "Заново сохраните секрет.",
                "incident_warning_handling", "Предупреждения остаются в аналитике.",
                "incident_escalates_to_workbench", true
            ),
            "system"
        );

        Map<String, Object> response = service.getIncident(91L);
        @SuppressWarnings("unchecked")
        Map<String, Object> incident = (Map<String, Object>) response.get("incident");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> events = (List<Map<String, Object>>) incident.get("events");

        @SuppressWarnings("unchecked")
        Map<String, Object> signalContext = (Map<String, Object>) incident.get("signal_context");


        assertThat(events).hasSize(1);
        assertThat(signalContext)
            .containsEntry("signal_type", "credential_rotation")
            .containsEntry("family", "credential_rotation")
            .containsEntry("context_version", 1)
            .containsEntry("reason", "Источник найден, но секрет пустой.")
            .containsEntry("severity_policy", "Инцидент создаётся только при критичном состоянии.")
            .containsEntry("severity_reason", "Источник найден, но секрет отсутствует.")
            .containsEntry("next_action", "Заново сохраните секрет.")
            .containsEntry("warning_handling", "Предупреждения остаются в аналитике.")
            .containsEntry("escalates_to_workbench", true);

        assertThat(String.valueOf(events.get(0).get("payload_json")))
            .contains("\"signal_family\":\"credential_rotation\"")
            .contains("\"incident_reason\":\"Источник найден, но секрет пустой.\"");
    }
}
