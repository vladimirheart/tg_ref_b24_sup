package com.example.panel.service;

import com.example.panel.security.PanelUserPrincipal;
import com.example.panel.support.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import static com.example.panel.support.AiControlIdentityFixture.*;
import static org.assertj.core.api.Assertions.*;

/** Frozen SQL cases use reflection only to cross the old String/new Authentication API. */
class AiWorkbenchReadIdentityPostgresTest {
    private JdbcTemplate jdbc;
    private ScopedDialogAiWorkbenchService work;
    @BeforeEach void setup()throws Exception {
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("ai_workbench_read_identity");
        AiWorkbenchReadIdentityFixture.schema(jdbc,false);AiWorkbenchReadIdentityFixture.seed(jdbc,"caller","unused");work=new ScopedDialogAiWorkbenchService(jdbc);
    }
    private Object invoke(String name,Object owner,Authentication actor)throws Exception {
        Class<?> first=name.equals("resolve")?String.class:UiEventTicketScope.class;
        try {
            try{return work.getClass().getMethod(name,first,Authentication.class).invoke(work,owner,actor);}
            catch(NoSuchMethodException before){return work.getClass().getMethod(name,first,String.class).invoke(work,owner,actor==null?null:actor.getName());}
        }catch(InvocationTargetException e){if(e.getCause() instanceof Exception cause)throw cause;throw new AssertionError(e.getCause());}
    }
    private List<?> read(String method,Authentication actor)throws Exception{return result(invoke(method,method.equals("resolve")?PUBLIC:SCOPE,actor));}
    private List<?> result(Object value){return value instanceof Optional<?> o?o.stream().toList():(List<?>)value;}
    private static Stream<Arguments> pairs(String... kinds){return Arrays.stream(kinds).flatMap(k->Stream.of("resolve","messages").map(m->Arguments.of(k,m)));}
    static Stream<Arguments> identities(){return pairs("wrong-id","missing-id","zero-id","negative-id","case-change","padded","rename-reuse","delete-reuse");}
    @ParameterizedTest @MethodSource("identities")
    void currentOrReusedNameCannotChooseTheReader(String kind,String method)throws Exception {
        long id=switch(kind){case "wrong-id"->99;case "missing-id"->404;case "zero-id"->0;case "negative-id"->-7;default->7;};String name="caller";
        if(kind.equals("case-change")||kind.endsWith("reuse"))replace(jdbc,kind);
        if(kind.equals("padded")){name=" caller ";jdbc.update("INSERT INTO users(id,username) VALUES(55,?)",name);jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(55,11,'ADMIN')");}
        var before=snapshot(jdbc);assertThat(read(method,auth(id,name))).isEmpty();assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    static Stream<Arguments> invalid(){return pairs("unbound","anonymous","unauthenticated","disabled-principal","null");}
    @ParameterizedTest @MethodSource("invalid")
    void invalidAuthenticationCannotRead(String kind,String method)throws Exception {
        Authentication actor=switch(kind){case "unbound"->UsernamePasswordAuthenticationToken.authenticated("caller",null,List.of());case "anonymous"->new AnonymousAuthenticationToken("fixture","caller",AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));case "unauthenticated"->UsernamePasswordAuthenticationToken.unauthenticated(new PanelUserPrincipal(7,"caller","unused",true,List.of()),null);case "disabled-principal"->UsernamePasswordAuthenticationToken.authenticated(new PanelUserPrincipal(7,"caller","unused",false,List.of()),null,List.of());default->null;};assertThat(read(method,actor)).isEmpty();
    }
    static Stream<Arguments> names(){return pairs("caller","Caller"," caller ","имя","anonymousUser","9007199254740993");}
    @ParameterizedTest @MethodSource("names")
    void exactLiteralRawNamesRemainValid(String name,String method)throws Exception {
        jdbc.update("UPDATE users SET username=? WHERE id=7",name);var rows=read(method,auth(7,name));assertThat(rows).hasSize(method.equals("resolve")?1:2);
        if(method.equals("resolve"))assertThat(rows.get(0)).isEqualTo(SCOPE);
        else assertThat(((ScopedDialogAiWorkbenchService.Message)rows.get(0)).preview()).isEqualTo("PRIVATE_R210_ANSWER");
    }
    static Stream<Arguments> ids(){return Stream.of(7L,9007199254740993L,Long.MAX_VALUE).flatMap(id->Stream.of("resolve","messages").map(m->Arguments.of(id,m)));}
    @ParameterizedTest @MethodSource("ids")
    void bigintIdsAreNotRounded(long id,String method)throws Exception {
        jdbc.update("UPDATE users SET id=? WHERE id=7",id);jdbc.update("UPDATE business_memberships SET user_id=? WHERE user_id=7",id);assertThat(read(method,auth(id,"caller"))).hasSize(method.equals("resolve")?1:2);
    }
    static Stream<Arguments> revocations(){return pairs("disabled","blocked","membership","inactive","unknown","ambiguous","archived");}
    @ParameterizedTest @MethodSource("revocations")
    void currentStatusUniqueAccountAndReadGrantRemainRequired(String kind,String method)throws Exception {
        if(List.of("disabled","blocked","membership").contains(kind))replace(jdbc,kind);
        else if(kind.equals("inactive"))jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=7");
        else if(kind.equals("unknown"))jdbc.update("UPDATE business_memberships SET role_code='UNKNOWN' WHERE user_id=7");
        else if(kind.equals("ambiguous"))jdbc.update("INSERT INTO users(id,username) VALUES(55,'CALLER')");
        else jdbc.update("UPDATE businesses SET status='archived' WHERE id=11");
        assertThat(read(method,auth(7,"caller"))).isEmpty();
    }
    static Stream<Arguments> owners(){return pairs("business","generation","general","collision","missing","unresolved");}
    @ParameterizedTest @MethodSource("owners")
    void ownerIdentityAndLegacyCollisionRemainClosed(String kind,String method)throws Exception {
        switch(kind){case "business"->jdbc.update("UPDATE tickets SET business_id=22");case "generation"->jdbc.update("UPDATE tickets SET ticket_public_id=?","b".repeat(32));case "general"->jdbc.update("UPDATE tickets SET business_id=NULL,is_general=TRUE");case "collision"->jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id) VALUES(88,?,? ,22)",RAW,"b".repeat(32));case "missing"->jdbc.update("DELETE FROM tickets");default->jdbc.update("UPDATE tickets SET business_id=NULL");}
        assertThat(read(method,auth(7,"caller"))).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings={"resolve","messages"})
    void publicReadApiHasNoStringIdentityOverload(String method){assertThat(Arrays.stream(work.getClass().getMethods()).filter(m->m.getName().equals(method)).map(m->m.getParameterTypes()[1])).doesNotContain(String.class);}
    @ParameterizedTest @ValueSource(strings={"user","raw","business"})
    void historyRequiresTheFullExpectedOwnerTuple(String field)throws Exception {
        var stale=new UiEventTicketScope(field.equals("user")?88L:42L,field.equals("raw")?RAW.trim():RAW,PUBLIC,"BUSINESS",field.equals("business")?22L:11L);
        assertThat(result(invoke("messages",stale,auth(7,"caller")))).isEmpty();
    }
    @ParameterizedTest @NullSource @ValueSource(strings={"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"," aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","short"})
    void malformedLocatorsRemainEmpty(String locator)throws Exception {assertThat(result(invoke("resolve",locator,auth(7,"caller")))).isEmpty();}
    @Test void textOnlyHistoryKeepsFingerprintDescendingOrderPreviewAndCap()throws Exception {
        assertThat(read("messages",auth(7,"caller"))).extracting(x->((ScopedDialogAiWorkbenchService.Message)x).id()).containsExactly(41L,40L);
        jdbc.update("INSERT INTO chat_history(id,ticket_owner_user_id,ticket_id,sender,message,message_type) SELECT n,42,?,'client',repeat('q',1200),'text' FROM generate_series(100,160) n",RAW);jdbc.execute("ANALYZE chat_history");
        var rows=read("messages",auth(7,"caller"));assertThat(rows).hasSize(50);var newest=(ScopedDialogAiWorkbenchService.Message)rows.get(0);assertThat(newest.id()).isEqualTo(160);assertThat(newest.preview()).isEqualTo("q".repeat(1000));assertThat(newest.fingerprint()).isEqualTo(jdbc.queryForObject("SELECT encode(sha256(convert_to(message,'UTF8')),'hex') FROM chat_history WHERE id=160",String.class));assertThat(((ScopedDialogAiWorkbenchService.Message)rows.get(49)).id()).isEqualTo(111);
    }
}
