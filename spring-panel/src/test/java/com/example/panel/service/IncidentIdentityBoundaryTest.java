package com.example.panel.service;

import com.example.panel.entity.*;
import com.example.panel.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IncidentIdentityBoundaryTest {
    static Stream<String> identities() {
        return Stream.of(" Lead", "Lead ", " Lead ", "\tLead\t", "Lead\r\n", " null ",
            "9007199254740993", "Lead,Ops;<b>x</b>");
    }
    private static String key(String value) { return value.toLowerCase(Locale.ROOT); }

    @ParameterizedTest @MethodSource("identities")
    void createKeepsDistinctOwnerWatchersAndAuditIdentities(String raw) {
        var f = new Fixture();
        f.create(Map.of("owner", raw, "watchers", List.of(raw, "lead", key(raw))), raw);
        assertThat(f.current.getOwner()).isEqualTo(key(raw));
        assertThat(f.current.getCreatedBy()).isEqualTo(key(raw));
        assertThat(f.watchers).extracting(IncidentWatcher::getWatcherIdentity).containsExactly(key(raw), "lead");
        assertThat(f.watchers).extracting(IncidentWatcher::getAddedBy).containsOnly(key(raw));
        assertThat(f.relations).extracting(IncidentRelation::getCreatedBy).containsOnly(key(raw));
        assertThat(f.events).extracting(IncidentEvent::getActor).containsOnly(key(raw));
        verify(f.notifications).notify(eq("incidents"), eq("incident_created"), eq(Set.of(key(raw), "lead")), anyString(), anyString(), eq(raw));
        assertThat(f.details().get("owner")).isEqualTo(key(raw));
    }

    @ParameterizedTest @MethodSource("identities")
    void updateKeepsLiteralSelectorsAndCurrentEventActor(String raw) {
        var f = new Fixture(); f.create(Map.of("owner", "before", "watchers", List.of("before")), "original");
        clearInvocations(f.notifications);
        f.service.updateIncident(77L, Map.of("owner", raw, "watchers", List.of(raw, "lead")), raw);
        assertThat(f.current.getOwner()).isEqualTo(key(raw));
        assertThat(f.current.getCreatedBy()).isEqualTo("original");
        assertThat(f.watchers).extracting(IncidentWatcher::getWatcherIdentity).containsExactly(key(raw), "lead");
        assertThat(f.watchers).extracting(IncidentWatcher::getAddedBy).containsOnly(key(raw));
        assertThat(f.events.get(f.events.size()-1).getActor()).isEqualTo(key(raw));
        verify(f.notifications).notify(eq("incidents"), anyString(), eq(Set.of(key(raw), "lead")), anyString(), anyString(), eq(raw));
    }

    @ParameterizedTest @MethodSource("identities")
    void storedOwnerAndWatchersReachNotificationRoutingWithoutPlainAccountSubstitution(String raw) {
        var f = new Fixture(); f.seed(); f.current.setOwner(raw); f.storedWatcher(raw); f.storedWatcher("lead");
        f.service.addIncidentEvent(77L, Map.of("event_text", "New observation"), raw);
        verify(f.notifications).notify(eq("incidents"), eq("incident_event"), eq(Set.of(key(raw), "lead")), anyString(), anyString(), eq(raw));
        assertThat(f.events.get(0).getActor()).isEqualTo(key(raw));
        assertThat(f.current.getOwner()).isEqualTo(raw);
        assertThat(f.watchers).extracting(IncidentWatcher::getWatcherIdentity).containsExactly(raw, "lead");
    }

    @ParameterizedTest @MethodSource("identities")
    void addAndRemoveWatcherAffectOnlyTheLiteralAccount(String raw) {
        var f = new Fixture(); f.seed(); f.storedWatcher("lead");
        f.service.addWatcher(77L, raw, raw);
        assertThat(f.watchers).extracting(IncidentWatcher::getWatcherIdentity).containsExactly("lead", key(raw));
        f.service.removeWatcher(77L, raw, raw);
        assertThat(f.watchers).extracting(IncidentWatcher::getWatcherIdentity).containsExactly("lead");
        assertThat(f.events).extracting(IncidentEvent::getActor).containsOnly(key(raw));
        assertThat(f.events).extracting(IncidentEvent::getEventType).containsExactly("watcher_added", "watcher_removed");
    }

    @ParameterizedTest @MethodSource("identities")
    void inlineUserRouteRetainsTheSameLiteralTarget(String raw) {
        var f = new Fixture();
        f.create(Map.of("routes", List.of(Map.of("route_type", " USER ", "route_target", raw))), "operator");
        assertThat(f.routes).extracting(IncidentRoute::getRouteTarget).containsExactly(raw);
        assertThat(f.routes).extracting(IncidentRoute::getRouteType).containsExactly("user");
    }

    @ParameterizedTest @MethodSource("identities")
    void addAndUpdateUserRouteKeepLiteralTargetAndActor(String raw) {
        var f = new Fixture(); f.seed();
        f.service.addRoute(77L, Map.of("route_type", "user", "route_target", raw), raw);
        assertThat(f.routes.get(0).getRouteTarget()).isEqualTo(raw);
        f.service.updateRoute(77L, 11L, Map.of("route_type", " USER ", "route_target", raw + "X"), raw);
        assertThat(f.routes.get(0).getRouteTarget()).isEqualTo(raw + "X");
        assertThat(f.events).extracting(IncidentEvent::getActor).containsOnly(key(raw));
        verify(f.outbox, times(2)).enqueueRouteReplay(f.current, 11L, raw);
    }

    @Test void blankSelectorsAndExistingCaseFoldingRemainCompatible() {
        var f = new Fixture();
        f.create(Map.of("owner", " \t\r\n ", "watchers", List.of(" ", "Ops", "OPS")), " \t ");
        assertThat(f.current.getOwner()).isNull();assertThat(f.current.getCreatedBy()).isNull();
        assertThat(f.watchers).extracting(IncidentWatcher::getWatcherIdentity).containsExactly("ops");
        assertThat(f.events.get(0).getActor()).isNull();
        assertThat(assertThrows(ResponseStatusException.class, () -> f.service.addWatcher(77L, " \t ", "operator")).getStatusCode().value()).isEqualTo(400);
    }

    @Test void nonUserRouteGrammarAndDisplayTextNormalizationAreUnchanged() {
        var f = new Fixture();
        f.create(Map.of("owner", "operator", "summary", " summary ", "routes", List.of(
            Map.of("route_type", "users", "route_target", " One, Two;Three\n "),
            Map.of("route_type", "department", "route_target", " Support "),
            Map.of("route_type", "webhook", "route_target", " https://invalid.example/hook "),
            Map.of("route_type", "operators", "route_target", " ignored "))), "operator");
        assertThat(f.current.getTitle()).isEqualTo("Fixture");assertThat(f.current.getSummary()).isEqualTo("summary");
        assertThat(f.routes).extracting(IncidentRoute::getRouteTarget).containsExactly("One, Two;Three", "Support", "https://invalid.example/hook", "all_operators");
        assertThat(assertThrows(ResponseStatusException.class, () -> f.service.addRoute(77L,
            Map.of("route_type", "user", "route_target", " \t "), "operator")).getStatusCode().value()).isEqualTo(400);
    }

    static final class Fixture {
        final IncidentRepository incidents = mock(IncidentRepository.class);
        final IncidentEventRepository eventRepo = mock(IncidentEventRepository.class);
        final IncidentRelationRepository relationRepo = mock(IncidentRelationRepository.class);
        final IncidentWatcherRepository watcherRepo = mock(IncidentWatcherRepository.class);
        final IncidentRouteRepository routeRepo = mock(IncidentRouteRepository.class);
        final NotificationRoutingService notifications = mock(NotificationRoutingService.class);
        final IncidentRouteDeliveryOutboxService outbox = mock(IncidentRouteDeliveryOutboxService.class);
        final List<IncidentWatcher> watchers = new ArrayList<>();
        final List<IncidentEvent> events = new ArrayList<>();
        final List<IncidentRelation> relations = new ArrayList<>();
        final List<IncidentRoute> routes = new ArrayList<>();
        Incident current;
        final IncidentService service;
        Fixture() {
            var tasks = mock(TaskRepository.class);when(tasks.existsById(15L)).thenReturn(true);
            service = new IncidentService(incidents,eventRepo,relationRepo,watcherRepo,routeRepo,mock(TicketRepository.class),tasks,
                mock(JdbcTemplate.class),new ObjectMapper(),notifications,outbox,mock(IncidentRelatedObjectService.class));
            when(incidents.save(any())).thenAnswer(i -> store(i.getArgument(0)));
            when(incidents.saveAndFlush(any())).thenAnswer(i -> store(i.getArgument(0)));
            when(incidents.findById(77L)).thenAnswer(i -> Optional.ofNullable(current));
            when(incidents.findByIdForUpdate(77L)).thenAnswer(i -> Optional.ofNullable(current));
            when(watcherRepo.findByIncidentIdOrderByWatcherIdentityAsc(77L)).thenAnswer(i -> new ArrayList<>(watchers));
            doAnswer(i -> {watchers.clear();return null;}).when(watcherRepo).deleteByIncidentId(77L);
            when(watcherRepo.save(any())).thenAnswer(i -> {IncidentWatcher w=i.getArgument(0);w.setId((long)watchers.size()+1);watchers.add(w);return w;});
            when(eventRepo.findByIncidentIdOrderByCreatedAtAscIdAsc(77L)).thenAnswer(i -> new ArrayList<>(events));
            when(eventRepo.save(any())).thenAnswer(i -> {IncidentEvent e=i.getArgument(0);e.setId((long)events.size()+1);events.add(e);return e;});
            when(relationRepo.findByIncidentIdOrderByPrimaryRelationDescCreatedAtAscIdAsc(77L)).thenAnswer(i -> new ArrayList<>(relations));
            doAnswer(i -> {relations.clear();return null;}).when(relationRepo).deleteByIncidentId(77L);
            when(relationRepo.save(any())).thenAnswer(i -> {IncidentRelation r=i.getArgument(0);r.setId((long)relations.size()+1);relations.add(r);return r;});
            when(routeRepo.findByIncidentIdOrderByCreatedAtAscIdAsc(77L)).thenAnswer(i -> new ArrayList<>(routes));
            when(routeRepo.findById(11L)).thenAnswer(i -> routes.stream().filter(r -> r.getId().equals(11L)).findFirst());
            when(routeRepo.save(any())).thenAnswer(i -> {IncidentRoute r=i.getArgument(0);if(r.getId()==null){r.setId(11L+(long)routes.size());routes.add(r);}return r;});
        }
        private Incident store(Incident incident) {incident.setId(77L);current=incident;return incident;}
        void seed() {current=new Incident();current.setId(77L);current.setIncidentKey("INC-77");current.setTitle("Fixture");current.setStatus("open");current.setSeverity("medium");}
        void storedWatcher(String username) {var w=new IncidentWatcher();w.setId((long)watchers.size()+1);w.setWatcherIdentity(username);w.setIncident(current);watchers.add(w);}
        void create(Map<String,Object> values,String actor) {var payload=new LinkedHashMap<String,Object>();payload.put("title"," Fixture ");payload.put("task_id",15L);payload.putAll(values);service.createIncident(payload,actor);}
        @SuppressWarnings("unchecked") Map<String,Object> details(){return (Map<String,Object>)service.getIncident(77L).get("incident");}
    }
}
