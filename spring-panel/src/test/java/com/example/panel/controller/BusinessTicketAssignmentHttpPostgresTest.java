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
    @SpyBean BusinessTicketParticipantService participants;
    @Autowired NotificationService notices;
    @Autowired UiEventOutboxAppendService outboxIds;
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
    private MockHttpServletRequestBuilder memberCommand(String action,String revision,long id){
        return post(url+"/participants/"+action).param("ownerId","901").param("rawId",raw).param("businessId",Long.toString(business))
            .param("participantRevision",revision).param("memberId",Long.toString(id));
    }
    private long memberEvents(){return jdbc.queryForObject("SELECT count(*) FROM business_ticket_participant_events WHERE ticket_id=?",Long.class,raw);}
    @Test void renderedMemberFormAddsViewerReloadsRemovesAndDoesNotTouchLegacyAssignmentOrAi() throws Exception {
        jdbc.update("INSERT INTO ticket_participants(ticket_id,username,added_by) VALUES(?,?,'old')",raw,target);
        var oldRows=jdbc.queryForList("SELECT * FROM ticket_participants WHERE ticket_id=?",raw);
        var opened=mvc.perform(auth(get(url),actor,"PAGE_DIALOGS")).andExpect(status().isOk()).andReturn();
        var html=opened.getResponse().getContentAsString();
        assertThat(hidden(html,"participantRevision")).isEqualTo("0");
        assertThat(participants.members(scope,actor)).isEmpty();
        mvc.perform(auth(post(url+"/participants/add").param("ownerId",hidden(html,"ownerId")).param("rawId",hidden(html,"rawId"))
            .param("businessId",hidden(html,"businessId")).param("participantRevision",hidden(html,"participantRevision"))
            .param("memberId",Long.toString(viewerId)).param("_csrf",hidden(html,"_csrf")).cookie(opened.getResponse().getCookies()),actor,"PAGE_DIALOGS"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(url));
        assertThat(participants.members(scope,viewer)).singleElement().satisfies(m->{assertThat(m.id()).isEqualTo(viewerId);assertThat(m.eligible()).isTrue();});
        assertThat(participants.revision(scope,actor)).contains(1L);
        var event=jdbc.queryForMap("SELECT * FROM business_ticket_participant_events WHERE ticket_id=?",raw);
        assertThat(event).containsEntry("ticket_user_id",901L).containsEntry("ticket_public_id",publicId).containsEntry("business_id",business)
            .containsEntry("member_user_id",viewerId).containsEntry("actor_user_id",actorId).containsEntry("revision",1L).containsEntry("action","add");
        assertThat(jdbc.queryForMap("SELECT * FROM ui_event_outbox WHERE ticket_id=?",raw))
            .containsEntry("event_type","business_participants_changed").containsEntry("ticket_user_id",901L)
            .containsEntry("ticket_public_id",publicId).containsEntry("ticket_business_id",business).containsEntry("ticket_scope_kind","BUSINESS");
        var readHtml=mvc.perform(auth(get(url),viewer,"PAGE_DIALOGS")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(readHtml).contains(viewer).doesNotContain("id=\"participantTarget\"","/participants/remove");
        mvc.perform(auth(memberCommand("remove","1",viewerId),actor,"PAGE_DIALOGS").with(csrf())).andExpect(status().is3xxRedirection());
        assertThat(participants.members(scope,actor)).isEmpty();assertThat(participants.revision(scope,actor)).contains(2L);
        assertThat(memberEvents()).isEqualTo(2);assertThat(outbox()).isEqualTo(2);
        assertThat(events()).isZero();assertThat(revision()).isZero();
        assertThat(jdbc.queryForList("SELECT * FROM ticket_participants WHERE ticket_id=?",raw)).isEqualTo(oldRows);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dialog_action_audit WHERE ticket_id=?",Long.class,raw)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_responsibles WHERE ticket_id=?",Long.class,raw)).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"owner","raw","business"})
    void forgedMemberOwnerFieldsDoNotRetargetTheCommand(String field) throws Exception {
        var command=post(url+"/participants/add").param("ownerId",field.equals("owner")?"902":"901")
            .param("rawId",field.equals("raw")?raw.trim():raw).param("businessId",Long.toString(field.equals("business")?other:business))
            .param("participantRevision","0").param("memberId",Long.toString(viewerId));
        mvc.perform(auth(command,actor,"PAGE_DIALOGS").with(csrf())).andExpect(status().isConflict());
        assertThat(memberEvents()).isZero();assertThat(outbox()).isZero();
    }
    @ParameterizedTest @ValueSource(longs={-1,0,9223372036854775807L})
    void invalidMemberIdDoesNotWrite(long id) {
        assertThat(participants.change(scope,actor,0,id,true)).isFalse();
        assertThat(memberEvents()).isZero();assertThat(outbox()).isZero();
    }
    @Test void participantRevisionIsIndependentOfAssignmentRevision() {
        assertThat(service.change(scope,actor,0,targetId,false)).isTrue();
        assertThat(participants.change(scope,actor,0,viewerId,true)).isTrue();
        assertThat(revision()).isEqualTo(1);assertThat(participants.revision(scope,actor)).contains(1L);
        assertThat(service.change(scope,actor,1,null,true)).isTrue();
        assertThat(participants.change(scope,actor,1,viewerId,false)).isTrue();
        assertThat(revision()).isEqualTo(2);assertThat(events()).isEqualTo(2);assertThat(memberEvents()).isEqualTo(2);
    }
    @Test void rosterBoundIsEnforcedBeforeInsertAndRemovalFreesCapacity() {
        jdbc.update("INSERT INTO users(username,password) SELECT ?||generate_series(1,200),'unused'",actor+"-bound-");
        jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) SELECT id,?,'VIEWER' FROM users WHERE username LIKE ?",business,actor+"-bound-%");
        jdbc.update("INSERT INTO business_ticket_participants(ticket_user_id,ticket_id,ticket_public_id,business_id,member_user_id,member,actor_user_id,actor,revision)"
            +" SELECT 901,?,?,?,id,username,?,?,row_number() OVER(ORDER BY id) FROM users WHERE username LIKE ?",
            raw,publicId,business,actorId,actor,actor+"-bound-%");
        jdbc.update("INSERT INTO business_ticket_participant_events(ticket_user_id,ticket_id,ticket_public_id,business_id,revision,member_user_id,member,actor_user_id,actor,action)"
            +" SELECT ticket_user_id,ticket_id,ticket_public_id,business_id,revision,member_user_id,member,actor_user_id,actor,'add'"
            +" FROM business_ticket_participants WHERE ticket_id=?",raw);
        assertThat(participants.members(scope,actor)).hasSize(200);
        assertThat(participants.change(scope,actor,200,viewerId,true)).isFalse();
        long first=participants.members(scope,actor).get(0).id();
        assertThat(participants.change(scope,actor,200,first,false)).isTrue();
        assertThat(participants.change(scope,actor,201,viewerId,true)).isTrue();
        assertThat(participants.members(scope,actor)).hasSize(200);assertThat(participants.revision(scope,actor)).contains(202L);
    }
    @Test void missingRosterSchemaDeniesWritesAndNeverAdoptsLegacyMembers() {
        jdbc.update("INSERT INTO ticket_participants(ticket_id,username,added_by) VALUES(?,?,'legacy')",raw,target);
        jdbc.execute("ALTER TABLE business_ticket_participants RENAME TO r164_hidden_participants");
        try {
            assertThat(participants.change(scope,actor,0,viewerId,true)).isFalse();
            assertThat(participants.members(scope,actor)).isEmpty();
            assertThat(notices.findScopedDialogRecipients(scope)).isEmpty();
            notices.notifyScopedDialogParticipants(scope,"missing schema","/business-dialogs",null);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE ticket_id=?",Long.class,raw)).isZero();
            assertThat(memberEvents()).isZero();assertThat(outbox()).isZero();
        }finally{jdbc.execute("ALTER TABLE r164_hidden_participants RENAME TO business_ticket_participants");}
    }
    @Test void memberCommandsRequirePageCsrfAndOperateEvenForSystemAdmin() throws Exception {
        mvc.perform(auth(memberCommand("add","0",viewerId),actor,"PAGE_DIALOGS")).andExpect(redirectedUrl("/error/403"));
        mvc.perform(auth(memberCommand("add","0",viewerId),actor,"ROLE_ADMIN").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(auth(memberCommand("add","0",viewerId),viewer,"PAGE_DIALOGS","ROLE_ADMIN").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(auth(memberCommand("add","0",viewerId),foreign,"PAGE_DIALOGS","ROLE_ADMIN").with(csrf())).andExpect(status().isNotFound());
        assertThat(memberEvents()).isZero();assertThat(outbox()).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"bad","9223372036854775808","-1","1"})
    void malformedOrStaleMemberRevisionCannotWrite(String revision) throws Exception {
        mvc.perform(auth(memberCommand("add",revision,viewerId),actor,"PAGE_DIALOGS").with(csrf()))
            .andExpect(revision.equals("bad")||revision.equals("9223372036854775808")?status().isBadRequest():status().isConflict());
        assertThat(memberEvents()).isZero();assertThat(outbox()).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"target-foreign","target-disabled","target-blocked","target-duplicate","target-revoked","actor-viewer",
        "actor-disabled","actor-blocked","actor-duplicate","actor-revoked","business-archived","owner","recreated","collision","general","closed","flag"})
    void memberWriteRechecksOwnerActorAndReadTarget(String mutation) {
        switch(mutation){
            case "target-foreign"->jdbc.update("UPDATE business_memberships SET business_id=? WHERE user_id=?",other,viewerId);
            case "target-disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=?",viewerId);
            case "target-blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=?",viewerId);
            case "target-duplicate"->account(viewer.toUpperCase(Locale.ROOT));
            case "target-revoked"->jdbc.update("DELETE FROM business_memberships WHERE user_id=?",viewerId);
            case "actor-viewer"->jdbc.update("UPDATE business_memberships SET role_code='VIEWER' WHERE user_id=?",actorId);
            case "actor-disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=?",actorId);
            case "actor-blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=?",actorId);
            case "actor-duplicate"->account(actor.toUpperCase(Locale.ROOT));
            case "actor-revoked"->jdbc.update("DELETE FROM business_memberships WHERE user_id=?",actorId);
            case "business-archived"->jdbc.update("UPDATE businesses SET status='archived' WHERE id=?",business);
            case "owner"->jdbc.update("UPDATE tickets SET business_id=? WHERE ticket_id=?",other,raw);
            case "recreated"->{jdbc.update("DELETE FROM tickets WHERE ticket_id=?",raw);jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id,status) VALUES(901,?,?,?,'open')",raw,UUID.randomUUID().toString().replace("-",""),business);}
            case "collision"->jdbc.update("INSERT INTO tickets(user_id,ticket_id,business_id) VALUES(902,?,?)",raw,other);
            case "general"->jdbc.update("UPDATE tickets SET business_id=NULL,is_general=TRUE,general_selection_source='MANUAL' WHERE ticket_id=?",raw);
            case "closed"->jdbc.update("UPDATE tickets SET status='closed' WHERE ticket_id=?",raw);
            case "flag"->flags.setUiSelector(false);
        }
        assertThat(participants.change(scope,actor,0,viewerId,true)).isFalse();
        assertThat(memberEvents()).isZero();assertThat(outbox()).isZero();assertThat(revision()).isZero();
    }
    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"add,actor","add,target","add,owner","add,collision","remove,actor","remove,owner","remove,collision"})
    void memberFinalSqlRepeatsOwnerAndAuthorityAfterEarlierProof(String action,String mutation) {
        boolean add=action.equals("add");
        if(!add)assertThat(participants.change(scope,actor,0,viewerId,true)).isTrue();
        var before=jdbc.queryForList("SELECT * FROM business_ticket_participants WHERE ticket_id=?",raw);
        var observed=spy(jdbc);
        var fired=new java.util.concurrent.atomic.AtomicBoolean();
        doAnswer(call->{
            var creator=call.getArgument(0,org.springframework.jdbc.core.PreparedStatementCreator.class);
            String sql=((org.springframework.jdbc.core.SqlProvider)creator).getSql();
            if((sql.startsWith("INSERT INTO business_ticket_participants")||sql.startsWith("DELETE FROM business_ticket_participants"))
                    &&fired.compareAndSet(false,true)) {
                // Deterministic JDBC-boundary injection in the test transaction, not an unlocked grant simulation.
                if(mutation.equals("actor"))jdbc.update("UPDATE business_memberships SET role_code='VIEWER' WHERE user_id=?",actorId);
                if(mutation.equals("target"))jdbc.update("DELETE FROM business_memberships WHERE user_id=?",viewerId);
                if(mutation.equals("owner"))jdbc.update("UPDATE tickets SET business_id=? WHERE ticket_id=?",other,raw);
                if(mutation.equals("collision"))jdbc.update("INSERT INTO tickets(user_id,ticket_id,business_id) VALUES(902,?,?)",raw,other);
            }
            return call.callRealMethod();
        }).when(observed).update(org.mockito.ArgumentMatchers.any(org.springframework.jdbc.core.PreparedStatementCreator.class));
        var writer=new BusinessTicketParticipantService(observed,flags,outboxIds);
        assertThat(writer.change(scope,actor,add?0:1,viewerId,add)).isFalse();
        assertThat(fired).isTrue();
        assertThat(jdbc.queryForList("SELECT * FROM business_ticket_participants WHERE ticket_id=?",raw)).isEqualTo(before);
        assertThat(memberEvents()).isEqualTo(add?0:1);assertThat(outbox()).isEqualTo(add?0:1);
    }
    @Test void memberGrantRevokedAfterControllerReadCannotProduceSuccess() throws Exception {
        doAnswer(call->{jdbc.update("DELETE FROM business_memberships WHERE user_id=?",viewerId);return call.callRealMethod();})
            .when(participants).change(eq(scope),eq(actor),eq(0L),eq(viewerId),eq(true));
        mvc.perform(auth(memberCommand("add","0",viewerId),actor,"PAGE_DIALOGS").with(csrf())).andExpect(status().isConflict());
        assertThat(memberEvents()).isZero();assertThat(outbox()).isZero();
    }
    @Test void memberNoOpAbaAndConcurrentFormsHaveOneWinner() throws Exception {
        var start=new CountDownLatch(1);
        java.util.function.Supplier<Boolean> attempt=()->{try{start.await();}catch(InterruptedException ex){throw new IllegalStateException(ex);}return participants.change(scope,actor,0,viewerId,true);};
        var a=CompletableFuture.supplyAsync(attempt);var b=CompletableFuture.supplyAsync(attempt);start.countDown();
        assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        assertThat(participants.change(scope,actor,1,viewerId,true)).isFalse();
        assertThat(participants.change(scope,actor,1,targetId,false)).isFalse();
        assertThat(participants.change(scope,actor,1,viewerId,false)).isTrue();
        assertThat(participants.change(scope,actor,0,viewerId,true)).isFalse();
        assertThat(participants.change(scope,actor,2,viewerId,true)).isTrue();
        assertThat(memberEvents()).isEqualTo(3);assertThat(outbox()).isEqualTo(3);assertThat(revision()).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"audit","outbox"})
    void memberInsertOrRemovalFailureRollsBackRosterRevisionAuditAndOutbox(String failure) {
        assertThat(participants.change(scope,actor,0,viewerId,true)).isTrue();
        String table=failure.equals("audit")?"business_ticket_participant_events":"ui_event_outbox";
        jdbc.execute("CREATE FUNCTION fail_r164_insert() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'synthetic R164 failure'; END; $$");
        jdbc.execute("CREATE TRIGGER fail_r164_insert BEFORE INSERT ON "+table+" FOR EACH ROW EXECUTE FUNCTION fail_r164_insert()");
        try {
            assertThat(participants.change(scope,actor,1,targetId,true)).isFalse();
            assertThat(participants.change(scope,actor,1,viewerId,false)).isFalse();
            assertThat(participants.members(scope,actor)).extracting(BusinessTicketParticipantService.Member::id).containsExactly(viewerId);
            assertThat(participants.revision(scope,actor)).contains(1L);assertThat(memberEvents()).isEqualTo(1);assertThat(outbox()).isEqualTo(1);
        }finally{jdbc.execute("DROP TRIGGER fail_r164_insert ON "+table);jdbc.execute("DROP FUNCTION fail_r164_insert()");}
        assertThat(participants.change(scope,actor,1,viewerId,false)).isTrue();
    }
    @ParameterizedTest @ValueSource(strings={"revoked","disabled","deleted"})
    void removedAccessDoesNotGrantNotificationsAndDoesNotPreventOperatorRemovingMember(String kind) {
        assertThat(participants.change(scope,actor,0,viewerId,true)).isTrue();
        assertThat(notices.findScopedDialogRecipients(scope)).contains(viewer);
        var captured=notices.findScopedDialogRecipients(scope);
        if(kind.equals("revoked"))jdbc.update("DELETE FROM business_memberships WHERE user_id=?",viewerId);
        if(kind.equals("disabled"))jdbc.update("UPDATE users SET enabled=FALSE WHERE id=?",viewerId);
        if(kind.equals("deleted"))jdbc.update("DELETE FROM users WHERE id=?",viewerId);
        assertThat(notices.findScopedDialogRecipients(scope)).doesNotContain(viewer);
        assertThat(notices.notifyScopedDialogUsers(scope,captured,"revoked","/business-dialogs")).isEmpty();
        assertThat(participants.members(scope,actor)).singleElement().satisfies(m->assertThat(m.eligible()).isFalse());
        assertThat(participants.change(scope,actor,1,viewerId,false)).isTrue();
        assertThat(participants.members(scope,actor)).isEmpty();
    }
    @Test void numericMemberIdentitySurvivesRenameAndCannotBeClaimedByReusedUsername() {
        assertThat(participants.change(scope,actor,0,viewerId,true)).isTrue();
        String renamed=viewer+" renamed";jdbc.update("UPDATE users SET username=? WHERE id=?",renamed,viewerId);
        long reuse=account(viewer);jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(?,?,'VIEWER')",reuse,business);
        assertThat(notices.findScopedDialogRecipients(scope)).containsExactly(renamed);
        assertThat(participants.members(scope,actor)).singleElement().satisfies(m->{assertThat(m.id()).isEqualTo(viewerId);assertThat(m.username()).isEqualTo(renamed);});
        assertThat(notices.notifyScopedDialogUsers(scope,notices.findScopedDialogRecipients(scope),"renamed","/business-dialogs")).containsExactly(renamed);
    }
    @Test void explicitBusinessRosterHasNoRawParticipantOrGlobalOperatorFallback() {
        jdbc.update("INSERT INTO ticket_participants(ticket_id,username,added_by) VALUES(?,?,'legacy')",raw,target);
        assertThat(notices.findScopedDialogRecipients(scope)).isEmpty();
        notices.notifyScopedDialogParticipants(scope,"no broadcast","/business-dialogs",null);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE ticket_id=?",Long.class,raw)).isZero();
        assertThat(participants.change(scope,actor,0,viewerId,true)).isTrue();
        notices.notifyScopedDialogParticipants(scope,"explicit roster","/business-dialogs",null);
        assertThat(jdbc.queryForList("SELECT user_identity FROM notifications WHERE ticket_id=?",String.class,raw)).containsExactly(viewer);
    }
    @Test void memberAuditAndIdentityAreImmutableAndDoNotFollowOwnerOrGeneration() {
        assertThat(participants.change(scope,actor,0,viewerId,true)).isTrue();
        assertThatThrownBy(()->jdbc.update("UPDATE business_ticket_participants SET member_user_id=? WHERE ticket_id=?",targetId,raw)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE business_ticket_participant_events SET actor='changed' WHERE ticket_id=?",raw)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->jdbc.update("DELETE FROM business_ticket_participant_events WHERE ticket_id=?",raw)).isInstanceOf(RuntimeException.class);
        jdbc.update("UPDATE tickets SET business_id=? WHERE ticket_id=?",other,raw);
        var moved=new UiEventTicketScope(901L,raw,publicId,"BUSINESS",other);
        assertThat(participants.members(moved,foreign)).isEmpty();assertThat(participants.recent(moved,foreign)).isEmpty();
        assertThat(participants.members(scope,actor)).isEmpty();assertThat(notices.findScopedDialogRecipients(scope)).isEmpty();
        jdbc.update("DELETE FROM tickets WHERE ticket_id=?",raw);
        String nextId="b".repeat(32);
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id,status) VALUES(901,?,?,?,'open')",raw,nextId,business);
        var next=new UiEventTicketScope(901L,raw,nextId,"BUSINESS",business);
        assertThat(participants.members(next,actor)).isEmpty();assertThat(participants.recent(next,actor)).isEmpty();
        assertThat(participants.revision(next,actor)).contains(0L);
    }
    @Test void memberGlobalReadGrantAllowsViewerButDoesNotAllowManagingRoster() {
        jdbc.update("DELETE FROM business_memberships WHERE user_id=?",viewerId);
        jdbc.update("INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES(?,'ALL_BUSINESSES','VIEWER')",viewerId);
        assertThat(participants.change(scope,actor,0,viewerId,true)).isTrue();
        assertThat(participants.members(scope,viewer)).hasSize(1);assertThat(participants.candidates(scope,viewer)).isEmpty();
        assertThat(participants.change(scope,viewer,1,targetId,true)).isFalse();
    }
    @Test void upgradeV78ToV79DoesNotInventMembersOrChangeOldRows() {
        var old=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("r164_upgrade");
        org.flywaydb.core.Flyway.configure().dataSource(old.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql").target("78").load().migrate();
        old.update("INSERT INTO tickets(user_id,ticket_id,status) VALUES(809,'old-member','open')");
        old.update("INSERT INTO ticket_participants(ticket_id,username,added_by) VALUES('old-member',' Old Member ','human')");
        var roots=old.queryForList("SELECT * FROM tickets");var members=old.queryForList("SELECT * FROM ticket_participants");
        org.flywaydb.core.Flyway.configure().dataSource(old.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql").load().migrate();
        assertThat(old.queryForList("SELECT * FROM tickets")).isEqualTo(roots);
        assertThat(old.queryForList("SELECT * FROM ticket_participants")).isEqualTo(members);
        assertThat(old.queryForObject("SELECT count(*) FROM business_ticket_participants",Long.class)).isZero();
        assertThat(old.queryForObject("SELECT count(*) FROM business_ticket_participant_events",Long.class)).isZero();
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
        assertThat(html).contains(actor,target,viewer).doesNotContain(foreign);
        var assignmentSelect=Pattern.compile("<select id=\"assignmentTarget\"[\\s\\S]*?</select>").matcher(html);
        assertThat(assignmentSelect.find()).isTrue();
        assertThat(assignmentSelect.group()).contains(actor,target).doesNotContain(viewer,foreign);
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
    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"business,add,path","business,add,query","business,remove,path","business,remove,query",
        "general,add,path","general,add,query","general,remove,path","general,remove,query",
        "strict,add,path","strict,add,query","strict,remove,path","strict,remove,query"})
    void rawParticipantRoutesCannotMutateScopedOrStrictRoot(String kind,String action,String route) throws Exception {
        flags.setStrictReadScope(false);
        if(route.equals("path")){String safe="r163-"+publicId;jdbc.update("UPDATE tickets SET ticket_id=? WHERE ticket_id=?",safe,raw);raw=safe;}
        if(kind.equals("business"))flags.getFoundation().setEnabled(false);
        if(kind.equals("general"))jdbc.update("UPDATE tickets SET business_id=NULL,is_general=TRUE,general_selection_source='MANUAL' WHERE ticket_id=?",raw);
        if(kind.equals("strict")){jdbc.update("UPDATE tickets SET business_id=NULL WHERE ticket_id=?",raw);flags.setStrictReadScope(true);}
        jdbc.update("INSERT INTO ticket_participants(ticket_id,username,added_by) VALUES(?,?,'original')",raw,target);
        var before=jdbc.queryForList("SELECT * FROM ticket_participants WHERE ticket_id=?",raw);
        MockHttpServletRequestBuilder request;
        if(action.equals("add")){
            request=route.equals("path")?post("/api/dialogs/{id}/participants",raw):post("/api/dialog-ticket/participants").param("ticketId",raw);
            request.contentType("application/json").content("{\"username\":\"fresh-member\"}");
        }else request=route.equals("path")?delete("/api/dialogs/{id}/participants/{username}",raw,target)
            :delete("/api/dialog-ticket/participants").param("ticketId",raw).param("username",target);
        mvc.perform(auth(request,actor,"PAGE_DIALOGS","ROLE_ADMIN").with(csrf())).andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false));
        assertThat(jdbc.queryForList("SELECT * FROM ticket_participants WHERE ticket_id=?",raw)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dialog_action_audit WHERE ticket_id=?",Long.class,raw)).isZero();
        assertThat(events()).isZero();assertThat(outbox()).isZero();assertThat(revision()).isZero();
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> legacyLifecycleRoutes() {
        return java.util.stream.Stream.of("business","general","strict").flatMap(kind->
            java.util.stream.Stream.of("resolve","reopen","categories","spam").flatMap(action->
                java.util.stream.Stream.of("path","query").map(route->org.junit.jupiter.params.provider.Arguments.of(kind,action,route))));
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.MethodSource("legacyLifecycleRoutes")
    void rawLifecycleRoutesCannotMutateScopedOrStrictRootEvenForSystemAdmin(String kind,String action,String route) throws Exception {
        flags.setStrictReadScope(false);
        if(route.equals("path")){String safe="r166-"+publicId;jdbc.update("UPDATE tickets SET ticket_id=? WHERE ticket_id=?",safe,raw);raw=safe;}
        if(kind.equals("business"))flags.getFoundation().setEnabled(false);
        if(kind.equals("general"))jdbc.update("UPDATE tickets SET business_id=NULL,is_general=TRUE,general_selection_source='MANUAL' WHERE ticket_id=?",raw);
        if(kind.equals("strict")){jdbc.update("UPDATE tickets SET business_id=NULL WHERE ticket_id=?",raw);flags.setStrictReadScope(true);}
        jdbc.update("UPDATE tickets SET status=? WHERE ticket_id=?",action.equals("reopen")?"closed":"pending",raw);
        jdbc.update("INSERT INTO ticket_categories(ticket_id,category) VALUES(?,'Original')",raw);
        var tables=List.of("tickets","ticket_categories","ticket_active","pending_feedback_requests","ticket_responsibles","dialog_action_audit","notifications","ui_event_outbox");
        var before=new LinkedHashMap<String,List<Map<String,Object>>>();
        for(String table:tables)before.put(table,jdbc.queryForList("SELECT * FROM "+table+" WHERE ticket_id=?",raw));
        var request=route.equals("path")?post("/api/dialogs/{id}/"+action,raw):post("/api/dialog-ticket/"+action).param("ticketId",raw);
        mvc.perform(auth(request,actor,"PAGE_DIALOGS","ROLE_ADMIN").with(csrf()).contentType("application/json").content("{\"categories\":[\"New\"],\"reason\":\"Spam\"}"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false));
        for(String table:tables)assertThat(jdbc.queryForList("SELECT * FROM "+table+" WHERE ticket_id=?",raw)).as(table).isEqualTo(before.get(table));
        assertThat(memberEvents()).isZero();assertThat(events()).isZero();assertThat(outbox()).isZero();assertThat(revision()).isZero();
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
