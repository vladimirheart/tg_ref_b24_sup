package com.example.panel.service;

import com.example.panel.security.PanelUserPrincipal;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import static org.assertj.core.api.Assertions.*;

class BusinessTicketCurrentAssignmentReadPostgresTest {
    private static final String RAW=" raw/Ю?%#+ ", PUBLIC="a".repeat(32);
    private JdbcTemplate jdbc;
    private BusinessTicketCurrentAssignmentReadService service;
    private Authentication viewer;
    @BeforeEach void fixture() throws Exception {
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("current_assignment");
        jdbc.execute("""
            CREATE TABLE users(id BIGINT PRIMARY KEY,username TEXT,enabled BOOLEAN DEFAULT TRUE,is_blocked BOOLEAN DEFAULT FALSE);
            CREATE TABLE businesses(id BIGINT PRIMARY KEY,status TEXT DEFAULT 'active');
            CREATE TABLE business_memberships(user_id BIGINT,business_id BIGINT,role_code TEXT,is_active BOOLEAN DEFAULT TRUE);
            CREATE TABLE business_access_grants(user_id BIGINT,grant_scope TEXT,role_code TEXT,is_active BOOLEAN DEFAULT TRUE);
            CREATE TABLE tickets(user_id BIGINT,ticket_id TEXT,ticket_public_id TEXT,business_id BIGINT,is_general BOOLEAN DEFAULT FALSE,
                status TEXT DEFAULT 'open',PRIMARY KEY(user_id,ticket_id));
            CREATE TABLE ticket_responsibles(ticket_id TEXT PRIMARY KEY,responsible TEXT,assigned_by TEXT,assigned_at TIMESTAMPTZ);
            INSERT INTO users(id,username) VALUES(1,'operator'),(2,'viewer'),(3,'all-reader'),(4,'foreign'),(5,'none');
            INSERT INTO businesses(id) VALUES(11),(22);
            INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(1,11,'OPERATOR'),(2,11,'VIEWER'),(4,22,'ADMIN');
            INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES(3,'ALL_BUSINESSES','VIEWER');
            """);
        for(String file:List.of("V77__scoped_sla_assignment_revision.sql","V78__business_manual_assignment_events.sql"))
            jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/postgresql",file)));
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id) VALUES(42,?,?,11),(88,'other',?,22)",RAW,PUBLIC,"b".repeat(32));
        service=new BusinessTicketCurrentAssignmentReadService(jdbc);
        viewer=actor(2,"viewer");
    }
    private Authentication actor(long id,String name) {
        var principal=new PanelUserPrincipal(id,name,"",true,List.of(()->"PAGE_DIALOGS"));
        return new TestingAuthenticationToken(principal,null,"PAGE_DIALOGS");
    }
    private long revision() { return jdbc.queryForObject("SELECT responsibility_revision FROM tickets WHERE user_id=42",Long.class); }
    private void live(String username) {
        jdbc.update("INSERT INTO ticket_responsibles(ticket_id,responsible,assigned_by,assigned_at) VALUES(?,?,'private actor',clock_timestamp()) ON CONFLICT(ticket_id) DO UPDATE SET responsible=EXCLUDED.responsible,assigned_at=clock_timestamp()",RAW,username);
    }
    private void manualEvent(long revision,String name,long id) {
        jdbc.update("INSERT INTO business_ticket_assignment_events(ticket_user_id,ticket_id,ticket_public_id,business_id,revision,previous_responsible,assignee_user_id,assignee,actor_user_id,actor,action) VALUES(42,?,?,11,?,'private previous',?,?,1,'private actor','assign')",RAW,PUBLIC,revision,id,name);
    }
    private void slaEvent(long revision,String name) {
        jdbc.update("INSERT INTO scoped_sla_assignment_events(ticket_user_id,ticket_id,ticket_public_id,business_id,scope_kind,revision,assignee,actor,source,route) VALUES(42,?,?,11,'BUSINESS',?,?,'private actor','private source','private route')",RAW,PUBLIC,revision,name);
    }
    private BusinessTicketCurrentAssignmentReadService.State read() { return service.current(PUBLIC,viewer).orElseThrow(); }
    @Test void neverAssignedIsExplicitAndViewerReadDoesNotRequireOperate() {
        var state=read();assertThat(state.status()).isEqualTo("UNASSIGNED");assertThat(state.revision()).isZero();assertThat(state.assignee()).isNull();
        assertThat(state.owner()).isEqualTo(new BusinessTicketCurrentAssignmentReadService.Owner("42",RAW,PUBLIC,"11"));
        assertThat(service.current(PUBLIC,actor(3,"all-reader"))).contains(state);
    }
    @ParameterizedTest @ValueSource(longs={7L,9007199254740993L,Long.MAX_VALUE})
    void manualSnapshotKeepsExactUsernameAndFrozenIdWithoutLookingUpAnotherAccount(long id) throws Exception {
        String name=" <img src=x> Ю\r\n ";live(name);manualEvent(revision(),name,id);
        var rows=jdbc.queryForList("SELECT * FROM ticket_responsibles");
        var state=read();assertThat(state.status()).isEqualTo("ASSIGNED");
        assertThat(state.assignee().id()).isEqualTo(id);assertThat(state.assignee().username()).isEqualTo(name);assertThat(state.assignee().origin()).isEqualTo("MANUAL");
        assertThat(state.assignee().assignedAt()).isNotNull();
        jdbc.update("INSERT INTO users(id,username) VALUES(99,?)",name);
        assertThat(read()).isEqualTo(state);
        String json=new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(state);
        assertThat(json).contains("\"id\":\""+id+"\"","\"revision\":\"1\"").doesNotContain("private actor","private previous","private source","private route","password","avatar");
        assertThat(jdbc.queryForList("SELECT * FROM ticket_responsibles")).isEqualTo(rows);
    }
    @Test void slaMatchesLiveRowButNeverInfersNumericIdFromUsername() {
        live(" sla target ");slaEvent(revision()," sla target ");
        jdbc.update("INSERT INTO users(id,username) VALUES(99,' sla target ')");
        var state=read();assertThat(state.status()).isEqualTo("ASSIGNED");assertThat(state.assignee().id()).isNull();
        assertThat(state.assignee().origin()).isEqualTo("SLA");assertThat(state.assignee().username()).isEqualTo(" sla target ");
    }
    @ParameterizedTest @ValueSource(strings={"live-only","deleted-live","stale","future","duplicate","mismatch","zero-id","blank","raw-collision","owner-business","new-generation"})
    void missingOrContradictoryCurrentProofIsNotReportedAsAnAssignment(String change) throws Exception {
        live("target");
        if(!change.equals("live-only")) manualEvent(revision(),"target",7);
        switch(change) {
            case "deleted-live" -> jdbc.update("DELETE FROM ticket_responsibles WHERE ticket_id=?",RAW);
            case "stale" -> live("changed target");
            case "future" -> slaEvent(revision()+1,"future target");
            case "duplicate" -> slaEvent(revision(),"target");
            case "mismatch" -> {
                jdbc.execute("ALTER TABLE business_ticket_assignment_events DISABLE TRIGGER preserve_business_assignment_event");
                jdbc.update("UPDATE business_ticket_assignment_events SET assignee='TARGET'");
            }
            case "zero-id" -> {
                jdbc.execute("ALTER TABLE business_ticket_assignment_events DISABLE TRIGGER preserve_business_assignment_event");
                jdbc.update("UPDATE business_ticket_assignment_events SET assignee_user_id=0");
            }
            case "blank" -> { live(" ");manualEvent(revision()," ",7); }
            case "raw-collision" -> jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id) VALUES(99,?,?,22)",RAW,"c".repeat(32));
            case "owner-business" -> { jdbc.update("UPDATE tickets SET business_id=22 WHERE user_id=42");viewer=actor(3,"all-reader"); }
            case "new-generation" -> jdbc.update("UPDATE tickets SET ticket_public_id=? WHERE user_id=42","c".repeat(32));
        }
        var state=service.current(change.equals("new-generation")?"c".repeat(32):PUBLIC,viewer).orElseThrow();
        assertThat(state.status()).isEqualTo("UNVERIFIED");assertThat(state.assignee()).isNull();
        assertThat(new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(state)).doesNotContain("target","private");
    }
    @ParameterizedTest @ValueSource(strings={"closed","resolved"})
    void closedTicketsStillAllowReadOnlyCurrentAssignment(String status) {
        live("target");manualEvent(revision(),"target",7);jdbc.update("UPDATE tickets SET status=? WHERE user_id=42",status);
        assertThat(read().status()).isEqualTo("ASSIGNED");
    }
    @ParameterizedTest @ValueSource(strings={"revoke","all-revoke","disabled","blocked","ambiguous","archived","role","delete","rename","reuse","foreign","no-grant","raw-name","case-change"})
    void currentBoundReaderAndBusinessReadAreMandatory(String change) {
        live("target");manualEvent(revision(),"target",7);
        switch(change) {
            case "revoke" -> jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=2");
            case "all-revoke" -> {viewer=actor(3,"all-reader");jdbc.update("UPDATE business_access_grants SET is_active=FALSE");}
            case "disabled" -> jdbc.update("UPDATE users SET enabled=FALSE WHERE id=2");
            case "blocked" -> jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=2");
            case "ambiguous" -> jdbc.update("INSERT INTO users(id,username) VALUES(9,'VIEWER')");
            case "archived" -> jdbc.update("UPDATE businesses SET status='archived' WHERE id=11");
            case "role" -> jdbc.update("UPDATE business_memberships SET role_code='UNKNOWN' WHERE user_id=2");
            case "delete" -> jdbc.update("DELETE FROM users WHERE id=2");
            case "rename" -> jdbc.update("UPDATE users SET username='renamed' WHERE id=2");
            case "reuse" -> {jdbc.update("DELETE FROM users WHERE id=2");jdbc.update("INSERT INTO users(id,username) VALUES(9,'viewer')");jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(9,11,'ADMIN')");}
            case "foreign" -> viewer=actor(4,"foreign");
            case "no-grant" -> viewer=actor(5,"none");
            case "raw-name" -> viewer=actor(2," viewer ");
            case "case-change" -> jdbc.update("UPDATE users SET username='VIEWER' WHERE id=2");
        }
        assertThat(service.current(PUBLIC,viewer)).isEmpty();
    }
    @Test void exactPaddedReaderAndLargeIdsAreSupported() {
        jdbc.update("UPDATE users SET username=' viewer ',id=? WHERE id=2",9007199254740993L);
        jdbc.update("UPDATE business_memberships SET user_id=? WHERE user_id=2",9007199254740993L);
        assertThat(service.current(PUBLIC,actor(9007199254740993L," viewer "))).isPresent();
    }
    @ParameterizedTest @ValueSource(strings={"general","unresolved","duplicate-public"})
    void nonBusinessOrAmbiguousPublicOwnersAreHidden(String change) {
        switch(change) {
            case "general" -> jdbc.update("UPDATE tickets SET business_id=NULL,is_general=TRUE WHERE user_id=42");
            case "unresolved" -> jdbc.update("UPDATE tickets SET business_id=NULL WHERE user_id=42");
            case "duplicate-public" -> jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id) VALUES(99,'duplicate',?,22)",PUBLIC);
        }
        assertThat(service.current(PUBLIC,viewer)).isEmpty();
    }
    @Test void invalidActorsAndLocatorsDoNotTouchStorage() {
        jdbc.execute("DROP TABLE users");
        for(Authentication invalid:java.util.Arrays.asList(null,new TestingAuthenticationToken("viewer",null,"PAGE_DIALOGS"),
                actor(0,"viewer"),actor(-1,"viewer"),actor(2," "),new TestingAuthenticationToken("viewer",null),
                new AnonymousAuthenticationToken("key",new PanelUserPrincipal(2,"viewer","",true,List.of(()->"PAGE_DIALOGS")),List.of(()->"PAGE_DIALOGS")))) {
            assertThat(service.current(PUBLIC,invalid)).isEmpty();
        }
        assertThat(service.current("invalid",viewer)).isEmpty();
    }
    @Test void schemaFailureIsUnavailableRatherThanUnassignedOrLegacyFallback() {
        jdbc.execute("DROP TABLE ticket_responsibles");
        assertThatThrownBy(this::read).isInstanceOf(BusinessTicketCurrentAssignmentReadService.ReadUnavailableException.class);
    }
}
