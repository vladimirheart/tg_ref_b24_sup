package com.example.panel.controller;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.*;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.*;
import org.springframework.web.util.HtmlUtils;

@SpringBootTest @AutoConfigureMockMvc
class BusinessTicketAssignmentHttpPostgresTest {
    @MockBean BotProcessService botProcessService;
    @MockBean(name="taskScheduler") TaskScheduler taskScheduler;
    @DynamicPropertySource static void postgres(DynamicPropertyRegistry registry) throws Exception {
        var ds=(DriverManagerDataSource)PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("business_assignment_http").getDataSource();
        var config=Files.createTempDirectory("r159-config-");var storage=Files.createTempDirectory("r159-storage-");
        registry.add("app.datasource.mode",()->"postgresql");registry.add("spring.datasource.url",ds::getUrl);
        registry.add("spring.datasource.username",ds::getUsername);registry.add("spring.datasource.password",ds::getPassword);
        registry.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        registry.add("spring.flyway.locations",()->"classpath:db/migration/postgresql");
        registry.add("shared-config.dir",config::toString);
        for(String key:List.of("attachments","knowledge-base","passport-photos","avatars"))
            registry.add("app.storage."+key,()->storage.resolve(key).toString());
        registry.add("app.storage.object.required-for-postgresql",()->"false");
        registry.add("app.coordination.required-for-postgresql",()->"false");
        registry.add("app.bots.auto-start-enabled",()->"false");
        registry.add("app.bots.internal-api.token",()->"r159-test-internal-bot-api-token");
        registry.add("app.security.remember-me-key",()->"r159-test-remember-me-key");
        registry.add("app.security.bootstrap-admin.username",()->"r159_test_bootstrap_admin");
        registry.add("app.security.bootstrap-admin.password",()->"r159-test-bootstrap-password");
        registry.add("monitoring.credentials.master-key",()->"r159-test-monitoring-master-key");
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired MultibusinessFeatureFlags flags;
    @SpyBean BusinessTicketAssignmentService service;
    @Autowired ScopedSlaAssignmentService sla;
    @SpyBean DialogResponsibilityService legacyAssignments;
    private long business,other,actorId,targetId,viewerId,foreignId;
    private String actor,target,viewer,foreign,raw,publicId,url;
    private UiEventTicketScope scope;
    @BeforeEach void fixture(){
        assertThat(jdbc.queryForObject("SELECT current_schema()",String.class)).startsWith("business_assignment_http_");
        flags.getFoundation().setEnabled(true);flags.setUiSelector(true);flags.setStrictReadScope(true);
        String suffix=UUID.randomUUID().toString().replace("-","");
        business=jdbc.queryForObject("INSERT INTO businesses(code,display_name) VALUES(?,'Same label') RETURNING id",Long.class,suffix);
        other=jdbc.queryForObject("INSERT INTO businesses(code,display_name) VALUES(?,'Same label') RETURNING id",Long.class,suffix+"-b");
        actor="operator-"+suffix;target="target-"+suffix;viewer="viewer-"+suffix;foreign="foreign-"+suffix;
        actorId=account(actor);targetId=account(target);viewerId=account(viewer);foreignId=account(foreign);
        jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(?,?,'OPERATOR'),(?,?,'OPERATOR'),(?,?,'VIEWER'),(?,?,'ADMIN')",
            actorId,business,targetId,business,viewerId,business,foreignId,other);
        raw=" Raw/?=поле "+suffix+" ";publicId=suffix;url="/business-dialog-assignment/"+publicId;
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id,status) VALUES(901,?,?,?,'open')",raw,publicId,business);
        scope=new UiEventTicketScope(901L,raw,publicId,"BUSINESS",business);
    }
    private long account(String name){return jdbc.queryForObject("INSERT INTO users(username,password) VALUES(?,'unused') RETURNING id",Long.class,name);}
    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request,String identity,String... authorities){
        return request.with(user(identity).authorities(Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }
    private MockHttpServletRequestBuilder command(String action,long revision){
        return post(url+"/"+action).param("ownerId","901").param("rawId",raw).param("businessId",Long.toString(business))
            .param("revision",Long.toString(revision)).param("targetId",Long.toString(targetId));
    }
    private long revision(){return jdbc.queryForObject("SELECT responsibility_revision FROM tickets WHERE user_id=901 AND ticket_id=?",Long.class,raw);}
    private long events(){return jdbc.queryForObject("SELECT count(*) FROM business_ticket_assignment_events WHERE ticket_id=?",Long.class,raw);}
    private long outbox(){return jdbc.queryForObject("SELECT count(*) FROM ui_event_outbox WHERE ticket_id=?",Long.class,raw);}
    private void legacy(String name){jdbc.update("INSERT INTO ticket_responsibles(ticket_id,responsible) VALUES(?,?) ON CONFLICT(ticket_id) DO UPDATE SET responsible=EXCLUDED.responsible",raw,name);}
    private String hidden(String html,String name){
        var match=Pattern.compile("name=\""+name+"\"[^>]*value=\"([^\"]*)\"").matcher(html);
        assertThat(match.find()).as("hidden "+name).isTrue();return HtmlUtils.htmlUnescape(match.group(1));
    }
    @Test void renderedFormCsrfAssignReloadHistoryAndFrozenOutboxWithoutProviderSideEffects() throws Exception {
        var opened=mvc.perform(auth(get(url),actor,"PAGE_DIALOGS")).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control","no-store")).andReturn();
        String html=opened.getResponse().getContentAsString();
        assertThat(html).contains(actor,target).doesNotContain(foreign,viewer);
        assertThat(hidden(html,"rawId")).isEqualTo(raw);
        mvc.perform(auth(post(url+"/assign").param("ownerId",hidden(html,"ownerId")).param("rawId",hidden(html,"rawId"))
            .param("businessId",hidden(html,"businessId")).param("revision",hidden(html,"revision"))
            .param("targetId",Long.toString(targetId)).param("_csrf",hidden(html,"_csrf")).cookie(opened.getResponse().getCookies()),actor,"PAGE_DIALOGS"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(url));
        assertThat(revision()).isEqualTo(1);assertThat(events()).isEqualTo(1);assertThat(outbox()).isEqualTo(1);
        var event=jdbc.queryForMap("SELECT * FROM business_ticket_assignment_events WHERE ticket_id=?",raw);
        assertThat(event).containsEntry("business_id",business).containsEntry("actor_user_id",actorId).containsEntry("assignee_user_id",targetId)
            .containsEntry("ticket_user_id",901L).containsEntry("ticket_public_id",publicId).containsEntry("revision",1L);
        var notice=jdbc.queryForMap("SELECT * FROM ui_event_outbox WHERE ticket_id=?",raw);
        assertThat(notice).containsEntry("event_type","business_assignment_changed").containsEntry("ticket_business_id",business)
            .containsEntry("ticket_user_id",901L).containsEntry("ticket_public_id",publicId).containsEntry("ticket_scope_kind","BUSINESS");
        mvc.perform(auth(get(url),viewer,"PAGE_DIALOGS")).andExpect(status().isOk()).andExpect(content().string(containsString(target)));
        assertThat(service.candidates(scope,viewer)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dialog_action_audit WHERE ticket_id=?",Long.class,raw)).isZero();
        mvc.perform(auth(command("assign",0),actor,"PAGE_DIALOGS").with(csrf())).andExpect(status().isConflict());
        assertThat(events()).isEqualTo(1);
    }
    @Test void takeUsesAuthenticatedActorNotSubmittedTargetAndCannotTurnNoOpIntoSuccess() throws Exception {
        mvc.perform(auth(command("take",0),actor,"PAGE_DIALOGS").with(csrf())).andExpect(status().is3xxRedirection());
        assertThat(jdbc.queryForObject("SELECT responsible FROM ticket_responsibles WHERE ticket_id=?",String.class,raw)).isEqualTo(actor);
        mvc.perform(auth(command("take",1),actor,"PAGE_DIALOGS").with(csrf())).andExpect(status().isConflict());
        assertThat(revision()).isEqualTo(1);assertThat(events()).isEqualTo(1);assertThat(outbox()).isEqualTo(1);
    }
    @Test void pageAuthorityCsrfAndBusinessOperateAreIndependent() throws Exception {
        mvc.perform(auth(get(url),actor,"ROLE_ADMIN")).andExpect(status().isForbidden());
        mvc.perform(auth(command("assign",0),actor,"PAGE_DIALOGS")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/error/403"));
        assertThat(events()).isZero();assertThat(outbox()).isZero();assertThat(revision()).isZero();
        mvc.perform(auth(command("assign",0),viewer,"PAGE_DIALOGS").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(auth(get(url),foreign,"PAGE_DIALOGS","ROLE_ADMIN")).andExpect(status().isNotFound());
        mvc.perform(auth(post(url+"/assign").param("ownerId","901").param("rawId",raw).param("businessId",Long.toString(business))
            .param("revision","0").param("targetId",Long.toString(foreignId)),actor,"PAGE_DIALOGS").with(csrf())).andExpect(status().isConflict());
        assertThat(events()).isZero();assertThat(outbox()).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"target-viewer","target-foreign","target-disabled","target-blocked","target-duplicate","target-revoked","actor-viewer","actor-disabled","actor-blocked","actor-duplicate","actor-revoked","business-archived","owner","recreated","collision","general","closed","flag"})
    void finalStatementRejectsLateOwnerOrAccountChanges(String mutation){
        switch(mutation){
            case "target-viewer"->jdbc.update("UPDATE business_memberships SET role_code='VIEWER' WHERE user_id=?",targetId);
            case "target-foreign"->jdbc.update("UPDATE business_memberships SET business_id=? WHERE user_id=?",other,targetId);
            case "target-disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=?",targetId);
            case "target-blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=?",targetId);
            case "target-duplicate"->account(target.toUpperCase(Locale.ROOT));
            case "target-revoked"->jdbc.update("DELETE FROM business_memberships WHERE user_id=?",targetId);
            case "actor-viewer"->jdbc.update("UPDATE business_memberships SET role_code='VIEWER' WHERE user_id=?",actorId);
            case "actor-disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=?",actorId);
            case "actor-blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=?",actorId);
            case "actor-duplicate"->account(actor.toUpperCase(Locale.ROOT));
            case "actor-revoked"->jdbc.update("DELETE FROM business_memberships WHERE user_id=?",actorId);
            case "business-archived"->jdbc.update("UPDATE businesses SET status='archived' WHERE id=?",business);
            case "owner"->jdbc.update("UPDATE tickets SET business_id=? WHERE ticket_id=?",other,raw);
            case "recreated"->{jdbc.update("DELETE FROM tickets WHERE ticket_id=?",raw);jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id,status) VALUES(901,?,?,?,'open')",raw,"f".repeat(32),business);}
            case "collision"->jdbc.update("INSERT INTO tickets(user_id,ticket_id,business_id) VALUES(902,?,?)",raw,other);
            case "general"->jdbc.update("UPDATE tickets SET business_id=NULL,is_general=TRUE,general_selection_source='MANUAL' WHERE ticket_id=?",raw);
            case "closed"->jdbc.update("UPDATE tickets SET status='closed' WHERE ticket_id=?",raw);
            case "flag"->flags.setUiSelector(false);
        }
        assertThat(service.change(scope,actor,0,targetId,false)).isFalse();
        assertThat(events()).isZero();assertThat(outbox()).isZero();assertThat(revision()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_responsibles WHERE ticket_id=?",Long.class,raw)).isZero();
    }
    @Test void revokeAfterControllerReadStillCannotWriteOrAuditSuccess() throws Exception {
        doAnswer(call->{jdbc.update("UPDATE business_memberships SET role_code='VIEWER' WHERE user_id=?",actorId);return call.callRealMethod();})
            .when(service).change(eq(scope),eq(actor),eq(0L),eq(targetId),eq(false));
        mvc.perform(auth(command("assign",0),actor,"PAGE_DIALOGS").with(csrf())).andExpect(status().isConflict());
        assertThat(events()).isZero();assertThat(outbox()).isZero();assertThat(revision()).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"aba","delete-aba","new-owner","tuple","revision"})
    void staleOrForgedFormCannotTargetAnotherRootOrOverrideAnotherWriter(String kind) throws Exception {
        var request=command("assign",0);
        if(kind.equals("aba")){legacy(actor);legacy(target);jdbc.update("DELETE FROM ticket_responsibles WHERE ticket_id=?",raw);}
        if(kind.equals("delete-aba")){legacy(actor);jdbc.update("DELETE FROM ticket_responsibles WHERE ticket_id=?",raw);}
        if(kind.equals("new-owner"))request=post(url+"/assign").param("ownerId","902").param("rawId",raw).param("businessId",Long.toString(business)).param("revision","0").param("targetId",Long.toString(targetId));
        if(kind.equals("tuple"))request=post(url+"/assign").param("ownerId","901").param("rawId",raw.trim()).param("businessId",Long.toString(business)).param("revision","0").param("targetId",Long.toString(targetId));
        if(kind.equals("revision"))request=command("assign",9);
        mvc.perform(auth(request,actor,"PAGE_DIALOGS").with(csrf())).andExpect(status().isConflict());
        assertThat(events()).isZero();assertThat(outbox()).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"audit","outbox"})
    void anyPostAssignmentFailureRollsBackRevisionProjectionAuditAndInvalidation(String failure){
        String table=failure.equals("audit")?"business_ticket_assignment_events":"ui_event_outbox";
        jdbc.execute("CREATE FUNCTION fail_r159_insert() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'synthetic R159 failure'; END; $$");
        jdbc.execute("CREATE TRIGGER fail_r159_insert BEFORE INSERT ON "+table+" FOR EACH ROW EXECUTE FUNCTION fail_r159_insert()");
        try{assertThat(service.change(scope,actor,0,targetId,false)).isFalse();assertThat(revision()).isZero();assertThat(events()).isZero();assertThat(outbox()).isZero();}
        finally{jdbc.execute("DROP TRIGGER fail_r159_insert ON "+table);jdbc.execute("DROP FUNCTION fail_r159_insert()");}
        assertThat(service.change(scope,actor,0,targetId,false)).isTrue();
    }
    @Test void concurrentCommandsCommitOneTransition() throws Exception {
        var start=new CountDownLatch(1);
        java.util.function.Supplier<Boolean> attempt=()->{try{start.await();}catch(InterruptedException ex){throw new IllegalStateException(ex);}return service.change(scope,actor,0,targetId,false);};
        var first=CompletableFuture.supplyAsync(attempt);var second=CompletableFuture.supplyAsync(attempt);start.countDown();
        assertThat(List.of(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        assertThat(events()).isEqualTo(1);assertThat(outbox()).isEqualTo(1);assertThat(revision()).isEqualTo(1);
    }
    @Test void historyIsAppendOnlyAndDoesNotFollowChangedBusinessOrNewGeneration(){
        assertThat(service.change(scope,actor,0,targetId,false)).isTrue();
        assertThat(service.recent(scope,viewer)).hasSize(1);
        assertThatThrownBy(()->jdbc.update("UPDATE business_ticket_assignment_events SET actor='changed' WHERE ticket_id=?",raw)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->jdbc.update("DELETE FROM business_ticket_assignment_events WHERE ticket_id=?",raw)).isInstanceOf(RuntimeException.class);
        jdbc.update("UPDATE tickets SET business_id=? WHERE ticket_id=?",other,raw);
        var replacement=new UiEventTicketScope(901L,raw,publicId,"BUSINESS",other);
        assertThat(service.recent(replacement,foreign)).isEmpty();assertThat(service.recent(scope,viewer)).isEmpty();
    }
    @Test void allBusinessOperateGrantIsAcceptedButViewerGlobalGrantCannotAssign(){
        jdbc.update("DELETE FROM business_memberships WHERE user_id IN (?,?)",actorId,targetId);
        jdbc.update("INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES(?,'ALL_BUSINESSES','OPERATOR'),(?,'ALL_BUSINESSES','VIEWER')",actorId,targetId);
        assertThat(service.change(scope,actor,0,targetId,false)).isFalse();
        jdbc.update("UPDATE business_access_grants SET role_code='MANAGER' WHERE user_id=?",targetId);
        assertThat(service.change(scope,actor,0,targetId,false)).isTrue();
    }
    @Test void manualAndSlaShareRevisionWithoutReusingEachOthersAuditOrRawWorkflow(){
        long channel=jdbc.queryForObject("INSERT INTO channels(token,channel_name,platform) VALUES(?,'Telegram','telegram') RETURNING id",Long.class,UUID.randomUUID().toString());
        jdbc.update("UPDATE tickets SET channel_id=? WHERE ticket_id=?",channel,raw);
        var frozen=new DialogLookupReadService.ScopedSlaDialog(null,scope,channel,0,null);
        assertThat(service.change(scope,actor,0,null,true)).isTrue();
        assertThat(sla.assign(frozen,target,"scheduler","rules","r")).isFalse();
        var fresh=new DialogLookupReadService.ScopedSlaDialog(null,scope,channel,1,actor);
        assertThat(sla.assign(fresh,target,"scheduler","rules","r")).isTrue();
        assertThat(service.change(scope,actor,1,null,true)).isFalse();
        assertThat(revision()).isEqualTo(2);assertThat(events()).isEqualTo(1);assertThat(outbox()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM scoped_sla_assignment_events WHERE ticket_id=?",Long.class,raw)).isEqualTo(1);
    }
    @Test void upgradeV77ToV78PreservesExistingRootsAssignmentsAndCreatesNoInventedHistory(){
        var old=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("r159_upgrade");
        org.flywaydb.core.Flyway.configure().dataSource(old.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql").target("77").load().migrate();
        old.update("INSERT INTO tickets(user_id,ticket_id,status) VALUES(808,'old-manual','open')");
        old.update("INSERT INTO ticket_responsibles(ticket_id,responsible,assigned_by,last_read_at) VALUES('old-manual',' Original ','human',CURRENT_TIMESTAMP)");
        var roots=old.queryForList("SELECT * FROM tickets");var assignments=old.queryForList("SELECT * FROM ticket_responsibles");
        org.flywaydb.core.Flyway.configure().dataSource(old.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql").load().migrate();
        assertThat(old.queryForList("SELECT * FROM tickets")).isEqualTo(roots);
        assertThat(old.queryForList("SELECT * FROM ticket_responsibles")).isEqualTo(assignments);
        assertThat(old.queryForObject("SELECT count(*) FROM business_ticket_assignment_events",Long.class)).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"bad","9223372036854775808","-1"})
    void malformedOrNegativeRevisionCannotWrite(String value) throws Exception {
        var request=post(url+"/assign").param("ownerId","901").param("rawId",raw).param("businessId",Long.toString(business))
            .param("revision",value).param("targetId",Long.toString(targetId));
        mvc.perform(auth(request,actor,"PAGE_DIALOGS").with(csrf()))
            .andExpect(value.equals("-1")?status().isConflict():status().isBadRequest());
        assertThat(events()).isZero();assertThat(outbox()).isZero();assertThat(revision()).isZero();
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource({"business,take,path","business,take,query","business,reassign,path","business,reassign,query",
        "general,take,path","general,take,query","general,reassign,path","general,reassign,query",
        "strict,take,path","strict,take,query","strict,reassign,path","strict,reassign,query"})
    void rawAssignmentRoutesCannotBypassScopedWorkflowEvenWithPageAndSystemAdmin(String kind,String action,String route) throws Exception {
        flags.setStrictReadScope(false);
        if(route.equals("path")){String safe="r160-"+publicId;jdbc.update("UPDATE tickets SET ticket_id=? WHERE ticket_id=?",safe,raw);raw=safe;}
        if(kind.equals("business"))flags.getFoundation().setEnabled(false); // Disabling the foundation cannot authorize a raw BUSINESS writer.
        if(kind.equals("general"))jdbc.update("UPDATE tickets SET business_id=NULL,is_general=TRUE,general_selection_source='MANUAL' WHERE ticket_id=?",raw);
        if(kind.equals("strict")){jdbc.update("UPDATE tickets SET business_id=NULL WHERE ticket_id=?",raw);flags.setStrictReadScope(true);}
        var request=route.equals("path")?post("/api/dialogs/{id}/"+action,raw):post("/api/dialog-ticket/"+action).param("ticketId",raw);
        mvc.perform(auth(request,actor,"PAGE_DIALOGS","ROLE_ADMIN").with(csrf()).contentType("application/json").content("{\"username\":\""+target+"\"}"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_responsibles WHERE ticket_id=?",Long.class,raw)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dialog_action_audit WHERE ticket_id=?",Long.class,raw)).isZero();
        assertThat(events()).isZero();assertThat(outbox()).isZero();assertThat(revision()).isZero();
    }
    @Test void rawCapabilityCannotBeReusedAfterTheRootBecomesBusiness() throws Exception {
        flags.setStrictReadScope(false);jdbc.update("UPDATE tickets SET business_id=NULL WHERE ticket_id=?",raw);
        var once=new java.util.concurrent.atomic.AtomicBoolean();
        doAnswer(call->{boolean allowed=(boolean)call.callRealMethod();if(allowed&&once.compareAndSet(false,true))jdbc.update("UPDATE tickets SET business_id=? WHERE ticket_id=?",business,raw);return allowed;})
            .when(legacyAssignments).canAssignLegacyResponsible(raw);
        mvc.perform(auth(post("/api/dialog-ticket/take").param("ticketId",raw),actor,"PAGE_DIALOGS").with(csrf()))
            .andExpect(status().isNotFound());
        assertThat(once).isTrue();assertThat(revision()).isZero();assertThat(events()).isZero();assertThat(outbox()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dialog_action_audit WHERE ticket_id=?",Long.class,raw)).isZero();
    }
    @Test void rawLegacyTakeRemainsAvailableBeforeStrictAndUsesTheSharedRevision() throws Exception {
        flags.setStrictReadScope(false);jdbc.update("UPDATE tickets SET business_id=NULL WHERE ticket_id=?",raw);
        mvc.perform(auth(post("/api/dialog-ticket/take").param("ticketId",raw),actor,"PAGE_DIALOGS").with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.changed").value(true));
        assertThat(jdbc.queryForObject("SELECT responsible FROM ticket_responsibles WHERE ticket_id=?",String.class,raw)).isEqualTo(actor);
        assertThat(revision()).isEqualTo(1);assertThat(events()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dialog_action_audit WHERE ticket_id=? AND action='take' AND result='success'",Long.class,raw)).isEqualTo(1);
    }
}
