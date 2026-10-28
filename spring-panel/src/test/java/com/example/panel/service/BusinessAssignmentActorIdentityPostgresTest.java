package com.example.panel.service;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.security.PanelUserPrincipal;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

import static org.assertj.core.api.Assertions.*;

/** Real SQL/transactions. The reflection bridge permits unchanged RED tests against the old API. */
class BusinessAssignmentActorIdentityPostgresTest {
    private static final String RAW=" R201/Ю?%#+ ",PUBLIC="a".repeat(32);
    private final UiEventTicketScope scope=new UiEventTicketScope(42L,RAW,PUBLIC,"BUSINESS",11L);
    private JdbcTemplate jdbc;
    private BusinessTicketAssignmentService service;
    @BeforeEach void fixture() throws Exception {
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("assignment_actor_identity");
        jdbc.execute("""
            CREATE TABLE users(id BIGINT PRIMARY KEY,username TEXT,enabled BOOLEAN DEFAULT TRUE,is_blocked BOOLEAN DEFAULT FALSE);
            CREATE TABLE businesses(id BIGINT PRIMARY KEY,status TEXT DEFAULT 'active');
            CREATE TABLE business_memberships(user_id BIGINT,business_id BIGINT,role_code TEXT,is_active BOOLEAN DEFAULT TRUE);
            CREATE TABLE business_access_grants(user_id BIGINT,grant_scope TEXT,role_code TEXT,is_active BOOLEAN DEFAULT TRUE);
            CREATE TABLE tickets(user_id BIGINT,ticket_id TEXT,ticket_public_id TEXT,business_id BIGINT,is_general BOOLEAN DEFAULT FALSE,channel_id BIGINT,status TEXT DEFAULT 'open',PRIMARY KEY(user_id,ticket_id));
            CREATE TABLE ticket_responsibles(ticket_id TEXT PRIMARY KEY,responsible TEXT,assigned_by TEXT,assigned_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP);
            CREATE TABLE ui_event_outbox(id BIGINT PRIMARY KEY,event_type TEXT,ticket_id TEXT,channel_id BIGINT,created_at TIMESTAMPTZ,ticket_user_id BIGINT,ticket_public_id TEXT,ticket_scope_kind TEXT,ticket_business_id BIGINT);
            INSERT INTO users(id,username) VALUES(7,'caller'),(8,'target'),(99,'foreign');
            INSERT INTO businesses(id) VALUES(11),(22);
            INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(7,11,'OPERATOR'),(8,11,'OPERATOR'),(99,22,'ADMIN');
            """);
        for(String file:List.of("V77__scoped_sla_assignment_revision.sql","V78__business_manual_assignment_events.sql"))jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/postgresql",file)));
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id) VALUES(42,?,?,11)",RAW,PUBLIC);
        var flags=new MultibusinessFeatureFlags();flags.getFoundation().setEnabled(true);flags.setUiSelector(true);
        service=new BusinessTicketAssignmentService(jdbc,flags,new UiEventOutboxAppendService(jdbc,new LegacyTicketIdJdbcGuard(jdbc)));
    }
    private Authentication bound(long id,String username){return UsernamePasswordAuthenticationToken.authenticated(new PanelUserPrincipal(id,username,"unused",true,List.of()),null,List.of());}
    private boolean write(Authentication authentication,boolean take) throws Exception {
        try {
            try{return (boolean)BusinessTicketAssignmentService.class.getMethod("change",UiEventTicketScope.class,Authentication.class,long.class,Long.class,boolean.class).invoke(service,scope,authentication,0L,take?null:8L,take);}
            catch(NoSuchMethodException oldApi){return (boolean)BusinessTicketAssignmentService.class.getMethod("change",UiEventTicketScope.class,String.class,long.class,Long.class,boolean.class).invoke(service,scope,authentication==null?null:authentication.getName(),0L,take?null:8L,take);}
        }catch(InvocationTargetException ex){if(ex.getCause() instanceof Exception cause)throw cause;throw new AssertionError(ex.getCause());}
    }
    private Map<String,List<Map<String,Object>>> snapshot(){var result=new LinkedHashMap<String,List<Map<String,Object>>>();for(String table:List.of("users","business_memberships","tickets","ticket_responsibles","business_ticket_assignment_events","ui_event_outbox"))result.put(table,jdbc.queryForList("SELECT * FROM "+table));return result;}
    private void replacement(String name){jdbc.update("INSERT INTO users(id,username) VALUES(55,?)",name);jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(55,11,'OPERATOR')");}
    static Stream<Arguments> substitutions(){return Stream.of("wrong-id","missing-id","zero-id","negative-id","case-changed","padded","renamed","deleted").flatMap(kind->Stream.of(false,true).map(take->Arguments.of(kind,take)));}
    @ParameterizedTest @MethodSource("substitutions")
    void anAccountCannotBeSubstitutedByItsCurrentOrReusedName(String kind,boolean take) throws Exception {
        long id=switch(kind){case "wrong-id"->99;case "missing-id"->404;case "zero-id"->0;case "negative-id"->-7;default->7;};String name="caller";
        if(kind.equals("case-changed"))name="CALLER";
        if(kind.equals("padded")){name=" caller ";replacement(name);}
        if(kind.equals("renamed")){jdbc.update("UPDATE users SET username='renamed' WHERE id=7");replacement("caller");}
        if(kind.equals("deleted")){jdbc.update("DELETE FROM users WHERE id=7");replacement("caller");}
        var before=snapshot();assertThat(write(bound(id,name),take)).isFalse();assertThat(snapshot()).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"unbound","anonymous","unauthenticated","disabled-principal","null"})
    void invalidAuthenticationCannotWriteEvenWhenItsNameIsAnOperator(String kind) throws Exception {
        Authentication auth=switch(kind){case "unbound"->UsernamePasswordAuthenticationToken.authenticated("caller",null,List.of());case "anonymous"->new AnonymousAuthenticationToken("fixture","caller",AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));case "unauthenticated"->UsernamePasswordAuthenticationToken.unauthenticated(new PanelUserPrincipal(7,"caller","unused",true,List.of()),null);case "disabled-principal"->UsernamePasswordAuthenticationToken.authenticated(new PanelUserPrincipal(7,"caller","unused",false,List.of()),null,List.of());default->null;};
        var before=snapshot();assertThat(write(auth,false)).isFalse();assertThat(snapshot()).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"caller","Caller"," caller ","имя","anonymousUser","9007199254740993"})
    void validLiteralNamesRetainTheSameNumericActorAndRawAudit(String name) throws Exception {
        jdbc.update("UPDATE users SET username=? WHERE id=7",name);assertThat(write(bound(7,name),true)).isTrue();
        assertThat(jdbc.queryForMap("SELECT actor_user_id,actor,assignee_user_id,assignee FROM business_ticket_assignment_events")).containsEntry("actor_user_id",7L).containsEntry("actor",name).containsEntry("assignee_user_id",7L).containsEntry("assignee",name);
        assertThat(jdbc.queryForMap("SELECT responsible,assigned_by FROM ticket_responsibles")).containsEntry("responsible",name).containsEntry("assigned_by",name);
    }
    @ParameterizedTest @ValueSource(longs={7,9007199254740993L,Long.MAX_VALUE})
    void numericActorIdsKeepTheirExactBigintValue(long id) throws Exception {
        jdbc.update("UPDATE users SET id=? WHERE id=7",id);jdbc.update("UPDATE business_memberships SET user_id=? WHERE user_id=7",id);
        assertThat(write(bound(id,"caller"),true)).isTrue();assertThat(jdbc.queryForMap("SELECT actor_user_id,assignee_user_id FROM business_ticket_assignment_events")).containsEntry("actor_user_id",id).containsEntry("assignee_user_id",id);
    }
    @ParameterizedTest @ValueSource(strings={"disabled","blocked","revoked","viewer"})
    void currentDatabaseStatusAndOperateGrantRemainRequired(String kind) throws Exception {
        switch(kind){case "disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=7");case "blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=7");case "revoked"->jdbc.update("DELETE FROM business_memberships WHERE user_id=7");default->jdbc.update("UPDATE business_memberships SET role_code='VIEWER' WHERE user_id=7");}
        var before=snapshot();assertThat(write(bound(7,"caller"),false)).isFalse();assertThat(snapshot()).isEqualTo(before);
    }
    @Test void noPublicStringOnlyWriterRemains(){assertThat(Arrays.stream(BusinessTicketAssignmentService.class.getMethods()).filter(m->m.getName().equals("change")).map(m->List.of(m.getParameterTypes()))).noneMatch(types->types.contains(String.class));}
    @Test void waitingForTheTicketLockCannotRebindTheActorAfterRename() throws Exception {
        var pool=Executors.newSingleThreadExecutor();var auth=bound(7,"caller");
        try(var blocker=jdbc.getDataSource().getConnection()){
            blocker.setAutoCommit(false);
            try(var lock=blocker.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?,0))")){lock.setString(1,RAW);lock.execute();}
            var future=pool.submit(()->write(auth,false));
            try{
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);boolean waiting=false;
                while(System.nanoTime()<deadline&&!future.isDone()){if(jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event='advisory'",Integer.class)>0){waiting=true;break;}Thread.sleep(10);}
                assertThat(waiting).as("writer is actually waiting for the transaction advisory lock").isTrue();
                jdbc.update("UPDATE users SET username='renamed' WHERE id=7");replacement("caller");
            }finally{blocker.commit();}
            assertThat(future.get(15,TimeUnit.SECONDS)).isFalse();assertThat(jdbc.queryForObject("SELECT count(*) FROM business_ticket_assignment_events",Integer.class)).isZero();assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_responsibles",Integer.class)).isZero();assertThat(jdbc.queryForObject("SELECT count(*) FROM ui_event_outbox",Integer.class)).isZero();assertThat(jdbc.queryForObject("SELECT responsibility_revision FROM tickets",Long.class)).isZero();
        }finally{pool.shutdownNow();}
    }
}
