package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.panel.config.MultibusinessFeatureFlags;
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

class ScopedAiPersistencePostgresTest {
    private static final String RAW = " ai/Ю?% ";
    private static final String PUBLIC = "a".repeat(32);
    private JdbcTemplate jdbc;
    private MultibusinessFeatureFlags flags;
    private ScopedDialogAiPersistenceService service;
    private UiEventTicketScope scope;

    @BeforeEach
    void setUp() throws Exception {
        jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("scoped_ai_records");
        jdbc.execute("CREATE TABLE tickets(user_id BIGINT,ticket_id TEXT,ticket_public_id TEXT,is_general BOOLEAN,business_id BIGINT,PRIMARY KEY(user_id,ticket_id))");
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,username TEXT,enabled BOOLEAN,is_blocked BOOLEAN)");
        jdbc.execute("CREATE TABLE businesses(id BIGINT PRIMARY KEY,status TEXT)");
        jdbc.execute("CREATE TABLE business_memberships(user_id BIGINT,business_id BIGINT,role_code TEXT,is_active BOOLEAN)");
        jdbc.execute("CREATE TABLE business_access_grants(user_id BIGINT,grant_scope TEXT,role_code TEXT,is_active BOOLEAN)");
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/postgresql/V62__scoped_ai_records.sql")));
        jdbc.update("INSERT INTO businesses VALUES(1,'active'),(2,'active')");
        jdbc.update("INSERT INTO users VALUES(1,'operator',TRUE,FALSE),(2,'foreign',TRUE,FALSE),(3,'viewer',TRUE,FALSE),(4,'admin',TRUE,FALSE),(5,'all',TRUE,FALSE)");
        jdbc.update("INSERT INTO business_memberships VALUES(1,1,'OPERATOR',TRUE),(2,2,'ADMIN',TRUE),(3,1,'VIEWER',TRUE)");
        jdbc.update("INSERT INTO business_access_grants VALUES(5,'ALL_BUSINESSES','OPERATOR',TRUE)");
        jdbc.update("INSERT INTO tickets VALUES(10,?,?,FALSE,1),(20,?,?,FALSE,2)", RAW,PUBLIC,RAW.trim(),"b".repeat(32));
        flags = new MultibusinessFeatureFlags();
        service = new ScopedDialogAiPersistenceService(jdbc,flags);
        scope = current();
    }

    static Stream<Arguments> rolesAndGrants() {
        return Stream.of("VIEWER","OPERATOR","MANAGER","ADMIN","UNKNOWN").flatMap(role ->
            Stream.of("membership","all").map(grant -> Arguments.of(role,grant)));
    }

    @ParameterizedTest
    @MethodSource("rolesAndGrants")
    void readsNeedReadAndControlsNeedOperateForEitherIndependentGrant(String role,String grant) {
        jdbc.update("DELETE FROM business_memberships WHERE user_id=1");
        if (grant.equals("membership")) jdbc.update("INSERT INTO business_memberships VALUES(1,1,?,TRUE)",role);
        else jdbc.update("INSERT INTO business_access_grants VALUES(1,'ALL_BUSINESSES',?,TRUE)",role);
        assertThat(service.recordHandoff(scope)).isTrue();
        boolean read = !role.equals("UNKNOWN");
        boolean operate = read && !role.equals("VIEWER");
        assertThat(service.findControl(scope,reader("OPERATOR")).isPresent()).isEqualTo(read);
        assertThat(service.findState(scope,reader("operator")).isPresent()).isEqualTo(read);
        assertThat(service.findEvents(scope,reader("operator"),1)).hasSize(read ? 1 : 0);
        assertThat(service.setControl(scope,reader("OPERATOR"),true,true," reason ")).isEqualTo(operate);
        if (operate) {
            assertThat(service.findControl(scope,reader("operator")).orElseThrow())
                .extracting(ScopedDialogAiPersistenceService.Control::aiDisabled,
                    ScopedDialogAiPersistenceService.Control::autoReplyBlocked,
                    ScopedDialogAiPersistenceService.Control::reason,
                    ScopedDialogAiPersistenceService.Control::updatedBy)
                .containsExactly(true,true,"reason","operator");
            assertThat(jdbc.queryForObject("SELECT updated_by_user_id FROM ticket_ai_scoped_control",Long.class)).isEqualTo(1L);
        }
        var beforeDenied=jdbc.queryForList("SELECT * FROM ticket_ai_scoped_control");
        assertThat(service.findControl(scope,reader(" OPERATOR "))).isEmpty();
        assertThat(service.setControl(scope,reader(" OPERATOR "),false,false,"must not overwrite")).isFalse();
        assertThat(jdbc.queryForList("SELECT * FROM ticket_ai_scoped_control")).isEqualTo(beforeDenied);

    }

    @ParameterizedTest
    @ValueSource(strings={"foreign","admin","missing",""," "})
    void unrelatedOrGlobalAdminIdentityDoesNotGrantBusinessAccess(String identity) {
        assertThat(service.recordHandoff(scope)).isTrue();
        assertDenied(scope,identity);
    }

    @ParameterizedTest
    @ValueSource(strings={"revoke","delete","disabled","blocked","identity_collision","archive","role"})
    void everyOperationReprovesCurrentActorAccessIncludingPreviouslyAuthorizedReads(String mutation) {
        assertThat(service.setControl(scope,reader("operator"),false,false,"before")).isTrue();
        assertThat(service.recordHandoff(scope)).isTrue();
        assertThat(service.findEvents(scope,reader("operator"),1)).hasSize(1);
        mutateActor(mutation);
        assertDenied(scope,"operator");
        assertThat(jdbc.queryForObject("SELECT reason FROM ticket_ai_scoped_control",String.class)).isEqualTo("before");
    }

    @ParameterizedTest
    @ValueSource(strings={"revoke","disabled","blocked","identity_collision","archive","role"})
    void mutationSqlRechecksAuthorityAtTheStatementInsteadOfTrustingAPrecheck(String mutation) {
        var observed = spy(new NamedParameterJdbcTemplate(jdbc));
        doAnswer(call -> {
            mutateActor(mutation);
            return new NamedParameterJdbcTemplate(jdbc).update(call.getArgument(0,String.class),call.getArgument(1,SqlParameterSource.class));
        }).when(observed).update(anyString(),any(SqlParameterSource.class));
        var guarded = new ScopedDialogAiPersistenceService(observed,flags);
        assertThat(service.findControl(scope,reader("operator"))).isPresent();
        assertThat(guarded.setControl(scope,reader("operator"),true,true,"late")).isFalse();
        assertThat(count("control")).isZero();
    }

    @Test
    void allBusinessesGrantAndMembershipAreIndependentButNeitherBypassesAccountOrBusinessStatus() {
        jdbc.update("INSERT INTO business_access_grants VALUES(1,'ALL_BUSINESSES','OPERATOR',TRUE)");
        jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=1");
        assertThat(service.setControl(scope,reader("operator"),false,false,null)).isTrue();
        jdbc.update("UPDATE business_memberships SET is_active=TRUE,role_code='VIEWER' WHERE user_id=1");
        jdbc.update("UPDATE business_access_grants SET is_active=FALSE WHERE user_id=1");
        assertThat(service.findControl(scope,reader("operator"))).isPresent();
        assertThat(service.setControl(scope,reader("operator"),true,true,null)).isFalse();
        jdbc.update("UPDATE businesses SET status='archived' WHERE id=1");
        assertDenied(scope,"all");
    }

    @ParameterizedTest
    @ValueSource(strings={"business","generation","tuple","raw","general","missing","collision","unresolved","archive"})
    void staleOwnerCannotReadWriteOrRetagPreviouslySavedRecords(String mutation) {
        assertThat(service.setControl(scope,reader("operator"),false,false,"original")).isTrue();
        assertThat(service.recordHandoff(scope)).isTrue();
        var before = snapshots();
        mutateOwner(mutation);
        assertDenied(scope,"all");
        assertThat(service.recordHandoff(scope)).isFalse();
        assertThat(snapshots()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings={RAW,"T/slash","T?#[]&=+","Обращение-ёж","T%2F%25","null"})
    void canonicalRecordsPreserveOpaqueRawIdAndDoNotSelectItsTrimmedNeighbour(String id) {
        jdbc.update("UPDATE tickets SET ticket_id=? WHERE user_id=10",id);
        scope = current();
        assertThat(service.setControl(scope,reader("operator"),false,true,"x".repeat(600))).isTrue();
        assertThat(service.recordHandoff(scope)).isTrue();
        for (String table : List.of("control","state","events")) {
            assertThat(jdbc.queryForMap("SELECT ticket_user_id,ticket_id,ticket_public_id,ticket_scope_kind,ticket_business_id FROM ticket_ai_scoped_"+table))
                .containsEntry("ticket_user_id",10L).containsEntry("ticket_id",id).containsEntry("ticket_public_id",PUBLIC)
                .containsEntry("ticket_scope_kind","BUSINESS").containsEntry("ticket_business_id",1L);
        }
        assertThat(service.findControl(scope,reader("operator")).orElseThrow().reason()).hasSize(500);
        assertThat(service.findState(scope,reader("viewer")).orElseThrow()).containsEntry("is_processing",false)
            .containsEntry("mode","escalate_only").containsEntry("last_action","operator_required");
    }

    @ParameterizedTest
    @ValueSource(strings={"business","generation"})
    void newBusinessOrGenerationNeverInheritsAnOldDisabledControlOrState(String mutation) {
        assertThat(service.recordHandoff(scope)).isTrue();
        assertThat(service.setControl(scope,reader("operator"),true,true,"old")).isTrue();
        var original = snapshots();
        mutateOwner(mutation);
        var fresh = current();
        String actor = mutation.equals("business") ? "foreign" : "operator";
        assertThat(service.findControl(fresh,reader(actor)).orElseThrow().aiDisabled()).isFalse();
        assertThat(service.findState(fresh,reader(actor))).isEmpty();
        assertThat(service.findEvents(fresh,reader(actor),50)).isEmpty();
        assertThat(service.recordHandoff(fresh)).isTrue();
        assertThat(service.setControl(fresh,reader(actor),false,false,"new")).isTrue();
        for (String table : List.of("control","state","events")) {
            assertThat(count(table)).isEqualTo(2);
            assertThat(jdbc.queryForList("SELECT * FROM ticket_ai_scoped_"+table+" WHERE ticket_public_id=? AND ticket_business_id=1",PUBLIC))
                .isEqualTo(original.get(table));
        }
    }

    @Test
    void conflictingFullOwnerSnapshotIsNeverOverwrittenOrBorrowed() {
        assertThat(service.recordHandoff(scope)).isTrue();
        assertThat(service.setControl(scope,reader("operator"),false,false,"original")).isTrue();
        jdbc.update("UPDATE tickets SET user_id=11 WHERE user_id=10");
        var fresh = current();
        var original = snapshots();
        assertThat(service.recordHandoff(fresh)).isFalse();
        assertThat(service.setControl(fresh,reader("operator"),true,true,"other")).isFalse();
        assertThat(service.findControl(fresh,reader("operator"))).isEmpty();
        assertThat(service.findState(fresh,reader("operator"))).isEmpty();
        assertThat(service.findEvents(fresh,reader("operator"),50)).isEmpty();
        assertThat(snapshots()).isEqualTo(original);
    }

    @Test
    void stateAndHandoffEventAreAtomicEvenWhenTheEventInsertFails() {
        assertThat(service.recordHandoff(scope)).isTrue();
        jdbc.update("UPDATE ticket_ai_scoped_state SET is_processing=TRUE,last_action='sentinel'");
        var original = snapshots();
        jdbc.execute("ALTER TABLE ticket_ai_scoped_events ADD CONSTRAINT reject_new_events CHECK(event_type<>'operator_handoff_requested') NOT VALID");
        assertThat(service.recordHandoff(scope)).isFalse();
        assertThat(snapshots()).isEqualTo(original);
        jdbc.update("DELETE FROM ticket_ai_scoped_state");
        assertThat(service.recordHandoff(scope)).isFalse();
        assertThat(count("state")).isZero();
        assertThat(count("events")).isEqualTo(1);
    }

    @Test
    void disabledAiBlocksHandoffWhileAutoReplyBlockedStillAllowsHumanEscalation() {
        assertThat(service.setControl(scope,reader("operator"),true,false,"disabled")).isTrue();
        assertThat(service.recordHandoff(scope)).isFalse();
        assertThat(count("state")).isZero();
        assertThat(count("events")).isZero();
        assertThat(service.setControl(scope,reader("operator"),false,true,null)).isTrue();
        assertThat(service.recordHandoff(scope)).isTrue();
        assertThat(service.findControl(scope,reader("viewer")).orElseThrow().autoReplyBlocked()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void unresolvedAutomationUsesStrictPolicyAndNeverGainsActorBusinessAuthority(boolean strict) {
        jdbc.update("UPDATE tickets SET business_id=NULL WHERE user_id=10");
        scope=current();
        flags.setStrictReadScope(strict);
        assertThat(service.recordHandoff(scope)).isEqualTo(!strict);
        assertDenied(scope,"all");
        assertThat(count("events")).isEqualTo(strict ? 0 : 1);
    }

    @Test
    void eventLimitIsAppliedAfterOwnerAndReadFilteringAndIsBounded() {
        for (int i=0;i<55;i++) assertThat(service.recordHandoff(scope)).isTrue();
        var foreign = new UiEventTicketAccessService(jdbc,flags).captureScope(RAW.trim()).orElseThrow();
        assertThat(service.recordHandoff(foreign)).isTrue();
        assertThat(service.findEvents(scope,reader("viewer"),100)).hasSize(50);
        var latest = service.findEvents(scope,reader("viewer"),1);
        assertThat(latest).hasSize(1);
        assertThat(latest.get(0).get("id")).isEqualTo(55L);
        assertThat(service.findEvents(scope,reader("viewer"),0)).hasSize(1);
        assertThat(service.findEvents(foreign,reader("viewer"),1)).isEmpty();
        jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=3");
        assertThat(service.findEvents(scope,reader("viewer"),1)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings={"ticket_ai_scoped_control","ticket_ai_scoped_state","ticket_ai_scoped_events","tickets","businesses"})
    void absentOwnershipOrScopedSchemaNeverFallsBackToLegacyTables(String table) {
        jdbc.execute("DROP TABLE "+table);
        assertThat(service.recordHandoff(scope)).isFalse();
        if (table.equals("ticket_ai_scoped_control") || table.equals("tickets") || table.equals("businesses")) {
            assertThat(service.setControl(scope,reader("operator"),true,true,"x")).isFalse();
            assertThat(service.findControl(scope,reader("operator"))).isEmpty();
        }
        if (!table.equals("ticket_ai_scoped_control")) {
            if (!table.equals("ticket_ai_scoped_events")) assertThat(service.findState(scope,reader("operator"))).isEmpty();
            if (!table.equals("ticket_ai_scoped_state")) assertThat(service.findEvents(scope,reader("operator"),1)).isEmpty();
        }
    }

    @Test
    void malformedScopesAndMissingFeaturePolicyStopBeforeAnyJdbcOperation() {
        var observed = mock(NamedParameterJdbcOperations.class);
        var guarded = new ScopedDialogAiPersistenceService(observed,flags);
        for (var invalid : Arrays.asList(null,new UiEventTicketScope(10L,RAW,PUBLIC,"GENERAL",null),
                new UiEventTicketScope(10L,RAW,null,"BUSINESS",1L),new UiEventTicketScope(null,RAW,PUBLIC,"BUSINESS",1L),
                new UiEventTicketScope(10L," ",PUBLIC,"BUSINESS",1L),new UiEventTicketScope(10L,RAW,PUBLIC,"UNRESOLVED",1L))) {
            assertThat(guarded.recordHandoff(invalid)).isFalse();
            assertThat(guarded.setControl(invalid,reader("operator"),true,true,null)).isFalse();
            assertThat(guarded.findControl(invalid,reader("operator"))).isEmpty();
            assertThat(guarded.findState(invalid,reader("operator"))).isEmpty();
            assertThat(guarded.findEvents(invalid,reader("operator"),5)).isEmpty();
        }
        assertThat(new ScopedDialogAiPersistenceService(observed,null).recordHandoff(scope)).isFalse();
        verifyNoInteractions(observed);
    }

    /** Models the initial fixture login; subsequent reads retain this exact ID/raw name. */
    private org.springframework.security.core.Authentication reader(String supplied) {
        if(supplied==null)return org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated("",null,List.of());
        return switch(supplied) {
            case "operator","OPERATOR"->com.example.panel.support.AiControlIdentityFixture.auth(1,"operator");
            case "foreign"->com.example.panel.support.AiControlIdentityFixture.auth(2,"foreign");
            case "viewer"->com.example.panel.support.AiControlIdentityFixture.auth(3,"viewer");
            case "admin"->com.example.panel.support.AiControlIdentityFixture.auth(4,"admin");
            case "all"->com.example.panel.support.AiControlIdentityFixture.auth(5,"all");
            default->org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(supplied,null,List.of());
        };
    }

    private UiEventTicketScope current() {
        return new UiEventTicketAccessService(jdbc,flags).captureScope(
            jdbc.queryForObject("SELECT ticket_id FROM tickets WHERE user_id IN (10,11)",String.class)).orElseThrow();
    }

    private int count(String suffix) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ticket_ai_scoped_"+suffix,Integer.class);
    }

    private Map<String,List<Map<String,Object>>> snapshots() {
        return Map.of("control",jdbc.queryForList("SELECT * FROM ticket_ai_scoped_control"),
            "state",jdbc.queryForList("SELECT * FROM ticket_ai_scoped_state"),
            "events",jdbc.queryForList("SELECT * FROM ticket_ai_scoped_events ORDER BY id"));
    }

    private void assertDenied(UiEventTicketScope target,String identity) {
        assertThat(service.setControl(target,reader(identity),true,true,"denied")).isFalse();
        assertThat(service.findControl(target,reader(identity))).isEmpty();
        assertThat(service.findState(target,reader(identity))).isEmpty();
        assertThat(service.findEvents(target,reader(identity),1)).isEmpty();
    }

    private void mutateActor(String mutation) {
        switch (mutation) {
            case "revoke" -> jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=1");
            case "delete" -> jdbc.update("DELETE FROM business_memberships WHERE user_id=1");
            case "disabled" -> jdbc.update("UPDATE users SET enabled=FALSE WHERE id=1");
            case "blocked" -> jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=1");
            case "identity_collision" -> jdbc.update("INSERT INTO users VALUES(9,'OPERATOR',FALSE,FALSE)");
            case "archive" -> jdbc.update("UPDATE businesses SET status='archived' WHERE id=1");
            case "role" -> jdbc.update("UPDATE business_memberships SET role_code='UNKNOWN' WHERE user_id=1");
            default -> throw new IllegalArgumentException(mutation);
        }
    }

    private void mutateOwner(String mutation) {
        switch (mutation) {
            case "business" -> jdbc.update("UPDATE tickets SET business_id=2 WHERE user_id=10");
            case "generation" -> jdbc.update("UPDATE tickets SET ticket_public_id=? WHERE user_id=10","c".repeat(32));
            case "tuple" -> jdbc.update("UPDATE tickets SET user_id=11 WHERE user_id=10");
            case "raw" -> jdbc.update("UPDATE tickets SET ticket_id='other' WHERE user_id=10");
            case "general" -> jdbc.update("UPDATE tickets SET is_general=TRUE,business_id=NULL WHERE user_id=10");
            case "missing" -> jdbc.update("DELETE FROM tickets WHERE user_id=10");
            case "collision" -> jdbc.update("INSERT INTO tickets VALUES(30,?,?,FALSE,2)",RAW,"c".repeat(32));
            case "unresolved" -> jdbc.update("UPDATE tickets SET business_id=NULL WHERE user_id=10");
            case "archive" -> jdbc.update("UPDATE businesses SET status='archived' WHERE id=1");
            default -> throw new IllegalArgumentException(mutation);
        }
    }
}
