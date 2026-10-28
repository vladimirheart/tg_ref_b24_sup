package com.example.panel.service;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.example.panel.service.ScopedAiSourceService.Kind.KNOWLEDGE;
import static com.example.panel.service.ScopedAiSourceService.Status.APPROVED;
import static com.example.panel.service.ScopedAiSourceService.Status.ARCHIVED;
import static org.assertj.core.api.Assertions.*;

/** A principal is an account key, never a free-form recipient list or a display name. */
class ScopedPrincipalIdentityPostgresTest {
    private static final String RAW=" R172/Ю?%#+ ",PUBLIC="a".repeat(32),OTHER="b".repeat(32);
    private static final List<String> TABLES=List.of("users","businesses","business_memberships","business_access_grants","tickets",
        "ticket_responsibles","business_ticket_assignment_events","business_ticket_participants","business_ticket_participant_events",
        "ui_event_outbox","ticket_ai_scoped_control","ticket_ai_scoped_state","ticket_ai_scoped_events","ai_scoped_sources",
        "ai_scoped_source_events","ai_scoped_memory_evidence","ai_scoped_handoff_evidence");
    private JdbcTemplate jdbc;
    private BusinessTicketAssignmentService assignments;
    private BusinessTicketParticipantService participants;
    private ScopedDialogAiPersistenceService persistence;
    private ScopedDialogAiWorkbenchService workbench;
    private ScopedAiSourceService sources;
    private ScopedAiHandoffKnowledgeService handoff;
    private final UiEventTicketScope scope=new UiEventTicketScope(42L,RAW,PUBLIC,"BUSINESS",11L);
    private final UiEventTicketScope other=new UiEventTicketScope(88L,RAW.trim(),OTHER,"BUSINESS",22L);
    private ScopedAiSourceService.Source approved;
    private String questionHash,answerHash;

    @BeforeEach void fixture() throws Exception {
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("scoped_principal_identity");
        jdbc.execute("""
            CREATE TABLE users(id BIGINT PRIMARY KEY,username TEXT,enabled BOOLEAN DEFAULT TRUE,is_blocked BOOLEAN DEFAULT FALSE);
            CREATE TABLE businesses(id BIGINT PRIMARY KEY,status TEXT DEFAULT 'active',display_name TEXT DEFAULT 'Business');
            CREATE TABLE business_memberships(user_id BIGINT,business_id BIGINT,role_code TEXT,is_active BOOLEAN DEFAULT TRUE);
            CREATE TABLE business_access_grants(user_id BIGINT,grant_scope TEXT,role_code TEXT,is_active BOOLEAN DEFAULT TRUE);
            CREATE TABLE tickets(user_id BIGINT,ticket_id TEXT,ticket_public_id TEXT,business_id BIGINT,is_general BOOLEAN DEFAULT FALSE,
                channel_id BIGINT,status TEXT DEFAULT 'open',PRIMARY KEY(user_id,ticket_id));
            CREATE TABLE ticket_responsibles(ticket_id TEXT PRIMARY KEY,responsible TEXT,assigned_by TEXT,assigned_at TIMESTAMPTZ);
            CREATE TABLE ui_event_outbox(id BIGINT PRIMARY KEY,event_type TEXT,ticket_id TEXT,channel_id BIGINT,created_at TIMESTAMPTZ,
                ticket_user_id BIGINT,ticket_public_id TEXT,ticket_scope_kind TEXT,ticket_business_id BIGINT);
            CREATE TABLE chat_history(id BIGINT PRIMARY KEY,ticket_owner_user_id BIGINT,ticket_id TEXT,sender TEXT,message TEXT,
                message_type TEXT,attachment TEXT,deleted_at TIMESTAMPTZ);
            INSERT INTO businesses(id) VALUES(11),(22);
            INSERT INTO users(id,username) VALUES(1,'admin'),(2,'viewer'),(3,'foreign'),(4,'all-reader'),(5,'target'),(6,' admin ');
            INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(1,11,'ADMIN'),(2,11,'VIEWER'),(3,22,'ADMIN'),(5,11,'OPERATOR'),(6,22,'ADMIN');
            INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES(4,'ALL_BUSINESSES','VIEWER');
            """);
        for(String file:List.of("V62__scoped_ai_records.sql","V63__business_owned_ai_sources.sql","V64__scoped_ai_handoff_evidence.sql",
                "V77__scoped_sla_assignment_revision.sql","V78__business_manual_assignment_events.sql","V79__business_ticket_participants.sql"))
            jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/postgresql/"+file)));
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id) VALUES(42,?,?,11),(88,?,?,22)",RAW,PUBLIC,RAW.trim(),OTHER);
        jdbc.update("INSERT INTO chat_history(id,ticket_owner_user_id,ticket_id,sender,message,message_type) VALUES(40,42,?,'client','refund question','text'),(41,42,?,'operator','refund answer','text'),(50,88,?,'client','own refund question','text'),(51,88,?,'operator','own refund answer','text')",RAW,RAW,RAW.trim(),RAW.trim());
        var flags=new MultibusinessFeatureFlags();flags.getFoundation().setEnabled(true);flags.setUiSelector(true);flags.setStrictReadScope(true);
        var ids=new UiEventOutboxAppendService(jdbc,new LegacyTicketIdJdbcGuard(jdbc));
        assignments=new BusinessTicketAssignmentService(jdbc,flags,ids);
        participants=new BusinessTicketParticipantService(jdbc,flags,ids);
        persistence=new ScopedDialogAiPersistenceService(jdbc,flags);
        workbench=new ScopedDialogAiWorkbenchService(jdbc);
        sources=new ScopedAiSourceService(jdbc,flags);
        handoff=new ScopedAiHandoffKnowledgeService(jdbc,flags,persistence);
        assertThat(assignments.change(scope,assignmentAuthentication("admin"),0,5L,false)).isTrue();
        assertThat(participants.change(scope,assignmentAuthentication("admin"),0,2L,true)).isTrue();
        assertThat(sources.createKnowledge(11L,"admin","proof","refund","PRIVATE Business A refund knowledge")).isTrue();
        assertThat(sources.revise(11L,"admin",KNOWLEDGE,"proof",1,"refund","PRIVATE Business A refund knowledge",APPROVED)).isTrue();
        approved=sources.find(11L,"admin",KNOWLEDGE,"proof").orElseThrow();
        questionHash=fingerprint(40);answerHash=fingerprint(41);
        assertThat(workbench.createMemory(scope,"admin",key(),40,41,questionHash,answerHash,"reviewed","safe memory",true)).isTrue();
        assertThat(handoff.record(scope,"refund")).isTrue();
        assertThat(persistence.setControlExpected(scope,"admin","",false,true,"original")).isTrue();
    }
    /** Models initial case-insensitive login; the resulting principal always has the stored raw name. */
    private org.springframework.security.core.Authentication assignmentAuthentication(String supplied){
        var rows=jdbc.queryForList("SELECT id,username FROM users WHERE lower(username)=lower(?) LIMIT 2",supplied);
        Object principal=rows.size()==1?new com.example.panel.security.PanelUserPrincipal(((Number)rows.get(0).get("id")).longValue(),
            (String)rows.get(0).get("username"),"unused",true,List.of()):supplied;
        return org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(principal,null,List.of());
    }
    private String key() { return UUID.randomUUID().toString(); }
    private String fingerprint(long id) { return jdbc.queryForObject("SELECT encode(sha256(convert_to(message,'UTF8')),'hex') FROM chat_history WHERE id=?",String.class,id); }
    private Map<String,List<String>> snapshot() {
        var result=new LinkedHashMap<String,List<String>>();
        for(String table:TABLES)result.put(table,jdbc.queryForList("SELECT to_jsonb(entry)::TEXT FROM "+table+" entry ORDER BY to_jsonb(entry)::TEXT",String.class));
        return result;
    }
    private void deniedReads(String actor) {
        org.assertj.core.api.SoftAssertions.assertSoftly(check->{
            check.assertThat(assignments.find(PUBLIC,assignmentAuthentication(actor))).as("assignment owner").isEmpty();
            check.assertThat(assignments.candidates(scope,assignmentAuthentication(actor))).as("assignment candidates").isEmpty();
            check.assertThat(assignments.recent(scope,assignmentAuthentication(actor))).as("assignment history").isEmpty();
            check.assertThat(participants.revision(scope,assignmentAuthentication(actor))).as("participant revision").isEmpty();
            check.assertThat(participants.members(scope,assignmentAuthentication(actor))).as("participants").isEmpty();
            check.assertThat(participants.candidates(scope,assignmentAuthentication(actor))).as("participant candidates").isEmpty();
            check.assertThat(participants.recent(scope,assignmentAuthentication(actor))).as("participant history").isEmpty();
            check.assertThat(workbench.resolve(PUBLIC,actor)).as("AI owner").isEmpty();
            check.assertThat(workbench.messages(scope,actor)).as("curation messages").isEmpty();
            check.assertThat(persistence.canOperate(scope,actor)).as("AI OPERATE").isFalse();
            check.assertThat(persistence.findControl(scope,actor)).as("AI controls").isEmpty();
            check.assertThat(persistence.findState(scope,actor)).as("AI state").isEmpty();
            check.assertThat(persistence.findEvents(scope,actor,50)).as("AI events").isEmpty();
            check.assertThat(sources.availableBusinesses(actor)).as("business choices").noneMatch(b->b.id()==11);
            check.assertThat(sources.canConfigure(11L,actor)).as("CONFIGURE").isFalse();
            check.assertThat(sources.catalog(11L,actor,null,null,"",null,50)).as("source catalog").isEmpty();
            check.assertThat(sources.find(11L,actor,KNOWLEDGE,"proof")).as("source body").isEmpty();
            check.assertThat(sources.history(11L,actor,KNOWLEDGE,"proof",50)).as("source history").isEmpty();
            check.assertThat(sources.retrieve(scope,actor,"refund",20)).as("retrieval").isEmpty();
            check.assertThat(sources.isCurrentCandidate(scope,actor,approved)).as("retrieved payload proof").isFalse();
            check.assertThat(handoff.candidates(scope,actor)).as("handoff evidence").isEmpty();
        });
    }
    private void deniedWrites(String actor) {
        String stamp=persistence.findControl(scope,"admin").map(ScopedDialogAiPersistenceService.Control::updatedAt).orElse("");
        assertThat(assignments.change(scope,assignmentAuthentication(actor),1,null,true)).isFalse();
        assertThat(assignments.change(scope,assignmentAuthentication(actor),1,1L,false)).isFalse();
        assertThat(participants.change(scope,assignmentAuthentication(actor),1,2L,false)).isFalse();
        assertThat(participants.change(scope,assignmentAuthentication(actor),1,5L,true)).isFalse();
        assertThat(persistence.setControl(scope,actor,true,true,"forged")).isFalse();
        assertThat(persistence.setControlExpected(scope,actor,stamp,true,true,"forged")).isFalse();
        assertThat(workbench.createMemory(scope,actor,key(),40,41,questionHash,answerHash,"forged","body",true)).isFalse();
        assertThat(sources.createKnowledge(11L,actor,key(),"forged","body")).isFalse();
        assertThat(sources.createMemory(scope,actor,key(),"forged","body")).isFalse();
        assertThat(sources.revise(11L,actor,KNOWLEDGE,"proof",2,"forged","body",ARCHIVED)).isFalse();
        assertThat(sources.reviseExpected(11L,actor,KNOWLEDGE,"proof",approved.id(),2,"forged","body",ARCHIVED)).isFalse();
    }
    @ParameterizedTest @ValueSource(strings={" admin","admin "," admin ","\tadmin\t","\nadmin\r"})
    void whitespacePrincipalCannotReadAnotherCanonicalAccount(String actor) {
        var before=snapshot();deniedReads(actor);assertThat(snapshot()).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={" admin","admin "," admin ","\tadmin\t","\nadmin\r"})
    void whitespacePrincipalCannotMutateAnotherCanonicalAccount(String actor) {
        var before=snapshot();deniedWrites(actor);assertThat(snapshot()).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"ADMIN","AdMiN"})
    void caseInsensitiveCanonicalAccountKeepsItsOwnReadAndWriteRights(String actor) {
        assertThat(assignments.find(PUBLIC,assignmentAuthentication(actor))).isPresent();assertThat(participants.members(scope,assignmentAuthentication(actor))).hasSize(1);
        assertThat(workbench.resolve(PUBLIC,actor)).contains(scope);assertThat(workbench.messages(scope,actor)).hasSize(2);
        assertThat(persistence.canOperate(scope,actor)).isTrue();assertThat(sources.find(11L,actor,KNOWLEDGE,"proof")).isPresent();
        assertThat(handoff.candidates(scope,actor)).hasSize(1);
        assertThat(assignments.change(scope,assignmentAuthentication(actor),1,null,true)).isTrue();
        assertThat(participants.change(scope,assignmentAuthentication(actor),1,2L,false)).isTrue();
        assertThat(sources.createKnowledge(11L,actor,key(),"case","body")).isTrue();
        assertThat(workbench.createMemory(scope,actor,key(),40,41,questionHash,answerHash,"case","body",true)).isTrue();
        assertThat(persistence.setControl(scope,actor,false,true,"case")).isTrue();
        assertThat(jdbc.queryForObject("SELECT actor_user_id FROM business_ticket_assignment_events WHERE revision=2",Long.class)).isEqualTo(1);
    }
    @Test void literalSpacedAccountKeepsOnlyItsOwnBusinessAndNumericAuditIdentity() {
        String actor=" ADMIN ";
        assertThat(assignments.find(OTHER,assignmentAuthentication(actor))).isPresent();assertThat(workbench.resolve(OTHER,actor)).contains(other);
        deniedReads(actor);
        assertThat(assignments.change(other,assignmentAuthentication(actor),0,null,true)).isTrue();
        assertThat(participants.change(other,assignmentAuthentication(actor),0,3L,true)).isTrue();
        assertThat(sources.createKnowledge(22L,actor,"own","refund","own refund")).isTrue();
        assertThat(sources.revise(22L,actor,KNOWLEDGE,"own",1,"refund","own refund",APPROVED)).isTrue();
        assertThat(workbench.createMemory(other,actor,key(),50,51,fingerprint(50),fingerprint(51),"own","reviewed",true)).isTrue();
        assertThat(persistence.setControlExpected(other,actor,"",false,true,"own")).isTrue();
        assertThat(handoff.record(other,"refund")).isTrue();assertThat(handoff.candidates(other,actor)).hasSize(1);
        assertThat(jdbc.queryForMap("SELECT actor_user_id,assignee_user_id,actor FROM business_ticket_assignment_events WHERE business_id=22"))
            .containsEntry("actor_user_id",6L).containsEntry("assignee_user_id",6L).containsEntry("actor"," admin ");
        assertThat(jdbc.queryForMap("SELECT updated_by_user_id,updated_by FROM ai_scoped_sources WHERE business_id=22 AND source_kind='KNOWLEDGE'"))
            .containsEntry("updated_by_user_id",6L).containsEntry("updated_by"," admin ");
        assertThat(workbench.resolve(OTHER,"admin")).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings={"disabled","blocked","revoked","duplicate","archived"})
    void exactPrincipalStillNeedsCurrentUniqueEnabledAccountAndBusinessRights(String change) {
        switch(change) {
            case "disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=1");
            case "blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=1");
            case "revoked"->jdbc.update("DELETE FROM business_memberships WHERE user_id=1");
            case "duplicate"->jdbc.update("INSERT INTO users(id,username) VALUES(9,'ADMIN')");
            case "archived"->jdbc.update("UPDATE businesses SET status='archived' WHERE id=11");
        }
        var before=snapshot();deniedReads("admin");deniedWrites("admin");assertThat(snapshot()).isEqualTo(before);
    }
    @Test void missingCanonicalSchemaDoesNotCreateAliasesOrFallbackAuthority() {
        jdbc.execute("DROP TABLE business_memberships");
        assertThat(assignments.find(PUBLIC,assignmentAuthentication("admin"))).isEmpty();assertThat(participants.members(scope,assignmentAuthentication("admin"))).isEmpty();
        assertThat(workbench.resolve(PUBLIC,"admin")).isEmpty();assertThat(persistence.canOperate(scope,"admin")).isFalse();
        assertThat(sources.find(11L,"admin",KNOWLEDGE,"proof")).isEmpty();assertThat(handoff.candidates(scope,"admin")).isEmpty();
    }
}
