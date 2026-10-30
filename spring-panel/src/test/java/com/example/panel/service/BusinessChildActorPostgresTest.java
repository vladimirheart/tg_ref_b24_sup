package com.example.panel.service;

import com.example.panel.security.PanelUserPrincipal;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import static com.example.panel.support.BusinessChildActorFixture.*;
import static org.assertj.core.api.Assertions.*;

/** Frozen original-account regression for BUSINESS card/child/media SELECTs. */
class BusinessChildActorPostgresTest {
    static JdbcTemplate jdbc;static SingleConnectionDataSource connection;
    BusinessScopedTicketReadService service;Authentication actor;
    @BeforeAll static void schema()throws Exception{var fresh=PostgresqlJdbcTestSupport.freshJdbcTemplate("business_child_actor");connection=new SingleConnectionDataSource(fresh.getDataSource().getConnection(),true);jdbc=new JdbcTemplate(connection);com.example.panel.support.BusinessChildActorFixture.schema(jdbc,false);}
    @AfterAll static void close(){if(connection!=null)connection.destroy();}
    @BeforeEach void setup(){reset(7,"caller",false);}
    void reset(long id,String name,boolean all){seed(jdbc,id,name,"unused",all);actor=auth(id,name);service=new BusinessScopedTicketReadService(jdbc,new BusinessAccessService(jdbc));}
    void allowed(Surface surface){var value=call(service,surface,actor).orElseThrow();switch(surface){
        case card->assertThat(((BusinessScopedTicketReadService.TicketRow)value).problemPreview()).isEqualTo("private problem");
        case history->assertThat(((BusinessScopedTicketReadService.TicketHistory)value).entries()).hasSize(3);
        case answers->assertThat(((BusinessScopedTicketReadService.TicketAnswers)value).answers()).singleElement().extracting(BusinessScopedTicketReadService.AnswerEntry::valueText).isEqualTo("private answer");
        case workspace->{var workspace=(BusinessScopedTicketReadService.TicketWorkspace)value;assertThat(workspace.responsible()).isEqualTo("private responsible");assertThat(workspace.categories()).containsExactly("Delivery");}
        case spans->assertThat(((BusinessScopedTicketReadService.TicketSpans)value).spans()).singleElement().extracting(BusinessScopedTicketReadService.SpanEntry::durationSeconds).isEqualTo(5);
        default->{var reference=(BusinessScopedTicketReadService.AttachmentReference)value;assertThat(reference.storageKey()).isEqualTo(storageKey(surface));assertThat(reference.sha256()).isEqualTo(surface==Surface.legacy?null:SHA);}
    }}
    static Stream<Arguments> denied(){return Stream.of("rename","rename-reuse","delete-reuse","case","disabled","blocked","null-blocked","duplicate","scope","wrong-id","wrong-name","unbound","null","unauthenticated","principal-disabled","zero").flatMap(mode->Arrays.stream(Surface.values()).map(surface->Arguments.of(mode,surface)));}
    @ParameterizedTest @MethodSource("denied") void invalidOrStaleActorsCannotReadAnyChild(String mode,Surface surface){
        switch(mode){case "wrong-id"->actor=auth(55,"caller");case "wrong-name"->actor=auth(7,"CALLER");case "unbound"->actor=UsernamePasswordAuthenticationToken.authenticated("caller",null,List.of());case "null"->actor=null;case "unauthenticated"->actor=UsernamePasswordAuthenticationToken.unauthenticated(new PanelUserPrincipal(7,"caller","unused",true,List.of()),null);case "principal-disabled"->actor=UsernamePasswordAuthenticationToken.authenticated(new PanelUserPrincipal(7,"caller","unused",false,List.of()),null,List.of());case "zero"->actor=auth(0,"caller");default->change(jdbc,mode);}
        var before=snapshot(jdbc);assertThat(call(service,surface,actor)).isEmpty();assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    static Stream<Arguments> names(){return Stream.of("caller"," Caller ","Юникод").flatMap(name->Stream.of(false,true).flatMap(all->Arrays.stream(Surface.values()).map(surface->Arguments.of(name,all,surface))));}
    @ParameterizedTest @MethodSource("names") void exactRawNamesWorkForMembershipAndAllBusinesses(String name,boolean all,Surface surface){reset(7,name,all);allowed(surface);}
    static Stream<Arguments> ids(){return Stream.of(9007199254740993L,Long.MAX_VALUE).flatMap(id->Stream.of(false,true).flatMap(all->Arrays.stream(Surface.values()).map(surface->Arguments.of(id,all,surface))));}
    @ParameterizedTest @MethodSource("ids") void largeBoundIdsRemainExact(long id,boolean all,Surface surface){reset(id,"caller",all);allowed(surface);}
    static Stream<Arguments> boundaries(){return Stream.of("rename-reuse","case","disabled","null-blocked","scope","archive").flatMap(mode->Stream.of(false,true).flatMap(all->Arrays.stream(Surface.values()).map(surface->Arguments.of(mode,all,surface))));}
    @ParameterizedTest @MethodSource("boundaries") void eachOwningSqlRechecksActorAndScopeAfterIdResolution(String mode,boolean all,Surface surface)throws Exception{
        reset(7,"caller",all);var fired=new AtomicInteger();var raced=boundary(jdbc,n->{if(n==1){change(jdbc,mode);fired.incrementAndGet();}});
        var reads=new BusinessScopedTicketReadService(raced,new BusinessAccessService(jdbc));assertThat(call(reads,surface,actor)).isEmpty();assertThat(fired).hasValue(1);
    }
    static Stream<Arguments> fallback(){return Stream.of(false,true).flatMap(all->Stream.of("rename-reuse","case").map(mode->Arguments.of(all,mode)));}
    @ParameterizedTest @MethodSource("fallback") void legacyAttachmentFallbackKeepsTheOriginalActor(boolean all,String mode)throws Exception{
        reset(7,"caller",all);var fired=new AtomicInteger();var raced=boundary(jdbc,n->{if(n==2){change(jdbc,mode);fired.incrementAndGet();}});
        assertThat(call(new BusinessScopedTicketReadService(raced,new BusinessAccessService(jdbc)),Surface.legacy,actor)).isEmpty();assertThat(fired).hasValue(1);
    }
}
