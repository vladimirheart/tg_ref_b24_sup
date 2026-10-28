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
import static org.mockito.Mockito.*;

/** Real SQL/transactions; the test-only bridge runs the same frozen cases against the old API. */
class BusinessParticipantActorIdentityPostgresTest {
    private static final String RAW=" R202/Ю?%#+ ",PUBLIC="a".repeat(32);
    private final UiEventTicketScope scope=new UiEventTicketScope(42L,RAW,PUBLIC,"BUSINESS",11L);
    private JdbcTemplate jdbc;
    private MultibusinessFeatureFlags flags;
    private UiEventOutboxAppendService ids;
    private BusinessTicketParticipantService service;
    @BeforeEach void fixture() throws Exception {
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("participant_actor_identity");
        jdbc.execute("""
            CREATE TABLE users(id BIGINT PRIMARY KEY,username TEXT,enabled BOOLEAN DEFAULT TRUE,is_blocked BOOLEAN DEFAULT FALSE);
            CREATE TABLE businesses(id BIGINT PRIMARY KEY,status TEXT DEFAULT 'active');
            CREATE TABLE business_memberships(user_id BIGINT,business_id BIGINT,role_code TEXT,is_active BOOLEAN DEFAULT TRUE);
            CREATE TABLE business_access_grants(user_id BIGINT,grant_scope TEXT,role_code TEXT,is_active BOOLEAN DEFAULT TRUE);
            CREATE TABLE tickets(user_id BIGINT,ticket_id TEXT,ticket_public_id TEXT,business_id BIGINT,is_general BOOLEAN DEFAULT FALSE,channel_id BIGINT,status TEXT DEFAULT 'open',PRIMARY KEY(user_id,ticket_id));
            CREATE TABLE ticket_participants(ticket_id TEXT,username TEXT);
            CREATE TABLE ui_event_outbox(id BIGINT PRIMARY KEY,event_type TEXT,ticket_id TEXT,channel_id BIGINT,created_at TIMESTAMPTZ,ticket_user_id BIGINT,ticket_public_id TEXT,ticket_scope_kind TEXT,ticket_business_id BIGINT);
            INSERT INTO users(id,username) VALUES(7,'caller'),(8,'target'),(99,'foreign');
            INSERT INTO businesses(id) VALUES(11),(22);
            INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(7,11,'OPERATOR'),(8,11,'VIEWER'),(99,22,'ADMIN');
            """);
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/postgresql/V79__business_ticket_participants.sql")));
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id) VALUES(42,?,?,11)",RAW,PUBLIC);
        jdbc.update("INSERT INTO ticket_participants VALUES(?,'legacy sentinel')",RAW);
        flags=new MultibusinessFeatureFlags();flags.getFoundation().setEnabled(true);flags.setUiSelector(true);
        ids=new UiEventOutboxAppendService(jdbc,new LegacyTicketIdJdbcGuard(jdbc));
        service=new BusinessTicketParticipantService(jdbc,flags,ids);
    }
    private Authentication bound(long id,String username){return UsernamePasswordAuthenticationToken.authenticated(new PanelUserPrincipal(id,username,"unused",true,List.of()),null,List.of());}
    private boolean write(Authentication authentication,boolean add) throws Exception {
        try {
            try{return (boolean)BusinessTicketParticipantService.class.getMethod("change",UiEventTicketScope.class,Authentication.class,long.class,Long.class,boolean.class).invoke(service,scope,authentication,add?0L:1L,8L,add);}
            catch(NoSuchMethodException oldApi){return (boolean)BusinessTicketParticipantService.class.getMethod("change",UiEventTicketScope.class,String.class,long.class,Long.class,boolean.class).invoke(service,scope,authentication==null?null:authentication.getName(),add?0L:1L,8L,add);}
        }catch(InvocationTargetException ex){if(ex.getCause() instanceof Exception cause)throw cause;throw new AssertionError(ex.getCause());}
    }
    private void prepare(boolean add){if(!add){
        jdbc.update("INSERT INTO business_ticket_participants(ticket_user_id,ticket_id,ticket_public_id,business_id,member_user_id,member,actor_user_id,actor,revision) VALUES(42,?,?,11,8,'target',99,'earlier actor',1)",RAW,PUBLIC);
        jdbc.update("INSERT INTO business_ticket_participant_events(ticket_user_id,ticket_id,ticket_public_id,business_id,revision,member_user_id,member,actor_user_id,actor,action) VALUES(42,?,?,11,1,8,'target',99,'earlier actor','add')",RAW,PUBLIC);
    }}
    private Map<String,List<Map<String,Object>>> snapshot(){var result=new LinkedHashMap<String,List<Map<String,Object>>>();for(String table:List.of("tickets","ticket_participants","business_ticket_participants","business_ticket_participant_events","ui_event_outbox"))result.put(table,jdbc.queryForList("SELECT * FROM "+table+" ORDER BY 1,2"));return result;}
    private void replacement(String name){jdbc.update("INSERT INTO users(id,username) VALUES(55,?)",name);jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(55,11,'OPERATOR')");}
    static Stream<Arguments> substitutions(){return Stream.of("wrong-id","missing-id","zero-id","negative-id","case-changed","padded","renamed","deleted").flatMap(kind->Stream.of(false,true).map(add->Arguments.of(kind,add)));}
    @ParameterizedTest @MethodSource("substitutions")
    void anAccountCannotBeSubstitutedByItsCurrentOrReusedName(String kind,boolean add) throws Exception {
        prepare(add);long id=switch(kind){case "wrong-id"->99;case "missing-id"->404;case "zero-id"->0;case "negative-id"->-7;default->7;};String name="caller";
        if(kind.equals("case-changed"))name="CALLER";
        if(kind.equals("padded")){name=" caller ";replacement(name);}
        if(kind.equals("renamed")){jdbc.update("UPDATE users SET username='renamed' WHERE id=7");replacement("caller");}
        if(kind.equals("deleted")){jdbc.update("DELETE FROM users WHERE id=7");replacement("caller");}
        var before=snapshot();assertThat(write(bound(id,name),add)).isFalse();assertThat(snapshot()).isEqualTo(before);
    }
    static Stream<Arguments> invalidAuthentications(){return Stream.of("unbound","anonymous","unauthenticated","disabled-principal","null").flatMap(kind->Stream.of(false,true).map(add->Arguments.of(kind,add)));}
    @ParameterizedTest @MethodSource("invalidAuthentications")
    void invalidAuthenticationCannotWriteEvenWhenItsNameIsAnOperator(String kind,boolean add) throws Exception {
        prepare(add);Authentication auth=switch(kind){case "unbound"->UsernamePasswordAuthenticationToken.authenticated("caller",null,List.of());case "anonymous"->new AnonymousAuthenticationToken("fixture","caller",AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));case "unauthenticated"->UsernamePasswordAuthenticationToken.unauthenticated(new PanelUserPrincipal(7,"caller","unused",true,List.of()),null);case "disabled-principal"->UsernamePasswordAuthenticationToken.authenticated(new PanelUserPrincipal(7,"caller","unused",false,List.of()),null,List.of());default->null;};
        var before=snapshot();assertThat(write(auth,add)).isFalse();assertThat(snapshot()).isEqualTo(before);
    }
    static Stream<Arguments> literalNames(){return Stream.of("caller","Caller"," caller ","имя","anonymousUser","9007199254740993").flatMap(name->Stream.of(false,true).map(add->Arguments.of(name,add)));}
    @ParameterizedTest @MethodSource("literalNames")
    void validLiteralNamesRetainTheSameNumericActorAndRawAudit(String name,boolean add) throws Exception {
        prepare(add);jdbc.update("UPDATE users SET username=? WHERE id=7",name);assertThat(write(bound(7,name),add)).isTrue();
        assertThat(jdbc.queryForMap("SELECT actor_user_id,actor,member_user_id,member,action FROM business_ticket_participant_events ORDER BY revision DESC LIMIT 1")).containsEntry("actor_user_id",7L).containsEntry("actor",name).containsEntry("member_user_id",8L).containsEntry("member","target").containsEntry("action",add?"add":"remove");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM business_ticket_participants",Integer.class)).isEqualTo(add?1:0);
        if(add)assertThat(jdbc.queryForMap("SELECT actor_user_id,actor FROM business_ticket_participants")).containsEntry("actor_user_id",7L).containsEntry("actor",name);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ui_event_outbox WHERE event_type='business_participants_changed' AND ticket_user_id=42 AND ticket_id=? AND ticket_public_id=? AND ticket_scope_kind='BUSINESS' AND ticket_business_id=11",Integer.class,RAW,PUBLIC)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT username FROM ticket_participants",String.class)).isEqualTo("legacy sentinel");
    }
    static Stream<Arguments> numericIds(){return Stream.of(7L,9007199254740993L,Long.MAX_VALUE).flatMap(id->Stream.of(false,true).map(add->Arguments.of(id,add)));}
    @ParameterizedTest @MethodSource("numericIds")
    void numericActorIdsKeepTheirExactBigintValue(long id,boolean add) throws Exception {
        prepare(add);jdbc.update("UPDATE users SET id=? WHERE id=7",id);jdbc.update("UPDATE business_memberships SET user_id=? WHERE user_id=7",id);
        assertThat(write(bound(id,"caller"),add)).isTrue();assertThat(jdbc.queryForObject("SELECT actor_user_id FROM business_ticket_participant_events ORDER BY revision DESC LIMIT 1",Long.class)).isEqualTo(id);
    }
    static Stream<Arguments> revokedActors(){return Stream.of("disabled","blocked","revoked","viewer","ambiguous","archived").flatMap(kind->Stream.of(false,true).map(add->Arguments.of(kind,add)));}
    @ParameterizedTest @MethodSource("revokedActors")
    void currentDatabaseStatusAndOperateGrantRemainRequired(String kind,boolean add) throws Exception {
        prepare(add);switch(kind){case "disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=7");case "blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=7");case "revoked"->jdbc.update("DELETE FROM business_memberships WHERE user_id=7");case "viewer"->jdbc.update("UPDATE business_memberships SET role_code='VIEWER' WHERE user_id=7");case "ambiguous"->replacement("CALLER");default->jdbc.update("UPDATE businesses SET status='archived' WHERE id=11");}
        var before=snapshot();assertThat(write(bound(7,"caller"),add)).isFalse();assertThat(snapshot()).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"disabled","revoked","deleted"})
    void removingAnIneligibleMemberStillUsesTheBoundActorAndStoredMemberSnapshot(String kind) throws Exception {
        prepare(false);switch(kind){case "disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=8");case "revoked"->jdbc.update("DELETE FROM business_memberships WHERE user_id=8");default->jdbc.update("DELETE FROM users WHERE id=8");}
        assertThat(write(bound(7,"caller"),false)).isTrue();assertThat(jdbc.queryForMap("SELECT actor_user_id,actor,member_user_id,member,action FROM business_ticket_participant_events WHERE revision=2")).containsEntry("actor_user_id",7L).containsEntry("actor","caller").containsEntry("member_user_id",8L).containsEntry("member","target").containsEntry("action","remove");
    }
    @Test void noPublicStringOnlyWriterRemains(){assertThat(Arrays.stream(BusinessTicketParticipantService.class.getMethods()).filter(m->m.getName().equals("change")).map(m->List.of(m.getParameterTypes()))).noneMatch(types->types.contains(String.class));}
    @ParameterizedTest @CsvSource({"true,false","false,false","true,true","false,true"})
    void waitingForTheTicketLockCannotRebindTheActorAfterRenameOrDelete(boolean add,boolean delete) throws Exception {
        prepare(add);var before=snapshot();var pool=Executors.newSingleThreadExecutor();var auth=bound(7,"caller");
        try(var blocker=jdbc.getDataSource().getConnection()){
            blocker.setAutoCommit(false);
            try(var lock=blocker.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?,0))")){lock.setString(1,RAW);lock.execute();}
            var future=pool.submit(()->write(auth,add));
            try{
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);boolean waiting=false;
                while(System.nanoTime()<deadline&&!future.isDone()){if(jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event='advisory'",Integer.class)>0){waiting=true;break;}Thread.sleep(10);}
                assertThat(waiting).as("writer is actually waiting for the transaction advisory lock").isTrue();
                if(delete)jdbc.update("DELETE FROM users WHERE id=7");else jdbc.update("UPDATE users SET username='renamed' WHERE id=7");replacement("caller");
            }finally{blocker.commit();}
            assertThat(future.get(15,TimeUnit.SECONDS)).isFalse();assertThat(snapshot()).isEqualTo(before);
        }finally{pool.shutdownNow();}
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void finalConditionalSqlCannotSwitchTheActorAfterTheLockingProof(boolean add) throws Exception {
        prepare(add);var before=snapshot();var observed=spy(jdbc);var fired=new java.util.concurrent.atomic.AtomicBoolean();
        doAnswer(call->{
            var creator=call.getArgument(0,org.springframework.jdbc.core.PreparedStatementCreator.class);
            String sql=((org.springframework.jdbc.core.SqlProvider)creator).getSql();
            if((sql.startsWith("INSERT INTO business_ticket_participants")||sql.startsWith("DELETE FROM business_ticket_participants"))&&fired.compareAndSet(false,true)){
                // Same-transaction JDBC-boundary mutation: no claim that a locked account can be updated by another transaction.
                jdbc.update("UPDATE users SET username='renamed' WHERE id=7");replacement("caller");
            }return call.callRealMethod();
        }).when(observed).update(org.mockito.ArgumentMatchers.any(org.springframework.jdbc.core.PreparedStatementCreator.class));
        service=new BusinessTicketParticipantService(observed,flags,ids);
        assertThat(write(bound(7,"caller"),add)).isFalse();assertThat(fired).isTrue();assertThat(snapshot()).isEqualTo(before);
    }
}
