package com.example.panel.service;

import com.example.panel.config.MultibusinessFeatureFlags;
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

/** The test-only reflection bridge runs frozen cases before and after the API change. */
class AiControlActorIdentityPostgresTest {
    private JdbcTemplate jdbc;
    private ScopedDialogAiPersistenceService service;
    @BeforeEach void fixture()throws Exception{
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("ai_control_identity");schema(jdbc,false);seed(jdbc,"caller","unused");
        service=new ScopedDialogAiPersistenceService(jdbc,new MultibusinessFeatureFlags());
    }
    private boolean write(Authentication actor,String stamp)throws Exception{
        try{
            try{return (boolean)service.getClass().getMethod("setControlExpected",UiEventTicketScope.class,Authentication.class,String.class,boolean.class,boolean.class,String.class).invoke(service,SCOPE,actor,stamp,true,true," reason ");}
            catch(NoSuchMethodException oldApi){return (boolean)service.getClass().getMethod("setControlExpected",UiEventTicketScope.class,String.class,String.class,boolean.class,boolean.class,String.class).invoke(service,SCOPE,actor==null?null:actor.getName(),stamp,true,true," reason ");}
        }catch(InvocationTargetException e){if(e.getCause() instanceof Exception cause)throw cause;throw new AssertionError(e.getCause());}
    }
    private String prepare(boolean present){return present?existing(jdbc):"";}
    static Stream<Arguments> identities(){return Stream.of("wrong-id","missing-id","zero-id","negative-id","case-change","padded","rename-reuse","delete-reuse").flatMap(kind->Stream.of(false,true).map(p->Arguments.of(kind,p)));}
    @ParameterizedTest @MethodSource("identities")
    void identityCannotBeSelectedAgainByCurrentOrReusedName(String kind,boolean present)throws Exception{
        String stamp=prepare(present),name="caller";long id=switch(kind){case "wrong-id"->99;case "missing-id"->404;case "zero-id"->0;case "negative-id"->-7;default->7;};
        if(kind.equals("case-change")||kind.endsWith("reuse"))replace(jdbc,kind);
        if(kind.equals("padded")){name=" caller ";jdbc.update("INSERT INTO users(id,username) VALUES(55,?)",name);jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(55,11,'ADMIN')");}
        var before=snapshot(jdbc);assertThat(write(auth(id,name),stamp)).isFalse();assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    static Stream<Arguments> invalid(){return Stream.of("unbound","anonymous","unauthenticated","disabled-principal","null").flatMap(kind->Stream.of(false,true).map(p->Arguments.of(kind,p)));}
    @ParameterizedTest @MethodSource("invalid")
    void invalidAuthenticationNeverWrites(String kind,boolean present)throws Exception{
        String stamp=prepare(present);Authentication actor=switch(kind){case "unbound"->UsernamePasswordAuthenticationToken.authenticated("caller",null,List.of());case "anonymous"->new AnonymousAuthenticationToken("fixture","caller",AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));case "unauthenticated"->UsernamePasswordAuthenticationToken.unauthenticated(new PanelUserPrincipal(7,"caller","unused",true,List.of()),null);case "disabled-principal"->UsernamePasswordAuthenticationToken.authenticated(new PanelUserPrincipal(7,"caller","unused",false,List.of()),null,List.of());default->null;};
        var before=snapshot(jdbc);assertThat(write(actor,stamp)).isFalse();assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    static Stream<Arguments> names(){return Stream.of("caller","Caller"," caller ","имя","anonymousUser","9007199254740993").flatMap(name->Stream.of(false,true).map(p->Arguments.of(name,p)));}
    @ParameterizedTest @MethodSource("names")
    void literalRawNamesKeepExactNumericAndTextAudit(String name,boolean present)throws Exception{
        String stamp=prepare(present);jdbc.update("UPDATE users SET username=? WHERE id=7",name);assertThat(write(auth(7,name),stamp)).isTrue();
        assertThat(jdbc.queryForMap("SELECT ticket_user_id,ticket_id,ticket_public_id,ticket_scope_kind,ticket_business_id,updated_by_user_id,updated_by,ai_disabled,auto_reply_blocked,reason FROM ticket_ai_scoped_control"))
            .containsEntry("ticket_user_id",42L).containsEntry("ticket_id",RAW).containsEntry("ticket_public_id",PUBLIC).containsEntry("ticket_scope_kind","BUSINESS").containsEntry("ticket_business_id",11L)
            .containsEntry("updated_by_user_id",7L).containsEntry("updated_by",name).containsEntry("ai_disabled",true).containsEntry("auto_reply_blocked",true).containsEntry("reason","reason");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_ai_scoped_events WHERE event_type='control_updated' AND ticket_user_id=42 AND ticket_id=? AND ticket_public_id=? AND ticket_business_id=11",Integer.class,RAW,PUBLIC)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_ai_scoped_state",Integer.class)).isZero();
    }
    static Stream<Arguments> ids(){return Stream.of(7L,9007199254740993L,Long.MAX_VALUE).flatMap(id->Stream.of(false,true).map(p->Arguments.of(id,p)));}
    @ParameterizedTest @MethodSource("ids")
    void bigintIdsAreNotRounded(long id,boolean present)throws Exception{
        String stamp=prepare(present);jdbc.update("UPDATE users SET id=? WHERE id=7",id);jdbc.update("UPDATE business_memberships SET user_id=? WHERE user_id=7",id);
        assertThat(write(auth(id,"caller"),stamp)).isTrue();assertThat(jdbc.queryForObject("SELECT updated_by_user_id FROM ticket_ai_scoped_control",Long.class)).isEqualTo(id);
    }
    static Stream<Arguments> revocations(){return Stream.of("disabled","blocked","membership","viewer","ambiguous","archived").flatMap(kind->Stream.of(false,true).map(p->Arguments.of(kind,p)));}
    @ParameterizedTest @MethodSource("revocations")
    void currentReadOperateAndUniqueActiveAccountRemainRequired(String kind,boolean present)throws Exception{
        String stamp=prepare(present);if(List.of("disabled","blocked","membership").contains(kind))replace(jdbc,kind);
        else if(kind.equals("viewer"))jdbc.update("UPDATE business_memberships SET role_code='VIEWER' WHERE user_id=7");
        else if(kind.equals("ambiguous"))jdbc.update("INSERT INTO users(id,username) VALUES(55,'CALLER')");else jdbc.update("UPDATE businesses SET status='archived' WHERE id=11");
        var before=snapshot(jdbc);assertThat(write(auth(7,"caller"),stamp)).isFalse();assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    @Test void publicCompareAndSetHasNoStringIdentityOverload(){assertThat(Arrays.stream(service.getClass().getMethods()).filter(m->m.getName().equals("setControlExpected")).map(m->m.getParameterTypes()[1])).doesNotContain(String.class);}
    @ParameterizedTest @ValueSource(booleans={false,true})
    void staleFormsCannotUpsertOrDuplicateAnEvent(boolean present)throws Exception{
        String stamp=prepare(present);assertThat(write(auth(7,"caller"),stamp)).isTrue();var before=snapshot(jdbc);
        assertThat(write(auth(7,"caller"),stamp)).isFalse();assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void eventFailureRollsBackControlAndItsTimestamp(boolean present)throws Exception{
        String stamp=prepare(present);var before=snapshot(jdbc);jdbc.execute("CREATE FUNCTION fail_control_event() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'fixture failure'; END; $$; CREATE TRIGGER fail_control_event BEFORE INSERT ON ticket_ai_scoped_events FOR EACH ROW EXECUTE FUNCTION fail_control_event()");
        assertThat(write(auth(7,"caller"),stamp)).isFalse();assertThat(snapshot(jdbc)).isEqualTo(before);
    }
}
