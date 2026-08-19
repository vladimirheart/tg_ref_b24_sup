package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.panel.entity.Incident;
import com.example.panel.entity.IncidentRoute;
import com.example.panel.repository.IncidentRouteRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class IncidentRouteDeliveryOutboxServiceTest {

    private JdbcTemplate jdbcTemplate;
    private IncidentRouteRepository incidentRouteRepository;
    private IncidentRouteDeliveryService incidentRouteDeliveryService;
    private IncidentRouteDeliveryOutboxService service;
    private IncidentRoute route;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:incident_route_outbox_" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "sa",
            ""
        ));
        jdbcTemplate.execute("""
                CREATE TABLE incidents (
                    id BIGINT PRIMARY KEY,
                    incident_key VARCHAR(120),
                    title VARCHAR(255)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE incident_routes (
                    id BIGINT PRIMARY KEY,
                    incident_id BIGINT,
                    route_type VARCHAR(120),
                    route_target VARCHAR(255),
                    route_status VARCHAR(64),
                    note CLOB,
                    created_at TIMESTAMP,
                    updated_at TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE incident_route_delivery_outbox (
                    event_id VARCHAR(120) PRIMARY KEY,
                    incident_id BIGINT NOT NULL,
                    route_id BIGINT NOT NULL,
                    event_type VARCHAR(120) NOT NULL,
                    route_type VARCHAR(120) NOT NULL,
                    route_target VARCHAR(255) NOT NULL,
                    message_text CLOB NOT NULL,
                    incident_url VARCHAR(255),
                    payload_json CLOB NOT NULL,
                    requested_by VARCHAR(120),
                    status VARCHAR(32) NOT NULL,
                    attempt_count INTEGER NOT NULL DEFAULT 0,
                    last_error CLOB,
                    available_at TIMESTAMP,
                    processing_started_at TIMESTAMP,
                    delivered_at TIMESTAMP,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);

        incidentRouteRepository = mock(IncidentRouteRepository.class);
        incidentRouteDeliveryService = mock(IncidentRouteDeliveryService.class);
        RuntimeCoordinationService runtimeCoordinationService = mock(RuntimeCoordinationService.class);
        doNothing().when(incidentRouteDeliveryService).deliver(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyMap());

        route = new IncidentRoute();
        route.setId(11L);
        route.setRouteType("user");
        route.setRouteTarget("opslead");
        route.setCreatedAt(OffsetDateTime.now());
        route.setUpdatedAt(OffsetDateTime.now());

        when(incidentRouteRepository.findByIncidentIdOrderByCreatedAtAscIdAsc(7L)).thenReturn(List.of(route));
        when(incidentRouteRepository.findById(11L)).thenReturn(Optional.of(route));
        when(incidentRouteRepository.save(org.mockito.ArgumentMatchers.any(IncidentRoute.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service = new IncidentRouteDeliveryOutboxService(
            jdbcTemplate,
            new ObjectMapper().findAndRegisterModules(),
            incidentRouteRepository,
            runtimeCoordinationService,
            incidentRouteDeliveryService
        );
    }

    @Test
    void enqueueAndDispatchDeliverRouteEvent() {
        Incident incident = new Incident();
        incident.setId(7L);
        incident.setIncidentKey("INC-7");
        incident.setTitle("Transport degradation");

        jdbcTemplate.update("INSERT INTO incidents(id, incident_key, title) VALUES (7, 'INC-7', 'Transport degradation')");
        jdbcTemplate.update("""
                INSERT INTO incident_routes(id, incident_id, route_type, route_target, route_status, created_at, updated_at)
                VALUES (11, 7, 'user', 'opslead', 'pending', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);

        int queued = service.enqueueIncidentRoutes(incident, "incident_created", "Incident created", Map.of("severity", "high"), "commander");
        service.dispatchBatch();

        assertThat(queued).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM incident_route_delivery_outbox WHERE incident_id = 7 AND route_id = 11",
            String.class
        )).isEqualTo("delivered");
        assertThat(route.getRouteStatus()).isEqualTo("delivered");
    }
}
