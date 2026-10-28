package com.example.panel.service;

import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.example.panel.security.PanelUserPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
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
import static org.assertj.core.api.Assertions.*;

class BusinessTicketAssignmentReadPostgresTest {
    private static final String RAW=" R170/Ю?%#+ ",PUBLIC="a".repeat(32);
    private JdbcTemplate jdbc;
    private BusinessTicketAssignmentReadService read;
    // Login input resolves to the exact stored name; statement-time SQL still checks ambiguity.
    private Authentication auth(String name) {
        String stored="VIEWER".equals(name)?"viewer":name;
        long id=switch(stored){case "operator"->1;case "viewer"->2;case "all-reader"->3;case "foreign"->4;case "none"->5;default->2;};
        var principal=new PanelUserPrincipal(id,stored,"unused",true,AuthorityUtils.createAuthorityList("PAGE_DIALOGS"));
        return UsernamePasswordAuthenticationToken.authenticated(principal,"unused",principal.getAuthorities());
    }
    @BeforeEach void fixture() throws Exception {
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("business_assignment_read");
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
            jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/postgresql/"+file)));
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id) VALUES(42,?,?,11),(88,?,?,22)",RAW,PUBLIC,RAW.trim(),"b".repeat(32));
        jdbc.update("INSERT INTO ticket_responsibles VALUES(?,'Legacy private','old',CURRENT_TIMESTAMP)",RAW);
        read=new BusinessTicketAssignmentReadService(jdbc);
    }
    private void manual(long revision) {
        jdbc.update("INSERT INTO business_ticket_assignment_events(ticket_user_id,ticket_id,ticket_public_id,business_id,revision,previous_responsible,assignee_user_id,assignee,actor_user_id,actor,action) VALUES(42,?,?,11,?,'Previous secret',9223372036854775807,'<img>snapshot',1,'operator','take')",RAW,PUBLIC,revision);
    }
    private void sla(long revision) {
        jdbc.update("INSERT INTO scoped_sla_assignment_events(ticket_user_id,ticket_id,ticket_public_id,business_id,scope_kind,revision,previous_responsible,assignee,actor,source,route) VALUES(42,?,?,11,'BUSINESS',?,'Previous secret','sla target','scheduler','Source secret','Route secret')",RAW,PUBLIC,revision);
    }
    private BusinessTicketAssignmentReadService.History history() { return read.history(PUBLIC,auth("viewer"),null,50).orElseThrow(); }
    @Test void mergesBothAuditsWithoutExposingLegacyResponsibleSourceRouteOrInferringSlaIds() throws Exception {
        manual(1);sla(2);
        var before=jdbc.queryForList("SELECT * FROM ticket_responsibles");
        for(String identity:List.of("viewer","VIEWER","all-reader")) {
            var page=read.history(PUBLIC,auth(identity),null,50).orElseThrow();
            assertThat(page.owner()).isEqualTo(new BusinessTicketAssignmentReadService.Owner("42",RAW,PUBLIC,"11"));
            assertThat(page.revision()).isEqualTo(1);assertThat(page.latestEventRevision()).isEqualTo(2);
            assertThat(page.events()).extracting(BusinessTicketAssignmentReadService.Event::origin).containsExactly("SLA","MANUAL");
            assertThat(page.events().get(0).assigneeId()).isNull();assertThat(page.events().get(0).actorId()).isNull();
            assertThat(page.events().get(1).assigneeId()).isEqualTo(Long.MAX_VALUE);
            String json=new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(page);
            assertThat(json).doesNotContain("Previous secret","Legacy private","Source secret","Route secret","previousResponsible","source","route");
        }
        assertThat(jdbc.queryForList("SELECT * FROM ticket_responsibles")).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"collision","closed","resolved","no-legacy-table"})
    void auditReadDoesNotDependOnRawUniquenessOpenStateOrLegacyTable(String change) {
        manual(1);sla(2);
        switch(change) {
            case "collision" -> jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id) VALUES(99,?,?,22)",RAW,"c".repeat(32));
            case "closed","resolved" -> jdbc.update("UPDATE tickets SET status=? WHERE user_id=42",change);
            case "no-legacy-table" -> jdbc.execute("DROP TABLE ticket_responsibles");
        }
        assertThat(history().events()).hasSize(2);
        assertThat(read.history("b".repeat(32),auth("all-reader"),null,50).orElseThrow().events()).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings={"membership","all-grant","disabled","blocked","ambiguous","archived","role","general-grant","foreign","space"})
    void currentReaderAccountAndBusinessReadAreRequired(String denied) {
        manual(1);sla(2);String identity="viewer";
        switch(denied) {
            case "membership" -> jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=2");
            case "all-grant" -> {identity="all-reader";jdbc.update("UPDATE business_access_grants SET is_active=FALSE");}
            case "disabled" -> jdbc.update("UPDATE users SET enabled=FALSE WHERE id=2");
            case "blocked" -> jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=2");
            case "ambiguous" -> jdbc.update("INSERT INTO users VALUES(6,'VIEWER',TRUE,FALSE)");
            case "archived" -> jdbc.update("UPDATE businesses SET status='archived' WHERE id=11");
            case "role" -> jdbc.update("UPDATE business_memberships SET role_code='UNKNOWN' WHERE user_id=2");
            case "general-grant" -> {identity="none";jdbc.update("INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES(5,'GENERAL_TICKETS','ADMIN')");}
            case "foreign" -> identity="foreign";
            case "space" -> identity=" viewer ";
        }
        assertThat(read.history(PUBLIC,auth(identity),null,50)).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings={"business","public","raw","user","general","unresolved","recreate","public-collision"})
    void auditCannotFollowAnotherFullOwnerOrGeneration(String change) {
        manual(1);sla(2);String publicId=PUBLIC;
        switch(change) {
            case "business" -> jdbc.update("UPDATE tickets SET business_id=22 WHERE user_id=42");
            case "public" -> {publicId="c".repeat(32);jdbc.update("UPDATE tickets SET ticket_public_id=? WHERE user_id=42",publicId);}
            case "raw" -> jdbc.update("UPDATE tickets SET ticket_id='new' WHERE user_id=42");
            case "user" -> jdbc.update("UPDATE tickets SET user_id=43 WHERE user_id=42");
            case "general" -> jdbc.update("UPDATE tickets SET business_id=NULL,is_general=TRUE WHERE user_id=42");
            case "unresolved" -> jdbc.update("UPDATE tickets SET business_id=NULL WHERE user_id=42");
            case "recreate" -> {jdbc.update("DELETE FROM tickets WHERE user_id=42");publicId="c".repeat(32);jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id) VALUES(42,?,?,11)",RAW,publicId);}
            case "public-collision" -> jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id) VALUES(99,'duplicate',?,22)",PUBLIC);
        }
        var page=read.history(publicId,auth("all-reader"),null,50);
        if(List.of("general","unresolved","public-collision").contains(change)) assertThat(page).isEmpty();
        else assertThat(page.orElseThrow().events()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM business_ticket_assignment_events",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM scoped_sla_assignment_events",Long.class)).isEqualTo(1);
    }
    @Test void mixedHistoryPagesHaveStrictCursorAndStableSnapshotMetadata() {
        for(int i=1;i<=7;i++)if(i%2==0)sla(i);else manual(i);
        var first=read.history(PUBLIC,auth("viewer"),null,3).orElseThrow();
        assertThat(first.events()).extracting(BusinessTicketAssignmentReadService.Event::revision).containsExactly(7L,6L,5L);
        assertThat(first.hasMore()).isTrue();assertThat(first.nextBeforeRevision()).isEqualTo(5);
        var next=read.history(PUBLIC,auth("viewer"),first.nextBeforeRevision(),3).orElseThrow();
        assertThat(next.events()).extracting(BusinessTicketAssignmentReadService.Event::revision).containsExactly(4L,3L,2L);
        assertThat(next.revision()).isEqualTo(first.revision());assertThat(next.latestEventRevision()).isEqualTo(7);
        var last=read.history(PUBLIC,auth("viewer"),next.nextBeforeRevision(),3).orElseThrow();
        assertThat(last.events()).extracting(BusinessTicketAssignmentReadService.Event::revision).containsExactly(1L);
        assertThat(last.hasMore()).isFalse();assertThat(last.nextBeforeRevision()).isNull();
        assertThat(read.history(PUBLIC,auth("viewer"),1L,100).orElseThrow().events()).isEmpty();
        sla(8);assertThat(history().latestEventRevision()).isEqualTo(8);assertThat(history().revision()).isEqualTo(1);
    }
    @Test void maximumBigintsAndSnapshotsSurviveAccountRenameDeleteAndNameReuse() throws Exception {
        manual(Long.MAX_VALUE);
        jdbc.update("DELETE FROM users WHERE id=1");jdbc.update("INSERT INTO users VALUES(6,'operator',TRUE,FALSE)");
        var page=history();assertThat(page.events().get(0).actorId()).isEqualTo(1);assertThat(page.events().get(0).actor()).isEqualTo("operator");
        var mapper=new ObjectMapper().registerModule(new JavaTimeModule());var json=mapper.readTree(mapper.writeValueAsString(page));
        for(String field:List.of("revision","latestEventRevision"))assertThat(json.get(field).isTextual()).isTrue();
        assertThat(json.get("latestEventRevision").asText()).isEqualTo(Long.toString(Long.MAX_VALUE));
        for(String field:List.of("revision","assigneeId","actorId"))assertThat(json.get("events").get(0).get(field).isTextual()).isTrue();
    }
    @Test void ambiguousCrossOriginRevisionFailsInsteadOfDroppingATiedEvent() {
        manual(1);sla(1);
        assertThatThrownBy(()->read.history(PUBLIC,auth("viewer"),null,1)).isInstanceOf(BusinessTicketAssignmentReadService.ReadUnavailableException.class);
    }
    @ParameterizedTest @ValueSource(strings={"business_ticket_assignment_events","scoped_sla_assignment_events"})
    void missingCanonicalAuditIsUnavailableRatherThanEmpty(String table) {
        jdbc.execute("DROP TABLE "+table);
        assertThatThrownBy(()->history()).isInstanceOf(BusinessTicketAssignmentReadService.ReadUnavailableException.class);
    }
    @Test void emptyHistoryAndInvalidInputsStayBounded() {
        assertThat(history().events()).isEmpty();assertThat(history().latestEventRevision()).isZero();
        assertThat(read.history("bad",auth("viewer"),null,50)).isEmpty();assertThat(read.history(PUBLIC,null,null,50)).isEmpty();
        assertThatThrownBy(()->read.history(PUBLIC,auth("viewer"),0L,50)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->read.history(PUBLIC,auth("viewer"),null,101)).isInstanceOf(IllegalArgumentException.class);
    }
}
