package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.util.*;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.jdbc.core.JdbcTemplate;

class ScopedAssignmentWriterIdentityPostgresTest {
    private static JdbcTemplate jdbc;
    private long businessA,businessB,channel,spacedId;
    private String plain,spaced;
    private UiEventTicketScope ownA,ownB;
    private DialogResponsibilityService create;
    private ScopedSlaAssignmentService sla;

    @BeforeAll static void schema() {
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("assignment_writer_identity");
        Flyway.configure().dataSource(jdbc.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql").load().migrate();
    }
    @BeforeEach void prepare() {
        String suffix=UUID.randomUUID().toString();plain="writer-r176-"+suffix;spaced=" "+plain+" ";
        businessA=business();businessB=business();
        channel=jdbc.queryForObject("INSERT INTO channels(token,channel_name,platform) VALUES (?,'R176','web') RETURNING id",Long.class,suffix);
        long plainId=account(plain);spacedId=account(spaced);
        jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES (?,?,'OPERATOR'),(?,?,'OPERATOR')",plainId,businessA,spacedId,businessB);
        ownA=owner(businessA);ownB=owner(businessB);
        var flags=new MultibusinessFeatureFlags();flags.getFoundation().setEnabled(true);flags.setStrictReadScope(true);
        create=new DialogResponsibilityService(jdbc,new LegacyTicketIdJdbcGuard(jdbc),flags);sla=new ScopedSlaAssignmentService(jdbc,flags);
    }
    static Stream<Arguments> aliases() {
        return Stream.of("create","sla").flatMap(writer->Stream.of("prefix","suffix","both","tab","newline").map(variant->Arguments.of(writer,variant)));
    }
    @ParameterizedTest @MethodSource("aliases")
    void literalTargetCannotBorrowTheTrimmedAccountsOperate(String writer,String variant) {
        String target=literal(variant);var before=snapshot(ownA);
        assertThat(write(writer,ownA,target,"routing")).isFalse();
        assertThat(snapshot(ownA)).isEqualTo(before);
    }
    @ParameterizedTest @MethodSource("aliases")
    void realLiteralAccountUsesOnlyItsOwnGrantAndKeepsExactAuditIdentity(String writer,String variant) {
        String target=literal(variant).toUpperCase(Locale.ROOT),actor=" Lead\t ";
        assertThat(write(writer,ownB,target,actor)).isTrue();
        assertThat(jdbc.queryForMap("SELECT responsible,assigned_by FROM ticket_responsibles WHERE ticket_id=?",ownB.ticketId()))
            .containsEntry("responsible",target).containsEntry("assigned_by",actor);
        assertThat(jdbc.queryForObject("SELECT responsibility_revision FROM tickets WHERE ticket_id=?",Long.class,ownB.ticketId())).isEqualTo(1L);
        if(writer.equals("sla"))assertThat(jdbc.queryForMap("SELECT assignee,actor,previous_responsible FROM scoped_sla_assignment_events WHERE ticket_id=?",ownB.ticketId()))
            .containsEntry("assignee",target).containsEntry("actor",actor).containsEntry("previous_responsible",null);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_responsibles WHERE ticket_id=?",Long.class,ownA.ticketId())).isZero();
    }
    static Stream<Arguments> grants() {
        return Stream.of("create","sla").flatMap(writer->Stream.of("OPERATOR","MANAGER","ADMIN").flatMap(role->Stream.of("membership","all").map(source->Arguments.of(writer,role,source))));
    }
    @ParameterizedTest @MethodSource("grants")
    void eachOperateRoleAndGrantSourceBelongsToTheExactAccount(String writer,String role,String source) {
        jdbc.update("DELETE FROM business_memberships WHERE user_id=?",spacedId);
        if(source.equals("all"))jdbc.update("INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES (?,'ALL_BUSINESSES',?)",spacedId,role);
        else jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES (?,?,?)",spacedId,businessA,role);
        assertThat(write(writer,ownA,spaced,"routing")).isTrue();
        assertThat(jdbc.queryForObject("SELECT responsible FROM ticket_responsibles WHERE ticket_id=?",String.class,ownA.ticketId())).isEqualTo(spaced);
    }
    @ParameterizedTest @ValueSource(strings={"disabled","blocked","duplicate","viewer","revoked","archived","system-only","foreign"})
    void currentLiteralAccountAndGrantAreCheckedAtEachWriter(String mutation) {
        switch(mutation) {
            case "disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=?",spacedId);
            case "blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=?",spacedId);
            case "duplicate"->account(spaced.toUpperCase(Locale.ROOT));
            case "viewer"->jdbc.update("UPDATE business_memberships SET role_code='VIEWER' WHERE user_id=?",spacedId);
            case "revoked"->jdbc.update("DELETE FROM business_memberships WHERE user_id=?",spacedId);
            case "archived"->jdbc.update("UPDATE businesses SET status='archived' WHERE id=?",businessB);
            case "foreign"->jdbc.update("UPDATE business_memberships SET business_id=? WHERE user_id=?",businessA,spacedId);
            case "system-only"->{jdbc.update("DELETE FROM business_memberships WHERE user_id=?",spacedId);jdbc.update("INSERT INTO user_authorities(user_id,authority) VALUES (?,'ROLE_ADMIN'),(?,'PAGE_DIALOGS')",spacedId,spacedId);}
        }
        var before=snapshot(ownB);
        for(String writer:List.of("create","sla")){assertThat(write(writer,ownB,spaced,"routing")).isFalse();assertThat(snapshot(ownB)).isEqualTo(before);}
    }
    @ParameterizedTest @ValueSource(strings={"business","recreated","tuple","general","collision"})
    void aCorrectLiteralAccountCannotWriteAgainstAReplacedFrozenOwner(String mutation) {
        jdbc.update("INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES (?,'ALL_BUSINESSES','OPERATOR')",spacedId);
        switch(mutation) {
            case "business"->jdbc.update("UPDATE tickets SET business_id=? WHERE ticket_id=?",businessA,ownB.ticketId());
            case "tuple"->jdbc.update("UPDATE tickets SET user_id=user_id+1 WHERE ticket_id=?",ownB.ticketId());
            case "general"->jdbc.update("UPDATE tickets SET business_id=NULL,is_general=TRUE,general_selection_source='MANUAL' WHERE ticket_id=?",ownB.ticketId());
            case "collision"->jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id) VALUES (?,?,?,?)",System.nanoTime(),ownB.ticketId(),UUID.randomUUID().toString().replace("-",""),businessA);
            case "recreated"->{jdbc.update("DELETE FROM tickets WHERE ticket_id=?",ownB.ticketId());jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id,channel_id,status) VALUES (?,?,?,?,?,'open')",ownB.ticketUserId(),ownB.ticketId(),UUID.randomUUID().toString().replace("-",""),businessB,channel);}
        }
        var before=snapshot(ownB);
        for(String writer:List.of("create","sla")){assertThat(write(writer,ownB,spaced,"routing")).isFalse();assertThat(snapshot(ownB)).isEqualTo(before);}
    }
    @ParameterizedTest @ValueSource(strings={"prefix","suffix","both","tab","newline"})
    void existingScopedAssignmentReturnsTheStoredLiteralKeyWithoutRedirect(String variant) {
        String target=literal(variant);
        assertThat(create.assignScopedResponsibleIfMissing(ownB,target,"routing")).isEqualTo(target);
        var before=snapshot(ownB);
        assertThat(create.assignScopedResponsibleIfMissing(ownB,null,"ignored")).isEqualTo(target);
        assertThat(create.assignScopedResponsibleIfMissing(ownB,plain,"ignored")).isEqualTo(target);
        assertThat(snapshot(ownB)).isEqualTo(before);
    }
    static Stream<Arguments> actors() {return Stream.of("create","sla").flatMap(writer->Stream.of(null," \t").map(actor->Arguments.of(writer,actor)));}
    @ParameterizedTest @MethodSource("actors")
    void blankActorFallbackPreservesTheLiteralAssignee(String writer,String actor) {
        assertThat(write(writer,ownB,spaced,actor)).isTrue();
        assertThat(jdbc.queryForObject("SELECT assigned_by FROM ticket_responsibles WHERE ticket_id=?",String.class,ownB.ticketId()))
            .isEqualTo(writer.equals("create")?spaced:"sla_orchestrator");
    }
    @Test void slaRedirectKeepsLiteralTargetPreviousResponsibleAndRevisionCompareAndSet() {
        jdbc.update("INSERT INTO ticket_responsibles(ticket_id,responsible,assigned_by) VALUES (?,?,'legacy')",ownB.ticketId(),plain);
        var frozen=new DialogLookupReadService.ScopedSlaDialog(null,ownB,channel,1L,plain);
        assertThat(sla.assign(frozen,spaced," Lead ","rules","r176")).isTrue();
        assertThat(jdbc.queryForMap("SELECT assignee,actor,previous_responsible,revision FROM scoped_sla_assignment_events WHERE ticket_id=?",ownB.ticketId()))
            .containsEntry("assignee",spaced).containsEntry("actor"," Lead ").containsEntry("previous_responsible",plain).containsEntry("revision",2L);
        var before=snapshot(ownB);assertThat(sla.assign(frozen,spaced," Lead ","rules","r176")).isFalse();assertThat(snapshot(ownB)).isEqualTo(before);
    }
    private String literal(String variant) {
        String value=switch(variant){case "prefix"->" "+plain;case "suffix"->plain+" ";case "tab"->"\t"+plain+"\t";case "newline"->"\n"+plain+"\r";default->" "+plain+" ";};
        jdbc.update("UPDATE users SET username=? WHERE id=?",value,spacedId);spaced=value;return value;
    }
    private boolean write(String writer,UiEventTicketScope scope,String target,String actor) {
        if(writer.equals("create"))return create.assignScopedResponsibleIfMissing(scope,target,actor)!=null;
        return sla.assign(new DialogLookupReadService.ScopedSlaDialog(null,scope,channel,0L,null),target,actor,"rules","r176");
    }
    private Map<String,Object> snapshot(UiEventTicketScope scope) {
        return Map.of("root",jdbc.queryForList("SELECT * FROM tickets WHERE ticket_id=? ORDER BY user_id",scope.ticketId()),
            "assignment",jdbc.queryForList("SELECT * FROM ticket_responsibles WHERE ticket_id=?",scope.ticketId()),
            "events",jdbc.queryForList("SELECT * FROM scoped_sla_assignment_events WHERE ticket_id=? ORDER BY revision",scope.ticketId()),
            "legacy_audit",jdbc.queryForList("SELECT * FROM dialog_action_audit WHERE ticket_id=? ORDER BY id",scope.ticketId()));
    }
    private long account(String username) {return jdbc.queryForObject("INSERT INTO users(username,password) VALUES (?,'unused') RETURNING id",Long.class,username);}
    private long business() {return jdbc.queryForObject("INSERT INTO businesses(code,display_name) VALUES (?,'R176') RETURNING id",Long.class,"r176-"+UUID.randomUUID());}
    private UiEventTicketScope owner(long business) {
        long user=System.nanoTime();String id=" r176/"+UUID.randomUUID()+" ",pub=UUID.randomUUID().toString().replace("-","");
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id,channel_id,status) VALUES (?,?,?,?,?,'open')",user,id,pub,business,channel);
        return new UiEventTicketScope(user,id,pub,"BUSINESS",business);
    }
}
