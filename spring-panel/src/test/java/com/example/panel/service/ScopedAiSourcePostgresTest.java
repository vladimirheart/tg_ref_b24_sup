package com.example.panel.service;

import static com.example.panel.service.ScopedAiSourceService.Kind.KNOWLEDGE;
import static com.example.panel.service.ScopedAiSourceService.Kind.MEMORY;
import static com.example.panel.service.ScopedAiSourceService.Status.APPROVED;
import static com.example.panel.service.ScopedAiSourceService.Status.ARCHIVED;
import static com.example.panel.service.ScopedAiSourceService.Status.DRAFT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.ScopedAiSourceService.Kind;
import com.example.panel.service.ScopedAiSourceService.Source;
import com.example.panel.support.PanelTimestampSqlSupport;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

class ScopedAiSourcePostgresTest {
    private static final String RAW=" source/Ю?% ";
    private static final String ORIGIN=" origin/ёж ?% ";
    private static final String PUBLIC="a".repeat(32);
    private static final String BODY="Общий ответ бизнеса A: секрет решения";
    private JdbcTemplate jdbc;
    private MultibusinessFeatureFlags flags;
    private ScopedAiSourceService service;
    private UiEventTicketScope scope;
    private UiEventTicketScope origin;

    @BeforeEach
    void setUp() throws Exception {
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("scoped_ai_sources");
        jdbc.execute("CREATE TABLE businesses(id BIGINT PRIMARY KEY,status TEXT,display_name TEXT)");
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,username TEXT,enabled BOOLEAN,is_blocked BOOLEAN)");
        jdbc.execute("CREATE TABLE user_authorities(user_id BIGINT,authority TEXT)");
        jdbc.execute("CREATE TABLE business_memberships(user_id BIGINT,business_id BIGINT,role_code TEXT,is_active BOOLEAN)");
        jdbc.execute("CREATE TABLE business_access_grants(user_id BIGINT,grant_scope TEXT,role_code TEXT,is_active BOOLEAN)");
        jdbc.execute("CREATE TABLE tickets(user_id BIGINT,ticket_id TEXT,ticket_public_id TEXT,is_general BOOLEAN,business_id BIGINT,PRIMARY KEY(user_id,ticket_id))");
        for (String migration:List.of("V62__scoped_ai_records.sql","V63__business_owned_ai_sources.sql")) {
            jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/postgresql",migration)));
        }
        jdbc.update("INSERT INTO businesses VALUES(1,'active','Business A'),(2,'active','Business B')");
        jdbc.update("INSERT INTO users VALUES(1,'admin',TRUE,FALSE),(2,'operator',TRUE,FALSE),(3,'viewer',TRUE,FALSE),"
            +"(4,'manager',TRUE,FALSE),(5,'foreign',TRUE,FALSE),(6,'global-admin',TRUE,FALSE),(7,'all-admin',TRUE,FALSE),(8,'all-viewer',TRUE,FALSE)");
        jdbc.update("INSERT INTO user_authorities VALUES(6,'ROLE_ADMIN')");
        jdbc.update("INSERT INTO business_memberships VALUES(1,1,'ADMIN',TRUE),(2,1,'OPERATOR',TRUE),(3,1,'VIEWER',TRUE),(4,1,'MANAGER',TRUE),(5,2,'ADMIN',TRUE)");
        jdbc.update("INSERT INTO business_access_grants VALUES(7,'ALL_BUSINESSES','ADMIN',TRUE),(8,'ALL_BUSINESSES','VIEWER',TRUE)");
        jdbc.update("INSERT INTO tickets VALUES(10,?,?,FALSE,1),(20,?,?,FALSE,2),(30,?,?,FALSE,1)",RAW,PUBLIC,RAW.trim(),"b".repeat(32),ORIGIN,"c".repeat(32));
        flags=new MultibusinessFeatureFlags();
        service=new ScopedAiSourceService(jdbc,flags);
        scope=capture(RAW);
        origin=capture(ORIGIN);
    }

    static Stream<Arguments> rolesAndGrants() {
        return Stream.of("VIEWER","OPERATOR","MANAGER","ADMIN","UNKNOWN").flatMap(role->
            Stream.of("membership","all").map(grant->Arguments.of(role,grant)));
    }

    @ParameterizedTest @MethodSource("rolesAndGrants")
    void readAndConfigureFollowCanonicalRolesForEachIndependentGrant(String role,String grant) {
        Source source=approved(KNOWLEDGE," exact/key ");
        jdbc.update("DELETE FROM business_memberships WHERE user_id=2");
        if (grant.equals("membership")) jdbc.update("INSERT INTO business_memberships VALUES(2,1,?,TRUE)",role);
        else jdbc.update("INSERT INTO business_access_grants VALUES(2,'ALL_BUSINESSES',?,TRUE)",role);
        boolean read=!role.equals("UNKNOWN"),configure=role.equals("ADMIN");
        assertThat(service.availableBusinesses(com.example.panel.support.AiControlIdentityFixture.login(jdbc,"OPERATOR"))).hasSize(read ? (grant.equals("all") ? 2 : 1) : 0);
        assertThat(service.canConfigure(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"operator"))).isEqualTo(configure);
        assertThat(service.find(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"OPERATOR"),KNOWLEDGE,source.key()).isPresent()).isEqualTo(read);
        assertThat(service.history(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"operator"),KNOWLEDGE,source.key(),1)).hasSize(read?1:0);
        assertThat(service.retrieve(scope,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"operator"),"решения",1)).hasSize(read?1:0);
        assertThat(service.isCurrentCandidate(scope,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"operator"),source)).isEqualTo(read);
        assertThat(service.createKnowledge(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"operator"),"other","Title",BODY)).isEqualTo(configure);
        assertThat(service.createMemory(origin,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"operator"),"memory","Title",BODY)).isEqualTo(configure);
        assertThat(service.revise(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"operator"),KNOWLEDGE,source.key(),2,"Changed",BODY,APPROVED)).isEqualTo(configure);
        if (configure) assertThat(jdbc.queryForObject("SELECT updated_by_user_id FROM ai_scoped_sources WHERE source_key=?",Long.class,source.key())).isEqualTo(2L);
        assertThat(service.availableBusinesses(com.example.panel.support.AiControlIdentityFixture.login(jdbc," OPERATOR "))).isEmpty();
        assertThat(service.find(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc," OPERATOR "),KNOWLEDGE,source.key())).isEmpty();
        assertThat(service.canConfigure(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc," OPERATOR "))).isFalse();
        assertThat(service.createKnowledge(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc," OPERATOR "),"principal-role-denied","Title",BODY)).isFalse();

    }

    @ParameterizedTest @ValueSource(strings={"foreign","global-admin","missing",""," "})
    void globalAdminAndUnrelatedIdentitiesDoNotGainBusinessSourceAccess(String identity) {
        Source source=approved(KNOWLEDGE,"same-key");
        assertThat(service.find(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,identity),KNOWLEDGE,source.key())).isEmpty();
        assertThat(service.retrieve(scope,com.example.panel.support.AiControlIdentityFixture.login(jdbc,identity),"решения",1)).isEmpty();
        assertThat(service.isCurrentCandidate(scope,com.example.panel.support.AiControlIdentityFixture.login(jdbc,identity),source)).isFalse();
        assertThat(service.createKnowledge(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,identity),"other","Title",BODY)).isFalse();
        assertThat(service.revise(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,identity),KNOWLEDGE,source.key(),2,"Changed",BODY,APPROVED)).isFalse();
    }

    @Test
    void allBusinessesStillRetrievesOnlyTheRequestedBusinessAndKeysDoNotCollide() {
        Source a=approved(KNOWLEDGE,"same-key");
        assertThat(service.createKnowledge(2L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"foreign"),"same-key","Business B",BODY+" B")).isTrue();
        assertThat(service.revise(2L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"foreign"),KNOWLEDGE,"same-key",1,"Business B",BODY+" B",APPROVED)).isTrue();
        assertThat(service.retrieve(scope,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"all-admin"),"решения",20)).containsExactly(a);
        var b=service.retrieve(capture(RAW.trim()),com.example.panel.support.AiControlIdentityFixture.login(jdbc,"all-viewer"),"решения",20);
        assertThat(b).hasSize(1);
        assertThat(b.get(0).businessId()).isEqualTo(2L);
        assertThat(service.isCurrentCandidate(scope,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"all-admin"),b.get(0))).isFalse();
        jdbc.execute("CREATE TABLE ai_agent_knowledge_unit(unit_key TEXT,body_text TEXT,business TEXT,status TEXT)");
        jdbc.execute("CREATE TABLE ai_agent_solution_memory(query_key TEXT,solution_text TEXT,scope_business TEXT,status TEXT)");
        jdbc.update("INSERT INTO ai_agent_knowledge_unit VALUES('same-key','legacy global secret',NULL,'active')");
        jdbc.update("INSERT INTO ai_agent_solution_memory VALUES('same-key','legacy memory secret','1','approved')");
        assertThat(service.retrieve(scope,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"all-admin"),"legacy",20)).isEmpty();
        assertThat(service.retrieve(scope,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"all-admin"),"решения",20)).containsExactly(a);
    }

    @Test
    void independentGrantsAreCombinedWithoutGlobalAdminBypass() {
        jdbc.update("INSERT INTO business_access_grants VALUES(2,'ALL_BUSINESSES','ADMIN',TRUE)");
        jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=2");
        assertThat(service.createKnowledge(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"operator"),"key","Title",BODY)).isTrue();
        jdbc.update("UPDATE business_access_grants SET is_active=FALSE WHERE user_id=2");
        jdbc.update("UPDATE business_memberships SET is_active=TRUE,role_code='VIEWER' WHERE user_id=2");
        assertThat(service.find(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"operator"),KNOWLEDGE,"key")).isPresent();
        assertThat(service.revise(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"operator"),KNOWLEDGE,"key",1,"Title",BODY,APPROVED)).isFalse();
        jdbc.update("UPDATE business_memberships SET role_code='ADMIN' WHERE user_id=2");
        assertThat(service.revise(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"operator"),KNOWLEDGE,"key",1,"Title",BODY,APPROVED)).isTrue();
    }

    @ParameterizedTest @ValueSource(strings={"revoke","delete","disabled","blocked","identity_collision","archive","role"})
    void everyReadWriteAndCandidateProofUsesLiveActorAuthority(String mutation) {
        Source source=approved(KNOWLEDGE,"key");
        var before=snapshots();
        mutateActor(mutation);
        assertThat(service.find(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),KNOWLEDGE,"key")).isEmpty();
        assertThat(service.history(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),KNOWLEDGE,"key",5)).isEmpty();
        assertThat(service.retrieve(scope,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),"решения",1)).isEmpty();
        assertThat(service.isCurrentCandidate(scope,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),source)).isFalse();
        assertThat(service.createKnowledge(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),"other","Title",BODY)).isFalse();
        assertThat(service.createMemory(origin,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),"memory","Title",BODY)).isFalse();
        assertThat(service.revise(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),KNOWLEDGE,"key",2,"Changed",BODY,APPROVED)).isFalse();
        assertThat(service.availableBusinesses(com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"))).isEmpty();
        assertThat(service.canConfigure(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"))).isFalse();
        assertThat(service.catalog(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),null,null,"",null,26)).isEmpty();
        assertThat(snapshots()).isEqualTo(before);
    }

    @Test
    void catalogFiltersLiveBusinessOriginAndStatusBeforeKeysetPaginationWithoutReturningBody() {
        Source a=approved(KNOWLEDGE,"a");
        Source memory=approved(MEMORY,"memory");
        assertThat(service.createKnowledge(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),"draft","Draft title","Only %_ literal")).isTrue();
        jdbc.update("INSERT INTO ai_scoped_sources(business_id,source_kind,source_key,title,body,status,revision,updated_by_user_id,updated_by)"
            +" SELECT 2,'KNOWLEDGE','foreign-'||i,'Foreign secret',?,'DRAFT',1,5,'foreign' FROM generate_series(1,30) i",BODY);
        assertThat(service.catalog(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"all-admin"),KNOWLEDGE,APPROVED,"",null,1)).extracting(ScopedAiSourceService.Summary::id).containsExactly(a.id());
        assertThat(service.catalog(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"viewer"),null,null,"",null,26)).hasSize(3);
        assertThat(service.catalog(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"viewer"),null,null,"%_",null,1)).extracting(ScopedAiSourceService.Summary::key).containsExactly("draft");
        assertThat(service.catalog(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"viewer"),null,null,"",memory.id(),26)).extracting(ScopedAiSourceService.Summary::id).containsExactly(a.id());
        assertThat(service.catalog(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"viewer"),null,null,"",null,26).toString()).doesNotContain(BODY,ORIGIN);
        mutateOwner(30,"generation");
        assertThat(service.catalog(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"all-admin"),MEMORY,null,"",null,1)).isEmpty();
        assertThat(service.catalog(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"foreign"),null,null,"",null,1)).isEmpty();
    }

    @Test
    void anExpectedSourceIdCannotUpdateAnotherBusinessOrARecreatedKeyAtTheSameRevision() {
        Source original=approved(KNOWLEDGE,"same");
        jdbc.update("DELETE FROM ai_scoped_source_events WHERE source_id=?",original.id());
        jdbc.update("DELETE FROM ai_scoped_sources WHERE id=?",original.id());
        Source replacement=approved(KNOWLEDGE,"same");
        var before=snapshots();
        assertThat(service.reviseExpected(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),KNOWLEDGE,"same",original.id(),2,"Wrong",BODY,APPROVED)).isFalse();
        assertThat(snapshots()).isEqualTo(before);
        assertThat(service.reviseExpected(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),KNOWLEDGE,"same",replacement.id(),2,"New",BODY,DRAFT)).isTrue();
        assertThat(service.find(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"viewer"),KNOWLEDGE,"same").orElseThrow().revision()).isEqualTo(3);
    }

    @ParameterizedTest @ValueSource(strings={"revoke","disabled","blocked","identity_collision","archive","role"})
    void configureIsRecheckedInsideTheFinalMutationStatement(String mutation) {
        Source source=approved(KNOWLEDGE,"key");
        var before=snapshots();
        var observed=spy(new NamedParameterJdbcTemplate(jdbc));
        doAnswer(call->{
            mutateActor(mutation);
            return new NamedParameterJdbcTemplate(jdbc).update(call.getArgument(0,String.class),call.getArgument(1,SqlParameterSource.class));
        }).when(observed).update(anyString(),any(SqlParameterSource.class));
        var guarded=new ScopedAiSourceService(observed,flags);
        assertThat(guarded.revise(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),KNOWLEDGE,"key",source.revision(),"Changed",BODY,APPROVED)).isFalse();
        assertThat(snapshots()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings={"business","generation","tuple","raw","general","missing","collision","unresolved","archive"})
    void staleRequestOwnerCannotRetrieveOrRevalidateEvidence(String mutation) {
        Source source=approved(KNOWLEDGE,"key");
        mutateOwner(10,mutation);
        assertThat(service.retrieve(scope,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"all-admin"),"решения",1)).isEmpty();
        assertThat(service.isCurrentCandidate(scope,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"all-admin"),source)).isFalse();
    }

    @ParameterizedTest @ValueSource(strings={"business","generation","tuple","raw","general","missing","collision","unresolved"})
    void memoryKeepsItsExactOriginAndCannotBeRetaggedOrUsedAfterOriginChanges(String mutation) {
        Source memory=approved(MEMORY,"same-key");
        Source knowledge=approved(KNOWLEDGE,"same-key");
        var before=snapshots();
        mutateOwner(30,mutation);
        assertThat(service.find(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"all-admin"),MEMORY,"same-key")).isEmpty();
        assertThat(service.history(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"all-admin"),MEMORY,"same-key",5)).isEmpty();
        assertThat(service.retrieve(scope,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"all-admin"),"решения",20)).containsExactly(knowledge);
        assertThat(service.isCurrentCandidate(scope,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"all-admin"),memory)).isFalse();
        assertThat(service.createMemory(origin,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"all-admin"),"other","Title",BODY)).isFalse();
        assertThat(service.revise(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"all-admin"),MEMORY,"same-key",2,"Changed",BODY,APPROVED)).isFalse();
        assertThat(snapshots()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings={"business","generation","tuple","raw","general","missing","collision","unresolved"})
    void originIsRecheckedInsideTheMemoryInsertAfterScopeValidation(String mutation) {
        var observed=spy(new NamedParameterJdbcTemplate(jdbc));
        doAnswer(call->{
            mutateOwner(30,mutation);
            return new NamedParameterJdbcTemplate(jdbc).update(call.getArgument(0,String.class),call.getArgument(1,SqlParameterSource.class));
        }).when(observed).update(anyString(),any(SqlParameterSource.class));
        assertThat(new ScopedAiSourceService(observed,flags).createMemory(origin,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"all-admin"),"key","Title",BODY)).isFalse();
        assertThat(jdbc.queryForList("SELECT * FROM ai_scoped_sources")).isEmpty();
        assertThat(jdbc.queryForList("SELECT * FROM ai_scoped_source_events")).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings={"business","generation"})
    void aNewOriginCannotInheritOrOverwriteTheOldMemoryIdentity(String mutation) {
        Source original=approved(MEMORY,"same-key");
        mutateOwner(30,mutation);
        var fresh=capture(ORIGIN);
        assertThat(service.find(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"all-admin"),MEMORY,"same-key")).isEmpty();
        boolean otherBusiness=mutation.equals("business");
        assertThat(service.createMemory(fresh,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"all-admin"),"same-key","New",BODY)).isEqualTo(otherBusiness);
        if (!otherBusiness) assertThat(service.createMemory(fresh,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"all-admin"),"new-key","New",BODY)).isTrue();
        assertThat(jdbc.queryForMap("SELECT origin_ticket_public_id,origin_ticket_business_id,revision,body FROM ai_scoped_sources WHERE id=?",original.id()))
            .containsEntry("origin_ticket_public_id",origin.ticketPublicId()).containsEntry("origin_ticket_business_id",1L)
            .containsEntry("revision",2L).containsEntry("body",BODY);
    }

    @ParameterizedTest @ValueSource(strings={RAW,"T/slash","T?#[]&=+","Обращение-ёж","T%2F%25","null"})
    void typedRetrievalPreservesExactOpaqueTicketAndSourceKeysWithoutEnteringLegacyStages(String id) {
        jdbc.update("UPDATE tickets SET ticket_id=? WHERE user_id=10",id);
        scope=capture(id);
        Source memory=approved(MEMORY," opaque/key?% ");
        var rawJdbc=mock(JdbcTemplate.class);
        var intents=mock(AiIntentService.class);
        var timestamps=mock(PanelTimestampSqlSupport.class);
        var facade=new AiRetrievalService(rawJdbc,intents,timestamps,service);
        var result=facade.retrieveScoped(scope,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"viewer"),"решения",1);
        assertThat(result.scope()).isEqualTo(scope);
        assertThat(result.candidates()).containsExactly(memory);
        assertThat(result.autoReplyAllowed()).isFalse();
        assertThat(memory.origin()).isEqualTo(origin);
        assertThat(memory.key()).isEqualTo(" opaque/key?% ");
        assertThat(memory.toString()).doesNotContain(BODY,ORIGIN,memory.key());
        assertThat(service.isCurrentCandidate(scope,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"viewer"),memory)).isTrue();
        assertThat(new AiRetrievalService(rawJdbc,intents,timestamps).retrieveScoped(scope,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"viewer"),"решения",1).candidates()).isEmpty();
        verifyNoInteractions(rawJdbc,intents,timestamps);
    }

    @Test
    void reviewAndCompareAndSetPreventStaleApprovalAndKeepBodyOutOfAudit() {
        assertThat(service.createKnowledge(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),"key","Title",BODY)).isTrue();
        assertThat(service.retrieve(scope,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"viewer"),"решения",1)).isEmpty();
        assertThat(service.createKnowledge(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),"key","Other",BODY)).isFalse();
        assertThat(service.revise(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),KNOWLEDGE,"key",1,"Title",BODY,APPROVED)).isTrue();
        Source original=service.retrieve(scope,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"viewer"),"решения",1).get(0);
        assertThat(service.revise(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),KNOWLEDGE,"key",2,"New title",BODY+" changed",DRAFT)).isTrue();
        assertThat(service.retrieve(scope,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"viewer"),"решения",1)).isEmpty();
        assertThat(service.isCurrentCandidate(scope,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"viewer"),original)).isFalse();
        assertThat(service.revise(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),KNOWLEDGE,"key",2,"Stale",BODY,APPROVED)).isFalse();
        assertThat(service.revise(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),KNOWLEDGE,"key",3,"New title",BODY+" changed",APPROVED)).isTrue();
        assertThat(service.retrieve(scope,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"viewer"),"решения",1).get(0).revision()).isEqualTo(4);
        assertThat(service.revise(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),KNOWLEDGE,"key",4,"New title",BODY,ARCHIVED)).isTrue();
        assertThat(service.retrieve(scope,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"viewer"),"решения",1)).isEmpty();
        var history=service.history(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"viewer"),KNOWLEDGE,"key",2);
        assertThat(history).hasSize(2);
        assertThat(history.get(0)).containsEntry("revision",5L).containsEntry("status","ARCHIVED");
        assertThat(history.toString()).doesNotContain(BODY,"New title");
    }

    @Test
    void sourceAndAuditAreAtomicForCreationAndRevision() {
        assertThat(service.createKnowledge(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),"key","Title",BODY)).isTrue();
        var before=snapshots();
        jdbc.execute("ALTER TABLE ai_scoped_source_events ADD CONSTRAINT reject_events CHECK(FALSE) NOT VALID");
        assertThat(service.revise(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),KNOWLEDGE,"key",1,"Changed",BODY,APPROVED)).isFalse();
        assertThat(service.createMemory(origin,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),"other","Title",BODY)).isFalse();
        assertThat(snapshots()).isEqualTo(before);
    }

    @Test
    void sourceVisibilityAndLiteralSearchAreAppliedBeforeOrderAndLimit() {
        Source a=approved(KNOWLEDGE,"key");
        jdbc.update("INSERT INTO ai_scoped_sources(business_id,source_kind,source_key,title,body,status,revision,updated_by_user_id,updated_by)"
            +" SELECT 2,'KNOWLEDGE','foreign-'||i,'Title',?,'APPROVED',1,5,'foreign' FROM generate_series(1,30) i",BODY);
        assertThat(service.createKnowledge(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),"draft","Title",BODY)).isTrue();
        assertThat(service.retrieve(scope,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"all-admin"),"решения",1)).containsExactly(a);
        assertThat(service.retrieve(scope,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"all-admin"),"%_",20)).isEmpty();
        assertThat(service.createKnowledge(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),"literal","Title","Ответ с %_ буквально")).isTrue();
        assertThat(service.revise(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),KNOWLEDGE,"literal",1,"Title","Ответ с %_ буквально",APPROVED)).isTrue();
        assertThat(service.retrieve(scope,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"viewer"),"%_",1)).extracting(Source::key).containsExactly("literal");
        for (int i=0;i<21;i++) approved(KNOWLEDGE,"extra-"+i);
        assertThat(service.retrieve(scope,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"all-admin"),"решения",200)).hasSize(20);
        assertThat(service.retrieve(scope,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"all-admin"),"решения",0)).hasSize(1);
    }

    @ParameterizedTest @ValueSource(strings={"actor","request_owner","origin_owner","control","revision","status"})
    void finalCandidateProofRejectsChangesAfterRetrievalAtTheActualSqlBoundary(String mutation) {
        Source source=approved(MEMORY,"key");
        var observed=spy(new NamedParameterJdbcTemplate(jdbc));
        doAnswer(call->{
            switch (mutation) {
                case "actor" -> mutateActor("revoke");
                case "request_owner" -> mutateOwner(10,"business");
                case "origin_owner" -> mutateOwner(30,"generation");
                case "control" -> disableAi();
                case "revision" -> service.revise(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),MEMORY,"key",2,"Changed",BODY,APPROVED);
                case "status" -> service.revise(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),MEMORY,"key",2,"Title",BODY,ARCHIVED);
                default -> throw new IllegalArgumentException(mutation);
            }
            return new NamedParameterJdbcTemplate(jdbc).queryForObject(call.getArgument(0,String.class),call.getArgument(1,SqlParameterSource.class),Boolean.class);
        }).when(observed).queryForObject(anyString(),any(SqlParameterSource.class),eq(Boolean.class));
        assertThat(new ScopedAiSourceService(observed,flags).isCurrentCandidate(scope,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),source)).isFalse();
    }

    @Test
    void candidateProofChecksPayloadAndOriginRatherThanTrustingAnIdAndRevision() {
        Source a=approved(MEMORY,"key");
        for (Source forged:List.of(
                new Source(a.id(),a.businessId(),a.kind(),a.key(),"Forged",a.body(),a.status(),a.revision(),a.origin()),
                new Source(a.id(),a.businessId(),a.kind(),a.key(),a.title(),"Forged",a.status(),a.revision(),a.origin()),
                new Source(a.id(),a.businessId(),a.kind(),"other",a.title(),a.body(),a.status(),a.revision(),a.origin()),
                new Source(a.id(),a.businessId(),a.kind(),a.key(),a.title(),a.body(),a.status(),a.revision(),scope),
                new Source(a.id(),a.businessId(),a.kind(),a.key(),a.title(),a.body(),a.status(),a.revision(),null),
                new Source(a.id(),2L,a.kind(),a.key(),a.title(),a.body(),a.status(),a.revision(),a.origin()))) {
            assertThat(service.isCurrentCandidate(scope,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"all-admin"),forged)).isFalse();
        }
        disableAi();
        assertThat(service.retrieve(scope,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"all-admin"),"решения",1)).isEmpty();
        assertThat(service.isCurrentCandidate(scope,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"all-admin"),a)).isFalse();
        assertThat(service.find(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"viewer"),MEMORY,"key")).isPresent();
    }

    @ParameterizedTest @ValueSource(strings={"ai_scoped_sources","business_memberships","business_access_grants","businesses","tickets","ticket_ai_scoped_control"})
    void missingRelevantSchemaNeverOpensLegacyFallback(String table) {
        Source source=approved(KNOWLEDGE,"key");
        jdbc.execute("DROP TABLE "+table+" CASCADE");
        assertThat(service.retrieve(scope,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"all-admin"),"решения",1)).isEmpty();
        assertThat(service.isCurrentCandidate(scope,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"all-admin"),source)).isFalse();
        if (!table.equals("tickets") && !table.equals("ticket_ai_scoped_control")) {
            assertThat(service.createKnowledge(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),"other","Title",BODY)).isFalse();
            assertThat(service.find(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),KNOWLEDGE,"key")).isEmpty();
        }
    }

    @Test
    void invalidInputsUnresolvedAndGeneralStopBeforeJdbcAndLegacyRetrieval() {
        var observed=mock(NamedParameterJdbcOperations.class);
        var guarded=new ScopedAiSourceService(observed,flags);
        for (var invalid:Arrays.asList(null,new UiEventTicketScope(10L,RAW,PUBLIC,"GENERAL",null),
                new UiEventTicketScope(10L,RAW,PUBLIC,"UNRESOLVED",null),new UiEventTicketScope(10L,RAW,null,"BUSINESS",1L))) {
            assertThat(guarded.createMemory(invalid,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),"key","Title",BODY)).isFalse();
            assertThat(guarded.retrieve(invalid,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),"решения",1)).isEmpty();
            assertThat(guarded.isCurrentCandidate(invalid,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),null)).isFalse();
        }
        assertThat(guarded.createKnowledge(null,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),"key","Title",BODY)).isFalse();
        assertThat(guarded.createKnowledge(0L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),"key","Title",BODY)).isFalse();
        assertThat(guarded.createKnowledge(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc," "),"key","Title",BODY)).isFalse();
        assertThat(guarded.createKnowledge(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin")," ","Title",BODY)).isFalse();
        assertThat(guarded.createKnowledge(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),"key"," ",BODY)).isFalse();
        assertThat(guarded.createKnowledge(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),"key","Title","x".repeat(16001))).isFalse();
        assertThat(guarded.revise(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),null,"key",1,"Title",BODY,APPROVED)).isFalse();
        assertThat(guarded.revise(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),KNOWLEDGE,"key",0,"Title",BODY,APPROVED)).isFalse();
        assertThat(guarded.find(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),KNOWLEDGE," ")).isEmpty();
        assertThat(guarded.history(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),null,"key",1)).isEmpty();
        assertThat(guarded.retrieve(scope,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin")," ",1)).isEmpty();
        assertThat(guarded.availableBusinesses(com.example.panel.support.AiControlIdentityFixture.login(jdbc," "))).isEmpty();
        assertThat(guarded.canConfigure(null,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"))).isFalse();
        assertThat(guarded.catalog(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),null,null,null,null,1)).isEmpty();
        assertThat(guarded.catalog(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),null,null,"",0L,1)).isEmpty();
        assertThat(guarded.reviseExpected(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),KNOWLEDGE,"key",0,1,"Title",BODY,DRAFT)).isFalse();
        assertThat(new ScopedAiSourceService(observed,null).retrieve(scope,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),"решения",1)).isEmpty();
        verifyNoInteractions(observed);
    }

    @ParameterizedTest @ValueSource(strings={"KNOWLEDGE","MEMORY"})
    void explicitErasureRemovesOnlyContentAndInvalidatesOldCandidates(String sourceKind) {
        Source original=approved(Kind.valueOf(sourceKind),"erase");
        assertThat(service.isCurrentCandidate(scope,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),original)).isTrue();
        var before=jdbc.queryForMap("SELECT id,business_id,source_kind,source_key,origin_ticket_id,created_at FROM ai_scoped_sources WHERE id=?",original.id());
        assertThat(service.createKnowledge(2L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"foreign"),"erase","Foreign title",BODY+" B")).isTrue();
        var foreign=jdbc.queryForMap("SELECT * FROM ai_scoped_sources WHERE business_id=2");
        assertThat(erase(original,"admin")).isTrue();
        var cleared=service.find(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"viewer"),original.kind(),original.key()).orElseThrow();
        assertThat(cleared.title()).isEqualTo("Материал очищен");
        assertThat(cleared.body()).isEqualTo("Содержимое очищено.");
        assertThat(cleared.status()).isEqualTo(ARCHIVED);
        assertThat(cleared.revision()).isEqualTo(original.revision()+1);
        assertThat(jdbc.queryForMap("SELECT id,business_id,source_kind,source_key,origin_ticket_id,created_at FROM ai_scoped_sources WHERE id=?",original.id())).isEqualTo(before);
        assertThat(jdbc.queryForMap("SELECT * FROM ai_scoped_sources WHERE business_id=2")).isEqualTo(foreign);
        assertThat(jdbc.queryForMap("SELECT actor_user_id,actor,revision,action FROM ai_scoped_source_events WHERE source_id=? ORDER BY revision DESC LIMIT 1",original.id()))
            .containsEntry("actor_user_id",1L).containsEntry("actor","admin").containsEntry("revision",3L).containsEntry("action","revise");
        assertThat(jdbc.queryForList("SELECT * FROM ai_scoped_source_events").toString()).doesNotContain(BODY,"Foreign title");
        assertThat(service.retrieve(scope,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),"решения",20)).isEmpty();
        assertThat(service.isCurrentCandidate(scope,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),original)).isFalse();
        var after=snapshots();
        assertThat(erase(original,"admin")).isFalse();
        assertThat(snapshots()).isEqualTo(after);
    }

    @ParameterizedTest @ValueSource(strings={"viewer","operator","manager","foreign","global-admin","all-viewer","all-admin","admin"})
    void erasureRequiresLiveBusinessConfigureRatherThanSystemRole(String actor) {
        Source original=approved(KNOWLEDGE,"erase");
        var before=snapshots();
        boolean configure=actor.equals("admin") || actor.equals("all-admin");
        assertThat(erase(original,actor)).isEqualTo(configure);
        if (!configure) assertThat(snapshots()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings={"revoke","disabled","blocked","identity_collision","archive","role","rename","reuse"})
    void erasureRechecksTheBoundAccountInsideSql(String mutation) {
        Source original=approved(KNOWLEDGE,"erase");
        var actor=com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin");
        var before=snapshots();
        var observed=spy(new NamedParameterJdbcTemplate(jdbc));
        doAnswer(call->{
            if (mutation.equals("rename") || mutation.equals("reuse")) {
                jdbc.update("UPDATE users SET username='renamed' WHERE id=1");
                if (mutation.equals("reuse")) {
                    jdbc.update("INSERT INTO users VALUES(9,'admin',TRUE,FALSE)");
                    jdbc.update("INSERT INTO business_memberships VALUES(9,1,'ADMIN',TRUE)");
                }
            } else mutateActor(mutation);
            return new NamedParameterJdbcTemplate(jdbc).update(call.getArgument(0,String.class),call.getArgument(1,SqlParameterSource.class));
        }).when(observed).update(anyString(),any(SqlParameterSource.class));
        assertThat(new ScopedAiSourceService(observed,flags).eraseContentExpected(1L,actor,original.kind(),original.key(),original.id(),original.revision())).isFalse();
        assertThat(snapshots()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings={"business","missing"})
    void frozenBusinessCanEraseMemoryEvenAfterTheOriginMovesOrDisappears(String mutation) {
        Source original=approved(MEMORY,"erase");
        mutateOwner(30,mutation);
        var tickets=jdbc.queryForList("SELECT * FROM tickets ORDER BY user_id");
        assertThat(service.find(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),MEMORY,original.key())).isEmpty();
        assertThat(erase(original,"admin")).isTrue();
        assertThat(jdbc.queryForMap("SELECT business_id,body,status,revision FROM ai_scoped_sources WHERE id=?",original.id()))
            .containsEntry("business_id",1L).containsEntry("body","Содержимое очищено.").containsEntry("status","ARCHIVED").containsEntry("revision",3L);
        assertThat(jdbc.queryForList("SELECT * FROM tickets ORDER BY user_id")).isEqualTo(tickets);
    }

    @Test
    void erasureFreezesBusinessKindKeyIdAndRevisionAndRollsBackOnAuditFailure() {
        Source original=approved(KNOWLEDGE,"erase");
        var actor=com.example.panel.support.AiControlIdentityFixture.login(jdbc,"all-admin");
        var before=snapshots();
        assertThat(service.eraseContentExpected(2L,actor,KNOWLEDGE,"erase",original.id(),2)).isFalse();
        assertThat(service.eraseContentExpected(1L,actor,MEMORY,"erase",original.id(),2)).isFalse();
        assertThat(service.eraseContentExpected(1L,actor,KNOWLEDGE,"other",original.id(),2)).isFalse();
        assertThat(service.eraseContentExpected(1L,actor,KNOWLEDGE,"erase",original.id()+1,2)).isFalse();
        assertThat(service.eraseContentExpected(1L,actor,KNOWLEDGE,"erase",original.id(),1)).isFalse();
        assertThat(snapshots()).isEqualTo(before);
        jdbc.execute("ALTER TABLE ai_scoped_source_events ADD CONSTRAINT reject_erase CHECK(FALSE) NOT VALID");
        assertThat(erase(original,"admin")).isFalse();
        assertThat(snapshots()).isEqualTo(before);
    }

    @Test
    void invalidErasureInputsAndUnboundOrMismatchedPrincipalsCannotWrite() {
        var observed=mock(NamedParameterJdbcOperations.class);
        var guarded=new ScopedAiSourceService(observed,flags);
        var actor=com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin");
        assertThat(guarded.eraseContentExpected(null,actor,KNOWLEDGE,"erase",1,1)).isFalse();
        assertThat(guarded.eraseContentExpected(1L,actor,null,"erase",1,1)).isFalse();
        assertThat(guarded.eraseContentExpected(1L,actor,KNOWLEDGE," ",1,1)).isFalse();
        assertThat(guarded.eraseContentExpected(1L,actor,KNOWLEDGE,"erase",0,1)).isFalse();
        assertThat(guarded.eraseContentExpected(1L,actor,KNOWLEDGE,"erase",1,0)).isFalse();
        var unbound=org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated("admin",null,List.of());
        assertThat(guarded.eraseContentExpected(1L,unbound,KNOWLEDGE,"erase",1,1)).isFalse();
        verifyNoInteractions(observed);
        Source original=approved(KNOWLEDGE,"erase");
        var before=snapshots();
        assertThat(service.eraseContentExpected(1L,com.example.panel.support.AiControlIdentityFixture.auth(5,"admin"),KNOWLEDGE,"erase",original.id(),2)).isFalse();
        assertThat(snapshots()).isEqualTo(before);
    }


    @ParameterizedTest @ValueSource(strings={"business","generation","tuple","raw","general","missing","collision","unresolved"})
    void orphanRecoveryListsOnlyMetadataAndErasesWithoutReadingTheLostOrigin(String mutation) {
        Source original=approved(MEMORY,"recover");
        mutateOwner(30,mutation);
        var actor=com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin");
        var roots=jdbc.queryForList("SELECT * FROM tickets ORDER BY user_id,ticket_id");
        assertThat(service.find(1L,actor,MEMORY,original.key())).isEmpty();
        var rows=service.orphaned(1L,actor,null,26);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).id()).isEqualTo(original.id());
        assertThat(rows.get(0).revision()).isEqualTo(original.revision());
        assertThat(rows.get(0).contentErased()).isFalse();
        assertThat(rows.toString()).doesNotContain(BODY,"Title",ORIGIN,PUBLIC);
        assertThat(service.eraseOrphanContentExpected(1L,actor,original.key(),original.id(),original.revision())).isTrue();
        assertThat(service.orphaned(1L,actor,null,26).get(0).contentErased()).isTrue();
        assertThat(jdbc.queryForMap("SELECT body,status,revision FROM ai_scoped_sources WHERE id=?",original.id()))
            .containsEntry("body","Содержимое очищено.").containsEntry("status","ARCHIVED").containsEntry("revision",3L);
        assertThat(jdbc.queryForList("SELECT * FROM tickets ORDER BY user_id,ticket_id")).isEqualTo(roots);
        assertThat(service.retrieve(scope,actor,"решения",20)).isEmpty();
        var after=snapshots();
        assertThat(service.eraseOrphanContentExpected(1L,actor,original.key(),original.id(),3)).isFalse();
        assertThat(snapshots()).isEqualTo(after);
    }

    @ParameterizedTest @ValueSource(strings={"admin","all-admin","viewer","operator","manager","foreign","global-admin","all-viewer"})
    void orphanMetadataAndErasureRequireConfigureOfItsImmutableBusiness(String identity) {
        Source original=approved(MEMORY,"shared-key");
        mutateOwner(30,"business");
        var actor=com.example.panel.support.AiControlIdentityFixture.login(jdbc,identity);
        boolean allowed=identity.equals("admin")||identity.equals("all-admin");
        assertThat(service.orphaned(1L,actor,null,26)).hasSize(allowed?1:0);
        assertThat(service.orphaned(2L,actor,null,26)).isEmpty();
        var before=snapshots();
        assertThat(service.eraseOrphanContentExpected(1L,actor,original.key(),original.id(),2)).isEqualTo(allowed);
        if(!allowed)assertThat(snapshots()).isEqualTo(before);
    }

    @Test void orphanPaginationFiltersHealthyKnowledgeAndForeignRowsBeforeLimit() {
        var actor=com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin");
        var ids=new java.util.ArrayList<Long>();
        for(int i=0;i<5;i++){
            assertThat(service.createMemory(origin,actor,"orphan-"+i,"Secret title",BODY)).isTrue();
            ids.add(service.find(1L,actor,MEMORY,"orphan-"+i).orElseThrow().id());
        }
        assertThat(service.createMemory(scope,actor,"healthy","Healthy",BODY)).isTrue();
        for(int i=0;i<6;i++)assertThat(service.createKnowledge(1L,actor,"knowledge-"+i,"Knowledge",BODY)).isTrue();
        var foreignScope=new UiEventTicketScope(20L,RAW.trim(),"b".repeat(32),"BUSINESS",2L);
        var foreign=com.example.panel.support.AiControlIdentityFixture.login(jdbc,"foreign");
        assertThat(service.createMemory(foreignScope,foreign,"orphan-4","FOREIGN_SECRET",BODY+" B")).isTrue();
        mutateOwner(30,"missing");
        var one=service.orphaned(1L,actor,null,2);
        assertThat(one).extracting(ScopedAiSourceService.OrphanSummary::id).containsExactly(ids.get(4),ids.get(3));
        var two=service.orphaned(1L,actor,one.get(1).id(),2);
        assertThat(two).extracting(ScopedAiSourceService.OrphanSummary::id).containsExactly(ids.get(2),ids.get(1));
        assertThat(service.orphaned(1L,actor,two.get(1).id(),2)).extracting(ScopedAiSourceService.OrphanSummary::id).containsExactly(ids.get(0));
        assertThat(service.orphaned(1L,actor,ids.get(0),2)).isEmpty();
        assertThat(service.orphaned(1L,actor,0L,2)).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings={"restore","revoke","disabled","blocked","archive","rename","reuse"})
    void orphanWriteRechecksOriginAndCurrentActorInsideFinalSql(String mutation) {
        Source original=approved(MEMORY,"recover");
        mutateOwner(30,"business");
        var actor=com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin");
        assertThat(service.orphaned(1L,actor,null,26)).hasSize(1);
        var before=snapshots();
        var observed=spy(new NamedParameterJdbcTemplate(jdbc));
        doAnswer(call->{
            if(mutation.equals("restore"))jdbc.update("UPDATE tickets SET business_id=1 WHERE user_id=30");
            else if(mutation.equals("rename")||mutation.equals("reuse")){
                jdbc.update("UPDATE users SET username='renamed' WHERE id=1");
                if(mutation.equals("reuse")){jdbc.update("INSERT INTO users VALUES(9,'admin',TRUE,FALSE)");jdbc.update("INSERT INTO business_memberships VALUES(9,1,'ADMIN',TRUE)");}
            }else mutateActor(mutation);
            return new NamedParameterJdbcTemplate(jdbc).update(call.getArgument(0,String.class),call.getArgument(1,SqlParameterSource.class));
        }).when(observed).update(anyString(),any(SqlParameterSource.class));
        assertThat(new ScopedAiSourceService(observed,flags).eraseOrphanContentExpected(1L,actor,original.key(),original.id(),2)).isFalse();
        assertThat(snapshots()).isEqualTo(before);
    }

    @Test void orphanErasureRejectsHealthyAndForgedFormsAndRollsBackAuditFailure() {
        Source memory=approved(MEMORY,"recover"),knowledge=approved(KNOWLEDGE,"recover");
        var actor=com.example.panel.support.AiControlIdentityFixture.login(jdbc,"all-admin");
        var before=snapshots();
        assertThat(service.eraseOrphanContentExpected(1L,actor,memory.key(),memory.id(),2)).isFalse();
        assertThat(service.eraseOrphanContentExpected(1L,actor,knowledge.key(),knowledge.id(),2)).isFalse();
        mutateOwner(30,"missing");
        for(long id:new long[]{0,knowledge.id(),memory.id()+100})
            assertThat(service.eraseOrphanContentExpected(1L,actor,memory.key(),id,2)).isFalse();
        assertThat(service.eraseOrphanContentExpected(2L,actor,memory.key(),memory.id(),2)).isFalse();
        assertThat(service.eraseOrphanContentExpected(1L,actor,"other-key",memory.id(),2)).isFalse();
        assertThat(service.eraseOrphanContentExpected(1L,actor,memory.key(),memory.id(),1)).isFalse();
        jdbc.execute("CREATE FUNCTION refuse_orphan_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'fixture audit failure'; END; $$");
        jdbc.execute("CREATE TRIGGER refuse_orphan_audit BEFORE INSERT ON ai_scoped_source_events FOR EACH ROW EXECUTE FUNCTION refuse_orphan_audit()");
        assertThat(service.eraseOrphanContentExpected(1L,actor,memory.key(),memory.id(),2)).isFalse();
        assertThat(snapshots()).isEqualTo(before);
    }

    private boolean erase(Source source,String actor) {
        return service.eraseContentExpected(source.businessId(),com.example.panel.support.AiControlIdentityFixture.login(jdbc,actor),
            source.kind(),source.key(),source.id(),source.revision());
    }

    private Source approved(Kind kind,String key) {
        assertThat(kind==KNOWLEDGE ? service.createKnowledge(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),key,"Title",BODY)
            :service.createMemory(origin,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),key,"Title",BODY)).isTrue();
        assertThat(service.revise(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"admin"),kind,key,1,"Title",BODY,APPROVED)).isTrue();
        return service.find(1L,com.example.panel.support.AiControlIdentityFixture.login(jdbc,"viewer"),kind,key).orElseThrow();
    }
    private UiEventTicketScope capture(String id) { return new UiEventTicketAccessService(jdbc,flags).captureScope(id).orElseThrow(); }
    private Map<String,List<Map<String,Object>>> snapshots() {
        return Map.of("sources",jdbc.queryForList("SELECT * FROM ai_scoped_sources ORDER BY id"),
            "events",jdbc.queryForList("SELECT * FROM ai_scoped_source_events ORDER BY id"));
    }
    private void disableAi() {
        assertThat(new ScopedDialogAiPersistenceService(jdbc,flags).setControl(scope,com.example.panel.support.AiControlIdentityFixture.auth(1,"admin"),true,false,"disabled")).isTrue();
    }
    private void mutateActor(String mutation) {
        switch (mutation) {
            case "revoke" -> jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=1");
            case "delete" -> jdbc.update("DELETE FROM business_memberships WHERE user_id=1");
            case "disabled" -> jdbc.update("UPDATE users SET enabled=FALSE WHERE id=1");
            case "blocked" -> jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=1");
            case "identity_collision" -> jdbc.update("INSERT INTO users VALUES(9,'ADMIN',FALSE,FALSE)");
            case "archive" -> jdbc.update("UPDATE businesses SET status='archived' WHERE id=1");
            case "role" -> jdbc.update("UPDATE business_memberships SET role_code='UNKNOWN' WHERE user_id=1");
            default -> throw new IllegalArgumentException(mutation);
        }
    }
    private void mutateOwner(int user,String mutation) {
        switch (mutation) {
            case "business" -> jdbc.update("UPDATE tickets SET business_id=2 WHERE user_id=?",user);
            case "generation" -> jdbc.update("UPDATE tickets SET ticket_public_id=? WHERE user_id=?","d".repeat(32),user);
            case "tuple" -> jdbc.update("UPDATE tickets SET user_id=99 WHERE user_id=?",user);
            case "raw" -> jdbc.update("UPDATE tickets SET ticket_id='other' WHERE user_id=?",user);
            case "general" -> jdbc.update("UPDATE tickets SET is_general=TRUE,business_id=NULL WHERE user_id=?",user);
            case "missing" -> jdbc.update("DELETE FROM tickets WHERE user_id=?",user);
            case "collision" -> jdbc.update("INSERT INTO tickets SELECT 99,ticket_id,?,FALSE,2 FROM tickets WHERE user_id=?","e".repeat(32),user);
            case "unresolved" -> jdbc.update("UPDATE tickets SET business_id=NULL WHERE user_id=?",user);
            case "archive" -> jdbc.update("UPDATE businesses SET status='archived' WHERE id=1");
            default -> throw new IllegalArgumentException(mutation);
        }
    }
}
