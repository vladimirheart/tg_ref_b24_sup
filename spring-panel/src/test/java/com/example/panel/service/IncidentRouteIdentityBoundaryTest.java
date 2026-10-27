package com.example.panel.service;

import com.example.panel.entity.Incident;
import com.example.panel.entity.IncidentRoute;
import com.example.panel.repository.IncidentRouteRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IncidentRouteIdentityBoundaryTest {
    static Stream<String> identities() { return IncidentIdentityBoundaryTest.identities(); }

    @ParameterizedTest @MethodSource("identities")
    void enqueueAndDispatchKeepLiteralUserTargetOwnerAndRequester(String raw) throws Exception {
        var f = new Fixture("user",raw);f.incident.setOwner(raw);
        assertThat(f.outbox.enqueueIncidentRoutes(f.incident,"incident_created","Created",Map.of("fixture",true),raw)).isEqualTo(1);
        assertThat(f.jdbc.row.get("route_target")).isEqualTo(raw);
        assertThat(f.jdbc.row.get("requested_by")).isEqualTo(raw);
        Map<?,?> envelope = new ObjectMapper().readValue((String)f.jdbc.row.get("payload_json"),Map.class);
        assertThat(envelope.get("owner")).isEqualTo(raw);assertThat(envelope.get("requested_by")).isEqualTo(raw);
        assertThat(envelope.get("route_target")).isEqualTo(raw);
        f.outbox.dispatchBatch();
        verify(f.notifications).notifyUser(eq(raw),anyString(),eq("/dialogs?incidentId=INC-7"));
        verifyNoMoreInteractions(f.notifications);assertThat(f.jdbc.delivered).isEqualTo(1);assertThat(f.jdbc.failed).isZero();
    }

    @ParameterizedTest @MethodSource("identities")
    void directUserDeliveryDoesNotSubstitutePlainAccount(String raw) {
        var f=new Fixture("user",raw);
        f.delivery.deliver(" USER ",raw,"Message","/incident",Map.of());
        verify(f.notifications).notifyUser(raw,"Message","/incident");verifyNoMoreInteractions(f.notifications);
    }

    @Test void legacyUsersCsvStillTrimsFoldsDeduplicatesAndSplits() {
        var f=new Fixture("users"," One, TWO;One\n three ");
        f.outbox.enqueueIncidentRoutes(f.incident,"created","Created",Map.of(),"operator");f.outbox.dispatchBatch();
        verify(f.notifications).notifyUsers(eq(Set.of("one","two","three")),anyString(),anyString());
        verify(f.notifications,never()).notifyUser(any(),any(),any());assertThat(f.jdbc.failed).isZero();
    }

    @Test void departmentAndOperatorRoutesRetainTheirExistingContracts() {
        var f=new Fixture("department"," Support ");when(f.routing.findDepartmentRecipients("Support")).thenReturn(Set.of(" lead ","lead"));
        f.outbox.enqueueIncidentRoutes(f.incident,"created","Created",Map.of(),"operator");f.outbox.dispatchBatch();
        verify(f.routing).findDepartmentRecipients("Support");verify(f.notifications).notifyUsers(eq(Set.of(" lead ","lead")),anyString(),anyString());
        f.delivery.deliver("operators",null,"Message","/incident",Map.of());verify(f.notifications).notifyAllOperators("Message","/incident",null);
    }

    @Test void nullAndBlankIdentityMetadataRemainNull() throws Exception {
        var f=new Fixture("user","operator");f.incident.setOwner(" \t ");
        f.outbox.enqueueIncidentRoutes(f.incident,"created","Created",Map.of(),null);
        Map<?,?> envelope=new ObjectMapper().readValue((String)f.jdbc.row.get("payload_json"),Map.class);
        assertThat(envelope.get("owner")).isNull();assertThat(envelope.get("requested_by")).isNull();assertThat(f.jdbc.row.get("requested_by")).isNull();
    }

    static final class Fixture {
        final NotificationService notifications=mock(NotificationService.class);
        final NotificationRoutingService routing=mock(NotificationRoutingService.class);
        final IncidentRouteDeliveryService delivery=new IncidentRouteDeliveryService(notifications,routing,new ObjectMapper());
        final CapturingJdbc jdbc=new CapturingJdbc();final Incident incident=new Incident();final IncidentRoute route=new IncidentRoute();
        final IncidentRouteDeliveryOutboxService outbox;
        Fixture(String type,String target){
            incident.setId(7L);incident.setIncidentKey("INC-7");incident.setTitle("Fixture");
            route.setId(11L);route.setIncident(incident);route.setRouteType(type);route.setRouteTarget(target);
            var repository=mock(IncidentRouteRepository.class);when(repository.findByIncidentIdOrderByCreatedAtAscIdAsc(7L)).thenReturn(List.of(route));when(repository.findById(11L)).thenReturn(Optional.of(route));
            outbox=new IncidentRouteDeliveryOutboxService(jdbc,new ObjectMapper(),repository,mock(RuntimeCoordinationService.class),delivery);
        }
    }

    /** Captures JDBC bindings and feeds them to the real row mapper; no SQL or external sends run. */
    static final class CapturingJdbc extends JdbcTemplate {
        final Map<String,Object> row=new LinkedHashMap<>();int delivered,failed;
        @Override public int update(String sql,Object... args){
            if(sql.contains("INSERT INTO incident_route_delivery_outbox")){
                String[] names={"event_id","incident_id","route_id","event_type","route_type","route_target","message_text","incident_url","payload_json","requested_by"};
                for(int i=0;i<names.length;i++)row.put(names[i],args[i]);
            }
            if(sql.contains("SET status = 'delivered'"))delivered++;
            if(sql.contains("SET status = 'failed'"))failed++;
            return 1;
        }
        @Override public <T> List<T> query(String sql,RowMapper<T> mapper,Object... args){
            try{
                ResultSet rs=mock(ResultSet.class);
                when(rs.getString(anyString())).thenAnswer(i -> {Object value=row.get(i.getArgument(0));return value==null?null:String.valueOf(value);});
                when(rs.getLong(anyString())).thenAnswer(i -> ((Number)row.get(i.getArgument(0))).longValue());
                when(rs.getInt("attempt_count")).thenReturn(1);
                return row.isEmpty()?List.of():List.of(mapper.mapRow(rs,0));
            }catch(Exception ex){throw new AssertionError(ex);}
        }
    }
}
