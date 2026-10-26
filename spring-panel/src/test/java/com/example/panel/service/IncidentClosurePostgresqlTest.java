package com.example.panel.service;

import com.example.panel.entity.Incident;
import com.example.panel.entity.IncidentEvent;
import com.example.panel.repository.*;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real isolated PostgreSQL transaction; repository ports record writes, never the working DB. */
class IncidentClosurePostgresqlTest {
    @Test void statusAndResolutionPersistTogetherAndRollbackOnEventFailure() {
        var jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplate("incident_closure_r12");
        jdbc.execute("CREATE TABLE closure_incidents(id bigint primary key, status text not null)");
        jdbc.execute("CREATE TABLE closure_events(id bigserial primary key, event_type text, event_text text)");
        jdbc.update("INSERT INTO closure_incidents VALUES(731, 'open')");
        var incidents = mock(IncidentRepository.class);
        var events = mock(IncidentEventRepository.class);
        var relations = mock(IncidentRelationRepository.class);
        var watchers = mock(IncidentWatcherRepository.class);
        var routes = mock(IncidentRouteRepository.class);
        when(incidents.findById(731L)).thenAnswer(call -> {
            Incident value = new Incident(); value.setId(731L); value.setIncidentKey("INC-731");
            value.setTitle("Проверка"); value.setSeverity("high");
            value.setStatus(jdbc.queryForObject("SELECT status FROM closure_incidents WHERE id=731", String.class));
            return Optional.of(value);
        });
        when(incidents.findByIdForUpdate(731L)).thenAnswer(call -> incidents.findById(731L));
        when(incidents.save(any(Incident.class))).thenAnswer(call -> {
            Incident value = call.getArgument(0);
            jdbc.update("UPDATE closure_incidents SET status=? WHERE id=?", value.getStatus(), value.getId()); return value;
        });
        when(events.save(any(IncidentEvent.class))).thenAnswer(call -> {
            IncidentEvent event = call.getArgument(0);
            jdbc.update("INSERT INTO closure_events(event_type,event_text) VALUES (?,?)", event.getEventType(), event.getEventText()); return event;
        });
        var target = new IncidentService(incidents, events, relations, watchers, routes,
            mock(TicketRepository.class), mock(TaskRepository.class), jdbc, new ObjectMapper(),
            mock(NotificationRoutingService.class), mock(IncidentRouteDeliveryOutboxService.class), mock(IncidentRelatedObjectService.class));
        var factory = new ProxyFactory(target);
        factory.addAdvice(new TransactionInterceptor(new JdbcTransactionManager(jdbc.getDataSource()), new AnnotationTransactionAttributeSource()));
        var service = (IncidentService) factory.getProxy();

        assertThatThrownBy(() -> service.updateIncident(731L, Map.of("status", "closed"), "operator"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM closure_incidents WHERE id=731", String.class)).isEqualTo("open");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM closure_events", Integer.class)).isZero();
        for (String invalid : java.util.List.of("", "   ", "\u00a0", "x".repeat(5001))) {
            assertThatThrownBy(() -> service.updateIncident(731L, Map.of("status", "resolved", "resolution_comment", invalid), "operator"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        }

        String comment = "Заменён кабель, соединение восстановлено. Проверена доставка.";
        service.updateIncident(731L, Map.of("status", "closed", "resolution_comment", comment), "operator");
        assertThat(jdbc.queryForObject("SELECT status FROM closure_incidents WHERE id=731", String.class)).isEqualTo("closed");
        assertThat(jdbc.queryForObject("SELECT event_text FROM closure_events WHERE event_type='resolution'", String.class)).isEqualTo(comment);
        assertThat(((Map<?, ?>) service.getIncident(731L).get("incident")).get("status")).isEqualTo("closed");

        jdbc.update("UPDATE closure_incidents SET status='open' WHERE id=731");
        when(events.save(any(IncidentEvent.class))).thenThrow(new IllegalStateException("Injected event write failure"));
        assertThatThrownBy(() -> service.updateIncident(731L, Map.of("status", "resolved", "resolution_comment", "Восстановлено"), "operator"))
            .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM closure_incidents WHERE id=731", String.class)).isEqualTo("open");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM closure_events WHERE event_type='resolution'", Integer.class)).isEqualTo(1);
    }
}
