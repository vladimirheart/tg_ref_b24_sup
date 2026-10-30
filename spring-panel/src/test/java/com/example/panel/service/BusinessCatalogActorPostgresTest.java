package com.example.panel.service;

import com.example.panel.security.PanelUserPrincipal;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import static com.example.panel.support.BusinessCatalogActorFixture.*;
import static com.example.panel.support.BusinessChildActorFixture.auth;
import static org.assertj.core.api.Assertions.*;

class BusinessCatalogActorPostgresTest {
    static JdbcTemplate jdbc;static SingleConnectionDataSource connection;
    BusinessScopedTicketReadService reads;ManualBusinessTicketCreationService creates;Authentication actor;boolean allScope;
    @BeforeAll static void schema()throws Exception{var fresh=PostgresqlJdbcTestSupport.freshJdbcTemplate("business_catalog_actor");connection=new SingleConnectionDataSource(fresh.getDataSource().getConnection(),true);jdbc=new JdbcTemplate(connection);com.example.panel.support.BusinessCatalogActorFixture.schema(jdbc,false);}
    @AfterAll static void close(){if(connection!=null)connection.destroy();}
    @BeforeEach void setup(){reset(7,"caller",false);}
    void reset(long id,String name,boolean all){allScope=all;seed(jdbc,id,name,"unused",all);actor=auth(id,name);reads=new BusinessScopedTicketReadService(jdbc,new BusinessAccessService(jdbc));creates=new ManualBusinessTicketCreationService(jdbc,new BusinessAccessService(jdbc));}
    void allowed(Surface surface){var value=call(reads,creates,surface,actor);assertThat(size(value)).isEqualTo(switch(surface){case catalog->allScope?2:1;case categories,manual->1;default->2;});
        if(surface==Surface.manual){var ticket=(ManualBusinessTicketCreationService.CreatedTicket)value;assertThat(jdbc.queryForObject("SELECT created_by_user_id FROM manual_business_clients WHERE user_id=?",Long.class,ticket.clientUserId())).isEqualTo(((PanelUserPrincipal)actor.getPrincipal()).getUserId());assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM messages WHERE ticket_id=?",Integer.class,ticket.ticketId())).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ticket_spans WHERE ticket_id=?",Integer.class,ticket.ticketId())).isEqualTo(1);}
    }
    static Stream<Arguments> identities(){return Stream.of("caller"," Caller ","Юникод").flatMap(name->Stream.of(false,true).flatMap(all->Arrays.stream(Surface.values()).map(s->Arguments.of(name,all,s))));}
    @ParameterizedTest @MethodSource("identities") void exactRawLoginKeepsEitherScopeSource(String name,boolean all,Surface surface){reset(7,name,all);allowed(surface);}
    static Stream<Arguments> ids(){return Stream.of(9007199254740993L,Long.MAX_VALUE).flatMap(id->Stream.of(false,true).flatMap(all->Arrays.stream(Surface.values()).map(s->Arguments.of(id,all,s))));}
    @ParameterizedTest @MethodSource("ids") void bigIdsAreNeverRounded(long id,boolean all,Surface surface){reset(id,"caller",all);allowed(surface);}
    static Stream<Arguments> stale(){return Stream.of("rename-reuse","delete-reuse","case","disabled","blocked","null-blocked","duplicate","scope","archive").flatMap(mode->Arrays.stream(Surface.values()).map(s->Arguments.of(mode,s)));}
    @ParameterizedTest @MethodSource("stale") void staleAccountCannotReadOrWrite(String mode,Surface surface){change(jdbc,mode);var before=snapshot(jdbc);assertThat(denied(reads,creates,surface,actor)).isTrue();assertThat(snapshot(jdbc)).isEqualTo(before);}
    static Stream<Arguments> invalid(){return Stream.of("wrong-id","wrong-name","unbound","null","anonymous","unauthenticated","disabled","zero").flatMap(mode->Arrays.stream(Surface.values()).map(s->Arguments.of(mode,s)));}
    @ParameterizedTest @MethodSource("invalid") void unboundAuthenticationNeverBecomesAStoredId(String mode,Surface surface){
        actor=switch(mode){case "wrong-id"->auth(55,"caller");case "wrong-name"->auth(7,"CALLER");case "unbound"->UsernamePasswordAuthenticationToken.authenticated("caller",null,List.of());case "null"->null;case "anonymous"->new AnonymousAuthenticationToken("fixture","caller",List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ANONYMOUS")));case "unauthenticated"->UsernamePasswordAuthenticationToken.unauthenticated(new PanelUserPrincipal(7,"caller","unused",true,List.of()),null);case "disabled"->UsernamePasswordAuthenticationToken.authenticated(new PanelUserPrincipal(7,"caller","unused",false,List.of()),null,List.of());case "zero"->auth(0,"caller");default->throw new AssertionError();};
        var before=snapshot(jdbc);
        if(surface==Surface.manual&&mode.equals("zero")){assertThatThrownBy(()->call(reads,creates,surface,actor)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);assertThat(snapshot(jdbc)).isEqualTo(before);return;}
        assertThat(denied(reads,creates,surface,actor)).isTrue();assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    static Stream<Arguments> late(){return Stream.of("rename-reuse","case","disabled","blocked","null-blocked","duplicate","scope","archive").flatMap(mode->Stream.of(false,true).flatMap(all->Arrays.stream(Surface.values()).map(s->Arguments.of(mode,all,s))));}
    @ParameterizedTest @MethodSource("late") void owningStatementRechecksAfterBusinessResolution(String mode,boolean all,Surface surface)throws Exception{
        reset(7,"caller",all);var fired=new AtomicBoolean();var raced=boundary(jdbc,()->{if(fired.compareAndSet(false,true))change(jdbc,mode);});
        var before=snapshot(jdbc);var read=new BusinessScopedTicketReadService(raced,new BusinessAccessService(jdbc));var create=new ManualBusinessTicketCreationService(raced,new BusinessAccessService(jdbc));
        if(mode.equals("archive")&&all&&surface==Surface.catalog){var options=(java.util.List<?>)call(read,create,surface,actor);assertThat(options).singleElement().satisfies(value->assertThat(((BusinessScopedTicketReadService.BusinessOption)value).id()).isEqualTo(22));}
        else assertThat(denied(read,create,surface,actor)).isTrue();assertThat(fired).isTrue();
        // Only the deliberate account/scope mutation is allowed; no ticket/client/message/span writes.
        var after=snapshot(jdbc);for(String table:List.of("manual_business_clients","tickets","messages","ticket_spans"))assertThat(after.get(table)).as(table).isEqualTo(before.get(table));
    }
    @ParameterizedTest @ValueSource(booleans={false,true}) void viewersRetainReadsButCannotCreate(boolean all){reset(7,"caller",all);change(jdbc,"viewer");for(Surface s:Surface.values())if(s!=Surface.manual)allowed(s);var before=snapshot(jdbc);assertThat(denied(reads,creates,Surface.manual,actor)).isTrue();assertThat(snapshot(jdbc)).isEqualTo(before);}
}
