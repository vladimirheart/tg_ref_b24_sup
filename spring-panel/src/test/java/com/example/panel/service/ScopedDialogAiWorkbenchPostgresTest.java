package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

class ScopedDialogAiWorkbenchPostgresTest {
    private JdbcTemplate jdbc;
    private ScopedDialogAiWorkbenchService work;
    private ScopedAiSourceService sources;
    private ScopedDialogAiPersistenceService persistence;
    private ScopedAiHandoffKnowledgeService knowledge;
    private UiEventTicketScope scope;
    private String qHash,aHash;
    private MultibusinessFeatureFlags flags;
    private static final String RAW=" curated/Ю?% ";
    @BeforeEach void setup() throws Exception {
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("ai_workbench");
        jdbc.execute("CREATE TABLE businesses(id BIGINT PRIMARY KEY,status TEXT,display_name TEXT)");
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,username TEXT,enabled BOOLEAN,is_blocked BOOLEAN)");
        jdbc.execute("CREATE TABLE business_memberships(user_id BIGINT,business_id BIGINT,role_code TEXT,is_active BOOLEAN)");
        jdbc.execute("CREATE TABLE business_access_grants(user_id BIGINT,grant_scope TEXT,role_code TEXT,is_active BOOLEAN)");
        jdbc.execute("CREATE TABLE tickets(user_id BIGINT,ticket_id TEXT,ticket_public_id TEXT,business_id BIGINT,is_general BOOLEAN,PRIMARY KEY(user_id,ticket_id))");
        jdbc.execute("CREATE TABLE chat_history(id BIGINT PRIMARY KEY,ticket_owner_user_id BIGINT,ticket_id TEXT,sender TEXT,message TEXT,message_type TEXT,attachment TEXT,deleted_at TIMESTAMPTZ)");
        for (int v=62;v<=64;v++) {
            String name=switch(v) {case 62->"scoped_ai_records";case 63->"business_owned_ai_sources";default->"scoped_ai_handoff_evidence";};
            jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/postgresql/V"+v+"__"+name+".sql")));
        }
        jdbc.update("INSERT INTO businesses VALUES(1,'active','A'),(2,'active','B')");
        jdbc.update("INSERT INTO users VALUES(1,'admin',TRUE,FALSE),(2,'foreign',TRUE,FALSE),(3,'all',TRUE,FALSE)");
        jdbc.update("INSERT INTO business_memberships VALUES(1,1,'ADMIN',TRUE),(2,2,'ADMIN',TRUE)");
        jdbc.update("INSERT INTO business_access_grants VALUES(3,'ALL_BUSINESSES','ADMIN',TRUE)");
        jdbc.update("INSERT INTO tickets VALUES(10,?,?,1,FALSE),(20,?,?,2,FALSE)",RAW,"a".repeat(32),RAW.trim(),"b".repeat(32));
        jdbc.update("INSERT INTO chat_history VALUES(40,10,?,'client','refund question','text',NULL,NULL),"
            +"(41,10,?,'operator','refund answer','text',NULL,NULL),(42,10,?,'operator','AI secret','ai_agent',NULL,NULL),"
            +"(43,NULL,?,'client','unlinked secret','text',NULL,NULL)",RAW,RAW,RAW,RAW);
        flags=new MultibusinessFeatureFlags(); work=new ScopedDialogAiWorkbenchService(jdbc);
        sources=new ScopedAiSourceService(jdbc,flags); persistence=new ScopedDialogAiPersistenceService(jdbc,flags);
        knowledge=new ScopedAiHandoffKnowledgeService(jdbc,flags,persistence);
        scope=new UiEventTicketScope(10L,RAW,"a".repeat(32),"BUSINESS",1L);
        qHash=jdbc.queryForObject("SELECT encode(sha256(convert_to(message,'UTF8')),'hex') FROM chat_history WHERE id=40",String.class);
        aHash=jdbc.queryForObject("SELECT encode(sha256(convert_to(message,'UTF8')),'hex') FROM chat_history WHERE id=41",String.class);
    }
    private org.springframework.security.core.Authentication boundControlActor(){
        return org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
            new com.example.panel.security.PanelUserPrincipal(1,"admin","unused",true,java.util.List.of()),null,java.util.List.of());
    }
    private String key() { return UUID.randomUUID().toString(); }
    private boolean memory(String actor,String key) { return work.createMemory(scope,com.example.panel.support.AiControlIdentityFixture.auth(1,actor),key,40,41,qHash,aHash,"Resolved refund","curated without PII",true); }
    private void approved(long business,String actor,String key) {
        assertThat(sources.createKnowledge(business,com.example.panel.support.AiControlIdentityFixture.login(jdbc,actor),key,"refund","refund approved")).isTrue();
        assertThat(sources.revise(business,actor,ScopedAiSourceService.Kind.KNOWLEDGE,key,1,"refund","refund approved",ScopedAiSourceService.Status.APPROVED)).isTrue();
    }
    @Test void fullCurationApprovalHandoffAndCurrentReferenceRead() {
        assertThat(work.resolve(scope.ticketPublicId(),boundControlActor())).contains(scope);
        assertThat(work.resolve(scope.ticketPublicId(),com.example.panel.support.AiControlIdentityFixture.auth(2,"foreign"))).isEmpty();
        assertThat(work.messages(scope,boundControlActor())).extracting(ScopedDialogAiWorkbenchService.Message::id).containsExactly(41L,40L);
        String k=key();
        assertThat(memory("admin",k)).isTrue();
        var draft=sources.find(1L,"admin",ScopedAiSourceService.Kind.MEMORY,k).orElseThrow();
        assertThat(draft.status()).isEqualTo(ScopedAiSourceService.Status.DRAFT);
        assertThat(draft.origin()).isEqualTo(scope);
        assertThat(jdbc.queryForMap("SELECT question_history_id,answer_history_id FROM ai_scoped_memory_evidence"))
            .containsEntry("question_history_id",40L).containsEntry("answer_history_id",41L);
        assertThat(knowledge.record(scope,"curated")).isTrue();
        assertThat(knowledge.candidates(scope,"admin")).isEmpty();
        assertThat(sources.reviseExpected(1L,"admin",draft.kind(),k,draft.id(),1,draft.title(),draft.body(),ScopedAiSourceService.Status.APPROVED)).isTrue();
        assertThat(knowledge.record(scope,"curated")).isTrue();
        assertThat(knowledge.candidates(scope,"admin")).extracting(ScopedAiSourceService.Summary::id).containsExactly(draft.id());
        assertThat(knowledge.candidates(scope,"foreign")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT is_processing FROM ticket_ai_scoped_state",Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("SELECT mode FROM ticket_ai_scoped_state",String.class)).isEqualTo("escalate_only");
    }
    static Stream<Arguments> roles() {
        return Stream.of("VIEWER","OPERATOR","MANAGER","ADMIN","UNKNOWN").flatMap(r->
            Stream.of("membership","all").map(g->Arguments.of(r,g)));
    }
    @ParameterizedTest @MethodSource("roles")
    void currentGrantMatrixDoesNotConflateReadOperateAndConfigure(String role,String grant) {
        approved(1,"admin","proof");
        jdbc.update("DELETE FROM business_memberships WHERE user_id=1");
        if (grant.equals("membership")) jdbc.update("INSERT INTO business_memberships VALUES(1,1,?,TRUE)",role);
        else jdbc.update("INSERT INTO business_access_grants VALUES(1,'ALL_BUSINESSES',?,TRUE)",role);
        boolean read=!role.equals("UNKNOWN"),operate=read&&!role.equals("VIEWER"),configure=role.equals("ADMIN");
        assertThat(work.resolve(scope.ticketPublicId(),boundControlActor()).isPresent()).isEqualTo(read);
        assertThat(persistence.canOperate(scope,boundControlActor())).isEqualTo(operate);
        assertThat(memory("admin",key())).isEqualTo(configure);
        assertThat(persistence.setControlExpected(scope,boundControlActor(),"",false,true,"reason")).isEqualTo(operate);
        assertThat(knowledge.record(scope,"refund")).isTrue();
        assertThat(knowledge.candidates(scope,"admin")).hasSize(read?1:0);
    }
    @ParameterizedTest @ValueSource(strings={"edit-question","edit-answer","delete","attachment","unlinked","foreign-owner","ai","reverse","fingerprint","no-review"})
    void evidenceMustStillBeTextualCurrentExactAndExplicitlyReviewed(String mutation) {
        long question=40,answer=41; boolean reviewed=true; String questionHash=qHash;
        switch(mutation) {
            case "edit-question"->jdbc.update("UPDATE chat_history SET message='edited' WHERE id=40");
            case "edit-answer"->jdbc.update("UPDATE chat_history SET message='edited' WHERE id=41");
            case "delete"->jdbc.update("UPDATE chat_history SET deleted_at=CURRENT_TIMESTAMP WHERE id=41");
            case "attachment"->jdbc.update("UPDATE chat_history SET attachment='private/media' WHERE id=41");
            case "unlinked"->jdbc.update("UPDATE chat_history SET ticket_owner_user_id=NULL WHERE id=41");
            case "foreign-owner"->jdbc.update("UPDATE chat_history SET ticket_owner_user_id=20 WHERE id=41");
            case "ai"->jdbc.update("UPDATE chat_history SET message_type='ai_agent' WHERE id=41");
            case "reverse"->{ question=41;answer=40; }
            case "fingerprint"->questionHash="0".repeat(64);
            case "no-review"->reviewed=false;
        }
        assertThat(work.createMemory(scope,boundControlActor(),key(),question,answer,questionHash,aHash,"title","reviewed body",reviewed)).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_scoped_sources",Integer.class)).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"business","generation","tuple","general","collision","missing","grant","blocked","duplicate-account","archive"})
    void staleOwnerOrRevokedActorCannotCurateOrChangeControls(String mutation) {
        switch(mutation) {
            case "business"->jdbc.update("UPDATE tickets SET business_id=2 WHERE ticket_id=?",RAW);
            case "generation"->jdbc.update("UPDATE tickets SET ticket_public_id=? WHERE ticket_id=?","c".repeat(32),RAW);
            case "tuple"->jdbc.update("UPDATE tickets SET user_id=99 WHERE ticket_id=?",RAW);
            case "general"->jdbc.update("UPDATE tickets SET business_id=NULL,is_general=TRUE WHERE ticket_id=?",RAW);
            case "collision"->jdbc.update("INSERT INTO tickets VALUES(99,?,?,2,FALSE)",RAW,"c".repeat(32));
            case "missing"->jdbc.update("DELETE FROM tickets WHERE ticket_id=?",RAW);
            case "grant"->jdbc.update("DELETE FROM business_memberships WHERE user_id=1");
            case "blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=1");
            case "duplicate-account"->jdbc.update("INSERT INTO users VALUES(9,'ADMIN',TRUE,FALSE)");
            case "archive"->jdbc.update("UPDATE businesses SET status='archived' WHERE id=1");
        }
        assertThat(memory("admin",key())).isFalse();
        assertThat(persistence.setControlExpected(scope,boundControlActor(),"",true,true,"reason")).isFalse();
        assertThat(work.messages(scope,boundControlActor())).isEmpty();
    }
    @Test void controlCasAndAuditAreAtomicAndNeverEnableAutoReply() {
        assertThat(persistence.setControlExpected(scope,boundControlActor(),"",true,true,"pause")).isTrue();
        var stamp=persistence.findControl(scope,boundControlActor()).orElseThrow().updatedAt();
        assertThat(persistence.setControlExpected(scope,boundControlActor(),"",false,false,"stale")).isFalse();
        assertThat(knowledge.record(scope,"refund")).isFalse();
        assertThat(persistence.setControlExpected(scope,boundControlActor(),stamp,false,true,"resume")).isTrue();
        assertThat(persistence.setControlExpected(scope,boundControlActor(),stamp,true,false,"old tab")).isFalse();
        var current=persistence.findControl(scope,boundControlActor()).orElseThrow();
        assertThat(current.aiDisabled()).isFalse(); assertThat(current.autoReplyBlocked()).isTrue();
        jdbc.execute("ALTER TABLE ticket_ai_scoped_events ADD CONSTRAINT no_control CHECK(event_type<>'control_updated') NOT VALID");
        assertThat(persistence.setControlExpected(scope,boundControlActor(),current.updatedAt(),true,false,"audit failure")).isFalse();
        assertThat(persistence.findControl(scope,boundControlActor())).contains(current);
    }
    @Test void curationAuditEvidenceFailureRollsBackSourceAndAudit() {
        jdbc.execute("ALTER TABLE ai_scoped_memory_evidence ADD CONSTRAINT reject_evidence CHECK(source_id<0)");
        assertThat(memory("admin",key())).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_scoped_sources",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_scoped_source_events",Integer.class)).isZero();
    }
    @Test void workerEvidenceFailureRollsBackStateAndEvent() {
        approved(1,"admin","approved");
        jdbc.execute("ALTER TABLE ai_scoped_handoff_evidence ADD CONSTRAINT reject_refs CHECK(source_id<0)");
        assertThat(knowledge.record(scope,"refund")).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ticket_ai_scoped_events",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ticket_ai_scoped_state",Integer.class)).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"revision","archive","delete","origin","disabled","revoke","owner"})
    void evidenceReadCannotResurrectChangedSourcesOrBroadenToOtherBusiness(String mutation) {
        approved(1,"admin","A"); approved(2,"foreign","B");
        assertThat(knowledge.record(scope,"refund")).isTrue();
        assertThat(knowledge.candidates(scope,"all")).hasSize(1);
        switch(mutation) {
            case "revision"->sources.revise(1L,"admin",ScopedAiSourceService.Kind.KNOWLEDGE,"A",2,"refund","new",ScopedAiSourceService.Status.APPROVED);
            case "archive"->sources.revise(1L,"admin",ScopedAiSourceService.Kind.KNOWLEDGE,"A",2,"refund","refund",ScopedAiSourceService.Status.ARCHIVED);
            case "delete"->jdbc.update("DELETE FROM ai_scoped_sources WHERE business_id=1");
            case "origin","owner"->jdbc.update("UPDATE tickets SET business_id=2 WHERE ticket_id=?",RAW);
            case "disabled"->persistence.setControl(scope,com.example.panel.support.AiControlIdentityFixture.auth(3,"all"),true,true,"pause");
            case "revoke"->jdbc.update("DELETE FROM business_access_grants WHERE user_id=3");
        }
        assertThat(knowledge.candidates(scope,"all")).isEmpty();
    }
    @Test void boundedLiteralQueryAndImmutableReferences() {
        approved(1,"admin","A");
        assertThat(knowledge.record(scope,"%")).isTrue(); assertThat(knowledge.candidates(scope,"admin")).isEmpty();
        assertThat(knowledge.record(scope,"refund")).isTrue(); assertThat(knowledge.candidates(scope,"admin")).hasSize(1);
        assertThatThrownBy(()->jdbc.update("UPDATE ai_scoped_handoff_evidence SET source_revision=999")).isInstanceOf(DataAccessException.class);
        assertThat(memory("admin",key())).isTrue();
        assertThatThrownBy(()->jdbc.update("UPDATE ai_scoped_memory_evidence SET question_history_id=1")).isInstanceOf(DataAccessException.class);
        assertThat(knowledge.record(scope,"x".repeat(501))).isTrue(); assertThat(knowledge.candidates(scope,"admin")).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings={"enabled","global-off","disabled","owner-changed"})
    void actualIncomingFlowUsesScopedKnowledgeWithoutMediaLlmOrRawRetrieval(String mode) {
        approved(1,"admin","pipeline");
        var config=mock(DialogAiAssistantConfigService.class);
        when(config.isAgentEnabled()).thenAnswer(call->{
            if (mode.equals("owner-changed")) jdbc.update("UPDATE tickets SET business_id=2 WHERE ticket_id=?",RAW);
            return !mode.equals("global-off");
        });
        if (mode.equals("disabled")) assertThat(persistence.setControl(scope,boundControlActor(),true,true,"pause")).isTrue();
        var escalation=mock(DialogAiAssistantEscalationService.class);
        var retrieval=mock(AiRetrievalService.class);
        var normalizer=mock(AiInputNormalizerService.class);
        var llm=mock(AiControlledLlmService.class);
        var rawState=mock(DialogAiAssistantStateService.class);
        var outcomes=mock(DialogAiAssistantMessageOutcomeService.class);
        var flow=new DialogAiAssistantMessageFlowService(mock(AiPolicyService.class),retrieval,mock(AiDecisionService.class),
            normalizer,llm,rawState,config,mock(DialogAiAssistantPolicyService.class),mock(DialogAiAssistantSuggestionService.class),
            mock(DialogAiAssistantEventService.class),escalation,outcomes,persistence,knowledge);
        flow.processScopedIncomingClientMessage(scope,"refund","photo","private/object-key");
        if (mode.equals("enabled")) {
            verify(escalation).notifyScopedOperatorsEscalation(eq(scope),eq("refund"),any());
            assertThat(knowledge.candidates(scope,"admin")).hasSize(1);
        } else verifyNoInteractions(escalation);
        verifyNoInteractions(retrieval,normalizer,llm,rawState,outcomes);
    }
    @Test void additiveUpgradePreservesV63DataAndDoesNotClassifyHistory() {
        var upgrade=PostgresqlJdbcTestSupport.freshJdbcTemplate("workbench_upgrade");
        org.flywaydb.core.Flyway.configure().dataSource(upgrade.getDataSource())
            .locations("filesystem:src/main/resources/db/migration/postgresql").target("63").load().migrate();
        long business=upgrade.queryForObject("SELECT id FROM businesses WHERE code='sushi'",Long.class);
        upgrade.update("INSERT INTO ai_scoped_sources(business_id,source_kind,source_key,title,body,status,revision,updated_by_user_id,updated_by)"
            +" VALUES(?,'KNOWLEDGE','existing','Title','Body','APPROVED',1,1,'admin')",business);
        var before=upgrade.queryForList("SELECT * FROM ai_scoped_sources");
        org.flywaydb.core.Flyway.configure().dataSource(upgrade.getDataSource())
            .locations("filesystem:src/main/resources/db/migration/postgresql").load().migrate();
        assertThat(upgrade.queryForList("SELECT * FROM ai_scoped_sources")).isEqualTo(before);
        assertThat(upgrade.queryForList("SELECT * FROM ai_scoped_memory_evidence")).isEmpty();
        assertThat(upgrade.queryForList("SELECT * FROM ai_scoped_handoff_evidence")).isEmpty();
    }
}
