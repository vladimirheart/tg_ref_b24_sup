package com.example.panel.service;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.security.PanelUserPrincipal;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import static com.example.panel.support.AiControlIdentityFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Non-CAS service primitive: independently test INSERT and conflict UPDATE with frozen assertions. */
class AiControlPrimitiveIdentityPostgresTest {
    private JdbcTemplate jdbc;
    private ScopedDialogAiPersistenceService service;
    private MultibusinessFeatureFlags flags;
    @BeforeEach void setup()throws Exception {
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("ai_control_primitive_identity");schema(jdbc,false);seed(jdbc,"caller","unused");flags=new MultibusinessFeatureFlags();service=new ScopedDialogAiPersistenceService(jdbc,flags);
    }
    private boolean write(UiEventTicketScope scope,Authentication actor,String reason)throws Exception {
        var types=new Class<?>[]{UiEventTicketScope.class,Authentication.class,boolean.class,boolean.class,String.class};
        try {
            try{return (Boolean)service.getClass().getMethod("setControl",types).invoke(service,scope,actor,true,false,reason);}
            catch(NoSuchMethodException oldApi){types[1]=String.class;return (Boolean)service.getClass().getMethod("setControl",types).invoke(service,scope,actor==null?null:actor.getName(),true,false,reason);}
        }catch(InvocationTargetException e){if(e.getCause() instanceof Exception cause)throw cause;throw new AssertionError(e.getCause());}
    }
    private void present(boolean value){if(value)existing(jdbc);}
    private void denied(Authentication actor)throws Exception {var before=snapshot(jdbc);assertThat(write(SCOPE,actor,"changed")).isFalse();assertThat(snapshot(jdbc)).isEqualTo(before);}
    private static Stream<Arguments> branches(String... choices){return Arrays.stream(choices).flatMap(c->Stream.of(false,true).map(p->Arguments.of(c,p)));}
    static Stream<Arguments> identities(){return branches("wrong-id","missing-id","zero-id","negative-id","case-change","padded","rename-reuse","delete-reuse");}
    @ParameterizedTest @MethodSource("identities")
    void loginCannotSelectAReplacementControlAuthor(String kind,boolean existing)throws Exception {
        present(existing);long id=switch(kind){case "wrong-id"->99;case "missing-id"->404;case "zero-id"->0;case "negative-id"->-7;default->7;};String name="caller";
        if(kind.equals("case-change")||kind.endsWith("reuse"))replace(jdbc,kind);
        if(kind.equals("padded")){name=" caller ";jdbc.update("INSERT INTO users(id,username) VALUES(55,?)",name);jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(55,11,'ADMIN')");}
        denied(auth(id,name));
    }
    static Stream<Arguments> invalidAuth(){return branches("unbound","anonymous","unauthenticated","disabled-principal","null");}
    @ParameterizedTest @MethodSource("invalidAuth")
    void invalidAuthenticationCannotInsertOrOverwriteControls(String kind,boolean existing)throws Exception {
        present(existing);Authentication actor=switch(kind){case "unbound"->UsernamePasswordAuthenticationToken.authenticated("caller",null,List.of());case "anonymous"->new AnonymousAuthenticationToken("fixture","caller",AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));case "unauthenticated"->UsernamePasswordAuthenticationToken.unauthenticated(new PanelUserPrincipal(7,"caller","unused",true,List.of()),null);case "disabled-principal"->UsernamePasswordAuthenticationToken.authenticated(new PanelUserPrincipal(7,"caller","unused",false,List.of()),null,List.of());default->null;};denied(actor);
    }
    static Stream<Arguments> names(){return branches("caller","Caller"," caller ","имя","anonymousUser","9007199254740993");}
    @ParameterizedTest @MethodSource("names")
    void theExactRawAuthorIsPreservedForBothWriteBranches(String name,boolean existing)throws Exception {
        present(existing);jdbc.update("UPDATE users SET username=? WHERE id=7",name);assertThat(write(SCOPE,auth(7,name),"  reason  ")).isTrue();
        assertThat(jdbc.queryForMap("SELECT ticket_user_id,ticket_id,ticket_public_id,ticket_scope_kind,ticket_business_id,ai_disabled,auto_reply_blocked,reason,updated_by_user_id,updated_by FROM ticket_ai_scoped_control"))
            .containsEntry("ticket_user_id",42L).containsEntry("ticket_id",RAW).containsEntry("ticket_public_id",PUBLIC).containsEntry("ticket_scope_kind","BUSINESS").containsEntry("ticket_business_id",11L).containsEntry("ai_disabled",true).containsEntry("auto_reply_blocked",false).containsEntry("reason","reason").containsEntry("updated_by_user_id",7L).containsEntry("updated_by",name);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_ai_scoped_events",Integer.class)).isZero();assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_ai_scoped_state",Integer.class)).isZero();
    }
    static Stream<Arguments> ids(){return Stream.of(7L,9007199254740993L,Long.MAX_VALUE).flatMap(id->Stream.of(false,true).map(p->Arguments.of(id,p)));}
    @ParameterizedTest @MethodSource("ids")
    void bigintIdsAreNotRoundedInTheAudit(long id,boolean existing)throws Exception {
        present(existing);jdbc.update("UPDATE users SET id=? WHERE id=7",id);jdbc.update("UPDATE business_memberships SET user_id=? WHERE user_id=7",id);assertThat(write(SCOPE,auth(id,"caller"),null)).isTrue();assertThat(jdbc.queryForObject("SELECT updated_by_user_id FROM ticket_ai_scoped_control",Long.class)).isEqualTo(id);
    }
    static Stream<Arguments> status(){return branches("disabled","blocked","membership","inactive","unknown","ambiguous","archived");}
    @ParameterizedTest @MethodSource("status")
    void currentStatusUniquenessAndBusinessAccessRemainRequired(String kind,boolean existing)throws Exception {
        present(existing);if(List.of("disabled","blocked","membership").contains(kind))replace(jdbc,kind);
        else if(kind.equals("inactive"))jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=7");else if(kind.equals("unknown"))jdbc.update("UPDATE business_memberships SET role_code='UNKNOWN' WHERE user_id=7");else if(kind.equals("ambiguous"))jdbc.update("INSERT INTO users(id,username) VALUES(55,'CALLER')");else jdbc.update("UPDATE businesses SET status='archived' WHERE id=11");denied(auth(7,"caller"));
    }
    static Stream<Arguments> roles(){return Stream.of("VIEWER","OPERATOR","MANAGER","ADMIN","UNKNOWN").flatMap(r->Stream.of("membership","all").flatMap(g->Stream.of(false,true).map(p->Arguments.of(r,g,p))));}
    @ParameterizedTest @MethodSource("roles")
    void operateIsSeparateFromReadForBothGrantKinds(String role,String grant,boolean existing)throws Exception {
        present(existing);jdbc.update("DELETE FROM business_memberships WHERE user_id=7");if(grant.equals("membership"))jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(7,11,?)",role);else jdbc.update("INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES(7,'ALL_BUSINESSES',?)",role);
        var before=snapshot(jdbc);boolean allowed=List.of("OPERATOR","MANAGER","ADMIN").contains(role);assertThat(write(SCOPE,auth(7,"caller"),"changed")).isEqualTo(allowed);if(!allowed)assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    static Stream<Arguments> owners(){return branches("business","generation","tuple","general","collision","missing","unresolved");}
    @ParameterizedTest @MethodSource("owners")
    void staleOwnersAndLegacyCollisionsCannotChangeControls(String kind,boolean existing)throws Exception {
        present(existing);switch(kind){case "business"->jdbc.update("UPDATE tickets SET business_id=22");case "generation"->jdbc.update("UPDATE tickets SET ticket_public_id=?","b".repeat(32));case "tuple"->jdbc.update("UPDATE tickets SET user_id=88");case "general"->jdbc.update("UPDATE tickets SET business_id=NULL,is_general=TRUE");case "collision"->jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id) VALUES(88,?,?,22)",RAW,"b".repeat(32));case "missing"->jdbc.update("DELETE FROM tickets");default->jdbc.update("UPDATE tickets SET business_id=NULL");}denied(auth(7,"caller"));
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void aSqlFailureDoesNotPartiallyChangeTheControl(boolean existing)throws Exception {
        present(existing);jdbc.execute("ALTER TABLE ticket_ai_scoped_control ADD CONSTRAINT deny_reason CHECK(reason<>'reject')");var before=snapshot(jdbc);assertThat(write(SCOPE,auth(7,"caller"),"reject")).isFalse();assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    @Test void aFreshOwnerCannotOverwriteAnEarlierOwnersControl()throws Exception {
        existing(jdbc);jdbc.update("UPDATE tickets SET user_id=88");var fresh=new UiEventTicketScope(88L,RAW,PUBLIC,"BUSINESS",11L);var before=snapshot(jdbc);assertThat(write(fresh,auth(7,"caller"),"new owner")).isFalse();assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void reasonTrimmingNullAndCapRemainUnchanged(boolean existing)throws Exception {
        present(existing);assertThat(write(SCOPE,auth(7,"caller"),"  "+"x".repeat(600)+"  ")).isTrue();assertThat(jdbc.queryForObject("SELECT reason FROM ticket_ai_scoped_control",String.class)).isEqualTo("x".repeat(500));assertThat(write(SCOPE,auth(7,"caller")," \t ")).isTrue();assertThat(jdbc.queryForObject("SELECT reason FROM ticket_ai_scoped_control",String.class)).isNull();
    }
    @Test void stringOnlyControlIdentityIsNoLongerPublicApi() {
        assertThat(Arrays.stream(service.getClass().getMethods()).filter(m->m.getName().equals("setControl")).map(m->m.getParameterTypes()[1])).containsExactly(Authentication.class);
    }
    @Test void invalidAuthenticationScopeAndMissingPolicyStopBeforeJdbc()throws Exception {
        var observed=mock(NamedParameterJdbcOperations.class);service=new ScopedDialogAiPersistenceService(observed,flags);assertThat(write(SCOPE,UsernamePasswordAuthenticationToken.authenticated("caller",null,List.of()),null)).isFalse();assertThat(write(null,auth(7,"caller"),null)).isFalse();service=new ScopedDialogAiPersistenceService(observed,null);assertThat(write(SCOPE,auth(7,"caller"),null)).isFalse();verifyNoInteractions(observed);
    }
}
