package com.example.panel.service;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.security.PanelUserPrincipal;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real SQL and locks. The test-only bridge runs unchanged cases against both API versions. */
class BusinessWorkActorIdentityPostgresTest {
    private static final String RAW=" R203/Ю?%#+ ",PUBLIC="a".repeat(32);
    private final UiEventTicketScope scope=new UiEventTicketScope(42L,RAW,PUBLIC,"BUSINESS",11L);
    private JdbcTemplate jdbc;
    private BusinessTicketWorkService service;
    @BeforeEach void fixture() throws Exception {
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("work_actor_identity");
        jdbc.execute("""
            CREATE TABLE users(id BIGINT PRIMARY KEY,username TEXT,enabled BOOLEAN DEFAULT TRUE,is_blocked BOOLEAN DEFAULT FALSE);
            CREATE TABLE businesses(id BIGINT PRIMARY KEY,status TEXT DEFAULT 'active');
            CREATE TABLE business_memberships(user_id BIGINT,business_id BIGINT,role_code TEXT,is_active BOOLEAN DEFAULT TRUE);
            CREATE TABLE business_access_grants(user_id BIGINT,grant_scope TEXT,role_code TEXT,is_active BOOLEAN DEFAULT TRUE);
            CREATE TABLE tickets(user_id BIGINT,ticket_id TEXT,ticket_public_id TEXT,business_id BIGINT,is_general BOOLEAN DEFAULT FALSE,
                channel_id BIGINT,status TEXT DEFAULT 'open',resolved_at TIMESTAMPTZ,resolved_by TEXT,closed_count INT DEFAULT 4,
                reopen_count INT DEFAULT 7,last_reopen_at TIMESTAMPTZ,PRIMARY KEY(user_id,ticket_id));
            CREATE TABLE ticket_categories(ticket_id TEXT,category TEXT);
            CREATE TABLE ui_event_outbox(id BIGINT PRIMARY KEY,event_type TEXT,ticket_id TEXT,channel_id BIGINT,created_at TIMESTAMPTZ,ticket_user_id BIGINT,ticket_public_id TEXT,ticket_scope_kind TEXT,ticket_business_id BIGINT);
            INSERT INTO users(id,username) VALUES(7,'caller'),(99,'foreign');
            INSERT INTO businesses(id) VALUES(11),(22);
            INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(7,11,'OPERATOR'),(99,22,'ADMIN');
            """);
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/postgresql/V81__business_ticket_manual_work.sql")));
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id,channel_id) VALUES(42,?,?,11,9)",RAW,PUBLIC);
        jdbc.update("INSERT INTO ticket_categories VALUES(?,'legacy sentinel')",RAW);
        var flags=new MultibusinessFeatureFlags();flags.getFoundation().setEnabled(true);flags.setUiSelector(true);
        service=new BusinessTicketWorkService(jdbc,flags,new UiEventOutboxAppendService(jdbc,new LegacyTicketIdJdbcGuard(jdbc)),new ObjectMapper());
    }
    private Authentication bound(long id,String username){return UsernamePasswordAuthenticationToken.authenticated(new PanelUserPrincipal(id,username,"unused",true,List.of()),null,List.of());}
    private void prepare(String action){if(action.equals("reopen"))jdbc.update("UPDATE tickets SET status='resolved',business_categories='[\"Existing\"]',resolved_by='earlier' WHERE user_id=42");}
    private long revision(String action){return action.equals("reopen")?1L:0L;}
    private String status(String action){return action.equals("reopen")?"resolved":"open";}
    private boolean write(Authentication authentication,String action) throws Exception {
        var categories=action.equals("reopen")?List.<String>of():List.of("New");
        try {
            try{return (boolean)BusinessTicketWorkService.class.getMethod("change",UiEventTicketScope.class,Authentication.class,long.class,String.class,String.class,List.class).invoke(service,scope,authentication,revision(action),status(action),action,categories);}
            catch(NoSuchMethodException oldApi){return (boolean)BusinessTicketWorkService.class.getMethod("change",UiEventTicketScope.class,String.class,long.class,String.class,String.class,List.class).invoke(service,scope,authentication==null?null:authentication.getName(),revision(action),status(action),action,categories);}
        }catch(InvocationTargetException ex){if(ex.getCause() instanceof Exception cause)throw cause;throw new AssertionError(ex.getCause());}
    }
    private Map<String,List<Map<String,Object>>> snapshot(){var result=new LinkedHashMap<String,List<Map<String,Object>>>();for(String table:List.of("tickets","ticket_categories","business_ticket_work_events","ui_event_outbox"))result.put(table,jdbc.queryForList("SELECT * FROM "+table+" ORDER BY 1,2"));return result;}
    private void replacement(String name){jdbc.update("INSERT INTO users(id,username) VALUES(55,?)",name);jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(55,11,'OPERATOR')");}
    static Stream<Arguments> substitutions(){return Stream.of("wrong-id","missing-id","zero-id","negative-id","case-changed","padded","renamed","deleted").flatMap(kind->Stream.of("close","reopen","categories").map(action->Arguments.of(kind,action)));}
    @ParameterizedTest @MethodSource("substitutions")
    void anAccountCannotBeSubstitutedByItsCurrentOrReusedName(String kind,String action) throws Exception {
        prepare(action);long id=switch(kind){case "wrong-id"->99;case "missing-id"->404;case "zero-id"->0;case "negative-id"->-7;default->7;};String name="caller";
        if(kind.equals("case-changed"))name="CALLER";
        if(kind.equals("padded")){name=" caller ";replacement(name);}
        if(kind.equals("renamed")){jdbc.update("UPDATE users SET username='renamed' WHERE id=7");replacement("caller");}
        if(kind.equals("deleted")){jdbc.update("DELETE FROM users WHERE id=7");replacement("caller");}
        var before=snapshot();assertThat(write(bound(id,name),action)).isFalse();assertThat(snapshot()).isEqualTo(before);
    }
    static Stream<Arguments> invalidAuthentications(){return Stream.of("unbound","anonymous","unauthenticated","disabled-principal","null").flatMap(kind->Stream.of("close","reopen","categories").map(action->Arguments.of(kind,action)));}
    @ParameterizedTest @MethodSource("invalidAuthentications")
    void invalidAuthenticationCannotWriteEvenWhenItsNameIsAnOperator(String kind,String action) throws Exception {
        prepare(action);Authentication auth=switch(kind){case "unbound"->UsernamePasswordAuthenticationToken.authenticated("caller",null,List.of());case "anonymous"->new AnonymousAuthenticationToken("fixture","caller",AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));case "unauthenticated"->UsernamePasswordAuthenticationToken.unauthenticated(new PanelUserPrincipal(7,"caller","unused",true,List.of()),null);case "disabled-principal"->UsernamePasswordAuthenticationToken.authenticated(new PanelUserPrincipal(7,"caller","unused",false,List.of()),null,List.of());default->null;};
        var before=snapshot();assertThat(write(auth,action)).isFalse();assertThat(snapshot()).isEqualTo(before);
    }
    static Stream<Arguments> literalNames(){return Stream.of("caller","Caller"," caller ","имя","anonymousUser","9007199254740993").flatMap(name->Stream.of("close","reopen","categories").map(action->Arguments.of(name,action)));}
    @ParameterizedTest @MethodSource("literalNames")
    void validLiteralNamesRetainTheSameNumericActorAndRawAudit(String name,String action) throws Exception {
        prepare(action);jdbc.update("UPDATE users SET username=? WHERE id=7",name);assertThat(write(bound(7,name),action)).isTrue();
        String next=action.equals("close")?"resolved":action.equals("reopen")?"pending":"open";
        assertThat(jdbc.queryForMap("SELECT actor_user_id,actor,action,from_status,to_status,revision,previous_categories::text,categories::text FROM business_ticket_work_events"))
            .containsEntry("actor_user_id",7L).containsEntry("actor",name).containsEntry("action",action).containsEntry("from_status",status(action)).containsEntry("to_status",next).containsEntry("revision",revision(action)+1)
            .containsEntry("previous_categories",action.equals("reopen")?"[\"Existing\"]":"[]").containsEntry("categories",action.equals("reopen")?"[\"Existing\"]":"[\"New\"]");
        assertThat(jdbc.queryForMap("SELECT status,business_work_revision,resolved_by,closed_count,reopen_count FROM tickets"))
            .containsEntry("status",next).containsEntry("business_work_revision",revision(action)+1).containsEntry("resolved_by",action.equals("close")?name:null)
            .containsEntry("closed_count",action.equals("close")?5:4).containsEntry("reopen_count",action.equals("reopen")?8:7);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ui_event_outbox WHERE event_type='business_work_changed' AND ticket_user_id=42 AND ticket_id=? AND ticket_public_id=? AND channel_id=9 AND ticket_scope_kind='BUSINESS' AND ticket_business_id=11",Integer.class,RAW,PUBLIC)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT category FROM ticket_categories",String.class)).isEqualTo("legacy sentinel");
    }
    static Stream<Arguments> numericIds(){return Stream.of(7L,9007199254740993L,Long.MAX_VALUE).flatMap(id->Stream.of("close","reopen","categories").map(action->Arguments.of(id,action)));}
    @ParameterizedTest @MethodSource("numericIds")
    void numericActorIdsKeepTheirExactBigintValue(long id,String action) throws Exception {
        prepare(action);jdbc.update("UPDATE users SET id=? WHERE id=7",id);jdbc.update("UPDATE business_memberships SET user_id=? WHERE user_id=7",id);
        assertThat(write(bound(id,"caller"),action)).isTrue();assertThat(jdbc.queryForObject("SELECT actor_user_id FROM business_ticket_work_events",Long.class)).isEqualTo(id);
    }
    static Stream<Arguments> revokedActors(){return Stream.of("disabled","blocked","revoked","viewer","ambiguous","archived").flatMap(kind->Stream.of("close","reopen","categories").map(action->Arguments.of(kind,action)));}
    @ParameterizedTest @MethodSource("revokedActors")
    void currentDatabaseStatusAndOperateGrantRemainRequired(String kind,String action) throws Exception {
        prepare(action);switch(kind){case "disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=7");case "blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=7");case "revoked"->jdbc.update("DELETE FROM business_memberships WHERE user_id=7");case "viewer"->jdbc.update("UPDATE business_memberships SET role_code='VIEWER' WHERE user_id=7");case "ambiguous"->replacement("CALLER");default->jdbc.update("UPDATE businesses SET status='archived' WHERE id=11");}
        var before=snapshot();assertThat(write(bound(7,"caller"),action)).isFalse();assertThat(snapshot()).isEqualTo(before);
    }
    @Test void noPublicStringOnlyWriterRemains(){assertThat(Arrays.stream(BusinessTicketWorkService.class.getMethods()).filter(m->m.getName().equals("change")).map(m->List.of(m.getParameterTypes()))).noneMatch(types->types.get(1).equals(String.class));}
    static Stream<Arguments> lockWaits(){return Stream.of(false,true).flatMap(delete->Stream.of("close","reopen","categories").map(action->Arguments.of(delete,action)));}
    @ParameterizedTest @MethodSource("lockWaits")
    void waitingForTheTicketLockCannotRebindTheActorAfterRenameOrDelete(boolean delete,String action) throws Exception {
        prepare(action);var before=snapshot();var pool=Executors.newSingleThreadExecutor();var auth=bound(7,"caller");
        try(var blocker=jdbc.getDataSource().getConnection()){
            blocker.setAutoCommit(false);
            try(var lock=blocker.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?,0))")){lock.setString(1,RAW);lock.execute();}
            var future=pool.submit(()->write(auth,action));
            try{
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);boolean waiting=false;
                while(System.nanoTime()<deadline&&!future.isDone()){if(jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event='advisory'",Integer.class)>0){waiting=true;break;}Thread.sleep(10);}
                assertThat(waiting).as("writer is actually waiting for the transaction advisory lock").isTrue();
                if(delete)jdbc.update("DELETE FROM users WHERE id=7");else jdbc.update("UPDATE users SET username='renamed' WHERE id=7");replacement("caller");
            }finally{blocker.commit();}
            assertThat(future.get(15,TimeUnit.SECONDS)).isFalse();assertThat(snapshot()).isEqualTo(before);
        }finally{pool.shutdownNow();}
    }
    static Stream<Arguments> sqlBoundaries(){return Stream.of("update","confirmation").flatMap(site->Stream.of("close","reopen","categories").map(action->Arguments.of(site,action)));}
    @ParameterizedTest @MethodSource("sqlBoundaries")
    void finalUpdateAndConfirmationRequireTheSameExactRawName(String site,String action) throws Exception {
        prepare(action);var before=snapshot();var named=spy(new NamedParameterJdbcTemplate(jdbc));var fired=new java.util.concurrent.atomic.AtomicBoolean();
        doAnswer(call->{String sql=call.getArgument(0);boolean boundary=site.equals("update")?sql.startsWith("UPDATE tickets owner SET"):sql.startsWith("SELECT owner.business_work_revision");
            // Same transaction injection, not an unlocked concurrent update of the already locked account.
            if(boundary&&fired.compareAndSet(false,true))jdbc.update("UPDATE users SET username='CALLER' WHERE id=7");return call.callRealMethod();
        }).when(named).query(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any(SqlParameterSource.class),org.mockito.ArgumentMatchers.any(org.springframework.jdbc.core.RowMapper.class));
        ReflectionTestUtils.setField(service,"jdbc",named);
        assertThat(write(bound(7,"caller"),action)).isFalse();assertThat(fired).isTrue();assertThat(snapshot()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT username FROM users WHERE id=7",String.class)).isEqualTo("caller");
    }
}
