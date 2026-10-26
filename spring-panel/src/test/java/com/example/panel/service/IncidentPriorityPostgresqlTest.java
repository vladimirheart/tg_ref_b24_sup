package com.example.panel.service;

import com.example.panel.entity.Incident;
import com.example.panel.entity.IncidentEvent;
import com.example.panel.repository.*;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual Spring Data queries + Hibernate locks + PostgreSQL transactions in an isolated schema. */
class IncidentPriorityPostgresqlTest {
    static class Fixture implements AutoCloseable {
        final JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplate("incident_priority_r15");
        final StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
            .applySetting("hibernate.connection.datasource", jdbc.getDataSource())
            .applySetting("hibernate.hbm2ddl.auto", "create-drop").build();
        final SessionFactory factory = new MetadataSources(registry).addAnnotatedClass(Incident.class)
            .addAnnotatedClass(IncidentEvent.class).buildMetadata().buildSessionFactory();
        final JpaRepositoryFactory repositories = new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(factory));
        final IncidentRepository incidents = repositories.getRepository(IncidentRepository.class);
        final IncidentEventRepository events = repositories.getRepository(IncidentEventRepository.class);
        IncidentService service(IncidentRepository incidentPort, IncidentEventRepository eventPort) {
            var target = new IncidentService(incidentPort, eventPort, mock(IncidentRelationRepository.class),
                mock(IncidentWatcherRepository.class), mock(IncidentRouteRepository.class), mock(TicketRepository.class),
                mock(TaskRepository.class), jdbc, new ObjectMapper(), mock(NotificationRoutingService.class),
                mock(IncidentRouteDeliveryOutboxService.class), mock(IncidentRelatedObjectService.class));
            var proxy = new ProxyFactory(target);
            proxy.addAdvice(new TransactionInterceptor(new JpaTransactionManager(factory), new AnnotationTransactionAttributeSource()));
            return (IncidentService) proxy.getProxy();
        }
        IncidentService service() { return service(incidents, events); }
        long signal(String key, String severity) {
            var response = service().openOrRefreshSignalIncident("credential_rotation", key,
                "Настройка прокси", "Проверка настройки", "Описание", severity, "registry",
                Map.of("signal_family", "credential_rotation", "last_status", "missing_secret"), "monitoring");
            return ((Number) ((Map<?, ?>) response.get("incident")).get("id")).longValue();
        }
        Map<?, ?> details(long id) { return (Map<?, ?>) service().getIncident(id).get("incident"); }
        String severity(long id) { return jdbc.queryForObject("select severity from incidents where id=?", String.class, id); }
        public void close() { factory.close(); StandardServiceRegistryBuilder.destroy(registry); }
    }

    @Test void manualPrioritySurvivesReloadNewServiceAndRepeatedAutomaticRefresh() {
        try (var f = new Fixture()) {
            long id = f.signal("channel.3.network_route.proxy.password", "critical");
            f.service().updateIncident(id, Map.of("severity", "low"), "operator");
            assertThat(f.severity(id)).isEqualTo("low");
            assertThat(f.details(id).get("severity_mode")).isEqualTo("manual");
            assertThat(f.signal("channel.3.network_route.proxy.password", "critical")).isEqualTo(id);
            assertThat(f.severity(id)).isEqualTo("low");
            f.service().updateIncident(id, Map.of("severity", "medium"), "operator");
            f.signal("channel.3.network_route.proxy.password", "high");
            assertThat(f.severity(id)).isEqualTo("medium");
            assertThat(f.details(id).get("severity")).isEqualTo("medium");
            assertThat(f.jdbc.queryForObject("select count(*) from incident_events where event_type='priority_override'", Integer.class)).isEqualTo(2);
            long automatic = f.signal("untouched", "critical");
            f.signal("untouched", "medium");
            assertThat(f.severity(automatic)).isEqualTo("medium");
            assertThat(f.details(automatic).get("severity_mode")).isEqualTo("automatic");
        }
    }

    @Test void priorityAndEventRollbackTogetherAndCommentCannotForgeOverride() {
        try (var f = new Fixture()) {
            long id = f.signal("rollback", "critical");
            for (String invalid : List.of("", "   ", "urgent")) {
                assertThatThrownBy(() -> f.service().updateIncident(id, Map.of("severity", invalid), "operator"))
                    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
            }
            assertThatThrownBy(() -> f.service().addIncidentEvent(id, Map.of("event_type", "PRIORITY_OVERRIDE",
                "event_text", "Подмена", "payload", Map.of("severity", "low")), "operator"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
            assertThatThrownBy(() -> f.service().appendSignalEvent("credential_rotation", "rollback", "priority_override", "Подмена", Map.of("severity", "low"), "operator"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
            var failing = mock(IncidentEventRepository.class, org.mockito.AdditionalAnswers.delegatesTo(f.events));
            doThrow(new IllegalStateException("Injected priority event failure")).when(failing)
                .save(argThat((IncidentEvent event) -> "priority_override".equals(event.getEventType())));
            assertThatThrownBy(() -> f.service(f.incidents, failing).updateIncident(id, Map.of("severity", "low"), "operator"))
                .isInstanceOf(IllegalStateException.class);
            assertThat(f.severity(id)).isEqualTo("critical");
            assertThat(f.jdbc.queryForObject("select count(*) from incident_events where event_type='priority_override'", Integer.class)).isZero();
        }
    }

    @Test void automaticRefreshWaitsForManualPriorityTransactionAndReadsCommittedOverride() throws Exception {
        try (var f = new Fixture()) {
            long id = f.signal("concurrent", "critical");
            var locked = new CountDownLatch(1); var release = new CountDownLatch(1); var started = new CountDownLatch(1);
            var guarded = mock(IncidentRepository.class, org.mockito.AdditionalAnswers.delegatesTo(f.incidents));
            doAnswer(call -> {
                var value = f.incidents.findByIdForUpdate(id); locked.countDown();
                if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test lock release timed out");
                return value;
            }).when(guarded).findByIdForUpdate(id);
            var pool = Executors.newFixedThreadPool(2);
            try {
                Future<?> manual = pool.submit(() -> f.service(guarded, f.events).updateIncident(id, Map.of("severity", "low"), "operator"));
                assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
                Future<?> automatic = pool.submit(() -> { started.countDown(); f.signal("concurrent", "critical"); });
                assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> automatic.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown(); manual.get(10, TimeUnit.SECONDS); automatic.get(10, TimeUnit.SECONDS);
                assertThat(f.severity(id)).isEqualTo("low");
            } finally { release.countDown(); pool.shutdownNow(); }
        }
    }
}
