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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import static com.example.panel.support.GeneralReadActorFixture.*;
import static org.assertj.core.api.Assertions.*;

/** Real PostgreSQL reads across every GENERAL root/child/media branch, frozen before the product edit. */
class GeneralReadActorPostgresTest {
    static JdbcTemplate jdbc;
    static SingleConnectionDataSource connection;
    GeneralTicketReadService service;
    Authentication actor;
    @BeforeAll static void schema()throws Exception{
        var fresh=PostgresqlJdbcTestSupport.freshJdbcTemplate("general_read_actor");
        connection=new SingleConnectionDataSource(fresh.getDataSource().getConnection(),true);jdbc=new JdbcTemplate(connection);
        com.example.panel.support.GeneralReadActorFixture.schema(jdbc,false);
    }
    @AfterAll static void close(){if(connection!=null)connection.destroy();}
    @BeforeEach void setup(){seed(jdbc,7,"caller","unused");actor=auth(7,"caller");service=new GeneralTicketReadService(jdbc,new GeneralTicketAccessService(jdbc));}
    void denied(GeneralTicketReadService reads,Surface surface,Authentication identity){
        try {var result=call(reads,surface,identity);if(surface==Surface.page)assertThat(((GeneralTicketReadService.TicketPage)result).tickets()).isEmpty();else assertThat((Optional<?>)result).isEmpty();}
        catch(AccessDeniedException ex){assertThat(surface).isEqualTo(Surface.page);}
    }
    void allowed(Surface surface){
        var result=call(service,surface,actor);
        switch(surface){
            case page->assertThat(((GeneralTicketReadService.TicketPage)result).tickets()).extracting(GeneralTicketReadService.TicketRow::ticketPublicId).containsExactlyInAnyOrder(PUBLIC,VK_PUBLIC);
            case find->assertThat(((Optional<GeneralTicketReadService.TicketRow>)result).orElseThrow().problem()).isEqualTo("private problem");
            case history->assertThat(((Optional<GeneralTicketReadService.TicketHistory>)result).orElseThrow().entries()).hasSize(3);
            case answers->assertThat(((Optional<GeneralTicketReadService.TicketAnswers>)result).orElseThrow().answers()).singleElement().extracting(GeneralTicketReadService.AnswerEntry::valueText).isEqualTo("private answer");
            default->{var reference=((Optional<GeneralTicketReadService.AttachmentReference>)result).orElseThrow();assertThat(reference.storageKey()).isEqualTo(storageKey(surface));assertThat(reference.sha256()).isEqualTo(surface==Surface.legacy?null:SHA);}
        }
    }
    static Stream<Arguments> deniedCases(){return Stream.of("rename","rename-reuse","delete-reuse","case","disabled","blocked","duplicate","read","wrong-id","wrong-name","unbound","null","unauthenticated","principal-disabled","blank","zero","negative").flatMap(mode->Arrays.stream(Surface.values()).map(surface->Arguments.of(mode,surface)));}
    @ParameterizedTest @MethodSource("deniedCases") void staleOrUnboundActorsCannotReadAnySurface(String mode,Surface surface){
        switch(mode){
            case "wrong-id"->actor=auth(55,"caller");case "wrong-name"->actor=auth(7,"CALLER");
            case "unbound"->actor=UsernamePasswordAuthenticationToken.authenticated("caller",null,List.of());case "null"->actor=null;
            case "unauthenticated"->actor=UsernamePasswordAuthenticationToken.unauthenticated(new PanelUserPrincipal(7,"caller","unused",true,List.of()),null);
            case "principal-disabled"->actor=UsernamePasswordAuthenticationToken.authenticated(new PanelUserPrincipal(7,"caller","unused",false,List.of()),null,List.of());
            case "blank"->actor=auth(7," ");case "zero"->actor=auth(0,"caller");case "negative"->actor=auth(-1,"caller");default->change(jdbc,mode);
        }
        var before=snapshot(jdbc);denied(service,surface,actor);assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    static Stream<Arguments> rawNames(){return Stream.of("caller"," Caller ","Юникод","İ","caller@domain").flatMap(name->Arrays.stream(Surface.values()).map(surface->Arguments.of(name,surface)));}
    @ParameterizedTest @MethodSource("rawNames") void exactRawStoredNamesKeepReadAndMediaContracts(String name,Surface surface){seed(jdbc,7,name,"unused");actor=auth(7,name);allowed(surface);}
    static Stream<Arguments> bigint(){return Stream.of(9007199254740993L,Long.MAX_VALUE,5L).flatMap(id->Arrays.stream(Surface.values()).map(surface->Arguments.of(id,surface)));}
    @ParameterizedTest @MethodSource("bigint") void boundBigintIdsAreNotRounded(long id,Surface surface){seed(jdbc,id,"caller","unused");actor=auth(id,"caller");allowed(surface);}
    @ParameterizedTest @EnumSource(Surface.class) void nullableBlockedPolicyAndReadWithoutOperateStayValid(Surface surface){jdbc.update("UPDATE users SET is_blocked=NULL WHERE id=7");assertThat(new GeneralTicketAccessService(jdbc).canOperate(7L)).isFalse();allowed(surface);}
    static Stream<Arguments> boundaries(){return Stream.of("rename-reuse","delete-reuse","case","disabled","blocked","duplicate","read").flatMap(mode->Arrays.stream(Surface.values()).map(surface->Arguments.of(mode,surface)));}
    @ParameterizedTest @MethodSource("boundaries") void changesAfterPreflightAreRecheckedByTheOwningStatement(String mode,Surface surface)throws Exception{
        var fired=new AtomicInteger();var raced=boundary(jdbc,n->{if(n==1){change(jdbc,mode);fired.incrementAndGet();}});
        denied(new GeneralTicketReadService(raced,new GeneralTicketAccessService(jdbc)),surface,actor);assertThat(fired).hasValue(1);
    }
    static Stream<Arguments> fallbacks(){return Stream.of(Surface.legacy,Surface.telegram,Surface.vk,Surface.document).flatMap(surface->Stream.of("rename-reuse","case").map(mode->Arguments.of(surface,mode)));}
    @ParameterizedTest @MethodSource("fallbacks") void everyAttachmentFallbackUsesTheOriginalBoundActor(Surface surface,String mode)throws Exception{
        int target=surface==Surface.legacy?3:surface==Surface.document?1:2;var fired=new AtomicInteger();
        var raced=boundary(jdbc,n->{if(n==target){change(jdbc,mode);fired.incrementAndGet();}});
        denied(new GeneralTicketReadService(raced,new GeneralTicketAccessService(jdbc)),surface,actor);assertThat(fired).hasValue(1);
    }
}
