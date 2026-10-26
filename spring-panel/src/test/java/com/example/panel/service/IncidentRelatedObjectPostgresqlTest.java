package com.example.panel.service;

import com.example.panel.entity.*;
import com.example.panel.repository.*;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IncidentRelatedObjectPostgresqlTest {
    private static void login(String username, String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(username, "unused",
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }
    @AfterEach void logout() { SecurityContextHolder.clearContext(); }

    static class Fixture {
        final JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplate("incident_objects_r19");
        final BusinessAccessService scope = mock(BusinessAccessService.class);
        final IncidentRelatedObjectService service = new IncidentRelatedObjectService(jdbc, scope);
        Fixture() {
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/postgresql/V82__incident_external_objects.sql")).execute(jdbc.getDataSource());
            jdbc.execute("CREATE TABLE users(id bigint, username text)");
            jdbc.execute("INSERT INTO users VALUES (11, 'Admin'), (12, 'admin'), (13, ' Admin ')");
            jdbc.execute("CREATE TABLE tickets(user_id bigint, ticket_id text, business_id bigint, is_general boolean, status text, created_at timestamptz)");
            jdbc.execute("CREATE TABLE messages(ticket_id text UNIQUE, problem text, client_name text, created_at timestamptz)");
            jdbc.execute("CREATE TABLE tasks(id bigint, seq bigint, title text, status text, created_at timestamptz)");
            jdbc.execute("CREATE TABLE objects(id bigint, name text, address text)");
            jdbc.execute("CREATE TABLE object_passports(id bigint, object_id bigint, passport_number text, created_at timestamptz)");
        }
        void ticket(String key, Long business, boolean general) {
            jdbc.update("INSERT INTO tickets VALUES (1,?,?,?,'open','2026-10-26 10:00:00Z')", key, business, general);
        }
        String external() {
            return (String) ((Map<?, ?>) service.createExternal(Map.of("name", "Внешний сервер", "external_number", "EXT-7",
                    "external_url", "https://example.test/item/7", "description", "Отдельный объект" )).get("item")).get("value");
        }
    }

    @Test void searchUsesHumanNumbersTitlesLiteralQueryAndOpaqueIds() {
        var f = new Fixture(); login("Admin", "PAGE_DIALOGS", "PAGE_TASKS", "PAGE_OBJECT_PASSPORTS");
        String exact = " T/1?&%# Ю "; f.ticket(exact, null, false);
        f.jdbc.update("INSERT INTO messages VALUES (?, 'Не работает касса', 'Анна', '2026-10-26 10:00:00Z')", exact);
        f.jdbc.update("INSERT INTO tasks VALUES (9007199254740993,42,'Заменить кабель','Новая',now())");
        f.jdbc.update("INSERT INTO objects VALUES (1, 'Ресторан 7 Гвардейская', 'Волгоград')");
        f.jdbc.update("INSERT INTO object_passports VALUES (17,1,'PS-17',now())");
        assertThat(f.service.search("ticket", "20261026-001", 20).toString()).contains(exact, "касса", "Анна");
        assertThat(f.service.search("ticket", "%", 20).toString()).contains(exact);
        assertThat(f.service.search("ticket", "' OR true --", 20).get("items")).isEqualTo(List.of());
        assertThat(f.service.search("task", "dl_42", 20).toString()).contains("9007199254740993", "Заменить кабель");
        assertThat(f.service.search("object_passport", "Гвардейская", 20).toString()).contains("PS-17", "Волгоград");
        f.service.validateSelection("ticket", exact);
        assertThatThrownBy(() -> f.service.validateSelection("ticket", exact.strip())).isInstanceOf(ResponseStatusException.class);
        f.service.validateSelection("task", "9007199254740993");
    }

    @Test void globalAdminDoesNotBypassScopeAndCollisionWithGeneralIsNeverResolved() {
        var f = new Fixture(); login("Admin", "ROLE_ADMIN", "PAGE_DIALOGS");
        f.ticket("legacy", null, false); f.ticket("allowed", 9L, false); f.ticket("other", 10L, false);
        f.ticket("collision", 9L, false); f.ticket("collision", 10L, false);
        f.ticket("general-collision", null, false); f.ticket("general-collision", null, true); f.ticket("general-only", null, true);
        when(f.scope.resolveAccessibleBusinessIds(11L)).thenReturn(Set.of(9L));
        String result = f.service.search("ticket", "", 30).toString();
        assertThat(result).contains("legacy", "allowed").doesNotContain("other", "collision", "general-only");
        for (String missing : List.of("other", "collision", "general-collision", "general-only", "absent")) {
            assertThatThrownBy(() -> f.service.validateSelection("ticket", missing)).isInstanceOf(ResponseStatusException.class);
        }
        f.service.validateSelection("ticket", "allowed");
        login("admin", "ROLE_ADMIN", "PAGE_DIALOGS");
        assertThat(f.service.search("ticket", "allowed", 20).get("items")).isEqualTo(List.of());
        verify(f.scope).resolveAccessibleBusinessIds(12L);
        login(" Admin ", "ROLE_ADMIN", "PAGE_DIALOGS");
        f.service.search("ticket", "", 20); verify(f.scope).resolveAccessibleBusinessIds(13L);
    }

    @Test void pageAndGlobalAdminPermissionsAreCheckedByTheServiceNotOnlyController() {
        var f = new Fixture(); login("Admin", "ROLE_ADMIN");
        assertThatThrownBy(() -> f.service.capabilities()).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> f.external()).isInstanceOf(AccessDeniedException.class);
        login("Admin", "PAGE_TASKS", "BUSINESS_ADMIN", "ALL_BUSINESSES");
        assertThat(f.service.capabilities().get("types")).isEqualTo(List.of("task", "external_object"));
        assertThat(f.service.capabilities().get("can_create_external")).isEqualTo(false);
        assertThatThrownBy(() -> f.external()).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> f.service.search("ticket", "", 20)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> f.service.validateSelection("object_passport", "1")).isInstanceOf(AccessDeniedException.class);
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM incident_external_objects", Integer.class)).isZero();
        login("Admin", "ROLE_ADMIN", "PAGE_TASKS"); String id = f.external();
        login("reader", "PAGE_DIALOGS");
        assertThat(f.service.search("external_object", "EXT-7", 20).toString()).contains(id, "Внешний сервер");
        f.service.validateSelection("external_object", id);
    }

    @Test void catalogValidatesInputDoesNotFetchLinksAndPreservesExactActorAndLargeIdentity() {
        var f = new Fixture(); login(" Admin ", "ROLE_ADMIN", "PAGE_DIALOGS");
        f.jdbc.execute("ALTER TABLE incident_external_objects ALTER COLUMN id RESTART WITH 9007199254740993");
        String id = f.external(); assertThat(id).isEqualTo("9007199254740993");
        assertThat(f.jdbc.queryForObject("SELECT created_by FROM incident_external_objects", String.class)).isEqualTo(" Admin ");
        for (Map<String, Object> bad : List.<Map<String, Object>>of(Map.of("name", " "), Map.of("name", "\u00a0"), Map.of("name", "x".repeat(201)), Map.of("name", List.of("x")),
                Map.of("name", "x", "external_url", "javascript:alert(1)"), Map.of("name", "x", "external_url", "https://user:secret@example.test"),
                Map.of("name", "x", "external_url", "/relative"), Map.of("name", "x", "description", "x".repeat(5001)))) {
            assertThatThrownBy(() -> f.service.createExternal(bad)).isInstanceOf(ResponseStatusException.class);
        }
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM incident_external_objects", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> f.service.search(null, "", 1)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> f.service.search("external_object", "x".repeat(201), 1)).isInstanceOf(ResponseStatusException.class);
    }

    @Test void manualIncidentReusesCatalogCanonicalLabelAndRejectsForgedSelectionTransactionally() {
        var f = new Fixture(); login("Admin", "ROLE_ADMIN", "PAGE_DIALOGS"); String id = f.external();
        var registry = new StandardServiceRegistryBuilder().applySetting("hibernate.connection.datasource", f.jdbc.getDataSource())
                .applySetting("hibernate.hbm2ddl.auto", "create-drop").build();
        try (var factory = new MetadataSources(registry).addAnnotatedClass(Incident.class).addAnnotatedClass(IncidentEvent.class)
                .addAnnotatedClass(IncidentRelation.class).addAnnotatedClass(IncidentWatcher.class).addAnnotatedClass(IncidentRoute.class)
                .buildMetadata().buildSessionFactory()) {
            var repos = new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(factory));
            var target = new IncidentService(repos.getRepository(IncidentRepository.class), repos.getRepository(IncidentEventRepository.class),
                    repos.getRepository(IncidentRelationRepository.class), repos.getRepository(IncidentWatcherRepository.class), repos.getRepository(IncidentRouteRepository.class),
                    mock(TicketRepository.class), mock(TaskRepository.class), f.jdbc, new ObjectMapper(), mock(NotificationRoutingService.class),
                    mock(IncidentRouteDeliveryOutboxService.class), f.service);
            var proxy = new ProxyFactory(target);
            proxy.addAdvice(new TransactionInterceptor(new JpaTransactionManager(factory), new AnnotationTransactionAttributeSource()));
            var service = (IncidentService) proxy.getProxy();
            login("reader", "PAGE_DIALOGS");
            Map<String, Object> payload = Map.of("title", "Ручной инцидент", "relations", List.of(Map.of("relation_type", "external_object", "relation_key", id, "relation_label", "Подмена")));
            for (int n = 0; n < 2; n++) {
                Map<?, ?> incident = (Map<?, ?>) service.createIncident(payload, "reader").get("incident");
                assertThat(incident.get("relations").toString()).contains("Внешний сервер · EXT-7").doesNotContain("Подмена");
            }
            assertThatThrownBy(() -> service.createIncident(Map.of("title", "Подмена", "relations", List.of(Map.of("relation_type", "external_object", "relation_key", "404"))), "reader"))
                    .isInstanceOf(ResponseStatusException.class);
            assertThat(f.jdbc.queryForObject("SELECT count(*) FROM incidents", Integer.class)).isEqualTo(2);
            assertThat(f.jdbc.queryForObject("SELECT count(*) FROM incident_relations WHERE relation_key=?", Integer.class, id)).isEqualTo(2);
            assertThatThrownBy(() -> service.updateIncident(1L, Map.of("relations", List.of(Map.of("relation_type", "external_object", "relation_key", "404"))), "reader"))
                    .isInstanceOf(ResponseStatusException.class);
            assertThat(f.jdbc.queryForObject("SELECT relation_key FROM incident_relations WHERE incident_id=1", String.class)).isEqualTo(id);
            assertThat(f.jdbc.queryForObject("SELECT count(*) FROM incident_external_objects", Integer.class)).isEqualTo(1);
        } finally { StandardServiceRegistryBuilder.destroy(registry); }
    }

    @Test void flywayUpgradeFrom81IsAdditiveAndRepeatStartupIsNoOp() {
        JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplate("incident_v82_upgrade");
        var configuration = Flyway.configure().dataSource(jdbc.getDataSource()).locations("classpath:db/migration/postgresql");
        configuration.target("81").load().migrate();
        String before = jdbc.queryForObject("SELECT string_agg(version || ':' || checksum::text, ',' ORDER BY installed_rank) FROM flyway_schema_history", String.class);
        configuration.target("82").load().migrate();
        assertThat(jdbc.queryForObject("SELECT string_agg(version || ':' || checksum::text, ',' ORDER BY installed_rank) FROM flyway_schema_history WHERE version <> '82'", String.class)).isEqualTo(before);
        assertThat(configuration.load().migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM incident_external_objects", Integer.class)).isZero();
        // Also compile and execute every picker query against the actual complete migration chain,
        // not only the small fixtures above. Use the real canonical scope resolver here.
        login("Admin", "ROLE_ADMIN", "PAGE_DIALOGS", "PAGE_TASKS", "PAGE_OBJECT_PASSPORTS");
        var lookup = new IncidentRelatedObjectService(jdbc, new BusinessAccessService(jdbc));
        for (String type : List.of("ticket", "task", "object_passport", "external_object")) {
            assertThat(lookup.search(type, "", 5).get("items")).isEqualTo(List.of());
        }
    }
}
